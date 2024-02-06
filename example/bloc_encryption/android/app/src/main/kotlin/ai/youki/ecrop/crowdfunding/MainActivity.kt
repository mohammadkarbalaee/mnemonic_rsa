package ai.youki.ecrop.crowdfunding

import de.post.ident.api.core.PinnedDomain
import io.flutter.embedding.android.FlutterFragmentActivity
import de.post.ident.api.core.PostidentSdk
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.engine.FlutterEngine
import android.content.Intent
import io.flutter.plugins.GeneratedPluginRegistrant
import android.util.Log;


class MainActivity: FlutterFragmentActivity(), EventChannel.StreamHandler {

    private val postidentSdkRequestCode = 12
    private val postidentSuccessCode = "1"
    private val postidentCancelCode = "2"
    private val  postidentInvalidCode = "3"
    private val methodChannelName = "ai.wattify_youki/identification"
    private val eventChannelName = "ai.wattify_youki/identification_response"

    private var eventSink: EventChannel.EventSink? = null

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == postidentSdkRequestCode) {
            when (PostidentSdk.resolveResultCode(resultCode)) {
                PostidentSdk.ResultCodes.RESULT_OK -> eventSink?.success(
                    postidentSuccessCode
                )
                PostidentSdk.ResultCodes.RESULT_CANCELLED -> eventSink?.success(
                    postidentCancelCode
                )
                PostidentSdk.ResultCodes.RESULT_METHOD_NOT_AVAILABLE -> eventSink?.success(
                    "The selected method is not available. User was given the choice to install POSTIDENT app."
                )
                PostidentSdk.ResultCodes.RESULT_TECHNICAL_ERROR -> eventSink?.success(
                    "Technical error (not specified) occurred."
                )
                PostidentSdk.ResultCodes.ERROR_SERVER_CONNECTION -> eventSink?.success(
                    "The server returned an error (timeout or bad request)."
                )
                PostidentSdk.ResultCodes.ERROR_SSL_PINNING -> eventSink?.success(
                    "A secure connection to the server could not be established (check certificate hashes). User was given the choice to install POSTIDENT app."
                )
                PostidentSdk.ResultCodes.ERROR_WRONG_MOBILE_SDK_API_KEY -> eventSink?.success(
                    "The supplied 'mobile SDK API key' is invalid. User was given the choice to install POSTIDENT app."
                )
                PostidentSdk.ResultCodes.ERROR_SDK_UPDATE -> eventSink?.success(
                    "SDK version is no longer supported. User was given the choice to install POSTIDENT app."
                )
                PostidentSdk.ResultCodes.ERROR_OS_VERSION -> eventSink?.success(
                    "Android version of the device is not supported."
                )
                PostidentSdk.ResultCodes.ERROR_OFFLINE -> eventSink?.success("The device has no active internet connection.")
                PostidentSdk.ResultCodes.ERROR_ROOT_DETECTED -> eventSink?.success(
                    "Rooted Devices are not supported by the POSTIDENT app."
                )
                PostidentSdk.ResultCodes.ERROR_CASE_DONE -> eventSink?.success("The case is already closed.")
                PostidentSdk.ResultCodes.ERROR_CASE_NOT_FOUND -> eventSink?.success(
                    postidentInvalidCode
                )
                PostidentSdk.ResultCodes.ERROR_CASE_INVALID -> eventSink?.success(
                    postidentInvalidCode
                )
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        GeneratedPluginRegistrant.registerWith(flutterEngine)
        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            eventChannelName
        ).setStreamHandler(this)

        Log.e("caseId", "resid vase shoro")
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            methodChannelName
        ).setMethodCallHandler { call, _ ->
            Log.e("caseId", ""+call.argument("caseId"))
            when (call.method) {
                "startPostident" ->


                    PostidentSdk.start(
                        activity = this,
                        requestCode = postidentSdkRequestCode,
                        caseId = call.argument("caseId")!!,
                        mobileSdkApiKey = "1e54a594097d435084395620b416eac1",
                        useTestEnvironment = true,
                    )
//                    PostidentSdk.start(
//                    activity = this,
//                    requestCode = postidentSdkRequestCode,
//                    caseId = call.argument("caseId")!!,
//                    useTestEnvironment = true,
//                    mobileSdkApiKey = "1e54a594097d435084395620b416eac1",
//                    middlewareUrl = "https://postident-itu.deutschepost.de/emmi-postident/api/",
//                    pinnedDomains = listOf(
//                        PinnedDomain(
//                            "postident-itu.deutschepost.de",
//                            "sha256/HdgvoQT1Kh7UbkUMLi4SBtt5+sJ+bwDB4auYr9DlkJo=" // new postident certificate hash
//                        ),
//                        PinnedDomain(
//                            "videoident-itu.deutschepost.de",
//                            "sha256/hhuFUXEHLm2WprHwLe4Ucz43PCGdoCWMYhjVbKXPTs=" // new video certificate hash
//                        )
//                    )
//                )
            }
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }
}