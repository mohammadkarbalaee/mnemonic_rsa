package de.post.ident.internal_autoid.ui

import androidx.annotation.Keep
import androidx.core.view.isVisible
import de.post.ident.internal_autoid.AutoIdentActivity
import de.post.ident.internal_autoid.R
import de.post.ident.internal_autoid.databinding.PiFragmentAutoidErrorBinding
import de.post.ident.internal_core.util.LocalizedStrings

@Keep
data class AutoIdErrorData(val identStep: AutoIdentActivity.Screen, val title: String, val description: String)

val errorViewDataList = listOf(
    AutoIdErrorData(
        identStep = AutoIdentActivity.Screen.DOC_CHECK_FRONT,
        title = "doc_check_error_title",
        description = "autoid_error_description"
    ),
    AutoIdErrorData(
        identStep = AutoIdentActivity.Screen.DOC_CHECK_BACK,
        title = "doc_check_error_title",
        description = "autoid_error_description"
    ),
    AutoIdErrorData(
        identStep = AutoIdentActivity.Screen.DVF,
        title = "dvf_error_title",
        description = "autoid_error_description"
    ),
    AutoIdErrorData(
        identStep = AutoIdentActivity.Screen.FVLC,
        title = "fvlc_error_title",
        description = "autoid_error_description"
    )
)

class ErrorView(
    val binding: PiFragmentAutoidErrorBinding,
    private val act: AutoIdentActivity,
    private val buttonClicked: (BtnAction) -> Unit
) {
    init {
        binding.retryBtn.isEnabled = true
        binding.retryBtn.isVisible = true
        binding.retryBtn.text = LocalizedStrings.getString("default_btn_retry")


        binding.backToMethodSelectionBtn.isEnabled = true
        binding.backToMethodSelectionBtn.text = LocalizedStrings.getString("default_btn_back_to_methodselection")
        binding.backToMethodSelectionBtn.setOnClickListener {
            buttonClicked(BtnAction.ABORT)
            setButtonsEnabled(false)
        }

        update()
    }

    fun update() {
        errorViewDataList.forEach {
            if (it.identStep.name == act.getCurrentIdentStep().name) {
                binding.title.text = LocalizedStrings.getString(it.title)
                binding.errorReason.isVisible = false
                binding.description.text = LocalizedStrings.getString(it.description)
            }
        }

        if (act.getStepIteration() < act.MAX_STEP_RETRIES) {
            setButtonsEnabled(true)
            binding.icon.setImageResource(when (act.getCurrentIdentStep()) {
                AutoIdentActivity.Screen.FVLC -> R.drawable.pi_id_card_search_bio
                else -> R.drawable.pi_id_card_search
            })
            binding.retryBtn.setOnClickListener {
                setButtonsEnabled(false)
                buttonClicked(BtnAction.RETRY)
            }
        } else {
            binding.retryBtn.isVisible = false
            binding.backToMethodSelectionBtn.isEnabled = true
            binding.description.text = LocalizedStrings.getString("autoid_error_description_noretry")
            binding.errorReason.isVisible = false
            binding.icon.setImageResource(R.drawable.pi_error_exclamation_mark)
        }

        val status = act.status
        if (status in listOf(AutoIdStatus.RESULT_INCOMPLETE, AutoIdStatus.REENTRY_INCOMPLETE)) {
            status.titleTextKey?.let { binding.title.text = LocalizedStrings.getString(it) }
            binding.errorReason.text = status.userFeedback
            binding.errorReason.isVisible = true
            binding.retryBtn.isVisible = true
            binding.icon.setImageResource(R.drawable.pi_error_exclamation_mark)
        }
    }

    private fun setButtonsEnabled(isEnabled: Boolean = true) {
        binding.retryBtn.isEnabled = isEnabled
        binding.backToMethodSelectionBtn.isEnabled = isEnabled
    }

    @Keep
    enum class BtnAction {
        RETRY, ABORT
    }
}