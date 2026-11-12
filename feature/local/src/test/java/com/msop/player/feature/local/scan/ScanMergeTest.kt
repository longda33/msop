package com.msop.player.feature.local.scan

import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanMergeTest {

    private fun track(
        title: String = "晴天",
        artist: String? = null,
        album: String? = null,
        albumId: String? = null,
        durationMs: Long = 0L,
        trackNo: Int? = null,
        year: Int? = null,
        coverUri: String? = null,
    ) = Track(
        id = "local:test",
        source = SourceType.LOCAL,
        uri = "content://media/external/audio/media/1",
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        trackNo = trackNo,
        year = year,
        coverUri = coverUri,
        localPath = "/storage/emulated/0/Music/晴天.flac",
    )

    @Test
    fun `库里没有这条记录时原样返回`() {
        val scanned = track()

        assertEquals(scanned, scanned.mergedWithExisting(null))
    }

    @Test
    fun `扫描结果为空缺时保留库里的封面`() {
        // 重扫时扫描器给的是 coverUri=null，不能把缓存路径抹掉
        val existing = track(coverUri = "file:///data/user/0/pkg/files/covers/abc.jpg")

        val merged = track().mergedWithExisting(existing)

        assertEquals("file:///data/user/0/pkg/files/covers/abc.jpg", merged.coverUri)
    }

    @Test
    fun `扫描结果为空缺时保留补出来的标签`() {
        // SAF 通道读不到标签，重扫不能把上次补的 artist/album 抹掉
        val existing = track(artist = "周杰伦", album = "叶惠美", durationMs = 269_000L, trackNo = 3)

        val merged = track().mergedWithExisting(existing)

        assertEquals("周杰伦", merged.artist)
        assertEquals("叶惠美", merged.album)
        assertEquals(269_000L, merged.durationMs)
        assertEquals(3, merged.trackNo)
    }

    @Test
    fun `扫描结果有值时以扫描结果为准`() {
        val existing = track(artist = "旧歌手", album = "旧专辑", durationMs = 1L)
        val scanned = track(artist = "新歌手", album = "新专辑", durationMs = 269_000L)

        val merged = scanned.mergedWithExisting(existing)

        assertEquals("新歌手", merged.artist)
        assertEquals("新专辑", merged.album)
        assertEquals(269_000L, merged.durationMs)
    }

    @Test
    fun `时长为零才回落到库里的值`() {
        val existing = track(durationMs = 269_000L)

        assertEquals(269_000L, track(durationMs = 0L).mergedWithExisting(existing).durationMs)
        assertEquals(1L, track(durationMs = 1L).mergedWithExisting(existing).durationMs)
    }

    @Test
    fun `库里本来就是空的不影响结果`() {
        val merged = track().mergedWithExisting(track())

        assertNull(merged.artist)
        assertNull(merged.coverUri)
        assertEquals(0L, merged.durationMs)
    }
}
