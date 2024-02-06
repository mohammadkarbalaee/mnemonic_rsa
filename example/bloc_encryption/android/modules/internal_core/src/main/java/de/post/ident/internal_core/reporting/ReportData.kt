package de.post.ident.internal_core.reporting

import androidx.annotation.Keep

/**
 * Diese Datei wird automatisch generiert und kann zentral erweitert/bearbeitet werden unter:
 * https://gitlab.7p-group.com/areion/unified-logging/blob/master/config.yaml
 */

@Keep
enum class EventStatus {
    SUCCESS, ERROR, FAILURE
}

@Keep
enum class LogLevel {
    INFO, WARN, ERROR
}

@Keep
enum class IdentMethod {
    VIDEO, PHOTO, BASIC, EID, AUTOID
}

@Keep
enum class EventType(val id: String) {
    USER_ACTION("user.action"),
    PAGE_VIEW("page.view"),
    CHECK_RESULT("check.result"),
    AGENT_MESSAGE("agent.message"),
    WEBRTC("webrtc"),
    MISC("misc");

    override fun toString(): String {
        return id
    }
}