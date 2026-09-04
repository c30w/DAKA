package com.marvin.daka.reminder

import com.marvin.daka.model.EndType
import com.marvin.daka.model.ReminderLike
import com.marvin.daka.model.RepeatType
import com.marvin.daka.util.CnHoliday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 提醒的重复规则引擎 —— 整个 V3 里最该写单元测试的一块，也是唯一一处纯 Kotlin 逻辑。
 *
 * 为什么单独抽成一个 object？
 * 因为它**不碰 Android、不碰数据库、不碰时间源**（now 是参数传进来的），
 * 这意味着：
 *   1. 可以脱离模拟器直接跑 JVM 单元测试（java.time 在 JVM 上原生可用）
 *   2. 闹钟调度、日历展开、系统日历（RRULE）三处共用同一份规则，
 *      不会出现「App 里周一提醒，同步到系统日历变成周二」这种割裂
 *
 * 一句话概括职责：
 *   **给一个习惯 + 一个日期，回答「这天该不该提醒」；给一段时间范围，回答「哪几天该提醒」。**
 *
 * 所有函数对脏数据的态度一致：**认不出来就当「不提醒」，绝不抛异常**。
 * 一个坏掉的提醒不该让整个 App 崩掉。
 */
object ReminderRule {

    /** 「往后找下一次提醒」最多找多少天。400 天足够覆盖「每年一次」之外的所有场景。 */
    private const val MAX_LOOKAHEAD_DAYS = 400L

    /**
     * endType / 次数上限判断：这个习惯的提醒是不是已经「用完了」。
     *
     * 只对 [EndType.AFTER_TIMES] 生效。举例：设了「提醒 10 次后停止」，
     * 已经响了 10 次 → 之后不再排期，也不再展开到日历上。
     */
    fun isExhausted(habit: ReminderLike): Boolean {
        if (habit.endTypeEnum != EndType.AFTER_TIMES) return false
        if (habit.repeatTimes <= 0) return false
        return habit.firedCount >= habit.repeatTimes
    }

    /** 「提醒 N 次后停止」还剩几次。没设次数上限就返回 null（表示不限）。 */
    fun remainingTimes(habit: ReminderLike): Int? {
        if (habit.endTypeEnum != EndType.AFTER_TIMES) return null
        if (habit.repeatTimes <= 0) return null
        return (habit.repeatTimes - habit.firedCount).coerceAtLeast(0)
    }

    /**
     * 某一天该不该提醒这个习惯。
     *
     * 只判断「日子对不对」，不判断「时间过没过」——那是 [nextTriggerMillis] 的事。
     *
     * @param habit 习惯（含提醒设置）
     * @param date  待判断的日期
     */
    fun matchesDate(habit: ReminderLike, date: LocalDate): Boolean {
        if (!habit.reminderEnabled) return false
        if (isExhausted(habit)) return false

        val start = parseDateOrNull(habit.effectiveStartDate) ?: return false
        if (date.isBefore(start)) return false

        // 结束日期：到那天为止（含当天）
        if (habit.endTypeEnum == EndType.ON_DATE) {
            val end = parseDateOrNull(habit.remindEndDate)
            if (end != null && date.isAfter(end)) return false
        }

        return when (habit.repeatTypeEnum) {
            RepeatType.DAILY -> true

            RepeatType.INTERVAL_DAYS -> {
                // 每 N 天：从开始日期起算，间隔天数能整除才算数。
                // interval 兜底成 1，防止用户存进去 0 导致取模除零崩溃
                val step = habit.repeatInterval.coerceAtLeast(1)
                ChronoUnit.DAYS.between(start, date).mod(step.toLong()) == 0L
            }

            RepeatType.WEEKLY -> {
                val days = habit.weekdaySet
                // 一个都没勾 = 用户还没配，退化成每天（比「一次都不提醒」更符合预期）
                days.isEmpty() || date.dayOfWeek.value in days
            }

            RepeatType.MONTHLY -> {
                val days = habit.monthDaySet
                days.isEmpty() || date.dayOfMonth in days
            }

            // V4：工作日按中国法定节假日判断（含调休上班的周末），
            // 不再是「看星期几」的笨办法——否则国庆假期里的周三也会提醒，调休补班的周六反而不提醒。
            // 表外的年份自动退化为周末判断，见 CnHoliday 的注释。
            RepeatType.WORKDAY -> CnHoliday.isWorkday(date)

            RepeatType.WEEKEND_HOLIDAY -> CnHoliday.isDayOff(date)
        }
    }

