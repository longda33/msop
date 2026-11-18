package com.msop.player.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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
 * 给编译加几十秒，而我们只需要十几个图标。这里用 [ImageVector] 直接描路径，
 * 零依赖、零体积、颜色跟随 `Icon` 的 tint。
 *
 * 2026-11-15 重绘（对照预览 `preview/icons-redesign.html`，用户确认后替换）：
 * - 替换 11 个：Disc / Queue / Filter / Shuffle / Repeat / RepeatOne / Folder / Timer /
 *   Pause / SkipNext / SkipPrevious；统一 1.9 描边、圆角端点，两两语义可区分。
 * - **保持原样**：Lyrics、Order、Cloud、DragHandle（用户指定）。
 * - **不新增 Library 图标**（用户否决），音乐库导航标签继续用 Disc。
 */
object PlayerIcons {

    /** 暂停：两条圆角竖条 */
    val Pause: ImageVector by lazy {
        icon("Pause") {
            solid {
                rect(6.6f, 4.6f, 10.3f, 19.4f)
                rect(13.7f, 4.6f, 17.4f, 19.4f)
            }
        }
    }

    /** 下一首：朝右三角 + 竖线 */
    val SkipNext: ImageVector by lazy {
        icon("SkipNext") {
            solid {
                moveTo(5f, 5.4f); verticalLineTo(18.6f); lineTo(15f, 12f); close()
                rect(16.2f, 5.4f, 18.8f, 18.6f)
            }
        }
    }

    /** 上一首：朝左三角 + 竖线 */
    val SkipPrevious: ImageVector by lazy {
        icon("SkipPrevious") {
            solid {
                moveTo(19f, 5.4f); verticalLineTo(18.6f); lineTo(9f, 12f); close()
                rect(5.2f, 5.4f, 7.8f, 18.6f)
            }
        }
    }

    /** 随机：两条交叉箭头（标准 shuffle 形态） */
    val Shuffle: ImageVector by lazy {
        icon("Shuffle") {
            stroke {
                moveTo(3f, 6f); horizontalLineTo(6.2f)
                curveTo(7.5f, 6f, 8.7f, 6.6f, 9.4f, 7.7f)
                lineTo(15.6f, 16.3f)
                curveTo(16.3f, 17.4f, 17.5f, 18f, 18.8f, 18f)
                horizontalLineTo(21f)
                moveTo(18f, 15f); lineTo(21f, 18f); lineTo(18f, 21f)
                moveTo(18f, 3f); lineTo(21f, 6f); lineTo(18f, 9f)
                moveTo(3f, 18f); horizontalLineTo(6.2f)
                curveTo(7.5f, 18f, 8.7f, 17.4f, 9.4f, 16.3f)
                lineTo(10.6f, 14.7f)
                moveTo(13.6f, 9.3f); lineTo(14.6f, 7.7f)
                curveTo(15.3f, 6.6f, 16.5f, 6f, 17.8f, 6f)
                horizontalLineTo(21f)
            }
        }
    }

    /** 列表循环：标准双箭头回环 */
    val Repeat: ImageVector by lazy {
        icon("Repeat") {
            stroke {
                moveTo(17f, 2.5f); lineTo(20.5f, 6f); lineTo(17f, 9.5f)
                moveTo(3.5f, 11f); verticalLineTo(9f)
                curveTo(3.5f, 7.3f, 4.9f, 6f, 6.5f, 6f)
                horizontalLineTo(20.5f)
                moveTo(7f, 21.5f); lineTo(3.5f, 18f); lineTo(7f, 14.5f)
                moveTo(20.5f, 13f); verticalLineTo(15f)
                curveTo(20.5f, 16.7f, 19.1f, 18f, 17.5f, 18f)
                horizontalLineTo(3.5f)
            }
        }
    }

