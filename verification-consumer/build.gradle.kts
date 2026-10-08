plugins {
    kotlin("multiplatform") version "2.2.21-1.0.0"
    id("com.android.library") version "8.10.1"
}
val permissionVersion = providers.gradleProperty("permissionVersion").orElse("0.1.8").get()
kotlin {
    androidTarget()
    iosArm64()
    iosX64 { binaries.framework { baseName = "PermissionConsumer" } }
    iosSimulatorArm64 { binaries.framework { baseName = "PermissionConsumer" } }
    ohosArm64()
    sourceSets {
        commonMain.dependencies { implementation("com.github.gycrosskit.permission:permission-core:$permissionVersion") }
        val ohosArm64Main by getting {
            dependencies { implementation("com.github.gycrosskit.permission:permission-kuikly:$permissionVersion") }
        }
    }
}
android { namespace = "io.github.gycrosskit.permission.consumer"; compileSdk = 36; defaultConfig { minSdk = 24 } }
