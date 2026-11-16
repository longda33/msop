package com.msop.player.core.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「下载歌曲」的落地位置（2026-11-16 晚用户需求「设置界面增加自定义下载路径」）。
 *
 * 默认不填任何东西，直接落到应用自己的外部目录
 * `Android/data/com.msop.player/files/Music`——不需要任何权限、卸载即清理。
 * 用户可以在设置页选一个 SAF 目录（`ACTION_OPEN_DOCUMENT_TREE` 授权后持久化），
 * 之后下载就写进那个目录，卸载应用文件也还在。
 *
 * 为什么用 SharedPreferences 而不是 DataStore：下载是同步回调里读的短配置，
 * 和 `ServerConfigStore` / `WebDavConfigStore` 保持一致，不额外引一条异步链路。
 */
@Singleton
class DownloadConfigStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 用户选的 SAF 目录（`content://…/tree/…`），空串表示用默认目录。 */
    fun treeUri(): String? = prefs.getString(KEY_TREE_URI, null)?.takeIf { it.isNotBlank() }

    /** 用户给这个目录起/选出来的显示名，设置页那一行副标题用。 */
    fun displayName(): String? = prefs.getString(KEY_TREE_NAME, null)?.takeIf { it.isNotBlank() }

    fun saveTree(uri: String?, name: String?) {
        prefs.edit().apply {
            if (uri.isNullOrBlank()) remove(KEY_TREE_URI) else putString(KEY_TREE_URI, uri)
            if (name.isNullOrBlank()) remove(KEY_TREE_NAME) else putString(KEY_TREE_NAME, name)
        }.apply()
    }

    fun clearTree() = saveTree(null, null)

    private companion object {
        const val PREFS_NAME = "msop_download"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_TREE_NAME = "tree_name"

        /** 默认下载目录名（应用外部私有目录下的一级文件夹）。 */
        const val DEFAULT_DIR = "Music"
    }

    /** 默认目录的展示文案（写死在 store 里，UI 与下载器读同一个值）。 */
    val defaultDirName: String get() = DEFAULT_DIR
}
