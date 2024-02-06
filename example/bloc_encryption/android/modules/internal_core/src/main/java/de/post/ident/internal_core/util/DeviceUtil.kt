package de.post.ident.internal_core.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.tech.TagTechnology
import android.os.Build
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import androidx.annotation.Keep
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.scottyab.rootbeer.RootBeer
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.rest.JsonEnum
import de.post.ident.internal_core.rest.JsonEnumClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.lang.reflect.Field
import java.net.InetAddress

@Keep
enum class NFCStatus {
    ENABLED, DISABLED, NOT_PRESENT, NOT_SUPPORTED
}

@Keep
enum class NfcCapabilityResult {
    SUPPORTED, NOT_SUPPORTED, QUERY_NOT_ALLOWED;
}

fun checkForRoot(context: Context) = RootBeer(context).isRooted

fun getNFCStatus(ctx: Context): NFCStatus? {
    val adapter = getNfcAdapter(ctx)
    return if (adapter != null) {
        when {
            checkExtendedLengthSupport(adapter) == NfcCapabilityResult.NOT_SUPPORTED -> NFCStatus.NOT_SUPPORTED
            adapter.isEnabled -> NFCStatus.ENABLED
            else -> NFCStatus.DISABLED
        }
    } else {
        NFCStatus.NOT_PRESENT
    }
}

fun getNfcAdapter(ctx: Context): NfcAdapter? {
    return (ctx.getSystemService(Context.NFC_SERVICE) as NfcManager).defaultAdapter
}

fun checkExtendedLengthSupport(adapter: NfcAdapter): NfcCapabilityResult {
    if (Build.VERSION.SDK_INT < 28) {
        val tagObject = getTagObject(adapter)
        val extSup = isExtendedLengthSupported(tagObject)
        val maxLen = getMaxTransceiveLength(tagObject)
        // some Devices only support one of the two methods (isExtendedLengthSupported or getMaxTransceiveLength) -> null check for each
        if (extSup != null) {
            return if (extSup) NfcCapabilityResult.SUPPORTED else NfcCapabilityResult.NOT_SUPPORTED
        }
        if (maxLen != null) {
            return if (maxLen > 370) NfcCapabilityResult.SUPPORTED else NfcCapabilityResult.NOT_SUPPORTED
        }
        return NfcCapabilityResult.NOT_SUPPORTED
    }
    log("NFC Query not allowed")
    return NfcCapabilityResult.QUERY_NOT_ALLOWED
}

private fun isExtendedLengthSupported(tagObj: Any): Boolean? {
    try {
        val extSupFun = tagObj.javaClass.getMethod("getExtendedLengthApdusSupported")
        val extSupObj = extSupFun.invoke(tagObj)
        if (extSupObj is Boolean) {
            return extSupObj
        }
    } catch (err: java.lang.Exception) {
        log("Error requesting extended length support.")
    }
    return null
}

private fun getMaxTransceiveLength(tagObj: Any): Int? {
    var tech = 3

    try {
        val isoDep: Field = TagTechnology::class.java.getDeclaredField("ISO_DEP")
        tech = isoDep.getInt(null)
    } catch (err: java.lang.Exception) {
        log("Error requesting ISO_DEP field.")
    }

    try {
        val tlenFun = tagObj.javaClass.getMethod("getMaxTransceiveLength", Integer.TYPE)
        val lenObj = tlenFun.invoke(tagObj, tech)
        if (lenObj is Int) {
            return lenObj
        }
    } catch (err: java.lang.Exception) {
        log("Requesting max transceive length is not allowed.")
    }
    return null
}

private fun getTagObject(nfcAdapter: NfcAdapter): Any {
    return try {
        val getTagFun = nfcAdapter.javaClass.getMethod("getTagService")
        getTagFun.invoke(nfcAdapter)
    } catch (err: Exception) {
        log("Error requesting TagService object.", err)
        NfcCapabilityResult.NOT_SUPPORTED
    }
}

@JsonClass(generateAdapter = true)
data class ConnectionTypeDTO(
        @Json(name = "connectionType") val type: ConnectionType
) {
    @JsonEnumClass()
    @Keep
    enum class ConnectionType {
        @JsonEnum(name = "", default = true) UNKNOWN,
        @JsonEnum(name = "WIFI") WIFI,
        @JsonEnum(name = "CELLULAR") CELLULAR
    }
}

@Keep
enum class NetworkTypeMobile {
    UNKNOWN, TWO_G, THREE_G, FOUR_G, FIVE_G
}

