package com.marvin.daka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.marvin.daka.audio.SoundEffectPlayer
import com.marvin.daka.data.local.DatabaseProvider
import com.marvin.daka.data.local.HabitDatabase
import com.marvin.daka.model.Habit
import com.marvin.daka.model.HabitRecord
import com.marvin.daka.ui.home.HabitViewModelFactory
import com.marvin.daka.ui.navigation.DakaNavGraph
import com.marvin.daka.ui.theme.DAKATheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 整个 App 只有一个 Activity —— Compose 单 Activity 架构的常规做法。
 * 页面切换靠 Navigation Compose（M5），不再是一个页面一个 Activity。
 *
 * 这个类的职责被压缩到只剩三件事：
 *   1. 建数据库（单例）
 *   2. 造 ViewModel 工厂，把 DAO 交给它
 *   3. setContent 挂上导航图
 * 业务逻辑全在 ViewModel 里，界面全在 Composable 里，这里只做「接线」。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 让内容延伸到状态栏和导航栏后面，配合 TopAppBar 做沉浸式
        enableEdgeToEdge()

        // 预加载全部 UI 音效（SoundPool 异步加载，不阻塞启动）
        SoundEffectPlayer.init(this)

        // 单例：整个进程只有一个数据库实例
        val db = DatabaseProvider.get(this)

        // ViewModel 工厂：把 DAO 和 applicationContext 塞进去，供 ViewModel 使用。
        // V3 起 ViewModel 需要 Context——它要排闹钟（AlarmManager 只能通过 Context 拿）。
        // 传 applicationContext 而不是 this：Activity 会被销毁重建，
        // 存它的引用就是经典的 Activity 泄漏。
        val factory = HabitViewModelFactory(
            habitDao = db.habitDao(),
            recordDao = db.habitRecordDao(),
            appContext = applicationContext
        )

        // 第一次启动灌入示例数据。
        // M5 做完新建页之后这段其实可以删了（用户能自己加），
        // 留着的好处是：刚装上看得到 streak 和 7 天格子的效果，方便验收。
        // 想从空白开始，把下面这个 launch 块整段删掉即可。
        lifecycleScope.launch(Dispatchers.IO) {
            seedIfEmpty(db)
        }

        setContent {
            DAKATheme {
                DakaNavGraph(factory = factory)
            }
        }
    }
}

/**
 * 示例习惯的定义。
 * streak / doneToday 是「希望它看起来是什么状态」，seedIfEmpty 会据此反推出打卡记录。
 */
private data class SeedHabit(
    val name: String,
    val emoji: String,
    val colorArgb: Long,
    val streak: Int,
    val doneToday: Boolean
)

private val SEED_HABITS = listOf(
    SeedHabit("喝水 2L", "💧", 0xFF2196F3L, streak = 12, doneToday = true),
    SeedHabit("跑步 3 公里", "🏃", 0xFFE91E63L, streak = 3, doneToday = true),
    SeedHabit("读书 30 分钟", "📖", 0xFF4CAF50L, streak = 28, doneToday = true),
    SeedHabit("冥想 10 分钟", "🧘", 0xFF9C27B0L, streak = 0, doneToday = false),
    SeedHabit("23 点前睡觉", "💤", 0xFFFF9800L, streak = 7, doneToday = false)
)

/**
 * 数据库里还没有习惯时，灌入示例数据。
 *
 * 为什么要「反推」出打卡记录，而不是直接存 streak 数字？
 * 因为 habits 表里压根没有 streak 字段——它是从记录里算出来的。
 * 想让界面显示「连续 12 天」，就必须造出连续 12 天的记录。
 * 这正好把 M3 的数据流走了一遍：记录 → 算法 → 界面。
 */
private suspend fun seedIfEmpty(db: HabitDatabase) {
    // 已经有数据就什么都不做（否则每次启动都会重复插入）
    if (db.habitDao().count() > 0) return

    // 插入习惯后拿到数据库分配的 id，下面造打卡记录要用它当 habitId
    val ids = db.habitDao().insertAll(
        SEED_HABITS.map {
            Habit(name = it.name, emoji = it.emoji, colorArgb = it.colorArgb)
        }
    )

    val today = LocalDate.now()
    val records = mutableListOf<HabitRecord>()

    SEED_HABITS.forEachIndexed { index, seed ->
        val habitId = ids.getOrNull(index) ?: return@forEachIndexed

        // 今天已打卡：从今天往前数 streak 天
        // 今天未打卡：从昨天往前数 streak 天（今天没打卡不算断签，起点要退一天）
        val startOffset = if (seed.doneToday) 0L else 1L

        repeat(seed.streak) { i ->
            records += HabitRecord(
                habitId = habitId,
                date = today.minusDays(startOffset + i).toString()
            )
        }
    }

    db.habitRecordDao().insertAll(records)
}
