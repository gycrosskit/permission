plugins {
    kotlin("multiplatform") version "2.2.21-1.0.0"
    id("com.android.library") version "8.10.1"
}
val permissionVersion = providers.gradleProperty("permissionVersion").orElse("0.1.9").get()
val verifyKuiklyNative = permissionVersion !in setOf("0.1.0", "0.1.1", "0.1.2", "0.1.3", "0.1.4", "0.1.5", "0.1.6", "0.1.7", "0.1.8")
val kuiklyRenderFrameworkDir = providers.gradleProperty("kuiklyRenderFrameworkDir").orNull
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
    iosArm64()
    iosX64 { binaries.framework {
        baseName = "PermissionConsumer"
        export("com.github.gycrosskit.permission:permission-core:$permissionVersion")
        if (verifyKuiklyNative) {
            export("com.github.gycrosskit.permission:permission-kuikly:$permissionVersion")
            kuiklyRenderFrameworkDir?.let { linkerOpts("-F$it", "-framework", "OpenKuiklyIOSRender") }
        }
    } }
    iosSimulatorArm64 { binaries.framework {
        baseName = "PermissionConsumer"
        export("com.github.gycrosskit.permission:permission-core:$permissionVersion")
        if (verifyKuiklyNative) {
            export("com.github.gycrosskit.permission:permission-kuikly:$permissionVersion")
            kuiklyRenderFrameworkDir?.let { linkerOpts("-F$it", "-framework", "OpenKuiklyIOSRender") }
        }
    } }
    ohosArm64()
    sourceSets {
        commonMain.dependencies { api("com.github.gycrosskit.permission:permission-core:$permissionVersion") }
        ohosArm64Main.dependencies { implementation("com.github.gycrosskit.permission:permission-kuikly:$permissionVersion") }
        if (verifyKuiklyNative) {
            androidMain.get().kotlin.srcDir("src/kuiklyNativeAndroidMain/kotlin")
            iosMain.get().kotlin.srcDir("src/kuiklyNativeIosMain/kotlin")
            androidMain.dependencies { api("com.github.gycrosskit.permission:permission-kuikly:$permissionVersion") }
            iosMain.dependencies { api("com.github.gycrosskit.permission:permission-kuikly:$permissionVersion") }
        }
    }
}
android {
    namespace = "io.github.gycrosskit.permission.consumer"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
