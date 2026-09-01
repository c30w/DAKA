package com.marvin.daka.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.marvin.daka.MainActivity
import kotlinx.coroutines.runBlocking

/**
 * 桌面小组件：1×1，极简版——只显示「今天已完成 N」。
 *
 * V4.7 新增。空间只有一格，塞不下标题和说明，就突出**那个数字**：
 * 用户扫一眼桌面就能看到今天打了几项，比看一堆文字更直观。
 * 全部完成时数字换成绿色并带个对勾，一眼看出「搞定」。
 */
class HabitWidgetSmall : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val list = runBlocking { loadTodayHabits(context) }
        val done = list.count { it.second }
        val total = list.size
        val allDone = total > 0 && done >= total

        val openAppAction = actionStartActivity(MainActivity::class.java, actionParametersOf())

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .background(ColorProvider(Color(0xFF1E1E2E)))
                    .cornerRadius(16.dp)
                    .clickable(openAppAction),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (allDone) "✓" else "今",
                    style = TextStyle(
                        color = ColorProvider(if (allDone) Color(0xFF98C379) else Color(0xFFABB2BF)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = "$done",
                    style = TextStyle(
                        color = ColorProvider(if (allDone) Color(0xFF98C379) else Color(0xFFFFB74D)),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = if (total == 0) "无习惯" else "已打$total 项",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFABB2BF)),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
