# @gycrosskit/permission-native

> iOS Git Pod `1.0.1`（`native-1.0.1`），私有 OHPM `2.0.1`，Maven `2.0.0`。发布与精确消费状态见对应发布回执。

鸿蒙包通过私有 OHPM 发布，使用前核对精确版本 Jenkins 回执与 Registry 安装结果。

相机、麦克风和前台定位权限状态与申请。当前 HAR target/compatible SDK 为 HarmonyOS API 22。

已公开导出 GycPermission / GycPermissionStatus / GycPermissionHandler；既有渠道历史状态见根 README。Android 运行时权限执行器属于 Maven permission-core，不在本 HAR。

```sh
ohpm install @gycrosskit/permission-native@2.0.1
```

```typescript
import { GycPermissionService } from '@gycrosskit/permission-native';
const service = new GycPermissionService();
const status = await service.request(context, 'CAMERA');
```

宿主提供 UIAbilityContext、权限声明和申请时机。状态为 NOT_DETERMINED/GRANTED/LIMITED/DENIED/RESTRICTED/UNSUPPORTED。扩展能力 key 通过 `new GycPermissionService(handlers)` 注册，未注册返回 UNSUPPORTED，不能覆盖内置 key；每页 receiver 可用无参宿主子类 `super(service)` 注入，具体 API 见接入指南的“宿主扩展能力”。Kuikly 注册 GycPermissionModule，页面结束时 Kotlin PermissionModule.dispose()；不提供后台定位或设置页跳转。

[完整接入指南](https://github.com/gycrosskit/permission/blob/main/docs/接入指南.md) · [开发与验证](https://github.com/gycrosskit/permission/blob/main/docs/开发与验证.md) · [版本](https://github.com/gycrosskit/permission/releases) · [问题反馈](https://github.com/gycrosskit/permission/issues)。

Apache-2.0，见 [LICENSE](LICENSE)。
