package com.marvin.daka.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 1×4 小组件的 Receiver。
 *
 * 和 [HabitWidgetReceiver] 一样，只是绑定到 [HabitWidgetWide]。
 */
class HabitWidgetWideReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidgetWide()
}
