package io.github.gycrosskit.permission

/** 组件支持的运行时权限；宿主仍需声明实际使用的权限。 */
enum class AppPermission {
    CAMERA,
    MICROPHONE,
    LOCATION_WHEN_IN_USE,
}

/**
 * 跨平台权限入口。
 *
 * 实现不得长期持有 Activity 或 ViewController。Android 通过当前宿主动态绑定
 * Activity Result Launcher；iOS 直接桥接系统 Framework 的异步授权 API。
 */
interface PermissionPlatform {
    suspend fun getStatus(permission: AppPermission): PermissionStatus

    /**
     * 执行一次由用户操作触发的系统权限申请。
     */
    suspend fun request(permission: AppPermission): PermissionStatus

    /**
     * 从应用设置返回后恢复权限。Android 需要兼容“每次询问”，可能受控发起一次系统申请；
     * iOS 可以直接读取系统状态。
     */
    suspend fun resumeAfterSettings(permission: AppPermission): PermissionStatus = getStatus(permission)
}
