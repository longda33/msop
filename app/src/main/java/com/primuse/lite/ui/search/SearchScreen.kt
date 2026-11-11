package com.primuse.lite.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primuse.lite.ui.components.EmptyHint
import com.primuse.lite.ui.components.ScreenTitle
import com.primuse.lite.ui.components.TrackRow
import com.primuse.lite.ui.library.LibraryViewModel
import com.primuse.lite.ui.theme.Dim
import com.primuse.lite.ui.theme.Palette
import com.primuse.lite.ui.theme.Shapes

/**
 * 搜索。本地曲库在前端过滤——5000 首以内一次 filter 是毫秒级，
 * 不值得为它建索引表。远程源接进来后（P3-03）再改成双路合并。
 */
@Composable
fun SearchScreen(vm: LibraryViewModel) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val results = remember(tracks, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            tracks.filter { track ->
                track.title.contains(q, ignoreCase = true) ||
                    track.artist?.contains(q, ignoreCase = true) == true ||
                    track.album?.contains(q, ignoreCase = true) == true
            }.take(80)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title = "搜索")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dim.screen, vertical = 6.dp)
                .height(46.dp)
                .clip(Shapes.pill)
                .background(Palette.Surface3)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = Palette.TextTertiary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索歌曲、专辑、艺术家",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextTertiary,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Palette.TextPrimary,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = SolidColor(Palette.BlueLight),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = Palette.TextTertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { query = "" },
                )
            }
        }

        when {
            query.isBlank() -> EmptyHint(
                title = "搜点什么",
                description = "输入歌名、艺术家或专辑名。\n本机与云端的结果会合并显示。",
            )
            results.isEmpty() -> EmptyHint(
                title = "没有找到",
                description = "换个关键词试试，或者确认曲库已经扫描完成。",
            )
            else -> {
                Text(
                    text = "找到 ${results.size} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    modifier = Modifier.padding(start = Dim.screen, top = 12.dp, bottom = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    items(items = results, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = track.id == playback.currentTrackId,
                            isPlaying = playback.isPlaying,
                            onClick = { vm.playTrack(track, results) },
                        )
                    }
                }
            }
        }
    }
}
