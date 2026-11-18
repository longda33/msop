package com.msop.player.core.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 云端内嵌封面解析（2026-11-17）。只用内存里的字节，不发网络请求。 */
class RemoteCoverProbeTest {

    @Test
    fun parsesId3v23Apic() {
        val image = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 3)
        val data = buildBytes {
            add(0) // ISO-8859-1
            addAll(bytes("image/jpeg"))
            add(0)
            add(3) // front cover
            addAll(bytes("front"))
            add(0)
            addAll(image.toList())
        }
        val frame = buildBytes {
            addAll(bytes("APIC"))
            addAll(be32(data.size).toList())
            addAll(listOf(0.toByte(), 0.toByte())) // flags
            addAll(data.toList())
        }
        val bytes = buildBytes {
            addAll(bytes("ID3"))
            add(3); add(0); add(0)
            addAll(synchsafe(frame.size).toList())
            addAll(frame.toList())
        }

        assertArrayEquals(image, RemoteCoverProbe.id3Picture(bytes))
        assertEquals(bytes.size.toLong(), RemoteCoverProbe.id3TagSize(bytes))
    }

    @Test
    fun parsesFlacPictureBlock() {
        val image = byteArrayOf(1, 2, 3, 4, 5)
        val block = buildBytes {
            addAll(be32(3).toList()) // picture type: cover front
            addAll(be32(10).toList())
            addAll(bytes("image/jpeg"))
            addAll(be32(0).toList()) // description
            repeat(16) { add(0) } // width, height, bit depth, colors
            addAll(be32(image.size).toList())
            addAll(image.toList())
        }
        val bytes = buildBytes {
            addAll(bytes("fLaC"))
            add(0x86.toByte()) // last block + PICTURE
            addAll(be24(block.size).toList())
            addAll(block.toList())
        }

        assertArrayEquals(image, RemoteCoverProbe.flacPicture(bytes))
        assertEquals((8 + block.size).toLong(), RemoteCoverProbe.flacPictureEnd(bytes))
    }

    @Test
    fun parsesMp4CovrAtom() {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
        val child = buildBytes {
            addAll(be32(16 + image.size).toList())
            addAll(bytes("data"))
            repeat(8) { add(0) } // version/flags + locale
            addAll(image.toList())
        }
        val bytes = buildBytes {
            addAll(be32(8 + child.size).toList())
            addAll(bytes("covr"))
            addAll(child.toList())
        }

        assertArrayEquals(image, RemoteCoverProbe.mp4Picture(bytes))
    }

    @Test
    fun imageExtensionChecksMagicBytes() {
        assertEquals("jpg", RemoteCoverProbe.imageExtension(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())))
        assertEquals(
            "png",
            RemoteCoverProbe.imageExtension(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)),
        )
        assertNull(RemoteCoverProbe.imageExtension(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun noPictureReturnsNull() {
        assertNull(RemoteCoverProbe.id3Picture("not id3".toByteArray()))
        assertNull(RemoteCoverProbe.flacPicture("not flac".toByteArray()))
        assertNull(RemoteCoverProbe.mp4Picture("no atoms here".toByteArray()))
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
