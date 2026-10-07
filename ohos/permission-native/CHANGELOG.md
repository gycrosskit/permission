# 更新日志

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
