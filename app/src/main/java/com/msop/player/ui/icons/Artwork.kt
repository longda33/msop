package com.msop.player.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 没有封面时使用的程序化封面。
 *
 * 之前缺封面就是一块灰底 + 一个唱片图标，整个曲库看起来像半成品。
 * 这里按「专辑名 / 歌手名」哈希出一套固定的极光渐变 + 几何图形：
 * 同一张专辑永远同一张图，离线、零网络、零磁盘，纯粹为了好看。
 */
private data class ArtPalette(
    val start: Color,
    val end: Color,
    val blobA: Color,
    val blobB: Color,
)

private val ART_PALETTES = listOf(
    ArtPalette(Color(0xFF221746), Color(0xFF6D4CFF), Color(0xFFB69CFF), Color(0xFF3ED9C7)),
    ArtPalette(Color(0xFF2A100B), Color(0xFFC2410C), Color(0xFFFFB86B), Color(0xFFFF6B8A)),
    ArtPalette(Color(0xFF062526), Color(0xFF0F766E), Color(0xFF5EEAD4), Color(0xFF60A5FA)),
    ArtPalette(Color(0xFF0A1638), Color(0xFF1D4ED8), Color(0xFF60A5FA), Color(0xFFA78BFA)),
    ArtPalette(Color(0xFF2A0A1A), Color(0xFF9D174D), Color(0xFFFDA4AF), Color(0xFFC084FC)),
    ArtPalette(Color(0xFF14210A), Color(0xFF4D7C0F), Color(0xFFD9F99D), Color(0xFF34D399)),
    ArtPalette(Color(0xFF10131E), Color(0xFF3B4252), Color(0xFF94A3B8), Color(0xFF818CF8)),
    ArtPalette(Color(0xFF1C0A2E), Color(0xFF7E22CE), Color(0xFFF0ABFC), Color(0xFFFB923C)),
)

private fun seedHash(seed: String): Int = (seed.hashCode().toLong() and 0x7fffffffL).toInt()

@Composable
fun GeneratedArtwork(
    seed: String,
    modifier: Modifier = Modifier,
) {
    val hash = remember(seed) { seedHash(seed) }
    val palette = remember(hash) { ART_PALETTES[hash % ART_PALETTES.size] }
    val variant = remember(hash) { (hash / ART_PALETTES.size) % 6 }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val minSide = minOf(w, h)

        // 底色：左上深、右下亮
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(palette.start, palette.end),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
        )

        // 两团柔光，位置由 variant 决定，保证同一 seed 稳定
        val ax = w * (0.68f + (variant % 3) * 0.06f)
        val ay = h * (0.20f + (variant / 3) * 0.08f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.blobA.copy(alpha = 0.72f), Color.Transparent),
                center = Offset(ax, ay),
                radius = minSide * 0.82f,
            ),
            radius = minSide * 0.82f,
            center = Offset(ax, ay),
        )
        val bx = w * (0.16f + (variant / 2) * 0.08f)
        val by = h * (0.82f - (variant % 2) * 0.06f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.blobB.copy(alpha = 0.58f), Color.Transparent),
                center = Offset(bx, by),
                radius = minSide * 0.70f,
            ),
            radius = minSide * 0.70f,
            center = Offset(bx, by),
        )

        // 一条极淡的斜向光带，让大图有「玻璃」感
        val band = Path().apply {
            moveTo(w * (0.02f + 0.1f * variant), h * 0.72f)
            lineTo(w * 0.92f, h * 0.08f)
            lineTo(w * 0.98f, h * 0.20f)
            lineTo(w * 0.12f, h * 0.90f)
            close()
        }
        drawPath(band, color = Color.White.copy(alpha = 0.055f))

        // 同心圆环，像唱片，也像镜头光晕
        val rx = w * (0.24f + 0.06f * (variant % 3))
        val ry = h * (0.34f + 0.05f * (variant % 2))
        for (i in 1..3) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f - i * 0.022f),
                radius = minSide * (0.14f + i * 0.11f),
                center = Offset(rx, ry),
                style = Stroke(width = (minSide * 0.006f).coerceIn(0.6f, 1.4f)),
            )
        }

        // 一个高光小点
        drawCircle(
            color = Color.White.copy(alpha = 0.65f),
            radius = (minSide * 0.018f).coerceAtLeast(1.2f),
            center = Offset(w * 0.78f, h * 0.28f),
        )

        // 底部压暗，保证叠上去的文字/角标可读
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x44000000)),
                startY = h * 0.45f,
                endY = h,
            ),
        )
    }
}

/** 艺术家头像：程序化渐变 + 首字，替代灰底圆圈。 */
@Composable
fun ArtistAvatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        GeneratedArtwork(seed = name, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x33000000)),
        )
        Text(
            text = name.trim().take(1).ifBlank { "♪" },
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}
