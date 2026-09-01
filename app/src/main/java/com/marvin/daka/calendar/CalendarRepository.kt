package com.marvin.daka.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.marvin.daka.model.Habit
import com.marvin.daka.reminder.ReminderRule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * 系统日历的读写（Calendar Provider）。
 *
 * 这是 DAKA 和手机日历（安卓日历 / 谷歌日历 / 厂商日历）之间的唯一桥梁。
 * 用的是系统自带的 **Calendar Provider**，不是任何第三方 SDK：
 * 所有合规的日历 App 都把数据写在这同一个库里，读它 = 读到全部日程。
 *
 * 两件事：
 *
 * ## 一、读：把系统日历的日程搬进 DAKA 日历视图
 * 查的是 `Instances` 表而不是 `Events` 表，这是关键。
 * Events 表里「每周一 9 点开会」只有**一条**记录；
 * Instances 表是系统按重复规则**展开后**的结果，
 * 问它「9 月份有哪些日程」，它会把四个周一都返回给你。
 * 我们不用自己实现一遍 RRULE 解析——系统已经做完了。
 *
 * ## 二、写：把习惯提醒同步进系统日历
 * 一个习惯 = 一条带 RRULE 的事件，再挂一条 0 分钟的 Reminder 让它真的会响。
 * 这样用户在谷歌日历 / 系统日历里也能看到和收到提醒。
 *
 * ⚠️ 必须申请 READ_CALENDAR / WRITE_CALENDAR 两个危险权限（Android 6 起动态申请）。
 * 没权限时 ContentResolver 会抛 SecurityException，**不是返回空**——
 * 所以每个入口都要先查权限，不能直接 try。
 *
 * ⚠️ 各家 ROM 的 Calendar Provider 实现质量参差不齐：
 * 有的列查不到、有的写入被静默拒绝。所以这里对游标取值一律用
 * `columnIndexOrNull` 的容错写法——列不存在就降级，绝不让 App 崩。
 */
class CalendarRepository(private val context: Context) {

    companion object {
        private const val TAG = "CalendarRepository"

        /**
         * 写进系统日历的事件标记，靠它下次同步时找回「哪些是我们写的」。
         *
         * 存在 UID_2445 列（iCalendar 的 UID 字段）里，而不是看起来更贴切的 SYNC_ID1：
         * 因为 **SYNC_ID1 不在公开 API 里**（它在 CalendarContract 的内部类上，
         * 第三方 App 引用不到，引用了会编译失败）。
         * UID_2445 是 EventsColumns 的正式公开列，可写、可查，且语义上就是
         * 「这条事件的全局唯一标识」，拿来当我们的标记正合适。
         */
        private const val UID_PREFIX = "daka-habit-"

        /** 一个习惯事件在时间轴上占多长（分钟）。只是为了让日历上有个看得见的条 */
        private const val EVENT_DURATION_MINUTES = 30L

        /**
         * 日历访问级别 ≥ CONTRIBUTOR(500) 才能写入。
         * 低于这个（READ=200 / FREEBUSY=100）只能看，写了会被 provider 拒绝。
         */
        private const val ACCESS_CONTRIBUTOR = 500
    }

    // ------------------------------------------------------------------
    // 权限
    // ------------------------------------------------------------------

    fun hasReadPermission(): Boolean = has(Manifest.permission.READ_CALENDAR)

    fun hasWritePermission(): Boolean = has(Manifest.permission.WRITE_CALENDAR)

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------
    // 读：日历账户
    // ------------------------------------------------------------------

    /**
     * 列出**可以写入**的日历账户。
     *
     * 只读日历（比如订阅的节假日日历、别人的共享日历）会过滤掉，
     * 让用户选了之后写不进去，是最糟糕的体验。
     */
    fun listWritableCalendars(): List<CalendarAccount> {
        if (!hasReadPermission()) return emptyList()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )

