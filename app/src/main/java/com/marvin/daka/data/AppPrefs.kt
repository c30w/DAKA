package com.marvin.daka.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * App 级的零散设置（与提醒无关的那些）。
 *
 * 为什么单独建一个 DataStore 文件而不是塞进 ReminderPrefs 的 reminder_settings？
 * DataStore 的文件名就是它的职责边界——reminder_settings 里全是提醒/日历同步的事，
 * 分类排序这种「首页布局偏好」混进去，以后排查问题时两拨逻辑互相干扰。
 * 一个小文件的成本，换个清爽的职责划分，值。
 *
 * dataStore 委托必须写在文件顶层的原因见 ReminderPrefs 的注释（单例约束）。
 */
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings"
)

class AppPrefs(private val context: Context) {

    private object Keys {
        /**
         * 分类显示顺序，逗号分隔的分类名。例："工作,学习,生活,健康,其他"。
         *
         * 为什么存 DataStore 而不是 habits 表？
         * 分类不是表（见 HabitCategory 的说明），没有地方挂「顺序」列；
         * 而分类顺序是纯界面偏好——没自定义过就是空，读出来按内置顺序排。
         * 空字符串 = 用户没动过分类顺序，这是最常见的状态。
         */
        val CATEGORY_ORDER = stringPreferencesKey("category_order")
        /** UI 音效开关，默认开。关掉后所有交互音效静音 */
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        /** 新手引导：是否已经看完整套引导（看完就不再自动弹出） */
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        /**
         * 主题模式：device（跟随系统）/ light（浅色）/ dark（深色）。
         * 取值常量见 ui.theme.ThemeMode，这里只存字符串，data 层不认识界面概念。
         */
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    /** 用户自定义的分类顺序。空列表 = 没自定义过，按内置顺序展示 */
    val categoryOrder: Flow<List<String>> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[Keys.CATEGORY_ORDER]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        }

    suspend fun setCategoryOrder(order: List<String>) {
        context.appDataStore.edit {
            it[Keys.CATEGORY_ORDER] = order.joinToString(",")
        }
    }

    /** UI 音效开关，默认开 */
    val soundEnabled: Flow<Boolean> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.SOUND_ENABLED] ?: true }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.appDataStore.edit {
            it[Keys.SOUND_ENABLED] = enabled
        }
    }

    /** 新手引导是否已完成。默认 false = 还没看完，需要展示引导 */
    val onboardingDone: Flow<Boolean> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        context.appDataStore.edit {
            it[Keys.ONBOARDING_DONE] = done
        }
    }

    /**
     * 主题模式。没设置过 = "device"（跟随系统）。
     * 读取时用 ThemeMode.normalize 兜底，遇到脏数据也不会崩。
     */
    val themeMode: Flow<String> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.THEME_MODE] ?: "device" }

    suspend fun setThemeMode(mode: String) {
        context.appDataStore.edit {
            it[Keys.THEME_MODE] = mode
        }
    }
}
