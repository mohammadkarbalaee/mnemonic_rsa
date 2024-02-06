package de.post.ident.internal_core.camera

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.core.view.isVisible
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.ui.recyclerview.CellRecyclerAdapter
import de.post.ident.internal_core.util.ui.recyclerview.ViewBindingCell
import de.post.ident.internal_core.R
import de.post.ident.internal_core.databinding.PiCameraBinding
import de.post.ident.internal_core.databinding.PiCameraReviewBinding
import de.post.ident.internal_core.databinding.PiTemplateViewBinding

@Keep
private enum class DocumentTypeList(val titleKey: String, val front: DocumentTemplate? = null, val back: DocumentTemplate? = null, val other: DocumentTemplate? = null, val documentType: DocumentType?) {
    PERSO(
            titleKey = "template_document_perso",
            front = DocumentTemplate(R.drawable.pi_template_document_perso_front, R.drawable.pi_template_document_perso_front_wireframe),
            back = DocumentTemplate(R.drawable.pi_template_document_perso_back, R.drawable.pi_template_document_perso_back_wireframe),
            documentType = DocumentType.IDENTIFICATION,
    ),
    PASS(
            titleKey = "template_document_pass",
            front = DocumentTemplate(R.drawable.pi_template_document_reisepass_front, R.drawable.pi_template_document_reisepass_wireframe_front),
            back = DocumentTemplate(R.drawable.pi_template_document_reisepass_back, R.drawable.pi_template_document_reisepass_wireframe_back),
            documentType = DocumentType.IDENTIFICATION,
    ),
    DL_EU(
            titleKey = "template_drivers_license_eu",
            front = DocumentTemplate(R.drawable.pi_template_driverslicense_front_eu, R.drawable.pi_template_driverslicense_frame_eu),
            back = DocumentTemplate(R.drawable.pi_template_driverslicense_back_eu, R.drawable.pi_template_driverslicense_frame_eu),
            documentType = DocumentType.DRIVERS_LICENSE,
    ),
    DL_OLD(
            titleKey = "template_drivers_license_old",
            front = DocumentTemplate(R.drawable.pi_template_driverslicense_front_old, R.drawable.pi_template_driverslicense_frame_old),
            back = DocumentTemplate(R.drawable.pi_template_driverslicense_back_old, R.drawable.pi_template_driverslicense_frame_old),
            documentType = DocumentType.DRIVERS_LICENSE,
    ),
    OTHER(
            titleKey = "template_other_format",
            documentType = null,
    )
}

@Keep
enum class DocumentType {
    IDENTIFICATION,
    DRIVERS_LICENSE
}

data class DocumentTemplate(@DrawableRes val solid: Int, @DrawableRes val wire: Int)

class CameraView(private val binding: PiCameraBinding, var isVideo: Boolean = false, buttonClicked: (BtnAction) -> Unit) {

    private lateinit var templateCellList: List<TemplateCell>
    var filename: String? = null
    private var currentFormatKey: String? = null

    init {
        binding.triggerButton.setOnClickListener { buttonClicked(if (isVideo) BtnAction.START else BtnAction.TAKE) }
        binding.switchCameraButton.setOnClickListener { buttonClicked(BtnAction.SWITCH) }
        binding.triggerButton.contentDescription = LocalizedStrings.getString("cd_take_picture")
        binding.switchCameraButton.contentDescription = LocalizedStrings.getString("cd_switch_camera")

        binding.templateNavigateNext.setOnClickListener { buttonClicked(BtnAction.NEXT) }
        binding.templateNavigatePrevious.setOnClickListener { buttonClicked(BtnAction.PREVIOUS) }
        binding.helpText.text = LocalizedStrings.getString("silhouette_text_before_recording")
        binding.viewPager.adapter = CellRecyclerAdapter(emptyList())
    }

    @Keep
    enum class BtnAction {
        TAKE, SWITCH, NEXT, PREVIOUS, START
    }

    @Keep
    enum class DocumentSide {
        FRONT, BACK, OTHER
    }

