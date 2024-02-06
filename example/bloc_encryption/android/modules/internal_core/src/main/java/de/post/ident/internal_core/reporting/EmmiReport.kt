package de.post.ident.internal_core.reporting

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.ArrayList

@JsonClass(generateAdapter = true)
data class EmmiReport(
        @Json(name = "attemptId") val attemptId: String?,
        @Json(name = "caseId") val caseId: String?,
        @Json(name = "identMethod") val identMethod: IdentMethod?,
        @Json(name = "logLevel") val logLevel: LogLevel = LogLevel.INFO,
        @Json(name = "message") val reportMessage: ReportMessage?,
        @Json(name = "nmChatId") val nmChatId: String?,
        @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class ReportMessage(
        @Json(name = "eventContext") val eventContext: Map<String, String>?,
        @Json(name = "eventId") val eventId: String,
        @Json(name = "eventStatus") val eventStatus: EventStatus?,
        @Json(name = "eventType") val eventType: String?,
        @Json(name = "message") val message: String?,
        @Json(name = "errorCode") val errorCode: String?
)

object ReportList {
        private val list = mutableListOf<EmmiReport>()

        val isEmpty: Boolean
                get() = list.isEmpty()

        fun size(): Int {
                return list.size
        }

        fun add(report: EmmiReport) {
                list.add(report)
        }

        fun flush(): ArrayList<EmmiReport> {
                val clonedList = ArrayList(list)
                list.clear()
                return clonedList
        }
}