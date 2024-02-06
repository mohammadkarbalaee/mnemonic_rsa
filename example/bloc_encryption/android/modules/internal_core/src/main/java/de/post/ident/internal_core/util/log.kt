package de.post.ident.internal_core.util

import android.util.Log
import de.post.ident.internal_core.BuildConfig

/**
 * Custom logger, can be called from anywhere and logs (if debuggable) with visibility debug (or error if throwable is supplied).
 *
 * Example usages:
 *   - log("log message")
 *   - log ("message", throwable)
 */

const val logFileName = "log.kt"

//fun log(error: Throwable? = null, msg: () -> String) {
//    log(msg(), error)
//}

fun log(msg: String, error: Throwable? = null) {
    if (BuildConfig.DEBUG.not()) return // only log in debug builds!

    val ct = Thread.currentThread()
    val tagName = ct.name
    val traces = ct.stackTrace
    val lineCount = traces.size - 1
    val stackTrace = traces.slice(3..lineCount).find { it.fileName != logFileName }
    val message = if (stackTrace != null) {
        val cname = stackTrace.className.substringAfterLast(".")
        "[${stackTrace.fileName}:${stackTrace.lineNumber}] $cname.${stackTrace.methodName} : $msg"
    } else {
        msg
    }

    if (error != null) {
        Log.e(tagName, message, error)
    } else {
        Log.d(tagName, message)
    }
}

fun log(error: Throwable) {
    log(error.message ?: "", error)
}