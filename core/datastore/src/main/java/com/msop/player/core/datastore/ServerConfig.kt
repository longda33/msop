package com.msop.player.core.datastore

/**
 * OpenList 服务器连接配置。
 *
 * [baseUrl] 必须是**已规范化**的形式：`https://host[/子路径]`，不带尾斜杠，
 * 规范化由 `ServerUrl.normalize()` 在保存时做一次，不要每次请求都算。
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
) {
    val configured: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}
