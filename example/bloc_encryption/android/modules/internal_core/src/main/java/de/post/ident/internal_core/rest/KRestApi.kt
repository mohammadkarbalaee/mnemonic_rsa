package de.post.ident.internal_core.rest

import android.util.Base64
import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import de.post.ident.internal_core.BuildConfig
import de.post.ident.internal_core.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

data class PinnedDomain(val domain: String, val certificateFingerPrint: String)

class HttpException(val code: Int, override val message: String, val body: String?) : IOException(message)
class JsonParsingError(): IOException()

class KRestApi(
        private val baseUrl: String,
        val moshi: Moshi,
        private val config: ConfigScope.() -> Unit = {}
) {
    companion object {
        val TIMEOUT: Long = 30
        val CONNECT_TIMEOUT: Long = 10
        val WRITE_TIMEOUT: Long = 10
        val READ_TIMEOUT: Long = 25
    }

    private var errorHandler: (Throwable) -> Throwable = { it }

    var restClient: OkHttpClient

    /**
     * creates a client with configured logging and header interceptors
     */
    init {
        val builder = OkHttpClient.Builder().callTimeout(TIMEOUT, TimeUnit.SECONDS).readTimeout(READ_TIMEOUT, TimeUnit.SECONDS).connectTimeout(
            CONNECT_TIMEOUT, TimeUnit.SECONDS).writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
        val scope = ConfigScope(builder)
        config(scope)
        errorHandler = scope.errorHandler
        createLoggingInterceptor(builder)
        restClient = builder.build()
    }
    private val client: OkHttpClient
        get() {
            return restClient
        }

    fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket = client.newWebSocket(request, listener)

    fun get() = MethodScope(Request.Builder().get())

    fun post(body: RequestBody) = MethodScope(Request.Builder().post(body))

    inline fun <reified T : Any> post(body: T): MethodScope =
            post(moshi.adapter(T::class.java).toJson(body).toRequestBody("application/json".toMediaType()))

    fun put(body: RequestBody) = MethodScope(Request.Builder().put(body))

    inline fun <reified T : Any> put(body: T): MethodScope =
            put(moshi.adapter(T::class.java).toJson(body).toRequestBody("application/json".toMediaType()))

    fun patch(body: RequestBody) = MethodScope(Request.Builder().patch(body))

    inline fun <reified T : Any> patch(body: T): MethodScope =
            patch(moshi.adapter(T::class.java).toJson(body).toRequestBody("application/json".toMediaType()))

    inner class MethodScope(private val requestBuilder: Request.Builder) {
        fun path(path: String): UrlScope = UrlScope(requestBuilder, baseUrl.toHttpUrl().newBuilder().addPathSegments(path.trimStart('/')))

        fun url(url: String) = UrlScope(requestBuilder, url.toHttpUrl().newBuilder()) // absolute URL

        fun addHeader(name: String, value: String?): MethodScope {
            if (value != null) {
                requestBuilder.addHeader(name, value)
            }
            return this
        }
    }

    private fun Call.executeWithErrorHandler() {
        executeWithErrorHandler { }
    }
    private inline fun <R> Call.executeWithErrorHandler(block: (Response) -> R): R = try {
        execute().use { response ->
            checkResponse(response)
            block(response)
        }
    } catch (err: Throwable) {
        log(err)
        throw errorHandler(err)
    }

    inner class UrlScope(private val requestBuilder: Request.Builder, private val urlBuilder: HttpUrl.Builder) {
        suspend fun execute() {
            withContext(Dispatchers.IO) {
                try {
                    requestBuilder.url(urlBuilder.build())
                    client.newCall(requestBuilder.build()).executeWithErrorHandler()
                } catch (err: Throwable) {
                    if (isActive) { // only throw exception when coroutine scope is still active
                        throw err
                    }
                }
            }
        }

        suspend fun executeForHttpStatusCode(): Int {
            val result = withContext(Dispatchers.IO) {
                try {
                    requestBuilder.url(urlBuilder.build())
                    client.newCall(requestBuilder.build()).executeWithErrorHandler() {
                        return@withContext it.code
                    }
                } catch (err: Throwable) {
                    if (isActive) { // only throw exception when coroutine scope is still active
                        throw err
                    } else {

                    }
                }
            }
            return result as Int
        }

        suspend fun <T : Any> executeList(kClass: KClass<T>): List<T> {
            val listType = Types.newParameterizedType(List::class.java, kClass.java)
            val listAdapter = moshi.adapter<List<T>>(listType)
            return execute(listAdapter) ?: throw IOException("No body!")
        }

        suspend fun <T : Any> execute(kClass: KClass<T>): T = execute(moshi.adapter(kClass.java))
        suspend fun <T : Any> execute(jsonAdapter: JsonAdapter<T>): T = withContext(Dispatchers.IO) {
            requestBuilder.addHeader("Accept", "application/json")
            requestBuilder.url(urlBuilder.build())
            try {
                client.newCall(requestBuilder.build()).executeWithErrorHandler { response ->
                    response.body?.use { body ->
                        val result = jsonAdapter.fromJson(body.source())
                        result?.let {
                            if (it::class.isSubclassOf(HttpStatusCodeDTO::class)) {
                                (it as HttpStatusCodeDTO).httpStatusCode = response.code
                            }
                        }
                        return@withContext result ?: throw JsonParsingError()
                    }
                }
                throw IOException("No body!")
            } catch (err: Throwable) {
                ensureActive() // if the coroutine parent is already cancelled a CancellationException is thrown
                throw err
            }
        }
        suspend fun <T : Any> executeNA(kClass: KClass<T>): T? = executeNA(moshi.adapter(kClass.java))
        suspend fun <T : Any> executeNA(jsonAdapter: JsonAdapter<T>): T? = withContext(Dispatchers.IO) {
            requestBuilder.addHeader("Accept", "application/json")
            requestBuilder.url(urlBuilder.build())
            try {
                client.newCall(requestBuilder.build()).executeWithErrorHandler { response ->
                    response.body?.use { body ->
                        if (body.source().exhausted()) {
                            return@withContext null
                        } else {
                            return@withContext jsonAdapter.fromJson(body.source())
                                ?: throw JsonParsingError()
                        }
                    }
                }
            } catch (err: Throwable) {
                ensureActive()
                throw err
            }
            null
        }

        fun queryParam(name: String, value: String?): UrlScope {
            if (value != null) { // do not add if null
                urlBuilder.addQueryParameter(name, value)
            }
            return this
        }
    }

    private fun checkResponse(response: Response) {
        when (val code = response.code) {
            in 200..300 -> return
            else -> throw HttpException(code, response.message, response.body?.string())
        }
    }

    private fun createLoggingInterceptor(client: OkHttpClient.Builder) {
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
                override fun log(message: String) {
                    Log.v("OkHttpClient", message)
                }
            })
            logging.level = HttpLoggingInterceptor.Level.BODY
            client.addInterceptor(logging)
        }
    }
}

