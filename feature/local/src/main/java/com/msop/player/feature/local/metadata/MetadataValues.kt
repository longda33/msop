package com.msop.player.feature.local.metadata

/**
 * `MediaMetadataRetriever` 原始字符串的解析规则。
 *
 * 单独抽出来是为了能在纯 JVM 单测里验证——真正的 retriever 在单测里是 stub，
 * 而这里全是字符串处理，正是最容易写错的地方。
 *
 * 已知的脏数据形态：
 * - 缺失标签：`null`、空串、`<unknown>`
 * - 音轨号：`"3"` 或 `"3/12"`（后者是「第 3 首，共 12 首」）
 * - 年份：`"2021"`、`"2021-05-01"`、`"2021年"` 都见过
 */
object MetadataValues {

    /** 与 MediaStore 一样，`<unknown>` 必须转 null，否则 UI 会把它当歌手名显示。 */
    fun clean(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.equals(UNKNOWN, ignoreCase = true)) return null
        return trimmed
    }

    /** `"3/12"` → 3；`"0"` → null（0 不是合法音轨号，是「未设置」）。 */
    fun parseNumber(raw: String?): Int? {
        val value = clean(raw)?.substringBefore('/')?.trim()?.toIntOrNull() ?: return null
        return value.takeIf { it > 0 }
    }

    /** `"2021-05-01"` → 2021；`"0"` → null。 */
    fun parseYear(raw: String?): Int? {
        val value = clean(raw)?.take(4)?.toIntOrNull() ?: return null
        // 1900 年之前基本是脏数据（或解析到了别的字段）
        return value.takeIf { it in 1000..2999 }
    }

    /** `"269000"` → 269000；负数与 0 都当没有。 */
    fun parseDuration(raw: String?): Long {
        val value = clean(raw)?.toLongOrNull() ?: return 0L
        return value.takeIf { it > 0L } ?: 0L
    }

    private const val UNKNOWN = "<unknown>"
}
