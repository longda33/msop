package com.msop.player.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.msop.player.core.model.Track
import com.msop.player.core.model.audioQualityLabel
import com.msop.player.core.model.toDurationLabel
import com.msop.player.ui.icons.GeneratedArtwork
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * 统一默认封面 seed（2026-11-14）：无封面/无 seed 时全 App 用同一张生成艺术图，
 * 保证封面位永远有「一张像样的封面」而不是灰底图标。
 */
const val DEFAULT_COVER_SEED = "msop-default-cover"

/**
 * 封面 URI 可用性检查（2026-11-16 晚用户报「封面怎么是白色」）。
 *
 * 本机封面存在 `files/covers/<hash>.jpg`。重装、清缓存、系统清理应用数据之后数据库里的
 * `coverUri` 还在、文件却没了；Coil 加载本地缺失文件只会失败，于是封面位露出底色
 * （浅色主题就是一块**白色**）。这里在渲染前判一次文件是否存在，失效就走程序化封面，
 * 不会再出现白块；数据侧的自愈（重新提取真封面）见 `LocalMetadataEnricher.repairBrokenCovers`。
 */
private fun usableCoverUri(coverUri: String?): String? {
    if (coverUri == null) return null
    if (!coverUri.startsWith("file://")) return coverUri
    val path = coverUri.removePrefix("file://")
    return if (java.io.File(path).isFile) coverUri else null
}

/**
 * 封面缩略图。有真实封面就加载，没有就生成一张固定哈希的极光封面。
 * [seed] 建议传专辑名或 track.id；同一张专辑会得到同一套颜色。
 */
@Composable
fun CoverThumb(
    coverUri: String?,
    size: Dp?,
    shape: Shape = Shapes.coverSmall,
    modifier: Modifier = Modifier,
    iconSize: Dp? = null,
    seed: String? = null,
) {
    val realCover = remember(coverUri) { usableCoverUri(coverUri) }
    Box(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier.fillMaxSize())
            .clip(shape)
            .background(Palette.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        when {
            realCover != null -> AsyncImage(
                model = realCover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            seed != null -> GeneratedArtwork(seed = seed, modifier = Modifier.fillMaxSize())

            // 2026-11-14：无封面也无一曲一 seed 时，展示统一默认封面（生成艺术图），
            // 不再是灰底光盘图标——封面位永远是一张「像样的封面」。
            else -> GeneratedArtwork(seed = DEFAULT_COVER_SEED, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.045f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = 0.07f), shape),
        )
    }
}

/** 正方形封面（宽度撑满，高度等于宽度），用于专辑网格。 */
@Composable
fun CoverSquare(
    coverUri: String?,
    shape: Shape = Shapes.coverGrid,
    modifier: Modifier = Modifier,
    seed: String? = null,
) {
    val realCover = remember(coverUri) { usableCoverUri(coverUri) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Palette.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        when {
            realCover != null -> AsyncImage(
                model = realCover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            seed != null -> GeneratedArtwork(seed = seed, modifier = Modifier.fillMaxSize())

            // 2026-11-14：统一默认封面（同 CoverThumb）。
            else -> GeneratedArtwork(seed = DEFAULT_COVER_SEED, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x55000000)),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = 0.07f), shape),
        )
    }
}

/**
 * 歌曲行三点菜单的动作集合（2026-11-16 用户需求）：
 * 「加入歌单」点一次就变「已加歌单」、「移动到其它歌单」、「移除曲库」，每项都带图标。
 *
 * 为什么用数据类而不是一堆可选参数：调用点有三处（曲库、歌单详情、收藏），
 * 各自需要的动作不一样，传一个对象比传 8 个 null 默认参数清爽。
 */
data class TrackMenuActions(
    /** 可选歌单 (id to 名称)，用于「加入歌单」的选择弹窗。 */
    val playlists: List<Pair<Long, String>> = emptyList(),
    /** 该曲已在哪些歌单里，决定菜单显示「已加歌单」。 */
    val playlistIdsContaining: Set<Long> = emptySet(),
    val inFavorites: Boolean = false,
    /**
     * 是否显示「收藏 / 取消收藏」这一项。
     *
     * 2026-11-16 晚用户指令：所有歌曲三点菜单里的「收藏」删掉，**只有「收藏歌曲」
     * 这个类目除外**（在收藏虚拟歌单里仍要能取消收藏）。收藏入口改由播放页爱心承担。
     */
    val showFavoriteItem: Boolean = false,
    val onAddToPlaylist: ((Long) -> Unit)? = null,
    val onRemoveFromPlaylist: (() -> Unit)? = null,
    val onRemoveFromLibrary: (() -> Unit)? = null,
    val onToggleFavorite: (() -> Unit)? = null,
    /** 云端曲目才有「下载歌曲」（菜单第一项）；给 null 就不显示这一项。 */
    val onDownload: (() -> Unit)? = null,
)

