# 更新日志

## 2.0.0（待发布）

- 新增 `GycPermissionHandler`、宿主能力 key 与 Service/Module 注入，未注册能力返回 `UNSUPPORTED`；保留内置权限和无参构造调用。
- `GycPermissionStatus` 联合类型新增 `UNSUPPORTED`，穷举映射需更新；与 Maven `2.0.0` 配套，真实 SDK 构建、发布及 Registry 消费另验。
- 三方法透传自定义能力，排队取消与已显示系统弹窗的真实回执屏障保持。

## 1.0.0（2026-10-26）

- Maven、iOS Pod 与 OHPM 统一版本为 1.0.0；同仓模块使用同版，Kuikly 配套保持 2.29.1。
- 本地源码/产物消费与设备、远程发布验收分别记录；不覆盖旧标签或修改历史发布字节。

## 0.1.6（待发布候选）

跨 Page 系统授权串行，排队项通过 requestId/cancelQueued 撤销；已展示弹窗继续等待真实回执。保留无 requestId 的 legacy 调用。

## 0.1.5

根入口补导出公开签名依赖的 GycPermission、GycPermissionStatus；服务与 Kuikly Module 保持。

## 0.1.2（候选）

- 与 permission-core/permission-kuikly 0.1.2 对齐；原生权限状态、申请时机和 Kuikly 名称保持不变。
- Android 运行时权限执行和可注入历史在 Maven permission-core 中提供，不由 HAR 实现。

## 0.1.1

- 发布包内补充 README 和安装命令，满足 ohpm 提交要求。

## 0.1.0

- 相机、麦克风、前台定位的权限状态与申请。
- 返回设置页后可重新读取系统状态；支持粗略定位状态。
- 提供 Kuikly 薄桥，页面销毁后不再投递回调。