    /**
     * 算出下一次提醒的时间戳（毫秒）。
     *
     * 逻辑：从今天开始逐日往后找，找到第一个「日子匹配、且那个时刻还没过」的日期。
     *
     * 为什么不直接用「明天同一时刻」？
     * 因为规则可能是「每周一三五」——今天是周三，下一次是周五，不是明天。
     * 逐日扫是最笨也最不会错的办法，400 次循环在手机上耗时可以忽略。
     *
     * @return 下次触发的毫秒时间戳；null = 这个习惯不需要再提醒了
     *         （关了、用完了、或者往后 400 天内一次都不匹配，比如「每月 31 号」）
     */
    fun nextTriggerMillis(habit: ReminderLike, now: LocalDateTime = LocalDateTime.now()): Long? {
        if (!habit.reminderEnabled) return null
        if (isExhausted(habit)) return null

        for (offset in 0..MAX_LOOKAHEAD_DAYS) {
            val date = now.toLocalDate().plusDays(offset)
            if (!matchesDate(habit, date)) continue

            val candidate = date.atTime(habit.reminderHour, habit.reminderMinute)
            // 用 isAfter 而不是 !isBefore：刚好同一秒时也算「过了」，
            // 否则会排出一个「立刻触发」的闹钟，表现为开了提醒就马上弹通知
            if (candidate.isAfter(now)) {
                return candidate.atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        }
        return null
    }

    /**
     * 把规则展开成一段时间内的所有日期 —— 日历视图的核心。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 这个习惯在 [from, to] 之间所有会提醒的日期，升序
     */
    fun expand(habit: ReminderLike, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (!habit.reminderEnabled) return emptyList()
        if (to.isBefore(from)) return emptyList()

        val result = mutableListOf<LocalDate>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            if (matchesDate(habit, cursor)) result += cursor
            cursor = cursor.plusDays(1)
        }

        // 「提醒 N 次后停止」：只保留从今天起还剩的那几次。
        // 注意从今天开始截断——过去已经响过的次数已经计进 firedCount 了
        val remaining = remainingTimes(habit) ?: return result
        val today = LocalDate.now()
        val future = result.filter { !it.isBefore(today) }
        val past = result.filter { it.isBefore(today) }
        return past + future.take(remaining)
    }

    // ------------------------------------------------------------------
    // 同步到系统日历：生成 RRULE
    // ------------------------------------------------------------------

    /**
     * 把重复规则翻译成标准 RRULE 字符串（RFC 5545），写进系统日历用。
     *
     * 为什么不用「未来 90 天每天插一条事件」这种笨办法？
     *   1. 系统会按 RRULE 自动展开，我们在日历里只占一条记录，用户手动改也不会乱
     *   2. 「永不结束」的规则展开不完，写死 90 天会让日历在第 91 天凭空消失
     *   3. 数据量小：一个习惯 = 一条事件，不是几百条
     *
     * @param habit 习惯
     * @return RRULE 字符串，例如 "FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=10"；
     *         规则无法表达时返回 null（调用方按「单次事件」处理）
     */
    fun toRRule(habit: ReminderLike): String? {
        val freqPart = when (habit.repeatTypeEnum) {
            RepeatType.DAILY -> "FREQ=DAILY"

            RepeatType.INTERVAL_DAYS ->
                "FREQ=DAILY;INTERVAL=${habit.repeatInterval.coerceAtLeast(1)}"

            RepeatType.WEEKLY -> {
                val days = habit.weekdaySet
                if (days.isEmpty()) "FREQ=DAILY" else "FREQ=WEEKLY;BYDAY=${formatByDay(days)}"
            }

            RepeatType.MONTHLY -> {
                val days = habit.monthDaySet
                if (days.isEmpty()) "FREQ=DAILY" else "FREQ=MONTHLY;BYMONTHDAY=${days.sorted().joinToString(",")}"
            }

            // 中国法定节假日没法用 RRULE 表达（它是每年国务院发文的，没有固定公历日期），
            // 写进系统日历只能退化为「周一到周五」的近似——系统日历里看会有一点点偏差，
            // App 内的闹钟走 matchesDate 是准的。这是标准 RRULE 的能力边界，不是 bug。
            RepeatType.WORKDAY -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"

            RepeatType.WEEKEND_HOLIDAY -> "FREQ=WEEKLY;BYDAY=SA,SU"
        }

        // 结束条件：COUNT（次数）和 UNTIL（日期）在 RFC 5545 里**互斥**，
        // 只能二选一，两个都写属于非法 RRULE，各家日历解析行为不一致。
        // 优先级：次数 > 日期（次数更具体）
        val endPart = when (habit.endTypeEnum) {
            EndType.NEVER -> ""

            EndType.AFTER_TIMES -> {
                val remaining = remainingTimes(habit)
                if (remaining == null) "" else ";COUNT=${remaining.coerceAtLeast(1)}"
            }

            EndType.ON_DATE -> {
                val end = parseDateOrNull(habit.remindEndDate)
                // UNTIL 必须是 UTC 时刻。取当天 23:59:59，保证结束日当天还会提醒
                if (end == null) "" else {
                    // 结束日当天的 23:59:59 UTC —— 保证「结束日」当天仍然提醒
                    val until = end.atTime(23, 59, 59).atZone(ZoneOffset.UTC)
                    ";UNTIL=${RRULE_UNTIL.format(until)}"
                }
            }
        }

        return freqPart + endPart
    }

