# GY CrossKit Permission

相机、麦克风、前台定位的权限状态与申请，面向 Android、iOS 和 HarmonyOS。页面文案、申请时机、权限声明和设置页跳转由宿主负责。

Maven `0.1.1` 已发布：[GitHub Release](https://github.com/gycrosskit/permission/releases/tag/0.1.1)，JitPack 状态 `ok`，独立消费的Android、iOS arm64/x64 编译、iOS Simulator Framework 链接、OHOS 编译通过。 OHPM 沿用此前已发布的 `@gycrosskit/permission-native@0.1.1`，本轮未重复发布。

## 公共契约

`PermissionPlatform` 提供 `getStatus`、`request`、`resumeAfterSettings`。权限为 `CAMERA`、`MICROPHONE`、`LOCATION_WHEN_IN_USE`；状态为 `NOT_DETERMINED`、`GRANTED`、`LIMITED`、`DENIED`、`RESTRICTED`。`LIMITED` 表示可用但受限，例如仅获粗略定位。

Android 使用 `AndroidPermissionPlatform`，宿主 `ComponentActivity` 出现时调用 `bind(activity)`，销毁时调用 `unbind(activity)`。当前页面的权限申请会在宿主解绑时取消；另一个页面的迟到结果不会交给新页面。iOS 使用 `IosPermissionPlatform`，无需 UIViewController 绑定。宿主仍须在 Android Manifest 声明 `CAMERA`、`RECORD_AUDIO`、`ACCESS_COARSE_LOCATION`、`ACCESS_FINE_LOCATION`，在 iOS Info.plist 按需提供 `NSCameraUsageDescription`、`NSMicrophoneUsageDescription`、`NSLocationWhenInUseUsageDescription`。

```kotlin
// settings.gradle.kts: dependencyResolutionManagement.repositories
maven { url = uri("https://jitpack.io") }

// commonMain.dependencies
implementation("com.github.gycrosskit.permission:permission-core:0.1.1")
// 鸿蒙 Kuikly 页面另加
implementation("com.github.gycrosskit.permission:permission-kuikly:0.1.1")
```

Kuikly 使用 `PermissionModule`，在页面结束时调用 `dispose()`；鸿蒙宿主另外从 HAR 注册 `GycPermissionModule`。鸿蒙原生 API 是 `GycPermissionService`，位于 `@gycrosskit/permission-native`。HAR 的目标版本为 `0.1.1`；初版 `0.1.0` 归档缺少 README，ohpm 拒绝提交。该包须待 ohpm 审核上架并远程安装后，才能使用远程版本号；构建通过不等于已经上架。HarmonyOS 模块需声明 `ohos.permission.CAMERA`、`ohos.permission.MICROPHONE`、`ohos.permission.APPROXIMATELY_LOCATION`、`ohos.permission.LOCATION` 中实际使用的权限。

## 验证与发布

版本 `0.1.0` 使用 Kotlin `2.2.21-1.0.0`。Maven KMP 产物在 macOS 构建并放入同版本 GitHub Release，JitPack 下载版本化归档并校验 SHA-256。iOS、HarmonyOS 的 KLIB 编译及 Android 编译、Android 状态映射测试、鸿蒙 HAR 构建与状态测试是发布前检查；系统弹窗和设置页往返仍需设备验收。

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" bash gradlew :permission-core:compileDebugKotlinAndroid :permission-core:compileKotlinIosSimulatorArm64 :permission-core:compileKotlinOhosArm64 :permission-kuikly:compileKotlinOhosArm64
node --test ohos/permission-native/test/permission.test.cjs
cd ohos && DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk /Applications/DevEco-Studio.app/Contents/tools/hvigor/bin/hvigorw assembleHar --no-daemon
```

Apache-2.0，见 [LICENSE](LICENSE)。

### Android 旧申请历史兼容

替换已有宿主时传 `AndroidPermissionPlatform(historyPreferencesName = "app_permission_history")`，
沿用原 SharedPreferences 中的权限枚举名记录；默认仍使用 `gycrosskit_permission_history`。
无需复制历史，已授权状态继续以系统为准；申请过但无 rationale 仍是 `DENIED`，不推断永久拒绝。

`0.1.1` 的 Maven 包为本轮修复候选，远程可用性以独立消费结果为准；OHPM `permission-native` 不受本次 Android 修改影响，继续使用已发布的 `0.1.1`。
