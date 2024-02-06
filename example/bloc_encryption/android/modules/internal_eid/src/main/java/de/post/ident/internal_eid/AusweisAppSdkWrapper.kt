package de.post.ident.internal_eid

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.IBinder
import android.os.RemoteException
import androidx.appcompat.app.AppCompatActivity
import com.governikus.ausweisapp2.IAusweisApp2Sdk
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import de.post.ident.internal_core.rest.EnumTypeAdapterFactory
import de.post.ident.internal_core.util.log
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.jvm.Throws

class AusweisAppSdkWrapper(private val activity: Activity, private val eventListener: (EidMessage) -> Unit) {

    var ausweisApp2Sdk: IAusweisApp2Sdk? = null
    private var nfcDispatcher: NfcForegroundDispatcher? = null
    private var service: ServiceConnection? = null
    private val sdkCallback = LocalCallback()

    private val moshi = Moshi.Builder()
            .add(EnumTypeAdapterFactory()) // Added to support enum default values
            .build()


    suspend fun send(cmd: EidCommandDto) {
        if (ausweisApp2Sdk == null) {
            service = bind()
        }
        try {
            val adapter: JsonAdapter<EidCommandDto> = moshi.adapter(EidCommandDto::class.java)
            val cmdJson = adapter.toJson(cmd)

            log("##### SEND COMMAND: $cmd")

            ausweisApp2Sdk?.send(sdkCallback.mSessionID, cmdJson)
        } catch (e: RemoteException) {
            log("bindAusweisApp2Sdk: ", e)
        }
    }

    private fun receiveMessage(msg: String) {
        val adapter: JsonAdapter<EidMessageDto> = moshi.adapter(EidMessageDto::class.java)
        val message = adapter.fromJson(msg)

        log("##### RECEIVE MESSAGE: $message")

        when (message?.msg) {
            EidMessageDto.MessageType.ACCESS_RIGHTS -> {
                message.accessRights?.let { eventListener(it) }
            }
            EidMessageDto.MessageType.CERTIFICATE -> {
                message.certificate?.let {
                    it.validity = message.validity
                    eventListener(it)
                }
            }
            EidMessageDto.MessageType.INFO -> {
                message.versionInfo?.let { eventListener(it) }
            }
            EidMessageDto.MessageType.INSERT_CARD -> {
                eventListener(InsertCardEvent(message.error))
            }
            EidMessageDto.MessageType.READER -> {
                eventListener(ReadEvent(message.card, simulated = message.name == "Simulator"))
            }
            EidMessageDto.MessageType.ENTER_PIN -> {
                eventListener(EnterPinEvent(card = requireNotNull(message.reader?.card)))
            }
            EidMessageDto.MessageType.ENTER_CAN -> {
                eventListener(EnterPinEvent(requireCan = true, error = message.error != null, requireNotNull(message.reader?.card)))
            }
            EidMessageDto.MessageType.ENTER_NEW_PIN -> {
                eventListener(EnterNewPinEvent)
            }
            EidMessageDto.MessageType.AUTH -> {
                if (message.result != null) {
                    eventListener(EidAuthEvent(message.url, message.result))
                } else {
                    //see: https://www.ausweisapp.bund.de/sdk/messages.html#auth
                    message.error?.let {
                        log("auth error: $it")
                        eventListener(EidErrorMessage(EidError.AUTHENTICATION_ERROR.exception()))
                    }
                }
            }
            EidMessageDto.MessageType.CHANGE_PIN -> {
                eventListener(ChangePinResultEvent(message.success ?: false))
            }
            EidMessageDto.MessageType.BAD_STATE -> {
                eventListener(BadStateEvent(message.error))
            }
            else -> {
                log("unhandled message: $message")
            }
        }
    }

