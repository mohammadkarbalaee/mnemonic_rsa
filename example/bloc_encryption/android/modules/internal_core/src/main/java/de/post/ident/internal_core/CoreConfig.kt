package de.post.ident.internal_core

import android.content.Context
import android.os.Build
import androidx.annotation.Keep
import de.post.ident.internal_core.rest.AppConfigDTO
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.rest.ServerConfig
import de.post.ident.internal_core.rest.PinnedDomain
import de.post.ident.internal_core.util.KPrefDelegate
import de.post.ident.internal_core.util.log
import java.lang.ref.WeakReference

private const val EMMI_URL = "https://postident.deutschepost.de/emmi-postident/api/"
private const val ITU_EMMI_URL = "https://master.itu.pub.postident.de/emmi-postident/api/"
private const val SIGNING_URL = "https://postident.deutschepost.de/signingportal/portal/api/v1/"
private const val ITU_SIGNING_URL = "https://master.itu.pub.postident.de/signingportal/portal/api/v1/"
private const val ML_SERVER_URL = "master.prod.api.postident.de/"

// INFO: If you need to make changes here, make use of the script "get-cert-hashes-sha256.sh" in the commons folder!
private val EMMI_PINNED_DOMAINS = listOf(
    PinnedDomain("postident.deutschepost.de", "sha256/SeVr84Us4Njv50ywWUq4RxdGrvCde3YfrbuO7tUq8y8="),  // current certificate
    PinnedDomain("postident.deutschepost.de", "sha256/B6PpTUPCqlfRWpPyy/QXfR1n5bHWa9cq9BXBEK3xtdw=") // old certificate
)

private val ITU_PINNED_DOMAINS = listOf(
PinnedDomain("master.itu.pub.postident.de", "sha256/IB7L2FY0k0/EExJpKxbnqNOXnZTV+EN65qpWVOjX5nk="), // current certificate
PinnedDomain("master.itu.pub.postident.de", "sha256/mPl3K8VlV/JsSCGNeE2IWKb/3KzfmOwBg8+D7VWl9OA=") // current certificate
)

private val ML_PINNED_DOMAINS = listOf(
    PinnedDomain("master.prod.api.postident.de", "sha256/bt0UQbVFwG5LHXyp0RDUAs/tjSR5DBgYqLsyqFz5Piw=") // current certificate
)

private const val AGENT_DOMAIN = "videoident.deutschepost.de"
private val AGENT_PINNED_DOMAINS = listOf(
        PinnedDomain("videoident.deutschepost.de", "sha256/bcHC4qeTDHaSkRw6Zd+U1JQPNAb+zQGSl0+10KJ8KtU="), // current certificate
        PinnedDomain("videoident.deutschepost.de", "sha256/s70w1dsdsbrfxo6kyHvPnFub5BxB35KSdaqQ+WgZA4Q=") // old certificate
)

object Commons {
    private val kPrefs = KPrefDelegate(CoreConfig.appContext, "commons")

    var caseId: String? by kPrefs.stringNA("CASE_ID")
    var attemptId: String? by kPrefs.stringNA("ATTEMPT_ID")
    var autoId: String? by kPrefs.stringNA("AUTO_ID")

    var clientId: String? = null
    var signingRedirectToken: String? = null
    var fotoSizeKb: Int = 350
    var fotoQuality: Int = 80
    var fotoMaxWidth: Int = 1000
    var previewFrameQuality: Int = 50
    var previewFrameWidth: Int = 360
    var identMethodsAvailable: Int = 0

    var skipMethodSelection: Boolean = false
    var showEidAccessRightsScreen: Boolean = true
}

object CoreConfig {

    private var photoRsaKey: String? = null
    private var serverConfigInternal: ServerConfig? = null

    val serverConfig
        get() = serverConfigInternal ?: throw IllegalStateException("Sdk not initialized!") // maybe we need this later to initialize other modules

    var isInitialized = false
        private set

    private var appContextInternal: Context? = null

    val appContext
        get() = appContextInternal ?: checkNotNull(SdkApplication.appContext)

    var isSdk = true
        private set

    var enableDevModeEid = false
        private set

    var enableSimEid = false
        private set

    var isScreenshotEnabled = false
        private set

    var isITU = false
        private set

    lateinit var appConfig : AppConfigDTO

