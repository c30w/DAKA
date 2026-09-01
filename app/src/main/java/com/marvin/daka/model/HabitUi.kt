package com.marvin.daka.model

/**
 * 界面真正要显示的一条习惯 —— 数据库里的 [Habit] 加上「算出来」的三个状态。
 *
 * 为什么不直接把 streak / doneToday / last7 塞进 [Habit]？
 * 因为这三个值是**推导出来的**，不是事实：
 *   - doneToday = 今天有没有这个习惯的打卡记录
 *   - streak    = 从今天（或昨天）往前数，连续有记录的天数
 *   - last7     = 最近 7 天每天有没有记录
 * 存进数据库就等于把同一份信息存了两份，两份迟早对不上，
 * 而这种 bug 极难查（用户会看到「记录里今天打了卡，但卡片显示没打」）。
 *
 * 原则：**数据库只存事实（打卡记录），状态一律实时算。**
 *
 * 这一层从 M3 活到 M4：M3 在 Composable 里算，M4 挪进 ViewModel 算，
 * 界面始终只认 [HabitUi]，所以换架构时界面代码几乎不用动。
 */
data class HabitUi(
    val id: Long,
    val name: String,
    val emoji: String,
    /** 主题色 ARGB，M5 起由用户新建时选择 */
    val colorArgb: Long,
    /** 连续打卡天数 */
    val streak: Int,
    /** 今天是否已打卡 */
    val doneToday: Boolean,
    /** 最近 7 天的打卡情况：下标 0 = 6 天前，下标 6 = 今天（从左到右、从旧到新） */
    val last7: List<Boolean> = List(7) { false },
    /** V4：是否置顶。卡片右上角画个小图钉 */
    val pinned: Boolean = false,
    /** V4：分类名。首页按分类筛选 */
    val category: String = HabitCategory.DEFAULT
)
