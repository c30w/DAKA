package com.marvin.daka.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.marvin.daka.reminder.ReminderPrefs
import com.marvin.daka.ui.calendar.CalendarScreen
import com.marvin.daka.ui.create.CreateHabitScreen
import com.marvin.daka.ui.home.HabitViewModel
import com.marvin.daka.ui.home.HabitViewModelFactory
import com.marvin.daka.ui.home.HomeScreen
import com.marvin.daka.ui.settings.SettingsScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_CREATE = "create"
private const val ROUTE_EDIT = "edit/{habitId}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CALENDAR = "calendar"

/**
 * App 的导航图（M5 建立 → V3 日历页 → V4 编辑页）。
 *
 * Navigation Compose 的心智模型就三样：
 *   1. rememberNavController() —— 记住「现在在哪个页面」的控制器
 *   2. NavHost —— 页面容器，里面登记「路由字符串 → 哪个 Composable」
 *   3. navController.navigate("路由") —— 跳转；popBackStack() —— 返回
 *
 * 页面之间只传**路由字符串**，不传对象。这是硬性设计：
 * 因为 App 被系统杀掉后重建时，Android 只能恢复字符串，恢复不了对象。
 * 真要传数据，用「id + 去数据库里查」，别直接传对象。
 * 编辑页就是照这个规矩做的：路由只带 habitId，习惯本体在页面里从 ViewModel 捞。
 *
 * @param factory ViewModel 工厂，由 MainActivity 用数据库 DAO 造好后传进来
 */
@Composable
fun DakaNavGraph(
    factory: HabitViewModelFactory,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    // ⚠️ 关键：viewModel() 写在 NavHost **外面**。
    // 如果写在 composable { } 里面，ViewModel 的作用域就是那个页面，
    // 首页和新建页会拿到**两个不同的实例**（各自维护一份数据，白白浪费）。
    // 写在外面，作用域是整个 Activity，所有页面共用同一个实例。
    val vm: HabitViewModel = viewModel(factory = factory)

    // 新建习惯时预填的默认提醒时间。放在导航层读，
    // 是因为只有导航层同时知道「要去新建页」和「用户上次设的默认时间」。
    val context = LocalContext.current
    val prefs = remember(context) { ReminderPrefs(context) }
    val defaultHour by prefs.defaultHour.collectAsStateWithLifecycle(initialValue = 21)
    val defaultMinute by prefs.defaultMinute.collectAsStateWithLifecycle(initialValue = 0)

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME,
        modifier = modifier
    ) {
        composable(ROUTE_HOME) {
            HomeScreen(
                viewModel = vm,
                onAddHabit = { navController.navigate(ROUTE_CREATE) },
                onEditHabit = { habitId -> navController.navigate("edit/$habitId") },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenCalendar = { navController.navigate(ROUTE_CALENDAR) }
            )
        }

        composable(ROUTE_CREATE) {
            CreateHabitScreen(
                defaultReminderTime = defaultHour to defaultMinute,
                onSave = { name, emoji, colorArgb, reminder, category ->
                    // 写库交给 ViewModel，写完直接返回首页。
                    // 不需要「通知首页刷新」——首页订阅的是数据库 Flow，数据一变自己就更新了。
                    vm.createHabit(name, emoji, colorArgb, reminder, category)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        // V4 编辑页：路由只带 id，习惯本体在页面里从 ViewModel 的数据流里捞。
        // 用 ? 读取路由参数；habitId 解析失败（理论上不会发生）就静默返回首页
        composable(
            route = ROUTE_EDIT,
            arguments = listOf(navArgument("habitId") { type = NavType.LongType })
        ) { entry ->
            val habitId = entry.arguments?.getLong("habitId") ?: 0L
            val habits by vm.habits.collectAsStateWithLifecycle()
            val editing = habits.firstOrNull { it.id == habitId }

            if (editing != null) {
                CreateHabitScreen(
                    editing = editing,
                    onSave = { _, _, _, _, _ -> },
                    onUpdate = { id, name, emoji, colorArgb, reminder, category ->
                        vm.updateHabit(id, name, emoji, colorArgb, category, reminder)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            // editing == null： habits 还没加载完（冷启动直达时可能发生）。
            // 空一帧没问题，Flow 一来就重组合。 habits 加载完了还没有 → id 非法，
            // 用户按返回键出去即可，不值得为这个边界弹错误提示
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenCalendar = { navController.navigate(ROUTE_CALENDAR) }
            )
        }

        composable(ROUTE_CALENDAR) {
            CalendarScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
