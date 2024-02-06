package de.post.ident.internal_core.rest

import com.squareup.moshi.Moshi
import de.post.ident.internal_core.CoreConfig
import java.util.*

object SigningRestService {

    private var restApiInternal: KRestApi? = null
    private val restApi: KRestApi
        get() = checkNotNull(restApiInternal) { "SigningRestService not initialized!" }

    init {
        val config = CoreConfig.serverConfig

        restApiInternal = KRestApi(config.signingUrl, Moshi.Builder().build()) {
            errorHandler = { err -> err }

            addPinnedDomains(config.pinnedDomains)
            if (config.authUser != null && config.authPassword != null) {
                addBasicAuth(config.authUser, config.authPassword)
            }
            addHeader("Accept-Language", Locale.getDefault().language)
        }
    }

    suspend fun getDownloadData(cookies: String): SigningDownloadDTO = restApi
        .get()
        .addHeader("Cookie", cookies)
        .path("downloadUrl")
        .execute(SigningDownloadDTO::class)
}