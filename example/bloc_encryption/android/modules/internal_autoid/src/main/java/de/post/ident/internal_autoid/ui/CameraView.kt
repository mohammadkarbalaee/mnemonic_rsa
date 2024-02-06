package de.post.ident.internal_autoid.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper.getMainLooper
import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.core.view.isVisible
import de.post.ident.internal_autoid.R
import de.post.ident.internal_autoid.databinding.PiCameraAutoidBinding
import de.post.ident.internal_autoid.databinding.PiCameraReviewAutoidBinding
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.showAlertDialog

@Keep
private enum class DocumentTypeList(
    val front: DocumentTemplate,
    val back: DocumentTemplate,
    val documentType: DocumentType?
) {
    ID_CARD_STANDARD(
        front = DocumentTemplate(
            R.drawable.pi_template_doc_check_id_card_back,
            titleKey = "auto_id_id_card_front_info_text",
            descriptionKey = "auto_id_id_card_front_call_to_action_info_text",
            infoKey = "autoid_docScan_id_front",
            infoKeyIntro = "autoid_docScan_id_front_pre"
        ),
        back = DocumentTemplate(
            R.drawable.pi_template_doc_check_id_card_back,
            titleKey = "auto_id_id_card_back_info_text",
            descriptionKey = "auto_id_id_card_back_call_to_action_info_text",
            infoKey = "autoid_docScan_id_front",
            infoKeyIntro = "autoid_docScan_id_front_pre"
        ),
        documentType = DocumentType.ID_CARD,
    ),
    PASSPORT(
        front = DocumentTemplate(
            R.drawable.pi_template_doc_check_passport_cover,
            titleKey = "auto_id_pass_info_text",
            descriptionKey = "auto_id_pass_cover_call_to_action_info_text",
            infoKey = "autoid_docScan_pass_front",
            infoKeyIntro = "autoid_docScan_pass_front_pre"
        ),
        back = DocumentTemplate(
            R.drawable.pi_template_doc_check_passport_data,
            titleKey = "auto_id_pass_info_text",
            descriptionKey = "auto_id_pass_data_call_to_action_info_text",
            infoKey = "autoid_docScan_pass_back",
            infoKeyIntro = "autoid_docScan_pass_front_pre"
        ),
        documentType = DocumentType.PASSPORT,
    )
}

data class DocumentTemplate(@DrawableRes val overlay: Int, val titleKey: String, val descriptionKey: String, val infoKey: String, val infoKeyIntro: String)

class CameraView(private val context: Context,
                 private val binding: PiCameraAutoidBinding,
                 var isVideo: Boolean = false,
                 buttonClicked: (BtnAction) -> Unit
) {

    private val animationHandler by lazy { Handler(getMainLooper()) }

    init {
        binding.continueBtn.setOnClickListener { buttonClicked(BtnAction.TAKE) }
        binding.continueBtn.contentDescription = LocalizedStrings.getString("photo_btn_take_photo")
        binding.continueBtn.text = LocalizedStrings.getString("photo_btn_take_photo")
        adjustWireframesToDisplay()
    }

    @Keep
    enum class BtnAction {
        TAKE, START
    }

    @Keep
    enum class DocumentSide {
        FRONT, BACK, OTHER
    }

    fun updateViewPhoto(
        side: DocumentSide,
        type: DocumentType,
    ) {
        toggleViewVisibilities()
        val doc = DocumentTypeList.values().first { it.documentType == type }
        updateTitle(doc, side)
    }

    private fun updateTitle(doc: DocumentTypeList, side: DocumentSide) {
        binding.continueBtn.isEnabled = false

        when (side) {
            DocumentSide.FRONT -> {
                binding.piIcon.setOnClickListener { showAlertDialog(context, LocalizedStrings.getString(doc.front.infoKey)) }
                binding.piTitle.text = LocalizedStrings.getString(doc.front.titleKey)
                binding.piTitle.gravity = (Gravity.START or Gravity.CENTER)
                binding.overlay.setImageResource(doc.front.overlay)
                startTextHandler(doc.front.descriptionKey)
            }
            DocumentSide.BACK -> {
                binding.piIcon.setOnClickListener { showAlertDialog(context, LocalizedStrings.getString(doc.back.infoKey)) }
                binding.piTitle.text = LocalizedStrings.getString(doc.back.titleKey)
                binding.piTitle.gravity = (Gravity.START or Gravity.CENTER)
                binding.overlay.setImageResource(doc.back.overlay)
                startTextHandler(doc.back.descriptionKey)
            }
            DocumentSide.OTHER -> {
            }
        }
    }

    private fun startTextHandler(descriptionKey: String) {
        animationHandler.postDelayed({
            binding.piTitle.text = LocalizedStrings.getString(descriptionKey)
            binding.piTitle.gravity = Gravity.CENTER
            binding.continueBtn.isEnabled = true
        }, 3000)
    }

    private fun adjustWireframesToDisplay() {
        val displayMetrics = context.resources.displayMetrics
        val aspectRatio = displayMetrics.heightPixels.toFloat() / displayMetrics.widthPixels.toFloat()
        if (aspectRatio >= 1.9) {
            binding.overlay.apply {
                scaleX = 1.2f
                scaleY = 1.2f
            }
        }
    }

    private fun toggleViewVisibilities(isVideo: Boolean = false, isRecording: Boolean = false) {
        this.isVideo = isVideo
        binding.piTitle.isVisible = isVideo.not()
        binding.templateContainer.isVisible = isVideo.not()

        binding.continueBtn.isEnabled = isRecording.not()
    }
}

class ImageReviewView(val binding: PiCameraReviewAutoidBinding, buttonClicked: (BtnAction) -> Unit) {
    init {
        binding.piBtnRetry.setOnClickListener { buttonClicked(BtnAction.RETRY) }
        binding.piBtnUse.setOnClickListener { buttonClicked(BtnAction.USE) }
        binding.piBtnUse.text = LocalizedStrings.getString("confirm_image")
        binding.piBtnRetry.text = LocalizedStrings.getString("retry_label")
        binding.descriptionReview.text = LocalizedStrings.getString("auto_id_success_info_text")
    }

    @Keep
    enum class BtnAction {
        USE, RETRY
    }

    fun showImage(bitmap: Bitmap) {
        binding.piCapturedImagePreview.setImageBitmap(bitmap)
        binding.descriptionReview.isVisible = true
        showProgress(false)
    }

    fun showProgress(showProgress: Boolean) {
        binding.piProgressSpinner.isVisible = showProgress
        binding.descriptionReview.isVisible = showProgress.not()
        binding.buttonContainer.isVisible = showProgress.not()
        binding.titleContainer.isVisible = showProgress.not()
        binding.piCapturedImagePreview.isVisible = showProgress.not()
        binding.piCapturedImagePreview.isVisible = showProgress.not()
    }
}