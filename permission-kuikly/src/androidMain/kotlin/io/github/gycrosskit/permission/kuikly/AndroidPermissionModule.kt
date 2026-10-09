package io.github.gycrosskit.permission.kuikly

import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import io.github.gycrosskit.permission.PermissionPlatform

/** SDK 每个 Renderer 创建一个实例；原生能力 owner 由宿主维护。 */
class AndroidPermissionModule(platform: PermissionPlatform) : KuiklyRenderBaseModule() {
    private val handler = PermissionModuleHandler(platform)

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        handler.call(method, params.orEmpty()) { callback?.invoke(it) }
        return null
    }

    override fun onDestroy() {
        handler.dispose()
        super.onDestroy()
    }
}

/** 在 registerExternalModule 中注册；factory 不能复用 receiver 实例。 */
fun IKuiklyRenderExport.registerPermissionModule(platform: PermissionPlatform) {
    moduleExport(PermissionModule.NAME) { AndroidPermissionModule(platform) }
}
