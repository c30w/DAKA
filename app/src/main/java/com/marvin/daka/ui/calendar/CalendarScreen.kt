package com.marvin.daka.ui.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marvin.daka.calendar.CalendarAccount
import com.marvin.daka.calendar.CalendarEvent
import com.marvin.daka.calendar.CalendarRepository
import com.marvin.daka.model.ReminderOccurrence
import com.marvin.daka.reminder.ReminderPrefs
import com.marvin.daka.ui.home.HabitViewModel
import com.marvin.daka.util.CnHoliday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 提醒日历页（V3）。
 *
 * 一页看全两件事：
 *   1. **习惯提醒** —— 每个习惯的重复规则展开成具体日期，画在月历上
 *   2. **系统日程** —— 从手机日历 App（安卓日历 / 谷歌日历 / 厂商日历）读来的日程
 *
 * 点某一天，下方列出这天的全部条目，两类分开显示。
 *
 * 页面底部还有「同步到系统日历」：把习惯提醒以 RRULE 的形式写进用户选的日历，
 * 这样在手机自带的日历 App 里也能看到、也会响。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: HabitViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val anchor by viewModel.monthAnchor.collectAsStateWithLifecycle()
    val occurrences by viewModel.reminderOccurrences.collectAsStateWithLifecycle()
    val systemEvents by viewModel.systemEvents.collectAsStateWithLifecycle()
    val calendarReadable by viewModel.calendarReadable.collectAsStateWithLifecycle()

    // 默认选中今天；翻月后如果今天不在视野里也没关系，用户可以自己点
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    // 读系统日历。放在 LaunchedEffect(Unit) 里而不是每次重组都调：
    // ContentResolver 查询不算便宜，重组一次查一次会让滑动掉帧
    LaunchedEffect(Unit) {
        viewModel.refreshSystemEvents(context)
    }

    // 按日期分桶。groupBy 出来的 Map 直接查，比每次 filter 整表快
    val occurrencesByDate = remember(occurrences) { occurrences.groupBy { it.date } }
    val eventsByDate = remember(systemEvents) {
        systemEvents.groupBy {
            java.time.Instant.ofEpochMilli(it.startMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    }

    val dayReminders = occurrencesByDate[selectedDate].orEmpty()
    val dayEvents = eventsByDate[selectedDate].orEmpty()

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("提醒日历") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        selectedDate = LocalDate.now()
                        viewModel.goToday()
                    }) { Text("今天") }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 月份切换 + 月历网格 ----
            item {
                MonthHeader(
                    anchor = anchor,
                    onPrev = { viewModel.goMonth(-1) },
                    onNext = { viewModel.goMonth(1) }
                )
            }

            item { WeekdayHeader() }

            // 6 行 × 7 列。固定 6 行，和 ViewModel 里 monthGridRange 的 42 天严格对应
            items(6) { weekIndex ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { dayIndex ->
                        val date = gridDate(anchor, weekIndex * 7 + dayIndex)
                        DayCell(
                            date = date,
                            inCurrentMonth = date.month == anchor.month,
                            isToday = date == LocalDate.now(),
                            isSelected = date == selectedDate,
                            reminders = occurrencesByDate[date].orEmpty(),
                            eventCount = eventsByDate[date].orEmpty().size,
                            onClick = { selectedDate = date },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ---- 选中那天的详情 ----
            item {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatDateTitle(selectedDate),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (dayReminders.isEmpty() && dayEvents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "这一天没有提醒，也没有日程",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (dayReminders.isNotEmpty()) {
                item {
                    SectionLabel(text = "习惯提醒 · ${dayReminders.size}")
                }
                items(items = dayReminders, key = { "${it.habitId}_${it.date}" }) { occurrence ->
                    ReminderRow(occurrence = occurrence)
                }
            }

            if (dayEvents.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionLabel(text = "手机日程 · ${dayEvents.size}")
                }
                items(items = dayEvents, key = { it.eventId }) { event ->
                    SystemEventRow(event = event)
                }
            }

            // ---- 系统日历同步 ----
            item {
                Spacer(modifier = Modifier.height(8.dp))
                CalendarSyncCard(
                    readable = calendarReadable,
                    onPermissionGranted = { viewModel.refreshSystemEvents(context) },
                    snackbarHostState = snackbarHostState,
                    viewModel = viewModel
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ------------------------------------------------------------------
// 月历网格
// ------------------------------------------------------------------

/** 网格里第 index 格对应的日期。起点是「当月 1 号所在那一周的周一」 */
private fun gridDate(anchor: LocalDate, index: Int): LocalDate {
    val first = anchor.withDayOfMonth(1)
    val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
    return gridStart.plusDays(index.toLong())
}

@Composable
private fun MonthHeader(
    anchor: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${anchor.year} 年 ${anchor.monthValue} 月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            )
        }
    }
}

/**
 * 月历里的一天。
 *
 * 视觉信息密度是这块的设计重点：一格就那么大，要同时表达
 * 「几号 / 是不是今天 / 选没选中 / 属于哪个月 / 有几条提醒 / 有几条日程」。
 * 所以约定：
 *   - 今天：主题色**描边圆**（不用实心底，会和选中态打架）
 *   - 选中：实心底
 *   - 非本月：文字淡化到 38%
 *   - 提醒：最多 3 个小圆点，颜色用习惯自己的主题色；超过 3 条显示 "3+"
 *   - 日程：底部一条细横杠（和系统提醒区分开：圆点=打卡提醒，横杠=手机日程）
 *   - V4：法定节假日格子下显示节日名（红色，跟纸质日历的习惯一致）；
 *     调休补班的周末显示一个小「班」字——这是「工作日」提醒模式最容易踩坑的日子
 */
@Composable
private fun DayCell(
    date: LocalDate,
    inCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    reminders: List<ReminderOccurrence>,
    eventCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = when {
        !inCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    // 节假日信息：节日名（放假）或「班」（调休补班的周末）
    val holidayName = CnHoliday.holidayName(date)
    val isMakeupWorkday = CnHoliday.isWorkday(date) && date.dayOfWeek.value >= 6

    Column(
        modifier = modifier
            // aspectRatio(1f) 让格子保持正方形，不同屏幕宽度下都不会压扁
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                when {
                    isSelected -> Modifier.background(MaterialTheme.colorScheme.primary)
                    isToday -> Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    )
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )

        // 节日名 / 调休「班」标记。没节日没调休就不占这一行
        if (holidayName != null || isMakeupWorkday) {
            Text(
                text = holidayName ?: "班",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isMakeupWorkday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 提醒圆点。最多画 3 个，再多就显示数字——
        // 一格塞不下 5 个圆点，硬塞会挤成一团
        if (reminders.isNotEmpty()) {
            if (reminders.size <= 3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reminders.forEach {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(it.colorArgb))
                        )
                    }
                }
            } else {
                Text(
                    text = "${reminders.size} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }

        // 日程横杠
        if (eventCount > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
            )
        }
    }
}

// ------------------------------------------------------------------
// 当天详情列表
// ------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun ReminderRow(occurrence: ReminderOccurrence) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = occurrence.emoji, style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = occurrence.habitName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = occurrence.ruleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "%02d:%02d".format(occurrence.hour, occurrence.minute),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(occurrence.colorArgb)
            )
        }
    }
}

