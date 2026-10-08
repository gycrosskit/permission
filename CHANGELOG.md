# 更新日志

## 0.1.8（发布候选，2026-10-08）

等待原生权限请求终态再释放 owner，取消的调用方不再导致下一次请求抢占。HAR 0.1.6 沿用。

## 0.1.7（2026-10-08）

- Android设备策略限制返回RESTRICTED；设置恢复已决定状态不重复请求；整理平台差异。
- 更新功能、测试覆盖与平台差异文档；设备业务验收范围保持明确。

## 0.1.4（待发布）

iOS 定位权限等待被后台取消时，continuation 身份判断与清理排回 Main。补齐 Android 生命周期绑定和共享权限历史的线程合同；Maven 制品与远程消费待验，HAR 保持 `0.1.2`。

## 0.1.3

修复 Kuikly 回调已完成但协程尚未消费时页面销毁的迟交付；iOS 权限查询/申请内部使用主线程，定位 manager 在主线程延迟创建。移除未使用的 Compose 构建依赖，并补齐新 POM 的 Apache-2.0 元数据。

| 渠道 | 本轮版本 | 状态 |
| --- | --- | --- |
| Maven core/Kuikly | 0.1.3 | JitPack 全文件/hash 与 Android/OHOS/三 iOS 编译、Simulator 链接通过 |
| HarmonyOS HAR | 0.1.2 | 源码未变，沿用旧 Release 已验产物；OHPM 仍需核验审核结果 |


历史版本与验证范围见 [Releases](https://github.com/gycrosskit/permission/releases)；真实消费与 Registry 状态见本轮发布验收。
