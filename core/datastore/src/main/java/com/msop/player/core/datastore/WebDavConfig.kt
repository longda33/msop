package com.msop.player.core.datastore

/**
 * WebDAV 第三音源配置（P7-07）。支持保存多条，同一时刻只有一条启用。
 *
 * [baseUrl] 是已规范化的服务器地址（`https://host[/子路径]`，不带尾斜杠）；
 * [rootPath] 是音乐库在服务器上的根目录（如 `/music`），同步只从这里往下走。
 * 密码只存本机，不进备份。
 *
 * [id] 与 [name] 的含义同 [ServerConfig]：id 由 Store 生成，name 是显示名。
 */
data class WebDavConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    val rootPath: String = "/",
    val allowPlainHttp: Boolean = false,
    /** 本地列表里的稳定 id；新建配置时留空，由 Store 分配。 */
    val id: String = "",
    /** 显示名，留空时 Store 用主机名兜底。 */
    val name: String = "",
    /** 请求并发数，0 表示用默认值；语义同 [ServerConfig.concurrency]。 */
    val concurrency: Int = 0,
) {
    val configured: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /** 实际生效的并发数（0 → 默认 4，并钳到 1..16）。 */
    val effectiveConcurrency: Int
        get() = concurrency.takeIf { it > 0 }?.coerceIn(1, MAX_CONCURRENCY) ?: DEFAULT_CONCURRENCY

    /** 列表里显示的一行标题：优先用户填的名字，否则退到主机名。 */
    val displayName: String
        get() = name.ifBlank { hostOf(baseUrl) }.ifBlank { "WebDAV 服务器" }

    /**
     * 音乐目录（2026-11-17 深夜用户需求「路径支持填写多条，一行一条」）。
     *
     * [rootPath] 允许换行写多个目录；这里按行切开、逐行归一化成「以 `/` 开头、不以 `/` 结尾」，
     * 空行丢掉、重复去掉；一行都没写等于根目录 `/`。
     */
    val rootPaths: List<String>
        get() = splitRootPaths(rootPath, default = "/")

    /** 多目录时，库里的路径改成「相对服务器根」的绝对路径，所以换算 URL 的根必须是 `/`。 */
    val effectiveRootPath: String
        get() = if (rootPaths.size > 1) "/" else rootPaths.first()
}

/**
 * 把设置页里填的「路径」文本（可多行）切成归一化的目录列表。
 *
 * 与 `WebDavUrl.normalizePath` 同口径（core:datastore 不能依赖 core:network，所以规则手写一份）：
 * 去空白、`\` → `/`、折叠重复斜杠、补前导 `/`、去尾 `/`；空串与 `/` 都算根目录。
 */
internal fun splitRootPaths(raw: String, default: String): List<String> {
    val paths = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val collapsed = line.replace('\\', '/').replace(Regex("/{2,}"), "/")
            val withRoot = if (collapsed.startsWith('/')) collapsed else "/$collapsed"
            if (withRoot.length > 1) withRoot.trimEnd('/').ifBlank { "/" } else "/"
        }
        .distinct()
        .toList()
    return paths.ifEmpty { listOf(default) }
}
