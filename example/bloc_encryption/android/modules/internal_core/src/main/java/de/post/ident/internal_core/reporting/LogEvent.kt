package de.post.ident.internal_core.reporting

import androidx.annotation.Keep

/**
 * Diese Datei wird automatisch generiert und kann zentral erweitert/bearbeitet werden unter:
 * https://gitlab.7p-group.com/areion/unified-logging/blob/master/config.yaml
 */

@Keep
enum class LogEvent (val id: String, val type: EventType, val identMethod: IdentMethod?, private val defaultLogLevel: LogLevel?) {
    // Signalisiert die Anzeige eines Dialogs, in dem die gültigkeit des Vorgangs geprüft wird.
    CE_CASE_CHECK("ce.case.check", EventType.PAGE_VIEW, null, null),
    // Wird gerufen wenn der Nutzer die Eingabe der CaseID bestätigt. Wird außerdem genutzt um die UUID und CaseID zur Ermittlung der CaseID in Crashlytics zu übertragen.
    CE_CASE_UUID("ce.case.uuid", EventType.USER_ACTION, null, LogLevel.INFO),
    // Signalisiert die Anzeige eines Dialogs, der ein Problem mit dem Vorgang erläutert.
    CE_CASE_ERROR("ce.case.error", EventType.PAGE_VIEW, null, LogLevel.INFO),
    // Signalisiert die Anzeige der Verfahrensauswahl.
    CE_METHOD_SELECTION("ce.method.selection", EventType.PAGE_VIEW, null, null),
    // Signalisiert die Anzeige der Datenvervollständigung vor der Verfahrensauswahl.
    DC_CONTACT_DATA("dc.contact.data", EventType.PAGE_VIEW, null, null),
    // Signalisiert die Anzeige des Start Video-Dialogs
    IH_START_VIDEO("ih.start-video", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, in dem das Vorhandensein einer Kamera geprüft werden.
    MR_CAM_CHECK("mr.cam.check", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der ein Problem mit der Kamera beschreibt.
    MR_CAM_ERROR("mr.cam.error", EventType.PAGE_VIEW, IdentMethod.VIDEO, LogLevel.WARN),
    // Signalisiert die Anzeige eines Dialogs, der den erfolgreichen Beginn der Kameranutzung beschreibt.
    MR_CAM_SUCCESS("mr.cam.success", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, in dem die Kamerabzugriffsberechtigung abgefragt wird.
    MR_CAM_PERMISSION("mr.cam.permission", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, in dem Probleme mit der Kameraberechtigung beschrieben werden.
    MR_CAM_PERMISSION_ERROR("mr.cam.permission.error", EventType.PAGE_VIEW, IdentMethod.VIDEO, LogLevel.WARN),
    // Signalisiert die Anzeige eines Dialogs, in dem das Vorhandensein eines Lautsprechers geprüft wird.
    MR_SPEAKER_CHECK("mr.speaker.check", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, in dem ein Problem mit dem Lautsprecher beschrieben wird.
    MR_SPEAKER_ERROR("mr.speaker.error", EventType.PAGE_VIEW, IdentMethod.VIDEO, LogLevel.WARN),
    // Signalisiert die Anzeige eines Dialogs, in dem die Audioausgabe getestet wird.
    MR_SPEAKER_AUDIO_CHECK("mr.speaker.audio.check", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der die erfolgreiche Audioausgabe bestätigt.
    MR_SPEAKER_AUDIO_SUCCESS("mr.speaker.audio.success", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der ein Problem mit der Audioausgabe beschreibt.
    MR_SPEAKER_AUDIO_ERROR("mr.speaker.audio.error", EventType.PAGE_VIEW, IdentMethod.VIDEO, LogLevel.WARN),
    // Signalisiert die Anzeige eines Dialogs, der die Mikrofonberechtigung abfragt.
    MR_MIC_PERMISSION("mr.mic.permission", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der ein Problem mit der Mikrofonberechtigung beschreibt.
    MR_MIC_PERMISSION_ERROR("mr.mic.permission.error", EventType.PAGE_VIEW, IdentMethod.VIDEO, LogLevel.WARN),
    // Signalisiert die Anzeige eines Dialogs, in dem das Vorhandensein eines Mikrofons geprüft wird.
    MR_MIC_CHECK("mr.mic.check", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der ein Problem mit dem Mikrofon beschreibt.
    MR_MIC_ERROR("mr.mic.error", EventType.PAGE_VIEW, IdentMethod.VIDEO, LogLevel.WARN),
    // Signalisiert die Anzeige eines Dialogs, in dem das Audioeingangssignal geprüft wird.
    MR_MIC_SIGNAL_CHECK("mr.mic.signal.check", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der ein Problem mit dem Audiosignaleingang beschreibt.
    MR_MIC_SIGNAL_ERROR("mr.mic.signal.error", EventType.PAGE_VIEW, IdentMethod.VIDEO, LogLevel.WARN),
    // Signalisiert die Anzeige eines Dialogs, der ein Intro zum OCR-Vorgang gibt.
    CR_OCR_INTRO("cr.ocr.intro", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der weitere Hilfsinformation zum OCR darstellt.
    CR_OCR_HELP("cr.ocr.help", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, in dem das Photo für OCR aufgenommen wird.
    CR_OCR_PHOTO("cr.ocr.photo", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, wo der Nutzer ein aufgenommenes Foto zum Upload bestätigen kann (OCR oder "Foto vorab").
    CR_OCR_CONFIRM("cr.ocr.confirm", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, in dem das Foto hochgeladen und die Antwort ausgewertet wird.
    CR_OCR_CHECK("cr.ocr.check", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der das erfolgreiche Auslesen eines gültigen Ausweises bestätigt.
    CR_OCR_SUCCESS("cr.ocr.success", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der dem Nutzer mitteilt, dass sein Ausweis nicht unterstützt wird.
    CR_OCR_UNSUPPORTED("cr.ocr.unsupported", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der dem Nutzer mitteilt, dass sein Ausweis abgelaufen ist.
    CR_OCR_EXPIRED("cr.ocr.expired", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der ein Problem mit dem Upload oder dem Foto an sich (nichts gelesen) darstellt.
    CR_OCR_GENERAL_ERROR("cr.ocr.general.error", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, für die Dateneingabe durch den Nutzer.
    CR_USER_SELF_ASSESSMENT("cr.user.self.assessment", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, in dem ein "Foto vorab"-Foto aufgenommen werden kann.
    CR_PRERECORD_PHOTO("cr.prerecord.photo", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, in dem der Nutzer den Videoanruf starten kann.
    VC_CONFIRM_CHAT("vc.confirm.chat", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der über ein derzeit geschlossenes Service Center informiert.
    IH_SERVICECENTER_CLOSED("ih.servicecenter.closed", EventType.PAGE_VIEW, IdentMethod.VIDEO, LogLevel.INFO),
    // Signalisiert die Anzeige eines Dialogs, der über aktuell verlängerte Wartezeiten vor dem Videochat informiert.
    IH_SERVICECENTER_BUSY("ih.servicecenter.busy", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Genutzt um das Auffangen eines Ausfalls von WebSockets mittels Polling zu protokollieren.
    VC_WEBSOCKET_FALLBACK("vc.websocket.fallback", EventType.MISC, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige der Warteschlange.
    VC_WAIT("vc.wait", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige des Textchats mit dem Agenten.
    VC_TEXT_CHAT("vc.text.chat", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige des ersten Schritts der Identifizierung im Videochat.
    VC_STEP_CONSENT("vc.step.consent", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige des Schritts der Fotovorderseitenaufnahme.
    DR_STEP_PHOTO_FRONT("dr.step.photo.front", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige des Schritts der Fotorückseitenaufnahme.
    DR_STEP_PHOTO_BACK("dr.step.photo.back", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige des Schritts der Portraitaufnahme.
    DR_STEP_PHOTO_PORTRAIT("dr.step.photo.portrait", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige des Schritts, in dem dem Nutzer seine Ausweisnummer zur Bestätigung angezeigt wird.
    DR_STEP_DOCUMENTNUMBER("dr.step.documentnumber", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige des Schritts der Datenvervollständigung.
    DR_STEP_DATA("dr.step.data", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige des Schritts der TAN-Eingabe.
    VC_STEP_TAN("vc.step.tan", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige der Verabschiedung durch den Agenten beim Videochat.
    VC_STEP_ENDING("vc.step.ending", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, in dem der Nutzer seinen Ident bewerten kann.
    SF_RATING("sf.rating", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der abschließend über den erfolgreichen Ident informiert.
    SF_SUCCESS("sf.success", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Weiterleitung des Nutzers zur nächsten Webseite (GK, Signing, etc).
    SF_REDIRECT("sf.redirect", EventType.PAGE_VIEW, IdentMethod.VIDEO, null),
    // Signalisiert die Anzeige eines Dialogs, der über den vorzeitigen Abbruch des Idents informiert.
    SF_IDENT_CANCELLED("sf.cancelled", EventType.PAGE_VIEW, IdentMethod.VIDEO, LogLevel.INFO),
    // Signalisiert die Anzeige eines Dialogs, der über das Auftreten eines unerwarteten Fehlers informiert.
    UNEXPECTED_ERROR("unexpected.error", EventType.PAGE_VIEW, null, LogLevel.INFO),
    // Signalisiert die Anzeige eines Dialogs, in dem der Nutzer das vorzeitige Verlassen der Anwendung bestätigen kann.
    SF_LEAVE_BROWSER_WARNING("sf.leave.browser.warning", EventType.PAGE_VIEW, IdentMethod.VIDEO, LogLevel.WARN),
    // Signalisiert die Rückleitung zur Verfahrensauswahl, wenn der Nutzer dies gewählt hat.
    SF_LEAVE_RETRY("sf.leave.retry", EventType.PAGE_VIEW, IdentMethod.VIDEO, LogLevel.INFO),
    // Das Ergebnis der Vorgangsprüfung.
    MR_CASE_CHECK_RESULT("mr.case.check.result", EventType.CHECK_RESULT, null, LogLevel.INFO),
    // Das Ergebnis des Browser-Checks.
    MR_BROWSER_CHECK_RESULT("mr.browser.check.result", EventType.CHECK_RESULT, IdentMethod.VIDEO, null),
    // Das Ergebnis der Prüfung auf Vorhandensein einer Kamera.
    MR_CAM_CHECK_RESULT("mr.cam.check.result", EventType.CHECK_RESULT, IdentMethod.VIDEO, null),
    // Das Ergebnis der Prüfung auf Vorhandensein eines Mikrofons.
    MR_MIC_CHECK_RESULT("mr.mic.check.result", EventType.CHECK_RESULT, IdentMethod.VIDEO, null),
    // Das Ergebnis der Zugriffsberechtigungsabfrage für die Kamera.
    MR_CAM_PERMISSION_RESULT("mr.cam.permission.result", EventType.CHECK_RESULT, null, LogLevel.INFO),
    // Das Ergebnis eines angefragten Auflösungswechsels des Kamerasignals.
    MR_CAM_RESOLUTION_CHANGE("mr.cam.resolution.change", EventType.CHECK_RESULT, IdentMethod.VIDEO, null),
    // Das Ergebnis der Prüfung auf Vorhandensein eines Lautsprechers.
    MR_SPEAKER_CHECK_RESULT("mr.speaker.check.result", EventType.CHECK_RESULT, IdentMethod.VIDEO, null),
    // Das Ergebnis der Audioausgangssignalprüfung.
    MR_SPEAKER_AUDIO_CHECK_RESULT("mr.speaker.audio.check.result", EventType.CHECK_RESULT, IdentMethod.VIDEO, null),
    // Das Ergebnis der Zugriffsberechtigungsabfrage für das Mikrofon.
    MR_MIC_PERMISSION_CHECK_RESULT("mr.mic.permission.check.result", EventType.CHECK_RESULT, IdentMethod.VIDEO, LogLevel.INFO),
    // Das Ergebnis der OCR-Prüfung.
    CR_OCR_CHECK_RESULT("cr.ocr.check.result", EventType.CHECK_RESULT, IdentMethod.VIDEO, LogLevel.INFO),
    // Das Ergebnis der Prüfung, ob SelfServicePhoto (Fotos vorab) angeboten werden soll.
    CR_SSP_MODULES_CHECK_RESULT("cr.ssp.modules.check.result", EventType.CHECK_RESULT, IdentMethod.VIDEO, null),
    // Das Ergebnis der Prüfung, ob das USA angeboten werden soll.
    CR_USA_MODULES_CHECK_RESULT("cr.usa.modules.check.result", EventType.CHECK_RESULT, IdentMethod.VIDEO, LogLevel.INFO),
    // Das Ergebnis des Uploads bzw. der Prüfung der vom Nutzer im USA eingegebenen Daten.
    CR_USA_CHECK_RESULT("cr.usa.check.result", EventType.CHECK_RESULT, IdentMethod.VIDEO, null),
    // Das Ergebnis der Service Center Availability-Abfrage.
    MR_SERVICECENTER_CHECK_RESULT("mr.servicecenter.check.result", EventType.CHECK_RESULT, IdentMethod.VIDEO, null),
    // Das Ergebnis der TAN-Überprüfung.
    VC_TAN_CHECK("vc.tan.check", EventType.CHECK_RESULT, IdentMethod.VIDEO, null),
    // Signalisiert das Absenden der vervollständigten Kontaktdaten durch den Nutzer.
    DC_SEND_CONTACT_DATA("dc.send.contact.data", EventType.USER_ACTION, null, null),
    // Signalisiert die Anlage eines Videoidents durch Fortschreiten des Nutzers in der Anwendung.
    MR_VIDEOIDENT_CREATED("mr.case.started", EventType.USER_ACTION, IdentMethod.VIDEO, null),
    // Signalisiert die Aufnahme eines Fotos durch den Nutzer für OCR.
    CR_OCR_PHOTO_CREATION("cr.ocr.photo_creation", EventType.USER_ACTION, IdentMethod.VIDEO, null),
    // Signalisiert das Absenden der USA-Daten.
    CR_USA_SEND_DATA("cr.usa.send_data", EventType.USER_ACTION, IdentMethod.VIDEO, null),
    // Signalisiert einen Kamerawechselversuch durch den Nutzer.
    MR_CAMERA_SWITCH("mr.camera_switch", EventType.USER_ACTION, IdentMethod.VIDEO, null),
    // Signalisiert das Ausschalten der Videovorschau durch den Nutzer.
    MR_CAMERA_MUTE("mr.camera_mute", EventType.USER_ACTION, IdentMethod.VIDEO, null),
    // Signalisiert das ein / ausschalten der Taschenlampe durch den Nutzer.
    MR_FLASHLIGHT_TOGGLE("mr.flashlight_toggle", EventType.USER_ACTION, IdentMethod.VIDEO, null),
    // Erhalt einer Chat-Id vom Agentensystem.
    VC_ID("vc.id", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Genutzt um Probleme bei der Vorbereitung und dem initialen Anrufen beim Agentensystem festzuhalten.
    VC_INIT("vc.init", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Signalisiert die Verbindung mit einem Agenten bzw Probleme dabei (noch nicht WebRTC).
    VC_CONNECT("vc.connect", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Signalisiert das Beenden der Verbindung zum Agenten (nicht WebRTC).
    VC_DISCONNECT("vc.disconnect", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Signalisiert den Aufbau der Verbindung per WebRTC.
    VC_WEBRTC_CONNECT("vc.webrtc.connect", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Signalisiert den Abbruch der WebRTC-Verbindung.
    VC_WEBRTC_DISCONNECT("vc.webrtc.disconnect", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, LogLevel.INFO),
    // Signalisiert den Empfang des Agentennamens.
    VC_AGENT_NAME("vc.agent.name", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Signalisiert den Empfang des Redirect-Tokens (für Signing).
    VC_REDIRECT_TOKEN("vc.redirect.token", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // User schiebt die App während er sich in der Warteschlange befindet in den Hintergrund.
    VC_WAITING_ROOM_BACKGROUND("vc.waitingroom.background", EventType.USER_ACTION, IdentMethod.VIDEO, null),
    // Signalisiert den Empfang der aktualisierten Wartezeit.
    VC_WAITING_TIME("vc.waiting.time", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Signalisiert den Abbruch in der Warteschlange durch den Nutzer über den "zurück zur Verfahrensauswahl" Buttons.
    VC_WAITING_LINE_ABORT("vc.waiting.line.abort", EventType.USER_ACTION, IdentMethod.VIDEO, null),
    // Signalisiert den Aufbau der Websocket-Verbindung.
    VC_WEBSOCKET_CONNECT("vc.websocket.connect", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Signalisiert einen Fehler beim Websocket.
    VC_WEBSOCKET_ERROR("vc.websocket.error", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Signalisiert einen Fehler beim Polling gegen das Agentensystem.
    VC_POLL_ERROR("vc.poll.error", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, LogLevel.INFO),
    // Signalisiert den Eingang einer Agentennachricht im Textchat.
    VC_AGENT_MESSAGE("vc.agent.message", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Signalisiert das Senden einer Nutzernachricht im Textchat.
    VC_USER_MESSAGE("vc.user.message", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Signalisiert, dass der Agent die TAN versendet hat.
    VC_TAN_TRANSMITTED("vc.tan.transmitted", EventType.AGENT_MESSAGE, IdentMethod.VIDEO, null),
    // Signalisiert einen Fehler in der WebRTC-Verbindung.
    VC_WEBRTC_ERROR("vc.webrtc", EventType.WEBRTC, IdentMethod.VIDEO, null),
    // Signalisiert, dass der lokale Stream des Nutzers gemuted wurde (schwarz gestellt), evtl. aktiv durch den Nutzer.
    VC_LOCAL_STREAM_MUTING("vc.local.stream.muting", EventType.MISC, IdentMethod.VIDEO, null),
    // Signalisiert das unerwartete Fehlen des Videosignals des Nutzers.
    VC_LOCAL_STREAM_MISSING("vc.local.stream.missing", EventType.MISC, IdentMethod.VIDEO, null),
    // Enthält Messwerte des aufgenommenen Fotos beim Fotoident (Blur, Dateiname).
    PH_IMAGE_QUALITY("ph.image.quality", EventType.CHECK_RESULT, IdentMethod.PHOTO, null),
    // Nutzer startet das Verfahren VIDEO.
    PD_START_VIDEO("pd.start-video", EventType.USER_ACTION, IdentMethod.VIDEO, null),
    // Nutzer startet das Verfahren PHOTO.
    PD_START_PHOTO("pd.start-photo", EventType.USER_ACTION, IdentMethod.PHOTO, null),
    // Nutzer startet das Verfahren BASIC.
    PD_START_BASIC("pd.start-basic", EventType.USER_ACTION, IdentMethod.BASIC, null),
    // Nutzer startet das Verfahren eID/nPa.
    PD_START_EID("pd.start-eid", EventType.USER_ACTION, IdentMethod.EID, null),
    // Nutzer startet das Verfahren AutoID.
    PD_START_AUTOID("pd.start-autoid", EventType.USER_ACTION, IdentMethod.AUTOID, null),
    // Signalisiert einen Fehler beim Versuch ein Foto zu schießen (OCR und Videochat).
    TAKE_PHOTO_ERROR("take.photo.error", EventType.MISC, IdentMethod.VIDEO, null),
    // Genutzt, um die Auflösung und Größe aufgenommener Bilder zu loggen.
    PHOTO_QUALITY("photo.quality", EventType.MISC, IdentMethod.VIDEO, null),
    // Genutzt, um das Qualitätsprofil des Videochats zu loggen.
    VIDEO_QUALITY("video.quality", EventType.MISC, IdentMethod.VIDEO, null),
    // Genutzt um etwaige Fehler bei HTTP-Requests zu loggen.
    REQUEST_ERROR("request.error", EventType.MISC, null, null),
    // Genutzt für nicht näher spezifizierbare Fehler.
    MISC_ERROR("misc.error", EventType.MISC, null, null),
    // Genutzt um den Grund für eine positive Jailbreak detection zu übermitteln.
    JB_DETECTION_RESULT("jb.detection.result", EventType.CHECK_RESULT, null, null),
    // Das Ergebnis der ChatLaunchID-Abfrage.
    VC_LAUNCH_ID("vc.launch.id", EventType.CHECK_RESULT, IdentMethod.VIDEO, null),
    // FAQ zu einer Identmethode wird angezeigt
    PD_FAQ("pd.faq", EventType.PAGE_VIEW, null, null),
    // Scan Info Screen wird vor dem Dranhalten der Karte angezeigt um dem Benutzer eine Hilfe zu geben, wie und wie lange die Karte an das Gerät gehalten werden soll.
    EI_SCAN_INFO("ei.scan.info", EventType.PAGE_VIEW, IdentMethod.EID, null),
    // Gerät wartet auf eID-Karte. Im Kontext (currentContext) wird der Kontext übergeben (ident, pinChangeStart, pinChange)
    EI_WAITING_FOR_CARD("ei.contact.waiting", EventType.PAGE_VIEW, IdentMethod.EID, null),
    // eID-Karte wurde im Lesebereich erfasst und Verbindung wird aufgebaut.
    EI_CONNECTING_CARD("ei.connect.card", EventType.PAGE_VIEW, IdentMethod.EID, null),
    // Button zu Zertifikatsinformationen sowie auszulesende Daten werden angezeigt.
    EI_DISPLAY_DATA("ei.display.data", EventType.PAGE_VIEW, IdentMethod.EID, null),
    // PIN-Eingabe wird angezeigt.
    EI_PIN_ENTRY("ei.pin.entry", EventType.PAGE_VIEW, IdentMethod.EID, null),
    // PIN wurde eingegeben und zur Prüfung an die Karte gesendet.
    EI_PIN_CHECK("ei.pin.check", EventType.USER_ACTION, IdentMethod.EID, null),
    // Ergebnis der Prüfung der PIN auf der Karte.
    EI_PIN_RESULT("ei.pin.result", EventType.CHECK_RESULT, IdentMethod.EID, null),
    // Identifizierungsvorgang war erfolgreich, Status im Portal wurde gesetzt.
    EI_SUCCESS("ei.success", EventType.PAGE_VIEW, IdentMethod.EID, null),
    // Anzeige eines Fehlerscreens im eID-Prozess.
    EI_ERROR_SCREEN("ei.error.screen", EventType.PAGE_VIEW, IdentMethod.EID, LogLevel.INFO),
    // Details zum Zertifikat werden angezeigt.
    EI_CERTIFICATE_INFO("ei.certificate.info", EventType.PAGE_VIEW, IdentMethod.EID, null),
    // Informationsseite zur Transport-PIN wird angezeigt.
    EI_TRANSPORT_PIN_INFO("ei.transport.pin.info", EventType.PAGE_VIEW, IdentMethod.EID, null),
    // Nach erfolgreicher Änderung der Transport-Pin.
    EI_TRANSPORT_SUCCESS("ei.transport.success", EventType.PAGE_VIEW, IdentMethod.EID, null),
    // Maske zum ändern der Transport-PIN wird angezeigt.
    EI_DISPLAY_CHANGE_PIN("ei.display.change.pin", EventType.PAGE_VIEW, IdentMethod.EID, null),
    // Prozess zum Ändern der Transport-PIN auf der Karte wurde gestartet.
    EI_CHANGE_PIN_CLICKED("ei.change.pin.clicked", EventType.USER_ACTION, IdentMethod.EID, null),
    // Ergebnis der Änderung der Transport-PIN.
    EI_CHANGE_PIN_RESULT("ei.change.pin.result", EventType.CHECK_RESULT, IdentMethod.EID, null),
    // Info aus dem openecard-Kernel über Statuscode (success/error - resultMinor).
    EI_AUTHENTICATION_RESULT_MINOR("ei.authentication.result", EventType.MISC, IdentMethod.EID, null),
    // EID nicht möglich wegen Betriebsystemversion oder NFC-Chip nicht vorhanden
    EI_IMPOSSIBLE("ei.impossible", EventType.MISC, IdentMethod.EID, null),
    // Governikus gab Major event zurück, das derzeit nicht behandelt wird
    EI_MAJOR_UNHANDLED("ei_major_unhandled", EventType.MISC, IdentMethod.EID, null),
    // Nutzer öffnet die Sprachauswahl.
    VC_LANGUAGE_SELECTION("vc.language.selection", EventType.PAGE_VIEW, null, null),
    // Nutzer öffnet den QR-Code Scanner.
    CE_QR_SCAN("ce.qr.scan", EventType.USER_ACTION, null, null),
    // Ergebnis des QR-Scans.
    CE_QR_SCAN_RESULT("ce.qr.scan.result", EventType.CHECK_RESULT, null, null),
    // Nutzer öffnet die Seite mit Informationen zur Vorgangsnummer.
    CE_INFO_CASE_ID("ce.info.case-id", EventType.PAGE_VIEW, null, null),
    // Nutzer öffnet die Seite mit Einstellungen und Details zur Version.
    CE_INFO_SETTINGS("ce.info.settings", EventType.PAGE_VIEW, null, null),
    // Anzeige eines Hinweises, dass die Version nicht mehr unterstützt wird.
    CE_FORCE_UPDATE("ce.force.update", EventType.PAGE_VIEW, null, null),
    // Anzeige eines Hinweises, dass die Betriebssystem-Version nicht mehr unterstützt wird.
    CE_FORCE_UPDATE_SDK("ce.force.update.sdk", EventType.PAGE_VIEW, null, null),
    // Nutzer fordert E-Mail mit Basicident-Coupon an.
    BA_COUPON_EMAIL("ba.coupon.email", EventType.USER_ACTION, IdentMethod.BASIC, null),
    // Nutzer startet Download des Basicident-Coupons.
    BA_COUPON_DOWNLOAD("ba.coupon.download", EventType.USER_ACTION, IdentMethod.BASIC, null),
    // Ergebnis des Downloads des Basicident-Coupons.
    BA_COUPON_DOWNLOAD_RESULT("ba.coupon.download.result", EventType.CHECK_RESULT, IdentMethod.BASIC, null),
    // Anzeige des Basicident-Coupons.
    BA_DISPLAY_COUPON("ba.display.coupon", EventType.PAGE_VIEW, IdentMethod.BASIC, null),
    // Anzeige des Filialfinders.
    BA_DISPLAY_FINDER("ba.display.finder", EventType.PAGE_VIEW, IdentMethod.BASIC, null),
    // Anzeige der Informationen zum Fortsetzen in der App.
    BA_APP_INFO("ba.app.info", EventType.PAGE_VIEW, IdentMethod.BASIC, null),
    // Anzeige der NPS-Abfrage (Kundenfeedback).
    DISPLAY_RATING("display.rating", EventType.PAGE_VIEW, null, null),
    // Upload von Dokumenten (.zip) im Fotoident.
    PH_UPLOAD_DOCUMENTS("ph.upload.documents", EventType.USER_ACTION, IdentMethod.PHOTO, null),
    // Einstellung der Sprache für die Oberflächen.
    CE_LANGUAGE_SELECTION("ce.language.selection", EventType.MISC, null, null),
    // Fehler reporting falls ein key in der app-config texts map fehlt
    AC_TEXTS_MISSING("ac.texts.missing", EventType.MISC, null, LogLevel.WARN),
    // Start / Abschluss des Erklärvideos auf dem Fehlerscreen im eID-Prozess. (Context state=started, state=ended)
    EI_ERROR_SCREEN_VIDEO_ACTION("ei.error.screen.action.video", EventType.USER_ACTION, IdentMethod.EID, null),
    // Fehler reporting für fehlenden Maps Api key
    BA_MAPS_KEY("ba.maps.key", EventType.MISC, IdentMethod.BASIC, LogLevel.INFO),
    // Anzeige eines Fehlerscreens im SDK (Context sdkResultCode=RESULT_TECHNICAL_ERROR, sdkResultCodeId=1001)
    SD_ERROR_SCREEN("sd.error.screen", EventType.PAGE_VIEW, null, LogLevel.WARN),
    // Rückgabe des ResultCodes an die HostApp (Context sdkResultCode=RESULT_TECHNICAL_ERROR, sdkResultCodeId=1001)
    SD_RESULT_CODE("sd.result.code", EventType.MISC, null, null),
    // Nutzer werden die Terms angezeigt (Context accepted = true)
    AUTOIDENT_TERMS("autoIdent.terms", EventType.PAGE_VIEW, IdentMethod.AUTOID, null),
    // Nutzer sieht Beschreibung für DocumentCheck (Context iterationCount = 1)
    AUTOIDENT_DOCCHECK_DESCRIPTION("autoIdent.machinePhase.docCheck.description", EventType.PAGE_VIEW, IdentMethod.AUTOID, null),
    // Nutzer macht Foto der Vorderseite (Context iterationCount = 1, type = front)
    AUTOIDENT_DOCCHECK_CAMERA_PREVIEW("autoIdent.machinePhase.docCheck.cameraPreview", EventType.MISC, IdentMethod.AUTOID, null),
    // Nutzer sieht Foto der Vorderseite Context( iterationCount = 1, userDocScanId = 1234, type = front )
    AUTOIDENT_DOCCHECK_CREATE_IMAGE("autoIdent.machinePhase.docCheck.createImage", EventType.PAGE_VIEW, IdentMethod.AUTOID, null),
    // App startet Upload der Daten (Context iterationCount = 1, imageWidthFront = 800, imageHeightFront = 600, imageWidthBack = 800, imageHeightBack = 600)
    AUTOIDENT_DOCCHECK_UPLOAD("autoIdent.machinePhase.docCheck.upload", EventType.MISC, IdentMethod.AUTOID, null),
    // Nutzer sieht Beschreibung für DVF (Context iterationCount = 1)
    AUTOIDENT_DVF_DESCRIPTION("autoIdent.machinePhase.docVisualFeaturesRun.description", EventType.PAGE_VIEW, IdentMethod.AUTOID, null),
    // Nutzer macht DVF (Klickt auf Button für Aufnahme) (Context iterationCount = 1)
    AUTOIDENT_DVF_CAPTURE("autoIdent.machinePhase.docVisualFeaturesRun.capture", EventType.MISC, IdentMethod.AUTOID, null),
    // Nutzer sieht, dass der DVF erfolgreich war (Context iterationCount = 1)
    AUTOIDENT_DVF_SUCCESS("autoIdent.machinePhase.docVisualFeaturesRun.success", EventType.PAGE_VIEW, IdentMethod.AUTOID, null),
    // App bricht nach definierter Zeit mit einem Timeout ab. (Context iterationCount = 1, frames = 8)
    AUTOIDENT_DVF_TIMEOUT("autoIdent.machinePhase.docVisualFeaturesRun.timeout", EventType.PAGE_VIEW, IdentMethod.AUTOID, null),
    // Nutzer sieht Beschreibung für FVLC (Context iterationCount = 1)
    AUTOIDENT_FVLC_DESCRIPTION("autoIdent.machinePhase.faceVerificationAndLivenesCheck.description", EventType.PAGE_VIEW, IdentMethod.AUTOID, null),
    // Nutzer macht FVLC (Klickt auf Button für Aufnahme)	 (Context iterationCount = 1)
    AUTOIDENT_FVLC_CAPTURE("autoIdent.machinePhase.faceVerificationAndLivenesCheck.capture", EventType.MISC, IdentMethod.AUTOID, null),
    // App erhält Erfolgsmeldung vom ML Server (Context iterationCount = 1)
    AUTOIDENT_FVLC_SUCCESS("autoIdent.machinePhase.faceVerificationAndLivenesCheck.success", EventType.PAGE_VIEW, IdentMethod.AUTOID, null),
    // App bricht nach definierter Zeit mit einem Timeout ab. (Context iterationCount = 1, frames = 8)
    AUTOIDENT_FVLC_TIMEOUT("autoIdent.machinePhase.faceVerificationAndLivenesCheck.timeout", EventType.PAGE_VIEW, IdentMethod.AUTOID, null),
    // App bekommt 200er auf mp/finish
    AUTOIDENT_MACHINE_PHASE_SUCCESS("autoIdent.machinePhase.success", EventType.MISC, IdentMethod.AUTOID, null),
    // User sieht den AutoId Statussceen
    AUTOIDENT_MACHINE_PHASE_FEEDBACKSCREEN("autoIdent.machinePhase.feedbackscreen", EventType.PAGE_VIEW, IdentMethod.AUTOID, null),
    // Nutzer sieht die Fehlerseite	(Context errorCode = MP0615)
    AUTOIDENT_MACHINE_ERROR_SCREEN("autoIdent.machinePhase.error.screen", EventType.PAGE_VIEW, IdentMethod.AUTOID, LogLevel.ERROR),
    // Identvorgang kann nicht erfolgreich abgeschlossen werden und wird mit einem call auf /incomplete abgebrochen
    AUTOIDENT_MACHINE_INCOMPLETE("autoIdent.machinePhase.incomplete", EventType.MISC, IdentMethod.AUTOID, LogLevel.ERROR);

    fun getLogLevel(): LogLevel {
        return defaultLogLevel ?: LogLevel.INFO
    }
}