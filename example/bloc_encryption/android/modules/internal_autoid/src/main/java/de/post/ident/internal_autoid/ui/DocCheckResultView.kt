package de.post.ident.internal_autoid.ui

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.Keep
import androidx.core.view.isVisible
import com.squareup.moshi.JsonClass
import de.post.ident.internal_autoid.AutoIdentActivity
import de.post.ident.internal_autoid.databinding.PiDocCheckResultRowBinding
import de.post.ident.internal_autoid.databinding.PiFragmentDocCheckResultBinding
import de.post.ident.internal_core.rest.AutoIdentCheckDocumentDTO
import de.post.ident.internal_core.util.LocalizedStrings

@JsonClass(generateAdapter = true)
data class ResultData(val locoKey: String, val docField: String, val displayIndex: Int)

class DocCheckResultView(
    private val binding: PiFragmentDocCheckResultBinding,
    activity: AutoIdentActivity,
    buttonClicked: (BtnAction) -> Unit
) {
    private val act = activity
    private val resultDataList = listOf(
        ResultData("first_names", "first_names", 1),
        ResultData("last_name", "last_name", 2),
        ResultData("birth_name", "birth_name", 3),
        ResultData("birth_date", "birth_date", 4),
        ResultData("birth_place", "birth_place", 5),
        ResultData("nationality", "nationality", 6),
        ResultData("doc_number", "doc_number", 7),
        ResultData("issue_date", "issue_date", 8),
        ResultData("expiry_date", "expiry_date", 9),
        ResultData("authority", "authority", 10),
        ResultData("place_of_issue", "place_of_issue", 11),
        ResultData("ad_street", "ad_street", 12),
        ResultData("ad_postal_code", "ad_postal_code", 13),
        ResultData("ad_city", "ad_city", 14),
        ResultData("ad_country", "ad_country", 15),
    )

    init {
        binding.retryBtn.text = LocalizedStrings.getString("autoid_retry_btn")
        binding.retryBtn.setOnClickListener {
            buttonClicked(BtnAction.RETRY)
        }

        binding.continueBtn.text = LocalizedStrings.getString("autoid_confirm_data_btn")
        binding.continueBtn.setOnClickListener {
            buttonClicked(BtnAction.CONTINUE)
        }
    }

    @Keep
    enum class BtnAction {
        RETRY, CONTINUE
    }

    fun updateResultScreen(documentCheckResult: AutoIdentCheckDocumentDTO?, ctx: Context) {
        val result = documentCheckResult?.resultData

        binding.overlay.isVisible = false
        binding.progressBar.isVisible = false

        if (act.getStepIteration() < act.MAX_STEP_RETRIES) {
            binding.retryBtn.isEnabled = true
            binding.retryBtn.isVisible = true
            binding.continueBtn.text = LocalizedStrings.getString("autoid_confirm_data_btn")
        } else {
            binding.retryBtn.isVisible = false
            binding.continueBtn.text = LocalizedStrings.getString("autoid_confirm_send_data_btn")
            binding.confirmDataInfo.text = LocalizedStrings.getString("autoid_confirm_data_info")
            binding.confirmDataInfo.isVisible = true
        }

        if (documentCheckResult?.isComplete == true) {
            binding.continueBtn.text = LocalizedStrings.getString("autoid_confirm_data_btn")
            binding.continueBtn.isVisible = true
            binding.continueBtn.isEnabled = true
        }

        binding.title.text = LocalizedStrings.getString("doc_check_result_title")
        binding.subtitle.text = LocalizedStrings.getString("doc_check_result_subtitle")

        // remove check for docfieldFound empty field shall not be displayed
        val docfields = result?.fields
        resultDataList.forEach { resultData ->
            var docFieldFound = false
            docfields?.forEach { docfield ->
                if (resultData.docField == docfield.code) {
                    docFieldFound = true
                    addResultToView(resultData.locoKey, docfield.value)
                }
            }
            if (!docFieldFound) addResultToView(resultData.locoKey, "")
        }
    }

    private fun addResultToView(locoKey: String, value: String?) {
        val resultBinding = PiDocCheckResultRowBinding.inflate(LayoutInflater.from(act), binding.resultContainer, true)
        resultBinding.key.text = LocalizedStrings.getString(locoKey)
        resultBinding.value.text = value
    }
}