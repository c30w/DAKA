package com.marvin.daka.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.marvin.daka.R
import com.marvin.daka.model.HabitUi
import com.marvin.daka.ui.theme.DAKATheme

/**
 * 习惯列表里的单行卡片。
 *
 * 结构：Surface → Row → [图标 | 文字+7天格子(权重撑开) | 勾选圈]
 *
 * 各里程碑的演进：
 *   M1 画出来 → M2 整张可点击 → M3 换 HabitUi → M5 用上主题色 → M6 加 7 天格子
 *   → V4 置顶图钉 → V4.1 尾部拖动手柄插槽 → V4.2 手柄退场（长按卡片即拖动）
 *
 * V4.2 的两个变化：
 *   - **置顶卡片有专属色系**：主题色描边 + 淡淡的主题色底，和普通卡一眼区分；
 *   - **无障碍自定义动作**：读屏用户不用长按拖拽（那个手势对读屏不友好），
 *     直接在无障碍菜单里就能置顶/编辑/移动/删除，动作列表由调用方传进来。
 *
 * 注意这里**没有** var done 之类的内部状态：
 * 卡片自己不记「我有没有打卡」，只负责「长什么样」和「被点了通知谁」。
 * 真正的状态在 ViewModel 里，通过 habit 传进来、通过 onToggle 报出去。
 * 这个套路叫**状态提升（state hoisting）**——顶部进度条要知道今天完成几项，
 * 状态必须放在两处都能看到的地方。
 *
 * @param a11yActions 无障碍自定义动作列表（动作名 to 执行回调）。
 *   只进无障碍服务的行为菜单，不影响视觉；传空列表就没有额外动作。
 */
@Composable
fun HabitCard(
    habit: HabitUi,
    onToggle: () -> Unit,
    a11yActions: List<Pair<String, () -> Unit>> = emptyList(),
    modifier: Modifier = Modifier
) {
    // 读屏状态描述（semantics 块不是 Composable 上下文，先在这里拼好）
    val stateDesc = buildString {
        append(if (habit.doneToday) stringResource(R.string.card_done) else stringResource(R.string.card_not_done))
        if (habit.pinned) append(stringResource(R.string.card_pinned))
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // V4.2：短按打卡。长按不再是操作面板（长按归拖动手势管了），
            // 面板改由拖拽状态机在「长按后原地松手」时触发。
            .combinedClickable(
                onClick = onToggle,
                indication = ripple(),
                interactionSource = remember { MutableInteractionSource() }
            )
            // 告诉读屏这是个复选框，并读出状态；否则视障用户只听到名字，不知道勾没勾
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                stateDescription = stateDesc
                // 读屏行为菜单：拖拽和滑动手势对视障用户不可用，动作全走这里
                customActions = a11yActions.map { (label, action) ->
                    CustomAccessibilityAction(label) { action(); true }
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = if (habit.pinned) {
            // 置顶色系：主题色淡淡的底，比普通卡「亮」一档，一眼能认出置顶区
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (habit.pinned) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HabitEmojiBadge(emoji = habit.emoji, colorArgb = habit.colorArgb)

            Spacer(modifier = Modifier.width(12.dp))

            // weight(1f) = 吃掉 Row 剩余空间，把右边勾选圈顶到末尾
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // 置顶标记：小图钉，跟在名字后面，不抢打卡状态的视觉焦点
                    if (habit.pinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "📌",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (habit.streak > 0) stringResource(R.string.card_streak, habit.streak) else stringResource(R.string.card_no_streak),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                // M6：最近 7 天小格子，用习惯自己的主题色填充
                WeekStrip(last7 = habit.last7, colorArgb = habit.colorArgb)

                Spacer(modifier = Modifier.height(6.dp))
                // V1.2：每张卡自带一行「侧滑能干什么」。
                // 底部那条全局提示容易滚出视野，而滑动手势看不见就永远学不会——
                // 提示跟着卡片走，抬手就能看到。做得很淡（50% 透明度 + 11sp），
                // 不抢打卡状态和 7 天格子的视觉焦点。
                Text(
                    text = stringResource(R.string.card_swipe_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            HabitCheckMark(done = habit.doneToday)
        }
    }
}

/**
 * 左侧 emoji 底托：40dp 圆形。
 *
 * M5 起底色改用习惯自己的主题色，而不是统一的 secondaryContainer，
 * 这样一眼就能靠颜色分辨习惯。
 */
@Composable
private fun HabitEmojiBadge(emoji: String, colorArgb: Long) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(colorArgb).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * 右侧勾选标记：已完成=实心圆+对勾，未完成=空心描边圆。
 *
 * clearAndSetSemantics { } = 声明「我是纯装饰，读屏别念我」。
 * 打卡状态已由外层 stateDescription 播报，否则读屏会多念一个「对勾」。
 */
@Composable
private fun HabitCheckMark(done: Boolean) {
    if (done) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HabitCardDonePreview() {
    DAKATheme {
        HabitCard(
            habit = HabitUi(
                id = 1,
                name = "喝水 2L",
                emoji = "💧",
                colorArgb = 0xFF2196F3,
                streak = 12,
                doneToday = true,
                last7 = listOf(true, true, false, true, true, true, true)
            ),
            onToggle = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HabitCardUndonePreview() {
    DAKATheme {
        HabitCard(
            habit = HabitUi(
                id = 2,
                name = "跑步 3 公里",
                emoji = "🏃",
                colorArgb = 0xFFE91E63,
                streak = 0,
                doneToday = false,
                last7 = listOf(false, false, true, false, false, false, false)
            ),
            onToggle = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HabitCardPinnedPreview() {
    DAKATheme {
        HabitCard(
            habit = HabitUi(
                id = 3,
                name = "23 点前睡觉",
                emoji = "💤",
                colorArgb = 0xFFFF9800,
                streak = 5,
                doneToday = false,
                pinned = true,
                last7 = listOf(true, false, false, true, true, false, false)
            ),
            onToggle = {}
        )
    }
}