    /** 队列：列表 + 标准八分音符（与 Lyrics 的气泡形态区分） */
    val Queue: ImageVector by lazy {
        icon("Queue") {
            stroke {
                moveTo(4f, 6f); horizontalLineTo(16f)
                moveTo(4f, 11f); horizontalLineTo(16f)
                moveTo(4f, 16f); horizontalLineTo(13f)
                moveTo(19.4f, 17f); verticalLineTo(9.4f); lineTo(21.5f, 8.4f)
            }
            solid {
                dot(17.2f, 17f, 2.2f)
            }
        }
    }

    /**
     * 歌词（**原始形态，用户要求改回**）：四条横线（第四行短一截）。
     *
     * 2026-11-17 深夜（续）用户指令：底部 dock 的歌词图标、设置页里的歌词图标改回原始，
     * 只有歌词页信息行的悬浮歌词钮用新形态 [LyricsFilled]。
     */
    val Lyrics: ImageVector by lazy {
        icon("Lyrics") {
            stroke {
                moveTo(4f, 6f); horizontalLineTo(20f)
                moveTo(4f, 10.5f); horizontalLineTo(20f)
                moveTo(4f, 15f); horizontalLineTo(20f)
                moveTo(4f, 19.5f); horizontalLineTo(13f)
            }
        }
    }

