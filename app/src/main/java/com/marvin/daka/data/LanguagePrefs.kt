package com.marvin.daka.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 界面语言偏好（i18n）。
 *
 * 为什么用 SharedPreferences 而不是 DataStore？
 * 语言要在 `Application.attachBaseContext` / `Activity.attachBaseContext` 这种
 * **同步**时机读取并套到 Configuration 上，DataStore 是挂起式的、那一刻读不到。
 * 一个只有「读一个字符串」需求的小文件，SharedPreferences 的同步 API 正合适。
 *
 * 存的是语言代码："" = 跟随系统（用 Locale.getDefault()）；"zh" / "en" = 固定语言。
 */
object LanguagePrefs {

    private const val PREF_NAME = "daka_language"
    private const val KEY_LANG = "lang"

    /** 读取当前语言代码（"" 表示跟随系统） */
    fun getCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, "") ?: ""
    }

    /** 写入语言代码并立即生效（调用方记得 recreate Activity 让界面刷新） */
    fun setCode(context: Context, code: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, code)
            .apply()
    }

    /**
     * 把一个 Context 包成「应用了所选语言」的 Context。
     * 在 attachBaseContext 里调用：super.attachBaseContext(applyLocale(base))。
     */
    fun applyLocale(base: Context): Context {
        val code = getCode(base)
        val locale = if (code.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(code)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
