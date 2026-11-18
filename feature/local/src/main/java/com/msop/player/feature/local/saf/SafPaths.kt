package com.msop.player.feature.local.saf

import android.net.Uri
import android.provider.DocumentsContract
import com.msop.player.feature.local.scan.TrackIdFactory

/**
 * 把 SAF 的 documentId 还原成绝对路径。
 *
 * documentId 形如：
 * - `primary:Music/周杰伦/晴天.flac`      → `/storage/emulated/0/Music/周杰伦/晴天.flac`
 * - `1A2B-3C4D:Music/a.flac`（SD 卡）    → `/storage/1A2B-3C4D/Music/a.flac`
 *
 * 为什么要还原：本地曲目 ID 基于绝对路径哈希，MediaStore 与 SAF 两条通道必须
 * 算出同一个 ID，才能去重（P1-06）。还原不出来的返回 null，调用方应跳过该文件。
 */
internal fun documentIdToAbsolutePath(documentId: String): String? {
    val separator = documentId.indexOf(':')
    if (separator <= 0 || separator == documentId.lastIndex) return null

    val volume = documentId.substring(0, separator)
    val relative = documentId.substring(separator + 1).trim('/')
    if (relative.isEmpty()) return null

    val root = if (volume.equals("primary", ignoreCase = true)) {
        "/storage/emulated/0"
    } else {
        "/storage/$volume"
    }
    return TrackIdFactory.normalizePath("$root/$relative")
}

/**
 * 设置里「添加音乐目录」那条 SAF tree URI 对应的**目录根**绝对路径。
 *
 * 2026-11-17（用户报「音乐库 文件夹 不要有那么多路径，显示设置界面填写的路径就行」）：
 * 文件夹 Tab 原来按曲目 `localPath` 的上级目录切分，`Music/周杰伦`、`Music/林俊杰`…
 * 每个子目录都变成一条。现在先拿每条 tree 还原成根路径，曲目按「所属的那个已授权目录」
 * 聚合，一个音乐目录一条。
 *
 * 与 [documentIdToAbsolutePath] 的区别：tree 的 documentId 可能就是卷根（`primary:`），
 * 这时返回 `/storage/emulated/0` 本身而不是 null。
 */
fun treeUriRootPath(treeUri: String): String? {
    val uri = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return null
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        ?: return null
    val separator = documentId.indexOf(':')
    if (separator <= 0) return null

    val volume = documentId.substring(0, separator)
    val relative = documentId.substring(separator + 1).trim('/')
    val root = if (volume.equals("primary", ignoreCase = true)) {
        "/storage/emulated/0"
    } else {
        "/storage/$volume"
    }
    return TrackIdFactory.normalizePath(
        if (relative.isEmpty()) root else "$root/$relative",
    )
}

/** 当前已授权的全部音乐目录根路径，**长的排前面**，方便前缀匹配一次命中最近的目录。 */
fun SafDirectoryStore.rootPaths(): List<String> =
    treeUris().mapNotNull { treeUriRootPath(it) }
        .distinct()
        .sortedByDescending { it.length }
