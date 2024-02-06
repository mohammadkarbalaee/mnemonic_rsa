package de.post.ident.internal_core.util

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import de.post.ident.internal_core.R
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.databinding.PiToolbarBinding
import java.util.*

/**
 * Base activity which has to be used by _ALL_ other activities to set the FLAG_SECURE (disable screenshots & blank screen in app overview)
 */
open class PiBaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (CoreConfig.isScreenshotEnabled.not()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        AppUpdateService().resumeUpdate(this)
    }

    private var locale: Locale? = null

    override fun onStart() {
        super.onStart()
        val newLocale = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            resources.configuration.locale
        } else {
            resources.configuration.locales[0]
        }

        locale?.let { locale ->
            if (locale.language != newLocale.language) {
                log("locale changed; sdk restart required")
                finish()
            }
        } ?: run {
            log("locale set")
            locale = newLocale
        }
    }
}

fun PiBaseActivity.showBackButton(show: Boolean, onClicked: () -> Unit) {
    var binding: PiToolbarBinding = PiToolbarBinding.inflate(layoutInflater)
    showBackButton(binding.toolbarActionbar, show, onClicked)
}

fun showBackButton(toolbar: MaterialToolbar, show: Boolean, onClicked: () -> Unit) {
    if (show) {
        toolbar.setNavigationIcon(R.drawable.pi_ic_arrow_back)
    } else {
        toolbar.navigationIcon = null
    }
    toolbar.setNavigationOnClickListener {
        onClicked()
    }
}
