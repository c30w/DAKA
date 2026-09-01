package com.marvin.daka.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 1×1 小组件的 Receiver。
 *
 * 和 [HabitWidgetReceiver] 一样，只是绑定到 [HabitWidgetSmall]。
 * Manifest 里每个尺寸的小组件都要单独声明一个 receiver（provider 元数据也独立），
 * 系统才认得出这是「另一个可添加的小组件」。
 */
class HabitWidgetSmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidgetSmall()
}
