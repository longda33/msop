package com.msop.player.core.network

/**
 * 把远程曲目的**稳定内部 URI**（`openlist:/路径`、`webdav:/路径`）换成当前有效的播放地址。
 *
 * 定义在 core 层是为了让 `feature:player` 能注入它，而不需要反向依赖
 * `feature:remote`——AGENTS.md 规定 feature 之间不互相依赖。
 *
 * 实现必须是**同步**的：`ResolvingDataSource.Resolver` 在 DataSource 线程上调用，
 * 那里可以用 OkHttp 的 `execute()`，不能用挂起函数。
 */
interface StreamUrlResolver {
    fun resolve(internalUri: String): String

    /**
     * 播放该 URI 需要附加的请求头。OpenList 的直链自带签名，返回空；
     * WebDAV 用 Basic Auth，由这里下发。
     */
    fun headers(internalUri: String): Map<String, String> = emptyMap()
}
