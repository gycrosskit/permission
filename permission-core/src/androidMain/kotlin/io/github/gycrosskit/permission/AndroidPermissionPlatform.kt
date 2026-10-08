package io.github.gycrosskit.permission

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * shared [PermissionPlatform] 的 Android 实现。
 *
 * Manager 可以由 ViewModel 长期持有，但 Activity 只在自身组合或创建期动态 [bind]。宿主使用栈而不是单个
 * 引用：扫码等临时 Activity 覆盖主 Host 后，销毁时会自动恢复前一个宿主，避免返回主页面后权限入口失效。
 * 同一时刻只执行一个系统权限请求，Activity 销毁时会取消属于它的挂起调用，回调不会进入旧页面。
 * @param requestHistory 申请历史所有者；迁移旧宿主时注入相同实例或旧 namespace。
 */
class AndroidPermissionPlatform(
    requestHistory: AndroidPermissionRequestHistory,
) : PermissionPlatform {
    /**
     * 创建持久化历史实现，保留既有构造签名。
     * @param historyPreferencesName 默认 gycrosskit_permission_history；迁移时传宿主旧 namespace。
     */
    @JvmOverloads
    constructor(historyPreferencesName: String = PERMISSION_HISTORY_PREFERENCES) : this(
        AndroidPermissionRequestHistory(historyPreferencesName),
    )

    private class ActiveRequest(
        val host: ComponentActivity,
        val permission: AppPermission,
        val continuation: CancellableContinuation<PermissionStatus>,
    ) {
        var launcher: ActivityResultLauncher<Array<String>>? = null
        val completed = CompletableDeferred<Unit>()
    }

    private class PermissionHostDetachedException : IllegalStateException("权限宿主已销毁")

    private val hosts = MutableStateFlow<List<ComponentActivity>>(emptyList())
    private val requestMutex = Mutex()
    private val systemRequestHistory = requestHistory
    private var activeRequest: ActiveRequest? = null

    /**
     * 绑定当前权限宿主。
     *
     * Launcher 在实际申请时使用独立 key 注册，完成或 [unbind] 时注销；因此根页面可以在 Activity 已进入
     * STARTED/RESUMED 后绑定，也不会重放前一次 Activity 的权限结果。
     * @param activity 在主线程绑定的当前宿主；同一实例重复绑定会失败。
     */
    fun bind(activity: ComponentActivity) {
        check(hosts.value.none { it === activity }) { "同一 Activity 不得重复绑定权限宿主" }
        hosts.value = hosts.value + activity
    }

    /**
     * 仅移除指定 Activity；如果它是临时顶层宿主，前一个 Host 会重新成为当前权限入口。
     * 属于销毁 Activity 的未完成请求会失败并清空，防止结果回调写入已经离开的页面。
     * @param activity 主线程解绑的宿主；未绑定实例忽略。
     */
    fun unbind(activity: ComponentActivity) {
        val host = hosts.value.lastOrNull { it === activity } ?: return
        hosts.value = hosts.value.filterNot { it === host }
        val request = activeRequest
        if (request?.host === host) {
            clearRequest(request)
            if (request.continuation.isActive) {
                request.continuation.resumeWithException(PermissionHostDetachedException())
            }
        }
    }

    override suspend fun getStatus(permission: AppPermission): PermissionStatus =
        withContext(Dispatchers.Main.immediate) {
            val host = currentHost()
            host.status(
                permission = permission,
                hasRequested = host.hasRequested(permission),
            )
        }

    override suspend fun request(permission: AppPermission): PermissionStatus =
        requestMutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                // 调用方取消不能关闭系统弹窗；下次申请须等真实回调或宿主解绑，避免并行系统申请。
                activeRequest?.completed?.await()
                val host = currentHost()
                val currentStatus = host.status(
                    permission = permission,
                    hasRequested = host.hasRequested(permission),
                )
                if (currentStatus.allowsUse || currentStatus == PermissionStatus.RESTRICTED) {
                    return@withContext currentStatus
                }
                suspendCancellableCoroutine { continuation ->
                    val request = ActiveRequest(
                        host = host,
                        permission = permission,
                        continuation = continuation,
                    )
                    activeRequest = request
                    val missing = permission.androidPermissions()
                        .filterNot(host::isRuntimePermissionGranted)
                    if (missing.isEmpty()) {
                        completeRequest(request)
                    } else {
                        // 每次请求独占 key；取消无法撤回系统弹窗，迟到结果不能交给下一次请求。
                        try {
                            request.launcher = host.activityResultRegistry.register(
                                "app_permission_${System.nanoTime()}",
                                ActivityResultContracts.RequestMultiplePermissions(),
                            ) { completeRequest(request) }
                            if (continuation.isActive) {
                                request.launcher?.launch(missing.toTypedArray())
                                host.markRequested(permission)
                            } else {
                                clearRequest(request)
                            }
                        } catch (error: Exception) {
                            clearRequest(request)
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
            }
        }

    private fun completeRequest(request: ActiveRequest) {
        if (activeRequest !== request) return
        clearRequest(request)
        if (!request.continuation.isActive) return
        request.continuation.resume(
            request.host.status(
                permission = request.permission,
                hasRequested = true,
            ),
        )
    }

    private fun clearRequest(request: ActiveRequest) {
        request.launcher?.unregister()
        request.launcher = null
        if (activeRequest === request) activeRequest = null
        request.completed.complete(Unit)
    }

    override suspend fun resumeAfterSettings(permission: AppPermission): PermissionStatus {
        val current = getStatus(permission)
        return if (current == PermissionStatus.DENIED) request(permission) else current
    }

    private suspend fun currentHost(): ComponentActivity = hosts
        .map(List<ComponentActivity>::lastOrNull)
        .filterNotNull()
        .first()

    private fun ComponentActivity.status(
        permission: AppPermission,
        hasRequested: Boolean,
    ): PermissionStatus {
        val permissions = permission.androidPermissions()
        return resolveAndroidPermissionStatus(
            primaryGranted = isRuntimePermissionGranted(permissions.first()),
            alternativeGranted = permissions.drop(1).any(::isRuntimePermissionGranted),
            shouldShowRationale = shouldShowAnyPermissionRationale(permissions.asList()),
            hasRequested = hasRequested,
            policyRestricted = permissions.any { packageManager.isPermissionRevokedByPolicy(it, packageName) },
        )
    }

    private fun ComponentActivity.hasRequested(permission: AppPermission): Boolean =
        systemRequestHistory.wasRequested(this, permission.name)

    private fun ComponentActivity.markRequested(permission: AppPermission) {
        systemRequestHistory.markRequested(this, permission.name)
    }

    private fun AppPermission.androidPermissions(): Array<String> = when (this) {
        AppPermission.CAMERA -> arrayOf(Manifest.permission.CAMERA)
        AppPermission.MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
        AppPermission.LOCATION_WHEN_IN_USE -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}

/** Android 授权事实到跨端状态的纯映射，供平台实现与 JVM 测试共用。 */
internal fun resolveAndroidPermissionStatus(
    primaryGranted: Boolean,
    alternativeGranted: Boolean,
    shouldShowRationale: Boolean,
    hasRequested: Boolean,
    policyRestricted: Boolean = false,
): PermissionStatus = when {
    primaryGranted -> PermissionStatus.GRANTED
    alternativeGranted -> PermissionStatus.LIMITED
    policyRestricted -> PermissionStatus.RESTRICTED
    shouldShowRationale || hasRequested -> PermissionStatus.DENIED
    else -> PermissionStatus.NOT_DETERMINED
}

private const val PERMISSION_HISTORY_PREFERENCES = "gycrosskit_permission_history"
