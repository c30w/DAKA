package com.marvin.daka.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 无极取色器（HSV 模型）。
 *
 * 上半部分是「饱和度 × 明度」二维面板：横向 = 饱和度（左白 → 右纯色），
 * 纵向 = 明度（上亮 → 下黑）；底色随下方色相条选中的色相实时变化。
 * 下半部分是彩虹色相条，左右拖动选色相。
 * 任意位置拖动都是「所见即所得」——选中的颜色通过 [onColorChanged] 实时抛出去。
 *
 * 为什么用 HSV 而不是 RGB 滑块？
 * 人挑颜色是「先选色相（红/蓝/绿），再调浓淡」，HSV 正好对应这个直觉，
 * 而且色相条能把整条彩虹铺开，比三个 0-255 数字好用太多。
 *
 * @param initialColor   打开时当前的强调色（初始位置对准它）
 * @param onColorChanged 颜色变化回调，参数是 Compose 的 [Color]
 */
@Composable
fun HsvColorPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    // Compose 的 Color 没有现成 HSV 构造/拆解，借 Android 框架的 HSV 工具做 ARGB <-> HSV 互转。
    // 用 FloatArray 同时装 (hue, sat, value)，作为状态本体。
    var hsv by remember {
        mutableStateOf(
            FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
        )
    }
    val hue = hsv[0]
    val sat = hsv[1]
    val value = hsv[2]

    // 颜色一变就抛给上层；上层一般写进 DataStore，主题即时重组（拖动即所见即所得）
    LaunchedEffect(hue, sat, value) {
        onColorChanged(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))))
    }

    Column(modifier = modifier) {
        // ---- 饱和度 / 明度 面板 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val x = (change.position.x / size.width).coerceIn(0f, 1f)
                            val y = (change.position.y / size.height).coerceIn(0f, 1f)
                            // 横向 = 饱和度；纵向反着 = 明度（上亮下黑）
                            hsv = floatArrayOf(hue, x, 1f - y)
                        }
                    }
            ) {
                val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                // 1) 纯色相底
                drawRect(hueColor)
                // 2) 横向：左白 → 右透明（叠出饱和度）
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                // 3) 纵向：上透明 → 下黑（叠出明度）
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                // 指示点：当前 (sat, value) 落点，白圈 + 黑圈描边让它在任何底色上都看得清
                val px = sat * size.width
                val py = (1f - value) * size.height
                drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(px, py), style = Stroke(width = 3.dp.toPx()))
                drawCircle(color = Color.Black, radius = 9.dp.toPx(), center = Offset(px, py), style = Stroke(width = 1.dp.toPx()))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- 色相条 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val x = (change.position.x / size.width).coerceIn(0f, 1f)
                            hsv = floatArrayOf(x * 360f, sat, value)
                        }
                    }
            ) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                            Color.Blue, Color.Magenta, Color.Red
                        )
                    )
                )
                // 当前色相位置：一条白色竖线指示
                val px = (hue / 360f) * size.width
                drawRect(
                    color = Color.White,
                    topLeft = Offset(px - 2.dp.toPx(), 0f),
                    size = Size(4.dp.toPx(), size.height),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
