package de.post.ident.internal_core.util

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.View
import android.webkit.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import de.post.ident.internal_core.*
import de.post.ident.internal_core.databinding.PiActivityWebviewBinding
import de.post.ident.internal_core.rest.SigningRestService
import de.post.ident.internal_core.start.getParameter
import de.post.ident.internal_core.start.parameter
import de.post.ident.internal_core.start.putParameter
import kotlinx.coroutines.launch

class WebviewActivity : PiBaseActivity() {

    companion object {
        private val EXTRA_URL = String.parameter("EXTRA_URL", "")
        private val EXTRA_IS_SIGNING = Boolean.parameter("EXTRA_IS_SIGNING", false)
        private val EXTRA_IS_CALLBACK = Boolean.parameter("EXTRA_IS_CALLBACK", false)
        val WEBVIEW_ACTIVITY_REQUEST = 7676

        fun start(activity: FragmentActivity, url: String, isSigning: Boolean = false, isCallback: Boolean = false) {
            val intent = Intent(activity, WebviewActivity::class.java)
            intent.putParameter(EXTRA_URL, url)
            intent.putParameter(EXTRA_IS_SIGNING, isSigning)
            intent.putParameter(EXTRA_IS_CALLBACK, isCallback)
            activity.startActivityForResult(intent, WEBVIEW_ACTIVITY_REQUEST)
        }
    }

    private lateinit var viewBinding: PiActivityWebviewBinding
    private lateinit var webView: WebView
    private lateinit var uri: String
    private lateinit var emmiBaseUrl: String
    private var isSigning: Boolean = false
    private var isCallback: Boolean = false
    private var wasSigningSuccessful: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = PiActivityWebviewBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        webView = viewBinding.webview
        uri = checkNotNull(intent.getParameter(EXTRA_URL))
        isSigning = intent.getParameter(EXTRA_IS_SIGNING) == true
        isCallback = intent.getParameter(EXTRA_IS_CALLBACK) == true

        showBackButton(!isSigning && !isCallback) { finish() }
        try {
            val parsedEmmiUri = Uri.parse(CoreConfig.serverConfig.emmiUrl)
            emmiBaseUrl = "${parsedEmmiUri.scheme}://${parsedEmmiUri.host}"
        } catch (e: Throwable) {
            log("error getting EMMI url")
            finish()
            return
        }

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true // needed for WIPI pages

