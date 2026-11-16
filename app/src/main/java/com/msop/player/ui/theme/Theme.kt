package com.msop.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 主题模式（2026-11-14，按用户指定）：仅深色 / 浅色两档，默认深色。
 * 不做「跟随系统」；存在 SharedPreferences（`ThemePreferences`），设置页二选一，改完立即全局生效。
 */
enum class ThemeMode { DARK, LIGHT }

/**
 * 色板实例。2026-11 视觉重构：从「纯黑 + 平涂蓝」换成
 * 「深空墨 + 极光紫」——背景更冷、表面更有层次、强调色偏紫，
 * 让封面与歌词成为画面里唯一的高饱和元素。
 *
 * 2026-11-14 深浅色双套：浅色是低饱和香芋紫，对应深空墨的镜像；
 * 强调紫在两套下保持同族（浅色下加深一档保证对比度）。
 */
data class MsopPalette(
    val isDark: Boolean,
    val Ink: Color,
    val Void: Color,
    val Surface: Color,
    val Surface2: Color,
    val Surface3: Color,
    val Line: Color,
    val Blue: Color,
    val BlueLight: Color,
    val BlueWash: Color,
    val Teal: Color,
    val Red: Color,
    val Green: Color,
    val Gold: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextTertiary: Color,
) {
    val accent: Brush = Brush.linearGradient(
        colors = listOf(Blue, BlueLight),
    )

    val accentSoft: Brush = Brush.linearGradient(
        colors = listOf(Blue.copy(alpha = 0.20f), BlueLight.copy(alpha = 0.13f)),
    )
}

/** 深空墨（原默认）。 */
val DarkMsopPalette = MsopPalette(
    isDark = true,
    Ink = Color(0xFF07080D),
    Void = Color(0xFF040509),
    Surface = Color(0xFF11131C),
    Surface2 = Color(0xFF181B27),
    Surface3 = Color(0xFF222636),
    Line = Color(0xFF2A3042),
    Blue = Color(0xFF6D5DFB),
    BlueLight = Color(0xFF9A8CFF),
    BlueWash = Color(0x2E7C6CFF),
    Teal = Color(0xFF4FD1C5),
    Red = Color(0xFFFF6B8A),
    Green = Color(0xFF4EE0B0),
    Gold = Color(0xFFFFC46B),
    TextPrimary = Color(0xFFF6F7FC),
    TextSecondary = Color(0xFFA7ADC2),
    TextTertiary = Color(0xFF6A7189),
)

/** 浅香芋紫（2026-11-14）：柔和底 + 同族紫强调，文字换深墨色。 */
val LightMsopPalette = MsopPalette(
    isDark = false,
    Ink = Color(0xFFF4F2FB),
    Void = Color(0xFFEFEDF8),
    Surface = Color(0xFFFDFCFF),
    Surface2 = Color(0xFFF6F4FB),
    Surface3 = Color(0xFFECE9F6),
    Line = Color(0xFFDCD9EC),
    Blue = Color(0xFF6D5DFB),
    BlueLight = Color(0xFF5B4BE0),
    BlueWash = Color(0x2E6D5DFB),
    Teal = Color(0xFF12A594),
    Red = Color(0xFFE0476E),
    Green = Color(0xFF0EAD8C),
    Gold = Color(0xFFC08A2E),
    TextPrimary = Color(0xFF20223A),
    TextSecondary = Color(0xFF565B76),
    TextTertiary = Color(0xFF8D92AC),
)

/** 当前生效的色板。默认深色（保证非主题上下文/测试不炸）。 */
val LocalMsopPalette = staticCompositionLocalOf { DarkMsopPalette }

/**
 * 调色板门面：属性从 [LocalMsopPalette] 解析，调用方（`Palette.Blue` 等）
 * **无需任何改动**即可随主题切换——深浅色的接线全部收敛在这里。
 */
object Palette {
    val Ink: Color @Composable get() = LocalMsopPalette.current.Ink
    val Void: Color @Composable get() = LocalMsopPalette.current.Void
    val Surface: Color @Composable get() = LocalMsopPalette.current.Surface
    val Surface2: Color @Composable get() = LocalMsopPalette.current.Surface2
    val Surface3: Color @Composable get() = LocalMsopPalette.current.Surface3
    val Line: Color @Composable get() = LocalMsopPalette.current.Line
    val Blue: Color @Composable get() = LocalMsopPalette.current.Blue
    val BlueLight: Color @Composable get() = LocalMsopPalette.current.BlueLight
    val BlueWash: Color @Composable get() = LocalMsopPalette.current.BlueWash
    val Teal: Color @Composable get() = LocalMsopPalette.current.Teal
    val Red: Color @Composable get() = LocalMsopPalette.current.Red
    val Green: Color @Composable get() = LocalMsopPalette.current.Green
    val Gold: Color @Composable get() = LocalMsopPalette.current.Gold
    val TextPrimary: Color @Composable get() = LocalMsopPalette.current.TextPrimary
    val TextSecondary: Color @Composable get() = LocalMsopPalette.current.TextSecondary
    val TextTertiary: Color @Composable get() = LocalMsopPalette.current.TextTertiary
}

