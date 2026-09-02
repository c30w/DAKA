plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // KSP = Kotlin 的注解处理器。Room 靠它在编译期生成 _Impl 数据库代码。
    // 老教程里的 kapt 是上一代方案，Kotlin 2.x 一律用 KSP，快且是官方唯一维护的方向。
    alias(libs.plugins.ksp)
    // V2：JSON 备份，给 data class 生成序列化代码。版本跟 Kotlin 走
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.marvin.daka"
    compileSdk {
        version = release(37)
    }

    // 发布签名：从 local.properties 读取（该文件已被 .gitignore 忽略，不会泄露）。
    // 未配置时 release 不签名，仍可 assembleRelease 产出未签名包；真机调试用 debug 构建即可。
    // 生成密钥与填写方法见 README 的「发布签名」一节。
    // 注意：Gradle Kotlin DSL 脚本的编译 classpath 不暴露 java.util.Properties，
    // 所以用 Kotlin 标准库的 File.readLines() 自己解析简单的 key=value。
    val releaseProps = run {
        val pf = rootProject.file("local.properties")
        if (!pf.exists()) emptyMap<String, String>()
        else pf.readLines()
            .map { it.substringBefore("#").trim() }
            .filter { it.isNotEmpty() && it.contains("=") }
            .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
    }
    signingConfigs {
        if (releaseProps["RELEASE_STORE_FILE"] != null) {
            create("release") {
                storeFile = file(releaseProps["RELEASE_STORE_FILE"]!!)
                storePassword = releaseProps["RELEASE_STORE_PASSWORD"]
                keyAlias = releaseProps["RELEASE_KEY_ALIAS"]
                keyPassword = releaseProps["RELEASE_KEY_PASSWORD"]
            }
        }
    }

    defaultConfig {
        applicationId = "com.marvin.daka"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 仅在 local.properties 配置了 RELEASE_STORE_FILE 时才启用签名
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        // Room 2.8.x 要求编译目标至少 Java 17，保持 Java 11 会直接编译失败。
        // 改这里之后 Kotlin 的 jvmTarget 会自动跟随，不需要另外配置。
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // 图标（FAB 的「+」）。版本由上面的 compose-bom 统一管，所以这里不写版本号
    implementation(libs.androidx.compose.material.icons.core)
    // V3：完整图标集。日历页的 CalendarMonth / Chevron 等在 core 里没有
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ---- M4：ViewModel + 生命周期感知地收集 Flow ----
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ---- M5：页面间导航 ----
    implementation(libs.androidx.navigation.compose)

    // ---- Room（M3 新增）----
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // 编译器用 ksp() 而不是 implementation()：
    // 它只在编译期生成代码，不该被打进 APK（会白白增大体积，还可能引发类冲突）
    ksp(libs.androidx.room.compiler)

    // ---- V2：备份导出成 JSON ----
    implementation(libs.kotlinx.serialization.json)

    // ---- V2：提醒设置项（开关 / 时间）的本地存储 ----
    implementation(libs.androidx.datastore.preferences)

    // ---- V2：桌面小组件（Glance = 用 Compose 写法写 AppWidget）----
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
