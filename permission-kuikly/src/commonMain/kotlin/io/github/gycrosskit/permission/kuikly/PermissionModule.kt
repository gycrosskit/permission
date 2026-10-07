package io.github.gycrosskit.permission.kuikly

import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import io.github.gycrosskit.permission.AppPermission
import io.github.gycrosskit.permission.PermissionPlatform
import io.github.gycrosskit.permission.PermissionStatus
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.random.Random

/** 每个 Kuikly Page 一个实例，所有调用和 [dispose] 在同一页面协程上下文执行；桥回执等待最多 120 秒。 */
class PermissionModule : Module(), PermissionPlatform {
    private val requestMutex = Mutex()
    private val pending = mutableSetOf<CancellableContinuation<JSONObject?>>()
    private var disposed = false
    private val requestPrefix = Random.nextLong().toString()
    private var sequence = 0L

    override fun moduleName(): String = NAME

    override suspend fun getStatus(permission: AppPermission): PermissionStatus = status("getStatus", permission)

    override suspend fun request(permission: AppPermission): PermissionStatus = requestMutex.withLock {
        status("request", permission)
    }

    override suspend fun resumeAfterSettings(permission: AppPermission): PermissionStatus {
        val current = getStatus(permission)
        return if (current == PermissionStatus.NOT_DETERMINED) request(permission) else current
    }

    private suspend fun status(method: String, permission: AppPermission): PermissionStatus {
        val response = withTimeout(120_000L) {
            invoke(method, JSONObject().apply { put("permission", permission.name) })
        }
        return PermissionStatus.entries.firstOrNull { it.name == response?.optString("status") }
            ?: error("Harmony permission service unavailable")
    }

    private suspend fun invoke(method: String, params: JSONObject): JSONObject? {
        check(!disposed) { "PermissionModule is disposed" }
        val requestId = "${requestPrefix}_${++sequence}"
        params.put("requestId", requestId)
        var completed = false
        var reference: CallbackRef? = null
        var continuation: CancellableContinuation<JSONObject?>? = null
        try {
            val response = suspendCancellableCoroutine<JSONObject?> { result ->
                continuation = result
                pending += result
                reference = toNative(false, method, params.toString(), { response ->
                    completed = true
                    if (result.isActive) result.resume(response)
                }, false).callbackRef
            }
            // resume 后仍可能等待调度；销毁必须阻止这段窗口内的旧结果交付。
            if (disposed) throw CancellationException("PermissionModule is disposed")
            return response
        } finally {
            continuation?.let { pending.remove(it) }
            // 只能撤销还在原生队列中的申请；已经展示的系统弹窗继续持有全局屏障。
            if (method == "request" && !completed) {
                asyncToNativeMethod("cancelQueued", JSONObject().apply { put("requestId", requestId) }, null)
            }
            reference?.let(::removeCallback)
        }
    }

    /** 页面销毁时调用；幂等，取消所有挂起调用并释放回调，不关闭系统权限弹窗。 */
    fun dispose() {
        disposed = true
        pending.toList().forEach { it.cancel() }
        pending.clear()
    }

    companion object { /** 与原生注册名一致的桥名称。 */ const val NAME = "GycPermissionModule" }
}
