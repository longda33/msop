package com.msop.player.feature.local.scan

import java.security.MessageDigest

/**
 * 生成稳定、可重现的曲目 ID。
 *
 * 为什么用路径哈希而不是 MediaStore 的 `_ID`：
 * MediaStore 的 `_ID` 在媒体库重建、SD 卡重插、恢复出厂后会变，一旦变就意味着
 * 播放记录、歌单关联全部失联。绝对路径在文件不动的前提下是稳定的。
 */
object TrackIdFactory {

    private const val LOCAL_PREFIX = "local:"

    /** 归一化路径：统一分隔符、折叠重复斜杠、去首尾空白。**不做大小写转换**，避免在大小写敏感存储上误合并。 */
    fun normalizePath(rawPath: String): String =
        rawPath.trim()
            .replace('\\', '/')
            .replace(Regex("/{2,}"), "/")

    fun localId(rawPath: String): String = LOCAL_PREFIX + sha1(normalizePath(rawPath))

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            if (value < 0x10) out.append('0')
            out.append(Integer.toHexString(value))
        }
        return out.toString()
    }
}
