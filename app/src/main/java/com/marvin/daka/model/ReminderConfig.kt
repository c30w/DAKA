package com.marvin.daka.model

import com.marvin.daka.util.todayString
import java.time.LocalDate

/**
 * 一套完整的提醒设置 —— 界面 ↔ ViewModel ↔ 数据库之间传递提醒配置的载体。
 *
 * 为什么不直接传 [Habit]？因为编辑提醒时我们手上往往只有「改了哪几个开关」，
 * 没有完整的习惯对象。传一个专门的载体，调用方不用为了改提醒
 * 而先去数据库把整个习惯捞出来（多一次查询，还可能拿到过期数据）。
 *
 * 用枚举而不是 Int：这层是**代码内部**传递，不落库，直接用枚举更安全——
 * 编译期就能挡住「传了个不存在的重复类型」这种错误。
 * 落库的那一层（[Habit]）才退化成 Int，那是 Room 的限制，不是我们的选择。
 */
data class ReminderConfig(
    /** 是否开启这个习惯的提醒 */
    val enabled: Boolean = false,

    /** 提醒时间：小时 0-23 */
    val hour: Int = 21,

    /** 提醒时间：分钟 0-59 */
    val minute: Int = 0,

    /** 重复方式 */
    val repeatType: RepeatType = RepeatType.DAILY,

    /** 每 N 天（仅 [RepeatType.INTERVAL_DAYS]） */
    val interval: Int = 1,

    /** 每周的星期几（1=周一 … 7=周日），仅 [RepeatType.WEEKLY] */
    val weekdays: Set<Int> = emptySet(),

    /** 每月的几号，仅 [RepeatType.MONTHLY] */
    val monthDays: Set<Int> = emptySet(),

    /** 结束方式 */
    val endType: EndType = EndType.NEVER,

    /** [EndType.AFTER_TIMES]：总共提醒多少次 */
    val times: Int = 0,

    /** [EndType.ON_DATE]：结束日期 yyyy-MM-dd，空 = 不限 */
    val endDate: String = "",

    /** 提醒开始日期 yyyy-MM-dd，空 = 从今天开始 */
    val startDate: String = ""
) {
    companion object {
        /** 从一个已有习惯读出它的提醒配置，编辑页用它做初始值 */
        fun from(habit: Habit): ReminderConfig = ReminderConfig(
            enabled = habit.reminderEnabled,
            hour = habit.reminderHour,
            minute = habit.reminderMinute,
            repeatType = habit.repeatTypeEnum,
            interval = habit.repeatInterval,
            weekdays = habit.weekdaySet,
            monthDays = habit.monthDaySet,
            endType = habit.endTypeEnum,
            times = habit.repeatTimes,
            endDate = habit.remindEndDate,
            startDate = habit.remindStartDate
        )

        /** 「关闭提醒」的默认配置。时间沿用传入值，免得用户关了再开要重设 */
        fun disabled(hour: Int = 21, minute: Int = 0) =
            ReminderConfig(enabled = false, hour = hour, minute = minute)

        /** 从一个附加提醒（reminders 表的一行）读出它的配置，编辑页用它做初始值 */
        fun from(reminder: Reminder): ReminderConfig = ReminderConfig(
            enabled = reminder.reminderEnabled,
            hour = reminder.reminderHour,
            minute = reminder.reminderMinute,
            repeatType = reminder.repeatTypeEnum,
            interval = reminder.repeatInterval,
            weekdays = reminder.weekdaySet,
            monthDays = reminder.monthDaySet,
            endType = reminder.endTypeEnum,
            times = reminder.repeatTimes,
            endDate = reminder.remindEndDate,
            startDate = reminder.remindStartDate
        )
    }
}

/**
 * 把提醒配置转成 reminders 表里的一行（附加提醒用）。
 *
 * 和 [Habit.withReminder] 是同一套映射的两个出口：withReminder 填 habits 的主提醒列，
 * 这里填 reminders 表的附加提醒行。startDate 空则落今天，和主提醒保持一致。
 */
fun ReminderConfig.toReminder(habitId: Long): Reminder = Reminder(
    habitId = habitId,
    reminderEnabled = enabled,
    reminderHour = hour,
    reminderMinute = minute,
    repeatType = repeatType.code,
    repeatInterval = interval.coerceAtLeast(1),
    repeatWeekdays = weekdays.sorted().joinToString(","),
    repeatMonthDays = monthDays.sorted().joinToString(","),
    endType = endType.code,
    repeatTimes = times,
    remindEndDate = endDate,
    remindStartDate = startDate.ifBlank { todayString() }
)

/**
 * 日历上的**一次**提醒 —— 重复规则展开后的结果。
 *
 * [Habit] 里存的是「规则」（每周一三五 7:30），
 * [ReminderOccurrence] 是「具体某一天的一次提醒」（9 月 2 日周三 7:30）。
 * 日历视图要画的是后者，所以必须在显示前把规则展开。
 *
 * 展开是**实时算的，不落库**：规则改了日历立刻跟着变，不需要同步两份数据。
 */
data class ReminderOccurrence(
    val habitId: Long,
    val habitName: String,
    val emoji: String,
    val colorArgb: Long,
    /** 这次提醒落在哪一天 */
    val date: LocalDate,
    val hour: Int,
    val minute: Int,
    /** 规则的人话描述，详情列表里显示 */
    val ruleText: String
)
