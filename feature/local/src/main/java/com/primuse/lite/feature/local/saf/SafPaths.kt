package com.primuse.lite.feature.local.saf

import com.primuse.lite.feature.local.scan.TrackIdFactory

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
