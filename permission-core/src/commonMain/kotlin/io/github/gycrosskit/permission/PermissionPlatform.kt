package io.github.gycrosskit.permission

/** 组件支持的运行时权限；宿主仍需声明实际使用的权限。 */
enum class AppPermission {
    /** 拍照、扫码等相机访问。 */
    CAMERA,
    /** 麦克风录音。 */
    MICROPHONE,
    /** 前台定位；粗略定位映射为 LIMITED。 */
    LOCATION_WHEN_IN_USE,
}

/**
 * 跨平台权限入口。
 *
 * 实现不得长期持有 Activity 或 ViewController。Android 通过当前宿主动态绑定
 * Activity Result Launcher；iOS 直接桥接系统 Framework 的异步授权 API。
 */
interface PermissionPlatform {
    /**
     * 读取系统当前状态，不弹申请框；平台实现自行调度到 UI 线程。
     * @param permission 宿主已在 Manifest/Info.plist 声明的权限。
     */
    suspend fun getStatus(permission: AppPermission): PermissionStatus

    /**
     * 执行一次由用户操作触发的系统权限申请。取消只停止等待，不能关闭系统弹窗；取消继续向上传播。
     * @param permission 本次申请的能力；已授权或受系统限制时直接返回当前状态。
     */
    suspend fun request(permission: AppPermission): PermissionStatus

    /**
     * 从应用设置返回后恢复权限。Android 需要兼容“每次询问”，可能受控发起一次系统申请；
     * iOS 可以直接读取系统状态。
     * @param permission 返回设置页后需要重新确认的权限。
     */
    suspend fun resumeAfterSettings(permission: AppPermission): PermissionStatus = getStatus(permission)
}
