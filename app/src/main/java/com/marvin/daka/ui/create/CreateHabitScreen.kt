package com.marvin.daka.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.marvin.daka.R
import androidx.compose.material.icons.filled.Close
import com.marvin.daka.ui.reminder.ReminderEditorDialog
import com.marvin.daka.reminder.ReminderRule
import com.marvin.daka.model.toReminder
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Surface
import com.marvin.daka.audio.SoundEffectPlayer
import com.marvin.daka.data.AppPrefs
import com.marvin.daka.model.Habit
import com.marvin.daka.model.HabitCategory
import com.marvin.daka.model.ReminderConfig
import com.marvin.daka.ui.common.ColorCatalog
import com.marvin.daka.ui.common.IconCatalog
import com.marvin.daka.ui.reminder.ReminderConfigEditor
import com.marvin.daka.ui.theme.DAKATheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 新建 / 编辑习惯页（M5 建立 → V3 提醒设置 → V4 图标推荐 + 全量选择器 + 分类 + 编辑模式）。
 *
 * 「编辑」和「新建」用**同一个页面**：
 * 两者 90% 的 UI 是一样的，拆成两个页面意味着每改一个功能要同步改两处，
 * 早晚有一天忘了改一边。复用的代价只是多传一个 [editing] 参数，划算。
 *
 * 图标 / 颜色选择器的布局规则（V5 按老陈的要求改过）：
 *   - 图标固定 2 行 × 6 列，不滚动；最后一格是「更多」（V4 是 3 行）
 *   - 颜色固定 1 行 × 6 列，不滚动；最后一格是「更多」（V4 是 2 行）
 *   - 「更多」弹出全量选择的对话框
 * 铺开的 11 个图标 / 5 个颜色是精选的常用项（见 IconCatalog.FEATURED 的前几项），
 * 冷门项收进二级对话框。行数砍掉是为了让「名称 + 备注 + 分类 + 图标 + 颜色」
 * 一屏能多看一点，减少滚动。
 *
 * 状态管理用的是最朴素的 `by remember { mutableStateOf(...) }`：
 * 这些值**只在这一个页面内用**，不跨页面、不需要存活到转屏之后，
 * 所以没必要塞进 ViewModel。简单问题用简单办法——
 * 把所有状态都往 ViewModel 里堆，是新手学完 ViewModel 后最容易犯的过度设计。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateHabitScreen(
    /** 传非 null = 编辑模式：预填这个习惯的全部设置，保存走 onUpdate */
    editing: Habit? = null,
    /** 编辑模式下该习惯的附加提醒（reminders 表里的）。新建模式忽略 */
    editingExtras: List<ReminderConfig> = emptyList(),
    defaultReminderTime: Pair<Int, Int> = 21 to 0,
    onSave: (
        name: String,
        emoji: String,
        colorArgb: Long,
        reminders: List<ReminderConfig>,
        category: String,
        note: String
    ) -> Unit = { _, _, _, _, _, _ -> },
    onUpdate: (
        habitId: Long,
        name: String,
        emoji: String,
        colorArgb: Long,
        reminders: List<ReminderConfig>,
        category: String,
        note: String
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onBack: () -> Unit,
    /** V1.3：打开模板库。编辑模式下不会用到（习惯已经存在了，没得从模板挑） */
    onOpenTemplates: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isEditing = editing != null

    var name by remember { mutableStateOf(editing?.name ?: "") }
    var selectedEmoji by remember {
        mutableStateOf(editing?.emoji ?: IconCatalog.FEATURED.first().emoji)
    }
    var selectedColor by remember {
        mutableLongStateOf(editing?.colorArgb ?: ColorCatalog.FEATURED.first().argb)
    }
    // V4.11：分类开放自建。
    // 选内置 chip 走 selectedBuiltin；输入自定义分类走 customText。
    // 最终分类 = 有自定义输入就用自定义，否则用选中的内置（category 计算属性）。
    var selectedBuiltin by remember {
        mutableStateOf(
            if (editing != null && editing.category in HabitCategory.ALL) editing.category
            else HabitCategory.DEFAULT
        )
    }
    var customText by remember {
        mutableStateOf(
            if (editing != null && editing.category !in HabitCategory.ALL) editing.category else ""
        )
    }
    // 最终分类 = 有自定义输入就用自定义，否则用选中的内置（读到了两个 state，重组自动取最新值）
    val category = customText.trim().ifBlank { selectedBuiltin }
    // V5：备注。用户随便写什么都可以，不做任何解析、不做长度限制（只存不展示）。
    // 编辑模式预填已有备注；新建模式从草稿恢复（见下面 draftRestore 的 LaunchedEffect）。
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }

    var reminder by remember {
        mutableStateOf(
            if (editing != null) {
                ReminderConfig.from(editing)
            } else {
                ReminderConfig.disabled(
                    hour = defaultReminderTime.first,
                    minute = defaultReminderTime.second
                )
            }
        )
    }

    // ---- 多提醒（V1.3.8）：主提醒之外可以再加几条 ----
    // 编辑模式以 habitId 为 key 快照一次：reminders Flow 和 habits Flow 同库同源，
    // editing 非 null 时它几乎必然已就绪；此后增删只改本地状态，保存时整体提交。
    var extraReminders by remember(editing?.id) { mutableStateOf(editingExtras) }
    // 附加提醒编辑弹窗：null = 不弹；-1 = 新增；>=0 = 编辑第 index 条
    var extraEditorIndex by remember { mutableStateOf<Int?>(null) }

    // V5：草稿的读写入口。只在**新建模式**用——编辑模式改的是已经存在的习惯，
    // 不存在「草稿」这回事，也绝不能让编辑页把用户另一份未提交的草稿冲掉。
    val context = LocalContext.current
    val appPrefs = remember(context) { AppPrefs(context.applicationContext) }

    // 「图标跟着名字自动选」：名字一变就重新推荐——但只在用户**还没手动挑过**图标时。
    // 一旦用户自己点了某个图标，就闭嘴不再自动改（userPickedEmoji 锁住），
    // 否则用户刚选好 🎸，多打一个字图标又跳回 💧，体验极差。
    var userPickedEmoji by remember { mutableStateOf(isEditing) }
    LaunchedEffect(name) {
        if (!userPickedEmoji) selectedEmoji = IconCatalog.suggest(name)
    }

    // ------------------------------------------------------------------
    // V5：草稿 —— 「返回也自动保存不清空」
    //
    // 三个状态变量的分工：
    //   draftReady  —— 草稿已读完，自动保存才允许动手。
    //                  没有它的话，页面刚打开（name 还是空）就会先跑一次保存，
    //                  把上次存下的草稿清掉，然后才轮到恢复逻辑去读——读到空，白存。
    //   draftRestored —— 这份内容是恢复出来的（给用户一句提示 + 一个「清空」入口）
    //   submitted   —— 已经点了保存。防止那个还在 400ms 倒计时的自动保存
    //                  在清空草稿之后又写回一份，表现为「保存完下次进来内容还在」。
    // ------------------------------------------------------------------
    var draftReady by remember { mutableStateOf(isEditing) }
    var draftRestored by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    // 进页面先恢复草稿。
    // 注意 draftReady 的置位放在**最后一行**：必须等草稿读完、字段都填好了，
    // 才允许下面那段自动保存动手，否则「先清空、后恢复」的顺序一错，草稿就没了。
    LaunchedEffect(Unit) {
        if (!isEditing) {
            val draft = appPrefs.habitDraft.first()
            if (draft != null && (draft.name.isNotBlank() || draft.note.isNotBlank())) {
                name = draft.name
                note = draft.note
                selectedEmoji = draft.emoji
                selectedColor = draft.colorArgb
                if (draft.category in HabitCategory.ALL) {
                    selectedBuiltin = draft.category
                    customText = ""
                } else {
                    customText = draft.category
                    selectedBuiltin = HabitCategory.DEFAULT
                }
                // 恢复出来的图标是用户挑过的，别让「名字一变就自动换图标」把它覆盖掉
                userPickedEmoji = true
                draftRestored = true
            }
        }
        draftReady = true
    }

    // 输入一变就（停手 400ms 后）落盘。按返回退出也不会丢，下次进来自动恢复。
    // delay 兼作防抖：连打十个字只在停手后写一次文件，不会一个字写一次。
    LaunchedEffect(draftReady, name, note, selectedEmoji, selectedColor, category) {
        if (isEditing || !draftReady || submitted) return@LaunchedEffect
        delay(DRAFT_SAVE_DEBOUNCE_MS)
        if (name.isBlank() && note.isBlank()) {
            // 名字和备注都空了 = 用户不想留了，清掉，别下次进来又冒出来
            appPrefs.clearHabitDraft()
        } else {
            appPrefs.setHabitDraft(
                AppPrefs.HabitDraft(
                    name = name,
                    note = note,
                    emoji = selectedEmoji,
                    colorArgb = selectedColor,
                    category = category
                )
            )
        }
    }

    /** 手动清空：把页面恢复成全新状态，自动保存随后会把草稿清掉 */
    fun clearEverything() {
        name = ""
        note = ""
        customText = ""
        selectedBuiltin = HabitCategory.DEFAULT
        selectedEmoji = IconCatalog.FEATURED.first().emoji
        selectedColor = ColorCatalog.FEATURED.first().argb
        userPickedEmoji = false
        draftRestored = false
    }

    // 二级对话框的开关状态
    var showIconDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEditing) R.string.create_title_edit else R.string.create_title_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            // AutoMirrored 版本：在 RTL（阿拉伯语等从右往左）布局下
                            // 箭头会自动翻转指向右边。普通版在 RTL 下是「指错方向」的，
                            // 官方已把旧的标记为废弃
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 内容变长了（提醒设置 + 分类），必须能滚，否则底部「保存」按钮点不到
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ---- V1.3：模板库入口 ----
            // 放在最上面而不是埋在底部：用户点「+」进来的第一秒就该知道
            // 「不用自己想，有现成的」。编辑模式下不显示——那时候习惯已经存在了。
            if (!isEditing) {
                OutlinedButton(
                    onClick = onOpenTemplates,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.tpl_entry))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ---- 名称 ----
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.create_name_label)) },
                placeholder = { Text(stringResource(R.string.create_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ---- V5：备注 ----
            // 紧贴名称放在最上面：两个都是「打字」的活儿，挨着填不用滚。
            // 多行（minLines 而不是 maxLines）——minLines 保证空着也有三行高，
            // 写着写着往下长；maxLines 只限制上限，不保证初始高度，会把布局压扁。
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.create_note_label)) },
                placeholder = { Text(stringResource(R.string.create_note_placeholder)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            // 内容是恢复出来的草稿时给个说明 + 一键清空。
            // 不给提示的话，用户打开「+」看到一堆已经填好的内容会以为是 bug。
            if (!isEditing && draftRestored) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.create_draft_restored),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = ::clearEverything) {
                        Text(stringResource(R.string.create_draft_clear))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- V4.11：分类（内置 chip + 自定义输入框） ----
            Text(
                text = stringResource(R.string.create_category),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HabitCategory.ALL.forEach { cat ->
                    FilterChip(
                        // 只有没在输自定义分类时，内置 chip 才高亮
                        selected = customText.isEmpty() && selectedBuiltin == cat,
                        onClick = {
                            selectedBuiltin = cat
                            customText = ""
                        },
                        label = { Text(cat) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                label = { Text(stringResource(R.string.create_custom_category)) },
                placeholder = { Text(stringResource(R.string.create_custom_category_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // 输入了自定义分类时给个明确反馈：提交即建该分类
            if (customText.trim().isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.create_new_category, customText.trim()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- 图标（2 行 × 6 列，不滚动，最后一格「更多」） ----
            Text(
                text = stringResource(R.string.create_icon),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            IconGrid(
                selected = selectedEmoji,
                onSelect = {
                    userPickedEmoji = true
                    selectedEmoji = it
                },
                onMore = { showIconDialog = true }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ---- 主题色（1 行 × 6 列，不滚动，最后一格「更多」） ----
            Text(
                text = stringResource(R.string.create_color),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            ColorGrid(
                selected = selectedColor,
                onSelect = { selectedColor = it },
                onMore = { showColorDialog = true }
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // ---- 提醒设置（复用设置页同一套编辑器） ----
            ReminderConfigEditor(
                config = reminder,
                onChange = { reminder = it }
            )

            // ---- 附加提醒（V1.3.8 多提醒）----
            // 主提醒在上面整块编辑；这里只做「列表 + 增删改弹窗」，
            // 弹窗复用 ReminderEditorDialog（和设置页同一套），不重复造编辑器
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.reminder_extra_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (extraReminders.isEmpty()) {
                Text(
                    text = stringResource(R.string.reminder_extra_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                extraReminders.forEachIndexed { index, extra ->
                    ExtraReminderRow(
                        config = extra,
                        onEdit = { extraEditorIndex = index },
                        onDelete = {
                            extraReminders = extraReminders.toMutableList().apply { removeAt(index) }
                        }
                    )
                    if (index < extraReminders.lastIndex) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = { extraEditorIndex = -1 }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.reminder_extra_add))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ---- 保存 ----
            Button(
                onClick = {
                    // 保存成功：清脆反馈，不喧宾夺主
                    SoundEffectPlayer.play(SoundEffectPlayer.Effect.DakaOk)
                    // 先立 flag，挡住那个还在 400ms 倒计时的草稿自动保存——
                    // 否则它会紧接着把刚清掉的草稿又写回去
                    submitted = true
                    // 主提醒 + 附加提醒一起提交，列表第一项会被当主提醒
                    val allReminders = listOf(reminder) + extraReminders
                    if (isEditing) {
                        onUpdate(
                            editing.id, name, selectedEmoji, selectedColor,
                            allReminders, category, note
                        )
                    } else {
                        onSave(name, selectedEmoji, selectedColor, allReminders, category, note)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (isEditing) R.string.create_save_edit else R.string.create_save_new))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ---- 附加提醒编辑弹窗（新增 / 编辑共用） ----
    extraEditorIndex?.let { index ->
        ReminderEditorDialog(
            habitName = name.ifBlank { stringResource(R.string.reminder_extra_add) },
            initial = if (index >= 0) {
                extraReminders[index]
            } else {
                // 新增：时间默认沿用主提醒的，用户多半想让几条提醒挨着响
                ReminderConfig.disabled(hour = reminder.hour, minute = reminder.minute)
            },
            onConfirm = { config ->
                if (index >= 0) {
                    extraReminders = extraReminders.toMutableList().apply { set(index, config) }
                } else {
                    extraReminders = extraReminders + config
                }
                extraEditorIndex = null
            },
            onDismiss = { extraEditorIndex = null }
        )
    }

    // ---- 更多图标对话框 ----
    if (showIconDialog) {
        PickerDialog(
            title = stringResource(R.string.picker_icons),
            onDismiss = { showIconDialog = false }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.height(420.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = IconCatalog.ALL, key = { it.emoji }) { item ->
                    IconCell(
                        emoji = item.emoji,
                        label = item.label,
                        selected = item.emoji == selectedEmoji,
                        onSelect = {
                            userPickedEmoji = true
                            selectedEmoji = item.emoji
                            showIconDialog = false
                        }
                    )
                }
            }
        }
    }

    // ---- 更多颜色对话框 ----
    if (showColorDialog) {
        PickerDialog(
            title = stringResource(R.string.picker_colors),
            onDismiss = { showColorDialog = false }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.height(420.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = ColorCatalog.ALL, key = { it.argb }) { item ->
                    ColorCell(
                        colorArgb = item.argb,
                        label = item.label,
                        selected = item.argb == selectedColor,
                        onSelect = {
                            selectedColor = item.argb
                            showColorDialog = false
                        }
                    )
                }
            }
        }
    }
}

/** 全量选择对话框的骨架：标题 + 内容 + 关闭按钮 */
@Composable
private fun PickerDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { content() },
            confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

// ------------------------------------------------------------------
// 图标网格：2 行 × 6 列，第 12 格是「更多」
// ------------------------------------------------------------------

/**
 * 草稿自动保存的防抖时长。
 *
 * 停手 400ms 再落盘：连打十个字只写一次文件，而不是每个字写一次。
 * DataStore 每次写都要过一遍文件事务，打字时高频触发纯属浪费。
 */
private const val DRAFT_SAVE_DEBOUNCE_MS = 400L

/** 图标网格每行几个。6 × 44dp + 5 × 8dp 间距 = 304dp，最窄的屏也放得下 */
private const val GRID_COLUMNS = 6

/**
 * 页面上直接铺开的图标行数。
 * V5：3 行 → 2 行（11 个图标 + 1 个「更多」）。腾出来的高度给备注输入框。
 */
private const val GRID_ICON_ROWS = 2

/**
 * 颜色网格行数。
 * V5：2 行 → 1 行（5 个颜色 + 1 个「更多」）。
 * 最常用的就前五个，剩下的点「更多」也就一步的事。
 */
private const val GRID_COLOR_ROWS = 1

@Composable
private fun IconGrid(
    selected: String,
    onSelect: (String) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 总格数 = 行数 × 每行列数。最后一格（右下角）固定是「更多」入口
    val totalSlots = GRID_COLUMNS * GRID_ICON_ROWS
    val shown = IconCatalog.FEATURED.take(totalSlots - 1)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 按位置铺格子：位置 < shown.size 放图标，== 最后一格放「更多」，其余补空
        (0 until totalSlots).chunked(GRID_COLUMNS).forEach { positions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                positions.forEach { position ->
                    when {
                        position < shown.size -> {
                            val item = shown[position]
                            IconCell(
                                emoji = item.emoji,
                                label = item.label,
                                selected = item.emoji == selected,
                                onSelect = { onSelect(item.emoji) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        position == totalSlots - 1 -> {
                            MoreCell(
                                label = stringResource(R.string.create_more_icons),
                                onClick = onMore,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        else -> Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 单个图标格子。选中 = 主色底托 + 描边 */
@Composable
private fun IconCell(
    emoji: String,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .aspectRatioCell()
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onSelect
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}

/** 「更多」入口：虚位格子样式 + 加号图标，一眼知道点开还有货 */
@Composable
private fun MoreCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .aspectRatioCell()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.create_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 网格格子的统一尺寸：按 6 列布局算出的宽高比近似 1:1。
 * 用固定高度而不是 size(44.dp)，是为了配合 Row 里的 weight(1f)：
 * 格子宽度随屏幕伸缩，高度跟着走，不同分辨率下都不会挤变形。
 */
private fun Modifier.aspectRatioCell(): Modifier =
    this.then(Modifier.height(48.dp))

// ------------------------------------------------------------------
// 颜色网格：1 行 × 6 列，第 6 格是「更多」
// ------------------------------------------------------------------

@Composable
private fun ColorGrid(
    selected: Long,
    onSelect: (Long) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1 行 × 6 列 = 6 格，最后一格（右下角）固定是「更多」
    val totalSlots = GRID_COLUMNS * GRID_COLOR_ROWS
    val shown = ColorCatalog.FEATURED.take(totalSlots - 1)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until totalSlots).chunked(GRID_COLUMNS).forEach { positions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                positions.forEach { position ->
                    when {
                        position < shown.size -> {
                            val item = shown[position]
                            ColorCell(
                                colorArgb = item.argb,
                                label = item.label,
                                selected = item.argb == selected,
                                onSelect = { onSelect(item.argb) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        position == totalSlots - 1 -> {
                            MoreCell(
                                label = stringResource(R.string.create_more_colors),
                                onClick = onMore,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        else -> Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 单个颜色圆块。
 *
 * ⚠️ **这里踩过的坑（V3 修的 bug）**：
 * 之前这些色块只是「画」出了颜色，一个可以点击的修饰符都没加——
 * 用户怎么点都选不中，颜色永远是默认的紫色。
 *
 * 教训：Compose 里**没有任何东西是默认可点的**。
 * `Modifier.background()` 只负责画，不负责交互；
 * 想让一块区域响应点击，必须显式挂 `clickable` / `combinedClickable` / `selectable`，
 * 或者用自带 onClick 的 Material 组件（如 Surface / Button）。
 * 视觉效果做得再像按钮，没挂 clickable 就只是张图。
 */
@Composable
private fun ColorCell(
    colorArgb: Long,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatioCell()
            .clip(CircleShape)
            .background(Color(colorArgb))
            .then(
                if (selected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            // 关键：让色块真的能点
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onSelect
            )
            .semantics {
                role = Role.RadioButton
                contentDescription = label
            },
        contentAlignment = Alignment.Center
    ) {
        // 选中态：中间一个白点。光靠外圈描边在深色主题下不够醒目
        if (selected) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CreateHabitScreenPreview() {
    DAKATheme {
        CreateHabitScreen(onBack = {})
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CreateHabitScreenDarkPreview() {
    DAKATheme {
        CreateHabitScreen(onBack = {})
    }
}


/**
 * 附加提醒的一行摘要：时间 + 规则描述 + 移除。
 *
 * 规则描述复用 [ReminderRule.describe]（它吃 ReminderLike），
 * 把 ReminderConfig 临时转成 Reminder 即可——不为「给人看的描述」再造一套逻辑。
 */
@Composable
private fun ExtraReminderRow(
    config: ReminderConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onEdit,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "%02d:%02d".format(config.hour, config.minute),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = ReminderRule.describe(config.toReminder(habitId = 0)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.reminder_extra_delete)
                )
            }
        }
    }
}
