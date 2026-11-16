package com.msop.player.core.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按 scheme 把播放地址解析分发给 OpenList / WebDAV（P7-07）。
 *
 * 单独抽一层而不是让两个客户端互相认识：`feature:player` 只依赖
 * [StreamUrlResolver]，新增音源时只改这里，播放层一行不用动。
 */
@Singleton
class CompositeStreamUrlResolver @Inject constructor(
    private val openList: OpenListClient,
    private val webDav: WebDavClient,
) : StreamUrlResolver {

    override fun resolve(internalUri: String): String =
        when {
            internalUri.startsWith(WebDavUrl.SCHEME) -> webDav.resolve(internalUri)
            internalUri.startsWith(ServerUrl.SCHEME) -> openList.resolve(internalUri)
            else -> internalUri
        }

    override fun headers(internalUri: String): Map<String, String> =
        when {
            internalUri.startsWith(WebDavUrl.SCHEME) -> webDav.headers(internalUri)
            else -> emptyMap()
        }
}
