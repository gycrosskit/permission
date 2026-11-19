# GY CrossKit Permission

新版 Maven 发布由 Jenkins 生成同版本归档、`source-receipt.json` 和 `SHA256SUMS`。JitPack 核对标签来源、归档尺寸和摘要后安装同一份制品。历史发布按原版本 Tag 复验。

Kuikly 由宿主统一选择版本；组件构建基线、允许范围及消费验证边界见[功能与平台差异](docs/功能与平台差异.md#kuikly-版本与宿主选择)。

> 统一版本候选 `2.0.1`：Maven 与 iOS Git Pod（`native-2.0.1`）待发布；私有 OHPM 沿用已发布的 `2.0.1`，不重发。Native receiver 与 `native-1.0.1` 源码相同，版本号统一不改变公开 API，也不表示各平台 API 完全相同；新渠道构建与公开消费另验。

本轮只补齐 Maven/Git Pod 版本；Git Pod 不代表 CocoaPods Specs 发布。既有 [Maven 1.0.0](https://github.com/gycrosskit/permission/releases/tag/1.0.0) 与 [HAR 1.0.0](https://github.com/gycrosskit/permission/releases/tag/har-1.0.0) 保留原 Tag 和字节；OHPM Registry 是否可安装须独立核验。

## 当前功能与平台边界

core 内置相机、麦克风、前台定位，提供六种权限状态（含未注册能力的 `UNSUPPORTED`）及申请/恢复；不提供页面 UI，permission-kuikly包含三端 Module 与 Android/iOS receiver，复用宿主已有原生 owner。

当前测试覆盖、执行时点和未验收项集中见[验证范围](docs/功能与平台差异.md#验证范围)，复现命令见[开发与验证](docs/开发与验证.md)。

[历史完整源码审查](docs/完整源码审查.md) 列出全部生产文件、公开调用链、实际验证与未测项。

相机、麦克风和前台定位的权限状态查询与申请。宿主可通过 `PermissionRegistry` / `PermissionHandler` 注册其它能力 key，旧 `AppPermission` 调用保持兼容；Kuikly 三端透传同一 key，未注册返回 `UNSUPPORTED`。key 表示能力，不是跨平台 Manifest 权限串，注册与各端原生实现见[接入指南](docs/接入指南.md#宿主扩展能力)。宿主负责申请时机、说明文案、系统权限声明和应用设置页跳转。

此版本将 Android `isPermissionRevokedByPolicy` 映射为 `RESTRICTED`；已有完整/粗略授权优先，设备策略限制不引导为普通设置恢复。

## 平台与要求

| 平台 | 接入方式 | 系统要求 |
| --- | --- | --- |
| Android | KMP `permission-core`，`AndroidPermissionPlatform` | API 24+，`ComponentActivity` |
| iOS | KMP `permission-core`，`IosPermissionPlatform` | iOS 14+（定位精度状态使用 iOS 14 API），无独立 Swift Package |
| HarmonyOS | `permission-kuikly` + 原生 HAR，或 ArkTS 直接使用 HAR | 当前 HAR 的 target/compatible SDK 均为 API 22 |

KMP 产物使用 Kotlin `2.2.21-1.0.0`、coroutines `1.10.2-1.0.0`；Kuikly 为 `2.29.1-2.0.21-ohos`。OHOS 宿主需要匹配的 Kotlin 工具链，完整仓库配置见接入指南。

## 架构与调用流程

`PermissionPlatform` 统一状态查询、申请与设置恢复，Android/iOS 使用宿主原生 owner，OHOS 经 HAR 服务与 `GycPermissionModule` 接线。`PermissionRegistry` 按能力 key 扩展，宿主负责申请时机和说明 UI。

系统弹窗的真实回执屏障、状态差异和取消合同见[功能与平台差异](docs/功能与平台差异.md)，接线见[接入指南](docs/接入指南.md)。源码职责和关系统一由 [AOCI 索引](aoci.txt)维护。

## 安装

以下 Maven 示例针对本轮候选；公开发布与精确版本消费通过前，不视为可用的远程坐标。

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
            }
            filter { includeGroup("com.tencent.kuikly-open") }
        }
        maven("https://jitpack.io")
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        google()
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts: kotlin.sourceSets
commonMain.dependencies {
    implementation("com.github.gycrosskit.permission:permission-core:2.0.1")
}
// HarmonyOS Kuikly 宿主额外添加
ohosArm64Main.dependencies {
    implementation("com.github.gycrosskit.permission:permission-kuikly:2.0.1")
}
```

HarmonyOS 原生包独立安装，不由 Maven 依赖自动携带。既有 HAR `2.0.1` 与 Maven 候选 `2.0.1` 保持原有能力 key 与状态协议；下方命令须在 Registry 上架后执行，Release HAR 下载不代表 Registry 可安装。

```sh
ohpm install @gycrosskit/permission-native@2.0.1
```

## 最小使用

```kotlin
import io.github.gycrosskit.permission.*

// Android 宿主创建时绑定，同一实例可供页面使用。
val permissions = AndroidPermissionPlatform()
permissions.bind(activity)
// 在绑定页面生命周期的协程内，由用户操作触发：
val status = permissions.request(AppPermission.CAMERA)
// Activity 销毁时：
permissions.unbind(activity)
```

iOS 用 `IosPermissionPlatform()` 替代 Android 实现，无需绑定 UIViewController；内部切换主线程执行。HarmonyOS 使用 `GycPermissionService` 或注册 Kuikly `PermissionModule`，见接入指南。

Android 媒体执行器可复用原历史范围，不依赖 media 或业务结果类型：

```kotlin
val cameraHistory = AndroidPermissionRequestHistory() // 每个 picker 一个实例
// 旧写相册权限的历史对象由宿主进程范围持有并注入，避免替换 saver 时丢失申请事实。
val state = activity.requestRuntimePermission(android.Manifest.permission.CAMERA, cameraHistory)
// 宿主映射 AndroidRuntimePermissionState 到 MediaPermissionState：
// GRANTED 可用；SETTINGS_REQUIRED 可提示设置恢复；RESTRICTED 不引导设置；其它按拒绝处理。
```

默认历史仅在对象实例内；传 `preferencesName` 可沿用旧 namespace 持久化，同一历史对象可由多个调用方共享。
`AndroidPermissionPlatform(requestHistory = history)` 也支持显式注入，原 `historyPreferencesName` 参数保持。
已授权或受系统策略限制时直接返回；其余状态在用户主动操作时发起一次系统申请，包括一次授权失效后的再次申请。
申请前已是 REQUESTED_WITHOUT_RATIONALE，实际重试明确拒绝且回执后仍无 rationale 时返回 SETTINGS_REQUIRED，宿主可提供设置恢复入口；它不是永久拒绝证明，也不阻止后续再次申请。首次拒绝、有 rationale 或空回执返回 DENIED，系统策略限制返回 RESTRICTED。旧 REQUESTED_WITHOUT_RATIONALE 仍只是查询时的历史推断，不能作为设置提示依据。
协程取消、结果交付和启动失败均注销本次 ActivityResult launcher。
此执行器不提供相册权限管理、设置导航或媒体业务映射，旧系统 WRITE_EXTERNAL_STORAGE 仍由宿主按需声明。

## 权限与生命周期

Android 权限 Launcher 注册/启动失败会完整释放本次等待并传播异常，只在系统受理后记录申请历史；HAR 入口导出 `GycPermission` / `GycPermissionStatus` 类型。

状态为 `NOT_DETERMINED / GRANTED / LIMITED / DENIED / RESTRICTED / UNSUPPORTED`；`LIMITED` 表示可用但受限，例如粗略定位。Android `DENIED` 不等于永久拒绝，设置返回后调用 `resumeAfterSettings` 重新判断。Android 宿主解绑会让属于该页面的挂起申请失败；取消协程不能强制关闭系统授权弹窗，迟到结果不会交给新请求。

Android Manifest 按需声明 `CAMERA`、`RECORD_AUDIO`、`ACCESS_COARSE_LOCATION`、`ACCESS_FINE_LOCATION`；iOS Info.plist 按需填写 `NSCameraUsageDescription`、`NSMicrophoneUsageDescription`、`NSLocationWhenInUseUsageDescription`，缺少声明可能导致系统终止应用。HarmonyOS 声明实际使用的相机、麦克风或前台定位权限。Kuikly Module 每页一个实例，页面结束时调用 `dispose()`。

不提供后台定位、相册权限管理或自动设置页导航。编译与远程依赖验证不代替真机权限弹窗和设置页往返验收。

## 文档与帮助

- [接入指南](docs/接入指南.md)：平台初始化、权限声明和生命周期。
- [开发与验证](docs/开发与验证.md)：源码构建、检查命令与验收范围。
- [版本与发行说明](https://github.com/gycrosskit/permission/releases)、[问题反馈](https://github.com/gycrosskit/permission/issues)。

Apache-2.0，见 [LICENSE](LICENSE)。

## 历史发布记录

以下记录保留对应版本、渠道与验收时点，不替代顶部当前功能和安装基线。

- <a id="015-历史发布状态"></a>[0.1.5 发布验收](docs/0.1.5发布验收.md)。
- <a id="014-历史-prerelease"></a>[0.1.4 远程发布验收](docs/0.1.4远程发布验收.md)。
- <a id="013-历史-prerelease"></a>[0.1.3 发布验收](docs/发布验收-0.1.3.md)。
- [0.1.1/0.1.2 权限与媒体权限历史验收](verification/验收记录.md)。

## 自动回归

源码回归使用 `component-ci/verify`，见[统一远程验证（Jenkins）](https://github.com/gycrosskit/.github/blob/main/docs/统一远程验证.md)，按已推送源码 ref/SHA 选择对应组件；平台命令与参数以受信 Jenkins recipe 为准。实际执行范围、测试数量、结果与耗时以绑定源码/CI SHA 的构建回执为准，轻量检查或跳过不算平台测试通过。

新版本按[发布流程](https://github.com/gycrosskit/.github/blob/main/docs/发布流程.md)执行 `component-ci/release`。按渠道绑定准确的源码 SHA、版本和必要的 Tag，在同一次 Run 冻结候选并核对远端制品，保留发布和消费回执。

OHOS KLIB 编译不代表 HAR 构建、ohpm 上架或真机验收。HAR 通过统一入口独立构建与消费，各阶段以对应准确源码 SHA 的回执为准。

阶段、缓存、有限网络重试、失败记录与证据边界见[共用 CI 规则](https://github.com/gycrosskit/.github/blob/main/docs/持续集成门禁.md)；本库实际平台命令以 Jenkins 的固定版本 recipe 为准。源码通过、远程消费、HAR/ohpm 与设备验收分别记录。

依赖解析由实际 Gradle 构建确认；每个进程先有限预解析定制仓库 DNS，并为下载设置连接/读取等待上限。构建或测试失败仍阻断门禁，不以独立 curl 探针代替真实消费者验证。
