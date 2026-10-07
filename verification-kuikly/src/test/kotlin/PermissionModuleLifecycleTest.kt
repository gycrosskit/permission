package io.github.gycrosskit.permission.kuikly
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import io.github.gycrosskit.permission.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import kotlin.test.*
@OptIn(ExperimentalCoroutinesApi::class)
class PermissionModuleLifecycleTest {
    @Test fun `cancelled request releases serialization and ignores its late result`() = runTest {
        val module = PermissionModule()
        val first = async { module.request(AppPermission.CAMERA) }
        runCurrent()
        val oldResponse = module.response
        val second = async { module.request(AppPermission.MICROPHONE) }
        runCurrent()
        assertEquals(1, module.calls.size)
        first.cancel()
        runCurrent()
        assertEquals(2, module.calls.size)
        assertEquals(1, module.removedCallbacks)
        assertEquals(1, module.cancelled.size)
        assertTrue(module.calls.first().second.contains(module.cancelled.single().optString("requestId")))
        oldResponse(JSONObject().apply { put("status", "GRANTED") })
        runCurrent()
        assertFalse(second.isCompleted)
        module.response(JSONObject().apply { put("status", "DENIED") })
        assertEquals(PermissionStatus.DENIED, second.await())
        assertEquals(2, module.removedCallbacks)
    }

    @Test fun `settings recovery requests only undetermined permission`() = runTest {
        val module = PermissionModule()
        val result = async { module.resumeAfterSettings(AppPermission.CAMERA) }
        runCurrent()
        module.response(JSONObject().apply { put("status", "NOT_DETERMINED") })
        runCurrent()
        assertEquals(listOf("getStatus", "request"), module.calls.map { it.first })
        module.response(JSONObject().apply { put("status", "GRANTED") })
        assertEquals(PermissionStatus.GRANTED, result.await())
        assertEquals(2, module.removedCallbacks)
    }

    @Test fun `dispose between callback and dispatch cancels all permission paths`() = runTest {
        for (method in listOf("getStatus", "request", "resumeAfterSettings")) {
            val module = PermissionModule()
            val result = async { when (method) {
                "getStatus" -> module.getStatus(AppPermission.CAMERA)
                "request" -> module.request(AppPermission.CAMERA)
                else -> module.resumeAfterSettings(AppPermission.CAMERA)
            } }
            runCurrent()
            module.response(JSONObject().apply { put("status", "GRANTED") })
            module.dispose()
            runCurrent()
            assertFailsWith<CancellationException> { result.await() }
            assertEquals(1, module.removedCallbacks)
        }
    }
    @Test fun `live permission result preserves limited status`() = runTest {
        val module = PermissionModule()
        val result = async { module.request(AppPermission.LOCATION_WHEN_IN_USE) }
        runCurrent()
        module.response(JSONObject().apply { put("status", "LIMITED") })
        assertEquals(PermissionStatus.LIMITED, result.await())
        assertEquals(1, module.removedCallbacks)
        assertTrue(module.cancelled.isEmpty(), "completed result does not send a cancellation")
    }
}
