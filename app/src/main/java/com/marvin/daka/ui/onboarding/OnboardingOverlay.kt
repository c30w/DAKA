package com.marvin.daka.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marvin.daka.R

/**
 * 新手引导（V1.1 新增）。
 *
 * 三步横滑式引导：欢迎 → 左右滑操作 → 添加习惯。
 * 最后一步提供「不再显示」勾选 + 「关闭」按钮：
 *   - 勾选了「不再显示」再点关闭 → 以后不再弹出（onDismiss 传 true）
 *   - 没勾选就关闭 → 只关掉这一次，下次启动还会弹出（onDismiss 传 false）
 *
 * @param onDismiss 关闭引导。参数 = 是否把「不再显示」记进偏好。
 */
@Composable
fun OnboardingOverlay(
    onDismiss: (markDone: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    data class Step(val emoji: String, val title: Int, val body: Int)

    val steps = listOf(
        Step("👋", R.string.onboarding_step1_title, R.string.onboarding_step1_body),
        Step("👉", R.string.onboarding_step2_title, R.string.onboarding_step2_body),
        Step("➕", R.string.onboarding_step3_title, R.string.onboarding_step3_body),
    )
    val lastIndex = steps.lastIndex

    var page by remember { mutableIntStateOf(0) }
    // 默认勾选「不再显示」：大多数用户不想每次启动都被打扰
    var dontShowAgain by remember { mutableStateOf(true) }

    val isLast = page == lastIndex

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.6f))

            // 大 emoji，纯装饰
            Text(
                text = steps[page].emoji,
                style = MaterialTheme.typography.displayMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(steps[page].title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(steps[page].body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            // 步骤指示点
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == page) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == page) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isLast) {
                // 最后一步：不再显示 + 关闭
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Text(
                        text = stringResource(R.string.onboarding_dont_show),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onDismiss(dontShowAgain) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.onboarding_close))
                }
            } else {
                // 前面的步骤：下一步 + 右上角跳过（跳到最后一步做决定）
                TextButton(onClick = { page = lastIndex }) {
                    Text(stringResource(R.string.onboarding_skip))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { page = (page + 1).coerceAtMost(lastIndex) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.onboarding_next))
                }
            }
        }
    }
}
