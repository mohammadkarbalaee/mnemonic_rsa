package de.post.ident.internal_core.start

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.squareup.moshi.Moshi

data class BundleParameter<T: Any>(
        val putParameter: (Bundle, T) -> Unit,
        val getParameter: (Bundle?) -> T?
) {
    companion object {
        inline fun <reified T : Any> moshi(moshi: Moshi, name: String): BundleParameter<T> {
            val adapter = moshi.adapter(T::class.java)
            val putExtra: (Bundle, T) -> Unit = { bundle: Bundle, value: T ->
                bundle.putString(name, adapter.toJson(value))
            }
            val getExtra: (Bundle?) -> T? = { bundle: Bundle? ->
                bundle?.getString(name)?.let {
                    adapter.fromJson(it)
                }
            }
            return BundleParameter(putExtra, getExtra)
        }
    }
}

// create a new instance of a fragment and add bundle parameters
fun <F : Fragment, T : Any> F.withParameter(data: T, bundleParameter: BundleParameter<T>): F {
    val bundle = this.arguments ?: Bundle()
    bundleParameter.putParameter(bundle, data)
    this.arguments = bundle
    return this
}

