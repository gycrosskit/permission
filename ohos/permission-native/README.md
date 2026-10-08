# @gycrosskit/permission-native

适用版本：此版 Maven `0.1.7` 复用已发布 HAR `0.1.6` 的原字节。完整功能与五入口限制见[功能与平台差异](https://github.com/gycrosskit/permission/blob/0.1.7/docs/功能与平台差异.md)；本版发布记录见[Release](https://github.com/gycrosskit/permission/releases/tag/0.1.7)。此源码 README 的文档更新不重新发布或修改既有 HAR。

HAR `0.1.6` 已发布；Registry 精确安装与固定 Release HAR 消费分别验收，不以一个渠道代替另一个。

相机、麦克风和前台定位权限状态与申请。当前 HAR target/compatible SDK 为 HarmonyOS API 22。

已公开导出 GycPermission / GycPermissionStatus；既有渠道历史状态见根 README。Android 运行时权限执行器属于 Maven permission-core，不在本 HAR。

```sh
ohpm install @gycrosskit/permission-native@0.1.6
```

```typescript
import { GycPermissionService } from '@gycrosskit/permission-native';
const service = new GycPermissionService();
const status = await service.request(context, 'CAMERA');
```

宿主提供 UIAbilityContext、权限声明和申请时机。状态为 NOT_DETERMINED/GRANTED/LIMITED/DENIED/RESTRICTED。Kuikly 注册 GycPermissionModule，页面结束时 Kotlin PermissionModule.dispose()；不提供后台定位或设置页跳转。

[完整接入指南](https://github.com/gycrosskit/permission/blob/main/docs/接入指南.md) · [开发与验证](https://github.com/gycrosskit/permission/blob/main/docs/开发与验证.md) · [版本](https://github.com/gycrosskit/permission/releases) · [问题反馈](https://github.com/gycrosskit/permission/issues)。

Apache-2.0，见 [LICENSE](LICENSE)。
