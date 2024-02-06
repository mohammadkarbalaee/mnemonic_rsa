package de.post.ident.internal_autoid.ui

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.core.view.isVisible
import de.post.ident.internal_autoid.AutoIdentActivity
import de.post.ident.internal_autoid.AutoIdentActivity.Screen
import de.post.ident.internal_autoid.R
import de.post.ident.internal_autoid.databinding.PiFragmentStepDescriptionBinding
import de.post.ident.internal_core.util.LocalizedStrings

@Keep
data class DescriptionData(val screen: Screen, val docType: DocumentType, val title: String, val subTitle: String, @DrawableRes val image: Int? = null, val imageDescription: ArrayList<ImageDescription>? = null, val stepDataList: ArrayList<StepData>)

@Keep
data class StepData(@DrawableRes val descriptionIcon: Int, val descriptionKey: String)
@Keep
data class ImageDescription(@DrawableRes val image: Int, @DrawableRes val descriptionIcon: Int, val descriptionKey: String)

val descriptionDataList = listOf(
    DescriptionData(
        Screen.DOC_CHECK_FRONT,
        docType = DocumentType.ID_CARD,
        title = "step_description_doc_check_title",
        subTitle = "step_description_doc_check_subtitle",
        image = R.drawable.step1_id_front,
        stepDataList =
        arrayListOf(
            StepData(R.drawable.pi_ic_lightbulp_icon, "step_description_doc_check_step_one"),
            StepData(R.drawable.pi_ic_surface_icon, "step_description_doc_check_step_two"),
            StepData(R.drawable.pi_ic_overlay_icon, "step_description_doc_check_step_three")
        )
    ),
    DescriptionData(
        Screen.DOC_CHECK_FRONT,
        docType = DocumentType.PASSPORT,
        title = "step_description_doc_check_pass_cover",
        subTitle = "step_description_doc_check_pass_cover_subtitle",
        image = R.drawable.step1_pass_front,
        stepDataList =
        arrayListOf(
            StepData(R.drawable.pi_ic_lightbulp_icon, "step_description_doc_check_step_one"),
            StepData(R.drawable.pi_ic_surface_icon, "step_description_doc_check_step_two"),
            StepData(R.drawable.pi_ic_overlay_icon, "step_description_doc_check_step_three")
        )
    ),
    DescriptionData(
        Screen.DOC_CHECK_BACK,
        docType = DocumentType.ID_CARD,
        title = "step_description_doc_check_title_back",
        subTitle = "step_description_doc_check_subtitle_back",
        image = R.drawable.step1_id_back,
        stepDataList =
        arrayListOf(
            StepData(R.drawable.pi_ic_lightbulp_icon, "step_description_doc_check_step_one"),
            StepData(R.drawable.pi_ic_surface_icon, "step_description_doc_check_step_two"),
            StepData(R.drawable.pi_ic_overlay_icon, "step_description_doc_check_step_three")
        )
    ),
    DescriptionData(
        Screen.DOC_CHECK_BACK,
        docType = DocumentType.PASSPORT,
        title = "step_description_doc_check_pass_data",
        subTitle = "step_description_doc_check_pass_data_subtitle",
        image = R.drawable.step1_pass_back,
        stepDataList =
        arrayListOf(
            StepData(R.drawable.pi_ic_lightbulp_icon, "step_description_doc_check_step_one"),
            StepData(R.drawable.pi_ic_surface_icon, "step_description_doc_check_step_two"),
            StepData(R.drawable.pi_ic_overlay_icon, "step_description_doc_check_step_three")
        )
    ),
    DescriptionData(
        Screen.DVF,
        docType = DocumentType.ID_CARD,
        title = "step_description_dvf_title",
        subTitle = "step_description_dvf_subtitle",
        imageDescription = arrayListOf(
            ImageDescription(R.drawable.step2_holo_invisible, R.drawable.pi_cross, "step_description_dvf_cross_text"),
            ImageDescription(R.drawable.step2_holo_visible, R.drawable.pi_check_mark, "step_description_dvf_check_mark_text")
        ),
        stepDataList =
        arrayListOf(
            StepData(R.drawable.pi_ic_lightbulp_icon, "step_description_dvf_step_one"),
            StepData(R.drawable.pi_ic_overlay_icon, "step_description_dvf_step_two"),
            StepData(R.drawable.pi_ic_finger_icon, "step_description_dvf_step_three")
        )
    ),
    DescriptionData(
        Screen.DVF,
        docType = DocumentType.PASSPORT,
        title = "step_description_dvf_title",
        subTitle = "step_description_dvf_subtitle_pass",
        imageDescription = arrayListOf(
            ImageDescription(R.drawable.step2_holo_invisible, R.drawable.pi_cross, "step_description_dvf_cross_text"),
            ImageDescription(R.drawable.step2_holo_visible, R.drawable.pi_check_mark, "step_description_dvf_check_mark_text")
        ),
        stepDataList =
        arrayListOf(
            StepData(R.drawable.pi_ic_lightbulp_icon, "step_description_dvf_step_one"),
            StepData(R.drawable.pi_ic_overlay_icon, "step_description_dvf_step_two"),
            StepData(R.drawable.pi_ic_finger_icon, "step_description_dvf_step_three")
        )
    ),
    DescriptionData(
        Screen.FVLC,
        docType = DocumentType.ID_CARD,
        title = "step_description_fvlc_title",
        subTitle = "step_description_fvlc_subtitle",
        image = R.drawable.step3,
        stepDataList =
        arrayListOf(
            StepData(R.drawable.pi_ic_lightbulp_icon, "step_description_fvlc_step_one"),
            StepData(R.drawable.pi_ic_face_overlay_icon, "step_description_fvlc_step_two"),
            StepData(R.drawable.pi_ic_face_icon, "step_description_fvlc_step_three")
        )
    ),
    DescriptionData(
        Screen.FVLC,
        docType = DocumentType.PASSPORT,
        title = "step_description_fvlc_title",
        subTitle = "step_description_fvlc_subtitle",
        image = R.drawable.step3,
        stepDataList =
        arrayListOf(
            StepData(R.drawable.pi_ic_lightbulp_icon, "step_description_fvlc_step_one"),
            StepData(R.drawable.pi_ic_face_overlay_icon, "step_description_fvlc_step_two"),
            StepData(R.drawable.pi_ic_face_icon, "step_description_fvlc_step_three")
        )
    )
)

