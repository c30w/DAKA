pluginManagement {
    repositories {
        // 阿里云镜像放在最前面：命中就直接下载，速度是官方源的 1.6~4.6 倍（2026-08-29 实测）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }

        // 官方源保留在后面兜底。
        // Gradle 按声明顺序依次查找：镜像里有就走镜像，镜像里没有（比如刚发布的新版本
        // 阿里云还没同步）会自动回落到官方源，所以加镜像不会有「某个依赖突然拉不到」的风险。
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 同上：镜像优先，官方兜底
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }

        google()
        mavenCentral()
    }
}

rootProject.name = "DAKA"
include(":app")
