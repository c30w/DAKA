package com.marvin.daka.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 一个习惯的「附加提醒」——多条提醒时，除了存在 habits 表上的主提醒，
 * 其余的每条都单独存一行在这里。
 *
 * 为什么不复用 habits 表？因为一个习惯可以有 N 条提醒，是 1:N 关系，
 * 一张表放不下（除非把提醒拼成字符串，那是反模式）。独立成表 + habitId 外键最干净。
 *
 * 字段和 Habit 的提醒列一一对应（只是名字统一成 reminderXxx 前缀，便于 ReminderLike 直接复用）。
 */
@Serializable
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = Habit::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("habitId")]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    override val habitId: Long,
    override val reminderEnabled: Boolean = true,
    override val reminderHour: Int = 21,
    override val reminderMinute: Int = 0,
    override val repeatType: Int = RepeatType.DAILY.code,
    override val repeatInterval: Int = 1,
    override val repeatWeekdays: String = "",
    override val repeatMonthDays: String = "",
    override val endType: Int = EndType.NEVER.code,
    override val repeatTimes: Int = 0,
    override val remindEndDate: String = "",
    override val firedCount: Int = 0,
    override val remindStartDate: String = ""
) : ReminderLike {
    /** 附加提醒的 reminderId 就是自己的主键 id */
    override val reminderId: Long get() = id
}
