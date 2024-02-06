package de.post.ident.internal_core.rest

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import de.post.ident.internal_core.*
import de.post.ident.internal_core.reporting.AutoIdErrorCode
import de.post.ident.internal_core.reporting.EmmiReport
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.EMPTY_REQUEST
import okio.*
import org.json.JSONObject
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.NoSuchElementException


/*
  https://pi.k8s.dev.7p-group.com/emmi/swagger-ui/index.html?configUrl=/emmi/v3/api-docs/swagger-config
 */

private const val HTTP_MISSING_DATA = 428

fun Throwable.getUserMessage(): String = when (this) {
    is GeneralError -> userMessage
    is PostidentError -> userMessage
    else -> LocalizedStrings.getString("err_dialog_technical_error")
}

interface PostidentError {
    val userMessage: String
}

class CaseResponseError(override val userMessage: String, val resultCode: SdkResultCodes) : IOException(userMessage), PostidentError
class CaseResponseMissingData(val contactData: ContactDataDTO) : IOException("Missing contact data!")
class NoAgentAvailableError(override val userMessage: String) : IOException(userMessage), PostidentError
class OcrResponseError(override val userMessage: String, val error: OcrErrorDto) : IOException(userMessage), PostidentError
class FieldResponseError(val fieldErrorList: List<DataFieldErrorDTO>) : IOException("Errors detected in field data detected!")
class ServerNotAvailableError : IOException("Server not available (received HTTP 502), user redirected to Makeup-Room")
class EmptyBodyException : IOException("Empty body")
class SigningRedirectEvent(val url: String) : IOException("Signing Redirect")
class VideochatRequirementsError: NoSuchElementException("Client does not meet requirements for videochat")
class EidRequirementsError: NoSuchElementException("Client does not meet requirements for eID")
class NoSuitableIdentMethodAvailable : NoSuchElementException("No intersection between portal methods and sdk methods")

class GeneralError(val userMessage: String, val type: Type = Type.DEFAULT) : IOException() {
    @Keep
    enum class Type { DEFAULT, NO_CONNECTION, SSL_ERROR, SERVER_ERROR, INTERFACE_KEY }
}

@Keep
data class ServerConfig(val authUser: String?, val authPassword: String?, val userAgent: String, val emmiUrl: String, val agentDomain: String,
                        val mlServerUrl: String, val interfaceKey: String, val pinnedDomains: List<PinnedDomain>, val hostAppVersion: String?,
                        val hostAppId: String?, val signingUrl: String)

object CoreEmmiService {

    private const val HEADER_KEY = "PI-Interface-Key"
    private const val HEADER_PLATFORM = "PI-Platform"
    private const val HEADER_APP_VERSION = "PI-App-Version"
    private const val HEADER_APP_ID = "PI-App-Id"
    private const val HEADER_SDK_VERSION = "PI-SDK-Version"
    private const val HEADER_OS_VERSION = "PI-OS-Version"
    private const val HEADER_DEVICE_NAME = "PI-Device-Name"
    private const val HEADER_CLIENT_ID = "PI-Client-Id"
    private const val HEADER_USER_AGENT = "User-Agent"
    private const val HEADER_ACCEPT_LANGUAGE = "Accept-Language"

    private var restApiInternal: KRestApi? = null
    val restApi: KRestApi
        get() = checkNotNull(restApiInternal) { "CoreEmmi not initialized!" }

    val moshi = Moshi.Builder()
            .add(EnumTypeAdapterFactory()) // Added to support enum default values
            .build()

