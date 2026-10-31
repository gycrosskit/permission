import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        maven { url = uri("https://maven.eazytec-cloud.com/nexus/repository/maven-public/"); content { includeVersionByRegex(".*", ".*", ".*-1\\.0\\.0") } }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 测试依赖使用可用镜像，避免 Maven Central 限流阻断行为回归。
        exclusiveContent {
            forRepository { maven { url = uri("https://repo.huaweicloud.com/repository/maven") } }
            filter { includeGroup("org.robolectric") }
        }
        // Kuikly 多平台依赖必须读取完整 .module，不能从仅返回 POM 的镜像回落到不存在的根 JAR。
        exclusiveContent {
            forRepository { maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/") } }
            filter { includeGroup("com.tencent.kuikly-open") }
        }

        maven { url = uri("https://maven.eazytec-cloud.com/nexus/repository/maven-public/"); content { includeVersionByRegex(".*", ".*", ".*-1\\.0\\.0") } }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/") }
    }
}

rootProject.name = "permission"
include(":permission-core", ":permission-kuikly")
