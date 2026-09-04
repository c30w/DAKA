package com.marvin.daka.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        /**
         * 检查更新结果缓存：上次成功拉到的版本号 / 发布页 URL / 时间戳(epoch ms)。
         * 1 小时内复用，避免频繁打 GitHub 无鉴权接口（60 次/小时/IP 限流）。
         */
        val UPDATE_CHECK_VERSION = stringPreferencesKey("update_check_version")
        val UPDATE_CHECK_URL = stringPreferencesKey("update_check_url")
        val UPDATE_CHECK_AT = longPreferencesKey("update_check_at")

        /**
         * V5：新建习惯页的**未提交草稿**（名称/备注/图标/颜色/分类）。
         *
         * 为什么存 DataStore 而不是 Room？
         * 草稿是「还没成为习惯的一堆输入」，它不是一个习惯，
         * 塞进 habits 表会污染首页列表和备份文件。它本质是界面偏好，归这里管。
         */
        val DRAFT_NAME = stringPreferencesKey("draft_name")
        val DRAFT_NOTE = stringPreferencesKey("draft_note")
        val DRAFT_EMOJI = stringPreferencesKey("draft_emoji")
        val DRAFT_COLOR = longPreferencesKey("draft_color")
        val DRAFT_CATEGORY = stringPreferencesKey("draft_category")

        /**
         * #9 外观自定义：强调色（ARGB Long）与圆角风格档位。
         * 档位常量在 ui.theme（CORNER_STANDARD/SQUARE/ROUND），这里只存 Int。
         */
        val ACCENT_COLOR = longPreferencesKey("accent_color")
        val CORNER_STYLE = stringPreferencesKey("corner_style")
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

    // ---------------- #9 外观自定义 ----------------

    /** 强调色（ARGB Long）。默认 0xFF0F8A7C 薄荷青绿（常量定义在 ui.theme） */
    val accentColor: Flow<Long> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.ACCENT_COLOR] ?: 0xFF0F8A7C }

    suspend fun setAccentColor(color: Long) {
        context.appDataStore.edit { it[Keys.ACCENT_COLOR] = color }
    }

    /** 圆角风格档位（"standard"/"square"/"round"）。默认标准 */
    val cornerStyle: Flow<String> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.CORNER_STYLE] ?: "standard" }

    suspend fun setCornerStyle(style: String) {
        context.appDataStore.edit { it[Keys.CORNER_STYLE] = style }
    }

    suspend fun setThemeMode(mode: String) {
        context.appDataStore.edit {
            it[Keys.THEME_MODE] = mode
        }
    }

    /** 检查更新结果缓存（版本号 + 发布页 URL + 时间戳）。null = 还没成功检查过 */
    data class UpdateCheckCache(val version: String, val url: String, val at: Long)

    val updateCheckCache: Flow<UpdateCheckCache?> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val at = prefs[Keys.UPDATE_CHECK_AT] ?: 0L
            val version = prefs[Keys.UPDATE_CHECK_VERSION]
            val url = prefs[Keys.UPDATE_CHECK_URL]
            if (version != null && url != null && at > 0L) {
                UpdateCheckCache(version, url, at)
            } else null
        }

    suspend fun setUpdateCheckCache(version: String, url: String) {
        context.appDataStore.edit {
            it[Keys.UPDATE_CHECK_VERSION] = version
            it[Keys.UPDATE_CHECK_URL] = url
            it[Keys.UPDATE_CHECK_AT] = System.currentTimeMillis()
        }
    }

    // ------------------------------------------------------------------
    // V5：新建习惯页的未提交草稿
    // ------------------------------------------------------------------

    /**
     * 新建页填了一半、用户按返回退出时的那份输入。
     * 存下来是为了「返回也自动保存不清空」——下次点「+」进来还在。
     */
    data class HabitDraft(
        val name: String,
        val note: String,
        val emoji: String,
        val colorArgb: Long,
        val category: String
    )

    /** null = 没有草稿（从来没存过，或者被清掉了） */
    val habitDraft: Flow<HabitDraft?> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val name = prefs[Keys.DRAFT_NAME] ?: return@map null
            val emoji = prefs[Keys.DRAFT_EMOJI] ?: return@map null
            val color = prefs[Keys.DRAFT_COLOR] ?: return@map null
            val category = prefs[Keys.DRAFT_CATEGORY] ?: return@map null
            HabitDraft(
                name = name,
                note = prefs[Keys.DRAFT_NOTE].orEmpty(),
                emoji = emoji,
                colorArgb = color,
                category = category
            )
        }

    suspend fun setHabitDraft(draft: HabitDraft) {
        context.appDataStore.edit {
            it[Keys.DRAFT_NAME] = draft.name
            it[Keys.DRAFT_NOTE] = draft.note
            it[Keys.DRAFT_EMOJI] = draft.emoji
            it[Keys.DRAFT_COLOR] = draft.colorArgb
            it[Keys.DRAFT_CATEGORY] = draft.category
        }
    }

    suspend fun clearHabitDraft() {
        context.appDataStore.edit {
            it.remove(Keys.DRAFT_NAME)
            it.remove(Keys.DRAFT_NOTE)
            it.remove(Keys.DRAFT_EMOJI)
            it.remove(Keys.DRAFT_COLOR)
            it.remove(Keys.DRAFT_CATEGORY)
        }
    }
}
