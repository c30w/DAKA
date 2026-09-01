package com.marvin.daka.model

/**
 * 提醒的重复方式。
 *
 * 存进数据库的是 [code] 而不是枚举名字：
 * 枚举一旦改名，存的是名字的老数据就读不回来了；
 * 存数字则只要不重新排列数字，怎么改名都安全。
 *
 * 「按次数重复」不在这里——它是**结束条件**（[Habit.repeatTimes]），
 * 不是重复方式。每天/每 3 天/每周一三五 说的是「多久一次」，
 * 「提醒 10 次后停止」说的是「什么时候结束」，两者可以叠加：
 * 「每周一三五提醒，一共提醒 10 次」是完全合理的组合。
 */
enum class RepeatType(val code: Int, val label: String) {
    /** 每天都提醒 */
    DAILY(0, "每天"),

    /** 每 N 天提醒一次，从开始日期起算。典型场景：隔天吃药 */
    INTERVAL_DAYS(1, "每 N 天"),

    /** 每周指定的星期几提醒。典型场景：周一三五健身 */
    WEEKLY(2, "每周"),

    /** 每月指定的几号提醒。典型场景：每月 1 号记账 */
    MONTHLY(3, "每月"),

    /** 工作日（周一至周五）提醒 */
    WORKDAY(4, "工作日"),

    /** 周末及节假日（周六日）提醒 */
    WEEKEND_HOLIDAY(5, "周末 / 节假日");

    companion object {
        /** 从数据库里的数字还原成枚举。认不出来就退回「每天」，绝不让数据把 App 搞崩。 */
        fun of(code: Int): RepeatType = entries.firstOrNull { it.code == code } ?: DAILY
    }
}

/**
 * 提醒的结束方式。
 *
 * 同样存数字，理由见 [RepeatType]。
 */
enum class EndType(val code: Int, val label: String) {
    /** 一直提醒下去 */
    NEVER(0, "永不结束"),

    /** 提醒满 N 次后自动停止 */
    AFTER_TIMES(1, "N 次后停止"),

    /** 到指定日期为止 */
    ON_DATE(2, "到日期结束");

    companion object {
        fun of(code: Int): EndType = entries.firstOrNull { it.code == code } ?: NEVER
    }
}
