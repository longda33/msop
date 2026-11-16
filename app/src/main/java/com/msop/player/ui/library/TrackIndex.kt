package com.msop.player.ui.library

import com.msop.player.core.model.Track

/**
 * 曲目列表的 id→下标索引（P5-04）。
 *
 * 为什么需要它：列表点击原来写的是 `tracks.indexOf(track)`，在 5 万首上是
 * 5 万次 `equals`（`Track` 是 data class，逐字段比较）。点一首歌掉一帧。
 *
 * 索引按**列表引用**缓存：同一个列表对象重复查询是 O(1)；列表换了
 * （扫描入库、筛选切换、Tab 切换）才重建一次 O(n)。
 *
 * 抽成独立类而不是留在 ViewModel 里，是为了能在 JVM 单测里直接验证
 * 「缓存命中」与「列表换引用后失效」两条行为——ViewModel 带 Hilt/Android
 * 依赖，跑不了纯单测。
 */
class TrackIndex {

    private var cachedList: List<Track>? = null
    private var cachedMap: Map<String, Int> = emptyMap()

    /** 返回 [id] 在 [list] 中的下标，找不到返回 -1。 */
    fun indexOf(list: List<Track>, id: String): Int {
        val map = if (cachedList === list) {
            cachedMap
        } else {
            val built = HashMap<String, Int>(list.size * 2)
            list.forEachIndexed { i, track -> built[track.id] = i }
            cachedList = list
            cachedMap = built
            built
        }
        return map[id] ?: -1
    }

    /** 当前缓存是否正好对应这个列表（单测用，也便于排查）。 */
    fun isCachedFor(list: List<Track>): Boolean = cachedList === list
}
