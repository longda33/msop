package com.msop.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Gradients
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * 退出确认弹窗（2026-11-17 定稿「玻璃确认卡」）。
 *
 * 只有三样东西：一枚红色调图标、「要退出 MSOP 吗？」、两个按钮。
 * **没有副文案**（用户 2026-11-17 指定删掉）：退出是低频动作，说明越短越干脆。
 *
 * 主次靠按钮本身表达——左「继续听」是紫渐变实心（默认动作），
 * 右「退出」只留红字 + 红描边，误触成本低。点卡片外或按返回键 = 继续听。
 */
@Composable
fun ExitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 入场：0.93 → 1 放大 + 淡入，和 App 里其它浮层的手感一致。
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scale by animateFloatAsState(
            targetValue = if (shown) 1f else 0.93f,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 460f),
            label = "exitDlgScale",
        )
        val alpha by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(180),
            label = "exitDlgAlpha",
        )

        Column(
            modifier = Modifier
                .width(232.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .glassSurface(shape = RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp)
                .padding(top = 22.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 图标：红色调的圆形底，不抢标题的注意力。
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Palette.Red.copy(alpha = 0.28f),
                                Palette.Red.copy(alpha = 0.10f),
                            ),
                        ),
                    )
                    .border(1.dp, Palette.Red.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = PlayerIcons.Power,
                    contentDescription = null,
                    tint = Palette.Red,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "要退出 MSOP 吗？",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                DialogPill(
                    label = "继续听",
                    primary = true,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                DialogPill(
                    label = "退出",
                    danger = true,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 弹窗内按钮：38dp 高胶囊，三种语气（实心 / 危险 / 普通）。 */
@Composable
private fun DialogPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
) {
    val background = when {
        primary -> Gradients.accent
        danger -> Brush.linearGradient(
            listOf(Palette.Red.copy(alpha = 0.10f), Palette.Red.copy(alpha = 0.10f)),
        )
        else -> Brush.linearGradient(
            listOf(Palette.Surface3.copy(alpha = 0.7f), Palette.Surface3.copy(alpha = 0.7f)),
        )
    }
    val textColor = when {
        primary -> Color.White
        danger -> Palette.Red
        else -> Palette.TextSecondary
    }
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(Shapes.pill)
            .background(background)
            .then(
                if (danger) Modifier.border(1.dp, Palette.Red.copy(alpha = 0.30f), Shapes.pill)
                else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}
