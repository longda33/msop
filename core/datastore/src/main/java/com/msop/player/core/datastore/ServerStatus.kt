package com.msop.player.core.datastore

/**
 * 单条服务器配置的最近一次连接结果，供设置页列表显示状态。
 *
 * [message] 是给用户看的中文短句（成功时是数量摘要，失败时是错误原因），
 * [atMillis] 是这次测试的时间，0 表示从未测过。
 */
data class ServerStatusInfo(
    val status: ServerStatus = ServerStatus.UNTESTED,
    val message: String = "",
    val atMillis: Long = 0L,
) {
    val tested: Boolean get() = atMillis > 0L
}

enum class ServerStatus {
    /** 从没测过，或配置刚被改动（改动后状态一律回落）。 */
    UNTESTED,

    /** 上次测试连接成功。 */
    OK,

    /** 上次测试连接失败，[ServerStatusInfo.message] 里是原因。 */
    FAIL,
}
