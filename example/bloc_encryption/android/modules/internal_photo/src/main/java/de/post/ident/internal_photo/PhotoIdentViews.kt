package de.post.ident.internal_photo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.databinding.PiUploadUiBinding
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_photo.databinding.PiGroupItemBinding
import de.post.ident.internal_photo.databinding.PiStepItemBinding
import de.post.ident.internal_photo.databinding.PiUploadStatusBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

interface OverviewItem {
    val binding: ViewBinding
}

class StepItem(inflater: LayoutInflater, parent: ViewGroup, data: IdentificationStepDTO, groupId: IdentificationGroupDTO.GroupIdDTO,
               val onStepClicked: (field: IdentificationStepDTO, groupId: IdentificationGroupDTO.GroupIdDTO) -> Unit = { _, _ -> }) : OverviewItem {
    override val binding = PiStepItemBinding.inflate(inflater, parent, true)

    init {
        binding.root.setOnClickListener {
            if (data.status != StatusDTO.CLOSED) {
                onStepClicked(data, groupId)
            }
        }
        binding.stepTitle.text = data.header
        binding.documentIcon.setImageResource(when (groupId) {
            IdentificationGroupDTO.GroupIdDTO.UNKNOWN -> R.drawable.pi_ic_card_back
            IdentificationGroupDTO.GroupIdDTO.IDENTIFICATION_CARD -> if (data.stepType == IdentificationStepDTO.StepTypeDTO.IMAGEFRONT) R.drawable.pi_ic_card_front else R.drawable.pi_ic_card_back
            IdentificationGroupDTO.GroupIdDTO.DRIVERS_LICENSE -> if (data.stepType == IdentificationStepDTO.StepTypeDTO.IMAGEFRONT) R.drawable.pi_ic_driving_license_front else R.drawable.pi_ic_card_back
            IdentificationGroupDTO.GroupIdDTO.VIDEO_AUTH -> R.drawable.pi_ic_video
        })
        binding.separator.piSeparator.isVisible = data.stepNumber > 1

        if (data.status == StatusDTO.REJECTED) {
            data.statusMessage?.let {
                binding.errorText.isVisible = true
                binding.errorText.text = it
            }
        } else {
            binding.errorText.isVisible = false
        }

        binding.stateIcon.setImageResource(
                if (data.stepType == IdentificationStepDTO.StepTypeDTO.VIDEO) {
                    when (data.status) {
                        StatusDTO.OPEN -> R.drawable.pi_video_add
                        StatusDTO.CREATED -> R.drawable.pi_video_check
                        StatusDTO.REJECTED -> R.drawable.pi_video_error
                        StatusDTO.CLOSED -> R.drawable.pi_lock
                    }
                } else {
                    when (data.status) {
                        StatusDTO.OPEN -> R.drawable.pi_picture_add
                        StatusDTO.CREATED -> R.drawable.pi_picture_check
                        StatusDTO.REJECTED -> R.drawable.pi_picture_error
                        StatusDTO.CLOSED -> R.drawable.pi_lock
                    }
                }
        )

        setId(data, groupId) //for testautomation
    }

    private fun setId(stepData: IdentificationStepDTO, groupId: IdentificationGroupDTO.GroupIdDTO) {
        binding.root.id = when (groupId) {
            IdentificationGroupDTO.GroupIdDTO.IDENTIFICATION_CARD -> when (stepData.stepType) {
                IdentificationStepDTO.StepTypeDTO.IMAGEFRONT -> R.id.step_id_image_front
                IdentificationStepDTO.StepTypeDTO.IMAGEBACK -> R.id.step_id_image_back
                else -> 0
            }
            IdentificationGroupDTO.GroupIdDTO.DRIVERS_LICENSE -> when (stepData.stepType) {
                IdentificationStepDTO.StepTypeDTO.IMAGEFRONT -> R.id.step_dl_image_front
                IdentificationStepDTO.StepTypeDTO.IMAGEBACK -> R.id.step_dl_image_back
                else -> 0
            }
            IdentificationGroupDTO.GroupIdDTO.VIDEO_AUTH -> R.id.step_video
            IdentificationGroupDTO.GroupIdDTO.UNKNOWN -> R.id.step_unknown
        }
    }
}

class GroupItem(inflater: LayoutInflater, parent: ViewGroup, data: IdentificationGroupDTO,
                private val onStepClicked: (field: IdentificationStepDTO, groupId: IdentificationGroupDTO.GroupIdDTO) -> Unit = { _, _ -> }) :
    OverviewItem {
    override val binding = PiGroupItemBinding.inflate(inflater, parent, true)

    init {
        binding.groupTitle.text = data.groupName
        data.steps.sortedBy { it.stepNumber }
                .forEach { step ->
                    StepItem(inflater, binding.stepItemContainer, step, data.groupId, onStepClicked)
                }
    }
}

