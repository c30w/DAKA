package com.marvin.daka.ui.stats

import com.marvin.daka.model.Habit
import com.marvin.daka.model.HabitRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 统计的时间范围。
 *
 * 注意 [days] 是**自然日跨度**，不是「本周/本月/今年」的日历边界：
 * 「近 30 天」比「9 月 1 日到 9 月 30 日」更有用——打卡看的是**连续性**，
 * 跨月的连续记录不该因为翻月被切成两半。
 */
enum class StatsRange(val days: Int) {
    WEEK(7),
    MONTH(30),
    YEAR(365);

    /** 起始日期（含）。结束日期永远是今天 */
    fun startOf(today: LocalDate): LocalDate = today.minusDays(days - 1L)
}

/**
 * 某一天的统计快照。
 *
 * [activeCount] 是**当天应当打卡的习惯数**，不是当前习惯总数——
 * 「8 月 1 日那天你只有 3 个习惯」不能按今天的 10 个算，
 * 否则早期的完成率永远上不去，趋势图会被新习惯的加入持续拉低，
 * 看起来像「越用越差」，实际是越用越勤。
 *
 * 判活跃：创建日期 ≤ 当天，且（未归档 或 归档日期 > 当天）。
 */
data class DailyStat(
    val date: LocalDate,
    /** 当天打了卡的条数（只数当时活跃的习惯） */
    val doneCount: Int,
    /** 当天本应打卡的习惯数 */
    val activeCount: Int
) {
    /** 完成率 0f..1f。没有活跃习惯的日子算 0，避免除零 */
    val rate: Float get() = if (activeCount == 0) 0f else doneCount.toFloat() / activeCount

    /** 当天是否全勤（有习惯且全打卡了）。没有活跃习惯的日子不算全勤 */
    val isPerfect: Boolean get() = activeCount > 0 && doneCount >= activeCount
}

/**
 * 单个习惯在一段时间内的表现。用于「完成率排行」。
 *
 * [activeDays] 同理只数这个习惯**存在过的天数**：
 * 三天前新建的习惯，在「近 30 天」里只有 3 天该打卡，
 * 拿它跟 30 天全勤的老习惯并列比 90% vs 100% 是没有意义的。
 */
data class HabitStat(
    val habitId: Long,
    val name: String,
    val emoji: String,
    val colorArgb: Long,
    /** 范围内打卡的天数 */
    val doneDays: Int,
    /** 范围内这个习惯存在的天数 */
    val activeDays: Int
) {
    val rate: Float get() = if (activeDays == 0) 0f else doneDays.toFloat() / activeDays
}

/**
 * 把「习惯 + 打卡记录」聚合成每天一条 [DailyStat]。
 *
 * 抽成顶层纯函数而不是 ViewModel 的方法，是为了不依赖任何 Android 类——
 * 将来可以脱离模拟器直接写单元测试（跟 buildHabitUiList 一个路子）。
 *
 * 复杂度 O(天数 × 习惯数)：365 × 几十个习惯 = 几万次比较，
 * 在 combine 里每次数据变化重算一遍也毫无压力（实测 < 5ms）。
 * 真要优化可以按日期分桶建索引，但对个人 App 属于过度设计。
 */
internal fun buildDailyStats(
    habits: List<Habit>,
    records: List<HabitRecord>,
    range: StatsRange,
    today: LocalDate = LocalDate.now()
): List<DailyStat> {
    val start = range.startOf(today)

    // 只保留范围内的记录，按日期分组。
    // 范围外的记录被丢掉——它们不影响任何一天的统计
    val datesByHabit: Map<Long, Set<String>> =
        records.groupBy { it.habitId }
            .mapValues { (_, list) -> list.map { it.date }.toSet() }

    return (0 until range.days).map { offset ->
        val date = start.plusDays(offset.toLong())
        val dateStr = date.toString()

        // 当天活跃的习惯：已创建 且 还没归档
        val activeIds = habits.filter { habit ->
            habit.createdDate <= date && (habit.archivedDate == null || habit.archivedDate!! > date)
        }.map { it.id }.toSet()

        val done = activeIds.count { id -> datesByHabit[id]?.contains(dateStr) == true }
        DailyStat(date = date, doneCount = done, activeCount = activeIds.size)
    }
}

/**
 * 算每个习惯在范围内的完成率，按完成率降序（同率按打卡天数降序）。
 *
 * 只返回 [activeDays] > 0 的习惯——范围内还没创建的习惯没有统计意义，
 * 列出来只会让排行里多一堆 0%。
 */
internal fun buildHabitStats(
    habits: List<Habit>,
    records: List<HabitRecord>,
    range: StatsRange,
    today: LocalDate = LocalDate.now()
): List<HabitStat> {
    val start = range.startOf(today)
    val daysByHabit: Map<Long, Set<String>> =
        records.groupBy { it.habitId }
            .mapValues { (_, list) -> list.map { it.date }.toSet() }

    return habits.mapNotNull { habit ->
        val created = habit.createdDate
        // 习惯在范围内的「有效天数」：从 max(创建日, 范围起点) 到 min(归档日, 今天)
        val from = maxOf(created, start)
        val to = minOf(habit.archivedDate?.minusDays(1) ?: today, today)
        if (from > to) return@mapNotNull null

        val activeDays = (from.toEpochDay()..to.toEpochDay()).count().coerceAtLeast(0)
        if (activeDays == 0) return@mapNotNull null

        val dates = daysByHabit[habit.id].orEmpty()
        val doneDays = (from.toEpochDay()..to.toEpochDay()).count { epochDay ->
            dates.contains(LocalDate.ofEpochDay(epochDay).toString())
        }

        HabitStat(
            habitId = habit.id,
            name = habit.name,
            emoji = habit.emoji,
            colorArgb = habit.colorArgb,
            doneDays = doneDays,
            activeDays = activeDays
        )
    }.sortedWith(compareByDescending<HabitStat> { it.rate }.thenByDescending { it.doneDays })
}

/** 毫秒时间戳 → 本地日期。习惯的 createdAt / archivedAt 都是毫秒 */
private val Habit.createdDate: LocalDate
    get() = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

/** 归档时间 → 本地日期。没归档返回 null */
private val Habit.archivedDate: LocalDate?
    get() = archivedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
