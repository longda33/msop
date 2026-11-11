package com.primuse.lite.feature.local.metadata

import com.primuse.lite.core.common.log.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 封面磁盘缓存。
 *
 * **按内容哈希命名**，不按曲目 id：一张专辑的 12 首歌内嵌的是同一张图，按曲目命名
 * 会存 12 份；按内容命名天然合并成一份，跨专辑重复的图也能复用。
 *
 * 写入用「临时文件 + 重命名」：直接写目标文件时若进程被杀，会留下一个半截的图片，
 * 之后 Coil 读它只会得到解码失败，而且再也不会重试（文件存在就被当成已缓存）。
 */
class CoverCache(
    private val rootDir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** 已缓存则直接返回；否则落盘。空字节、超大图、写失败都返回 null。 */
    suspend fun ensure(bytes: ByteArray): File? = withContext(io) {
        if (bytes.isEmpty()) return@withContext null
        if (bytes.size > MAX_BYTES) {
            AppLog.w("封面过大，跳过缓存 size=${bytes.size}")
            return@withContext null
        }

        val target = File(rootDir, "${sha1(bytes)}.jpg")
        if (target.isFile && target.length() == bytes.size.toLong()) return@withContext target

        runCatching {
            rootDir.mkdirs()
            val temp = File(rootDir, "${target.name}.tmp")
            temp.writeBytes(bytes)
            // renameTo 在同一分区上是原子的；失败时退回「删了再重命名」
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) {
                    temp.delete()
                    return@withContext null
                }
            }
            target
        }.getOrElse { throwable ->
            AppLog.w("封面写盘失败 dir=${rootDir.absolutePath} 原因=${throwable.message}")
            null
        }
    }

    /** 校验数据库里的 `coverUri` 是否还指向一个存在的文件（缓存被清理过就会失效）。 */
    fun existing(coverUri: String?): File? {
        val path = coverUri?.removePrefix(FILE_SCHEME) ?: return null
        if (path.isEmpty()) return null
        val file = File(path)
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    suspend fun count(): Int = withContext(io) {
        rootDir.listFiles()?.count { it.isFile && !it.name.endsWith(TEMP_SUFFIX) } ?: 0
    }

    suspend fun sizeBytes(): Long = withContext(io) {
        rootDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    }

    /** 清空缓存。返回删掉的文件数。 */
    suspend fun clear(): Int = withContext(io) {
        val files = rootDir.listFiles() ?: return@withContext 0
        var removed = 0
        for (file in files) {
            if (file.isFile && file.delete()) removed++
        }
        AppLog.i("封面缓存已清空 删除=$removed")
        removed
    }

    /**
     * 删掉不再被任何曲目引用的封面。
     *
     * 封面按内容哈希命名、多首曲目共用一份，所以不能「删曲目时顺手删封面」——
     * 会误删同专辑其他歌还在用的那张。只能反查引用集合做差集。
     *
     * 调用时机：全量扫描之后（那是唯一会删除曲目的地方）。增量扫描不删曲目，
     * 也就不可能产生新的孤儿文件，跑了也是白跑。
     *
     * @param referencedUris 数据库里所有非空的 `coverUri`
     */
    suspend fun sweep(referencedUris: Set<String>): Int = withContext(io) {
        val files = rootDir.listFiles() ?: return@withContext 0

        // 只保留文件名：同一个文件可能被存成 file:// 形式，也可能被存成裸路径
        val keep = referencedUris.mapNotNull { existing(it)?.name }.toHashSet()

        var removed = 0
        for (file in files) {
            if (!file.isFile) continue
            val removable = file.name.endsWith(TEMP_SUFFIX) || file.name !in keep
            if (removable && file.delete()) removed++
        }
        if (removed > 0) AppLog.i("清理孤立封面 $removed 张")
        removed
    }

    private fun sha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        val out = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            if (value < 0x10) out.append('0')
            out.append(Integer.toHexString(value))
        }
        return out.toString()
    }

    companion object {
        const val DIR_NAME: String = "covers"
        const val FILE_SCHEME: String = "file://"
        private const val TEMP_SUFFIX = ".tmp"

        /** 内嵌封面超过 8MB 基本是用户把整本画册塞进了 ID3，缓存它没意义。 */
        private const val MAX_BYTES = 8 * 1024 * 1024
    }
}

/** 缓存文件的 URI 形式，直接可以喂给 Coil。 */
fun File.toCoverUri(): String = CoverCache.FILE_SCHEME + absolutePath
