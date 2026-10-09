package io.github.gycrosskit.permission.kuikly

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import io.github.gycrosskit.permission.AppPermission
import io.github.gycrosskit.permission.PermissionPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 每个 Renderer 独占；platform 必须复用宿主的权限 owner，取消不解除原生弹窗屏障。 */
class PermissionModuleHandler(private val platform: PermissionPlatform) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = mutableMapOf<String, Job>()

    fun call(method: String, params: String, callback: (String) -> Unit) {
        scope.launch {
            val args = try { JSONObject(params) } catch (_: Exception) {
                callback("{\"status\":\"error\"}")
                return@launch
            }
            val id = (args.opt("requestId") as? String)?.takeIf { it.isNotBlank() }
            if (method == "cancelQueued") {
                id?.let { requests.remove(it)?.cancel() }
                return@launch
            }
            val permission = AppPermission.entries.firstOrNull { it.name == args.opt("permission") }
            if (id == null || permission == null || requests.containsKey(id) ||
                method !in listOf("getStatus", "request", "resumeAfterSettings")) {
                callback("{\"status\":\"error\"}")
                return@launch
            }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val response = try {
                    val status = when (method) {
                        "request" -> platform.request(permission)
                        "resumeAfterSettings" -> platform.resumeAfterSettings(permission)
                        else -> platform.getStatus(permission)
                    }
                    JSONObject().apply { put("status", status.name) }.toString()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    "{\"status\":\"error\"}"
                } finally {
                    if (requests[id] === coroutineContext[Job]) requests.remove(id)
                }
                if (isActive) callback(response)
            }
            requests[id] = job
            job.start()
        }
    }

    /** 只撤销当前 Renderer 的等待与交付，权限 owner 由宿主解绑。 */
    fun dispose() { scope.cancel() }
}
