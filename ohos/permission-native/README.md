# @gycrosskit/permission-native

HarmonyOS 相机、麦克风与前台定位权限服务，提供 `GycPermissionService` 和 `GycPermissionModule`。应用在 `module.json5` 中声明实际使用的权限，并由页面决定申请时机及提示文案。

上架后可用 `ohpm install @gycrosskit/permission-native@0.1.1` 安装。`GycPermissionModule` 的 `MODULE_NAME` 是 `GycPermissionModule`，宿主需注册到 Kuikly。HAR 本地构建通过不代表 ohpm 已审核上架。

构建：在 `ohos/` 执行 `hvigorw assembleHar --no-daemon`；提交前执行 `ohpm prepublish`。
