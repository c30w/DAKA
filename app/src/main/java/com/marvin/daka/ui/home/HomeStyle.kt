package com.marvin.daka.ui.home

/**
 * 主页布局风格的可选值（存进 DataStore 的就是这几个字符串）。
 *
 * 把常量集中在 UI 层：AppPrefs 只负责「存一个字符串、读一个字符串」，
 * 不该知道界面有几种布局；界面（设置页、首页）既要显示文案又要落库，常量放这正合适。
 *
 * 三种风格的差异（见 HomeScreen 的渲染分支）：
 *   - [LIST]    列表卡片：每行一张大卡，带 emoji、连续天数、最近 7 天格子、勾选圈；
 *               支持长按拖动排序、左右滑快捷操作，功能最全。
 *   - [GRID]    磁贴网格：两列方格磁贴，emoji 大、信息精简，一眼扫完所有习惯。
 *   - [COMPACT] 紧凑清单：每行一行，名字 + 连续天数 + 勾选圈，最省空间，习惯多时一屏看更多。
 */
object HomeStyle {
    /** 列表卡片（默认） */
    const val LIST = "list"

    /** 磁贴网格 */
    const val GRID = "grid"

    /** 紧凑清单 */
    const val COMPACT = "compact"

    /** 全部可选值，设置页单选用 */
    val ALL = listOf(LIST, GRID, COMPACT)
}
