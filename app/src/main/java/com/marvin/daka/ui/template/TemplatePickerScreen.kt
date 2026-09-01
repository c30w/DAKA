package com.marvin.daka.ui.template

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marvin.daka.R

/**
 * 模板选择页 —— 勾几个模板，一键导入成习惯。
 *
 * 交互上刻意做得「像逛清单而不是像填表」：默认一个都没勾，
 * 用户扫一眼、想要哪个点哪个，最后按一下导入。
 * 不做「点一个立刻导入并关闭」——批量导入是这个功能的核心价值，
 * 一次只能加一个的话，跟手动新建没区别。
 *
 * @param onImport 回调已勾选的模板（已按分类分组顺序），由调用方负责写库
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerScreen(
    onImport: (List<HabitTemplate>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 勾选状态用 index 而不是模板对象：模板是 data class，
    // 同名模板（理论上不会有）也会 equals 相等，用下标最稳。
    val checked = remember { mutableStateListOf<Int>() }
    val groups = remember { HabitTemplates.groupByCategory() }

    // 扁平顺序要和 HabitTemplates.ALL 完全一致，才能用下标反查模板
    val flat = remember { HabitTemplates.ALL }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tpl_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    // 全选 / 全不选：12 个模板全勾一遍也不过两下，但要留个快捷方式
                    TextButton(
                        onClick = {
                            if (checked.size == flat.size) checked.clear()
                            else {
                                checked.clear()
                                checked.addAll(flat.indices)
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                if (checked.size == flat.size) R.string.tpl_none else R.string.tpl_all
                            )
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tpl_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        enabled = checked.isNotEmpty(),
                        onClick = { onImport(checked.map { flat[it] }) }
                    ) {
                        Text(stringResource(R.string.tpl_import_count, checked.size))
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            groups.forEach { (category, templates) ->
                // 分类小标题。分组是为了让 12 个模板不显得像一堵墙，
                // 用户按自己关心的类目扫就行
                item(key = "cat:$category") {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(
                    count = templates.size,
                    key = { index -> "tpl:${templates[index].nameRes}" }
                ) { indexInGroup ->
                    val template = templates[indexInGroup]
                    val globalIndex = flat.indexOf(template)
                    TemplateRow(
                        template = template,
                        checked = globalIndex in checked,
                        onToggle = {
                            if (globalIndex in checked) checked.remove(globalIndex)
                            else checked.add(globalIndex)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/**
 * 一个模板卡片：圆形 emoji 底托 + 名称 + 描述 + 建议提醒时间 + 勾选框。
 *
 * 整行可点（不只是勾选框）——12 个模板逐个瞄准小方块太累。
 */
@Composable
private fun TemplateRow(
    template: HabitTemplate,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor =
        if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = if (checked) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(onClick = onToggle),
        shape = shape,
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // emoji 底托用模板自带的主题色：用户挑的时候就能看出加进去长什么样
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(template.colorArgb).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = template.emoji, style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(template.nameRes),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(template.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    // 只展示建议时间，不真的开提醒——提醒开关始终由用户自己决定
                    text = stringResource(
                        R.string.tpl_suggest_remind,
                        formatTime(template.suggestHour, template.suggestMinute)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

/** 7:5 → "07:05"。模板时间都是整点或半点，补零只为对齐好看 */
private fun formatTime(hour: Int, minute: Int): String =
    "%02d:%02d".format(hour, minute)
