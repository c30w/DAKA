package com.marvin.daka.model

import java.time.LocalDate

/**
 * 提醒的「统一接口」：无论提醒挂在习惯身上（主提醒，reminderId = 0），
 * 还是独立存在 reminders 表里（附加提醒，reminderId > 0），
 * 对提醒引擎（ReminderRule）、调度器（ReminderScheduler）、接收器（ReminderReceiver）来说
 * 都是「同一套字段」。这个接口让它们不用关心提醒到底存在哪。
 *
 * 字段命名刻意和 Habit 的提醒列、Reminder 实体的字段**完全一致**，
 * 这样 Habit 和 Reminder 只要写 `override val xxx` 就能直接实现，
 * 引擎也只认这套字段，互不耦合。
 */
interface ReminderLike {
    val habitId: Long
    /** 0 = 主提醒（在 habits 表上）；>0 = 附加提醒（reminders 表的主键 id） */
    val reminderId: Long
    val reminderEnabled: Boolean
    val reminderHour: Int
    val reminderMinute: Int
    val repeatType: Int
    val repeatInterval: Int
    val repeatWeekdays: String
    val repeatMonthDays: String
    val endType: Int
    val repeatTimes: Int
    val remindEndDate: String
    val firedCount: Int
    val remindStartDate: String

    // 下面这些是「由上面原始字段算出来的派生属性」，用接口默认实现，
    // Habit / Reminder 不用各自抄一遍。
    val repeatTypeEnum: RepeatType get() = RepeatType.of(repeatType)
    val endTypeEnum: EndType get() = EndType.of(endType)
    val weekdaySet: Set<Int> get() = RepeatParse.intList(repeatWeekdays).toSet()
    val monthDaySet: Set<Int> get() = RepeatParse.intList(repeatMonthDays).filter { it in 1..31 }.toSet()
    val effectiveStartDate: String
        get() = remindStartDate.ifBlank { LocalDate.now().toString() }
}