    private suspend fun bind() = suspendCoroutine<ServiceConnection> { cont ->
        var ausweisApp2Connection: ServiceConnection? = null
        var contResumed = false

        fun resume(exception: Exception? = null) {
            if (contResumed) return

            exception?.let {
                cont.resumeWithException(exception)
            } ?: run {
                cont.resume(checkNotNull(ausweisApp2Connection))
            }
            contResumed = true
        }

        ausweisApp2Connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                try {
                    ausweisApp2Sdk = IAusweisApp2Sdk.Stub.asInterface(service)
                    connectSdk()
                    resume()
                } catch (err: Exception) {
                    resume(err)
                }
            }

            override fun onServiceDisconnected(className: ComponentName) {
                ausweisApp2Sdk = null
            }
        }

        val serviceIntent = Intent("com.governikus.ausweisapp2.START_SERVICE")
        serviceIntent.setPackage(activity.packageName)
        activity.bindService(serviceIntent, ausweisApp2Connection, AppCompatActivity.BIND_AUTO_CREATE)
    }

    private fun connectSdk() {

        ausweisApp2Sdk?.connectSdk(sdkCallback)

        if (ausweisApp2Sdk != null && nfcDispatcher == null) {
            nfcDispatcher = NfcForegroundDispatcher(activity, ausweisApp2Sdk!!, sdkCallback.mSessionID) { error -> eventListener(EidErrorMessage(error.exception())) }
            nfcDispatcher!!.enable()
        }
    }

    fun enableNfcDispatcher() {
        nfcDispatcher?.enable()
    }

    fun disableNfcDispatcher() {
        nfcDispatcher?.disable()
    }

    suspend fun updateNfcTag(tag: Tag) {
        if (ausweisApp2Sdk == null) {
            bind()
        }
        ausweisApp2Sdk?.updateNfcTag(sdkCallback.mSessionID, tag)
    }

    private class NfcForegroundDispatcher(private val mActivity: Activity, pSdk: IAusweisApp2Sdk, pSdkSessionID: String?, errorCallback: (EidError) -> Unit) {
        private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(mActivity)
        private val nfcFlags: Int = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        private val nfcReaderCallback: NfcAdapter.ReaderCallback

        fun enable() {
            nfcAdapter?.enableReaderMode(mActivity, nfcReaderCallback, nfcFlags, null)
        }

        fun disable() {
            nfcAdapter?.disableReaderMode(mActivity)
        }

        init {
            nfcReaderCallback = NfcAdapter.ReaderCallback { pTag ->
                log("Tag detected: $pTag")
                log("Tech list: ${pTag.techList}")
                val isoDep: IsoDep? = IsoDep.get(pTag)

                isoDep?.let {
                    log("IsoDep detected: $it")
                    log("maxTransceiveLength: ${it.maxTransceiveLength}")
                    log("isExtendedLengthApduSupported: ${it.isExtendedLengthApduSupported}")
                    if (it.maxTransceiveLength < 370 && it.isExtendedLengthApduSupported.not()) {
                        errorCallback(EidError.EXTENDED_LENGTH_ISSUE)
                        return@ReaderCallback
                    }
                }

                if (pTag.techList.contains(IsoDep::class.java.name)) {
                    pSdk.updateNfcTag(pSdkSessionID, pTag)
                }
            }
        }
    }

    inner class LocalCallback : com.governikus.ausweisapp2.IAusweisApp2SdkCallback.Stub() {
        var mSessionID: String? = null

        @Throws(RemoteException::class)
        override fun sessionIdGenerated(pSessionId: String, pIsSecureSessionId: Boolean) {
            mSessionID = pSessionId
        }

        @Throws(RemoteException::class)
        override fun receive(pJson: String) {
            receiveMessage(pJson)
        }

        @Throws(RemoteException::class)
        override fun sdkDisconnected() {
            unbind()
        }
    }

    fun unbind() {
        service?.let {
            try {
                activity.unbindService(it)
            } catch (e: java.lang.RuntimeException) {
                log("service already unbound or destroyed", e)
            }
        }
    }
}