        return queryCalendarAccounts(projection).filter { it.second >= ACCESS_CONTRIBUTOR }
            .map { it.first }
    }

    /** 查所有日历账户，同时带出访问级别（用于过滤可写的） */
    private fun queryCalendarAccounts(
        projection: Array<String>
    ): List<Pair<CalendarAccount, Int>> {
        val result = mutableListOf<Pair<CalendarAccount, Int>>()

        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLongOrNull(CalendarContract.Calendars._ID) ?: continue
                    val access = cursor.getIntOrNull(
                        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
                    ) ?: 0
                    result += CalendarAccount(
                        id = id,
                        displayName = cursor.getStringOrNull(
                            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
                        ) ?: "未命名日历",
                        accountName = cursor.getStringOrNull(
                            CalendarContract.Calendars.ACCOUNT_NAME
                        ) ?: "",
                        color = cursor.getIntOrNull(CalendarContract.Calendars.CALENDAR_COLOR)
                            ?: 0xFF6750A4.toInt(),
                        isPrimary = (cursor.getIntOrNull(CalendarContract.Calendars.IS_PRIMARY)
                            ?: 0) != 0
                    ) to access
                }
            }
        }.onFailure {
            Log.w(TAG, "读日历账户失败", it)
        }

        return result
    }

    /** 日历 id → 账户信息。给日程打上「属于哪个日历」的标签用 */
    private fun calendarMap(): Map<Long, CalendarAccount> =
        queryCalendarAccounts(
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
            )
        ).associate { it.first.id to it.first }

    // ------------------------------------------------------------------
    // 读：某段时间内的日程
    // ------------------------------------------------------------------

    /**
     * 查询一段时间内的全部日程（重复事件已由系统展开）。
     *
     * @param startMillis 起始时刻（含）
     * @param endMillis   结束时刻（不含）
     */
    fun eventsInRange(startMillis: Long, endMillis: Long): List<CalendarEvent> {
        if (!hasReadPermission()) return emptyList()

        // Instances 的 URI 是「路径分段传参」的怪异设计：
        // content://com.android.calendar/instances/when/<start>/<end>
        // 必须用 ContentUris.appendId 依次拼上开始和结束时间，
        // 写成 selection 条件是不生效的（provider 只认 URI 里的值）
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, startMillis)
            ContentUris.appendId(this, endMillis)
        }.build()

        // CALENDAR_ID 是从 Events 表带过来的（Instances 是 Events 的视图）。
        // 部分 ROM 的实现可能不暴露它，所以取值时做了容错：拿不到就归到 -1
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID
        )

        val calendars = calendarMap()
        val result = mutableListOf<CalendarEvent>()

        runCatching {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                // 不传排序就用 provider 默认（按 begin 升序），这里显式写出来更保险
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId =
                        cursor.getLongOrNull(CalendarContract.Instances.EVENT_ID) ?: continue
                    val start = cursor.getLongOrNull(CalendarContract.Instances.BEGIN) ?: continue
                    val calendarId = cursor.getLongOrNull(CalendarContract.Events.CALENDAR_ID) ?: -1L
                    val account = calendars[calendarId]

                    result += CalendarEvent(
                        eventId = eventId,
                        title = cursor.getStringOrNull(CalendarContract.Instances.TITLE)
                            ?.ifBlank { "(无标题)" } ?: "(无标题)",
                        startMillis = start,
                        endMillis = cursor.getLongOrNull(CalendarContract.Instances.END)
                            ?: start + EVENT_DURATION_MINUTES * 60_000L,
                        allDay = (cursor.getIntOrNull(CalendarContract.Instances.ALL_DAY) ?: 0) != 0,
                        calendarId = calendarId,
                        calendarName = account?.displayName ?: "",
                        calendarColor = account?.color ?: 0xFF6750A4.toInt()
                    )
                }
            }
        }.onFailure {
            Log.w(TAG, "读日程失败", it)
        }

        return result
    }

    // ------------------------------------------------------------------
    // 写：把习惯提醒同步进系统日历
    // ------------------------------------------------------------------

    /**
     * 把「开启了提醒的习惯」全部同步到指定日历。
     *
     * 策略是**全量重刷**：先删掉上次写的，再整体重写。
     * 为什么不做增量 diff？因为习惯可能被改了规则、删了、关了提醒，
     * 逐条比对哪些变了、哪些没变的复杂度远高于「全删全写」，
     * 而自用场景下习惯最多几十个，全量写入几十条事件耗时可以忽略。
     *
     * @param habits     要同步的习惯（调用方负责过滤「只传开启提醒的」）
     * @param calendarId 目标日历 id
     * @param knownEventIds 上次同步时记录的 habitId→eventId 映射，用于精准删除
     * @return 本次写入后新的 habitId→eventId 映射
     */
    fun syncHabits(
        habits: List<Habit>,
        calendarId: Long,
        knownEventIds: Map<Long, Long> = emptyMap()
    ): Map<Long, Long> {
        if (!hasWritePermission()) return emptyMap()

        // ① 先清掉上一轮写进去的，避免重复累积
        clearSyncedEvents(calendarId, knownEventIds)

        val newMapping = LinkedHashMap<Long, Long>()

        habits.forEach { habit ->
            val eventId = insertHabitEvent(habit, calendarId)
            if (eventId != null) newMapping[habit.id] = eventId
        }

        return newMapping
    }

    /** 插入一条习惯提醒事件（含 RRULE 和 0 分钟提醒） */
    private fun insertHabitEvent(habit: Habit, calendarId: Long): Long? {
        // DTSTART 必须是「第一个会真实发生的时刻」：
        // 规则里写的开始日期可能已经过去了，但日历事件不能从过去开始（否则日历上会显示一堆灰条目）
        val firstDate = run {
            val today = LocalDate.now()
            ReminderRule.expand(habit, today, today.plusYears(1)).firstOrNull()
        } ?: return null // 往后一年一次都不匹配（例如「每月 31 号」+ 结束日期已过）→ 不写

        val startMillis = firstDate
            .atTime(habit.reminderHour, habit.reminderMinute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, "${habit.emoji} ${habit.name}")
            put(CalendarContract.Events.DESCRIPTION, "DAKA 习惯提醒：${ReminderRule.describe(habit)}")
            // 时区必须显式写。不写的话部分 ROM 会把事件当成 UTC，显示时间差 8 小时
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(
                CalendarContract.Events.DTEND,
                startMillis + EVENT_DURATION_MINUTES * 60_000L
            )
            put(CalendarContract.Events.ALL_DAY, 0)
            // HAS_ALARM 必须置 1，否则下面挂的 Reminder 不会生效
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.UID_2445, UID_PREFIX + habit.id)
            ReminderRule.toRRule(habit)?.let { put(CalendarContract.Events.RRULE, it) }
        }

        val eventId = runCatching {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?.lastPathSegment
                ?.toLongOrNull()
        }.onFailure {
            Log.w(TAG, "写入日程失败：${habit.name}", it)
        }.getOrNull() ?: return null

        addReminder(eventId)
        return eventId
    }

    /**
     * 给事件挂一条「提前 0 分钟」的提醒。
     *
     * 少了这一步，事件躺在日历里但**不会响**——
     * 日历事件和日历提醒是两张表，插了事件不等于插了闹钟。
     */
    private fun addReminder(eventId: Long) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 0)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        runCatching {
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
        }.onFailure {
            Log.w(TAG, "挂日程提醒失败", it)
        }
    }

    /**
     * 清掉本 App 之前写进系统日历的事件。关闭同步、换日历时都要调。
     *
     * 两条路径都走一遍：
     *   1. 按记录的 eventId 直接删（最准）
     *   2. 按 UID 标记查出来再删（兜底：映射丢了、或上次同步被中断留下残骸）
     */
    fun clearSyncedEvents(calendarId: Long?, knownEventIds: Map<Long, Long> = emptyMap()) {
        if (!hasWritePermission()) return

        knownEventIds.values.forEach { eventId -> deleteEventById(eventId) }

        val selection = buildString {
            append("${CalendarContract.Events.UID_2445} LIKE ?")
            if (calendarId != null) append(" AND ${CalendarContract.Events.CALENDAR_ID} = ?")
        }
        val args = buildList {
            add("$UID_PREFIX%")
            if (calendarId != null) add(calendarId.toString())
        }.toTypedArray()

        runCatching {
            context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selection, args)
        }.onFailure {
            Log.w(TAG, "清理已同步日程失败", it)
        }
    }

    /** 按 eventId 删单个事件。删不掉（已经没了）就静默跳过 */
    private fun deleteEventById(eventId: Long) {
        runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(uri, null, null)
        }.onFailure {
            Log.w(TAG, "删除日程 $eventId 失败", it)
        }
    }

    /** 校验某个日历 id 现在还有效（用户可能在系统日历里删了它） */
    fun calendarExists(calendarId: Long): Boolean =
        listWritableCalendars().any { it.id == calendarId }

    // ------------------------------------------------------------------
    // 游标容错取值
    // ------------------------------------------------------------------

    /**
     * 按列名取值，列不存在时返回 null 而不是抛异常。
     *
     * 为什么不用 getColumnIndexOrThrow？因为国产 ROM 的 Calendar Provider
     * 经常少暴露几个列。一个列查不到就让整个日历页崩掉，代价太大——
     * 降级成「这一列当作没值」最多是显示少点信息。
     */
    private fun Cursor.columnIndexOrNull(column: String): Int? =
        getColumnIndex(column).takeIf { it >= 0 }

    private fun Cursor.getLongOrNull(column: String): Long? =
        columnIndexOrNull(column)?.let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.getIntOrNull(column: String): Int? =
        columnIndexOrNull(column)?.let { if (isNull(it)) null else getInt(it) }

    private fun Cursor.getStringOrNull(column: String): String? =
        columnIndexOrNull(column)?.let { if (isNull(it)) null else getString(it) }
}

/** 某天 00:00 的时间戳（本地时区） */
fun LocalDate.startOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 次日 00:00 的时间戳（本地时区）。作为查询区间的右开边界 */
fun LocalDate.endOfDayMillis(): Long =
    plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** LocalDateTime → 毫秒（本地时区） */
fun LocalDateTime.toMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
