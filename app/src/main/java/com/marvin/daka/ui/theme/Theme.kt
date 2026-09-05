package com.marvin.daka.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/**
 * 主题模式的三种取值。存进 DataStore 的就是这三个字符串。
 *
 * 为什么把字符串常量放这里而不是 data 层？
 * AppPrefs 只负责「存一个字符串、读一个字符串」，不该知道界面有几种主题；
 * 而界面（设置页、MainActivity）既要显示文案又要落库，常量放 UI 层正合适。
 */
// ---------------- #9 外观自定义：常量与色板 ----------------

/** 默认强调色：薄荷青绿（与 v1.2 品牌色一致）。DataStore 里没存过时用它 */
const val DEFAULT_ACCENT_COLOR = 0xFF0F8A7CL

/**
 * 可选的强调色预设（ARGB）。首项即默认品牌色。
 * 无极取色器（HsvColorPicker）是主入口，这 8 个作为「快捷预设」放在取色弹窗里，
 * 手懒不想调时一键选——既有「任意颜色」的自由，又有「一键到位」的省事。
 */
val AccentPalette: List<Long> = listOf(
    0xFF0F8A7C, // 薄荷青绿（默认）
    0xFF2E6E8E, // 湖蓝
    0xFF4C5FBF, // 靛蓝
    0xFF7A4FA8, // 紫罗兰
    0xFFB0366B, // 玫红
    0xFFC25E3A, // 珊瑚橙
    0xFF9C6F19, // 琥珀
    0xFF5A6468  // 石墨
)

/**
 * 浅色模式：用强调色重染 primary 系（primary/onPrimary/容器色）。
 * 关系近似 M3 的 tonal palette：容器色 = 主色调淡，容器上的文字 = 主色调深。
 */
private fun ColorScheme.withAccentLight(accent: Color): ColorScheme = copy(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.25f).compositeOver(Color.White),
    onPrimaryContainer = lerp(accent, Color.Black, 0.55f)
)

/** 深色模式：主色提亮（深底上深色主色会糊），容器色压暗、文字提亮 */
private fun ColorScheme.withAccentDark(accent: Color): ColorScheme = copy(
    primary = lerp(accent, Color.White, 0.5f),
    onPrimary = lerp(accent, Color.Black, 0.75f),
    primaryContainer = lerp(accent, Color.Black, 0.65f),
    onPrimaryContainer = lerp(accent, Color.White, 0.75f)
)

object ThemeMode {
    /** 跟随系统（Android 10+ 的「深色模式」开关） */
    const val DEVICE = "device"

    /** 始终浅色 */
    const val LIGHT = "light"

    /** 始终深色 */
    const val DARK = "dark"

    /** 去掉脏数据：任何不认识的值都按「跟随系统」处理 */
    fun normalize(value: String?): String = when (value) {
        LIGHT, DARK, DEVICE -> value
        else -> DEVICE
    }

    /** 这个模式在给定系统深色状态下，最终是不是深色 */
    fun isDark(mode: String, systemDark: Boolean): Boolean = when (normalize(mode)) {
        LIGHT -> false
        DARK -> true
        else -> systemDark
    }
}

private val LightColorScheme = lightColorScheme(
    primary = MintPrimaryLight,
    onPrimary = OnMintPrimaryLight,
    primaryContainer = MintPrimaryContainerLight,
    onPrimaryContainer = OnMintPrimaryContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = OnSlateSecondaryLight,
    secondaryContainer = SlateSecondaryContainerLight,
    onSecondaryContainer = OnSlateSecondaryContainerLight,
    tertiary = LilacTertiaryLight,
    onTertiary = OnLilacTertiaryLight,
    tertiaryContainer = LilacTertiaryContainerLight,
    onTertiaryContainer = OnLilacTertiaryContainerLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = MintPrimaryDark,
    onPrimary = OnMintPrimaryDark,
    primaryContainer = MintPrimaryContainerDark,
    onPrimaryContainer = OnMintPrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = OnSlateSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = OnSlateSecondaryContainerDark,
    tertiary = LilacTertiaryDark,
    onTertiary = OnLilacTertiaryDark,
    tertiaryContainer = LilacTertiaryContainerDark,
    onTertiaryContainer = OnLilacTertiaryContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

/**
 * App 主题。
 *
 * @param themeMode [ThemeMode] 里的三种取值之一（默认跟随系统）
 * @param dynamicColor 是否用 Material You 动态取色。
 *   **默认关**：DAKA 的界面主色是习惯色板（40 种固定色），
 *   动态取色从壁纸抓来的颜色既不可控、又容易跟习惯色打架，
 *   还会让「清新青绿」这个品牌感消失。想要的话传 true 即可（仅 Android 12+ 生效）。
 *
 * #9 圆角风格档位已移除：圆角统一回归 Material 3 默认值（中等圆角），
 * 不再提供「方正 / 圆润」切换——卡片、按钮自身的局部圆角是设计语言的一部分，照旧保留。
 */
@Composable
fun DAKATheme(
    themeMode: String = ThemeMode.DEVICE,
    /** #9 强调色 ARGB。默认品牌薄荷青；设了别的颜色就重染 primary 系 */
    accentColor: Long = DEFAULT_ACCENT_COLOR,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = ThemeMode.isDark(themeMode, isSystemInDarkTheme())

    val colorScheme = when {
        // 动态取色（默认不走这条路，原因见上面的注释）
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        }

        darkTheme ->
            if (accentColor == DEFAULT_ACCENT_COLOR) DarkColorScheme
            else DarkColorScheme.withAccentDark(Color(accentColor))
        else ->
            if (accentColor == DEFAULT_ACCENT_COLOR) LightColorScheme
            else LightColorScheme.withAccentLight(Color(accentColor))
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