    fun init(config: ServerConfig) {

        restApiInternal = KRestApi(config.emmiUrl, moshi) {
            errorHandler = { err ->
                when (err) {
                    is UnknownHostException ->
                        GeneralError(LocalizedStrings.getString(R.string.err_dialog_no_connection_text), GeneralError.Type.NO_CONNECTION)
                    is SocketTimeoutException, is TimeoutException ->
                        GeneralError(LocalizedStrings.getString(R.string.err_dialog_technical_error), GeneralError.Type.SERVER_ERROR)
                    is SSLHandshakeException, is SSLPeerUnverifiedException, is SSLException ->
                        GeneralError(LocalizedStrings.getString(R.string.err_dialog_ssl_error_text), GeneralError.Type.SSL_ERROR)
                    // EOFException is thrown by the Json parser when the stream reached the end. So maybe not in every situation really empty body!
                    is EOFException -> EmptyBodyException()
                    else -> err
                }
            }
            addHeader(HEADER_USER_AGENT, config.userAgent)
            addHeader(HEADER_KEY, config.interfaceKey)
            addHeader(HEADER_PLATFORM, if(CoreConfig.isSdk) "Android_SDK" else "Android")
            addHeader(HEADER_SDK_VERSION, BuildConfig.SDK_MODULE_VERSION)
            addHeader(HEADER_OS_VERSION, Build.VERSION.RELEASE)
            addHeader(HEADER_DEVICE_NAME, Build.BRAND + " " + Build.MODEL)
            addHeader(HEADER_ACCEPT_LANGUAGE, Locale.getDefault().language)
            addHeader(HEADER_APP_VERSION, config.hostAppVersion)
            addHeader(HEADER_APP_ID, config.hostAppId)
            addHeader(HEADER_CLIENT_ID, Commons.clientId)

            if (config.authUser != null && config.authPassword != null) {
                addBasicAuth(config.authUser, config.authPassword)
            }

            addPinnedDomains(config.pinnedDomains)
        }
    }

    //region GENERAL

    suspend fun appConfig(): AppConfigDTO = restApi.get().path("app-config").execute(AppConfigDTO::class)

    suspend fun sendReport(emmiReports: List<EmmiReport>) {
        restApi.post(emmiReports).path("reports/app/logs").execute()
    }

    suspend fun sendContactData(userData: Map<String, String?>, caseId: String) {
        try {
            restApi.put(userData).path("cases/$caseId/contactData").execute()
        } catch (err: Throwable) {
            throw GeneralError(LocalizedStrings.getString("err_contact_data_http_error"))
        }
    }

    suspend fun getCaseInformationByPath(path: String): CaseResponseDTO = getCaseInformation {
        restApi.get().path(path).execute(CaseResponseDTO::class)
    }

    suspend fun getCaseInformationByCaseId(caseId: String): CaseResponseDTO = getCaseInformation {
        restApi.get().path("cases/$caseId").execute(CaseResponseDTO::class)
    }

    suspend fun sendUserStarts(caseId: String) {
        restApi.post(EMPTY_REQUEST).path("cases/$caseId/userStarts").execute()
    }

    suspend fun getProcessDescription(path: String): ProcessDescriptionDTO = getCaseInformation {
        restApi.get().path("$path/processDescription").execute(ProcessDescriptionDTO::class)
    }