    /**
     * 悬浮歌词钮用：**Material `Icons.Filled.Lyrics` 的原始 pathData**（手抄，不引 `material-icons-extended`）。
     *
     * 2026-11-17 深夜（续）用户指令：「歌词悬浮按钮图标改成和ShuYin一样」——ShuYin 用的就是
     * `Icons.Filled.Lyrics`。形态是「聊天气泡 + 三行歌词 + 右上角音符」，实心填充，
     * 气泡里的三行用**奇偶填充**挖空（[PathFillType.EvenOdd]，否则同一色填充会把它们盖住）。
     */
    val LyricsFilled: ImageVector by lazy {
        icon("LyricsFilled") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            ) {
                // 气泡主体：圆角矩形 + 左下角尖角，右侧给音符让位
                moveTo(14f, 9f)
                curveTo(14f, 6.96f, 15.24f, 5.21f, 17f, 4.43f)
                lineTo(17f, 4f)
                curveTo(17f, 2.9f, 16.1f, 2f, 15f, 2f)
                lineTo(4f, 2f)
                curveTo(2.9f, 2f, 2.01f, 2.9f, 2.01f, 4f)
                lineTo(2f, 22f)
                lineTo(6f, 18f)
                lineTo(15f, 18f)
                curveTo(16.1f, 18f, 17f, 17.1f, 17f, 16f)
                lineTo(17f, 13.58f)
                curveTo(15.24f, 12.8f, 14f, 11.05f, 14f, 9f)
                close()
                // 三行歌词（挖空）
                rect(6f, 12f, 10f, 14f)
                rect(6f, 9f, 13f, 11f)
                rect(6f, 6f, 13f, 8f)
                // 右上角音符
                moveTo(20f, 6.18f)
                curveTo(19.69f, 6.07f, 19.35f, 6f, 19f, 6f)
                curveTo(17.34f, 6f, 16f, 7.34f, 16f, 9f)
                curveTo(16f, 10.66f, 17.34f, 12f, 19f, 12f)
                curveTo(20.66f, 12f, 22f, 10.66f, 22f, 9f)
                lineTo(22f, 3f)
                lineTo(24f, 3f)
                lineTo(24f, 1f)
                lineTo(20f, 1f)
                lineTo(20f, 6.18f)
                close()
            }
        }
    }

    /** 云端：一段云形曲线（用户指定保持原样） */
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

    /** 文件夹：标准单层文件夹闭合路径 */
    val Folder: ImageVector by lazy {
        icon("Folder") {
            stroke {
                moveTo(3f, 7.4f)
                curveTo(3f, 6.3f, 3.9f, 5.4f, 5f, 5.4f)
                horizontalLineTo(9f); lineTo(11f, 7.4f); horizontalLineTo(19f)
                curveTo(20.1f, 7.4f, 21f, 8.3f, 21f, 9.4f)
                verticalLineTo(18f)
                curveTo(21f, 19.1f, 20.1f, 20f, 19f, 20f)
                horizontalLineTo(5f)
                curveTo(3.9f, 20f, 3f, 19.1f, 3f, 18f)
                close()
            }
        }
    }

    /** 唱片：外圈 + 内圈 + 中心孔（仍兼作「音乐库」导航标签，用户否决新增 Library） */
    val Disc: ImageVector by lazy {
        icon("Disc") {
            stroke {
                dot(12f, 12f, 9f)
                dot(12f, 12f, 3.1f)
            }
            solid {
                dot(12f, 12f, 1f)
            }
        }
    }

    /** 定时：标准秒表（表盘 + 顶部按键 + 指针） */
    val Timer: ImageVector by lazy {
        icon("Timer") {
            stroke {
                dot(12f, 13.2f, 8f)
                moveTo(12f, 8.8f); verticalLineTo(13.4f); lineTo(15f, 15.3f)
                moveTo(9.5f, 2.6f); horizontalLineTo(14.5f)
                moveTo(19.4f, 6.2f); lineTo(20.7f, 4.9f)
            }
        }
    }

    /** 顺序播放：三行列表 + 向下的箭头（用户指定保持原样） */
    val Order: ImageVector by lazy {
        icon("Order") {
            stroke {
                moveTo(4f, 6.6f); horizontalLineTo(13f)
                moveTo(4f, 11.6f); horizontalLineTo(13f)
                moveTo(4f, 16.6f); horizontalLineTo(9f)
                moveTo(17.6f, 7.4f); verticalLineTo(18.4f)
                moveTo(14.7f, 15.5f); lineTo(17.6f, 18.4f); lineTo(20.5f, 15.5f)
            }
        }
    }

    /** 单曲循环：回环 + 中间的「1」 */
    val RepeatOne: ImageVector by lazy {
        icon("RepeatOne") {
            stroke {
                moveTo(17f, 2.5f); lineTo(20.5f, 6f); lineTo(17f, 9.5f)
                moveTo(3.5f, 11f); verticalLineTo(9f)
                curveTo(3.5f, 7.3f, 4.9f, 6f, 6.5f, 6f)
                horizontalLineTo(20.5f)
                moveTo(7f, 21.5f); lineTo(3.5f, 18f); lineTo(7f, 14.5f)
                moveTo(20.5f, 13f); verticalLineTo(15f)
                curveTo(20.5f, 16.7f, 19.1f, 18f, 17.5f, 18f)
                horizontalLineTo(3.5f)
            }
            stroke(width = 2.4f) {
                moveTo(10.7f, 11.3f); lineTo(12.2f, 10.3f); verticalLineTo(14.3f)
            }
        }
    }

    /** 拖动手柄：2 列 × 3 行圆点（用户指定保持原样） */
    val DragHandle: ImageVector by lazy {
        icon("DragHandle") {
            solid {
                listOf(9.2f, 14.8f).forEach { x ->
                    listOf(6.4f, 12f, 17.6f).forEach { y -> dot(x, y, 1.75f) }
                }
            }
        }
    }

    /**
     * 横竖屏切换（2026-11-15 用户指令「歌词区增加横竖屏切换按钮」）。
     *
     * 形状取自用户自有项目 music-web-cf-main 全屏歌词工具条的 `ICON.land`：
     * 躺倒的圆角手机 + 一段带箭头的弧线（`index.html` 14871 行 SVG 原样搬）。
     */
    val Rotate: ImageVector by lazy {
        icon("Rotate") {
            stroke {
                // 躺倒的手机外形（圆角矩形，r≈2）
                moveTo(4.6f, 8.2f)
                horizontalLineTo(13.2f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 15.2f, y1 = 10.2f)
                verticalLineTo(14.8f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 13.2f, y1 = 16.8f)
                horizontalLineTo(4.6f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 2.6f, y1 = 14.8f)
                verticalLineTo(10.2f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 4.6f, y1 = 8.2f)
                close()
            }
            stroke {
                // 旋转弧线
                moveTo(19.6f, 4.4f)
                arcTo(9.6f, 9.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19.8f, y1 = 11.8f)
            }
            stroke(width = 1.9f) {
                // 箭头
                moveTo(16.8f, 3.6f)
                lineTo(19.7f, 4.9f)
                lineTo(18.4f, 7.8f)
            }
        }
    }

    /**
     * 加入歌单（2026-11-16 用户需求「所有歌曲三点菜单增加 加入歌单…每个功能前增加对应图标」）：
     * 八分音符 + 右上角加号。
     */
    val PlaylistAdd: ImageVector by lazy {
        icon("PlaylistAdd") {
            solid { dot(8.4f, 18.2f, 2.6f) }
            stroke {
                moveTo(11f, 18.2f)
                verticalLineTo(7.4f)
                lineTo(15.2f, 5.8f)
                verticalLineTo(9.6f)
            }
            stroke(width = 1.7f) {
                moveTo(19.2f, 3.8f)
                verticalLineTo(9.8f)
                moveTo(16.2f, 6.8f)
                horizontalLineTo(22.2f)
            }
        }
    }

    /** 移动到其它歌单：文件夹 + 指向内的箭头。 */
    val MoveTo: ImageVector by lazy {
        icon("MoveTo") {
            stroke {
                moveTo(3.6f, 18.6f)
                verticalLineTo(7f)
                horizontalLineTo(9.2f)
                lineTo(11f, 9.4f)
                horizontalLineTo(20.4f)
                verticalLineTo(18.6f)
                close()
            }
            stroke(width = 1.7f) {
                moveTo(9.4f, 14f)
                horizontalLineTo(15.6f)
                moveTo(13.6f, 11.9f)
                lineTo(15.8f, 14f)
                lineTo(13.6f, 16.1f)
            }
        }
    }

    /** 移除曲库：圆形减号。 */
    val RemoveFromLibrary: ImageVector by lazy {
        icon("RemoveFromLibrary") {
            stroke {
                moveTo(12f, 3.2f)
                arcTo(8.8f, 8.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 20.8f)
                arcTo(8.8f, 8.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 3.2f)
                close()
            }
            stroke(width = 1.9f) {
                moveTo(8.4f, 12f)
                horizontalLineTo(15.6f)
            }
        }
    }

    /**
     * 加入歌单（2026-11-16 晚用户指令「加入歌单图标使用圆圈加号」）：
     * 与「移除曲库」的圆圈减号同一套笔画，只把横杠换成十字。
     */
    val PlaylistAddCircle: ImageVector by lazy {
        icon("PlaylistAddCircle") {
            stroke {
                moveTo(12f, 3.2f)
                arcTo(8.8f, 8.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 20.8f)
                arcTo(8.8f, 8.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 3.2f)
                close()
            }
            stroke(width = 1.9f) {
                moveTo(8.4f, 12f)
                horizontalLineTo(15.6f)
                moveTo(12f, 8.4f)
                verticalLineTo(15.6f)
            }
        }
    }

    /** 下载歌曲：向下箭头 + 底部托盘。 */
    val Download: ImageVector by lazy {
        icon("Download") {
            stroke(width = 1.8f) {
                moveTo(12f, 3.6f)
                verticalLineTo(14.6f)
                moveTo(8.2f, 11f)
                lineTo(12f, 14.9f)
                lineTo(15.8f, 11f)
            }
            stroke(width = 1.8f) {
                moveTo(4.4f, 17.4f)
                verticalLineTo(19.2f)
                horizontalLineTo(19.6f)
                verticalLineTo(17.4f)
            }
        }
    }

    /** 筛选：标准对称漏斗 */
    val Filter: ImageVector by lazy {
        icon("Filter") {
            stroke {
                moveTo(3.5f, 5.5f)
                horizontalLineTo(20.5f)
                lineTo(13.9f, 12.9f)
                verticalLineTo(18.5f)
                lineTo(10.1f, 20.6f)
                verticalLineTo(12.9f)
                close()
            }
        }
    }

    /** 电源：退出确认弹窗用（顶部缺口圆环 + 一根竖线）。 */
    val Power: ImageVector by lazy {
        icon("Power") {
            stroke {
                moveTo(12f, 3.2f); verticalLineTo(11.4f)
                moveTo(16.1f, 5.5f)
                arcTo(
                    horizontalEllipseRadius = 8.2f,
                    verticalEllipseRadius = 8.2f,
                    theta = 0f,
                    isMoreThanHalf = true,
                    isPositiveArc = true,
                    x1 = 7.9f,
                    y1 = 5.5f,
                )
            }
        }
    }

    /** 全部来源：三条横线。 */
    val AllSources: ImageVector by lazy {
        icon("AllSources") {
            stroke {
                moveTo(4f, 7f); horizontalLineTo(20f)
                moveTo(4f, 12f); horizontalLineTo(20f)
                moveTo(4f, 17f); horizontalLineTo(20f)
            }
        }
    }

    /** 本机设备：手机轮廓 + 底部横条。 */
    val Device: ImageVector by lazy {
        icon("Device") {
            stroke {
                moveTo(7f, 4.2f)
                horizontalLineTo(17f)
                verticalLineTo(19.8f)
                horizontalLineTo(7f)
                close()
                moveTo(10.6f, 17.2f); horizontalLineTo(13.4f)
            }
        }
    }

    // ---------------------------------------------------------------- 歌词工具条（2026-11-16）
    // 三个形状全部取自 music-web-cf-main 全屏歌词工具条的 SVG（index.html 14871 行
    // 附近的 `ICON.target` / `ICON.swap` / `ICON.min`），用户要求「按钮搬过来，
    // 位置和 music-web-cf-main 相同」。

    /** 歌词偏移微调：准星（圆 + 内点 + 四向刻度），web `ICON.target` */
    val Target: ImageVector by lazy {
        icon("Target") {
            stroke {
                dot(12f, 12f, 8f)
                dot(12f, 12f, 2.4f)
                moveTo(12f, 2f); verticalLineTo(5f)
                moveTo(12f, 19f); verticalLineTo(22f)
                moveTo(2f, 12f); horizontalLineTo(5f)
                moveTo(19f, 12f); horizontalLineTo(22f)
            }
        }
    }

    /** 换一份歌词 / 重新加载：上下两条反向箭头，web `ICON.swap` */
    val Swap: ImageVector by lazy {
        icon("Swap") {
            stroke {
                moveTo(8f, 3f); lineTo(4f, 7f); lineTo(8f, 11f)
                moveTo(4f, 7f); horizontalLineTo(20f)
                moveTo(16f, 21f); lineTo(20f, 17f); lineTo(16f, 13f)
                moveTo(20f, 17f); horizontalLineTo(4f)
            }
        }
    }

    /** 退出歌词全屏：四角向内的箭头，web `ICON.min` */
    val Collapse: ImageVector by lazy {
        icon("Collapse") {
            stroke {
                moveTo(8f, 3f); verticalLineTo(6f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 6f, y1 = 8f)
                horizontalLineTo(3f)
                moveTo(21f, 8f); horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 16f, y1 = 6f)
                verticalLineTo(3f)
                moveTo(3f, 16f); horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 8f, y1 = 18f)
                verticalLineTo(21f)
                moveTo(16f, 21f); verticalLineTo(18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 18f, y1 = 16f)
                horizontalLineTo(21f)
            }
        }
    }

    // ------------------------------------------------ 设置页图标（2026-11-18 用户指令）

    /**
     * OpenList 品牌标志。
     *
     * 官方 logo 是「左侧开口圆环 + 中间一道斜条 + 右下折钩」，双色（青绿 + 蓝）。
     * 这里按单色描边路径重绘：`Icon` 的颜色由调用方的 tint 决定，所以双色被压成一色。
     * 笔画比常规图标粗（3.0，普通是 1.9）——品牌图形本身是粗体块面，细线会失真。
     */
    val OpenList: ImageVector by lazy {
        icon("OpenList") {
            stroke(width = 3.0f) {
                // 环：从左上圆头起，绕过右上 / 右下 / 底部，收在左下偏中（官方缺口约 68°）
                moveTo(5.2f, 7.4f)
                arcTo(8.2f, 8.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 5.2f, y1 = 16.6f)
                // 中间斜条（原图是蓝色那条）
                moveTo(8.6f, 18.4f); lineTo(13.0f, 9.6f)
                // 右下折钩
                moveTo(16.9f, 11.6f); lineTo(13.4f, 18.6f); lineTo(15.0f, 18.6f)
            }
        }
    }

    /** 电池：外壳 + 正极小凸起 + 内部电量块（电池优化白名单）。 */
    val Battery: ImageVector by lazy {
        icon("Battery") {
            stroke {
                moveTo(3.4f, 8.6f)
                horizontalLineTo(16.4f)
                verticalLineTo(16.6f)
                horizontalLineTo(3.4f)
                close()
                // 右侧正极触点
                moveTo(16.4f, 11.0f)
                horizontalLineTo(18.6f)
                verticalLineTo(14.2f)
                horizontalLineTo(16.4f)
            }
            solid {
                // 电量块
                rect(5.4f, 10.2f, 10.6f, 14.9f)
            }
        }
    }

    /** 硬盘：盘体 + 盘面分层线 + 右下读写指示灯（远程播放缓存）。 */
    val HardDrive: ImageVector by lazy {
        icon("HardDrive") {
            stroke {
                moveTo(3.4f, 5.6f)
                horizontalLineTo(20.6f)
                verticalLineTo(18.4f)
                horizontalLineTo(3.4f)
                close()
                // 盘面分界：上部是盘片区，下部窄条是指示灯区
                moveTo(4.6f, 13.6f)
                horizontalLineTo(19.4f)
            }
            solid {
                // 指示灯
                dot(17.2f, 16.0f, 1.1f)
            }
        }
    }

    /** Wi‑Fi：三段同心弧 + 底部圆点（仅 Wi‑Fi 缓存）。 */
    val Wifi: ImageVector by lazy {
        icon("Wifi") {
            stroke {
                moveTo(3.4f, 15.2f)
                arcTo(8.6f, 8.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20.6f, y1 = 15.2f)
                moveTo(6.6f, 15.2f)
                arcTo(5.4f, 5.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 17.4f, y1 = 15.2f)
                moveTo(9.8f, 15.2f)
                arcTo(2.2f, 2.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 14.2f, y1 = 15.2f)
            }
            solid {
                dot(12f, 18.6f, 1.4f)
            }
        }
    }

    /** i 图标：圆环 + i 字（版本号）。 */
    val InfoCircle: ImageVector by lazy {
        icon("InfoCircle") {
            stroke {
                dot(12f, 12f, 9.2f)
                moveTo(12f, 10.8f)
                verticalLineTo(17.0f)
            }
            solid {
                dot(12f, 7.8f, 1.25f)
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
    stroke(width = 1.9f, pathBuilder = pathBuilder)
}

private fun ImageVector.Builder.stroke(
    width: Float,
    pathBuilder: PathBuilder.() -> Unit,
) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}

/** 轴对齐矩形（PathBuilder 没有 addRect）。 */
private fun PathBuilder.rect(left: Float, top: Float, right: Float, bottom: Float) {
    moveTo(left, top)
    horizontalLineTo(right)
    verticalLineTo(bottom)
    horizontalLineTo(left)
    close()
}

/** 实心/空心小圆点。PathBuilder 没有 addOval，用两段半圆拼。 */
private fun PathBuilder.dot(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
    close()
}
