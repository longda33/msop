package com.msop.player.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 云端内嵌歌词解析（2026-11-17）。纯内存字节，不发网络请求。 */
class RemoteLyricsProbeTest {

    @Test
    fun parsesId3Uslt() {
        val text = "[00:00.00]第一句\n[00:03.00]第二句"
        val data = buildBytes {
            add(3) // UTF-8
            addAll(bytes("eng")) // language
            addAll(bytes("desc"))
            add(0)
            addAll(bytes(text))
        }
        val frame = buildBytes {
            addAll(bytes("USLT"))
            addAll(be32(data.size).toList())
            addAll(listOf(0.toByte(), 0.toByte()))
            addAll(data.toList())
        }
        val tag = buildBytes {
            addAll(bytes("ID3"))
            add(3); add(0); add(0)
            addAll(synchsafe(frame.size).toList())
            addAll(frame.toList())
        }

        assertEquals(text, RemoteLyricsProbe.id3Lyrics(tag))
    }

    @Test
    fun parsesFlacVorbisLyrics() {
        val text = "[00:01.00]FLAC 歌词"
        val comment = "LYRICS=$text"
        val block = buildBytes {
            addAll(be32(0).toList()) // vendor length
            addAll(be32(1).toList()) // one comment
            addAll(be32(comment.toByteArray().size).toList())
            addAll(bytes(comment))
        }
        val bytes = buildBytes {
            addAll(bytes("fLaC"))
            add(0x84.toByte()) // last block + Vorbis Comment
            addAll(be24(block.size).toList())
            addAll(block.toList())
        }

        assertEquals(text, RemoteLyricsProbe.flacLyrics(bytes))
        assertEquals((8 + block.size).toLong(), RemoteLyricsProbe.flacVorbisEnd(bytes))
    }

    @Test
    fun negativeVorbisCommentCountReturnsNullInsteadOfThrowing() {
        // 损坏/恶意的 Vorbis Comment 把 32 位 count 写成 0x80000000，
        // 旧代码 toInt() 后变负数，repeat() 抛异常被上层吞掉，只表现为歌词消失。
        val block = buildBytes {
            addAll(be32(0).toList()) // vendor length
            addAll(be32(Int.MIN_VALUE).toList()) // 无符号 0x80000000
        }
        val bytes = buildBytes {
            addAll(bytes("fLaC"))
            add(0x84.toByte())
            addAll(be24(block.size).toList())
            addAll(block.toList())
        }
        assertNull(RemoteLyricsProbe.flacLyrics(bytes))
    }

    @Test
    fun noLyricsReturnsNull() {
        assertNull(RemoteLyricsProbe.id3Lyrics("not id3".toByteArray()))
        assertNull(RemoteLyricsProbe.flacLyrics("not flac".toByteArray()))
        assertNull(RemoteLyricsProbe.mp4Lyrics("no atoms".toByteArray()))
    }

    private fun bytes(text: String): List<Byte> = text.toByteArray().toList()

    private fun buildBytes(block: MutableList<Byte>.() -> Unit): ByteArray =
        mutableListOf<Byte>().apply(block).toByteArray()

    private fun be32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun be24(value: Int): ByteArray = byteArrayOf(
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7f).toByte(),
        ((value ushr 14) and 0x7f).toByte(),
        ((value ushr 7) and 0x7f).toByte(),
        (value and 0x7f).toByte(),
    )
}