/**
 * 歌曲行：48dp 封面 + 标题 + 艺术家 · 专辑 + 时长。
 *
 * 当前播放行不是只把字变蓝：整行有一个内缩的玻璃底 + 左侧细竖条，
 * 让「正在播哪首」在一屏 8 行里也能一眼扫到。
 */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCover: Boolean = true,
    onRemove: (() -> Unit)? = null,
    menu: TrackMenuActions = TrackMenuActions(),
    /** 歌单内拖动排序：给了这个就显示拖动手柄（按住手柄整行跟手）。 */
    dragHandle: (@Composable () -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var confirmRemoveLibrary by remember { mutableStateOf(false) }
    val picker = remember { TrackPickerState() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dim.rowHeight)
                .clip(RoundedCornerShape(17.dp))
                .background(
                    if (isCurrent) {
                        Brush.linearGradient(
                            colors = listOf(Palette.Blue.copy(alpha = 0.22f), Color.Transparent),
                        )
                    } else {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    },
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCurrent) {
                Box(
                    Modifier
                        .width(2.5.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                        .background(Palette.BlueLight),
                )
                Spacer(Modifier.width(8.dp))
            }
            if (showCover) {
                CoverThumb(
                    coverUri = track.coverUri,
                    size = Dim.coverSmall,
                    shape = Shapes.coverSmall,
                    seed = track.artSeed(),
                )
                Spacer(Modifier.width(13.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) Palette.BlueLight else Palette.TextPrimary,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (isCurrent && isPlaying) {
                PlayingBars()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = track.durationMs.toDurationLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextTertiary,
            )
            dragHandle?.let { handle ->
                Spacer(Modifier.width(2.dp))
                handle()
            }
            // 2026-11-16 用户指令「所有歌曲三点弹窗都要和三点在一块」：
            // 原来 `DropdownMenu` 挂在整行的 Box 里 → 锚点是整行左边缘，弹窗贴着
            // 行首弹出（离右侧三点很远）。这里把菜单挪进三点的 Box 里共用锚点。
            Box(Modifier.size(30.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = Palette.TextTertiary.copy(alpha = 0.75f),
                        modifier = Modifier.size(17.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Palette.Surface2,
                    shape = Shapes.cardSmall,
                ) {
                    // 2026-11-16 晚用户指令「三点菜单 下载歌曲位置移动到第一」：
                    // 云端曲目把它提到最上面，其余项顺序不动。
                    if (track.source != com.msop.player.core.model.SourceType.LOCAL) {
                        menu.onDownload?.let { download ->
                            DropdownMenuItem(
                                text = { Text("下载歌曲") },
                                leadingIcon = {
                                    Icon(PlayerIcons.Download, null, tint = Palette.TextSecondary)
                                },
                                onClick = {
                                    menuOpen = false
                                    download()
                                },
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text("歌曲信息") },
                        leadingIcon = {
                            Icon(Icons.Filled.Info, null, tint = Palette.TextSecondary)
                        },
                        onClick = {
                            menuOpen = false
                            infoOpen = true
                        },
                    )
                    if (menu.showFavoriteItem) {
                        menu.onToggleFavorite?.let { toggle ->
                            DropdownMenuItem(
                                text = { Text(if (menu.inFavorites) "取消收藏" else "收藏") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (menu.inFavorites) {
                                            Icons.Filled.Favorite
                                        } else {
                                            Icons.Filled.FavoriteBorder
                                        },
                                        contentDescription = null,
                                        tint = if (menu.inFavorites) Palette.Red else Palette.TextSecondary,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    toggle()
                                    // 菜单里的状态跟着收藏状态变，下次打开即为「取消收藏」
                                },
                            )
                        }
                    }
                    // 「加入歌单」：已经加过就显示「已加歌单」，点它仍可加到别的歌单。
                    // 一个歌单都没有时也保留这一项——点开弹窗会提示先新建，而不是从菜单里消失
                    menu.onAddToPlaylist?.let { add ->
                        DropdownMenuItem(
                            text = { Text(if (menu.playlistIdsContaining.isEmpty()) "加入歌单" else "已加歌单") },
                            leadingIcon = {
                                // 2026-11-16 晚：圆圈加号（与「移除曲库」的圆圈减号成对）
                                Icon(
                                    imageVector = if (menu.playlistIdsContaining.isEmpty()) {
                                        PlayerIcons.PlaylistAddCircle
                                    } else {
                                        Icons.Filled.Check
                                    },
                                    contentDescription = null,
                                    tint = if (menu.playlistIdsContaining.isEmpty()) {
                                        Palette.TextSecondary
                                    } else {
                                        Palette.BlueLight
                                    },
                                )
                            },
                            onClick = {
                                menuOpen = false
                                picker.open(add)
                            },
                        )
                    }
                    // 2026-11-16 晚用户指令「歌单 移动和加歌单功能重复，删掉移动」：
                    // 「移动到其它歌单」整项移除（批量操作条里的「移动」保留，那是多选语义）。
                    (menu.onRemoveFromPlaylist ?: onRemove)?.let { remove ->
                        DropdownMenuItem(
                            text = { Text("从歌单移除", color = Palette.Red) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Palette.Red) },
                            onClick = {
                                menuOpen = false
                                remove()
                            },
                        )
                    }
                    // 「移除曲库」放在下载之后（下载项已上移到菜单第一）
                    menu.onRemoveFromLibrary?.let { remove ->
                        DropdownMenuItem(
                            text = { Text("移除曲库", color = Palette.Red) },
                            leadingIcon = {
                                Icon(PlayerIcons.RemoveFromLibrary, null, tint = Palette.Red)
                            },
                            onClick = {
                                menuOpen = false
                                // 2026-11-16 晚用户需求「移除曲库增加二次确认」：
                                // 只从曲库隐藏，不删磁盘文件，且可在「已移除歌曲」里恢复。
                                confirmRemoveLibrary = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (infoOpen) {
        TrackInfoDialog(track = track, onDismiss = { infoOpen = false })
    }

    if (confirmRemoveLibrary) {
        val remove = menu.onRemoveFromLibrary
        AlertDialog(
            onDismissRequest = { confirmRemoveLibrary = false },
            containerColor = Palette.Surface2,
            title = { Text("从曲库移除？", color = Palette.TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "《${track.title}》会从曲库列表里消失，磁盘上的文件不会被删除。",
                        color = Palette.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "之后重新扫描也不会把它扫回来；想恢复就去曲库右上角菜单 →「已移除歌曲」。",
                        color = Palette.TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveLibrary = false
                        remove?.invoke()
                    },
                ) { Text("移除", color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveLibrary = false }) {
                    Text("取消", color = Palette.TextSecondary)
                }
            },
        )
    }

    if (picker.open) {
        val action = picker.action
        if (action != null) {
            PlaylistPickerDialog(
                title = "加入歌单",
                playlists = menu.playlists,
                excludeId = picker.excludeId,
                // 菜单里「已加歌单」的那个高亮成对勾，方便看出已经加过哪个
                checkedIds = menu.playlistIdsContaining,
                onPick = { id ->
                    action(id)
                    picker.dismiss()
                },
                onDismiss = { picker.dismiss() },
            )
        }
    }
}

/** 三点菜单里「加入歌单」用的弹窗状态。 */
private class TrackPickerState {
    var open by mutableStateOf(false)
    var action by mutableStateOf<((Long) -> Unit)?>(null)
    var excludeId by mutableStateOf<Long?>(null)

    fun open(a: (Long) -> Unit) {
        action = a
        open = true
    }

    fun dismiss() {
        open = false
        action = null
    }
}

/** 歌单选择弹窗：一列歌单，点一个就落库并关掉。 */
@Composable
private fun PlaylistPickerDialog(
    title: String,
    playlists: List<Pair<Long, String>>,
    excludeId: Long?,
    checkedIds: Set<Long>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .glassSurface(Shapes.card, Palette.Surface, elevation = 24.dp)
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )
            val choices = playlists.filter { it.first != excludeId }
            if (choices.isEmpty()) {
                Text(
                    text = "还没有其它歌单，先去新建一个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                )
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                ) {
                    items(choices, key = { it.first }) { (id, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(id) }
                                .padding(horizontal = 22.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Palette.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (id in checkedIds) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Palette.BlueLight,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 当前播放的三根音柱，做轻微呼吸动画。只渲染一行，开销可忽略。 */
@Composable
private fun PlayingBars() {
    val transition = rememberInfiniteTransition(label = "playingBars")
    val h1 by transition.animateFloat(
        initialValue = 5f,
        targetValue = 11f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "bar1",
    )
    val h2 by transition.animateFloat(
        initialValue = 11f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(430), RepeatMode.Reverse),
        label = "bar2",
    )
    val h3 by transition.animateFloat(
        initialValue = 7f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(610), RepeatMode.Reverse),
        label = "bar3",
    )
    Row(
        modifier = Modifier.height(12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        listOf(h1, h2, h3).forEach { h ->
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Palette.BlueLight),
            )
        }
    }
}

private fun Track.subtitle(): String {
    val artist = artist?.takeIf { it.isNotBlank() }
    val album = album?.takeIf { it.isNotBlank() }
    return when {
        artist != null && album != null -> "$artist · $album"
        artist != null -> artist
        album != null -> album
        else -> "未知艺术家"
    }
}

/** 生成封面用的稳定 seed。优先专辑，其次标题，最后 id。 */
fun Track.artSeed(): String = album?.takeIf { it.isNotBlank() }
    ?: title.takeIf { it.isNotBlank() }
    ?: id

/**
 * 歌曲信息弹窗。用 Dialog 而不是新页面，够用且不打断列表滚动位置。
 *
 * 2026-11-16 晚：从 `private` 放开——播放页顶栏菜单的「歌曲信息」也复用它，
 * 两处看到的信息必须完全一致。
 */
@Composable
fun TrackInfoDialog(track: Track, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .glassSurface(Shapes.card, Palette.Surface, elevation = 24.dp)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverThumb(
                    coverUri = track.coverUri,
                    size = 48.dp,
                    seed = track.artSeed(),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Palette.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when (track.source.name) {
                            "LOCAL" -> "本机文件"
                            "WEBDAV" -> "WebDAV 云端"
                            else -> "OpenList 云端"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            InfoLine("艺术家", track.artist ?: "未知")
            InfoLine("专辑", track.album ?: "未知")
            InfoLine("时长", track.durationMs.toDurationLabel())
            track.audioQualityLabel()?.let { InfoLine("音质", it) }
            track.mimeType?.let { InfoLine("格式", it) }
            track.sizeBytes?.let { InfoLine("大小", "%.1f MB".format(it / 1024.0 / 1024.0)) }
            track.localPath?.let { InfoLine("路径", it) }
            Text(
                text = "关闭",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.BlueLight,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(Shapes.pill)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextTertiary,
            modifier = Modifier.width(52.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 空状态：渐变唱盘 + 标题 + 说明，可选一个主按钮。 */
@Composable
fun EmptyHint(
    title: String,
    description: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Palette.Blue.copy(alpha = 0.42f), Palette.Surface2),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.03f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = PlayerIcons.Disc,
                    contentDescription = null,
                    tint = Palette.BlueLight,
                    modifier = Modifier.size(42.dp),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action?.let {
            Spacer(Modifier.height(22.dp))
            it()
        }
    }
}

/** 分组小标题，用于设置页与列表分区。 */
@Composable
fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextTertiary,
        modifier = Modifier.padding(start = Dim.screen, end = Dim.screen, top = 18.dp, bottom = 8.dp),
    )
}

/** 顶部大标题行：可选前置图标（如二级页返回键）+ 标题 + 可选副标题 + 右侧动作。 */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                // 带前置图标时收紧起始留白（图标自带视觉重量），与旧 PlaylistHeader 对齐。
                start = if (leading != null) 6.dp else Dim.screen,
                end = 10.dp,
                top = 10.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(2.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = Palette.TextPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        actions()
    }
}

/** 44dp 触摸目标的圆形图标按钮，全 App 统一的点击热区。 */
@Composable
fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = Palette.TextSecondary,
    container: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(Dim.touch)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
    }
}
