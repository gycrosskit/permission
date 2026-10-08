# @gycrosskit/permission-native

2026-10-08 当前源码与三端/五入口边界见[功能与平台差异](../../docs/功能与平台差异.md)；本包只承担上文所述原生能力，以下版本和渠道记录按各自日期阅读。

本轮 HAR 候选为 `0.1.5`，尚未发布；下面精确安装命令用于发布并确认可见后，历史验收不代表本候选已验收。

相机、麦克风和前台定位权限状态与申请。当前 HAR target/compatible SDK 为 HarmonyOS API 22。

本候选新增公开导出 GycPermission / GycPermissionStatus；既有渠道历史状态见根 README。Android 运行时权限执行器属于 Maven permission-core，不在本 HAR。

```sh
ohpm install @gycrosskit/permission-native@0.1.5
```

```typescript
import { GycPermissionService } from '@gycrosskit/permission-native';
const service = new GycPermissionService();
const status = await service.request(context, 'CAMERA');
```

宿主提供 UIAbilityContext、权限声明和申请时机。状态为 NOT_DETERMINED/GRANTED/LIMITED/DENIED/RESTRICTED。Kuikly 注册 GycPermissionModule，页面结束时 Kotlin PermissionModule.dispose()；不提供后台定位或设置页跳转。

[完整接入指南](https://github.com/gycrosskit/permission/blob/main/docs/接入指南.md) · [开发与验证](https://github.com/gycrosskit/permission/blob/main/docs/开发与验证.md) · [版本](https://github.com/gycrosskit/permission/releases) · [问题反馈](https://github.com/gycrosskit/permission/issues)。

Apache-2.0，见 [LICENSE](LICENSE)。
