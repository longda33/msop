package com.primuse.lite.feature.remote

import java.security.MessageDigest
import java.util.Locale

/**
 * 远程曲目的标识、路径与格式判定。纯 JVM，无 Android 依赖，方便单测。
 *
 * ID 用**服务器相对路径的 SHA-1**，不用 raw_url：raw_url 带临时签名会变，
 * 用它当主键会让歌单、队列、播放记录每次同步全部失联。
 */
object RemoteIdFactory {

    /** 与 AGENTS.md §3 硬约束一致：只认这几种常见格式。 */
    private val MIME_BY_EXT = mapOf(
        "mp3" to "audio/mpeg",
        "aac" to "audio/aac",
        "m4a" to "audio/mp4",
        "flac" to "audio/flac",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "opus" to "audio/ogg",
    )

    fun isAudio(name: String): Boolean = extension(name) in MIME_BY_EXT

    fun mimeType(name: String): String? = MIME_BY_EXT[extension(name)]

    fun displayTitle(name: String): String =
        name.substringBeforeLast('.', name).ifBlank { name }

    /** 统一成以 / 开头、无重复斜杠、无尾斜杠（根路径例外）的形式。 */
    fun normalizePath(path: String): String {
        val collapsed = path.replace(Regex("/{2,}"), "/")
        val withRoot = if (collapsed.startsWith('/')) collapsed else "/$collapsed"
        return if (withRoot.length > 1) withRoot.trimEnd('/') else "/"
    }

    /** 拼接服务器内的目录与文件名。这是**文件路径**不是 URL，不走 HttpUrl。 */
    fun join(directory: String, name: String): String {
        val parent = normalizePath(directory)
        return if (parent == "/") "/$name" else "$parent/$name"
    }

    fun parentPath(path: String): String {
        val normalized = normalizePath(path)
        val parent = normalized.substringBeforeLast('/', "")
        return parent.ifBlank { "/" }
    }

    fun parentName(path: String): String {
        val parent = parentPath(path)
        return if (parent == "/") "OpenList" else parent.substringAfterLast('/')
    }

    fun id(path: String): String = "openlist:" + sha1(normalizePath(path))

    fun albumId(path: String): String = "openlist:album:" + sha1(parentPath(path))

    private fun extension(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun sha1(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                append(((byte.toInt() shr 4) and 0xF).toString(16))
                append((byte.toInt() and 0xF).toString(16))
            }
        }
    }
}
