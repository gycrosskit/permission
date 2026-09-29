package io.github.gycrosskit.permission.kuikly

import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import io.github.gycrosskit.permission.AppPermission
import io.github.gycrosskit.permission.PermissionPlatform
import io.github.gycrosskit.permission.PermissionStatus
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Bind one instance to each Kuikly page and call [dispose] when that page is destroyed. */
class PermissionModule : Module(), PermissionPlatform {
    private val requestMutex = Mutex()
    private val pending = mutableSetOf<CancellableContinuation<JSONObject?>>()
    private var disposed = false

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
        var reference: CallbackRef? = null
        var continuation: CancellableContinuation<JSONObject?>? = null
        try {
            return suspendCancellableCoroutine { result ->
                continuation = result
                pending += result
                reference = toNative(false, method, params.toString(), { response ->
                    if (result.isActive) result.resume(response)
                }, false).callbackRef
            }
        } finally {
            continuation?.let { pending.remove(it) }
            reference?.let(::removeCallback)
        }
    }

    fun dispose() {
        disposed = true
        pending.toList().forEach { it.cancel() }
        pending.clear()
    }

    companion object { const val NAME = "GycPermissionModule" }
}
