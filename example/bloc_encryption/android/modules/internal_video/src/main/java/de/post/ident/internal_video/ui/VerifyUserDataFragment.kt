package de.post.ident.internal_video.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextSwitcher
import android.widget.TextView
import androidx.annotation.Keep
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.ui.subscribeForEvent
import de.post.ident.internal_video.*
import de.post.ident.internal_video.databinding.PiFragmentVerifyUserDataBinding
import de.post.ident.internal_video.databinding.PiFragmentVerifyUserDataItemBinding
import de.post.ident.internal_video.rest.ChatChangeMessageDTO
import de.post.ident.internal_video.util.EmmiVideoReporter
import java.text.SimpleDateFormat
import java.util.*


class VerifyUserDataFragment : Fragment() {
    companion object {
        fun newInstance(): VerifyUserDataFragment = VerifyUserDataFragment()
    }

    @Keep
    enum class CardItem(val textKey: String, val field: (ChatChangeMessageDTO.WorkflowUserDataDTO) -> String?) {
        IDC_NUMBER("id_card_nr", { it.idcNumber }),
        IDC_LAST_NAME("id_card_name", { it.idcLastName }),
        IDC_BIRTH_NAME("id_card_birth_name", { it.idcBirthName }),
        IDC_FIRST_NAME("id_card_forename", { it.idcFirstName }),
        IDC_BIRTH_DATE("id_card_date_of_birth", { resolveTimestamp(it.idcBirthDate) }),
        IDC_BIRTH_PLACE("id_card_place_of_birth", { it.idcBirthPlace }),
        IDC_NATIONALITY("id_card_nationality", { it.idcNationality }),
        ADDRESS_STREET("id_card_street", { dualField(it.addressStreet, it.addressStreetNumber) }),
        ADDRESS_APPENDIX("id_card_address_extension", { it.addressAppendix }),
        ADDRESS_CITY("id_card_zip_and_city", { dualField(it.addressPostalCode, it.addressCity) }),
        ADDRESS_COUNTRY("id_card_country", { it.addressCountry }),
        IDC_DATE_ISSUED("id_card_issued_on", { resolveTimestamp(it.idcDateIssued) }),
        IDC_DATE_OF_EXPIRY("id_card_valid_until", { resolveTimestamp(it.idcDateOfExpiry) }),
        IDC_AUTHORITY("id_card_government_agency", { it.idcAuthority }),
        IDC_PLACE_OF_ISSUE("id_card_city_of_issue", { it.idcPlaceOfIssue }),
    }

    private val mapBindingToCardItem = mutableMapOf<CardItem, PiFragmentVerifyUserDataItemBinding>()

    private val videoManager = VideoManager.instance()
    private val emmiReporter = EmmiVideoReporter(videoManager)

    private lateinit var viewBinding: PiFragmentVerifyUserDataBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoManager.currentState.observe(this) {
            onProgressUpdate(it)
        }
        videoManager.novomindEventBus.subscribeForEvent(this) { event: NovomindEvent.WorkflowUserData ->
            onNewData(event.userData)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentVerifyUserDataBinding.inflate(inflater, container, false)

        viewBinding.idCardDataTitle.text = LocalizedStrings.getString("id_card_data_title")

        CardItem.values().forEach {
            val itemBinding = PiFragmentVerifyUserDataItemBinding.inflate(inflater, viewBinding.idCardDataItemContainer, true)
            itemBinding.key.text = LocalizedStrings.getString(it.textKey)
            setViewFactory(itemBinding.value)
            mapBindingToCardItem[it] = itemBinding
            if (it == CardItem.IDC_NUMBER) {
                itemBinding.root.isVisible = true
            }
        }

        return viewBinding.root
    }

    private fun setViewFactory(textSwitcher: TextSwitcher) {
        textSwitcher.setFactory {
            TextView(requireContext()).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setTextAppearance(R.style.PITextAppearance_Bold)
                }
            }
        }
    }

    private fun onNewData(data: ChatChangeMessageDTO.WorkflowUserDataDTO) {
        val isStateFillIdCardData = videoManager.currentState.value == VideoState.FILL_IDCARD_DATA
        mapBindingToCardItem.forEach { (cardItem, binding) ->
            if (isStateFillIdCardData || cardItem == CardItem.IDC_NUMBER) {
                setTextAndAnimate(binding, cardItem.field(data))
            }
        }
    }

    private fun onProgressUpdate(serverState: VideoState) {
        when (serverState) {
            VideoState.GRAB_IDCARD_NUMBER -> {
                emmiReporter.send(LogEvent.DR_STEP_DOCUMENTNUMBER)
            }
            VideoState.FILL_IDCARD_DATA -> {
                emmiReporter.send(LogEvent.DR_STEP_DATA)
            }
            else -> {}
        }
    }

    private fun setTextAndAnimate(binding: PiFragmentVerifyUserDataItemBinding, text: String?) {
        if (text != null) {
            binding.root.isVisible = true
            val previousText = if (binding.value.currentView != null) (binding.value.currentView as TextView).text.toString() else null
            val newText = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() // decode HTML characters like &#39; -> '

            // only update / animate text if it changed
            if (previousText != newText) {
                binding.value.setText(newText)
            }
        }
    }
}

private fun dualField(a: String?, b: String?) = if (a == null && b == null) null else "${a ?: ""} ${b ?: ""}"

private fun resolveTimestamp(timestamp: String?): String? {
    if (timestamp.isNullOrEmpty()) {
        return null
    }
    val longTimestamp: Long = timestamp.toLong()
    return if (longTimestamp == 0L) {
        null
    } else SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(longTimestamp)
}
