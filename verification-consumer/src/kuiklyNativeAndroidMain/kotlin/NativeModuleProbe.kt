import io.github.gycrosskit.permission.kuikly.registerPermissionModule

fun registerKuiklyPermission(exports: com.tencent.kuikly.core.render.android.IKuiklyRenderExport, platform: io.github.gycrosskit.permission.PermissionPlatform) = exports.registerPermissionModule(platform)