    /** UNTIL 的格式化器：20261231T235959Z，必须是 UTC */
    private val RRULE_UNTIL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    /** ISO 星期数字集合 → RRULE 的 BYDAY 片段。例：{1,3,5} → "MO,WE,FR" */
    private fun formatByDay(days: Set<Int>): String {
        val names = arrayOf("", "MO", "TU", "WE", "TH", "FR", "SA", "SU")
        return days.sorted()
            .mapNotNull { names.getOrNull(it) }
            .joinToString(",")
            .ifEmpty { "MO,TU,WE,TH,FR,SA,SU" }
    }

    // ------------------------------------------------------------------
    // 界面文案
    // ------------------------------------------------------------------

    /** 把一套提醒设置说成人话，给列表和日历用。例：每周一、三、五 07:30 · 提醒 10 次后停止 */
    fun describe(habit: ReminderLike): String {
        if (!habit.reminderEnabled) return "未开启提醒"

        val time = "%02d:%02d".format(habit.reminderHour, habit.reminderMinute)

        val repeat = when (habit.repeatTypeEnum) {
            RepeatType.DAILY -> "每天"
            RepeatType.INTERVAL_DAYS -> {
                val n = habit.repeatInterval.coerceAtLeast(1)
                if (n == 1) "每天" else "每 $n 天"
            }
            RepeatType.WEEKLY -> {
                val days = habit.weekdaySet
                if (days.isEmpty()) "每天" else "每周${formatWeekdayCn(days)}"
            }
            RepeatType.MONTHLY -> {
                val days = habit.monthDaySet
                if (days.isEmpty()) "每天" else "每月 ${days.sorted().joinToString("、")} 号"
            }
            RepeatType.WORKDAY -> "工作日（法定节假日自动跳过，调休补班会提醒）"
            RepeatType.WEEKEND_HOLIDAY -> "周末及节假日"
        }

        val end = when (habit.endTypeEnum) {
            EndType.NEVER -> ""
            EndType.AFTER_TIMES -> {
                val total = habit.repeatTimes
                " · 共 $total 次，已提醒 ${habit.firedCount} 次"
            }
            EndType.ON_DATE -> {
                if (habit.remindEndDate.isBlank()) "" else " · 至 ${habit.remindEndDate}"
            }
        }

        return "$repeat $time$end"
    }

    /** 星期数字集合 → 中文。例：{1,3,5} → "一、三、五" */
    private fun formatWeekdayCn(days: Set<Int>): String {
        val names = arrayOf("", "一", "二", "三", "四", "五", "六", "日")
        return days.sorted().mapNotNull { names.getOrNull(it) }.joinToString("、")
    }

    /** 安全解析日期。脏数据返回 null，由调用方决定降级行为，绝不抛异常 */
    private fun parseDateOrNull(raw: String): LocalDate? =
        raw.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
