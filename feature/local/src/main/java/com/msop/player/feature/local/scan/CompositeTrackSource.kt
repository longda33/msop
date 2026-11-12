package com.msop.player.feature.local.scan

import com.msop.player.core.model.Track

/**
 * 把多个来源串成一条扫描流水线。
 *
 * **顺序有语义**：后写入者覆盖前者（按 `Track.id` upsert）。所以把**元数据更丰富**的
 * 来源放在最后，否则会退化成字段更少的版本。
 *
 * 本项目的顺序是 SAF 在前、MediaStore 在后：同一个文件若两边都能看到，
 * 最终留下的是 MediaStore 那份（有 album / duration），而不是 SAF 那份（只有路径）。
 * 只在 SAF 里出现的文件不受影响。
 *
 * 去重靠 `Track.id` 一致（都基于绝对路径哈希），见 P1-06。
 */
class CompositeTrackSource(
    private val sources: List<TrackPageSource>,
) : TrackPageSource {

    override suspend fun scan(
        since: Long,
        pageSize: Int,
        onPage: suspend (List<Track>) -> Unit,
    ): Int {
        var total = 0
        for (source in sources) {
            total += source.scan(since, pageSize, onPage)
        }
        return total
    }
}