class UploadUi(private val binding: PiUploadUiBinding, buttonCancelClicked: () -> Unit) {

    init {
        binding.progressBar.isVisible = true
        binding.uploadFail.setOnClickListener {
            buttonCancelClicked()
        }
        binding.textviewUpload.text = LocalizedStrings.getString(
            "photo_upload_progress",
            "0.0",
            "0.0",
            0
        )
    }

    fun showProgress(size: Double? = 0.0, uploadedBytes: Long) {
        binding.textviewUpload.text = LocalizedStrings.getString(
            "photo_upload_progress",
            convertBytesUploadedToMegabyteUploaded(uploadedBytes, size!!),
            DecimalFormat("0.0").format(size),
            calculatePercentageUpload(size, uploadedBytes)
        )
        binding.uploadProgressBar.progress = calculatePercentageUpload(size, uploadedBytes)
    }

    private fun convertBytesUploadedToMegabyteUploaded(size: Long,  max: Double): String {
        val mbUploaded = (size / 1024.0 / 1024.0)
        return if (mbUploaded <= max) DecimalFormat("0.0").format(mbUploaded) else DecimalFormat("0.0").format(max)
    }


    private fun calculatePercentageUpload(size: Double = 0.0, uploadedBytes: Long): Int {
        val percentage = (uploadedBytes.toDouble() / size * 100.0).toInt()
        return if (percentage <= 100) percentage else 100
    }
}

class UploadStatusView(
    private val binding: PiUploadStatusBinding,
    caseResponse: CaseResponseDTO,
    private val buttonClicked: (BtnAction) -> Unit
) {

    init {

        binding.textUploadInfo.text = caseResponse.caseStatus?.displayText

        when (caseResponse.caseStatus?.statusCode) {
            StatusCodeDTO.IN_BEARBEITUNG -> {
                setStatusAssets("photo_upload_status_in_progress", R.drawable.pi_ic_status_in_progress)
                setupButton(LocalizedStrings.getString("default_btn_quit"), BtnAction.CLOSE)
            }
            StatusCodeDTO.ABGEWIESEN -> {
                setStatusAssets("photo_update_status_failure", R.drawable.pi_ic_status_rejected)
                setupButton(caseResponse.caseStatus?.buttonText, BtnAction.BACK)
            }
            StatusCodeDTO.ABGESCHLOSSEN -> {
                setStatusAssets("photo_upload_status_successful", R.drawable.pi_ic_status_success)
                setupButton(LocalizedStrings.getString("default_btn_quit"), BtnAction.CLOSE)
            }
            StatusCodeDTO.ABGELEHNT -> {
                setStatusAssets("photo_update_status_failure", R.drawable.pi_ic_status_rejected)
                setupButton(caseResponse.caseStatus?.buttonText, BtnAction.CLOSE)
            }
            else -> {}
        }

        binding.textUploadInfo.text = caseResponse.caseStatus?.displayText
        binding.textDocumentStatus.text = caseResponse.caseStatus?.uploadStatusText
        binding.textUploadingDate.text = caseResponse.uploadDate?.let {
            LocalizedStrings.getString(
                "photo_identification_status_date",
                getFormattedDate(it)
            )
        }
        binding.statusTitle.text = LocalizedStrings.getString("photo_identification_status_title")

        binding.refreshButton.setOnClickListener {
            buttonClicked(BtnAction.REFRESH)
        }

        if (caseResponse.toMethodSelection != null && Commons.skipMethodSelection.not()) {
            binding.toMethodSelection.isVisible = true
            binding.toMethodSelection.text = caseResponse.toMethodSelection!!.text
            binding.toMethodSelection.setOnClickListener {
                buttonClicked(BtnAction.TO_METHOD_SELECTION)
            }
        }

    }

    private fun setupButton(buttonText: String?, btnAction: BtnAction) {
        binding.continueBtn.text = buttonText
        binding.continueBtn.setOnClickListener {
            buttonClicked(btnAction)
        }
    }

    private fun getFormattedDate(sourceDate: String): String {
        val sourceFormat = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
        val targetFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        val date = sourceFormat.parse(sourceDate)
        return if (date != null) targetFormat.format(date) else sourceDate
    }

    @Keep
    enum class BtnAction {
        BACK, CLOSE, TO_METHOD_SELECTION, REFRESH
    }

    private fun setStatusAssets(stringKey: String, drawable: Int) {
        binding.textUploadStatus.text = LocalizedStrings.getString(stringKey)
        binding.imageviewDocumentStatus.setImageDrawable(ContextCompat.getDrawable(binding.root.context, drawable))
    }
}

data class UploadStatus(val max: Long, val value: Long)