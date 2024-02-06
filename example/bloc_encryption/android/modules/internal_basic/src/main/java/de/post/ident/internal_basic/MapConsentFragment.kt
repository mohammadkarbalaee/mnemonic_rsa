package de.post.ident.internal_basic

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.post.ident.internal_basic.databinding.PiMapConsentFragmentBinding
import de.post.ident.internal_core.util.LocalizedStrings

class MapConsentFragment : Fragment() {
    private lateinit var viewBinding: PiMapConsentFragmentBinding
    private lateinit var onConsentListener: () -> Unit

    companion object {
        fun newInstance(onClick: () -> Unit): MapConsentFragment {
            val fragment = MapConsentFragment()
            fragment.setOnConsentListener(onClick)
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiMapConsentFragmentBinding.inflate(inflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.consentBtn.setOnClickListener { onConsentListener() }

        viewBinding.consentHeader.text = LocalizedStrings.getString("maps_consent_title")
        viewBinding.consentTxt.text = LocalizedStrings.getHtmlString("maps_consent_text")
        viewBinding.consentTxt.movementMethod = LinkMovementMethod.getInstance()
        viewBinding.consentBtn.text = LocalizedStrings.getString("default_btn_confirm")
    }

    private fun setOnConsentListener(listener: () -> Unit) {
        this.onConsentListener = listener
    }
}