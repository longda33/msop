package com.primuse.lite.feature.local.metadata

import com.primuse.lite.core.model.Track

/**
 * 按需取封面：先看缓存，没有再回文件里抠。
 *
 * 为什么不扫描时一次性给所有曲目提封面：1000 首各开一次 `MediaMetadataRetriever`
 * 要几十秒，而用户可能只看前 20 行。封面属于「用到才做」的工作，
 * 补标签（时长/歌手/专辑）才是列表必须先有的数据。
 *
 * 拿到封面后会回写数据库，下次直接命中 `coverUri`，不再读文件。
 */
class CoverProvider(
    private val store: MetadataStore,
    private val reader: MetadataReader,
    private val cache: CoverCache,
) {

    suspend fun coverUri(track: Track): String? {
        // 数据库里的路径可能已被「清理缓存」删掉，所以要校验文件还在
        cache.existing(track.coverUri)?.let { return it.toCoverUri() }

        val bytes = reader.readCover(track.uri) ?: return null
        val file = cache.ensure(bytes) ?: return null
        val uri = file.toCoverUri()

        if (track.coverUri != uri) {
            store.upsertAll(listOf(track.copy(coverUri = uri)))
        }
        return uri
    }
}
