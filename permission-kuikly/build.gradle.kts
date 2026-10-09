plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    ohosArm64()
    sourceSets.commonMain.dependencies {
        api(project(":permission-core"))
        api(libs.kuikly.core)
    }
    sourceSets {
        androidMain.dependencies {
            api("com.tencent.kuikly-open:core-render-android:2.28.0-2.0.21-ohos")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2-1.0.0")
        }
    }
}

publishing {
    repositories.maven {
        name = "staging"
        url = uri(rootProject.layout.buildDirectory.dir("maven"))
    }
}

android {
    namespace = "io.github.gycrosskit.permission.kuikly"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
