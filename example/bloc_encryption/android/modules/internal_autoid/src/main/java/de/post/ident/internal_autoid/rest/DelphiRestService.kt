package de.post.ident.internal_autoid.rest

import com.squareup.moshi.Moshi
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.R
import de.post.ident.internal_core.rest.EnumTypeAdapterFactory
import de.post.ident.internal_core.rest.GeneralError
import de.post.ident.internal_core.rest.KRestApi
import de.post.ident.internal_core.util.LocalizedStrings
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

private const val HEADER_USER_AGENT = "User-Agent"

object DelphiRestService {

    private var restApiInternal: KRestApi? = null
    val restApi: KRestApi
        get() = checkNotNull(restApiInternal) { "NovomindRestService not initialized!" }

    val moshi = Moshi.Builder()
        .add(EnumTypeAdapterFactory()) // Added to support enum default values
        .build()

    init {
        val config = CoreConfig.serverConfig

        restApiInternal = KRestApi("https://${config.agentDomain}/", moshi) {
            errorHandler = { err ->
                when (err) {
                    is UnknownHostException ->
                        GeneralError(
                            LocalizedStrings.getString(R.string.err_dialog_no_connection_text),
                            GeneralError.Type.NO_CONNECTION
                        )
                    is SocketTimeoutException, is TimeoutException ->
                        GeneralError(
                            LocalizedStrings.getString(R.string.err_dialog_technical_error),
                            GeneralError.Type.SERVER_ERROR
                        )
                    is SSLHandshakeException, is SSLPeerUnverifiedException, is SSLException ->
                        GeneralError(
                            LocalizedStrings.getString(R.string.err_dialog_ssl_error_text),
                            GeneralError.Type.SSL_ERROR
                        )
                    else -> err
                }
            }

            addHeader(HEADER_USER_AGENT, config.userAgent)
            if (config.authUser != null && config.authPassword != null) {
                addBasicAuth(config.authUser!!, config.authPassword!!)
            }
            addPinnedDomains(config.pinnedDomains)
        }
    }

}