    private suspend fun <T> getCaseInformation(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (err: HttpException) {
            log(err)

            when (err.code) {
                HTTP_MISSING_DATA -> {
                    val body = err.body
                    if (body != null) {
                        val caseIDError: ContactDataDTO? = ContactDataDTOJsonAdapter(moshi).fromJson(body)
                        throw CaseResponseMissingData(requireNotNull(caseIDError))
                    } else {
                        throw caseIdErrorHandling(err)
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_BAD_REQUEST, HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_MOVED_PERM -> {
                    err.body?.let { body ->
                        CaseIDErrorDTOJsonAdapter(moshi).fromJson(body)?.let {
                            when (err.code) {
                                HttpURLConnection.HTTP_NOT_FOUND -> throw CaseResponseError(it.errorText!!, SdkResultCodes.ERROR_CASE_NOT_FOUND)
                                HttpURLConnection.HTTP_UNAUTHORIZED -> throw GeneralError(it.errorText ?: "", GeneralError.Type.INTERFACE_KEY)
                                HttpURLConnection.HTTP_MOVED_PERM -> handleSigningRedirect(it)
                                else -> throw CaseResponseError(it.errorText!!, SdkResultCodes.ERROR_CASE_INVALID)
                            }
                        }
                    }
                    throw caseIdErrorHandling(err)
                }
                else -> throw caseIdErrorHandling(err)
            }
        }
    }

    private fun handleSigningRedirect(err: CaseIDErrorDTO) {
        if (CoreConfig.isSdk) {
            throw CaseResponseError(LocalizedStrings.getString("case_id_gone"), SdkResultCodes.ERROR_CASE_DONE)
        } else {
            throw SigningRedirectEvent("${err.signingURL}?headless=true")
        }
    }

    private fun caseIdErrorHandling(err: Throwable): Throwable {
        return when {
            err is HttpException && err.code == HttpURLConnection.HTTP_NOT_FOUND ->
                CaseResponseError(LocalizedStrings.getString("case_id_not_found"), SdkResultCodes.ERROR_CASE_NOT_FOUND)
            err is HttpException && err.code == HttpURLConnection.HTTP_GONE ->
                CaseResponseError(LocalizedStrings.getString("case_id_gone"), SdkResultCodes.ERROR_CASE_DONE)
            err is HttpException && err.code == HTTP_MISSING_DATA ->
                GeneralError(LocalizedStrings.getString("case_id_missing_data"))
            err is GeneralError -> err
            else -> GeneralError(LocalizedStrings.getString("case_id_service_unavailable"))
        }
    }

    suspend fun sendTermsAccepted(target: String) = restApi.put(TermsAcceptedDTO()).path(target).execute()

    suspend fun sendCreateAttempt(userFrontendInformation: UserFrontendInformationDTO, caseId: String, identMethod: String) =
            restApi.post(userFrontendInformation).path("cases/$caseId/$identMethod/attempts").execute(AttemptResponseDTO::class)

    suspend fun sendCustomerFeedback(rating: Int, feedbackText: String, path: String, chatId: Int? = null) {
        val result = CustomerFeedbackResultDTO(rating, feedbackText, chatId, checkNotNull(Commons.caseId))
        restApi.post(result).path(path).execute()
    }

    //endregion

    //region VIDEO

    suspend fun getServiceCenterInfo(callcenterCategory: String?, caseId: String? = null): ServiceInfoDTO =
            restApi.get().path("service-center/info").queryParam("category", callcenterCategory).queryParam("caseId", caseId).execute(ServiceInfoDTO::class)


    suspend fun getChatLaunchId(caseId: String, attemptId: String) =
            restApi.post(EMPTY_REQUEST).path("cases/$caseId/videoIdent/attempts/$attemptId/chatLaunch").execute(ChatLaunchIdDTO::class)


    suspend fun sendAttemptDataMakeupRoom(caseId: String, attemptId: String, attemptData: MakeupRoomDataDTO) {
        restApi.patch(attemptData).path("cases/$caseId/videoIdent/attempts/$attemptId/makeup").execute()
    }

    suspend fun sendAttemptDataConnectionQuality(caseId: String, attemptId: String, attemptData: VideoConnectionQualityDTO) {
        restApi.patch(attemptData).path("cases/$caseId/videoIdent/attempts/$attemptId").execute()
    }

    /**
     * Fetch image and highlighting coordinates from EMMI.
     * If country and documentType are unknown (null) EMMI will return the german ID card by default.
     */
    suspend fun getDocumentData(path: String, country: String?, documentType: String?): DocumentDataDto =
            restApi.get().path(path).queryParam("country", country).queryParam("type", documentType).execute(DocumentDataDto::class)

    /**
     * path is fetched from emmi: /api/cases/{caseId}/videoIdent/userSelfAssessment/fields
     */
    suspend fun getUsaFields(path: String): List<DataFieldDTO> = restApi.get().path(path).executeList(DataFieldDTO::class)

    /**
     * path is fetched from emmi: /api/cases/{caseId}/videoIdent/attempts/{attemptId}/userSelfAssessment
     */
    suspend fun sendUsaFields(path: String, userData: Map<String, String?>) {
        try {
            return restApi.put(userData).path(path).execute()
        } catch (err: HttpException) {
            log(err)
            when (err.code) {
                HTTP_MISSING_DATA -> {
                    val body = err.body
                    if (body != null) {
                        val ocrError = checkNotNull(OcrErrorDtoJsonAdapter(moshi).fromJson(body))
                        throw OcrResponseError(ocrError.description, ocrError)
                    }
                }
                HttpURLConnection.HTTP_BAD_REQUEST -> {
                    err.body?.let { body ->
                        val listType = Types.newParameterizedType(List::class.java, DataFieldErrorDTO::class.java)
                        val listAdapter = moshi.adapter<List<DataFieldErrorDTO>>(listType)
                        val errors = checkNotNull(listAdapter.fromJson(body))
                        throw FieldResponseError(errors)
                    }
                }
                HttpURLConnection.HTTP_BAD_GATEWAY -> throw ServerNotAvailableError()
                else -> throw err
            }
        }
    }

    data class FileUploadItem(val fileName: String, val widthPx: Int = 0, val heightPx: Int = 0, val jpegArrayData: ByteArray)

    suspend fun uploadPhoto(path: String, data: List<FileUploadItem>) {
        val mediaType: MediaType = "image/jpeg".toMediaType()
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        for (item in data) {
            builder.addFormDataPart("files", item.fileName.replace("jpeg", "jpg"), item.jpegArrayData.toRequestBody(mediaType))
        }
        val requestBody: RequestBody = builder.build()
        try {
            restApi.post(requestBody).path(path).execute()
        } catch (err: HttpException) {
            when (err.code) {
                HTTP_MISSING_DATA -> {
                    val body = err.body
                    if (body != null) {
                        val ocrError = checkNotNull(OcrErrorDtoJsonAdapter(moshi).fromJson(body))
                        throw OcrResponseError(ocrError.description, ocrError)
                    }
                }
            }
            throw err
        }
    }

    suspend fun uploadPhotoCrop(caseId: String, attemptId: String, jpegArrayData: ByteArray): SelfServicePhotoCropResponseDto {
        val mediaType: MediaType = "image/jpeg".toMediaType()
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("files", "image.jpg", jpegArrayData.toRequestBody(mediaType))
        val requestBody = builder.build()
        return restApi.post(requestBody).path("cases/$caseId/videoIdent/attempts/$attemptId/selfServicePhotos/crop")
                .execute(SelfServicePhotoCropResponseDto::class)
    }

    //endregion

    //region BASIC

    suspend fun getCoupon(path: String): CouponDTO = withContext(Dispatchers.IO) {
        restApi.get().path(path).execute(CouponDTO::class)
    }

    //endregion

    //region EID

    suspend fun sendAttemptDataEid(caseId: String, attemptId: String, attemptData: EidAttemptDataDTO) {
        restApi.patch(attemptData).path("cases/$caseId/eidIdent/attempts/$attemptId").execute()
    }

    //endregion

    //region PHOTO

    suspend fun uploadPhotoDocuments(caseId: String, file: File, listener: (max: Long, value: Long) -> Unit) {
        restApi.put(CountingRequestBody(file.asRequestBody("application/zip".toMediaType()), listener)).path("cases/$caseId/photoIdent/documents").execute()
    }

    suspend fun getPhotoIdentInformationByCaseId(caseId: String): CaseResponseDTO = getCaseInformation {
        restApi.get().path("cases/$caseId/photoIdent").execute(CaseResponseDTO::class)
    }

    //endregion

    //region AutoId

    suspend fun mpFinish(caseId: String, attemptId: String) =
        restApi.post(caseId).path("/cases/${caseId}/autoIdent/attempts/${attemptId}/mp/finish").execute(StatusAndResultDTO::class)

    suspend fun sendUserDocScans(caseId: String, attemptId: String, userEntryDoc: String?): UserDocScanDTO {
        val jsonObject = JSONObject()
        jsonObject.put("userEntryDocTypeMajorAndConstructionCode", userEntryDoc)
        val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        return restApi.post(body).path("/cases/$caseId/autoIdent/attempts/$attemptId/mp/userDocScans").execute(UserDocScanDTO::class)
    }

    suspend fun sendDocumentCheck(caseId: String, identId: String, attemptId: String, userDocScan: UserDocScanDTO, frontMediaRecordId: Int? = null, backMediaRecordId: Int? = null, data: List<FileUploadItem>): AutoIdentCheckDocumentDTO {
        val mediaType: MediaType = "image/jpeg".toMediaType()
        val mediaTypeDocument: MediaType = "application/json; charset=utf-8".toMediaType()

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("front",data[0].fileName.replace("jpeg", "jpg"), data[0].jpegArrayData.toRequestBody(mediaType))
        builder.addFormDataPart("back",data[1].fileName.replace("jpeg", "jpg"), data[1].jpegArrayData.toRequestBody(mediaType))

        val jsonObject = JSONObject()
        jsonObject.put("identId", identId)
        jsonObject.put("userDocScanId", userDocScan.id.toString())
        jsonObject.put("frontMediaRecordId", frontMediaRecordId.toString())
        jsonObject.put("backMediaRecordId", backMediaRecordId.toString())
        val body = jsonObject.toString().toRequestBody(mediaTypeDocument)
        builder.addFormDataPart("document","",body = body)
        val requestBody: RequestBody = builder.build()

        return restApi.post(requestBody).path("cases/${caseId}/autoIdent/attempts/${attemptId}/mp/documentCheck").execute(AutoIdentCheckDocumentDTO::class)
    }

    suspend fun dvfNextRun(caseId: String, attemptId: String): DvfNextRunResponseDTO {
        return restApi.post(EMPTY_REQUEST).path("cases/$caseId/autoIdent/attempts/$attemptId/mp/dvf/nextRun").execute(DvfNextRunResponseDTO::class)
    }

    suspend fun fvlcNextRun(caseId: String, attemptId: String): FvlcNextRunResponseDTO {
        return restApi.post(EMPTY_REQUEST).path("cases/$caseId/autoIdent/attempts/$attemptId/mp/fvlc/nextRun").execute(FvlcNextRunResponseDTO::class)
    }

    suspend fun sendIncomplete(caseId: String, attemptId: String, errorCode: AutoIdErrorCode, internalNote: String?): MpFinishResultDTO {
        val jsonObject = JSONObject()
        jsonObject.put("errorCodeId", errorCode.id)
        internalNote.let { jsonObject.put("internalNote", internalNote) }
        val body = jsonObject.toString().toRequestBody("application/json".toMediaType())
        val result = restApi.post(body).path("cases/$caseId/autoIdent/attempts/$attemptId/mp/incomplete").executeForHttpStatusCode()
        return MpFinishResultDTO(httpStatusCode = result)
    }

    suspend fun getIdentStatus(caseId: String) = restApi.get().path("cases/$caseId/autoIdent/identificationStatus").execute(AutoIdentStatusDTO::class)
}

class CountingRequestBody(var delegate: RequestBody, private var listener: (max: Long, value: Long) -> Unit): RequestBody() {

    override fun contentType(): MediaType? {
        return delegate.contentType()
    }

    override fun contentLength(): Long {
        try {
            return delegate.contentLength()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return -1
    }

    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink)
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    inner class CountingSink(delegate: Sink): ForwardingSink(delegate) {
        private var bytesWritten: Long = 0
        private val handler = Handler(Looper.getMainLooper())

        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            handler.post {
                listener(contentLength(), bytesWritten)
            }

        }

    }
}

object NovomindCertificateCheck {

    private var restApiInternal: KRestApi? = null

    private val restApiCert: KRestApi
        get() = checkNotNull(restApiInternal) { "NovomindRestService not initialized!" }

    private val moshi = Moshi.Builder()
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
                    else -> err
                }
            }
            addHeader("User-Agent", config.userAgent)
            if (config.authUser != null && config.authPassword != null) {
                addBasicAuth(config.authUser, config.authPassword)
            }
            addPinnedDomains(config.pinnedDomains)
        }
    }

    suspend fun testVideoCertificate() = restApiCert
        .get()
        .path("")
        .execute()
}