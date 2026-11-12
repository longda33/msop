package com.msop.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 色板。全部取自 Msop 的实际界面（macOS 资料库页与播放页截图取样）。
 *
 * 深色是唯一主题：Msop 在 iOS / macOS / tvOS 上都是深色为主，
 * 播放页更是恒定深色。这里不再维护浅色一套，减少一半配色验证工作。
 */
object Palette {
    /** 页面底色 */
    val Ink = Color(0xFF0A0A0C)

    /** 卡片、底部导航、面板 */
    val Surface = Color(0xFF141418)

    /** 次级表面：输入框、图标底板 */
    val Surface2 = Color(0xFF1B1B21)
    val Surface3 = Color(0xFF24242C)

    /** 分割线 */
    val Line = Color(0xFF2A2A33)

    /** Msop 蓝 */
    val Blue = Color(0xFF3D5AF1)
    val BlueLight = Color(0xFF5B76FF)

    /** 蓝色 18% 透明，用于选中态胶囊底 */
    val BlueWash = Color(0x2E3D5AF1)

    val Red = Color(0xFFF04A5C)
    val Green = Color(0xFF4ADE9B)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0x99FFFFFF)
    val TextTertiary = Color(0x5CFFFFFF)
}

private val MsopColorScheme = darkColorScheme(
    primary = Palette.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E2A6E),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Palette.BlueLight,
    onSecondary = Color.White,
    background = Palette.Ink,
    onBackground = Palette.TextPrimary,
    surface = Palette.Ink,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.Surface2,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface2,
    surfaceContainerHighest = Palette.Surface3,
    outline = Palette.Line,
    outlineVariant = Color(0xFF1F1F26),
    error = Palette.Red,
    onError = Color.White,
)

/**
 * 字体层级。不打包第三方字体，用系统默认（Roboto / Noto Sans SC），
 * 靠字号 + 字重 + 字距建立层级——Msop 也是这个思路。
 */
private val MsopTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.8).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
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
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
    ),
)

/** 尺寸令牌。列表行 64dp、小封面 44dp，与设计稿一致。 */
object Dim {
    val screen = 16.dp
    val rowHeight = 64.dp
    val coverSmall = 44.dp
    val miniHeight = 58.dp
    val navHeight = 82.dp
    val touch = 48.dp
}

object Shapes {
    val coverSmall = RoundedCornerShape(7.dp)
    val coverGrid = RoundedCornerShape(12.dp)
    val coverStage = RoundedCornerShape(20.dp)
    val card = RoundedCornerShape(16.dp)
    val mini = RoundedCornerShape(19.dp)
    val sheetTop = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val pill = RoundedCornerShape(999.dp)
}

@Composable
fun MsopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MsopColorScheme,
        typography = MsopTypography,
        content = content,
    )
}
