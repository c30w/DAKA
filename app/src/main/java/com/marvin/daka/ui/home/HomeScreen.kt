package com.marvin.daka.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marvin.daka.audio.SoundEffectPlayer
import com.marvin.daka.R
import com.marvin.daka.model.HabitUi
import com.marvin.daka.ui.theme.DAKATheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ------------------------------------------------------------------
// 拖拽 key 约定
//
// LazyColumn 里有三类条目，key 必须稳定且互不相同，拖拽状态机靠 key
// 在 layoutInfo 里定位条目、在本地列表里换位：
//   今日汇总 = "summary"        （不可拖、不可换位）
//   分类头   = "c:生活"          （c = category）
//   习惯卡   = "h:3"            （h = habit，末尾是习惯 id）
//   底部留白 = "footer"         （不可拖、不可换位）
// 习惯卡 key 只含 id 不含分类名——跨分类拖动后 key 依然对得上号（V4.2 教训）。
// ------------------------------------------------------------------
private const val SUMMARY_KEY = "summary"
private const val FOOTER_KEY = "footer"
private const val CAT_PREFIX = "c:"
private const val HABIT_PREFIX = "h:"

private fun catKey(category: String) = CAT_PREFIX + category
private fun habitKey(id: Long) = "$HABIT_PREFIX$id"
private fun habitIdOf(key: String) = key.removePrefix(HABIT_PREFIX).toLong()

/** 是不是「可换位」的条目（分类头或习惯卡），用来把汇总区和留白排除在换位目标之外 */
private fun isRowKey(key: String) = key.startsWith(CAT_PREFIX) || key.startsWith(HABIT_PREFIX)

