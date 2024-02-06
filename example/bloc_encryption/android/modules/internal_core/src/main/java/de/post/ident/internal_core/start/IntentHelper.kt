package de.post.ident.internal_core.start

import android.content.Intent
import android.os.Bundle
import com.squareup.moshi.Moshi

data class ExtraParameter<T>(
        val putExtra: (Intent, T) -> Unit,
        val getExtra: (Intent) -> T?
) {
    companion object {
        inline fun <reified T : Any> moshi(moshi: Moshi, name: String): ExtraParameter<T> {
            val bundleParameter: BundleParameter<T> = BundleParameter.moshi(moshi, name)

            val putExtra: (Intent, T) -> Unit = { intent, value ->
                val bundle = Bundle()
                bundleParameter.putParameter(bundle, value)
                intent.putExtra(name, bundle)
            }
            val getExtra = { intent: Intent ->
                bundleParameter.getParameter(intent.getBundleExtra(name))
            }
            return ExtraParameter(putExtra, getExtra)
        }
    }
}

fun <T> Intent.putParameter(parameter: ExtraParameter<T>, value: T) { parameter.putExtra(this, value) }
fun <T> Intent.getParameter(parameter: ExtraParameter<T>): T? = parameter.getExtra(this)

fun String.Companion.parameter(name: String, defaultValue: String): ExtraParameter<String> {
    val putExtra: (Intent, String) -> Unit = { intent, value ->
        intent.putExtra(name, value)
    }
    val getExtra = { intent: Intent ->
        intent.getStringExtra(name) ?: defaultValue
    }
    return ExtraParameter(putExtra, getExtra)
}

fun Boolean.Companion.parameter(name: String, defaultValue: Boolean): ExtraParameter<Boolean> {
    val putExtra: (Intent, Boolean) -> Unit = { intent, value ->
        intent.putExtra(name, value)
    }
    val getExtra = { intent: Intent ->
        intent.getBooleanExtra(name, defaultValue)
    }
    return ExtraParameter(putExtra, getExtra)
}
