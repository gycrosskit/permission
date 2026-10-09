package io.github.gycrosskit.permission.kuikly

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.PageData
import io.github.gycrosskit.permission.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionModuleHandlerTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private class Platform : PermissionPlatform {
        val waits = mutableListOf<CompletableDeferred<PermissionStatus>>()
        var resumed = 0
        override suspend fun getStatus(permission: AppPermission) = PermissionStatus.LIMITED
        override suspend fun request(permission: AppPermission): PermissionStatus {
            val wait = CompletableDeferred<PermissionStatus>()
            waits += wait
            return wait.await()
        }
        override suspend fun resumeAfterSettings(permission: AppPermission): PermissionStatus {
            resumed++
            return PermissionStatus.DENIED
        }
    }
    private fun args(id: String) = "{\"requestId\":\"$id\",\"permission\":\"CAMERA\"}"

    @Test fun `cancel old renderer request and late terminal cannot deliver into successor`() = runTest {
        val platform = Platform()
        val old = PermissionModuleHandler(platform)
        val fresh = PermissionModuleHandler(platform)
        val replies = mutableListOf<String>()
        old.call("request", args("same")) { replies += "old:$it" }
        runCurrent()
        old.dispose()
        fresh.call("request", args("same")) { replies += "new:$it" }
        runCurrent()
        platform.waits[0].complete(PermissionStatus.GRANTED)
        runCurrent()
        assertTrue(replies.isEmpty())
        platform.waits[1].complete(PermissionStatus.RESTRICTED)
        runCurrent()
        assertEquals(listOf("new:{\"status\":\"RESTRICTED\"}"), replies)
        fresh.dispose()
    }

    @Test fun `native status and settings recovery delegate to actual owner while invalid input does not`() = runTest {
        val platform = Platform()
        val handler = PermissionModuleHandler(platform)
        val replies = mutableListOf<String>()
        handler.call("getStatus", args("status"), replies::add)
        handler.call("resumeAfterSettings", args("resume"), replies::add)
        handler.call("request", "{\"requestId\":\"bad\",\"permission\":\"OTHER\"}", replies::add)
        runCurrent()
        assertEquals(setOf("LIMITED", "DENIED", "error"), replies.map { JSONObject(it).optString("status") }.toSet())
        assertEquals(1, platform.resumed)
        assertTrue(platform.waits.isEmpty())
        handler.dispose()
    }

    @Test fun `cancelQueued only cancels matching ID and leaves other request live`() = runTest {
        val platform = Platform()
        val handler = PermissionModuleHandler(platform)
        val replies = mutableListOf<String>()
        handler.call("request", args("old"), replies::add)
        handler.call("request", args("new"), replies::add)
        runCurrent()
        handler.call("cancelQueued", args("old"), replies::add)
        runCurrent()
        platform.waits[0].complete(PermissionStatus.GRANTED)
        platform.waits[1].complete(PermissionStatus.DENIED)
        runCurrent()
        assertEquals(listOf("DENIED"), replies.map { JSONObject(it).optString("status") })
        handler.dispose()
    }

    @Test fun `Android and iOS caller use native settings recovery rather than OHOS policy`() = runTest {
        for (ios in listOf(false, true)) {
            val module = PermissionModule().apply { pageData = PageData(isAndroid = !ios, isIOS = ios) }
            val result = async { module.resumeAfterSettings(AppPermission.CAMERA) }
            runCurrent()
            assertEquals("resumeAfterSettings", module.calls.single().first)
            module.response(JSONObject().apply { put("status", "DENIED") })
            assertEquals(PermissionStatus.DENIED, result.await())
            module.dispose()
        }
    }
}
