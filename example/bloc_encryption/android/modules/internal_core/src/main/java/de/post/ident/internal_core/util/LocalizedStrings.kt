package de.post.ident.internal_core.util

import android.annotation.SuppressLint
import android.text.Spanned
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.BuildConfig
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent

@JsonClass(generateAdapter = true)
data class OfflineLocalizedStrings(val map: Map<String, String>)

@SuppressLint("StaticFieldLeak")
object LocalizedStrings {

    private val kPrefs = KPrefDelegate(CoreConfig.appContext, "localized_strings")
    private var internalTextMap by kPrefs.objNA<OfflineLocalizedStrings>("string_map")

    private val emmiReporter = EmmiCoreReporter
    private val textMap: Map<String, String>
        get() = checkNotNull(internalTextMap?.map) { "Not initialized!" }


    private val ctx = CoreConfig.appContext

    fun init(textMap: Map<String, String>) {
        internalTextMap = OfflineLocalizedStrings(textMap)
    }

    /**
     * Use this method to get translation for a key.
     */
    fun getString(key: String, vararg formatArgs: Any): String {
        val text = textMap[key] ?: keyMissing(key)
        return text.replace("\\n","\n").format(args = *formatArgs)
    }

    /**
     * Use this method to get a translation that contains HTML.
     */
    fun getHtmlString(key: String, vararg formatArgs: Any): Spanned = HtmlCompat.fromHtml(getString(key, formatArgs), HtmlCompat.FROM_HTML_MODE_LEGACY)

    /**
     * Use this method to get translation for a key including a quantity (plural) string.
     */
    fun getQuantityString(key: String, quantity: Int, vararg formatArgs: Any): String = getString(if (quantity == 1) key else "$key-plural", *formatArgs)

    /**
     * Use this method to get translations that maybe used before initialization is ready.
     */
    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String {
        val text = internalTextMap?.let {
            val key = ctx.resources.getResourceName(resId)
            textMap[key]
        } ?: ctx.createConfigurationContext(ctx.resources.configuration).getString(resId)
        return text.format(args = *formatArgs)
    }

    /**
     * Handling missing key.
     */
    private fun keyMissing(key: String): String {
        emmiReporter.send(LogEvent.AC_TEXTS_MISSING, eventContext = mapOf("key" to key))
        return if (BuildConfig.DEBUG) key else ""
    }
}