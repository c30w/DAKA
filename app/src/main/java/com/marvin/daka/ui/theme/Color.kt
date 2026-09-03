package com.marvin.daka.ui.theme

import androidx.compose.ui.graphics.Color

// ------------------------------------------------------------------
// DAKA 的配色底座（V1.2 重做：清新薄荷青绿）
//
// 为什么换掉默认的 Material 紫色？
// DAKA 的界面里飘着**用户自选的 40 色习惯色板**（见 ColorCatalog）：
// 首页卡片圆托、7 天格子、小组件、日历条目全是它。
// 原来的紫色主题 + Material You 动态取色有两个毛病：
//   1. 紫底和紫 primary 会跟习惯里的「紫色/深紫色/亮紫色」撞车，谁也不突出；
//   2. 动态取色（Android 12+ 从壁纸抓色）抓到什么全看运气，
//      可能抓出个大红大绿，把习惯色全压下去。
// 所以现在走**低饱和中性底 + 薄荷青绿点缀**：
//   - 底色是接近白的淡青灰（浅色）/ 墨绿黑（深色），几乎不参与抢戏，
//     任何颜色的习惯卡片放上去都干净、都跳得出来；
//   - primary 用薄荷青绿（teal 系），它在 40 色板里没有正面冲突
//     （板子里最接近的是青色 #00BCD4 / 湖水绿 #26A69A，但那是习惯的标识色，
//       只在小圆点和格子上用，跟界面主色分工不同），
//     同时「青绿」天然带点「完成、清爽」的心理暗示，很适合打卡类 App。
// ------------------------------------------------------------------

// ---------------- 浅色 ----------------

/** 主色：薄荷青绿。按钮、进度条、置顶标记、完成态都用它 */
val MintPrimaryLight = Color(0xFF0F8A7C)
val OnMintPrimaryLight = Color(0xFFFFFFFF)
/** 主色容器：置顶卡片的淡底、选中态背景 */
val MintPrimaryContainerLight = Color(0xFFC4F2EA)
val OnMintPrimaryContainerLight = Color(0xFF00201C)

/** 辅助色：沉静蓝灰。次级按钮、次要强调 */
val SlateSecondaryLight = Color(0xFF4C6A78)
val OnSlateSecondaryLight = Color(0xFFFFFFFF)
val SlateSecondaryContainerLight = Color(0xFFD2E4ED)
val OnSlateSecondaryContainerLight = Color(0xFF0A1E28)

/** 第三色：柔和紫。只做少量点缀（日历、分组计数），面积一大就压过习惯色了 */
val LilacTertiaryLight = Color(0xFF6D6BA8)
val OnLilacTertiaryLight = Color(0xFFFFFFFF)
val LilacTertiaryContainerLight = Color(0xFFE4E1FF)
val OnLilacTertiaryContainerLight = Color(0xFF201F4B)

/** 底色：几乎白，只掺一丝青，让 40 色习惯卡放上去都不发灰 */
val SurfaceLight = Color(0xFFF3F8F7)
val OnSurfaceLight = Color(0xFF16201F)
val SurfaceVariantLight = Color(0xFFDCE5E3)
val OnSurfaceVariantLight = Color(0xFF41504E)

/** 卡片/容器的五档高度（M3 规范：越低越接近底色，越高越"浮起来"） */
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFEDF3F2)
val SurfaceContainerLight = Color(0xFFE7EEED)
val SurfaceContainerHighLight = Color(0xFFE1E8E7)
val SurfaceContainerHighestLight = Color(0xFFDBE3E1)

val OutlineLight = Color(0xFF6F7D7B)
val OutlineVariantLight = Color(0xFFBECBC9)

val InverseSurfaceLight = Color(0xFF2C3534)
val InverseOnSurfaceLight = Color(0xFFECF2F1)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// ---------------- 深色 ----------------

/** 深色下主色转淡（深底上不能用深色主色，会糊在一起） */
val MintPrimaryDark = Color(0xFF7BD9CB)
val OnMintPrimaryDark = Color(0xFF003730)
val MintPrimaryContainerDark = Color(0xFF005047)
val OnMintPrimaryContainerDark = Color(0xFF9CF0E3)

val SlateSecondaryDark = Color(0xFFB6CBDA)
val OnSlateSecondaryDark = Color(0xFF1F333F)
val SlateSecondaryContainerDark = Color(0xFF354A57)
val OnSlateSecondaryContainerDark = Color(0xFFD2E4ED)

val LilacTertiaryDark = Color(0xFFC6C2F5)
val OnLilacTertiaryDark = Color(0xFF2E2C60)
val LilacTertiaryContainerDark = Color(0xFF454288)
val OnLilacTertiaryContainerDark = Color(0xFFE4E1FF)

/** 深色底用「墨绿黑」而不是纯黑：OLED 上省电，且和青绿主色同族，夜里不刺眼 */
val SurfaceDark = Color(0xFF0F1A19)
val OnSurfaceDark = Color(0xFFE0E7E6)
val SurfaceVariantDark = Color(0xFF3F4C4A)
val OnSurfaceVariantDark = Color(0xFFBFCBC9)

val SurfaceContainerLowestDark = Color(0xFF0A1312)
val SurfaceContainerLowDark = Color(0xFF172322)
val SurfaceContainerDark = Color(0xFF1B2827)
val SurfaceContainerHighDark = Color(0xFF253331)
val SurfaceContainerHighestDark = Color(0xFF303E3C)

val OutlineDark = Color(0xFF899693)
val OutlineVariantDark = Color(0xFF3F4C4A)

val InverseSurfaceDark = Color(0xFFE0E7E6)
val InverseOnSurfaceDark = Color(0xFF2C3534)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

