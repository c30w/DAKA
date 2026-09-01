package com.marvin.daka.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.marvin.daka.MainActivity
import com.marvin.daka.model.Habit
import kotlinx.coroutines.runBlocking

/**
 * 桌面小组件：1×4，横向「快速打卡条」（Google Keep 清单的横向版）。
 *
 * 左边一块进度（今天 X/N + 进度条），点它打开 App；
 * 右边把习惯一个个排成「卡片」：每张 = 复选框圆 + emoji + 名称，整张可点切换打卡。
 * 因为 1×4 只有一格高，竖着排不下多行，就横着排——但每个习惯依旧是
 * 「清晰的勾选框 + 文字标签 + 整块可点」，和 Keep 一样桌面直接操作、即时同步。
 *
 * 习惯太多时右侧放不下，只展示前 5 个（置顶优先），其余进 App 看。
 */
class HabitWidgetWide : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val list = runBlocking { loadTodayHabits(context) }
        val done = list.count { it.second }
        val total = list.size
        val ratio = if (total == 0) 0f else done.toFloat() / total

        val openAppAction = actionStartActivity(MainActivity::class.java, actionParametersOf())

        provideContent {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .background(ColorProvider(Color(0xFF1E1E2E)))
                    .cornerRadius(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ---------- 左：进度块（点这里打开 App） ----------
                Column(
                    modifier = GlanceModifier
                        .width(84.dp)
                        .clickable(openAppAction),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFABB2BF)),
                            fontSize = 13.sp
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$done",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFFFB74D)),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "/$total",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFABB2BF)),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    Spacer(GlanceModifier.height(5.dp))
                    Box(
                        modifier = GlanceModifier
                            .width(84.dp)
                            .height(6.dp)
                            .background(ColorProvider(Color(0xFF3A3A4A)))
                            .cornerRadius(3.dp)
                    ) {
                        if (ratio > 0f) {
                            Box(
                                modifier = GlanceModifier
                                    .width((84f * ratio.coerceAtMost(1f)).dp)
                                    .height(6.dp)
                                    .background(
                                        ColorProvider(if (done >= total) Color(0xFF98C379) else Color(0xFFFFB74D))
                                    )
                                    .cornerRadius(3.dp),
                                content = {}
                            )
                        }
                    }
                }

                Spacer(GlanceModifier.width(12.dp))

                // ---------- 右：习惯卡片（每张整块可点切换） ----------
                if (total == 0) {
                    Text(
                        text = "还没有习惯",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFABB2BF)),
                            fontSize = 14.sp
                        ),
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 不加 Spacer：每张卡片自带 padding(4.dp)，相邻卡片的 padding 拼出间距，
                        // 这样右侧 Row 的子节点 = 5 张卡片 + 可能的 "+N" ≤ 6，不会超 Glance 的 10 上限
                        list.take(5).forEach { (habit, isDone) ->
                            HabitChip(habit = habit, isDone = isDone)
                        }
                        if (total > 5) {
                            Text(
                                text = "+${total - 5}",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFABB2BF)),
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1×4 里的一张「习惯卡片」：复选框圆在上、emoji+名称在下，整张可点。
 * 复选框圆复用 [HabitCheckCircle]，这里只负责竖排成卡片。
 */
@Composable
private fun HabitChip(habit: Habit, isDone: Boolean) {
    val toggle = actionRunCallback<ToggleHabitAction>(
        actionParametersOf(HabitIdKey to habit.id.toString())
    )
    Column(
        modifier = GlanceModifier
            .clickable(toggle)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HabitCheckCircle(isDone = isDone, sizeDp = 24, checkSp = 14)
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = habit.emoji,
            style = TextStyle(
                fontSize = 14.sp,
                color = ColorProvider(Color(0xFFE6E6EE))
            )
        )
        Text(
            text = habit.name.take(3),
            style = TextStyle(
                fontSize = 10.sp,
                color = ColorProvider(if (isDone) Color(0xFF6B6B78) else Color(0xFFC8C8D4))
            )
        )
    }
}
