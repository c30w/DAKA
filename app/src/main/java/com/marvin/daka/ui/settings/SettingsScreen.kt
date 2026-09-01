package com.marvin.daka.ui.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marvin.daka.audio.SoundEffectPlayer
import com.marvin.daka.data.AppPrefs
import com.marvin.daka.data.LanguagePrefs
import com.marvin.daka.R
import com.marvin.daka.model.Habit
import com.marvin.daka.model.ReminderConfig
import com.marvin.daka.reminder.NotificationHelper
import com.marvin.daka.reminder.ReminderPrefs
import com.marvin.daka.reminder.ReminderRule
import com.marvin.daka.reminder.ReminderScheduler
import com.marvin.daka.ui.home.BackupSummary
import com.marvin.daka.ui.home.HabitViewModel
import com.marvin.daka.ui.reminder.ReminderEditorDialog
import com.marvin.daka.ui.theme.DAKATheme
import com.marvin.daka.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

/**
 * 设置页（V2 建立，V3 改版）。
 *
 * 这里管三件事：**每习惯提醒管理**、**提醒日历入口**、**备份导出/恢复**。
 *
 * 备份部分用的是 **SAF（Storage Access Framework，系统文件选择器）**：
 * 不申请任何存储权限，让系统弹「文件另存为」对话框，用户自己选位置，
 * 系统再把一个 [Uri] 回调给我们。
 *
 * 为什么不用「直接往 Download 目录写文件」？
 * Android 10 之后分区存储收紧，直接写公共目录要么被拒、要么要申请
 * READ/WRITE_EXTERNAL_STORAGE——而 Android 13 起这个权限基本已不可用。
 * SAF 是官方唯一推荐的出路，不需要任何权限。
 *
 * V3 的变化：提醒从「全局一个时间」变成「每个习惯一套规则」，
 * 所以这里的主角从「提醒开关 + 时间」变成了「习惯列表」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HabitViewModel,
    onBack: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val prefs = remember { ReminderPrefs(context) }
    val appPrefs = remember { AppPrefs(context) }

    // 音效开关：读 DataStore，实时同步到播放器（关了就全静音）
    val soundEnabled by appPrefs.soundEnabled.collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(soundEnabled) { SoundEffectPlayer.enabled = soundEnabled }

    // 正在读写文件时禁用按钮，防止连点
    var busy by remember { mutableStateOf(false) }
    // V4.2：选中的备份文件解析出的预览摘要；error 非空 = 解析失败
    var pendingImport by remember { mutableStateOf<BackupSummary?>(null) }

    /**
     * 等权限到手之后再落库的提醒设置。
     *
     * 为什么要暂存？因为开提醒要过两道系统权限关（通知 + 精确闹钟），
     * 两关都要跳到系统界面让用户操作，等用户回来时这个 Composable
     * 可能已经重组过好几次了。不暂存的话，「用户想给哪个习惯设几点」这个意图就丢了。
     */
    var pendingSave by remember { mutableStateOf<Pair<Long, ReminderConfig>?>(null) }

    // ⚠️ 两个 launcher 的声明顺序不能反：
    // notificationLauncher 的回调里要调 exactAlarmLauncher，
    // 而 Kotlin 局部变量**必须先声明再使用**，写反了会「未解析的引用」。
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pending = pendingSave
        pendingSave = null
        scope.launch {
            if (pending == null) return@launch
            if (hasExactAlarmPermission(context)) {
                commitReminder(viewModel, context, pending, snackbarHostState, scope)
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.snack_alarm_perm))
            }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingSave ?: return@rememberLauncherForActivityResult

        if (!granted) {
            pendingSave = null
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snack_notif_perm)) }
            return@rememberLauncherForActivityResult
        }

        // 通知过了，还有精确闹钟这一关。继续跳设置页，pendingSave 先留着
        if (!hasExactAlarmPermission(context)) {
            exactAlarmLauncher.launch(exactAlarmSettingsIntent(context))
            return@rememberLauncherForActivityResult
        }

        pendingSave = null
        commitReminder(viewModel, context, pending, snackbarHostState, scope)
    }

    /** 统一的保存入口：先过权限关，过了才写库 */
    fun saveReminder(habitId: Long, config: ReminderConfig) {
        scope.launch {
            if (config.enabled && !NotificationHelper.hasPermission(context)) {
                pendingSave = habitId to config
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@launch
            }
            if (config.enabled && !hasExactAlarmPermission(context)) {
                pendingSave = habitId to config
                exactAlarmLauncher.launch(exactAlarmSettingsIntent(context))
                return@launch
            }
            commitReminder(viewModel, context, habitId to config, snackbarHostState, scope)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // 用户取消了

        scope.launch {
            busy = true
            val result = runCatching {
                val json = viewModel.buildBackupJson()
                // use{} 保证流一定被关闭，哪怕中途抛异常
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()
                    ?.use { it.write(json) }
                    ?: error("打不开目标文件")
            }
            busy = false
            result.fold(
                onSuccess = {
                    // V4.2：导出完顺手递上「分享」入口（微信/网盘/文件管理器随便发），
                    // 不用再让用户自己去文件系统里翻刚存的那个文件在哪
                    val action = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snack_exported),
                        actionLabel = context.getString(R.string.common_share),
                        duration = SnackbarDuration.Long
                    )
                    if (action == SnackbarResult.ActionPerformed) {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, context.getString(R.string.common_share)))
                    }
                },
                onFailure = { snackbarHostState.showSnackbar(context.getString(R.string.snack_export_fail, it.message)) }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        // V4.2：选完文件先**只读预览**，把备份里有什么摆出来让用户确认，
        // 而不是甩一句「备份里的数据会与当前数据合并」就闷头改库
        scope.launch {
            busy = true
            pendingImport = runCatching {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: error("读不到文件内容")
                viewModel.inspectBackup(json)
            }.fold(
                onSuccess = { it },
                onFailure = { BackupSummary(error = it.message ?: "文件损坏或格式不对") }
            )
            busy = false
        }
    }

    val habits by viewModel.habits.collectAsStateWithLifecycle()

    // 正在编辑提醒的那个习惯
    var editingHabit by remember { mutableStateOf<Habit?>(null) }

    // i18n：语言选择弹窗开关
    var showLangDialog by remember { mutableStateOf(false) }
    val currentLangCode = LanguagePrefs.getCode(context)

    // V1.2 主题：跟随系统 / 浅色 / 深色。改完即时生效（Compose 换配色，不重建 Activity）
    val themeMode by appPrefs.themeMode.collectAsStateWithLifecycle(
        initialValue = ThemeMode.DEVICE
    )
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // AutoMirrored 版本，RTL 布局下箭头会自动翻转方向
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // V4.8：设置项较多（每习惯提醒 + 通用 + 备份恢复 + 说明），
                // 小屏会被挤出底部。加滚动让「备份与恢复」那一段一定能滚到看见。
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // ---------------- 每习惯提醒 ----------------
            HabitReminderSection(
                habits = habits.filter { it.archivedAt == null },
                onEdit = { editingHabit = it }
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // ---------------- 默认时间 + 日历入口 ----------------
            SectionTitle(stringResource(R.string.settings_general))
            DefaultReminderTimeItem(prefs = prefs)
            Spacer(modifier = Modifier.height(12.dp))
            // 音效开关：配合清脆交互音效，可一键静音
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_sound),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_sound_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = { on ->
                            scope.launch { appPrefs.setSoundEnabled(on) }
                            // 即时反馈：关的时候也播一下，让用户听清楚自己在关什么
                            if (on) SoundEffectPlayer.play(SoundEffectPlayer.Effect.DakaOk)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SettingsItem(
                title = stringResource(R.string.settings_calendar),
                subtitle = stringResource(R.string.settings_calendar_desc),
                enabled = true,
                onClick = onOpenCalendar
            )
            Spacer(modifier = Modifier.height(12.dp))
            // i18n：语言选择。改完立即写偏好并 recreate 整个 Activity 让界面刷新
            SettingsItem(
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_desc),
                enabled = true,
                onClick = { showLangDialog = true }
            )
            Spacer(modifier = Modifier.height(12.dp))
            // V1.2 主题：副标题直接显示当前是哪种模式，不用点进去才知道
            SettingsItem(
                title = stringResource(R.string.settings_theme),
                subtitle = themeModeLabel(themeMode),
                enabled = true,
                onClick = { showThemeDialog = true }
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // ---------------- 备份 ----------------
            SectionTitle(stringResource(R.string.settings_backup))

            // V4.6：备份/恢复改成两个独立、显眼的按钮（而不是普通列表项），
            // 一眼就能看到、直接点，不用在设置列表里往下翻。
            // V4.7：给两个按钮都加了图标——备份是「下载/导出」箭头（把数据存出去），
            // 恢复是「上传/导入」箭头（把数据读进来），方向感和动作一一对应。
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackupRestoreButton(
                    text = stringResource(R.string.settings_backup_btn),
                    icon = Icons.Filled.FileDownload,
                    iconDesc = stringResource(R.string.settings_backup_btn),
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val fileName = "daka-backup-${LocalDate.now()}.json"
                        exportLauncher.launch(fileName)
                    }
                )
                BackupRestoreButton(
                    text = stringResource(R.string.settings_restore_btn),
                    icon = Icons.Filled.FileUpload,
                    iconDesc = stringResource(R.string.settings_restore_btn),
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.settings_about))
            Text(
                text = stringResource(R.string.settings_about_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 提醒编辑弹窗
    editingHabit?.let { habit ->
        ReminderEditorDialog(
            habitName = habit.name,
            initial = ReminderConfig.from(habit),
            onConfirm = { config ->
                editingHabit = null
                saveReminder(habit.id, config)
            },
            onDismiss = { editingHabit = null }
        )
    }

    // V4.2：恢复前的预览确认弹窗。
    // 解析成功 = 摆出「几个习惯、几条记录、什么时候导的」；解析失败 = 只给错误和关闭。
    pendingImport?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = {
                Text(
                    if (summary.error == null) {
                        stringResource(R.string.import_title)
                    } else {
                        stringResource(R.string.import_fail_title)
                    }
                )
            },
            text = {
                when {
                    summary.error != null -> Text(stringResource(R.string.import_fail_text, summary.error))
                    else -> {
                        val dateText = Instant.ofEpochMilli(summary.exportedAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        Text(
                            stringResource(
                                R.string.import_summary,
                                summary.habitCount,
                                summary.recordCount,
                                dateText.toString()
                            )
                        )
                    }
                }
            },
            confirmButton = {
                if (summary.error == null) {
                    TextButton(
                        onClick = {
                            val json = summary.json
                            pendingImport = null
                            scope.launch {
                                busy = true
                                val result = runCatching {
                                    viewModel.restoreFromBackup(json)
                                }
                                busy = false
                                snackbarHostState.showSnackbar(
                                    result.fold(
                                        onSuccess = { count -> context.getString(R.string.restore_done, count) },
                                        onFailure = { context.getString(R.string.restore_fail, it.message) }
                                    )
                                )
                            }
                        }
                    ) { Text(stringResource(R.string.import_confirm)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // i18n：语言选择弹窗（跟随系统 / 中文 / English）。改完立刻生效。
    if (showLangDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = listOf(
                "" to stringResource(R.string.settings_language_system),
                "zh" to stringResource(R.string.settings_language_zh),
                "en" to stringResource(R.string.settings_language_en)
            ),
            selected = currentLangCode,
            onSelected = { code ->
                LanguagePrefs.setCode(context, code)
                // 语言已写入，重建 Activity 让所有界面立即用新语言重绘
                context.findActivity()?.recreate()
            },
            onDismiss = { showLangDialog = false }
        )
    }

    // V1.2：主题选择弹窗（跟随系统 / 浅色 / 深色）。
    // 只写偏好，不 recreate——Compose 会带着新 colorScheme 重组，比重建更快也不闪。
    if (showThemeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                ThemeMode.DEVICE to stringResource(R.string.settings_theme_device),
                ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
            ),
            selected = themeMode,
            onSelected = { mode -> scope.launch { appPrefs.setThemeMode(mode) } },
            onDismiss = { showThemeDialog = false }
        )
    }
}

/** 主题模式的显示名（设置项副标题用） */
@Composable
private fun themeModeLabel(mode: String): String = stringResource(
    when (ThemeMode.normalize(mode)) {
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
        else -> R.string.settings_theme_device
    }
)

/**
 * 通用单选弹窗：一列选项，当前项右侧打勾。
 *
 * 语言和主题两个设置长得几乎一样（都是「N 选 1 + 立刻生效」），
 * 抽成一份省得两处各写一遍、改样式时还容易漏掉一边。
 *
 * @param options 每个选项 = 「存进偏好的值」to「显示给用户的文案」
 * @param onSelected 只在选中**不同**的选项时回调
 */
@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                if (value != selected) onSelected(value)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (value == selected) {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** 从任意 Context（含被 locale 包装过的）一路向上找到 Activity */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 真正把提醒写进数据库并重排闹钟。
 *
 * 抽成顶层 suspend 函数（而不是 Composable 里的局部函数）：
 * 一是 Composable 内声明函数容易踩 Compose 编译器的坑，
 * 二是它不依赖任何 Composable 状态，本来就该独立。
 */
private fun commitReminder(
    viewModel: HabitViewModel,
    context: Context,
    pending: Pair<Long, ReminderConfig>,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val (habitId, config) = pending
    viewModel.updateReminder(habitId, config)
    scope.launch {
        snackbarHostState.showSnackbar(
            if (config.enabled) {
                if (ReminderScheduler.canScheduleExact(context)) {
                    context.getString(R.string.reminder_on)
                } else {
                    context.getString(R.string.reminder_exact_warn)
                }
            } else {
                context.getString(R.string.reminder_off)
            }
        )
    }
}

// ------------------------------------------------------------------
// 每习惯提醒
// ------------------------------------------------------------------

/**
 * 逐个习惯管理提醒。
 *
 * ⚠️ 开个提醒在 Android 上要过**两道权限关**，这是代码绕不开的现实：
 *
 * 1. **通知权限**（Android 13 / API 33 起）
 *    运行时危险权限，弹系统对话框让用户点允许。
 *
 * 2. **精确闹钟权限**（Android 12 引入，Android 14 起默认**不授予**）
 *    这个最坑：不在运行时弹窗申请，必须跳系统设置页让用户手动开，
 *    而且没有可靠回调——用户开了没有、什么时候开的，只能等他从设置页回来再查一次。
 *
 * 所以流程是「检查 → 缺哪个引导去开哪个 → 开完回来再写库」，
 * 不是一次性搞定。
 */
@Composable
private fun HabitReminderSection(
    habits: List<Habit>,
    onEdit: (Habit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        SectionTitle(stringResource(R.string.settings_habit_reminder))

        if (habits.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_no_habit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        // 权限状态提示：没拿到就明确告诉用户差哪个，别让他对着「没反应」的开关发呆
        val missingPermissions = missingReminderPermissions(context)
        if (missingPermissions.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_missing_perm,
                        missingPermissions.joinToString(
                            if (context.resources.configuration.locales[0]?.language == "zh") "、" else ", "
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            habits.forEach { habit ->
                HabitReminderRow(habit = habit, onClick = { onEdit(habit) })
            }
        }
    }
}

/** 一行：emoji + 名字 + 提醒规则描述 + 开关状态 */
@Composable
private fun HabitReminderRow(
    habit: Habit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 习惯色的圆点底托，和首页卡片保持一致，方便对上号
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(habit.colorArgb).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = habit.emoji, style = MaterialTheme.typography.titleSmall)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = ReminderRule.describe(habit),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (habit.reminderEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                )
            }

            Switch(
                checked = habit.reminderEnabled,
                // 整行的点击交给 Surface，这里的 onCheckedChange 只负责「点了开关本身」。
                // 因为 Surface 已经有 onClick 会打开编辑弹窗，
                // 开关本身再响一次就会变成「点开关 = 弹窗 + 切换」两个动作叠加。
                // 传 null 让 Switch 变成纯展示，所有操作统一从弹窗里走。
                onCheckedChange = null
            )
        }
    }
}

/** 当前缺哪些提醒权限。空列表 = 都齐了 */
private fun missingReminderPermissions(context: Context): List<String> {
    val result = mutableListOf<String>()
    if (!NotificationHelper.hasPermission(context)) {
        result += context.getString(R.string.perm_notification)
    }
    if (!hasExactAlarmPermission(context)) {
        result += context.getString(R.string.perm_alarm)
    }
    return result
}

// ------------------------------------------------------------------
// 默认提醒时间
// ------------------------------------------------------------------

/** 新建习惯时预填的提醒时间 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultReminderTimeItem(prefs: ReminderPrefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hour by prefs.defaultHour.collectAsStateWithLifecycle(initialValue = 21)
    val minute by prefs.defaultMinute.collectAsStateWithLifecycle(initialValue = 0)

    var showTimePicker by remember { mutableStateOf(false) }

    SettingsItem(
        title = stringResource(R.string.settings_default_reminder),
        subtitle = stringResource(
            R.string.settings_default_reminder_desc,
            "%02d:%02d".format(hour, minute)
        ),
        enabled = true,
        onClick = { showTimePicker = true }
    )

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        TimePickerDialog(
            // 新版 M3 把 title 提到了第一个**必需**参数位置，不传编译不过
            title = { Text(stringResource(R.string.settings_default_reminder_title)) },
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                        scope.launch { prefs.setDefaultTime(state.hour, state.minute) }
                    }
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        ) {
            TimePicker(state = state)
        }
    }
}

// ------------------------------------------------------------------
// 权限工具
// ------------------------------------------------------------------

/** Android 12 起要判断能不能用精确闹钟 */
private fun hasExactAlarmPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}

/** 跳系统「闹钟和提醒」授权页 */
private fun exactAlarmSettingsIntent(context: Context) =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
    }

// ------------------------------------------------------------------
// 通用小组件
// ------------------------------------------------------------------

/** 分组标题 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/**
 * 备份/恢复的独立按钮。实色 Button，醒目可点，和普通列表项拉开视觉层级。
 *
 * [icon] + [iconDesc]：左侧一个图标，让「备份(导出)」「恢复(导入)」一眼能分清方向，
 * 不识字、看不清文字也照样知道哪个是哪个。
 */
@Composable
private fun BackupRestoreButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconDesc: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Icon(icon, contentDescription = iconDesc, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** 一行可点击的设置项 */
@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SettingsItemPreview() {
    DAKATheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("备份与恢复")
            SettingsItem(
                title = "导出备份",
                subtitle = "把全部习惯和打卡记录存成一个 JSON 文件",
                enabled = true,
                onClick = {}
            )
        }
    }
}
