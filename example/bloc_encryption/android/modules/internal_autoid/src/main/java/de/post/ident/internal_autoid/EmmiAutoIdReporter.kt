package de.post.ident.internal_autoid

import de.post.ident.internal_core.reporting.*

object EmmiAutoIdReporter {
    fun send(
        logEvent: LogEvent,
        iterationCount: Int = 0,
        eventContext: Map<String, String>? = null,
        flush: Boolean = false,
        attemptId: String? = null,
        machinePhase: Boolean = true,
        eventStatus: EventStatus? = null
    ) {
        val newEventContext = mutableMapOf("method" to "autoIdent")
        if (iterationCount > 0) newEventContext.put("iterationCount", iterationCount.toString())
        if (machinePhase) newEventContext.put("attempt_phase", "machinePhase")
        eventContext?.keys?.forEach { key ->
            newEventContext.put(key, eventContext[key].toString())
        }
        EmmiCoreReporter.send(
            logEvent = logEvent,
            eventContext = newEventContext,
            attemptId = attemptId,
            eventStatus = eventStatus
        )
        if (flush) EmmiCoreReporter.flush()
    }
}