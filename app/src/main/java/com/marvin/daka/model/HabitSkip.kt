package com.marvin.daka.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 「跳过当天」记录：用户主动跳过某习惯某一天，那天既不提醒、也不算断签。
 *
 * 设计要点：
 * - 一行 = (habitId, skipDate) 一条。唯一索引保证不会重复插。
 * - skipDate 用 "yyyy-MM-dd" 字符串，和打卡记录、提醒规则同一套日期格式。
 * - 外键级联：习惯硬删除时跳过记录一起没（软归档不触发，归档的习惯接收器会自己停）。
 */
@Serializable
@Entity(
    tableName = "habit_skips",
    foreignKeys = [ForeignKey(
        entity = Habit::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["habitId", "skipDate"], unique = true)]
)
data class HabitSkip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val skipDate: String
)