class ConfigScope(val builder: OkHttpClient.Builder) {
    private val headersMap = mutableMapOf<String, String>()
    var errorHandler: (Throwable) -> Throwable = { it }

    private val headerInterceptor: Interceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            with(original.newBuilder()) {
                headersMap.forEach { (name, value) ->
                    addHeader(name, value)
                }
                return chain.proceed(build())
            }
        }
    }

    fun addHeader(name: String, value: String?) {
        if (headersMap.isEmpty()) { // first use
            addInterceptor(headerInterceptor)
        }

        value?.let {
            headersMap[name] = it
        }
    }

    fun addInterceptor(interceptor: Interceptor) {
        builder.addInterceptor(interceptor)
    }

    fun addBasicAuth(user: String, password: String) {
        val authHeaderKey = "Authorization"

        val userAndPassword = "$user:$password"
        val encoded = Base64.encodeToString(userAndPassword.toByteArray(StandardCharsets.US_ASCII), Base64.NO_WRAP)
        val credentials = "Basic $encoded"

        addHeader(authHeaderKey, credentials)
    }

    fun addPinnedDomains(pinnedDomainList: List<PinnedDomain>) {
        val certificatePinner = CertificatePinner.Builder().apply {
            pinnedDomainList.forEach {
                add(it.domain, it.certificateFingerPrint)
            }
        }.build()
        builder.certificatePinner(certificatePinner)
    }
}