/**
 * 首页。
 *
 * 里程碑演进：
 *   M1 静态假数据 → M2 点击打勾 → M3 接 Room → M4 改用 ViewModel → M5 加 FAB → M6 加 7 天格子
 *   → V3 加日历入口 → V4 加分类、置顶、左右滑快捷操作、长按操作面板
 *   → V4.1 分类融入列表（分组标题） → V4.2 长按拖动大改版
 *   → V4.3 拖拽重写为 LazyColumn + 本地列表换位（跟手、弹簧动画、连续多格）
 *
 * 交互约定：
 *   - 短按卡片 = 打卡（不变，最高频的操作必须零学习成本）
 *   - 长按卡片 = 进入拖动，卡片实时跟手；**原地松手**（几乎没挪动）= 弹出操作面板
 *   - 拖动中屏幕上方浮出「置顶区」，拖进去松手 = 直接置顶
 *   - 拖动可跨分类：卡片越过分类头就换分组（往下越过 = 进队首，往上越过 = 进队尾）
 *   - 分类头也能长按拖动，拖动换整个分组的位置
 *   - 右滑卡片 = 置顶；左滑 = 编辑（删除收进操作面板，误触代价小）
 *
 * @param viewModel 首页数据来自它
 * @param onAddHabit 点右下角「+」按钮的回调（跳转新建页由导航层负责，这里不关心）
 * @param onEditHabit 左滑或操作面板选「编辑」后跳编辑页，参数是习惯 id
 * @param onOpenSettings 点顶栏齿轮
 * @param onOpenCalendar 点顶栏日历图标
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HabitViewModel,
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    // collectAsStateWithLifecycle 是官方现在推荐的做法（要 lifecycle-runtime-compose）：
    // 它比 collectAsState 多一件事——App 退到后台时自动停止收集，省电。
    val items by viewModel.items.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()

    // 今日全部完成时播一段悦耳庆祝音（只在「未全完成 → 全完成」跃迁时播一次，不重复）
    val wasAllDone = remember { mutableStateOf(items.isNotEmpty() && items.all { it.doneToday }) }
    val nowAllDone = items.isNotEmpty() && items.all { it.doneToday }
    androidx.compose.runtime.LaunchedEffect(nowAllDone) {
        if (nowAllDone && !wasAllDone.value && SoundEffectPlayer.enabled) {
            SoundEffectPlayer.play(SoundEffectPlayer.Effect.DakaAllDone)
        }
        wasAllDone.value = nowAllDone
    }

    // 操作面板当前对应的习惯；null = 不弹
    var sheetHabit by remember { mutableStateOf<HabitUi?>(null) }
    // 待确认删除的习惯（面板里点的）；null = 不弹窗
    var pendingDelete by remember { mutableStateOf<HabitUi?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = todayTitle(),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                // V4.7：把「设置」从右侧挪到左侧（navigationIcon）。
                // 右手单手操作时拇指自然落在右上角，那里留给最常用的「提醒日历」，
                // 「设置」这种偶尔才进的放左手侧，互不挤占。
                navigationIcon = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.cd_settings)
                        )
                    }
                },
                actions = {
                    // V3：提醒日历。日常会点进去看，放右上角拇指最舒服的位置。
                    IconButton(onClick = onOpenCalendar) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = stringResource(R.string.cd_calendar)
                        )
                    }
                }
            )
        },
        // M5：右下角悬浮按钮，跳到新建习惯页
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHabit) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "新建习惯"
                )
            }
        }
    ) { innerPadding ->
        HomeContent(
            sections = sections,
            doneCount = items.count { it.doneToday },
            totalCount = items.size,
            onToggle = viewModel::toggle,
            onRequestDelete = { pendingDelete = it },
            onTogglePin = viewModel::togglePin,
            onPinToTop = viewModel::pinToTop,
            onApplyOrder = viewModel::applyOrder,
            onEditHabit = onEditHabit,
            onMoveUp = { viewModel.moveHabit(it, -1) },
            onMoveDown = { viewModel.moveHabit(it, +1) },
            onOpenSheet = { sheetHabit = it },
            modifier = Modifier.padding(innerPadding)
        )
    }

    // 操作面板：长按后原地松手弹出（拖动手势没被识别成「拖」时）
    sheetHabit?.let { habit ->
        HabitActionSheet(
            habit = habit,
            onDismiss = { sheetHabit = null },
            onEdit = {
                sheetHabit = null
                SoundEffectPlayer.play(SoundEffectPlayer.Effect.DakaEdit)
                onEditHabit(habit.id)
            },
            onTogglePin = {
                SoundEffectPlayer.play(SoundEffectPlayer.Effect.DakaPin)
                viewModel.togglePin(habit.id)
                sheetHabit = null
            },
            onMoveUp = {
                viewModel.moveHabit(habit.id, -1)
                sheetHabit = null
            },
            onMoveDown = {
                viewModel.moveHabit(habit.id, +1)
                sheetHabit = null
            },
            onDelete = {
                sheetHabit = null
                pendingDelete = habit
            }
        )
    }

    // 删除确认弹窗。用 let 而不是 if，省掉一次 habit 的空判断
    pendingDelete?.let { habit ->
        DeleteHabitDialog(
            habitName = habit.name,
            onConfirm = {
                SoundEffectPlayer.play(SoundEffectPlayer.Effect.DakaDelete)
                viewModel.deleteHabit(habit.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

/**
 * 长按卡片弹出的操作面板。
 *
 * 为什么用 ModalBottomSheet 而不是 AlertDialog？
 * 操作面板是「五个平级动作」，弹窗天然适合「确认/取消」两个动作的问答结构，
 * 底部抽屉才是移动端动作列表的惯例（参考系统相册、微信的「…」菜单）。
 * 打卡不放进面板——短按卡片就是打卡，放进面板反而稀释了操作列表的用途。
 *
 * 面板退居二线：长按优先给拖动手势，原地松手才轮到面板。
 * 「删除」目前只从这里进（左滑已改成编辑）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitActionSheet(
    habit: HabitUi,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        // 头部：习惯自己的信息，让用户确认「我在操作哪个」
        ListItem(
            headlineContent = {
                Text(habit.name, style = MaterialTheme.typography.titleMedium)
            },
            supportingContent = {
                Text(
                    text = buildString {
                        append(habit.category)
                        if (habit.streak > 0) append(" · 连续 ${habit.streak} 天")
                    }
                )
            },
            leadingContent = {
                Text(text = habit.emoji, style = MaterialTheme.typography.headlineSmall)
            }
        )

        SheetAction(
            icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            label = stringResource(R.string.sheet_edit),
            onClick = onEdit
        )
        SheetAction(
            icon = {
                // 已置顶显示描边图钉（点它 = 取消），未置顶显示实心（点它 = 置顶）
                Icon(
                    if (habit.pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                    contentDescription = null
                )
            },
            label = if (habit.pinned) stringResource(R.string.sheet_unpin) else stringResource(R.string.sheet_pin),
            onClick = onTogglePin
        )
        SheetAction(
            icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
            label = stringResource(R.string.sheet_move_up),
            onClick = onMoveUp
        )
        SheetAction(
            icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
            label = stringResource(R.string.sheet_move_down),
            onClick = onMoveDown
        )
        SheetAction(
            icon = {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            label = stringResource(R.string.sheet_delete),
            labelColor = MaterialTheme.colorScheme.error,
            onClick = onDelete
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** 操作面板里的一行动作。Surface(onClick) 负责整行可点 + 水波纹 */
@Composable
private fun SheetAction(
    icon: @Composable () -> Unit,
    label: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(label, color = labelColor) },
            leadingContent = icon
        )
    }
}

/**
 * 删除习惯前的二次确认。
 *
 * 破坏性操作必须有确认——手滑一下就把攒了几十天的习惯弄没了，体验极差。
 * 文案里明确说「历史记录仍保留」，是因为用户最担心的其实是「我之前的打卡是不是也没了」。
 */
