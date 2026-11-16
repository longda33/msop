package com.msop.player.feature.player

import android.content.Context
import com.msop.player.core.common.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放快照的落盘（P7-02）。
 *
 * 为什么用 SharedPreferences 而不是 Room/DataStore：
 * - `PlaybackController` 在播放事件回调里保存，需要同步、低开销；
 * - 快照只有一条、结构固定，不值得建表；
 * - DataStore 的挂起 API 会把保存挤到协程里，进程被强停时容易丢最后一次。
 */
@Singleton
class PlaybackSnapshotStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): PlaybackSnapshot? {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { json.decodeFromString<PlaybackSnapshot>(raw) }
            .onFailure { AppLog.w("播放快照解析失败，按无快照处理", it) }
            .getOrNull()
    }

    fun save(snapshot: PlaybackSnapshot) {
        runCatching {
            prefs.edit()
                .putString(KEY_SNAPSHOT, json.encodeToString(PlaybackSnapshot.serializer(), snapshot))
                .apply()
        }.onFailure { AppLog.w("播放快照写入失败", it) }
    }

    fun clear() {
        prefs.edit().remove(KEY_SNAPSHOT).apply()
    }

    private companion object {
        const val PREFS_NAME = "msop_playback_snapshot"
        const val KEY_SNAPSHOT = "snapshot_v1"
    }
}