class StepDescriptionView(
    binding: PiFragmentStepDescriptionBinding,
    activity: AutoIdentActivity,
    buttonClicked: (BtnAction) -> Unit
) {
    private val vb = binding
    private val act = activity

    init {
        vb.btnContinueStandard.text = LocalizedStrings.getString("default_btn_continue")
        vb.btnContinueStandard.setOnClickListener {
            buttonClicked(BtnAction.CONTINUE)
            it.isEnabled = false
        }
    }

    fun updateDescriptionView() {
        val currentIdentStep =  act.getCurrentIdentStep()
        val descriptionData = descriptionDataList.first { it.screen == currentIdentStep && it.docType == act.documentType }
        vb.title.text = LocalizedStrings.getString(descriptionData.title)
        vb.subtitle.text = LocalizedStrings.getString(descriptionData.subTitle)
        when (currentIdentStep) {
            Screen.DVF -> {
                vb.image.isVisible = false
                vb.imageContainer.isVisible =  true
                descriptionData.imageDescription?.let {
                    vb.imageOne.setImageResource(descriptionData.imageDescription[0].image)
                    vb.iconOne.setImageResource(descriptionData.imageDescription[0].descriptionIcon)
                    vb.iconOneDescription.text = LocalizedStrings.getString(descriptionData.imageDescription[0].descriptionKey)
                    vb.imageTwo.setImageResource(descriptionData.imageDescription[1].image)
                    vb.iconTwo.setImageResource(descriptionData.imageDescription[1].descriptionIcon)
                    vb.iconTwoDescription.text = LocalizedStrings.getString(descriptionData.imageDescription[1].descriptionKey)
                }
            }
            Screen.DOC_CHECK_FRONT, Screen.DOC_CHECK_BACK, Screen.FVLC -> {
                vb.image.isVisible = true
                vb.imageContainer.isVisible =  false
                descriptionData.image?.let { vb.image.setImageResource(it) }
            }
            else -> {}
        }
        vb.descriptionStepOne.text = LocalizedStrings.getString(descriptionData.stepDataList[0].descriptionKey)
        vb.iconStepOne.setImageResource(descriptionData.stepDataList[0].descriptionIcon)
        vb.descriptionStepTwo.text = LocalizedStrings.getString(descriptionData.stepDataList[1].descriptionKey)
        vb.iconStepTwo.setImageResource(descriptionData.stepDataList[1].descriptionIcon)
        vb.descriptionStepThree.text = LocalizedStrings.getString(descriptionData.stepDataList[2].descriptionKey)
        vb.iconStepThree.setImageResource(descriptionData.stepDataList[2].descriptionIcon)
        vb.btnContinueStandard.isEnabled = true
    }

    @Keep
    enum class BtnAction {
        CONTINUE
    }
}