@Composable
private fun DeleteHabitDialog(
    habitName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title, habitName)) },
        text = { Text(stringResource(R.string.delete_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/**
 * 首页内容区：今日汇总 + 按分类分组的习惯列表（LazyColumn）。
 *
 * 为什么 V4.3 从 Column+verticalScroll 换回了 LazyColumn？
 * 顺滑拖拽的两大刚需都是 LazyColumn 的主场：
 *   1. `Modifier.animateItem()` 让「被换位的其他卡片」自动带弹簧动画，
 *      Column 想做到同样的效果得手写每个条目的偏移动画，bug 密度极高；
 *   2. LazyListItemInfo 自带每个条目的实时位置和尺寸，换位判定直接读，
 *      不用自己挂 onGloballyPositioned 攒位置表（V4.2 卡顿的元凶之一）。
 * 旧注释里担心的「条目回收导致位置过期」用稳定 key 解决：key 不变，
 * 回收的只是绘制，位置永远从 layoutInfo 现查，不会拿到过期数据。
 * 习惯列表撑死几十条，LazyColumn 的性能优势用不上，但这两样刚需是真香。
 *
 * @param onApplyOrder 拖拽松手后一次性落库（分类顺序 + 每个习惯的分类和排序）
 * @param onPinToTop 拖进顶部置顶区松手（只置顶不取消，见 ViewModel.pinToTop）
 * @param onMoveUp/onMoveDown 操作面板和无障碍的「上移/下移一位」
 */
@Composable
private fun HomeContent(
    sections: List<HomeSection>,
    doneCount: Int,
    totalCount: Int,
    onToggle: (habitId: Long, currentlyDone: Boolean) -> Unit,
    onRequestDelete: (HabitUi) -> Unit,
    onTogglePin: (Long) -> Unit,
    onPinToTop: (Long) -> Unit,
    onApplyOrder: (List<HomeSection>) -> Unit,
    onEditHabit: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onOpenSheet: (HabitUi) -> Unit,
    modifier: Modifier = Modifier
) {
    // 空状态：还没建习惯时给句人话，别让用户对着白屏发呆
    if (sections.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.home_empty_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 「原地松手 = 面板」的判定阈值：累计位移小于它就算「没拖」
    val tapThresholdPx = with(LocalDensity.current) { 12.dp.toPx() }

    // ---------------------------------------------------------------
    // 拖拽的数据模型：拖动期间改内存里的扁平列表 flat，松手才落库。
    //
    // flat = [分类头, 习惯, 习惯, 分类头, 习惯 ...]，和屏幕视觉顺序一一对应。
    // DB/Flow 只在松手时收到最终结果，拖动全程零数据库参与 → 零卡顿。
    // sections 每次变化（落库后 Flow 推送、打卡状态刷新）都会重建 flat；
    // 拖动进行中不会重建——拖动期间没有 DB 写入，sections 本来也不会变。
    // ---------------------------------------------------------------
    var flat by remember { mutableStateOf(sections.toHomeRows()) }
    LaunchedEffect(sections) { flat = sections.toHomeRows() }

    // id → 习惯的速查表：拖拽回调里只拿得到 key，弹操作面板要习惯对象
    val habitsById = remember(sections) {
        sections.flatMap { it.habits }.associateBy { it.id }
    }
    // 同 flat 的套路：状态机捕获的是这个「永远最新」的引用
    var currentHabitsById by remember { mutableStateOf(habitsById) }
    currentHabitsById = habitsById

    // 分组的完成度统计（拖动中数字不刷新没关系，打卡和拖拽不会同时发生）
    val statsByCategory = remember(sections) {
        sections.associate { section ->
            section.category to (section.habits.count { it.doneToday } to section.habits.size)
        }
    }

    val drag = remember(tapThresholdPx) {
        DragDropState(
            listState = listState,
            scope = scope,
            tapThresholdPx = tapThresholdPx,
            onMove = { fromKey, toKey, downward ->
                val next = moveRow(flat, fromKey, toKey, downward)
                val changed = next != flat
                flat = next
                changed
            },
            onSwapGroups = { fromCategory, toCategory ->
                val next = swapGroupBlocks(flat, fromCategory, toCategory)
                val changed = next != flat
                flat = next
                changed
            },
            onLongPressHabit = { id -> currentHabitsById[id]?.let(onOpenSheet) },
            onDropPin = onPinToTop,
            onCommitOrder = { onApplyOrder(flat.toSections()) }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // 登记列表自身的根坐标，给置顶区悬停判定换算用（见 DragDropState）
                .onGloballyPositioned { drag.reportViewportTop(it.boundsInRoot().top.roundToInt()) }
                .padding(horizontal = 16.dp)
        ) {
            item(key = SUMMARY_KEY) {
                TodaySummary(done = doneCount, total = totalCount)
            }

            items(flat, key = { it.key }) { row ->
                val isDragged = drag.draggingKey == row.key
                Box(
                    modifier = Modifier
                        // animateItem：条目换位时自动弹簧动画。
                        // 被拖的那条关掉——它由 graphicsLayer 跟手平移，两个动画叠加会打架
                        .animateItem(
                            placementSpec = if (isDragged) {
                                null
                            } else {
                                spring(stiffness = Spring.StiffnessMediumLow)
                            }
                        )
                        .zIndex(if (isDragged) 1f else 0f)
                ) {
                    when (row) {
                        is HomeRow.Header -> {
                            val (done, total) = statsByCategory[row.category] ?: (0 to 0)
                            SectionHeader(
                                category = row.category,
                                done = done,
                                total = total,
                                drag = drag
                            )
                        }

                        is HomeRow.HabitRow -> {
                            val habit = row.habit
                            val a11yActions = listOf(
                                (if (habit.pinned) stringResource(R.string.a11y_pin_on) else stringResource(R.string.a11y_pin_off)) to
                                    { onTogglePin(habit.id) },
                                stringResource(R.string.a11y_edit) to { onEditHabit(habit.id) },
                                stringResource(R.string.a11y_move_up) to { onMoveUp(habit.id) },
                                stringResource(R.string.a11y_move_down) to { onMoveDown(habit.id) },
                                stringResource(R.string.a11y_delete) to { onRequestDelete(habit) }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 跟手平移：被拖时实时读 displayTranslation（含松手回弹动画）
                                    .graphicsLayer {
                                        translationY = if (drag.draggingKey == row.key) {
                                            drag.displayTranslation
                                        } else {
                                            0f
                                        }
                                    }
                                    .alpha(if (drag.isVisuallyDragging(row.key)) 0.55f else 1f)
                                    .then(drag.gestureModifier(row.key))
                                    .padding(bottom = 12.dp)
                            ) {
                                SwipeableHabitCard(
                                    habit = habit,
                                    a11yActions = a11yActions,
                                    onToggle = {
                                        // 打卡成功/取消播放不同音效，清脆不突兀
                                        SoundEffectPlayer.play(
                                            if (habit.doneToday) {
                                                SoundEffectPlayer.Effect.DakaCancel
                                            } else {
                                                SoundEffectPlayer.Effect.DakaOk
                                            }
                                        )
                                        onToggle(habit.id, habit.doneToday)
                                    },
                                    onSwipeEdit = {
                                        SoundEffectPlayer.play(SoundEffectPlayer.Effect.DakaEdit)
                                        onEditHabit(habit.id)
                                    },
                                    onSwipePin = {
                                        SoundEffectPlayer.play(SoundEffectPlayer.Effect.DakaPin)
                                        onTogglePin(habit.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item(key = FOOTER_KEY) {
                // V4.11 清理时误删的「左右滑提示」补回：让用户知道卡片能滑。
                // 用当前代码实际方向——左滑=编辑、右滑=置顶（删除误触风险高，留在操作面板里）。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.home_swipe_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // V4.2：拖动习惯时顶部浮出「置顶区」。浮层不参与布局，
        // 出现/消失不会把列表顶得跳一下（V4.2 塞在列表里就有这毛病）
        if (drag.isDraggingHabit) {
            PinDropZone(
                drag = drag,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        LaunchedEffect(drag.isDraggingHabit) {
            if (!drag.isDraggingHabit) drag.clearPinZone()
        }
    }
}

/** 分类分组标题：分类名 + 今日完成数。长按拖动可调整分组顺序 */
@Composable
private fun SectionHeader(
    category: String,
    done: Int,
    total: Int,
    drag: DragDropState,
    modifier: Modifier = Modifier
) {
    val key = catKey(category)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
            .graphicsLayer {
                translationY = if (drag.draggingKey == key) drag.displayTranslation else 0f
            }
            .alpha(if (drag.isVisuallyDragging(key)) 0.55f else 1f)
            .then(drag.gestureModifier(key))
            // 读屏：这行是可以拖动的；上下移动走卡片的自定义无障碍动作
            .semantics { contentDescription = "$category 分组，$done / $total 已完成" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.padding(start = 8.dp))
        Text(
            text = "$done/$total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 置顶投放区（浮层）。
 *
 * 只在拖动**习惯卡**时出现（分类头拖动不出现），悬浮在列表上方。
 * 被拖条目的顶部越过分区的下边界 = 悬停高亮「松手置顶」，
 * 真松手才生效——悬停只是预告，随时可以拖走反悔。
 */
@Composable
private fun PinDropZone(
    drag: DragDropState,
    modifier: Modifier = Modifier
) {
    val hovering = drag.pinHover
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                drag.reportPinZone(coords.boundsInRoot().bottom.roundToInt())
            },
        shape = RoundedCornerShape(12.dp),
        color = if (hovering) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (hovering) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        shadowElevation = if (hovering) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (hovering) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.padding(start = 8.dp))
            Text(
                text = if (hovering) stringResource(R.string.home_pin_hint_release) else stringResource(R.string.home_pin_hint_drag),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ------------------------------------------------------------------
// 拖拽的数据模型：扁平行
// ------------------------------------------------------------------

/** 扁平列表里的一行：要么是分类头，要么是习惯卡 */
private sealed interface HomeRow {
    val key: String

    data class Header(val category: String) : HomeRow {
        override val key: String get() = catKey(category)
    }

    data class HabitRow(val habit: HabitUi) : HomeRow {
        override val key: String get() = habitKey(habit.id)
    }
}

/** sections → 扁平行列表（[分类头, 习惯, ...]） */
private fun List<HomeSection>.toHomeRows(): List<HomeRow> = flatMap { section ->
    listOf<HomeRow>(HomeRow.Header(section.category)) +
        section.habits.map { HomeRow.HabitRow(it) }
}

/**
 * 扁平行列表 → sections（松手落库用）。
 *
 * 每个习惯归属哪个分类，由它上面最近的分类头决定——所以「把习惯拖过分类头」
 * 在扁平列表里天然就是换分组，不需要任何特判。
 */
private fun List<HomeRow>.toSections(): List<HomeSection> {
    val result = mutableListOf<HomeSection>()
    for (row in this) {
        when (row) {
            is HomeRow.Header -> result += HomeSection(row.category, emptyList())
            is HomeRow.HabitRow -> {
                val last = result.lastOrNull() ?: continue
                result[result.lastIndex] = last.copy(habits = last.habits + row.habit)
            }
        }
    }
    return result
}

/**
 * 在扁平列表里把一个习惯移到目标行附近。
 *
 * 方向语义（都是相对目标行）：
 *   向下拖 = 插到目标行**后面**（越过分类头 = 进那个分类的队首）
 *   向上拖 = 插到目标行**前面**（越过分类头 = 进上面那个分类的队尾）
 *
 * 目标行不是习惯（比如拖到了汇总区/留白上）时不动。
 */
private fun moveRow(
    rows: List<HomeRow>,
    fromKey: String,
    toKey: String,
    downward: Boolean
): List<HomeRow> {
    val fromIdx = rows.indexOfFirst { it.key == fromKey }
    if (fromIdx < 0) return rows
    val moving = rows[fromIdx] as? HomeRow.HabitRow ?: return rows

    val rest = rows.toMutableList().apply { removeAt(fromIdx) }
    var toIdx = rest.indexOfFirst { it.key == toKey }
    if (toIdx < 0) return rows
    if (downward) toIdx += 1
    toIdx = toIdx.coerceIn(0, rest.size)

    return rest.toMutableList().apply { add(toIdx, moving) }
}

/**
 * 交换两个分类分组的整块位置（分类头 + 它的全部习惯）。
 *
 * 为什么分类拖动不走 moveRow？拖「分组头」用户的预期是整个分组搬家，
 * 只挪标题一行会让它的习惯被别的分组收编，完全反直觉。
 */
private fun swapGroupBlocks(
    rows: List<HomeRow>,
    categoryA: String,
    categoryB: String
): List<HomeRow> {
    fun startOf(category: String) =
        rows.indexOfFirst { it is HomeRow.Header && it.category == category }

    fun endOfBlock(startIdx: Int): Int {
        val nextHeader = rows.drop(startIdx + 1).indexOfFirst { it is HomeRow.Header }
        return if (nextHeader == -1) rows.size else startIdx + 1 + nextHeader
    }

    val iA = startOf(categoryA)
    val iB = startOf(categoryB)
    if (iA < 0 || iB < 0 || iA == iB) return rows

    // 统一成「前面的块」和「后面的块」互换位置，中间隔着的块原地不动
    val (fStart, fEnd, sStart, sEnd) = if (iA < iB) {
        listOf(iA, endOfBlock(iA), iB, endOfBlock(iB))
    } else {
        listOf(iB, endOfBlock(iB), iA, endOfBlock(iA))
    }

    return rows.subList(0, fStart) +
        rows.subList(sStart, sEnd) +
        rows.subList(fEnd, sStart) +
        rows.subList(fStart, fEnd) +
        rows.subList(sEnd, rows.size)
}

/**
 * 拖拽排序状态机（V4.3：LazyColumn + 跟手平移版）。
 *
 * 和 V4.2 的本质区别——**拖动全程不碰数据库、不碰 Flow**：
 *   1. 长按条目进入拖动，记下它的起始布局位置
 *   2. 每次位移都把累计量换算成「显示位移」喂给 graphicsLayer，卡片实时跟手
 *   3. 被拖卡片的**中心**进入哪个邻居的范围，就调 onMove 在内存列表里换位；
 *      换位后该卡片在 LazyColumn 里的布局位置变了，显示位移公式自动校正，
 *      卡片在屏幕上纹丝不动（还贴着手指），其他卡片由 animateItem 弹簧动画让位
 *   4. 松手：显示位移弹簧动画归零（卡片滑进最终位置），把最终顺序一次性落库
 *
 * 「中心命中」算法天然支持连续拖过多格：换位后中心还在自己新槽位里，
 * 不会立刻反向触发，手指继续动就继续换，丝滑无抖动。
 */
private class DragDropState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    /** 「原地松手」的位移阈值（px）：累计位移小于它 = 长按弹面板，不算拖 */
    private val tapThresholdPx: Float,
    /** 习惯移到目标行附近，返回是否真的动了（决定松手时要不要落库） */
    private val onMove: (fromKey: String, toKey: String, downward: Boolean) -> Boolean,
    /** 交换两个分组的整块位置，返回是否真的动了 */
    private val onSwapGroups: (fromCategory: String, toCategory: String) -> Boolean,
    /** 长按后原地松手：弹操作面板 */
    private val onLongPressHabit: (habitId: Long) -> Unit,
    /** 拖进置顶区松手：置顶（只置顶不取消） */
    private val onDropPin: (habitId: Long) -> Unit,
    /** 松手且顺序变过：把最终顺序一次性落库 */
    private val onCommitOrder: () -> Unit
) {

    /** 正在被拖（含松手后的回弹动画期间）的 key；null = 空闲 */
    var draggingKey by mutableStateOf<String?>(null)
        private set

    val isDragging: Boolean get() = draggingKey != null
    val isDraggingHabit: Boolean get() = draggingKey?.startsWith(HABIT_PREFIX) == true

    /** 手指已松开、正在回弹动画中——用来提前恢复卡片透明度 */
    var settling by mutableStateOf(false)
        private set

    /** 被拖条目当前的显示位移（px）。graphicsLayer 直接读它 */
    var displayTranslation by mutableFloatStateOf(0f)
        private set

    /** 被拖习惯正悬停在置顶区上（高亮预告「松手置顶」） */
    var pinHover by mutableStateOf(false)
        private set

    /** 长按生效时条目的布局位置（LazyColumn 视口坐标系） */
    private var startOffset = 0

    /** 手指累计位移（原始值，不校正） */
    private var rawDelta = 0f

    /** 位移绝对值累计，松手时和 tapThresholdPx 比较 */
    private var travel = 0f

    /** 本次拖拽是否真的换过位（决定松手要不要落库） */
    private var moved = false

    /** LazyColumn 自身的根坐标 y（置顶区悬停判定换算用） */
    private var viewportTop = 0

    /** 置顶区下边界的根坐标（0 = 分区不在屏上） */
    private var pinZoneBottom = 0

    private var settleJob: Job? = null

    private val draggedInfo: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    fun reportViewportTop(y: Int) {
        viewportTop = y
    }

    fun reportPinZone(bottomInRoot: Int) {
        pinZoneBottom = bottomInRoot
    }

    /** 分区消失（拖动结束）时清掉旧边界，防止下次拖动用上过期数据 */
    fun clearPinZone() {
        pinZoneBottom = 0
    }

    /** 卡片透明度只在「手指还按着」时降低，回弹期间恢复正常 */
    fun isVisuallyDragging(key: String): Boolean = draggingKey == key && !settling

    /** 挂在可拖条目上：长按后接管拖动手势 */
    fun gestureModifier(key: String): Modifier = Modifier.pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { startDrag(key) },
            onDrag = { change, amount ->
                change.consume()
                dragBy(amount.y)
            },
            onDragEnd = { endDrag(key, cancelled = false) },
            onDragCancel = { endDrag(key, cancelled = true) }
        )
    }

    private fun startDrag(key: String) {
        val info = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == key } ?: return
        // 上一次的回弹动画还在跑就先掐掉，防止它继续写 displayTranslation
        settleJob?.cancel()
        draggingKey = key
        startOffset = info.offset
        rawDelta = 0f
        travel = 0f
        moved = false
        settling = false
        displayTranslation = 0f
        pinHover = false
    }

    private fun dragBy(deltaY: Float) {
        val key = draggingKey ?: return
        val info = draggedInfo ?: return
        rawDelta += deltaY
        travel += abs(deltaY)

        // 显示位移 = 手指原始位移 + 布局位置修正量。
        // 换位后条目的布局位置跳了，修正量把它拉回来，视觉上纹丝不动贴着手指。
        // 这一行是「连续拖多格不打架」的全部秘密。
        displayTranslation = rawDelta + (startOffset - info.offset)

        val visualTop = startOffset + rawDelta
        val downward = deltaY >= 0

        // 置顶区悬停判定（只对习惯卡）：虚拟顶部越过分区的下边界就算悬停。
        // 悬停期间冻结换位——都到最顶上了，也没有可换的邻居
        if (key.startsWith(HABIT_PREFIX)) {
            pinHover = pinZoneBottom > 0 && (viewportTop + visualTop) < pinZoneBottom
            if (pinHover) return
        }

        if (key.startsWith(CAT_PREFIX)) {
            trySwapGroup(key, info, visualTop, downward)
        } else {
            trySwapItem(key, info, visualTop, downward)
        }
        autoScroll(visualTop, info.size)
    }

    /**
     * 习惯卡换位：中心命中哪个邻居就和它换。
     * 即便布局还没来得及刷新（同一帧内连发），moveRow 的结果也是幂等的，
     * 不会出现「换了又换回去」的抖动。
     */
    private fun trySwapItem(
        key: String,
        info: LazyListItemInfo,
        visualTop: Float,
        downward: Boolean
    ) {
        val center = visualTop + info.size / 2f
        val hovered = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != key && isRowKey(it.key.toString()) &&
                center >= it.offset && center < it.offset + it.size
        } ?: return
        if (onMove(key, hovered.key.toString(), downward)) moved = true
    }

    /**
     * 分类头换位：只认**另一个分类头**做目标，越过它的中线就整组互换。
     * 中间隔着的习惯行直接跳过——拖分组头的预期是整组搬家，一格一格挪太磨叽。
     */
    private fun trySwapGroup(
        key: String,
        info: LazyListItemInfo,
        visualTop: Float,
        downward: Boolean
    ) {
        val center = visualTop + info.size / 2f
        val others = listState.layoutInfo.visibleItemsInfo.filter {
            it.key != key && it.key.toString().startsWith(CAT_PREFIX)
        }
        val target = if (downward) {
            others.filter { it.offset > info.offset }.minByOrNull { it.offset }
        } else {
            others.filter { it.offset + it.size < info.offset + info.size }
                .maxByOrNull { it.offset }
        } ?: return
        val crossed = if (downward) {
            center > target.offset + target.size / 2f
        } else {
            center < target.offset + target.size / 2f
        }
        if (crossed && onSwapGroups(
                key.removePrefix(CAT_PREFIX),
                target.key.toString().removePrefix(CAT_PREFIX)
            )
        ) {
            moved = true
        }
    }

    /** 拖到列表边缘时自动滚动，够不到屏幕外的条目时用 */
    private fun autoScroll(visualTop: Float, itemSize: Int) {
        val layout = listState.layoutInfo
        val center = visualTop + itemSize / 2f
        when {
            center < layout.viewportStartOffset + EDGE_SCROLL_PX ->
                scope.launch { listState.scrollBy(-EDGE_SCROLL_STEP) }
            center > layout.viewportEndOffset - EDGE_SCROLL_PX ->
                scope.launch { listState.scrollBy(EDGE_SCROLL_STEP) }
        }
    }

    private fun endDrag(key: String, cancelled: Boolean) {
        if (draggingKey == null) return
        val habitId = if (key.startsWith(HABIT_PREFIX)) habitIdOf(key) else null
        val pinDrop = !cancelled && habitId != null && pinHover
        if (!cancelled && habitId != null) {
            when {
                // 悬停在置顶区上松手 → 置顶（「松手置顶」的预告兑现）
                pinDrop -> {
                    SoundEffectPlayer.play(SoundEffectPlayer.Effect.DakaPin)
                    onDropPin(habitId)
                }
                // 长按后没怎么挪 → 原地长按，弹操作面板
                travel < tapThresholdPx -> onLongPressHabit(habitId)
            }
        }
        settle()
        // 置顶走 ViewModel 自己的落库逻辑，本地顺序没变，不用再落一遍
        if (moved && !pinDrop) {
            SoundEffectPlayer.play(SoundEffectPlayer.Effect.DakaDrag)
            onCommitOrder()
        }
        moved = false
    }

    /** 松手：显示位移弹簧动画归零，卡片滑进最终位置，动画结束后清空拖拽状态 */
    private fun settle() {
        if (draggingKey == null) return
        settling = true
        settleJob = scope.launch {
            val from = displayTranslation
            if (from != 0f) {
                animate(
                    initialValue = from,
                    targetValue = 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { value, _ -> displayTranslation = value }
            }
            draggingKey = null
            settling = false
            displayTranslation = 0f
            pinHover = false
        }
    }

    private companion object {
        /** 距列表边缘多近开始自动滚动 */
        const val EDGE_SCROLL_PX = 90f

        /** 每个手势事件的自动滚动步长（px） */
        const val EDGE_SCROLL_STEP = 6f
    }
}

/**
 * 左右滑包裹层。
 *
 * 为什么不用 confirmValueChange 拦截？
 * 它在 material3 新版里已经废弃（官方给的替代思路是「不该出现的锚点就不要放进锚点集」，
 * 但对「滑动触发动作但不消失」的场景没有直接替代）。
 * 现在的写法：让卡片正常滑到位 → LaunchedEffect 监听到落点 → 触发动作 → 弹回原位。
 * 用户体验上等价（卡片本来也不会真的划走），代码还躲开了废弃 API。
 *
 * 触发的动作：
 *   左滑（EndToStart）= 编辑 → 直接跳编辑页。删除收进操作面板，
 *     因为左滑的误触率远高于其他手势，拿它开删除太危险；编辑高频且无害，正合适。
 *   右滑（StartToEnd）= 置顶 → 跳到分类顶部 + 图钉标记（不变）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHabitCard(
    habit: HabitUi,
    a11yActions: List<Pair<String, () -> Unit>>,
    onToggle: () -> Unit,
    onSwipeEdit: () -> Unit,
    onSwipePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()
    // 独立 scope 跑归位：snapTo 放在这里，避免被 LaunchedEffect 的 key 变化取消
    val dismissScope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> {
                // 左滑编辑。先用独立 scope 把卡片归位到 Settled（防返回后残留），
                // 再同步触发编辑。归位不阻塞导航，动作一定能执行（V4.6 修残留）。
                dismissScope.launch {
                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                }
                onSwipeEdit()
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                dismissScope.launch {
                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                }
                onSwipePin()
            }
            else -> { /* Settled：正常停着，无事可做 */ }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        modifier = modifier,
        backgroundContent = {
            // 背景只在该滑动态被**激活**（当前值非 Settled）时才渲染动作文字。
            // 用 currentValue 而不是 dismissDirection：后者在回弹/返回后仍记忆
            // 最近一次方向，会导致左滑编辑后「编辑」字样残留在右侧勾选框位置
            // （V4.6 修）。Settled 时给空白，卡片归位后背景必然干净。
            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = stringResource(R.string.home_swipe_edit),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (habit.pinned) stringResource(R.string.home_swipe_unpin) else stringResource(R.string.home_swipe_pin),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) {
        HabitCard(
            habit = habit,
            onToggle = onToggle,
            a11yActions = a11yActions
        )
    }
}

/** 顶部今日进度汇总：大数字 + 进度条 */
@Composable
private fun TodaySummary(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (total == 0) 0f else done.toFloat() / total.toFloat()
    // 读屏：把整块汇总读成一句话（semantics 块不是 Composable 上下文，先在这里算好）
    val summaryDesc = stringResource(R.string.home_summary_full, done, total) +
        "，" + stringResource(R.string.home_summary_label) + " " + (progress * 100).toInt() + "%"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { contentDescription = summaryDesc }
    ) {
        Text(
            text = stringResource(R.string.home_summary_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = done.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.home_summary_done, total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}

/** 标题栏日期文案，例如「8月30日 周日」/「Tue, Sep 2」。随应用语言本地化。 */
@Composable
private fun todayTitle(): String {
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
    val today = LocalDate.now()
    return if (locale.language == "zh") {
        val weekday = today.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        "${today.monthValue}月${today.dayOfMonth}日 $weekday"
    } else {
        DateTimeFormatter.ofPattern("EEE, MMM d", locale).format(today)
    }
}

/** Preview 专用假数据，只用于 IDE 预览，真数据来自 Room */
private val previewItems = listOf(
    HabitUi(
        id = 1, name = "喝水 2L", emoji = "💧", colorArgb = 0xFF2196F3,
        streak = 12, doneToday = true, pinned = true, category = "健康",
        last7 = listOf(true, true, true, false, true, true, true)
    ),
    HabitUi(
        id = 2, name = "跑步 3 公里", emoji = "🏃", colorArgb = 0xFFE91E63,
        streak = 3, doneToday = true, category = "健康",
        last7 = listOf(false, false, false, true, true, true, true)
    ),
    HabitUi(
        id = 3, name = "读书 30 分钟", emoji = "📖", colorArgb = 0xFF4CAF50,
        streak = 28, doneToday = false, category = "学习",
        last7 = listOf(true, true, true, true, true, true, false)
    ),
    HabitUi(
        id = 4, name = "23 点前睡觉", emoji = "💤", colorArgb = 0xFFFF9800,
        streak = 0, doneToday = false, category = "生活",
        last7 = listOf(true, false, false, false, false, false, false)
    )
)

private val previewSections = listOf(
    HomeSection(category = "健康", habits = previewItems.take(2)),
    HomeSection(category = "学习", habits = previewItems.subList(2, 3)),
    HomeSection(category = "生活", habits = previewItems.subList(3, 4))
)

@Preview(showBackground = true)
@Composable
fun HomeContentPreview() {
    DAKATheme {
        HomeContent(
            sections = previewSections,
            doneCount = 2,
            totalCount = 4,
            onToggle = { _, _ -> },
            onRequestDelete = {},
            onTogglePin = {},
            onPinToTop = {},
            onApplyOrder = {},
            onEditHabit = {},
            onMoveUp = {},
            onMoveDown = {},
            onOpenSheet = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HomeContentDarkPreview() {
    DAKATheme {
        HomeContent(
            sections = previewSections,
            doneCount = 2,
            totalCount = 4,
            onToggle = { _, _ -> },
            onRequestDelete = {},
            onTogglePin = {},
            onPinToTop = {},
            onApplyOrder = {},
            onEditHabit = {},
            onMoveUp = {},
            onMoveDown = {},
            onOpenSheet = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HomeContentEmptyPreview() {
    DAKATheme {
        HomeContent(
            sections = emptyList(),
            doneCount = 0,
            totalCount = 0,
            onToggle = { _, _ -> },
            onRequestDelete = {},
            onTogglePin = {},
            onPinToTop = {},
            onApplyOrder = {},
            onEditHabit = {},
            onMoveUp = {},
            onMoveDown = {},
            onOpenSheet = {}
        )
    }
}
