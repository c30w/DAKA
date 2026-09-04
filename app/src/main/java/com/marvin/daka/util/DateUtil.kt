package com.marvin.daka.util

import java.time.LocalDate

/**
 * 「今天」的字符串形式，格式 "2026-08-30"。
 *
 * 全 App 统一用它表示日期，不要到处写 LocalDate.now().toString()。
 * 统一成一个函数的好处：将来要改日期规则（比如想支持「凌晨 4 点才算新的一天」）只改一处。
 */
fun todayString(): String = LocalDate.now().toString()

/**
 * 计算连续打卡天数（streak）。
 *
 * @param records 这个习惯**所有**打卡日期的集合，元素形如 "2026-08-30"
 * @param today   今天，默认取系统当天（参数化是为了好写测试）
 *
 * 算法：从今天开始往回逐日查，遇到空缺就停。
 *
 * 最关键的一行是下面那个 if：
 * **如果今天还没打卡，从昨天开始数，而不是直接返回 0。**
 *
 * 为什么？因为「今天还没打卡」是**正常状态**，不是断签——
 * 用户早上八点打开 App，昨晚打过了、今天还没打，连续天数应该还是原来的数。
 * 要是这里直接返回 0，用户每天早上都会看到连续记录归零，体验极差。
 */
fun calcStreak(records: Set<String>, today: LocalDate = LocalDate.now()): Int {
    var cursor = today

    // 今天没打卡不算断签，从昨天开始数
    if (!records.contains(cursor.toString())) {
        cursor = cursor.minusDays(1)
    }

    var streak = 0
    while (records.contains(cursor.toString())) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/**
 * 计算历史最长连续打卡天数（最佳连击）。
 *
 * 与 [calcStreak]（数到今天为止的当前连击）不同，这里数的是**历史任意一段**
 * 最长的连续天数——可能早就断了，但曾经坚持过的最长记录。
 *
 * @param dates 这个习惯所有打卡日期的集合，元素形如 "2026-08-30"
 * @return 最长连续天数；空集合返回 0
 */
fun calcBestStreak(dates: Set<String>): Int {
    if (dates.isEmpty()) return 0
    // ISO 日期字符串排序 == 按时间排序；转 epochDay 方便判断「是否相邻」
    val epochs = dates.mapNotNull { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() }
        .distinct()
        .sorted()
    var best = 1
    var cur = 1
    for (i in 1 until epochs.size) {
        cur = if (epochs[i] == epochs[i - 1] + 1) cur + 1 else 1
        if (cur > best) best = cur
    }
    return best
}

/**
 * 最近 7 天的打卡情况，用于画那排 7 格小方块。
 *
 * @return 长度固定为 7 的 Boolean 列表，**下标 0 = 6 天前，下标 6 = 今天**（也就是从左到右、从旧到新）。
 *
 * 顺序别搞反：用户看热力条时，最右边那格必须是今天，这样才符合直觉。
 */
fun last7Days(records: Set<String>, today: LocalDate = LocalDate.now()): List<Boolean> =
    (6 downTo 0).map { daysAgo ->
        records.contains(today.minusDays(daysAgo.toLong()).toString())
    }

/** 最近 7 天对应的「周几」文字，供无障碍播报用，例如 [周一, 周二, ... 周日] */
fun last7WeekdayLabels(today: LocalDate = LocalDate.now()): List<String> =
    (6 downTo 0).map { daysAgo ->
        today.minusDays(daysAgo.toLong())
            .dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.CHINA)
    }