    fun updateViewPhoto(side: DocumentSide, type: DocumentType) {
        toggleViewVisibilities()
        templateCellList = DocumentTypeList.values()
                .filter { it.documentType == type || it.documentType == null }
                .map {
                    TemplateCell(when (side) {
                        DocumentSide.FRONT -> it.front
                        DocumentSide.BACK -> it.back
                        DocumentSide.OTHER -> it.other
                    })
                }
        binding.viewPager.adapter = CellRecyclerAdapter(templateCellList)
        TabLayoutMediator(binding.tabLayout, binding.viewPager, true, true) { tab, position ->
            val titleKey = DocumentTypeList
                .values().filter { it.documentType == type || it.documentType == null }.map { it.titleKey }[position]
            tab.text = LocalizedStrings.getString(titleKey)
            tab.id = when (DocumentTypeList.values().filter { it.titleKey == titleKey }.first()) { //for testautomation
                DocumentTypeList.PERSO -> R.id.cam_view_tab_perso
                DocumentTypeList.PASS -> R.id.cam_view_tab_pass
                DocumentTypeList.DL_EU -> R.id.cam_view_tab_dleu
                DocumentTypeList.DL_OLD -> R.id.cam_view_tab_dlold
                DocumentTypeList.OTHER -> R.id.cam_view_tab_other
            }
        }.attach()
        updateButton()
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButton()
                val currentFormat = DocumentTypeList.values()
                    .filter { it.documentType == type || it.documentType == null }[position]
                updateTitle(currentFormat, side)
                currentFormatKey = currentFormat.titleKey
            }
        })
        binding.viewPager.currentItem = DocumentTypeList.values()
            .filter { it.documentType == type || it.documentType == null }.map { it.titleKey }
            .indexOf(currentFormatKey)
    }

    private fun updateTitle(currentFormat: DocumentTypeList, side: DocumentSide) {
        val titleText = when (currentFormat) {
            DocumentTypeList.PERSO -> LocalizedStrings.getString(
                when (side) {
                    DocumentSide.FRONT -> "template_document_perso_ext_front"
                    DocumentSide.BACK -> "template_document_perso_ext_back"
                    DocumentSide.OTHER -> ""
                }
            )
            DocumentTypeList.PASS -> LocalizedStrings.getString(
                when (side) {
                    DocumentSide.FRONT -> "template_document_pass_ext_front"
                    DocumentSide.BACK -> "template_document_pass_ext_back"
                    DocumentSide.OTHER -> ""
                }
            )
            DocumentTypeList.DL_EU,
            DocumentTypeList.DL_OLD -> LocalizedStrings.getString(
                when (side) {
                    DocumentSide.FRONT -> "template_document_dl_ext_front"
                    DocumentSide.BACK -> "template_document_dl_ext_back"
                    DocumentSide.OTHER -> ""
                }
            )
            DocumentTypeList.OTHER -> ""
        }
        binding.piTitle.setText(titleText)
    }

    fun updateViewVideoDescription() {
        toggleViewVisibilities(isVideo = true)
    }

    fun updateViewVideoRecording() {
        toggleViewVisibilities(isVideo = true, isRecording = true)
    }

    fun updateRemainingTimeRecording(remainingTime: String) {
        binding.remainingTime.text = remainingTime
    }

    private fun toggleViewVisibilities(isVideo: Boolean = false, isRecording: Boolean = false) {
        this.isVideo = isVideo
        binding.piTitle.isVisible = isVideo.not()
        binding.templateContainer.isVisible = isVideo.not()
        binding.tabLayout.isVisible = isVideo.not()

        binding.helpText.isVisible = isVideo && isRecording.not()

        binding.remainingTime.isVisible = isVideo && isRecording
        binding.maskFvlc.isVisible = isVideo && isRecording

        binding.triggerButton.isEnabled = isRecording.not()
    }

    fun updateButton() {
        val selected = binding.viewPager.currentItem
        val isLast = selected == templateCellList.size - 1
        binding.templateNavigateNext.isVisible = isLast.not()
        binding.templateNavigatePrevious.isVisible = selected != 0
    }
}

class TemplateCell(private val template: DocumentTemplate?) : ViewBindingCell<PiTemplateViewBinding>() {
    override fun bindView(viewHolder: PiTemplateViewBinding) {
        viewHolder.imageSolid.setImageResource(template?.solid ?: 0)
        viewHolder.imageWire.setImageResource(template?.wire ?: 0)
        val fade = ValueAnimator.ofFloat(1.0f, 0f)
                .apply {
                    addUpdateListener {
                        viewHolder.imageSolid.alpha = this.animatedValue as Float
                        duration = 2000L
                    }
                }
        fade.start()
    }

    override fun inflate(inflater: LayoutInflater, parent: ViewGroup, attachToParent: Boolean): PiTemplateViewBinding {
        return PiTemplateViewBinding.inflate(inflater, parent, attachToParent)
    }
}

class CapturedPreviewView(val binding: PiCameraReviewBinding, buttonClicked: (BtnAction) -> Unit) {
    init {
        binding.piBtnRetry.setOnClickListener { buttonClicked(BtnAction.RETRY) }
        binding.piBtnUse.setOnClickListener { buttonClicked(BtnAction.USE) }
        binding.piBtnUse.text = LocalizedStrings.getString("confirm_image")
        binding.piBtnRetry.text = LocalizedStrings.getString("retry_label")
        binding.descriptionReview.text = LocalizedStrings.getString("is_blurry_title")
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
        binding.piCapturedImagePreview.isVisible = showProgress.not()
        binding.piBtnRetry.isVisible = showProgress.not()
        binding.piBtnUse.isVisible = showProgress.not()
    }
}