fun getNetworkConnectionType(ctx: Context): ConnectionTypeDTO.ConnectionType {
    val cm: ConnectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val activeNetworkInfo = cm.activeNetworkInfo
    val isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected

    if (isConnected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return ConnectionTypeDTO.ConnectionType.CELLULAR
            } else if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return ConnectionTypeDTO.ConnectionType.WIFI
            }
        }
    }
    return ConnectionTypeDTO.ConnectionType.UNKNOWN
}
fun getWifiSignalStrength(context: Context): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val wifiInfo = wifiManager.connectionInfo
    if (wifiManager.isWifiEnabled && wifiInfo != null) {
        val rssi = wifiInfo.rssi
        val level = WifiManager.calculateSignalLevel(wifiInfo.rssi, 5)
        val linkSpeed = wifiInfo.linkSpeed
        log("WIFI_SIGNAL Signalstärke in dBm: $rssi, RSSI-Level: $level")
        map["wifiRssi"] = rssi.toString()
        map["wifiLevel"] = level.toString()
        map["wifiLinkspeed"] = linkSpeed.toString()
    } else log("is wifi enabled: ${wifiManager.isWifiEnabled}")
    return map
}

@SuppressLint("MissingPermission")
fun getmobileDataNetworkType(context: Context): NetworkTypeMobile {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    log("networkTypeMobile: ${telephonyManager.networkType}")
    return when (telephonyManager.networkType) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN -> {
            log(NetworkTypeMobile.TWO_G.name)
            NetworkTypeMobile.TWO_G
        }

        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP -> {
            log(NetworkTypeMobile.THREE_G.name)
            NetworkTypeMobile.THREE_G
        }

        TelephonyManager.NETWORK_TYPE_LTE -> {
            log(NetworkTypeMobile.FOUR_G.name)
            NetworkTypeMobile.FOUR_G
        }

        TelephonyManager.NETWORK_TYPE_NR -> {
            log(NetworkTypeMobile.FIVE_G.name)
            NetworkTypeMobile.FOUR_G
        }

        else -> {
            log(NetworkTypeMobile.UNKNOWN.name)
            NetworkTypeMobile.UNKNOWN
        }
    }
}

fun getMobileDataSignalStrength(context: Context, callback: (strength: Int) -> Unit) {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    val phoneStateListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            super.onSignalStrengthsChanged(signalStrength)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cellSignalStrengths = signalStrength.cellSignalStrengths
                for (cellSignalStrength in cellSignalStrengths) {
                    when (cellSignalStrength) {
                        is CellSignalStrengthGsm -> {
                            // get GSM (2G) signal strength
                            val gsmSignalStrength = cellSignalStrength.dbm
                            log("SignalStrength GSM Signal Strength: $gsmSignalStrength")
                            callback(gsmSignalStrength)
                        }

                        is CellSignalStrengthWcdma -> {
                            // get WCDMA (3G) signal strength
                            val wcdmaSignalStrength = cellSignalStrength.dbm
                            log("SignalStrength WCDMA Signal Strength: $wcdmaSignalStrength")
                            callback(wcdmaSignalStrength)
                        }

                        is CellSignalStrengthLte -> {
                            // get LTE (4G) signal strength
                            val lteRsrp = cellSignalStrength.rsrp
                            log("SignalStrength LTE RSRP: $lteRsrp")
                            callback(lteRsrp)
                        }

                        is CellSignalStrengthNr -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                // get NR (5G) signal strength
                                val nrRsrp = cellSignalStrength.csiRsrp
                                log("SignalStrength NR RSRP: $nrRsrp")
                                callback(nrRsrp)
                            }
                        }
                    }
                }
            }
        }
    }
    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
}

suspend fun getLatency(address: String?): Int {
    var latency = 0
    try {
        CoroutineScope(Dispatchers.IO).async {
            val inetAddress = InetAddress.getByName(address)
            val startTime = System.currentTimeMillis()
            if (inetAddress.isReachable(300)) {
                val endTime = System.currentTimeMillis()
                latency = (endTime - startTime).toInt()
                log("latency: $latency")
            }
            latency
        }.await()
    } catch (e: Throwable) {
        log("ip address not reachable")
    }
    return latency
}

class AppUpdateService {

    lateinit var appUpdateManager: AppUpdateManager
    private lateinit var updateinfoding: AppUpdateInfo

    fun updateAvailable(ctx: Context): Boolean {
        val appUpdateManager = AppUpdateManagerFactory.create(ctx)
        var isUpdateAvailable = false
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                updateinfoding = appUpdateInfo
                isUpdateAvailable = true
            }
        }
        return isUpdateAvailable
    }

    fun startAppUpdate() {
        appUpdateManager.startUpdateFlowForResult(updateinfoding, AppUpdateType.IMMEDIATE, Activity(), 0)
    }

    fun resumeUpdate(ctx: Context) {
        val appUpdateManager = AppUpdateManagerFactory.create(ctx)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, Activity(), 0)
            }
        }
    }
}

