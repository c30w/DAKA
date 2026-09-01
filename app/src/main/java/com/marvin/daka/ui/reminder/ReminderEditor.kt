package com.marvin.daka.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.marvin.daka.R
import com.marvin.daka.model.EndType
import com.marvin.daka.model.ReminderConfig
import com.marvin.daka.model.RepeatType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 提醒设置编辑器 —— 新建习惯页和设置页**共用**这一套 UI。
 *
 * 抽出来的理由很实在：提醒有两套配置入口（新建时顺手设、之后回头改），
 * 如果各写一份，将来加一个「按农历重复」就得改两个地方，而且必然改漏一个。
 *
 * 这个 Composable 是**无状态**的：当前值由外部通过 [config] 传进来，
 * 改动通过 [onChange] 报出去。这个套路叫状态提升——
 * 好处是调用方可以决定「存哪、什么时候存」，这里只管画和收集输入。
 *
 * @param config   当前的提醒配置
 * @param onChange 任何一项被改动时回调，参数是改完之后的完整配置
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderConfigEditor(
    config: ReminderConfig,
    onChange: (ReminderConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ---- 总开关 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reminder_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (config.enabled) {
                        stringResource(R.string.reminder_on_desc)
                    } else {
                        stringResource(R.string.reminder_off_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 整行不设 onClick：外层可点 + 内层 Switch 可点会触发两次切换，等于没反应
            Switch(
                checked = config.enabled,
                onCheckedChange = { onChange(config.copy(enabled = it)) }
            )
        }

        if (!config.enabled) return

        Spacer(modifier = Modifier.height(16.dp))

        // ---- 提醒时间 ----
        SectionLabel(stringResource(R.string.reminder_time_section))
        TimePickerRow(
            hour = config.hour,
            minute = config.minute,
            onTimeChange = { h, m -> onChange(config.copy(hour = h, minute = m)) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ---- 重复方式 ----
        SectionLabel(stringResource(R.string.reminder_repeat_section))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RepeatType.entries.forEach { type ->
                FilterChip(
                    selected = config.repeatType == type,
                    onClick = { onChange(config.copy(repeatType = type)) },
                    label = { Text(repeatTypeLabel(type)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- 按重复方式展开的细化设置 ----
        when (config.repeatType) {
            RepeatType.DAILY -> {
                HintText(stringResource(R.string.reminder_every_day))
            }

            RepeatType.INTERVAL_DAYS -> {
                NumberStepper(
                    label = stringResource(R.string.reminder_interval_label),
                    value = config.interval,
                    range = 1..365,
                    suffix = stringResource(R.string.reminder_interval_suffix),
                    onValueChange = { onChange(config.copy(interval = it)) }
                )
                HintText(stringResource(R.string.reminder_interval_hint, config.interval))
            }

            RepeatType.WEEKLY -> {
                WeekdaySelector(
                    selected = config.weekdays,
                    onToggle = { day ->
                        val next = if (day in config.weekdays) {
                            config.weekdays - day
                        } else {
                            config.weekdays + day
                        }
                        onChange(config.copy(weekdays = next))
                    }
                )
                HintText(
                    if (config.weekdays.isEmpty()) {
                        stringResource(R.string.reminder_weekly_none)
                    } else {
                        stringResource(R.string.reminder_weekly_picked, config.weekdays.size)
                    }
                )
            }

            RepeatType.MONTHLY -> {
                MonthDaySelector(
                    selected = config.monthDays,
                    onToggle = { day ->
                        val next = if (day in config.monthDays) {
                            config.monthDays - day
                        } else {
                            config.monthDays + day
                        }
                        onChange(config.copy(monthDays = next))
                    }
                )
                HintText(
                    if (config.monthDays.isEmpty()) {
                        stringResource(R.string.reminder_monthly_none)
                    } else {
                        stringResource(
                            R.string.reminder_monthly_picked,
                            config.monthDays.sorted().joinToString(listSeparator())
                        )
                    }
                )
            }

            RepeatType.WORKDAY -> HintText(stringResource(R.string.reminder_workday))

            RepeatType.WEEKEND_HOLIDAY -> HintText(stringResource(R.string.reminder_weekend))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- 结束方式 ----
        SectionLabel(stringResource(R.string.reminder_end_section))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            EndType.entries.forEach { type ->
                FilterChip(
                    selected = config.endType == type,
                    onClick = { onChange(config.copy(endType = type)) },
                    label = { Text(endTypeLabel(type)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (config.endType) {
            EndType.NEVER -> HintText(stringResource(R.string.reminder_never))

            EndType.AFTER_TIMES -> {
                NumberStepper(
                    label = stringResource(R.string.reminder_after_times_label),
                    value = config.times,
                    range = 1..999,
                    suffix = stringResource(R.string.reminder_after_times_suffix),
                    onValueChange = { onChange(config.copy(times = it)) }
                )
                HintText(stringResource(R.string.reminder_after_times_hint, config.times))
            }

            EndType.ON_DATE -> {
                DatePickerRow(
                    label = stringResource(R.string.reminder_end_date),
                    date = config.endDate,
                    onDateChange = { onChange(config.copy(endDate = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- 开始日期 ----
        SectionLabel(stringResource(R.string.reminder_start_section))
        DatePickerRow(
            label = stringResource(R.string.reminder_start_from),
            date = config.startDate,
            onDateChange = { onChange(config.copy(startDate = it)) }
        )
        HintText(stringResource(R.string.reminder_start_hint))
    }
}

/** 列表分隔符：中文用「、」，其它语言用英文逗号 */
@Composable
private fun listSeparator(): String =
    if (LocalContext.current.resources.configuration.locales[0]?.language == "zh") "、" else ", "

/** 重复方式枚举 → 本地化标签 */
@Composable
private fun repeatTypeLabel(type: RepeatType): String = stringResource(
    when (type) {
        RepeatType.DAILY -> R.string.repeat_daily
        RepeatType.INTERVAL_DAYS -> R.string.repeat_interval
        RepeatType.WEEKLY -> R.string.repeat_weekly
        RepeatType.MONTHLY -> R.string.repeat_monthly
        RepeatType.WORKDAY -> R.string.repeat_workday
        RepeatType.WEEKEND_HOLIDAY -> R.string.repeat_weekend
    }
)

/** 结束方式枚举 → 本地化标签 */
@Composable
private fun endTypeLabel(type: EndType): String = stringResource(
    when (type) {
        EndType.NEVER -> R.string.end_never
        EndType.AFTER_TIMES -> R.string.end_after_times
        EndType.ON_DATE -> R.string.end_on_date
    }
)

// ------------------------------------------------------------------
// 编辑器内部的各种输入控件
// ------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 时间选择行：显示当前时间，点击弹系统的 TimePicker */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerRow(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "%02d:%02d".format(hour, minute),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }

    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true // 中文用户习惯 24 小时制
        )
        TimePickerDialog(
            // 新版 M3 把 title 提到了第一个**必需**参数位置，不传编译不过
            title = { Text(stringResource(R.string.reminder_pick_time)) },
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPicker = false
                        onTimeChange(state.hour, state.minute)
                    }
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        ) {
            TimePicker(state = state)
        }
    }
}

/** 日期选择行。空值显示为「不限 / 今天」 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerRow(
    label: String,
    date: String,
    onDateChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
            Text(
                text = date.ifBlank {
                    label + listSeparator() + stringResource(R.string.reminder_date_any)
                }
            )
        }
        if (date.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { onDateChange("") }) { Text(stringResource(R.string.reminder_clear)) }
        }
    }

    if (showPicker) {
        val initial = runCatching { LocalDate.parse(date) }.getOrNull() ?: LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
            // 结束日期不能选到今天之前——选了等于「立刻结束」，没意义
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= LocalDate.now()
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPicker = false
                        state.selectedDateMillis?.let { millis ->
                            onDateChange(
                                Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                            )
                        }
                    }
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/** 数字步进器。比让用户敲键盘省心，也避免了各种输入校验 */
@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String,
    onValueChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            enabled = value > range.first
        ) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.reminder_decrease))
        }

        Text(
            text = "$value $suffix",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp)
        )

        IconButton(
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            enabled = value < range.last
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.reminder_increase))
        }
    }
}

@Composable
private fun WeekdaySelector(
    selected: Set<Int>,
    onToggle: (Int) -> Unit
) {
    val locale = LocalContext.current.resources.configuration.locales[0] ?: java.util.Locale.getDefault()
    val labels = (1..7).map { day ->
        java.time.DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.NARROW, locale)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val day = index + 1
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                // 7 个 chip 挤一行，默认的 chip 内边距太大会换行，这里收紧
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent
                )
            )
        }
    }
}

/** 每月几号的多选。31 个格子，5 行 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthDaySelector(
    selected: Set<Int>,
    onToggle: (Int) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        (1..31).forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = { Text(day.toString()) },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent
                )
            )
        }
    }
}

// ------------------------------------------------------------------
// 弹窗版本：设置页里改单个习惯的提醒
// ------------------------------------------------------------------

/**
 * 编辑某个习惯提醒的弹窗。
 *
 * 用 Dialog + 可滚动 Surface，而不是 AlertDialog：
 * AlertDialog 的内容区高度有限，提醒设置项一多（6 种重复 × 各种细化配置）
 * 就会被截断，用户根本滑不到底部的「保存」按钮。
 * 自己套一层 Dialog 就能自由控制滚动和高度上限。
 *
 * @param habitName 标题里显示「给谁设提醒」
 * @param initial   当前配置
 * @param onConfirm 点保存。传回改完的配置，由调用方负责写库和重排闹钟
 */
@Composable
fun ReminderEditorDialog(
    habitName: String,
    initial: ReminderConfig,
    onConfirm: (ReminderConfig) -> Unit,
    onDismiss: () -> Unit
) {
    // 弹窗内部持有草稿状态：改了不点保存就不生效，符合「取消」的直觉
    var draft by remember(initial) { mutableStateOf(initial) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column {
                // 标题（固定不滚）
                Text(
                    text = stringResource(R.string.reminder_dialog_title, habitName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 内容区（可滚动，且有高度上限，防止在小屏上顶满）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    ReminderConfigEditor(config = draft, onChange = { draft = it })
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 按钮（固定不滚）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(draft) }) { Text(stringResource(R.string.common_save)) }
                }
            }
        }
    }
}
