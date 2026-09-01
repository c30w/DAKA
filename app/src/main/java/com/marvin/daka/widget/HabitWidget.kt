package com.marvin.daka.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import com.marvin.daka.data.local.DatabaseProvider
import com.marvin.daka.model.Habit
import com.marvin.daka.model.HabitRecord
import com.marvin.daka.util.todayString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 桌面小组件：2×2，Google Keep 清单风格。
 *
 * 设计（参考 Keep 的 checklist 小组件）：
 * - 顶部一行「今日打卡 X/N」是标题，点它打开 App 去管理。
 * - 下面每一项习惯是一整行：**左侧复选框圆 + 右侧 emoji+名称**，整行可点。
 *   没打卡 = 灰环空心圆 + 亮色文字；打卡了 = 绿色实心圆 + 白色对勾 + 文字变灰。
 *   一眼能看出哪些做了、哪些没做，点一下就切换——不用进 App，符合直觉。
 *
 * 实时同步（双向）：
 * - 在组件里点某项 → [ToggleHabitAction] 改库 + [refreshHabitWidgets] 刷新全部三种组件。
 * - 在 App 里改数据 → [HabitViewModel] 在每次写入后调 [refreshHabitWidgets]，组件跟着变。
 * 所以桌面点一下、App 里点一下，两边永远一致。
 *
 * Glance 1.2 笔记：尺寸参数是裸 float(dp)，只有 fontSize 用 sp。
 */
class HabitWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val list = runBlocking { loadTodayHabits(context) }
        val done = list.count { it.second }
        val total = list.size

        val openAppAction = actionStartActivity(MainActivity::class.java, actionParametersOf())

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .background(ColorProvider(Color(0xFF1E1E2E)))
                    .cornerRadius(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题行：点这里打开 App（想进 App 增删/排序时用）
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(openAppAction)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日打卡",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFABB2BF)),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "$done/$total",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFFB74D)),
                            fontSize = 16.sp,
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
                            fontSize = 13.sp
                        ),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(openAppAction)
                    )
                } else {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        list.take(3).forEach { (habit, isDone) ->
                            HabitCheckRow(habit = habit, isDone = isDone)
                            Spacer(GlanceModifier.height(4.dp))
                        }
                        if (total > 3) {
                            Text(
                                text = "还有 ${total - 3} 项，点顶部打开 App",
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

/**
 * 共享：一个习惯的「清单行」——左侧复选框圆 + 右侧 emoji+名称，整行可点切换。
 * 2×2 和 1×4 都用它（1×4 把它竖着装进卡片）。
 *
 * 复选框用两层 Box 叠出「环」：未打卡外层灰环 + 内层深色，看着就是空心圆；
 * 已打卡单层绿圆 + 白色对勾。这样不依赖 border API，各版本都稳。
 */
@Composable
private fun HabitCheckRow(habit: Habit, isDone: Boolean) {
    val toggle = actionRunCallback<ToggleHabitAction>(
        actionParametersOf(HabitIdKey to habit.id.toString())
    )
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(toggle)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDone) {
            Box(
                modifier = GlanceModifier
                    .size(26.dp)
                    .background(ColorProvider(Color(0xFF98C379)))
                    .cornerRadius(13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFFFFFFF)),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        } else {
            Box(
                modifier = GlanceModifier
                    .size(26.dp)
                    .background(ColorProvider(Color(0xFF4A4A5A)))
                    .cornerRadius(13.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(22.dp)
                        .background(ColorProvider(Color(0xFF1E1E2E)))
                        .cornerRadius(11.dp)
                ) {}
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = "${habit.emoji} ${habit.name.take(10)}",
            style = TextStyle(
                fontSize = 14.sp,
                color = ColorProvider(if (isDone) Color(0xFF6B6B78) else Color(0xFFE6E6EE))
            )
        )
    }
}

/**
 * 一次读库，返回「今天的全部活跃习惯」+「每个习惯今天是否已打卡」。
 *
 * 三种小组件共用：2×2 只取 done/total + 前 3 行，1×1 取总进度，1×4 要逐行列习惯，
 * 所以统一在这拿「带完成状态的列表」，各取所需。
 *
 * 排序：置顶在前、按 sortOrder，和首页一致，组件里列出来才对得上号。
 */
internal suspend fun loadTodayHabits(context: Context): List<Pair<Habit, Boolean>> {
    val db = DatabaseProvider.get(context)
    val habits = db.habitDao().getAllActive()
        .sortedWith(compareBy<Habit> { !it.pinned }.thenBy { it.sortOrder }.thenBy { it.createdAt })
    val today = todayString()
    val records = db.habitRecordDao().observeAll().first()
    val todaySet = records.filter { it.date == today }.map { it.habitId }.toSet()
    return habits.map { it to (it.id in todaySet) }
}

/** 数据变更后调用，让全部三种小组件（2×2 / 1×1 / 1×4）一起刷新。 */
suspend fun refreshHabitWidgets(context: Context) {
    HabitWidget().updateAll(context)
    HabitWidgetSmall().updateAll(context)
    HabitWidgetWide().updateAll(context)
}

/** 兼容旧调用名：只刷新 2×2。现在全部刷新走 [refreshHabitWidgets]。 */
suspend fun refreshHabitWidget(context: Context) = refreshHabitWidgets(context)

// ------------------------------------------------------------------
// 小组件内直接打卡（无需打开 App）
// ------------------------------------------------------------------

/**
 * 在小组件里点某个习惯时，用它把 habitId 带进 [ToggleHabitAction]。
 *
 * 用 String 传而不是 Long：Glance 的 ActionParameters 对 Long 的序列化在某些版本不稳，
 * String 最稳，回调里再 parse 回 Long。
 */
val HabitIdKey = ActionParameters.Key<String>("habitId")

/**
 * 小组件内点按的回调：切换「某习惯今天」的打卡状态，然后刷新全部三种组件。
 *
 * 它跑在 Glance 自己的广播协程里（App 不需要在前台），直接改 Room 库、再 updateAll，
 * 组件原地重绘——全程不打开界面，满足「组件内功能独立操作、即时同步」的需求。
 * 逻辑和 ViewModel.toggle 保持一致（今天有记录就删、没有就插，靠唯一索引防重复打卡）。
 */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val idStr = parameters[HabitIdKey] ?: return
        val habitId = idStr.toLongOrNull() ?: return
        toggleHabitToday(context, habitId)
        // 改完库刷新全部组件，让 2×2 / 1×1 / 1×4 一起同步
        refreshHabitWidgets(context)
    }
}

/**
 * 在数据库里切换某习惯今天的打卡状态。
 * 抽成 internal suspend，给三种组件共用；切到 IO 线程写 Room。
 */
internal suspend fun toggleHabitToday(context: Context, habitId: Long) {
    withContext(Dispatchers.IO) {
        val db = DatabaseProvider.get(context)
        val today = todayString()
        val records = db.habitRecordDao().observeAll().first()
        val done = records.any { it.habitId == habitId && it.date == today }
        if (done) {
            db.habitRecordDao().deleteByDate(habitId, today)
        } else {
            db.habitRecordDao().insert(HabitRecord(habitId = habitId, date = today))
        }
    }
}
