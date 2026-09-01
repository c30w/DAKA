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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.marvin.daka.MainActivity
import kotlinx.coroutines.runBlocking

/**
 * 桌面小组件：4×3，Google Keep 清单风格的「大号版」。
 *
 * 和 2×2 的 [HabitWidget] 是同一套交互（整行可点切换、顶部标题点开 App），
 * 只是尺寸更大：能放下更多行（最多 6 行 + 余量提示），复选框和文字也更大，
 * 桌面一眼扫完今天的清单，点一下就切换，不需要进 App。
 *
 * 实时同步同 [HabitWidget]：组件内点 → [ToggleHabitAction] 改库 + [refreshHabitWidgets]
 * 刷新全部三种组件；App 里改 → [com.marvin.daka.ui.home.HabitViewModel] 写库后刷新。
 * 两边永远一致。
 *
 * 行布局复用 [HabitCheckRow]（复选框圆来自 [HabitCheckCircle]），sizeDp=28 比 2×2 略大。
 */
class HabitWidgetBig : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val list = runBlocking { loadTodayHabits(context) }
        val done = list.count { it.second }
        val total = list.size

        val openAppAction = actionStartActivity(MainActivity::class.java, actionParametersOf())

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .background(ColorProvider(Color(0xFF1E1E2E)))
                    .cornerRadius(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题行：点这里打开 App（想进 App 增删/排序时用）
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(openAppAction)
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日打卡",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFABB2BF)),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = "$done/$total",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFFB74D)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                if (total == 0) {
                    // 还没有习惯：整块提示点开 App 去加
                    Text(
                        text = "还没有习惯，点这里添加",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF98C379)),
                            fontSize = 14.sp
                        ),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(openAppAction)
                    )
                } else {
                    // 每行自带上下 padding 当间距，不另加 Spacer——避免 list Column 子节点超 10 个
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        list.take(6).forEach { (habit, isDone) ->
                            HabitCheckRow(
                                habit = habit,
                                isDone = isDone,
                                sizeDp = 26,
                                nameMax = 12,
                                vPadding = 5
                            )
                        }
                        if (total > 6) {
                            Text(
                                text = "还有 ${total - 6} 项，点顶部打开 App",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF6B6B78)),
                                    fontSize = 12.sp
                                ),
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .clickable(openAppAction)
                            )
                        }
                    }
                }
            }
        }
    }
}
