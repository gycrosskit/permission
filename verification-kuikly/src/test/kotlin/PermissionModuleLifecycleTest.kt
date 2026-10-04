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
    }
}
