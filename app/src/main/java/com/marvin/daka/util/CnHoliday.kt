package com.marvin.daka.util

import java.time.LocalDate

/**
 * 中国法定节假日判断 —— 「工作日」提醒模式的真相之源。
 *
 * 为什么不联网查？
 * 纯本地 App 的原则：不引第三方 SDK、不依赖网络。 Holidays 每年就几十天，
 * 内置一张表最稳：没网也准、没后端也准、也没有 SDK 收集隐私的嫌疑。
 *
 * 数据来源：国务院办公厅《关于2026年部分节假日安排的通知》（国办发明电〔2025〕7号）。
 *
 * 表的覆盖策略：
 *   - 2026 年内：精确按官方安排（含调休上班的周末）
 *   - 表外的日期（2027 及以后，官方还没发布）：退化为「周末休、工作日上班」的老办法，
 *     误差只出现在法定节假日与调休日，一年也就十几天。
 *     等新安排公布后往 [HOLIDAYS] 里加一年数据即可。
 */
object CnHoliday {

    // ------------------------------------------------------------------
    // 放假日期表。key = 年份，value = 节日名 → 日期区间（含两端）
    // ------------------------------------------------------------------

    private val HOLIDAYS: Map<Int, List<Triple<String, String, String>>> = mapOf(
        2026 to listOf(
            //                节日名    开始       结束
            Triple("元旦", "2026-01-01", "2026-01-03"),
            Triple("春节", "2026-02-15", "2026-02-23"),
            Triple("清明节", "2026-04-04", "2026-04-06"),
            Triple("劳动节", "2026-05-01", "2026-05-05"),
            Triple("端午节", "2026-06-19", "2026-06-21"),
            Triple("中秋节", "2026-09-25", "2026-09-27"),
            Triple("国庆节", "2026-10-01", "2026-10-07")
        )
    )

    /**
     * 调休上班日 —— 官方安排里「周末但要上班」的日子。
     * 「工作日」模式最容易被忽略的就是这批：只看星期几会把它们当休息日漏提醒。
     */
    private val MAKEUP_WORKDAYS: Map<Int, Set<String>> = mapOf(
        2026 to setOf(
            "2026-01-04",  // 周日，补元旦
            "2026-02-14",  // 周六，补春节
            "2026-02-28",  // 周六，补春节
            "2026-05-09",  // 周六，补劳动节
            "2026-09-20",  // 周日，补国庆
            "2026-10-10"   // 周六，补国庆
        )
    )

    // ------------------------------------------------------------------
    // 查询 API。全部纯函数，调用方随便在哪个线程用
    // ------------------------------------------------------------------

    /**
     * 这天是不是「工作日」（该上班/该正常过日子）。
     *
     * 判定优先级：
     *   1. 调休上班的周末 → 是工作日
     *   2. 法定节假日（含撞上周末的）→ 不是工作日
     *   3. 其余按星期几：周一至五是，周六日不是
     */
    fun isWorkday(date: LocalDate): Boolean {
        val key = date.toString()
        if (MAKEUP_WORKDAYS[date.year]?.contains(key) == true) return true
        if (holidayNameAt(date) != null) return false
        return date.dayOfWeek.value in 1..5
    }

    /** 这天是不是「休息日」（法定节假日或周末）。工作日的反义，周末节假日提醒模式用 */
    fun isDayOff(date: LocalDate): Boolean = !isWorkday(date)

    /**
     * 这天落在哪个法定节假日里，返回节日名；不在任何假期内返回 null。
     * 日历视图可以用它给节日日期做标记。
     */
    fun holidayName(date: LocalDate): String? {
        if (MAKEUP_WORKDAYS[date.year]?.contains(date.toString()) == true) {
            return null  // 调休上班日不算假期，哪怕它是周末
        }
        return holidayNameAt(date)
    }

    /** 只查放假表，不排除调休上班日（那些本来就是周末，不会出现在放假表里） */
    private fun holidayNameAt(date: LocalDate): String? =
        HOLIDAYS[date.year]
            ?.firstOrNull { date >= LocalDate.parse(it.second) && date <= LocalDate.parse(it.third) }
            ?.first
}
