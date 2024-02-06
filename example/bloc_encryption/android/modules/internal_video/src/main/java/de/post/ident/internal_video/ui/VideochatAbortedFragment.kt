package de.post.ident.internal_video.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.R
import de.post.ident.internal_video.databinding.PiFragmentVideochatAbortedBinding
import kotlinx.coroutines.launch

@Keep
enum class AbortReason {
    ENDED_BY_USER,
    ENDED_BY_AGENT,
    ACTIVITY_DESTROYED_LOW_MEMORY,
    RECORDING_ERROR,
    IDENTIFICATION_COMPLETED,
    IDENTIFICATION_IN_PROGRESS,
    REROUTING_COUNT_MAX_EXCEEDED
}

@JsonClass(generateAdapter = true)
data class VideochatAbortedData(val reason: AbortReason)

class VideochatAbortedFragment : BaseVideoFragment() {
    companion object {
        private val PARAMETER: BundleParameter<VideochatAbortedData> = BundleParameter.moshi(
            CoreEmmiService.moshi, "DATA")
        fun newInstance(reason: AbortReason): VideochatAbortedFragment = VideochatAbortedFragment().withParameter(VideochatAbortedData(reason), PARAMETER)
    }

    private lateinit var viewBinding: PiFragmentVideochatAbortedBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentVideochatAbortedBinding.inflate(inflater, container, false)
        val abortReason = requireNotNull(PARAMETER.getParameter(arguments)).reason
        var titleKey = "identification_canceled"
        var subTitleKey = "identification_canceled_subtitle"
        var iconResId = R.drawable.pi_ic_fail
        var retryButtonVisible = true
        when (abortReason) {
            AbortReason.ENDED_BY_USER -> {
                titleKey = "identification_canceled_user"
            }
            AbortReason.RECORDING_ERROR -> {
                subTitleKey = "identification_canceled_subtitle_RECORDING_ERROR"
            }
            AbortReason.IDENTIFICATION_IN_PROGRESS -> {
                subTitleKey = "identification_canceled_subtitle_IDENTIFICATION_IN_PROGRESS"
                iconResId = R.drawable.pi_check_circle
                retryButtonVisible = false
            }
            AbortReason.IDENTIFICATION_COMPLETED -> {
                subTitleKey = "identification_canceled_subtitle_IDENTIFICATION_COMPLETED"
                iconResId = R.drawable.pi_check_circle
                retryButtonVisible = false
            }
            AbortReason.REROUTING_COUNT_MAX_EXCEEDED -> {
                subTitleKey = "identification_canceled_subtitle_REROUTING_COUNT_MAX_EXCEEDED"
            }
            else -> {}
        }
        viewBinding.identCancelTitle.text = LocalizedStrings.getString(titleKey)
        viewBinding.identCancelTitle.setCompoundDrawablesWithIntrinsicBounds(iconResId, 0, 0, 0)
        viewBinding.identCancelSubtitle.text = LocalizedStrings.getString(subTitleKey)
        viewBinding.buttonRetryCall.text = LocalizedStrings.getString("default_btn_retry")
        viewBinding.buttonRetryCall.isVisible = retryButtonVisible

        val eventContext = mapOf("reason" to abortReason.name)
        emmiReporter.send(logEvent = LogEvent.SF_IDENT_CANCELLED, eventContext = eventContext)

        if (videoManager.caseResponse.toMethodSelection != null && Commons.skipMethodSelection.not()) {
            viewBinding.buttonToMethodSelection.text = videoManager.caseResponse.toMethodSelection?.text
        } else {
            viewBinding.buttonToMethodSelection.text = LocalizedStrings.getString("default_btn_quit")
        }

        viewBinding.buttonToMethodSelection.setOnClickListener {
            requireActivity().finish()
        }

        viewBinding.buttonRetryCall.setOnClickListener {
            lifecycleScope.launch {
                (requireActivity() as VideoIdentActivity).startVideoProcess(false)
            }
        }

        return viewBinding.root
    }
}