/** 常用渐变（随主题）。集中在这里，避免每个页面各调一套紫色。 */
object Gradients {
    val accent: Brush @Composable get() = LocalMsopPalette.current.accent
    val accentSoft: Brush @Composable get() = LocalMsopPalette.current.accentSoft
}

/** 深色 Material 色板（原默认）。 */
@Composable
private fun msopDarkColorScheme(p: MsopPalette) = darkColorScheme(
    primary = p.BlueLight,
    onPrimary = p.Ink,
    primaryContainer = Color(0xFF2B2360),
    onPrimaryContainer = Color(0xFFE4E0FF),
    secondary = p.Teal,
    onSecondary = p.Ink,
    background = p.Ink,
    onBackground = p.TextPrimary,
    surface = p.Surface,
    onSurface = p.TextPrimary,
    surfaceVariant = p.Surface2,
    onSurfaceVariant = p.TextSecondary,
    surfaceContainer = p.Surface,
    surfaceContainerHigh = p.Surface2,
    surfaceContainerHighest = p.Surface3,
    outline = p.Line,
    outlineVariant = Color(0xFF1D2130),
    error = p.Red,
    onError = p.Ink,
)

/** 浅色 Material 色板（2026-11-14）。 */
@Composable
private fun msopLightColorScheme(p: MsopPalette) = lightColorScheme(
    primary = p.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E0FF),
    onPrimaryContainer = Color(0xFF241C5E),
    secondary = p.Teal,
    onSecondary = Color.White,
    background = p.Ink,
    onBackground = p.TextPrimary,
    surface = p.Surface,
    onSurface = p.TextPrimary,
    surfaceVariant = p.Surface2,
    onSurfaceVariant = p.TextSecondary,
    surfaceContainer = p.Surface,
    surfaceContainerHigh = p.Surface2,
    surfaceContainerHighest = p.Surface3,
    outline = p.Line,
    outlineVariant = Color(0xFFE6E4F2),
    error = p.Red,
    onError = Color.White,
)

/**
 * 字体层级。不打包第三方字体，用系统默认（Roboto / Noto Sans SC）。
 * 标题用负字距和更重的字重，正文留足行高，标签收紧字距——
 * 层级靠字号、字重、颜色三层建立，而不是靠加框。
 */
private val MsopTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.1).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.9).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 23.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.25.sp,
    ),
)

/** 尺寸令牌。列表行 68dp、小封面 48dp，给封面留出呼吸空间。 */
object Dim {
    val screen = 20.dp
    val rowHeight = 68.dp
    val coverSmall = 48.dp
    val miniHeight = 64.dp
    val navHeight = 62.dp
    /** 底部浮层与屏幕底边（手势条上方）的悬浮间距，避免导航胶囊贴死屏幕底部。 */
    val navBottomGap = 20.dp
    val touch = 44.dp
}

object Shapes {
    val coverSmall = RoundedCornerShape(11.dp)
    val coverGrid = RoundedCornerShape(18.dp)
    val coverStage = RoundedCornerShape(30.dp)
    val card = RoundedCornerShape(22.dp)
    val cardSmall = RoundedCornerShape(15.dp)
    val mini = RoundedCornerShape(20.dp)
    val sheetTop = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val pill = RoundedCornerShape(999.dp)
}

/**
 * 根主题：按 [themeMode] 选色板（仅深浅两档，无跟随系统）。
 * 深色 = 深空墨（默认，原视觉不变）；浅色 = 浅香芋紫。
 */
@Composable
fun MsopTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val palette = when (themeMode) {
        ThemeMode.DARK -> DarkMsopPalette
        ThemeMode.LIGHT -> LightMsopPalette
    }
    CompositionLocalProvider(LocalMsopPalette provides palette) {
        MaterialTheme(
            colorScheme = if (themeMode == ThemeMode.DARK) {
                msopDarkColorScheme(palette)
            } else {
                msopLightColorScheme(palette)
            },
            typography = MsopTypography,
            content = content,
        )
    }
}
