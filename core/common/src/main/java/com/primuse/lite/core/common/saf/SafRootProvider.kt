package com.primuse.lite.core.common.saf

/**
 * 用户已授权的 SAF 目录树。
 *
 * 放在 `core:common` 是为了让 `feature:lyrics` 能顺着目录树找同名的 `.lrc`，
 * 又不用依赖 `feature:local`——两个 feature 之间不允许互相依赖（AGENTS §5）。
 * 实现由 `:feature:local` 提供。
 */
fun interface SafRootProvider {
    /** `content://.../tree/...` 形式的 tree URI 字符串。 */
    fun treeUris(): List<String>
}
