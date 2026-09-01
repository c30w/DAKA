package com.marvin.daka.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import com.marvin.daka.R
import com.marvin.daka.data.AppPrefs
import com.marvin.daka.ui.stats.StatsScreen
import com.marvin.daka.reminder.ReminderPrefs
import com.marvin.daka.ui.calendar.CalendarScreen
import com.marvin.daka.ui.create.CreateHabitScreen
import com.marvin.daka.model.ReminderConfig
import com.marvin.daka.ui.home.HabitViewModel
import com.marvin.daka.ui.home.HabitViewModelFactory
import com.marvin.daka.ui.home.HomeScreen
import com.marvin.daka.ui.home.NewHabitDraft
import com.marvin.daka.ui.onboarding.OnboardingOverlay
import com.marvin.daka.ui.settings.SettingsScreen
import com.marvin.daka.ui.template.TemplatePickerScreen
import kotlinx.coroutines.launch

private const val ROUTE_HOME = "home"
private const val ROUTE_CREATE = "create"
private const val ROUTE_EDIT = "edit/{habitId}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CALENDAR = "calendar"
/** V1.3：习惯模板选择页。从新建页进入，勾完直接返回首页 */
private const val ROUTE_TEMPLATE = "template"
/** V1.3：统计页（周/月/年图表）。底部导航第二个 tab */
private const val ROUTE_STATS = "stats"

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

    // 新手引导：首次启动展示一次；「不再显示」勾选后再启动就不弹了
    val appPrefs = remember(context) { AppPrefs(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val onboardingDone by appPrefs.onboardingDone.collectAsStateWithLifecycle(initialValue = false)
    var showOnboarding by remember { mutableStateOf(false) }
    LaunchedEffect(onboardingDone) {
        // 引导跟 onboardingDone 实时联动：完成 = 收起；未完成 = 展示
        showOnboarding = !onboardingDone
    }

    // V1.3：底部导航栏只在「打卡 / 统计」两个主页显示。
    // 新建、编辑、设置、日历都是从这两个页钻进去的子页，
    // 底栏跟着出现会让用户在子页里误触跳转，还占掉本来就紧张的屏幕高度。
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(ROUTE_HOME, ROUTE_STATS)

    Box(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME
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
                onBack = { navController.popBackStack() },
                // V1.3：模板库入口。新建页顶部那个「从模板导入」按钮
                onOpenTemplates = { navController.navigate(ROUTE_TEMPLATE) }
            )
        }

        // V1.3 模板库：勾选 → 批量导入 → 回首页。
        // 导入走 popBackStack 两次（模板页 → 新建页 → 首页），
        // 因为用户是从首页 → 新建页 → 模板页进来的，一次性退回起点最干净：
        // 停在新建页没有意义（习惯已经加好了，没什么可再填的）。
        composable(ROUTE_TEMPLATE) {
            TemplatePickerScreen(
                onImport = { templates ->
                    val drafts = templates.map { template ->
                        NewHabitDraft(
                            name = context.getString(template.nameRes),
                            emoji = template.emoji,
                            colorArgb = template.colorArgb,
                            category = template.category,
                            // 提醒一律关：模板只提供建议时间，不替用户开通知
                            reminder = ReminderConfig.disabled(
                                hour = template.suggestHour,
                                minute = template.suggestMinute
                            )
                        )
                    }
                    vm.createHabits(drafts)
                    navController.popBackStack(ROUTE_HOME, inclusive = false)
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

        composable(ROUTE_STATS) {
            StatsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_CALENDAR) {
            CalendarScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
        }

        // V1.3 底部导航：切主页用 popUpTo + launchSingleTop，
        // 避免反复点击在返回栈里堆出一串重复的页面（点五次统计就得按五次返回）。
        if (showBottomBar) {
            DakaBottomBar(
                currentRoute = currentRoute,
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo(ROUTE_HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 新手引导盖在最上层
        if (showOnboarding) {
            OnboardingOverlay(
                onDismiss = { markDone ->
                    showOnboarding = false
                    // 勾了「不再显示」才写偏好；没勾只关这一次
                    if (markDone) {
                        scope.launch { appPrefs.setOnboardingDone(true) }
                    }
                }
            )
        }
    }
}

/**
 * 底部导航栏：打卡 / 统计 两个主页。
 *
 * 为什么用 NavigationBar 而不是顶部 Tab？
 * 底部是拇指最舒服的落点（单手握持时拇指自然扫过的区域），而且这两个页
 * **平级且要频繁来回切**。顶部 tab 更适合「同一列表的不同筛选」这种
 * 内容相似的场景，底部栏适合功能完全不同的两个模块。
 *
 * 图标选 CheckCircle（打卡）和 BarChart（统计）——都是一眼能认出的形状，
 * 不用字就能区分，切页时不会认错。
 */
@Composable
private fun DakaBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = currentRoute == ROUTE_HOME,
            onClick = { onSelect(ROUTE_HOME) },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.nav_home)) }
        )
        NavigationBarItem(
            selected = currentRoute == ROUTE_STATS,
            onClick = { onSelect(ROUTE_STATS) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.nav_stats)) }
        )
    }
}
