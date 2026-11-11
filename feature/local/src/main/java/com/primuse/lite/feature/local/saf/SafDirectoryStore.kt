package com.primuse.lite.feature.local.saf

import android.content.Context
import com.primuse.lite.core.common.log.AppLog

/**
 * 用户通过 SAF 添加的音乐目录。
 *
 * 存的是 `content://...tree/...` 形式的 tree URI 字符串。这些 URI 在
 * `takePersistableUriPermission` 之后可以跨重启使用，所以只需要持久化字符串。
 */
interface SafDirectoryStore {
    fun treeUris(): List<String>
    fun add(treeUri: String)
    fun remove(treeUri: String)
    fun clear()
}

class PrefsSafDirectoryStore(context: Context) : SafDirectoryStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun treeUris(): List<String> =
        prefs.getStringSet(KEY_TREES, emptySet()).orEmpty().sorted()

    override fun add(treeUri: String) {
        val current = prefs.getStringSet(KEY_TREES, emptySet()).orEmpty().toMutableSet()
        if (current.add(treeUri)) {
            prefs.edit().putStringSet(KEY_TREES, current).apply()
            AppLog.i("已添加 SAF 目录：$treeUri")
        }
    }

    override fun remove(treeUri: String) {
        val current = prefs.getStringSet(KEY_TREES, emptySet()).orEmpty().toMutableSet()
        if (current.remove(treeUri)) {
            prefs.edit().putStringSet(KEY_TREES, current).apply()
            AppLog.i("已移除 SAF 目录：$treeUri")
        }
    }

    override fun clear() {
        prefs.edit().remove(KEY_TREES).apply()
    }

    private companion object {
        const val PREFS_NAME = "primuse_saf_dirs"
        const val KEY_TREES = "tree_uris"
    }
}
