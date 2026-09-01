package com.marvin.daka.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 一次打卡记录 —— 「某个习惯在某天打了一次卡」就是一行。
 *
 * 两个关键约束，都是为了防止脏数据：
 *
 * 1. **唯一索引 `(habitId, date)`**：
 *    保证同一个习惯同一天只能有一条记录。没有它，用户手抖点两下就会插入两行，
 *    同一天被打两次卡，统计全乱。有了它，第二次插入会被忽略（我们用的是 IGNORE 策略）。
 *
 * 2. **外键 + onDelete = CASCADE**：
 *    习惯被删除时，它的打卡记录跟着自动删除，不会留下「没有主人的孤儿记录」。
 *
 * 关于 [date] 为什么是 String 而不是时间戳：
 * 「一天」是日历概念，不是时间段。用时间戳存，跨时区、跨夏令时会算出诡异结果；
 * 用 LocalDate.now().toString() 存成 "2026-08-29"，比较和按天查询都极其简单，
 * 排序也天然正确（字典序 = 时间序）。
 *
 * V2 补充：加了 @Serializable，好让备份功能直接把它序列化成 JSON。
 * 注解只是让编译器生成转换代码，不影响 Room 的任何行为。
 */
@Serializable
@Entity(
    tableName = "habit_records",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["habitId", "date"], unique = true)]
)
data class HabitRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    /** 本地日期，格式 "2026-08-29" */
    val date: String,
    val doneAt: Long = System.currentTimeMillis()
)
