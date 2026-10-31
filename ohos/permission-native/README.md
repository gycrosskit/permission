# @gycrosskit/permission-native

> 当前 HAR 候选 **2.0.0** 尚未发布，配套 Maven `2.0.0` 的能力 key/状态协议。新增 `UNSUPPORTED` 后，穷举状态映射须补齐；未注册能力不引导设置恢复。

下方 Registry 命令须在精确版本上架后执行；提交审核、固定 Release HAR 下载与 Registry 安装分别核验。既有 [HAR 1.0.0](https://github.com/gycrosskit/permission/releases/tag/har-1.0.0) 保留原 Tag 和字节，不包含本轮扩展。

相机、麦克风和前台定位权限状态与申请。当前 HAR target/compatible SDK 为 HarmonyOS API 22。

已公开导出 GycPermission / GycPermissionStatus / GycPermissionHandler；既有渠道历史状态见根 README。Android 运行时权限执行器属于 Maven permission-core，不在本 HAR。

```sh
ohpm install @gycrosskit/permission-native@2.0.0
```

```typescript
import { GycPermissionService } from '@gycrosskit/permission-native';
const service = new GycPermissionService();
const status = await service.request(context, 'CAMERA');
```

宿主提供 UIAbilityContext、权限声明和申请时机。状态为 NOT_DETERMINED/GRANTED/LIMITED/DENIED/RESTRICTED/UNSUPPORTED。扩展能力 key 通过 `new GycPermissionService(handlers)` 注册，未注册返回 UNSUPPORTED，不能覆盖内置 key；每页 receiver 可用无参宿主子类 `super(service)` 注入，具体 API 见接入指南的“宿主扩展能力”。Kuikly 注册 GycPermissionModule，页面结束时 Kotlin PermissionModule.dispose()；不提供后台定位或设置页跳转。

[完整接入指南](https://github.com/gycrosskit/permission/blob/main/docs/接入指南.md) · [开发与验证](https://github.com/gycrosskit/permission/blob/main/docs/开发与验证.md) · [版本](https://github.com/gycrosskit/permission/releases) · [问题反馈](https://github.com/gycrosskit/permission/issues)。

Apache-2.0，见 [LICENSE](LICENSE)。
