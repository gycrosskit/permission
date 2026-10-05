plugins {
    kotlin("multiplatform") version "2.2.21-1.0.0"
    id("com.android.library") version "8.10.1"
}
kotlin {
    androidTarget()
    iosArm64()
    iosX64()
    iosSimulatorArm64 { binaries.framework { baseName = "PermissionConsumer" } }
    ohosArm64()
    sourceSets {
        commonMain.dependencies { implementation("com.github.gycrosskit.permission:permission-core:0.1.5") }
        val ohosArm64Main by getting {
            dependencies { implementation("com.github.gycrosskit.permission:permission-kuikly:0.1.5") }
        }
    }
}
android { namespace = "io.github.gycrosskit.permission.consumer"; compileSdk = 36; defaultConfig { minSdk = 24 } }
