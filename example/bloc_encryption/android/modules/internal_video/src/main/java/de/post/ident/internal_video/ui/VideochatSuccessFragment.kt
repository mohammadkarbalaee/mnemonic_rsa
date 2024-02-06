package de.post.ident.internal_video.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.reporting.IdentMethod
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.PostidentTypeDTO
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.WebviewActivity
import de.post.ident.internal_video.R
import de.post.ident.internal_video.VideoState
import de.post.ident.internal_video.databinding.PiFragmentSuccessBinding


class VideochatSuccessFragment : BaseVideoFragment() {
    companion object {
        fun newInstance(): VideochatSuccessFragment = VideochatSuccessFragment()
    }

    private var isSigning = false

    private lateinit var viewBinding: PiFragmentSuccessBinding

    init {
        videoManager.currentState.observe(this) {
            onProgressUpdate(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentSuccessBinding.inflate(inflater, container, false)

        isSigning = videoManager.caseResponse.postidentType == PostidentTypeDTO.SIGNING_CLASSIC
        initView(viewBinding)

        return viewBinding.root
    }

    private fun initView(vb: PiFragmentSuccessBinding) {

        val caseResponseModules = (activity as VideoIdentActivity).getCaseResponse().modules
        val identStatus = caseResponseModules.identStatus
        val signingUrl = caseResponseModules.identCallTermination?.successUrl?.webUrl

        identStatus?.let { status ->
            vb.statusTitle.text = status.title
            vb.statusSubTitle.text = status.subtitle
            vb.btnFinishPostident.text = status.continueButton?.text ?: LocalizedStrings.getString("default_btn_quit")

            if (isSigning && CoreConfig.isSdk.not() && signingUrl.isNullOrBlank().not()) {
                vb.statusHint.text = status.hint
                vb.btnFinishPostident.setOnClickListener { openSigningWebview("$signingUrl?headless=true") }
            } else {
                vb.btnFinishPostident.setOnClickListener {
                    emmiReporter.send(LogEvent.SF_REDIRECT)
                    (requireActivity() as VideoIdentActivity).finishVideochatWithSuccess()
                }
            }

            vb.statusIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(),
                    when (status.icon) {
                        "OK_ICON" -> R.drawable.pi_ic_check
                        "SIGNING_ICON" -> R.drawable.pi_ic_blank_pdf_screen
                        else -> R.drawable.pi_ic_check
                    }
            ))
        }
    }

    private fun openSigningWebview(url: String) {
        emmiReporter.send(LogEvent.SF_REDIRECT)
        videoManager.endCall()
        WebviewActivity.start(requireActivity(), url, true)
    }

    private fun onProgressUpdate(serverState: VideoState) {
        when (serverState) {
            VideoState.CALL_ENDED_SUCCESSFULLY -> {
                emmiReporter.send(LogEvent.SF_SUCCESS, IdentMethod.VIDEO)
            }
            else -> { /* ignore */ }
        }
    }
}