package com.primuse.lite.core.database.mapper

import com.primuse.lite.core.model.SourceType
import com.primuse.lite.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 纯 JVM 单测，不依赖 Android 运行时，也不需要设备。
 * 覆盖 `Track -> TrackEntity -> Track` 的往返一致性——字段漏映射是最容易犯又最难发现的错。
 */
class TrackMapperTest {

    private fun sampleLocal() = Track(
        id = "local:abc123",
        source = SourceType.LOCAL,
        uri = "content://media/external/audio/media/42",
        title = "晴天",
        artist = "周杰伦",
        album = "叶惠美",
        albumId = null,
        durationMs = 269_000L,
        trackNo = 3,
        discNo = 1,
        year = 2003,
        coverUri = "/data/cache/covers/abc123.jpg",
        sizeBytes = 9_400_000L,
        mimeType = "audio/flac",
        localPath = "/storage/emulated/0/Music/周杰伦/晴天.flac",
        lastModified = 1_700_000_000L,
        addedAt = 1_700_000_100L,
    )

    private fun sampleRemote() = Track(
        id = "subsonic:9981",
        source = SourceType.OPENLIST,
        uri = "https://music.example.com/openlist/rest/stream.view?id=9981",
        title = "平凡之路",
        artist = "朴树",
        album = "猎户星座",
        albumId = "al-77",
        durationMs = 303_000L,
        coverUri = "https://music.example.com/openlist/rest/getCoverArt.view?id=al-77",
    )

    @Test
    fun `本地曲目往返转换后字段完全一致`() {
        val original = sampleLocal()
        assertEquals(original, original.toEntity().toTrack())
    }

    @Test
    fun `远程曲目往返转换后字段完全一致`() {
        val original = sampleRemote()
        assertEquals(original, original.toEntity().toTrack())
    }

    @Test
    fun `来源类型不会被写错`() {
        assertEquals(SourceType.LOCAL, sampleLocal().toEntity().toTrack().source)
        assertEquals(SourceType.OPENLIST, sampleRemote().toEntity().toTrack().source)
    }

    @Test
    fun `可空字段为空时也能正确往返`() {
        val minimal = Track(
            id = "local:min",
            source = SourceType.LOCAL,
            uri = "content://media/external/audio/media/1",
            title = "无标签曲目",
        )
        assertEquals(minimal, minimal.toEntity().toTrack())
    }
}
