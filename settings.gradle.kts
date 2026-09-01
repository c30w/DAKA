// 是否用阿里云镜像。CI 跑在美国 GitHub runner 上，阿里云镜像既慢又可能是失败源，
// 官方源反而快且稳，所以 CI 里用环境变量 DAKA_NO_MIRROR=true 关掉镜像、直接走官方源；
// 本地/国内开发不设该变量，继续用镜像加速。
// 注意：不能抽成顶层 val 再在 pluginManagement{} 里引用——Gradle 的 pluginManagement/plugins
// 块在脚本编译期被特别处理，闭包捕获不到顶层变量。所以直接在块内内联读环境变量。

pluginManagement {
    repositories {
        // 阿里云镜像放在最前面：命中就直接下载，速度是官方源的 1.6~4.6 倍（2026-08-29 实测）。
        // 只在国内开发（镜像关掉=false）时启用；CI 上关掉，避免镜像不稳定拖垮解析。
        if (System.getenv("DAKA_NO_MIRROR") != "true") {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        }

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
        // 同上：镜像优先，官方兜底（CI 里关镜像，见文件头注释）
        if (System.getenv("DAKA_NO_MIRROR") != "true") {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }

        google()
        mavenCentral()
    }
}

rootProject.name = "DAKA"
include(":app")
