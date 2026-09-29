package io.github.gycrosskit.permission

/**
 * 跨平台权限状态。
 *
 * Android、iOS 与鸿蒙的系统枚举不同，平台实现负责系统弹窗和设置页恢复差异。
 */
enum class PermissionStatus {
    /** 尚未向用户申请，页面可以先展示用途说明。 */
    NOT_DETERMINED,

    /** 已获得功能需要的完整权限。 */
    GRANTED,

    /** 已获得可继续使用的受限权限，例如粗略定位。 */
    LIMITED,

    /** 系统权限已经被用户拒绝；宿主可引导用户前往设置页。 */
    DENIED,

    /** 权限被系统策略或家长控制限制，应用自身无法恢复。 */
    RESTRICTED,
    ;

    /** 完整或受限授权都可继续，具体能力差异由宿主决定。 */
    val allowsUse: Boolean
        get() = this == GRANTED || this == LIMITED
}
