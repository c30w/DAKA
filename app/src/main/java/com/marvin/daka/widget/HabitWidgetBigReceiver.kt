package com.marvin.daka.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 4×3 小组件的 Receiver，绑定到 [HabitWidgetBig]。
 * 每个尺寸的小组件都要单独声明一个 receiver（+ 独立 info.xml），系统才认得出
 * 这是「另一个可添加的小组件」。
 */
class HabitWidgetBigReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidgetBig()
}
