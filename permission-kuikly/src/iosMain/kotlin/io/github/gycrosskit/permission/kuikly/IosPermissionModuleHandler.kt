package io.github.gycrosskit.permission.kuikly

import io.github.gycrosskit.permission.PermissionPlatform

/** 从宿主既有 Shared framework 导出，供原生 Kuikly receiver 调用；不创建额外 runtime。 */
class IosPermissionModuleHandler(platform: PermissionPlatform) {
    private val handler = PermissionModuleHandler(platform)
    fun call(method: String, params: String, callback: (String) -> Unit) = handler.call(method, params, callback)
    fun dispose() = handler.dispose()
}
