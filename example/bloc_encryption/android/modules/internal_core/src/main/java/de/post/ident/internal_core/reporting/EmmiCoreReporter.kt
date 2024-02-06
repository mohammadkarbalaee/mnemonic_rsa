package de.post.ident.internal_core.reporting

import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.util.log
import kotlinx.coroutines.*

object EmmiCoreReporter : CoroutineScope by MainScope() {
    private val emmiInterface: CoreEmmiService = CoreEmmiService
    private val reportList: ReportList = ReportList
    private const val REPORT_POOL_SIZE = 10

    fun send(
            logEvent: LogEvent,
            identMethod: IdentMethod? = logEvent.identMethod,
            logLevel: LogLevel = logEvent.getLogLevel(),
            eventContext: Map<String, String>? = null,
            eventStatus: EventStatus? = null,
            message: String? = null,
            attemptId: String? = null,
            nmChatId: String? = null,
            errorCode: String? = null,
    ) {

        val reportMessage = ReportMessage(
            eventContext,
            logEvent.id,
            eventStatus,
            logEvent.type.id,
            message,
            errorCode
        )

        val report = EmmiReport(
                attemptId = attemptId,
                caseId = Commons.caseId,
                identMethod = identMethod,
                logLevel = logLevel,
                reportMessage = reportMessage,
                nmChatId = nmChatId
        )

        log("report: $report")
        if (report.logLevel == LogLevel.ERROR || report.logLevel == LogLevel.WARN) {
            sendImmediateReport(report)
        } else if (report.logLevel == LogLevel.INFO) {
            sendReport(report)
        }
    }

    fun flush() {
        log("Flushing remaining reports.")

        if (!reportList.isEmpty) {
            sendReports()
        }
    }

    private fun sendImmediateReport(report: EmmiReport) {
        reportList.add(report)
        sendReports()
    }

    private fun sendReport(report: EmmiReport) {
        reportList.add(report)
        if (REPORT_POOL_SIZE <= reportList.size()) {
            sendReports()
        }
    }

    private fun sendReports() {
        val emmiReportList: List<EmmiReport> = reportList.flush()
        logReport(emmiReportList)

        launch {
            try {
                emmiInterface.sendReport(emmiReportList)
                log("Successfully sent report.")
            } catch (err: Exception) {
                log("Failed to send report, re-adding events to list. ", err)
                emmiReportList.forEach { reportList.add(it) }
            }
        }
    }

    private fun logReport(emmiReportList: List<EmmiReport>) {
        for (emmiReport in emmiReportList) {
            log("Send: $emmiReport")
        }
    }
}