@Composable
private fun SystemEventRow(event: CalendarEvent) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧竖条用日历自己的颜色，一眼能看出这个日程来自哪个日历
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(event.calendarColor))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = buildString {
                        append(event.calendarName.ifBlank { "系统日历" })
                        append(" · ")
                        append(if (event.allDay) "全天" else formatTime(event.startMillis))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// 系统日历同步
// ------------------------------------------------------------------

/**
 * 同步到手机日历的卡片。
 *
 * 要过的关比看起来多：
 *   1. READ_CALENDAR / WRITE_CALENDAR 两个危险权限（Android 6 起动态申请）
 *   2. 用户得选一个**可写**的日历（只读日历写了会被 provider 拒绝）
 *   3. 上次选的日历可能已经被删了，同步前要校验
 *
 * 所以这里的顺序是「要权限 → 列日历 → 选一个 → 同步」，
 * 每一步都可能停下来等用户操作，不是一次点完就完事的。
 */
@Composable
private fun CalendarSyncCard(
    readable: Boolean,
    onPermissionGranted: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ReminderPrefs(context) }
    val repository = remember { CalendarRepository(context) }

    val syncEnabled by prefs.calendarSyncEnabled.collectAsStateWithLifecycle(initialValue = false)
    val savedCalendarId by prefs.calendarId.collectAsStateWithLifecycle(initialValue = -1L)

    // 可写日历列表。权限到手后才查得出来
    val calendars = remember { mutableStateListOf<CalendarAccount>() }
    var selectedCalendarId by remember { mutableStateOf<Long?>(null) }

    // 用 savedCalendarId 初始化选中项。只在第一次拿到值时设一次
    LaunchedEffect(savedCalendarId, calendars.size) {
        if (selectedCalendarId == null && savedCalendarId > 0) {
            selectedCalendarId = savedCalendarId
        }
    }

    /** 权限到手后刷新日历列表 */
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.READ_CALENDAR] == true &&
            granted[Manifest.permission.WRITE_CALENDAR] == true
        if (!ok) {
            scope.launch { snackbarHostState.showSnackbar("没有日历权限，读不到日程也无法同步") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            calendars.clear()
            calendars.addAll(withContext(Dispatchers.IO) { repository.listWritableCalendars() })
            onPermissionGranted()
            if (calendars.isEmpty()) {
                snackbarHostState.showSnackbar("手机上没有可写入的日历，请先在日历 App 里添加一个账户")
            }
        }
    }

    /** 已经授权过（第二次进页面）时，直接把列表填上，别让用户再点一次授权 */
    LaunchedEffect(Unit) {
        if (repository.hasReadPermission() && calendars.isEmpty()) {
            withContext(Dispatchers.IO) { repository.listWritableCalendars() }
                .let { calendars.addAll(it) }
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "同步到手机日历",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "把习惯提醒写进系统日历（安卓日历 / 谷歌日历 / 厂商日历），日历 App 里也能看到和提醒；同时把日历里的日程读到这里显示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!repository.hasReadPermission()) {
                Button(onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR
                        )
                    )
                }) { Text("授权读取日历") }
                return@Card
            }

            // 同步开关
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "开启同步", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (syncEnabled) "已开启" else "关闭后会把已写入的提醒从日历里删掉",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = syncEnabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            if (!checked) {
                                withContext(Dispatchers.IO) {
                                    repository.clearSyncedEvents(
                                        calendarId = null,
                                        knownEventIds = prefs.syncedEventIds.first()
                                    )
                                }
                                prefs.setSyncedEventIds(emptyMap())
                                prefs.setCalendarSync(false)
                                snackbarHostState.showSnackbar("已停止同步，日历里的 DAKA 提醒已清除")
                            } else {
                                if (calendars.isEmpty()) {
                                    calendars.addAll(
                                        withContext(Dispatchers.IO) {
                                            repository.listWritableCalendars()
                                        }
                                    )
                                }
                                if (calendars.isEmpty()) {
                                    snackbarHostState.showSnackbar("没有可写入的日历，请先在日历 App 里添加一个")
                                    return@launch
                                }
                                prefs.setCalendarSync(true)
                            }
                        }
                    }
                )
            }

            if (!syncEnabled) return@Card

            Spacer(modifier = Modifier.height(12.dp))

            // 选哪个日历
            Text(
                text = "写到哪个日历",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (calendars.isEmpty()) {
                Text(
                    text = "没有可写入的日历",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    calendars.forEach { account ->
                        CalendarChoiceRow(
                            account = account,
                            selected = selectedCalendarId == account.id,
                            onSelect = {
                                selectedCalendarId = account.id
                                scope.launch { prefs.setCalendarId(account.id) }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val calendarId = selectedCalendarId
                    if (calendarId == null) {
                        scope.launch { snackbarHostState.showSnackbar("先选一个日历") }
                        return@OutlinedButton
                    }
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                // 当前所有「开启提醒且未归档」的习惯，就是这次要同步的全部内容
                                val habits = viewModel.habits.value.filter {
                                    it.reminderEnabled && it.archivedAt == null
                                }
                                val mapping = repository.syncHabits(
                                    habits = habits,
                                    calendarId = calendarId,
                                    knownEventIds = prefs.syncedEventIds.first()
                                )
                                prefs.setSyncedEventIds(mapping)
                                mapping.size
                            }
                        }
                        snackbarHostState.showSnackbar(
                            result.fold(
                                onSuccess = { "已同步 $it 个提醒到系统日历" },
                                onFailure = { "同步失败：${it.message}" }
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("立即同步") }

            if (readable) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已读取到系统日历日程，月历上以横杠标记",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalendarChoiceRow(
    account: CalendarAccount,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(account.color))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.displayName, style = MaterialTheme.typography.bodyMedium)
                if (account.accountName.isNotBlank()) {
                    Text(
                        text = account.accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Text(
                    text = "已选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// 小工具
// ------------------------------------------------------------------

/** "9月2日 周三"；落在法定节假日里会带上节日名，例如 "10月1日 周四 · 国庆节（放假）" */
private fun formatDateTitle(date: LocalDate): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    val base = if (date == LocalDate.now()) {
        "今天 · ${date.monthValue}月${date.dayOfMonth}日 $weekday"
    } else {
        "${date.monthValue}月${date.dayOfMonth}日 $weekday"
    }
    val holiday = CnHoliday.holidayName(date)
    if (holiday != null) return "$base · $holiday（放假）"
    // 调休补班的周末：用户最需要被提醒的日子
    if (CnHoliday.isWorkday(date) && date.dayOfWeek.value >= 6) {
        return "$base · 调休上班"
    }
    return base
}

/** 毫秒时间戳 → "HH:mm" */
private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .format(java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
