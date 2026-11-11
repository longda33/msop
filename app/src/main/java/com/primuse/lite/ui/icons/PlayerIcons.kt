package com.primuse.lite.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 播放器专用图标。
 *
 * 为什么自绘而不是加 `material-icons-extended`：那个包未混淆时给 APK 加约 8 MB、
 * 给编译加几十秒，而我们只需要十个图标。这里用 [ImageVector] 直接描路径，
 * 零依赖、零体积、颜色跟随 `Icon` 的 tint。
 */
object PlayerIcons {

    /** 暂停：两条竖条 */
    val Pause: ImageVector by lazy {
        icon("Pause") {
            solid {
                moveTo(7f, 4.8f); horizontalLineTo(10.4f); verticalLineTo(19.2f); horizontalLineTo(7f); close()
                moveTo(13.6f, 4.8f); horizontalLineTo(17f); verticalLineTo(19.2f); horizontalLineTo(13.6f); close()
            }
        }
    }

    /** 下一首：竖线 + 朝右三角 */
    val SkipNext: ImageVector by lazy {
        icon("SkipNext") {
            solid {
                moveTo(15.4f, 5f); horizontalLineTo(17.6f); verticalLineTo(19f); horizontalLineTo(15.4f); close()
                moveTo(4f, 5.6f); lineTo(13.1f, 12f); lineTo(4f, 18.4f); close()
            }
        }
    }

    /** 上一首：竖线 + 朝左三角 */
    val SkipPrevious: ImageVector by lazy {
        icon("SkipPrevious") {
            solid {
                moveTo(6.4f, 5f); horizontalLineTo(8.6f); verticalLineTo(19f); horizontalLineTo(6.4f); close()
                moveTo(20f, 5.6f); lineTo(10.9f, 12f); lineTo(20f, 18.4f); close()
            }
        }
    }

    /** 随机：两条交叉线 + 四段箭头 */
    val Shuffle: ImageVector by lazy {
        icon("Shuffle") {
            stroke {
                moveTo(16.5f, 4f); lineTo(20f, 4f); lineTo(20f, 7.5f)
                moveTo(20f, 4f); lineTo(14.6f, 9.4f)
                moveTo(4f, 4.6f); lineTo(8.6f, 9.2f)
                moveTo(16.5f, 20f); lineTo(20f, 20f); lineTo(20f, 16.5f)
                moveTo(20f, 20f); lineTo(14.2f, 14.2f)
                moveTo(4f, 19.4f); lineTo(8.6f, 14.8f)
            }
        }
    }

    /** 循环：开口矩形 + 两个箭头 */
    val Repeat: ImageVector by lazy {
        icon("Repeat") {
            stroke {
                moveTo(19.2f, 12f); verticalLineTo(15.4f); horizontalLineTo(7.2f)
                moveTo(4.8f, 12f); verticalLineTo(8.6f); horizontalLineTo(16.8f)
                moveTo(14.4f, 6.2f); lineTo(16.8f, 8.6f); lineTo(14.4f, 11f)
                moveTo(9.6f, 17.8f); lineTo(7.2f, 15.4f); lineTo(9.6f, 13f)
            }
        }
    }

    /** 队列：三条线 + 一个音符 */
    val Queue: ImageVector by lazy {
        icon("Queue") {
            stroke {
                moveTo(4f, 7f); horizontalLineTo(16f)
                moveTo(4f, 12f); horizontalLineTo(16f)
                moveTo(4f, 17f); horizontalLineTo(11f)
            }
            solid {
                moveTo(17.6f, 13.4f); lineTo(21.6f, 10.3f); lineTo(21.6f, 16.5f); close()
            }
        }
    }

    /** 歌词：四条横线 */
    val Lyrics: ImageVector by lazy {
        icon("Lyrics") {
            stroke {
                moveTo(4f, 6f); horizontalLineTo(20f)
                moveTo(4f, 11f); horizontalLineTo(16f)
                moveTo(4f, 16f); horizontalLineTo(20f)
                moveTo(4f, 21f); horizontalLineTo(12f)
            }
        }
    }

    /** 云端：一段云形曲线 */
    val Cloud: ImageVector by lazy {
        icon("Cloud") {
            stroke {
                moveTo(7f, 18.5f)
                curveTo(4.5f, 18.5f, 2.5f, 16.5f, 2.5f, 14f)
                curveTo(2.5f, 11.6f, 4.3f, 9.7f, 6.7f, 9.5f)
                curveTo(7.3f, 6.9f, 9.6f, 5f, 12.3f, 5f)
                curveTo(15.4f, 5f, 17.9f, 7.5f, 17.9f, 10.6f)
                curveTo(20f, 11.1f, 21.5f, 13f, 21.5f, 15.2f)
                curveTo(21.5f, 17.6f, 19.6f, 18.5f, 17.6f, 18.5f)
                close()
            }
        }
    }

    /** 文件夹 */
    val Folder: ImageVector by lazy {
        icon("Folder") {
            stroke {
                moveTo(3.4f, 7.4f)
                curveTo(3.4f, 6.3f, 4.3f, 5.4f, 5.4f, 5.4f)
                lineTo(8.6f, 5.4f); lineTo(10.6f, 7.4f); lineTo(18.6f, 7.4f)
                curveTo(19.7f, 7.4f, 20.6f, 8.3f, 20.6f, 9.4f)
                verticalLineTo(17.6f)
                curveTo(20.6f, 18.7f, 19.7f, 19.6f, 18.6f, 19.6f)
                horizontalLineTo(5.4f)
                curveTo(4.3f, 19.6f, 3.4f, 18.7f, 3.4f, 17.6f)
                close()
            }
        }
    }

    /** 唱片：同心圆 */
    val Disc: ImageVector by lazy {
        icon("Disc") {
            stroke {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 21f, y1 = 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 3f, y1 = 12f)
                moveTo(9.6f, 12f)
                arcTo(2.4f, 2.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 14.4f, y1 = 12f)
                arcTo(2.4f, 2.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 9.6f, y1 = 12f)
            }
        }
    }
}

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

private fun ImageVector.Builder.solid(pathBuilder: PathBuilder.() -> Unit) {
    path(fill = SolidColor(Color.Black), pathBuilder = pathBuilder)
}

private fun ImageVector.Builder.stroke(pathBuilder: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}
