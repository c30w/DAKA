package com.marvin.daka.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 小组件的 Receiver。
 *
 * 系统通过它把小组件实例绑定到 [HabitWidget]，并接收更新/删除回调。
 * 用 Glance 的 Receiver 而不是手写 AppWidgetProvider，
 * 因为 Glance 已经封装好了 provider 需要的回调（onUpdate 等）。
 */
class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}
