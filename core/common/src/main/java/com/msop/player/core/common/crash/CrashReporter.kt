package com.msop.player.core.common.crash

import android.os.Process
import com.msop.player.core.common.log.AppLog
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * 全局未捕获异常记录。
 *
 * 只做「记录 + 交回系统默认处理」，不做重启、不吞异常——自用工具没必要搞复杂的崩溃恢复，
 * 但崩溃现场必须留在 logcat 里，否则真机排查无从下手。
 */
object CrashReporter {

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stack = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }.toString()
                AppLog.e("未捕获异常 thread=${thread.name}\n$stack", throwable)
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
