package de.post.ident.internal_autoid.ui

import android.view.Gravity
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.annotation.Keep
import de.post.ident.internal_autoid.AutoIdentActivity
import de.post.ident.internal_autoid.R
import de.post.ident.internal_autoid.databinding.PiFragmentDocTypeSelectionBinding
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.UiUtil

@Keep
enum class DocumentType(val UserEntryDocTypeMajorAndConstructionCode: String) {
    ID_CARD("iddoc_card"),
    ID_CARD_ALTERNATIVE("iddoc_card"),
    PASSPORT("iddoc_booklet"),
    DRIVING_LICENSE("drivlic_card")
}

data class DocTypeOption(val type: DocumentType, val bgDrawableResId: Int, val title: String)

val docTypeOptions = listOf(
    DocTypeOption(DocumentType.ID_CARD, R.drawable.doctype_selector_idcard, LocalizedStrings.getString("autoId_docType_selection_idCard")),
    DocTypeOption(DocumentType.PASSPORT, R.drawable.doctype_selector_passport, LocalizedStrings.getString("autoId_docType_selection_pass"))
)

class DocTypeSelectionView(
    val binding: PiFragmentDocTypeSelectionBinding,
    val activity: AutoIdentActivity,
    buttonClicked: (BtnAction) -> Unit
) {
    private val optionButtons = mutableListOf<TouchableImageView>()

    init {
        binding.title.text = LocalizedStrings.getString("autoId_docType_selection_title")
        binding.btnContinueStandard.text = LocalizedStrings.getString("default_btn_continue")
        binding.btnContinueStandard.setOnClickListener {
            activity.documentType = getSelectedDocType()
            buttonClicked(BtnAction.CONTINUE)
            it.isEnabled = false
        }

        docTypeOptions.forEach { docTypeOption ->
            val imageView = TouchableImageView(activity).apply {
                setImageResource(docTypeOption.bgDrawableResId)
                contentDescription = docTypeOption.type.name
                tag = docTypeOption.type
                setOnClickListener {
                    optionButtons.forEach { it.isSelected = false }
                    it.isSelected = true
                    binding.btnContinueStandard.isEnabled = true
                }
            }
            optionButtons.add(imageView)
            binding.doctypeOptionsContainer.addView(imageView)
            binding.doctypeOptionsContainer.addView(
                TextView(activity).apply {
                    text = docTypeOption.title
                    gravity = Gravity.CENTER
                    setPadding(0, UiUtil.dpToPx(activity, 4f), 0, UiUtil.dpToPx(activity, 16f))
                }
            )
        }
    }

    private fun getSelectedDocType() = optionButtons.first { it.isSelected }.tag as DocumentType

    @Keep
    enum class BtnAction {
        CONTINUE
    }
}