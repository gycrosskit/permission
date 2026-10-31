package io.github.gycrosskit.permission

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow

/** 一次权限交互流程的结果，不替代系统权限状态。 */
enum class PermissionRequestResult { GRANTED, DENIED, CANCELLED }

/** 应用统一的权限交互；hook 应通过宿主当前 UI 服务展示，不捕获页面或 Activity。 */
data class PermissionFlowHandlers(
    val onExplanation: suspend (String) -> Boolean = { false },
    val onSettingsRequired: suspend (String) -> Boolean = { false },
    val onDenied: suspend (String) -> Unit = {},
)

/** 应用初始化时配置；只保存交互，不缓存授权状态。在途流程保留入口时的配置快照。 */
object PermissionFlowDefaults {
    private val handlers = MutableStateFlow(PermissionFlowHandlers())

    fun configure(handlers: PermissionFlowHandlers) { this.handlers.value = handlers }

    internal fun snapshot(): PermissionFlowHandlers = handlers.value
}

/**
 * 读取状态、说明并实际申请；只有进入流程前已拒绝且重试仍拒绝时才询问设置恢复。
 * 单次 hook 逐项覆盖 [PermissionFlowDefaults]；未配置说明时安全取消，不静默申请。
 * [onExplanation] 说明本次用途并等待确认，false 取消本次流程。
 * [onSettingsRequired] 由宿主确认、打开设置并等待实际返回后返回 true；false 取消设置流程。
 * [onDenied] 仅在最终结果为 DENIED 时调用一次，CANCELLED 不调用。
 * 协程取消和平台异常向上传播；完整或受限授权都返回 [PermissionRequestResult.GRANTED]。
 */
suspend fun PermissionPlatform.requestWithFlow(
    permission: AppPermission,
    onExplanation: (suspend () -> Boolean)? = null,
    onSettingsRequired: (suspend () -> Boolean)? = null,
    onDenied: (suspend () -> Unit)? = null,
): PermissionRequestResult = runPermissionRequestFlow(
    permission.name, { getStatus(permission) }, { request(permission) }, { resumeAfterSettings(permission) },
    onExplanation, onSettingsRequired, onDenied,
)

/** 与 [AppPermission] 重载相同；[permission] 是已注册的能力 key。 */
suspend fun PermissionPlatform.requestWithFlow(
    permission: String,
    onExplanation: (suspend () -> Boolean)? = null,
    onSettingsRequired: (suspend () -> Boolean)? = null,
    onDenied: (suspend () -> Unit)? = null,
): PermissionRequestResult = runPermissionRequestFlow(
    permission, { getStatus(permission) }, { request(permission) }, { resumeAfterSettings(permission) },
    onExplanation, onSettingsRequired, onDenied,
)

private suspend fun runPermissionRequestFlow(
    permissionKey: String,
    getStatus: suspend () -> PermissionStatus,
    request: suspend () -> PermissionStatus,
    resumeAfterSettings: suspend () -> PermissionStatus,
    onExplanation: (suspend () -> Boolean)?,
    onSettingsRequired: (suspend () -> Boolean)?,
    onDenied: (suspend () -> Unit)?,
): PermissionRequestResult {
    val handlers = PermissionFlowDefaults.snapshot()
    suspend fun denied(): PermissionRequestResult {
        currentCoroutineContext().ensureActive()
        if (onDenied != null) onDenied() else handlers.onDenied(permissionKey)
        return PermissionRequestResult.DENIED
    }
    val before = getStatus()
    if (before.allowsUse) return PermissionRequestResult.GRANTED
    if (before != PermissionStatus.NOT_DETERMINED && before != PermissionStatus.DENIED) {
        return denied()
    }
    if (!(onExplanation?.invoke() ?: handlers.onExplanation(permissionKey))) return PermissionRequestResult.CANCELLED
    val requested = request()
    if (requested.allowsUse) return PermissionRequestResult.GRANTED
    if (before != PermissionStatus.DENIED || requested != PermissionStatus.DENIED) {
        return denied()
    }
    if (!(onSettingsRequired?.invoke() ?: handlers.onSettingsRequired(permissionKey))) return PermissionRequestResult.CANCELLED
    return if (resumeAfterSettings().allowsUse) PermissionRequestResult.GRANTED else denied()
}