    fun init(context: Context,
             isSdkOnly: Boolean = true,
             authUser: String? = null,
             authPassword: String? = null,
             hostAppId: String,
             emmiUrl: String?,
             agentDomain: String? = null,
             mlServerUrl: String?,
             interfaceKey: String,
             rsaKey: String? = null,
             pinnedDomains: List<PinnedDomain>,
             hostAppVersion: String?,
             enableScreenshotsForTestEnvironments: Boolean = false,
             signingUrl: String? = null,
             enableDeveloperModeEid: Boolean = false,
             enableSimulatorEid: Boolean = false,
             useTestEnvironment: Boolean? = false
    ) {

        appContextInternal = context.applicationContext
        isITU = useTestEnvironment ?: false
        val emmiDomain = if (!isITU) emmiUrl else ITU_EMMI_URL
        val signingDomain = if (!isITU) signingUrl else ITU_SIGNING_URL
        isSdk = isSdkOnly
        isScreenshotEnabled = if (BuildConfig.DEBUG) true else if (emmiUrl == null) false else enableScreenshotsForTestEnvironments
        photoRsaKey = rsaKey
        val mergedPinnedDomains = EMMI_PINNED_DOMAINS + ITU_PINNED_DOMAINS + AGENT_PINNED_DOMAINS + ML_PINNED_DOMAINS + pinnedDomains
        val newConfig = ServerConfig(
                authUser,
                authPassword,
                getDefaultUserAgentString(hostAppId, hostAppVersion),
                emmiDomain ?: EMMI_URL,
                agentDomain ?: AGENT_DOMAIN,
                mlServerUrl ?: ML_SERVER_URL,
                interfaceKey,
                mergedPinnedDomains,
                hostAppVersion,
                hostAppId,
                signingDomain ?: SIGNING_URL
        )
        try {
            CoreEmmiService.init(newConfig)
        } catch (e: Throwable)  {
            log("error initializing CoreEmmiService")
        }
        enableDevModeEid = enableDeveloperModeEid
        enableSimEid = enableSimulatorEid
        serverConfigInternal = newConfig
        isInitialized = true
    }

    // convention: Postidentapp/{videoApp name}/{version number} [ATARI-2079]
    // update: get feature version number from app [ATARI-20978]
    // update: make user agent static and use app id instead of app name [ATARI-38818]
    private fun getDefaultUserAgentString(appId: String, hostAppVersion: String?)
            = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; " +
            "${Build.MODEL} Build/${Build.ID})" +
            " Postidentapp/$appId/${getAppVersionName(hostAppVersion)}/${BuildConfig.SDK_MODULE_VERSION}"

    private fun getAppVersionName(hostAppVersion: String?) = hostAppVersion?: BuildConfig.MODULE_VERSION_NAME
    fun getRsaKey() = photoRsaKey ?: "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3PFdAqYkaYZufVmTLKNNfbXYBtsrcUTejPngAFgYPV/MJwzMEG97wQP83kvVcgi9/bWkmwTM6E//MczQY0GcnVaSN+kIX+y1W2sIlK46XuvqJsUExjnHkApGJ89KPYYpOsmC/01pNHf6/UplSd6ipai7GUgg99KvyXOOjyl52nGh7CUtdAzNhrXo3rrTZq+kLdiw7ijKCnrWIo4GUbEzcYa3GYiP1m+0vcy1gxFtp0IS0RBJVZKFdhxQYyx0zgOSKkjbhoE9OAusGK93kksJ3bMsq2p/7YZdKTOfI0jx2kql64RDsNBz5uFBHVv07zuZ3ivXZgP6rWtoBurjFmyenwIDAQAB"
}

@Keep
enum class SdkResultCodes(val id: Int) {
    RESULT_OK(-1),
    RESULT_CANCELLED(0),
    RESULT_METHOD_NOT_AVAILABLE(1000),
    RESULT_TECHNICAL_ERROR(1001),
    ERROR_SERVER_CONNECTION(1002),
    ERROR_SSL_PINNING(1003),
    ERROR_WRONG_MOBILE_SDK_API_KEY(1004),
    ERROR_SDK_UPDATE(1005),
    ERROR_OS_VERSION(1006),
    ERROR_OFFLINE(1007),
    ERROR_ROOT_DETECTED(1008),
    ERROR_CASE_DONE(1101),
    ERROR_CASE_NOT_FOUND(1102),
    ERROR_CASE_INVALID(1103);

    companion object {
        val idMap = values().associateBy { it.id }
    }
}
