package de.post.ident.internal_video.util

import de.post.ident.internal_core.reporting.*
import de.post.ident.internal_video.VideoManager

class EmmiVideoReporter(private val videoManager: VideoManager) {
    fun send(
            logEvent: LogEvent,
            identMethod: IdentMethod? = logEvent.identMethod,
            logLevel: LogLevel = logEvent.getLogLevel(),
            eventContext: Map<String, String>? = null,
            eventStatus: EventStatus? = null,
            message: String? = null
    ) {
        EmmiCoreReporter.send(logEvent, identMethod, logLevel, eventContext, eventStatus, message, videoManager.attemptId, videoManager.launchId)
    }

    fun flush() {
        EmmiCoreReporter.flush()
    }
}