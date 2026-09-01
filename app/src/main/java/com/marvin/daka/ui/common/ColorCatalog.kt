package com.marvin.daka.ui.common

/**
 * 主题色目录 —— 新建/编辑习惯页的色板全集。
 *
 * 默认在页面上直接铺 11 个（2 行 × 6 列 - 1 个「更多」入口），
 * 其余进二级对话框。全部用固定 ARGB 而不是 MaterialTheme 动态色：
 * 习惯色是「用户认脸」的标识（首页卡片、小组件、日历条目都靠它区分），
 * 必须跟系统主题解耦——深色模式里这个习惯也还是那个颜色。
 */
object ColorCatalog {

    /** 一个颜色 = ARGB + 中文名（读屏用） */
    data class ColorItem(val argb: Long, val label: String)

    /** 全量色板，40 色。亮度分布均匀，深浅主题下当卡片点缀色都看得清 */
    val ALL: List<ColorItem> = listOf(
        // ---- 默认页面上直接展示的 11 个（第 12 格是「更多」） ----
        ColorItem(0xFF6750A4, "紫色"),
        ColorItem(0xFF2196F3, "蓝色"),
        ColorItem(0xFF4CAF50, "绿色"),
        ColorItem(0xFFFF9800, "橙色"),
        ColorItem(0xFFE91E63, "粉色"),
        ColorItem(0xFF00BCD4, "青色"),
        ColorItem(0xFFF44336, "红色"),
        ColorItem(0xFF9C27B0, "深紫色"),
        ColorItem(0xFF3F51B5, "靛蓝色"),
        ColorItem(0xFF8BC34A, "浅绿色"),
        ColorItem(0xFFFFC107, "金黄色"),
        // ---- 以下在「更多」对话框里 ----
        ColorItem(0xFF795548, "棕色"),
        ColorItem(0xFF607D8B, "蓝灰色"),
        ColorItem(0xFF000000, "黑色"),
        ColorItem(0xFF536DFE, "亮靛蓝"),
        ColorItem(0xFF00E5FF, "亮青色"),
        ColorItem(0xFF00C853, "亮绿色"),
        ColorItem(0xFFC6FF00, "黄绿色"),
        ColorItem(0xFFFFD600, "亮黄色"),
        ColorItem(0xFFFF6D00, "深橙色"),
        ColorItem(0xFFD500F9, "亮紫色"),
        ColorItem(0xFFFF4081, "亮粉色"),
        ColorItem(0xFFB71C1C, "深红色"),
        ColorItem(0xFF880E4F, "酒红色"),
        ColorItem(0xFF4A148C, "暗紫色"),
        ColorItem(0xFF1A237E, "藏青色"),
        ColorItem(0xFF0D47A1, "深蓝色"),
        ColorItem(0xFF006064, "深青色"),
        ColorItem(0xFF1B5E20, "墨绿色"),
        ColorItem(0xFF33691E, "橄榄绿"),
        ColorItem(0xFF827717, "暗金色"),
        ColorItem(0xFFE65100, "焦橙色"),
        ColorItem(0xFFBF360C, "赤褐色"),
        ColorItem(0xFF3E2723, "深棕色"),
        ColorItem(0xFF37474F, "炭灰色"),
        ColorItem(0xFF455A64, "石板灰"),
        ColorItem(0xFF26A69A, "湖水绿"),
        ColorItem(0xFF26C6DA, "天青色"),
        ColorItem(0xFF42A5F5, "天蓝色"),
        ColorItem(0xFF5C6BC0, "鸢尾蓝")
    )

    /** 新建页默认展示的颜色（不含「更多」入口），2 行 × 6 列 - 1 = 11 个 */
    val FEATURED: List<ColorItem> = ALL.take(11)

    /** ARGB → 中文名，读屏用 */
    val LABELS: Map<Long, String> = ALL.associate { it.argb to it.label }
}
