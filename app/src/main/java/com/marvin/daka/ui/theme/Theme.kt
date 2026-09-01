package com.marvin.daka.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 主题模式的三种取值。存进 DataStore 的就是这三个字符串。
 *
 * 为什么把字符串常量放这里而不是 data 层？
 * AppPrefs 只负责「存一个字符串、读一个字符串」，不该知道界面有几种主题；
 * 而界面（设置页、MainActivity）既要显示文案又要落库，常量放 UI 层正合适。
 */
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
 */
@Composable
fun DAKATheme(
    themeMode: String = ThemeMode.DEVICE,
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

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
