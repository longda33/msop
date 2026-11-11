package com.primuse.lite.feature.local.metadata

import com.primuse.lite.core.model.SourceType
import com.primuse.lite.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataMergeTest {

    private fun track(
        title: String = "晴天",
        artist: String? = null,
        album: String? = null,
        durationMs: Long = 0L,
        path: String? = "/storage/emulated/0/Music/晴天.flac",
    ) = Track(
        id = "local:test",
        source = SourceType.LOCAL,
        uri = "content://media/external/audio/media/1",
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        localPath = path,
    )

    @Test
    fun `空缺字段被标签填上`() {
        val merged = track().mergedWith(
            TrackMetadata(
                artist = "周杰伦",
                album = "叶惠美",
                year = 2003,
                trackNo = 3,
                discNo = 1,
                durationMs = 269_000L,
            ),
        )

        assertEquals("周杰伦", merged.artist)
        assertEquals("叶惠美", merged.album)
        assertEquals(2003, merged.year)
        assertEquals(3, merged.trackNo)
        assertEquals(1, merged.discNo)
        assertEquals(269_000L, merged.durationMs)
    }

    @Test
    fun `已有值不被覆盖`() {
        // MediaStore 那份可能有用户手工整理过的专辑名，不能被文件里拼错的标签冲掉
        val merged = track(artist = "周杰伦", album = "叶惠美", durationMs = 269_000L)
            .mergedWith(TrackMetadata(artist = "Jay Chou", album = "叶惠美 (Deluxe)", durationMs = 1L))

        assertEquals("周杰伦", merged.artist)
        assertEquals("叶惠美", merged.album)
        assertEquals(269_000L, merged.durationMs)
    }

    @Test
    fun `文件名兜底的标题会被标签标题替换`() {
        // SAF 通道拿不到 title，用文件名顶上；标签里的才是真标题
        val merged = track(title = "01 - 晴天", path = "/storage/emulated/0/Music/01 - 晴天.flac")
            .mergedWith(TrackMetadata(title = "晴天"))

        assertEquals("晴天", merged.title)
    }

    @Test
    fun `标题不是文件名兜底时保持原样`() {
        val merged = track(title = "晴天（Live）", path = "/storage/emulated/0/Music/track01.flac")
            .mergedWith(TrackMetadata(title = "晴天"))

        assertEquals("晴天（Live）", merged.title)
    }

    @Test
    fun `标签全空时不改动曲目`() {
        val original = track()

        assertEquals(original, original.mergedWith(TrackMetadata()))
    }

    @Test
    fun `没有本地路径时不会误判文件名兜底`() {
        val merged = track(title = "晴天", path = null).mergedWith(TrackMetadata(title = "别的标题"))

        assertEquals("晴天", merged.title)
        assertNull(merged.artist)
    }
}
