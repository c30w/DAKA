package com.marvin.daka.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 提醒相关的全局设置（V3 改版）。
 *
 * V2 的时候这里存的是「全局一个提醒时间」。V3 提醒下沉到每个习惯身上，
 * 时间和重复规则都存在 habits 表里（见 [com.marvin.daka.model.Habit]），
 * 所以这里只剩三样**跨习惯的全局设置**：
 *
 *   1. 新建习惯时的默认提醒时间（省得每次都从 21:00 重新拨）
 *   2. 日历同步开关
 *   3. 同步到哪个系统日历（日历 id）
 *
 * 用 DataStore 而不是 Room：这几个是零散的开关/标量，
 * Room 是给「结构化、有关系、要查询」的数据用的，杀鸡用牛刀。
 *
 * 两个必须注意的写法：
 *
 * 1. **dataStore 委托必须写在文件顶层**（不能是类成员）。
 *    这是为了保证整个进程只有这一个实例：DataStore 要求单例，
 *    建多个实例同时读写同一个文件会直接崩（`IllegalStateException: 有多个 DataStore 活跃`）。
 *
 * 2. **读取时加了 catch(IOException)**。
 *    文件损坏时 DataStore 会抛异常，不接住的话整个 Flow 就死了，
 *    用户会看到「设置页白屏」这种极难排查的问题。
 *    这里降级成「读失败就当没设置过」，至少 App 还能用。
 */
private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reminder_settings"
)

class ReminderPrefs(private val context: Context) {

    private object Keys {
        val DEFAULT_HOUR = intPreferencesKey("default_hour")
        val DEFAULT_MINUTE = intPreferencesKey("default_minute")
        val CALENDAR_SYNC = booleanPreferencesKey("calendar_sync")
        val CALENDAR_ID = longPreferencesKey("calendar_id")
        val SYNCED_EVENTS = stringPreferencesKey("synced_events")
    }

    /** 新建习惯时预填的小时。默认 21 点——一天结束前查漏补缺最合适 */
    val defaultHour: Flow<Int> = context.reminderDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.DEFAULT_HOUR] ?: 21 }

    /** 新建习惯时预填的分钟 */
    val defaultMinute: Flow<Int> = context.reminderDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.DEFAULT_MINUTE] ?: 0 }

    /** 是否把习惯提醒同步到系统日历（安卓日历 / 谷歌日历）。默认关 */
    val calendarSyncEnabled: Flow<Boolean> = context.reminderDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.CALENDAR_SYNC] ?: false }

    /**
     * 同步到哪个系统日历。-1 = 还没选。
     *
     * ⚠️ 这个 id 可能失效：用户在系统日历里删掉那个日历之后，
     * 我们存的就是个野 id。所以每次同步前都要校验它还在不在（见 CalendarRepository）。
     */
    val calendarId: Flow<Long> = context.reminderDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.CALENDAR_ID] ?: -1L }

    /**
     * 已经写进系统日历的事件 id 映射：habitId → 系统日历里的 eventId。
     *
     * 存成 "1=101,2=102" 这种扁平字符串而不是 Set：DataStore Preferences 的
     * stringSetPreferencesKey 在并发写入上有已知的坑（集合是「整体替换」语义，
     * 两个协程同时改会互相覆盖），扁平字符串配合 edit{} 的事务更安全。
     *
     * 为什么要记这份映射？因为**只靠事件上的标记不一定找得回来**：
     * 谷歌日历的同步适配器在下一轮同步时可能把非同步源写入的 SYNC_ID1 清掉。
     * 记下 eventId 就多一条退路——同步前先按 id 删，删不掉再按标记找。
     */
    val syncedEventIds: Flow<Map<Long, Long>> = context.reminderDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[Keys.SYNCED_EVENTS]
                .orEmpty()
                .split(',')
                .mapNotNull { pair ->
                    val parts = pair.split('=')
                    val habitId = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                    val eventId = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                    habitId to eventId
                }
                .toMap()
        }

    suspend fun setDefaultTime(hour: Int, minute: Int) {
        context.reminderDataStore.edit {
            it[Keys.DEFAULT_HOUR] = hour
            it[Keys.DEFAULT_MINUTE] = minute
        }
    }

    suspend fun setCalendarSync(enabled: Boolean) {
        context.reminderDataStore.edit { it[Keys.CALENDAR_SYNC] = enabled }
    }

    suspend fun setCalendarId(id: Long) {
        context.reminderDataStore.edit { it[Keys.CALENDAR_ID] = id }
    }

    /** 覆盖写入「habitId → eventId」映射。传空 map 表示清空（已停止同步） */
    suspend fun setSyncedEventIds(map: Map<Long, Long>) {
        context.reminderDataStore.edit {
            it[Keys.SYNCED_EVENTS] = map.entries.joinToString(",") { (h, e) -> "$h=$e" }
        }
    }
}
