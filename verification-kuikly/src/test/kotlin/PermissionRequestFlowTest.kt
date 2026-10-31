package io.github.gycrosskit.permission

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionRequestFlowTest {
    @AfterTest fun resetDefaults() { PermissionFlowDefaults.configure(PermissionFlowHandlers()) }

    @Test fun installDefaultsNeverOverridesExplicitConfigurationIncludingEmptyHooks() = runTest {
        val calls = mutableListOf<String>()
        val custom = PermissionFlowHandlers(onExplanation = { calls += "explain:$it"; true },
            onSettingsRequired = { calls += "settings:$it"; false })
        PermissionFlowDefaults.configure(custom)
        assertFalse(PermissionFlowDefaults.installDefaults(PermissionFlowHandlers()))
        assertEquals(custom, PermissionFlowDefaults.snapshot())
        assertEquals(true, PermissionFlowDefaults.snapshot().onExplanation("CAMERA"))
        assertFalse(PermissionFlowDefaults.snapshot().onSettingsRequired("MICROPHONE"))
        assertEquals(listOf("explain:CAMERA", "settings:MICROPHONE"), calls)
        val empty = PermissionFlowHandlers()
        PermissionFlowDefaults.configure(empty)
        assertFalse(PermissionFlowDefaults.installDefaults(custom))
        assertEquals(empty, PermissionFlowDefaults.snapshot())
        assertFalse(PermissionFlowDefaults.snapshot().onExplanation("CAMERA"))
    }

    private class Platform(
        val before: PermissionStatus,
        val requested: PermissionStatus = PermissionStatus.DENIED,
        val restored: PermissionStatus = PermissionStatus.DENIED,
        val pauseAt: String? = null,
        val cancelAt: String? = null,
    ) : PermissionPlatform {
        val calls = mutableListOf<String>()
        suspend fun record(phase: String) {
            calls += phase
            if (pauseAt == phase) awaitCancellation()
            if (cancelAt == phase) currentCoroutineContext().cancel()
        }
        override suspend fun getStatus(permission: AppPermission): PermissionStatus { record("status"); return before }
        override suspend fun request(permission: AppPermission): PermissionStatus { record("request"); return requested }
        override suspend fun resumeAfterSettings(permission: AppPermission): PermissionStatus { record("resume"); return restored }
    }

    @Test fun `usable or unrequestable initial states never explain or request`() = runTest {
        for (status in listOf(PermissionStatus.GRANTED, PermissionStatus.LIMITED,
            PermissionStatus.RESTRICTED, PermissionStatus.UNSUPPORTED)) {
            val platform = Platform(status)
            var denied = 0
            val result = platform.requestWithFlow(AppPermission.CAMERA,
                onExplanation = { error("No explanation for $status") },
                onSettingsRequired = { error("No settings for $status") }, onDenied = { denied++ })
            assertEquals(if (status.allowsUse) PermissionRequestResult.GRANTED else PermissionRequestResult.DENIED, result)
            assertEquals(if (status.allowsUse) 0 else 1, denied)
            assertEquals(listOf("status"), platform.calls)
        }
    }

    @Test fun `declining explanation cancels either requestable state without requesting`() = runTest {
        for (status in listOf(PermissionStatus.NOT_DETERMINED, PermissionStatus.DENIED)) {
            val platform = Platform(status)
            assertEquals(PermissionRequestResult.CANCELLED, platform.requestWithFlow(AppPermission.CAMERA,
                onExplanation = { platform.record("explanation"); false },
                onSettingsRequired = { error("Explanation was declined") },
                onDenied = { error("Explanation cancellation is not denial") }))
            assertEquals(listOf("status", "explanation"), platform.calls)
        }
    }

    @Test fun `first request maps every actual result without offering settings`() = runTest {
        for (status in PermissionStatus.entries) {
            val platform = Platform(PermissionStatus.NOT_DETERMINED, status)
            var denied = 0
            val result = platform.requestWithFlow(AppPermission.CAMERA,
                onExplanation = { platform.record("explanation"); true },
                onSettingsRequired = { error("First request cannot require settings") }, onDenied = { denied++ })
            assertEquals(if (status.allowsUse) PermissionRequestResult.GRANTED else PermissionRequestResult.DENIED, result)
            assertEquals(if (status.allowsUse) 0 else 1, denied)
            assertEquals(listOf("status", "explanation", "request"), platform.calls)
        }
    }

    @Test fun `retry offers settings only for actual denied result`() = runTest {
        for (status in PermissionStatus.entries) {
            val platform = Platform(PermissionStatus.DENIED, status)
            var denied = 0
            val result = platform.requestWithFlow(AppPermission.CAMERA,
                onExplanation = { platform.record("explanation"); true },
                onSettingsRequired = { platform.record("settings"); false }, onDenied = { denied++ })
            val expected = when {
                status.allowsUse -> PermissionRequestResult.GRANTED
                status == PermissionStatus.DENIED -> PermissionRequestResult.CANCELLED
                else -> PermissionRequestResult.DENIED
            }
            assertEquals(expected, result)
            assertEquals(if (expected == PermissionRequestResult.DENIED) 1 else 0, denied)
            assertEquals(listOf("status", "explanation", "request") +
                if (status == PermissionStatus.DENIED) listOf("settings") else emptyList(), platform.calls)
        }
    }

    @Test fun `settings recovery waits for host return then maps actual restored state`() = runTest {
        for (status in PermissionStatus.entries) {
            val platform = Platform(PermissionStatus.DENIED, restored = status)
            var denied = 0
            val returnedFromSettings = CompletableDeferred<Boolean>()
            val result = async {
                platform.requestWithFlow(AppPermission.CAMERA,
                    onExplanation = { platform.record("explanation"); true },
                    onSettingsRequired = { platform.record("settings"); returnedFromSettings.await() }, onDenied = { denied++ })
            }
            runCurrent()
            assertEquals(listOf("status", "explanation", "request", "settings"), platform.calls)
            returnedFromSettings.complete(true)
            assertEquals(if (status.allowsUse) PermissionRequestResult.GRANTED else PermissionRequestResult.DENIED, result.await())
            assertEquals(if (status.allowsUse) 0 else 1, denied)
            assertEquals(listOf("status", "explanation", "request", "settings", "resume"), platform.calls)
        }
    }

    @Test fun `custom keys use registered operations and unknown keys do not show UI`() = runTest {
        val calls = mutableListOf<String>()
        val registry = PermissionRegistry(Platform(PermissionStatus.UNSUPPORTED), mapOf("PHOTO_LIBRARY" to object : PermissionHandler {
            override suspend fun getStatus(): PermissionStatus { calls += "status"; return PermissionStatus.DENIED }
            override suspend fun request(): PermissionStatus { calls += "request"; return PermissionStatus.DENIED }
            override suspend fun resumeAfterSettings(): PermissionStatus { calls += "resume"; return PermissionStatus.LIMITED }
        }))
        assertEquals(PermissionRequestResult.GRANTED, registry.requestWithFlow("PHOTO_LIBRARY",
            onExplanation = { calls += "explanation"; true },
            onSettingsRequired = { calls += "settings"; true }))
        assertEquals(listOf("status", "explanation", "request", "settings", "resume"), calls)
        assertEquals(PermissionRequestResult.DENIED, registry.requestWithFlow("UNKNOWN",
            onExplanation = { error("Unknown key") }, onSettingsRequired = { error("Unknown key") }))
    }

    @Test fun `cancellation at every suspend phase propagates without continuing flow`() = runTest {
        PermissionFlowDefaults.configure(PermissionFlowHandlers(onDenied = { error("Coroutine cancellation is not denial") }))
        val phases = listOf("status", "explanation", "request", "settings", "resume")
        for ((index, phase) in phases.withIndex()) {
            val platform = Platform(PermissionStatus.DENIED, pauseAt = phase)
            val result = async {
                platform.requestWithFlow(AppPermission.CAMERA,
                    onExplanation = { platform.record("explanation"); true },
                    onSettingsRequired = { platform.record("settings"); true })
            }
            runCurrent()
            result.cancel()
            assertFailsWith<CancellationException> { result.await() }
            assertEquals(phases.take(index + 1), platform.calls)
        }
    }

    @Test fun `synchronous return after cancelling current job cannot advance flow or caller`() = runTest {
        val phases = listOf("status", "explanation", "request", "settings", "resume", "denied")
        for (phase in listOf("entry") + phases) {
            val platform = Platform(
                before = if (phase == "status") PermissionStatus.GRANTED else PermissionStatus.DENIED,
                requested = if (phase == "request") PermissionStatus.GRANTED else PermissionStatus.DENIED,
                restored = if (phase == "resume") PermissionStatus.GRANTED else PermissionStatus.DENIED,
                cancelAt = phase,
            )
            var callerContinued = false
            val result = async {
                if (phase == "entry") currentCoroutineContext().cancel()
                platform.requestWithFlow(AppPermission.CAMERA,
                    onExplanation = { platform.record("explanation"); true },
                    onSettingsRequired = { platform.record("settings"); true },
                    onDenied = { platform.record("denied") })
                callerContinued = true
            }
            runCurrent()
            assertFailsWith<CancellationException> { result.await() }
            assertFalse(callerContinued, "Caller continued after cancellation at $phase")
            assertEquals(phases.take(phases.indexOf(phase) + 1), platform.calls)
        }
    }

    @Test fun `unconfigured flow safely cancels before requesting`() = runTest {
        val platform = Platform(PermissionStatus.NOT_DETERMINED)
        assertEquals(PermissionRequestResult.CANCELLED, platform.requestWithFlow(AppPermission.CAMERA))
        assertEquals(listOf("status"), platform.calls)
    }

    @Test fun `global handlers receive built in and custom keys and final denial only once`() = runTest {
        val calls = mutableListOf<String>()
        PermissionFlowDefaults.configure(PermissionFlowHandlers(
            onExplanation = { calls += "explanation:$it"; true },
            onSettingsRequired = { calls += "settings:$it"; true },
            onDenied = { calls += "denied:$it" },
        ))
        val platform = Platform(PermissionStatus.DENIED)
        assertEquals(PermissionRequestResult.DENIED, platform.requestWithFlow(AppPermission.CAMERA))
        assertEquals(listOf("explanation:CAMERA", "settings:CAMERA", "denied:CAMERA"), calls)
        calls.clear()
        val registry = PermissionRegistry(platform, mapOf("PHOTO_LIBRARY" to object : PermissionHandler {
            override suspend fun getStatus() = PermissionStatus.DENIED
            override suspend fun request() = PermissionStatus.DENIED
            override suspend fun resumeAfterSettings() = PermissionStatus.DENIED
        }))
        assertEquals(PermissionRequestResult.DENIED, registry.requestWithFlow("PHOTO_LIBRARY"))
        assertEquals(listOf("explanation:PHOTO_LIBRARY", "settings:PHOTO_LIBRARY", "denied:PHOTO_LIBRARY"), calls)
        for (status in listOf(PermissionStatus.RESTRICTED, PermissionStatus.UNSUPPORTED)) {
            calls.clear()
            assertEquals(PermissionRequestResult.DENIED, Platform(status).requestWithFlow(AppPermission.CAMERA))
            assertEquals(listOf("denied:CAMERA"), calls)
        }
    }

    @Test fun `each call override keeps other global handlers`() = runTest {
        val calls = mutableListOf<String>()
        PermissionFlowDefaults.configure(PermissionFlowHandlers(
            onExplanation = { calls += "global explanation"; true },
            onSettingsRequired = { calls += "global settings"; true },
            onDenied = { calls += "global denied" },
        ))
        for (override in listOf("explanation", "settings", "denied")) {
            calls.clear()
            val result = Platform(PermissionStatus.DENIED).requestWithFlow(AppPermission.CAMERA,
                onExplanation = if (override == "explanation") ({ calls += "local explanation"; true }) else null,
                onSettingsRequired = if (override == "settings") ({ calls += "local settings"; true }) else null,
                onDenied = if (override == "denied") ({ calls += "local denied" }) else null)
            assertEquals(PermissionRequestResult.DENIED, result)
            assertEquals(listOf("explanation", "settings", "denied").map {
                (if (override == it) "local " else "global ") + it
            }, calls)
        }
        for (cancelAt in listOf("explanation", "settings")) {
            calls.clear()
            assertEquals(PermissionRequestResult.CANCELLED, Platform(PermissionStatus.DENIED).requestWithFlow(AppPermission.CAMERA,
                onExplanation = if (cancelAt == "explanation") ({ false }) else null,
                onSettingsRequired = if (cancelAt == "settings") ({ false }) else null))
            assertEquals(if (cancelAt == "explanation") emptyList() else listOf("global explanation"), calls)
        }
    }

    @Test fun `configuration change during status read does not change in flight handlers`() = runTest {
        val calls = mutableListOf<String>()
        fun handlers(name: String) = PermissionFlowHandlers(
            onExplanation = { calls += "$name explanation"; true },
            onSettingsRequired = { calls += "$name settings"; true },
            onDenied = { calls += "$name denied" },
        )
        PermissionFlowDefaults.configure(handlers("old"))
        val statusReady = CompletableDeferred<PermissionStatus>()
        val platform = object : PermissionPlatform {
            override suspend fun getStatus(permission: AppPermission) = statusReady.await()
            override suspend fun request(permission: AppPermission) = PermissionStatus.DENIED
            override suspend fun resumeAfterSettings(permission: AppPermission) = PermissionStatus.DENIED
        }
        val result = async { platform.requestWithFlow(AppPermission.CAMERA) }
        runCurrent()
        PermissionFlowDefaults.configure(handlers("new"))
        statusReady.complete(PermissionStatus.DENIED)
        assertEquals(PermissionRequestResult.DENIED, result.await())
        assertEquals(listOf("old explanation", "old settings", "old denied"), calls)
    }
}
