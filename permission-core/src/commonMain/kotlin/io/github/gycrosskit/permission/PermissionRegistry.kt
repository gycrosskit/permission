package io.github.gycrosskit.permission

/** 一个宿主能力的原生实现；负责线程、系统声明、取消和仍在显示的系统弹窗屏障。 */
interface PermissionHandler {
    suspend fun getStatus(): PermissionStatus
    suspend fun request(): PermissionStatus
    suspend fun resumeAfterSettings(): PermissionStatus = getStatus()
}

/** 复用既有平台 owner，按能力 key 注入宿主实现；注册表无权限状态缓存。 */
class PermissionRegistry(
    private val platform: PermissionPlatform,
    handlers: Map<String, PermissionHandler>,
) : PermissionPlatform {
    private val handlers = handlers.toMap()

    init {
        require(handlers.keys.none { key -> key.isBlank() || AppPermission.entries.any { it.name == key } }) {
            "Permission keys must be non-blank and must not replace built-in permissions"
        }
        require(handlers.keys.none { key -> (platform as? PermissionRegistry)?.hasHandler(key) == true }) {
            "Permission keys must not replace existing registrations"
        }
    }

    private fun hasHandler(key: String): Boolean =
        key in handlers || (platform as? PermissionRegistry)?.hasHandler(key) == true

    override suspend fun getStatus(permission: AppPermission): PermissionStatus = platform.getStatus(permission)
    override suspend fun request(permission: AppPermission): PermissionStatus = platform.request(permission)
    override suspend fun resumeAfterSettings(permission: AppPermission): PermissionStatus = platform.resumeAfterSettings(permission)

    override suspend fun getStatus(permission: String): PermissionStatus =
        handlers[permission]?.getStatus() ?: platform.getStatus(permission)

    override suspend fun request(permission: String): PermissionStatus =
        handlers[permission]?.request() ?: platform.request(permission)

    override suspend fun resumeAfterSettings(permission: String): PermissionStatus =
        handlers[permission]?.resumeAfterSettings() ?: platform.resumeAfterSettings(permission)
}
