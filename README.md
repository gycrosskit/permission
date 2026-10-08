# GY CrossKit Permission

## 当前功能与平台边界

core 提供相机、麦克风、前台定位的五种权限状态及申请/恢复；无CMP UI模块，permission-kuikly仅OHOS Module，A/i两套UI复用宿主注入的原生实现。

适用版本：Maven 0.1.7；HAR 0.1.6沿用原字节。本次修复与平台边界见[功能与平台差异](docs/功能与平台差异.md)，构建与渠道验收见[版本发布记录](https://github.com/gycrosskit/permission/releases/tag/0.1.7)；下方旧版本记录保留其历史范围。

当前测试覆盖、执行时点和未验收项集中见[验证范围](docs/功能与平台差异.md#验证范围)，复现命令见[开发与验证](docs/开发与验证.md)。

当前源码新增[跨端行为候选](docs/跨端行为候选.md)，尚未发布；下方远程版本验收仍对应其既有不可变标签。

[历史完整源码审查](docs/完整源码审查.md) 列出全部生产文件、公开调用链、实际验证与未测项。

相机、麦克风和前台定位的权限状态查询与申请。宿主负责申请时机、说明文案、系统权限声明和应用设置页跳转。

当前未发布候选将 Android `isPermissionRevokedByPolicy` 映射为 `RESTRICTED`；已有完整/粗略授权优先，设备策略限制不引导为普通设置恢复。

## 0.1.5 发布状态

Maven core/Kuikly `0.1.5` 已提供 GitHub 预发行，JitPack 的精确标签/提交、完整 publication 和实际文件校验通过。Release HAR 已重下载校验；OHPM 以独立 `candidate-0.1.5` 标签提交审核，精确 Registry 安装仍返回 NOTFOUND，旧 next 保持。全新远程 Maven 的 Android/iOS/OHOS 消费与 Simulator Framework 链接已通过。详情见[0.1.5 发布验收](docs/0.1.5发布验收.md)。

Android 权限 Launcher 注册/启动失败会完整释放本次等待并传播异常，只在系统受理后记录申请历史；HAR 入口导出 `GycPermission` / `GycPermissionStatus` 类型。既有不可变版本状态见下文。

## 0.1.4 历史 prerelease

iOS 定位权限等待被后台取消时，把 continuation 归属判断与清理排回 Main；公开 API 保持兼容。

| 渠道 | 当时版本 | 历史状态 |
| --- | --- | --- |
| Maven core/Kuikly | 0.1.4 | prerelease 已发布，JitPack 制品审计通过；独立消费结果见验收文档 |
| HarmonyOS HAR | 0.1.2 | 原生源码未变，保持既有版本；Registry 状态沿用历史记录 |

[0.1.4 Release](https://github.com/gycrosskit/permission/releases/tag/0.1.4) 已提供固定 Maven 归档与 SHA256SUMS；JitPack 最终状态、精确 commit 和制品审计通过。源码回归、远程渠道限制及独立消费进度见 [0.1.4 远程发布验收](docs/0.1.4远程发布验收.md)，不代表生产宿主或真实设备验收通过。

历史 0.1.2 发布时：Maven 0.1.2 已提供为 prerelease，默认 JitPack 的 Android/iOS/OHOS 消费验证通过。
HAR 0.1.2 已以 next 标签提交审核，registry 正式 latest 仍为 0.1.1；审核完成前使用既有 HAR。
0.1.1 历史渠道与验收记录保留。

## 0.1.3 历史 prerelease

修复 Kuikly 回调已完成但协程尚未消费时页面销毁的迟交付；iOS 权限查询/申请内部使用主线程，定位 manager 在主线程延迟创建。移除未使用的 Compose 构建依赖，并补齐新 POM 的 Apache-2.0 元数据。

| 渠道 | 当时版本 | 历史状态 |
| --- | --- | --- |
| Maven core/Kuikly | 0.1.3 | JitPack 全文件/hash 与 Android/OHOS/三 iOS 编译、Simulator 链接通过 |
| HarmonyOS HAR | 0.1.2 | 源码未变，沿用旧 Release 已验产物；OHPM 仍需核验审核结果 |


## 平台与要求

| 平台 | 接入方式 | 系统要求 |
| --- | --- | --- |
| Android | KMP `permission-core`，`AndroidPermissionPlatform` | API 24+，`ComponentActivity` |
| iOS | KMP `permission-core`，`IosPermissionPlatform` | iOS 14+（定位精度状态使用 iOS 14 API），无独立 Swift Package |
| HarmonyOS | `permission-kuikly` + 原生 HAR，或 ArkTS 直接使用 HAR | 当前 HAR 的 target/compatible SDK 均为 API 22 |

KMP 产物使用 Kotlin `2.2.21-1.0.0`、coroutines `1.10.2-1.0.0`；Kuikly 为 `2.28.0-2.0.21-ohos`。OHOS 宿主需要匹配的 Kotlin 工具链，完整仓库配置见接入指南。

## 架构与调用流程

`permission-core` 定义权限与状态；Android/iOS 实现负责系统申请，HarmonyOS 通过 Kuikly Module 或直接调用 HAR。宿主负责权限声明、申请时机与设置导航。

```mermaid
flowchart TB
    Host[宿主] --> Core[permission-core<br/>PermissionPlatform]
    Core --> Android[Android<br/>AndroidPermissionPlatform]
    Core --> IOS[iOS<br/>IosPermissionPlatform]
    Core --> Module[permission-kuikly<br/>PermissionModule]
    Android --> Result[ActivityResult<br/>系统权限]
    IOS --> Apple[AVFoundation<br/>CoreLocation]
    Module --> Native[HAR<br/>GycPermissionModule]
    Host --> Service[HAR<br/>GycPermissionService]
    Native --> Service
    Service --> System[abilityAccessCtrl]
```

Android 申请串行执行；已经可用的权限直接返回，否则等待系统回调。协程取消不关闭系统弹窗，下次申请仍需等待真实回调或宿主解绑。

```mermaid
sequenceDiagram
    participant Host as 宿主
    participant Platform as AndroidPermissionPlatform
    participant System as ActivityResult / 系统
    Host->>Platform: bind(activity)
    Host->>Platform: request(permission)
    Platform->>Platform: 获取互斥锁，等待旧系统请求结束
    Platform->>Platform: 查询权限与申请历史
    alt 已可用或受限于系统策略
        Platform-->>Host: 当前 PermissionStatus
    else 需要申请
        Platform->>System: 独立 key 注册并 launch
        System-->>Platform: 权限回调
        Platform->>Platform: 注销 launcher，清空请求
        Platform-->>Host: PermissionStatus（协程仍有效）
    end
    Note over Host,Platform: unbind 会清理该 Activity 的请求并令挂起调用失败
```

核心类型仅展示 KMP 公共契约与实现；虚线箭头表示依赖，空心三角指向被实现的接口。

```mermaid
classDiagram
    direction LR
    class PermissionPlatform {
        <<interface>>
        +getStatus(permission) PermissionStatus
        +request(permission) PermissionStatus
        +resumeAfterSettings(permission) PermissionStatus
    }
    class AndroidPermissionPlatform {
        +bind(activity)
        +unbind(activity)
    }
    class IosPermissionPlatform
    class PermissionModule {
        +dispose()
    }
    class AppPermission {
        <<enumeration>>
    }
    class PermissionStatus {
        <<enumeration>>
        +allowsUse Boolean
    }
    PermissionPlatform <|.. AndroidPermissionPlatform
    PermissionPlatform <|.. IosPermissionPlatform
    PermissionPlatform <|.. PermissionModule
    PermissionPlatform ..> AppPermission : 输入
    PermissionPlatform ..> PermissionStatus : 返回
```

源码：[公共入口与 AppPermission](permission-core/src/commonMain/kotlin/io/github/gycrosskit/permission/PermissionPlatform.kt)、[PermissionStatus](permission-core/src/commonMain/kotlin/io/github/gycrosskit/permission/PermissionStatus.kt)、[Android 实现](permission-core/src/androidMain/kotlin/io/github/gycrosskit/permission/AndroidPermissionPlatform.kt)、[iOS 实现](permission-core/src/iosMain/kotlin/io/github/gycrosskit/permission/IosPermissionPlatform.kt)、[Kuikly Module](permission-kuikly/src/commonMain/kotlin/io/github/gycrosskit/permission/kuikly/PermissionModule.kt)、[HAR Module](ohos/permission-native/src/main/ets/GycPermissionModule.ets)、[HAR Service](ohos/permission-native/src/main/ets/GycPermissionService.ets)。

## 安装

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
    implementation("com.github.gycrosskit.permission:permission-core:0.1.5")
}
// HarmonyOS Kuikly 宿主额外添加
ohosArm64Main.dependencies {
    implementation("com.github.gycrosskit.permission:permission-kuikly:0.1.5")
}
```

HarmonyOS 原生包独立安装，不由 Maven 依赖自动携带。以下 `0.1.5` Registry 命令在本轮核验时仍返回 NOTFOUND；审核期间从 [0.1.5 Release](https://github.com/gycrosskit/permission/releases/tag/0.1.5) 下载并校验 HAR 后本地安装：

```sh
ohpm install @gycrosskit/permission-native@0.1.5
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
// GRANTED -> GRANTED；REQUESTED_WITHOUT_RATIONALE -> BLOCKED；其余 -> DENIED。
```

默认历史仅在对象实例内；传 `preferencesName` 可沿用旧 namespace 持久化，同一历史对象可由多个调用方共享。
`AndroidPermissionPlatform(requestHistory = history)` 也支持显式注入，原 `historyPreferencesName` 参数保持。
首次/仍有 rationale 时申请；刚拒绝返回 DENIED，下次主动申请才根据历史与 rationale 返回
REQUESTED_WITHOUT_RATIONALE。协程取消、结果交付和启动失败均注销本次 ActivityResult launcher。
此执行器不提供相册权限管理、设置导航或媒体业务映射，旧系统 WRITE_EXTERNAL_STORAGE 仍由宿主按需声明。

## 权限与生命周期

状态为 `NOT_DETERMINED / GRANTED / LIMITED / DENIED / RESTRICTED`；`LIMITED` 表示可用但受限，例如粗略定位。Android `DENIED` 不等于永久拒绝，设置返回后调用 `resumeAfterSettings` 重新判断。Android 宿主解绑会让属于该页面的挂起申请失败；取消协程不能强制关闭系统授权弹窗，迟到结果不会交给新请求。

Android Manifest 按需声明 `CAMERA`、`RECORD_AUDIO`、`ACCESS_COARSE_LOCATION`、`ACCESS_FINE_LOCATION`；iOS Info.plist 按需填写 `NSCameraUsageDescription`、`NSMicrophoneUsageDescription`、`NSLocationWhenInUseUsageDescription`，缺少声明可能导致系统终止应用。HarmonyOS 声明实际使用的相机、麦克风或前台定位权限。Kuikly Module 每页一个实例，页面结束时调用 `dispose()`。

不提供后台定位、相册权限管理或自动设置页导航。编译与远程依赖验证不代替真机权限弹窗和设置页往返验收。

## 文档与帮助

- [接入指南](docs/接入指南.md)：平台初始化、权限声明和生命周期。
- [开发与验证](docs/开发与验证.md)：源码构建、检查命令与验收范围。
- [版本与发行说明](https://github.com/gycrosskit/permission/releases)、[问题反馈](https://github.com/gycrosskit/permission/issues)。

Apache-2.0，见 [LICENSE](LICENSE)。

本轮状态见 [0.1.5 发布验收](docs/0.1.5发布验收.md)；历史制品与远程记录见 [0.1.4 远程发布验收](docs/0.1.4远程发布验收.md)及 [0.1.3 发布验收](docs/发布验收-0.1.3.md)。

## 自动回归

[Component regression](.github/workflows/regression.yml) 在 PR 和 `main` 更新时运行现有 Python/Node 契约测试、Android 单元测试及编译、独立 Kuikly 生命周期测试，以及 macOS 上的 iOS/OHOS KLIB 编译。Release 发布或手动指定不可变版本后，还会校验 Release Maven 归档的 SHA-256、POM、metadata 与文件引用，并从 JitPack 独立编译 Android 消费者、链接 iOS 消费者、编译 OHOS Kuikly 消费者；不会发布二进制。

OHOS KLIB 编译不代表 HAR 构建、ohpm 上架或真机验收。当前没有已确认可用的 DevEco/Hvigor runner，这些检查尚未自动化，不能作为 CI 通过范围。

PR 的发布回归固定验证已发布 `0.1.5` 基线，五个 job 都通过后才合并；Release 事件使用其精确标签。基线证明远程产物可消费，不代表 PR 新源码已发布。

依赖解析由实际 Gradle 构建确认；每个进程先有限预解析定制仓库 DNS，并为下载设置连接/读取等待上限。构建或测试失败仍阻断门禁，不以独立 curl 探针代替真实消费者验证。
