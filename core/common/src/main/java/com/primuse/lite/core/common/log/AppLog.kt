package com.primuse.lite.core.common.log

import android.util.Log

/**
 * 统一日志入口。
 *
 * 约定：
 * - TAG 固定为 `PrimuseLite`，方便 `adb logcat -s PrimuseLite:V` 一次性过滤
 * - 业务代码不要直接调 `android.util.Log`
 * - 后续接入文件日志时只改本文件，调用方不受影响
 */
object AppLog {

    const val TAG: String = "PrimuseLite"

    fun v(message: String) {
        Log.v(TAG, message)
    }

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable == null) Log.w(TAG, message) else Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable == null) Log.e(TAG, message) else Log.e(TAG, message, throwable)
    }
}
