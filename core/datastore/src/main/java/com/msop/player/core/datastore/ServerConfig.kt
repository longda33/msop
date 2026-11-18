package com.msop.player.core.datastore

/**
 * OpenList 服务器连接配置。
 *
 * [baseUrl] 必须是**已规范化**的形式：`https://host[/子路径]`，不带尾斜杠，
 * 规范化由 `ServerUrl.normalize()` 在保存时做一次，不要每次请求都算。
 *
 * [id] 是配置在本地列表里的稳定标识（`p1`、`p2`……），由 [ServerConfigStore]
 * 生成；[name] 是用户可见的显示名，留空时由 Store 用主机名补一个。
 * 同一时刻只有**一条**配置处于启用状态（[ServerConfigStore.activeId]），
 * 同步与流播都按启用项工作，因此曲目 id 仍可沿用 `openlist:{sha1(path)}`。
 */
data class ServerConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    /** 用户明确二次确认过明文传输才为 true；默认 false。 */
    val allowPlainHttp: Boolean = false,
    /**
     * OpenList「媒体应用密钥」（`olmp_` 开头），**可选**。
     *
     * 填了之后走 Subsonic 的 `apiKey` 鉴权补元数据（艺术家/专辑/真实封面），
     * 不填则只靠原生 REST 的目录信息（标题取文件名、专辑取父目录、无封面）。
     * 注意它**不是**登录密码：Subsonic 的 `u`+`t`/`p` 两种鉴权在这台服务器上都返回 40。
     */
    val appKey: String = "",
    /**
     * 要同步的音乐目录，**可选**，空串表示整个网盘根目录 `/`。
     *
     * 真实服务器（尤其是带备份盘、`od-*` 挂载点的 OpenList）根目录里常年混着
     * 与音乐无关、甚至列目录会 500 的目录，从 `/` 全盘 BFS 既慢又容易撞上坏目录，
     * 因此让用户直接指定音乐所在的那一层，例如 `/yd/music`、`/yd/musicweb`。
     */
    val rootPath: String = "",
    /** 本地列表里的稳定 id；新建配置时留空，由 Store 分配。 */
    val id: String = "",
    /** 显示名，留空时 Store 用主机名兜底。 */
    val name: String = "",
    /**
     * 请求并发数（2026-11-16 晚用户需求「openlist webdav 增加 请求并发数 输入框，不填使用默认」）。
     *
     * 0 表示用默认值（[DEFAULT_CONCURRENCY]），只影响目录 BFS 与时长探测的并行度：
     * 公网/小服务器给 2～3 更稳，内网 NAS 可以拉到 8～16 换速度。
     */
    val concurrency: Int = 0,
) {
    /** 实际生效的并发数（0 → 默认 4，并钳到 1..16）。 */
    val effectiveConcurrency: Int
        get() = concurrency.takeIf { it > 0 }?.coerceIn(1, MAX_CONCURRENCY) ?: DEFAULT_CONCURRENCY
    val configured: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /** 列表里显示的一行标题：优先用户填的名字，否则退到主机名。 */
    val displayName: String
        get() = name.ifBlank { hostOf(baseUrl) }.ifBlank { "OpenList 服务器" }

    /**
     * 音乐目录（2026-11-17 深夜用户需求「路径支持填写多条，一行一条」）。
     *
     * [rootPath] 允许换行写多个目录，见 [splitRootPaths]；一行都没写等于整个网盘根 `/`。
     */
    val rootPaths: List<String>
        get() = splitRootPaths(rootPath, default = "/")
}

/** 从规范化后的地址里取主机名，只用于显示，失败时返回空串。 */
internal fun hostOf(baseUrl: String): String {
    val withoutScheme = baseUrl.substringAfter("://", baseUrl)
    return withoutScheme.substringBefore('/').substringBefore('?')
}

/** 没填「请求并发数」时用的默认并行度。 */
internal const val DEFAULT_CONCURRENCY = 4

/** 并发数上限，避免用户填个 200 把服务器打挂。 */
internal const val MAX_CONCURRENCY = 16
