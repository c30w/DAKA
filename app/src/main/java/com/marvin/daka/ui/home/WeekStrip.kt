package com.marvin.daka.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.marvin.daka.util.last7WeekdayLabels

/**
 * 最近 7 天的小格子（GitHub 贡献图那种）。
 *
 * @param last7    长度 7 的 Boolean 列表，下标 0 = 6 天前，下标 6 = 今天（从左到右、从旧到新）
 * @param colorArgb 打卡日的填充色，用这个习惯自己的主题色
 *
 * 设计细节：
 * - 今天那格画一圈描边（并且略大一点），让用户一眼找到「今天在哪」
 * - 没打卡的格子用 surfaceContainerHighest（主题里最淡的灰），深浅模式自动适配
 * - 整排加了 contentDescription，读屏时一次念完「最近 7 天：周一 已打卡…」，
 *   而不是逐个格子念 7 遍——这是无障碍里「合并语义」的典型用法
 */
@Composable
fun WeekStrip(
    last7: List<Boolean>,
    colorArgb: Long,
    modifier: Modifier = Modifier
) {
    val weekdays = last7WeekdayLabels()

    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            val desc = last7.mapIndexed { i, done ->
                "${weekdays.getOrNull(i) ?: ""}${if (done) "已打卡" else "未打卡"}"
            }.joinToString("、")
            contentDescription = "最近 7 天：$desc"
        },
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        last7.forEachIndexed { index, done ->
            val isToday = index == last7.lastIndex
            val size = if (isToday) 14.dp else 12.dp

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        if (done) {
                            Color(colorArgb)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
                    .then(
                        // 今天：套一圈描边，方便定位
                        if (isToday) {
                            Modifier.border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = CircleShape
                            )
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}
