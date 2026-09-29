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
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    ohosArm64()
    sourceSets {
        commonMain.dependencies { api(libs.coroutines.core) }
        androidMain.dependencies {
            implementation(libs.androidx.activity)
            implementation(libs.androidx.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

android {
    namespace = "io.github.gycrosskit.permission"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

publishing {
    repositories.maven {
        name = "staging"
        url = uri(rootProject.layout.buildDirectory.dir("maven"))
    }
}