        try {
            loadWebview()
        } catch (e: ActivityNotFoundException) {
            // replace String after ATARI-46392 is done
            showAlertDialog(this, LocalizedStrings.getString("eid_error_unknown0")) { onBackPressed() }
        }
    }

    private fun loadWebview() {
        webView.webViewClient = getWebviewClient()
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false

        if (BuildConfig.WEBVIEW_DEBUGGABLE) WebView.setWebContentsDebuggingEnabled(true)
        log("WebView debuggable: ${BuildConfig.WEBVIEW_DEBUGGABLE}")

        if (isSigning && Commons.signingRedirectToken.isNullOrBlank().not()) {
            try {
                val maxCookieAgeSeconds = 3600 * 24 // 24 hours

                val cookieString = "iaToken=" + Commons.signingRedirectToken + ";max-age=" + maxCookieAgeSeconds + ";SameSite=Strict;Secure;path=/"
                val parsedUri = Uri.parse(uri)
                val baseUrl = "${parsedUri.scheme}://${parsedUri.host}"

                CookieManager.getInstance().setCookie(baseUrl, cookieString) {
                    log("signing cookie set successfully")
                    webView.loadUrl(uri)
                }
            } catch (e: Throwable) {
                log("error setting cookie", e)
                webView.loadUrl(uri)
            }
        } else {
            webView.loadUrl(uri)
        }

        log("opening webview with URL: ${intent.getParameter(EXTRA_URL)}")
    }

    private fun getWebviewClient(): WebViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            viewBinding.progressIndicator.visibility = View.GONE
            viewBinding.webview.visibility = View.VISIBLE
        }

        override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
            handler?.proceed(CoreConfig.serverConfig.authUser, CoreConfig.serverConfig.authPassword)
        }

        @TargetApi(Build.VERSION_CODES.N)
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            if (request?.url == null || view?.context == null) {
                return false
            }

            handleUrl(view, request.url)
            return true
        }

        @Deprecated("Needed for older Android versions up till API 24 (Nougat)")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url.isNullOrEmpty() || view?.context == null) {
                return false
            }

            handleUrl(view, Uri.parse(url))
            return true
        }

        private fun handleUrl(view: WebView, url: Uri) {
            try {
                // follow internal links and open external links in browser
                if (url.toString().contains(emmiBaseUrl)) {
                    wasSigningSuccessful =
                        url.path?.contains("/signingsuccess") == true || url.path?.contains("/complete") == true
                    val isDownload =
                        url.toString().contains("download-downloadForm") && url.toString()
                            .contains("downloadButton=")
                    if (isDownload) {
                        downloadSigningDocuments(url.toString())
                    } else {
                        view.loadUrl(url.toString())
                    }
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, url)
                    view.context.startActivity(intent)
                    if (isCallback) closeSdkWithSuccess()
                }
            } catch (e: ActivityNotFoundException) {
                // replace String after ATARI-46392 is done
                showAlertDialog(this@WebviewActivity, LocalizedStrings.getString("eid_error_unknown0")) { onBackPressed() }
            }
        }
    }

    private fun closeSdkWithSuccess() {
        setResult(SdkResultCodes.RESULT_OK.id)
        finish()
    }

    private fun downloadSigningDocuments(url: String) {

        lifecycleScope.launch {
            try {
                val downloadData = SigningRestService.getDownloadData(CookieManager.getInstance().getCookie(url))
                val filename = downloadData.filename ?: LocalizedStrings.getString("signing_download_filename")
                val downloadRequest = DownloadManager.Request(Uri.parse(downloadData.downloadURL))

                if (CoreConfig.serverConfig.authUser.isNullOrEmpty().not()) {
                    val basicAuth = "Basic " + Base64.encodeToString((CoreConfig.serverConfig.authUser + ":" + CoreConfig.serverConfig.authPassword).toByteArray(), Base64.NO_WRAP)
                    downloadRequest.addRequestHeader("Authorization", basicAuth)
                }

                downloadRequest.setTitle(downloadData.title ?: LocalizedStrings.getString("signing_download_title"))
                downloadRequest.setDescription(downloadData.description ?: LocalizedStrings.getString("signing_download_description"))
                downloadRequest.allowScanningByMediaScanner()
                downloadRequest.setAllowedOverMetered(true)
                downloadRequest.setAllowedOverRoaming(true)
                downloadRequest.setMimeType(MimeTypeMap.getSingleton().getMimeTypeFromExtension(filename.split(".")[1]))
                downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                downloadRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)

                (webView.context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(downloadRequest)

                Snackbar.make(viewBinding.signingSnackbar, LocalizedStrings.getString("signing_download_snackbar_message"), Snackbar.LENGTH_LONG)
                    .show()
            } catch (err: Throwable) {
                log("error during signing document download", err)
                Snackbar.make(viewBinding.signingSnackbar, LocalizedStrings.getString("signing_download_message_error"), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onBackPressed() {
        if (isSigning && wasSigningSuccessful.not()) {
            showChoiceDialog(
                context = this,
                title = LocalizedStrings.getString("signing_cancel_dialog_title"),
                msg = LocalizedStrings.getString("signing_cancel_dialog_message"),
                positiveButton = LocalizedStrings.getString("default_btn_yes"),
                onPositive = {
                    setResult(SdkResultCodes.RESULT_OK.id)
                    finish()
                },
                negativeButton = LocalizedStrings.getString("default_btn_no")
            )
        }
        if (isSigning && wasSigningSuccessful || isCallback) {
            closeSdkWithSuccess()
        } else {
            super.onBackPressed()
        }
    }
}
