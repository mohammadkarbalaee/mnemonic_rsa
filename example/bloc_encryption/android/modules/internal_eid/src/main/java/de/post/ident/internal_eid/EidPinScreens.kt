package de.post.ident.internal_eid

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.showAlertDialog
import de.post.ident.internal_core.util.showChoiceDialog
import de.post.ident.internal_eid.databinding.PiEidDialogWeblinkBinding
import de.post.ident.internal_eid.databinding.PiEidWrongPinDialogBinding
import de.post.ident.internal_eid.databinding.PiFragmentEidEnterPinBinding
import de.post.ident.internal_eid.databinding.PiFragmentNewPinDialogBinding
import de.post.ident.internal_eid.databinding.PiPinHelpBottomSheetBinding
import de.post.ident.internal_eid.util.NoImeTextInputEditText


abstract class EidPinScreen : EidScreen {

    private lateinit var bottomSheetDialog: BottomSheetDialog

    fun setupBottomSheetDialog(context: Context, topics: List<PinHelpTopics>) {
        bottomSheetDialog = BottomSheetDialog(context)
        val binding = PiPinHelpBottomSheetBinding.inflate(LayoutInflater.from(context))
        bottomSheetDialog.setContentView(binding.root)

        val onClickListener = View.OnClickListener { view ->
            when (view.id) {
                binding.pinHelpCan.id -> {
                    showAlertDialog(binding.root.context,
                        LocalizedStrings.getString("eid_help_can_title"),
                        LocalizedStrings.getString("eid_help_can_msg"),
                        R.drawable.pi_can_help_perso)
                }
                binding.pinHelpPin.id -> {
                    showAlertDialog(binding.root.context,
                        LocalizedStrings.getString("eid_help_pin_title"),
                        LocalizedStrings.getString("eid_help_pin_msg"))
                }
                binding.pinHelpForgotPin.id -> {
                    val customView = PiEidDialogWeblinkBinding.inflate(LayoutInflater.from(context))
                    customView.dialogTitle.text = LocalizedStrings.getString("eid_pin_forgotten")
                    customView.dialogDescription.text = LocalizedStrings.getHtmlString("eid_help_pin_forgotten_description")
                    customView.dialogDescription.movementMethod = LinkMovementMethod.getInstance()

                    showChoiceDialog(
                        context = binding.root.context,
                        view = customView,
                        cancelable = true,
                        negativeButton = LocalizedStrings.getString(de.post.ident.internal_core.R.string.default_btn_ok),
                        positiveButton = LocalizedStrings.getString("default_btn_back_to_methodselection"),
                        onPositive = {
                            val activity = context as Activity
                            activity.finish()
                        }
                    )
                }
            }
            bottomSheetDialog.dismiss()
        }

        for (topic in topics) {
            when (topic) {
                PinHelpTopics.PIN_LOCATION -> {
                    binding.pinHelpPin.visibility = View.VISIBLE
                    binding.pinHelpPin.text = LocalizedStrings.getString("eid_help_pin_title")
                    binding.pinHelpPin.setOnClickListener(onClickListener)
                }
                PinHelpTopics.PIN_FORGOTTEN -> {
                    binding.pinHelpForgotPin.visibility = View.VISIBLE
                    binding.pinHelpForgotPin.text = LocalizedStrings.getString("eid_pin_request_forgotten")
                    binding.pinHelpForgotPin.setOnClickListener(onClickListener)
                }
                PinHelpTopics.CAN_LOCATION -> {
                    binding.pinHelpCan.visibility = View.VISIBLE
                    binding.pinHelpCan.text = LocalizedStrings.getString("eid_help_can_title")
                    binding.pinHelpCan.setOnClickListener(onClickListener)
                }
            }
        }
    }

    fun showBottomSheet() {
        bottomSheetDialog.show()
    }

    enum class PinHelpTopics {
        PIN_LOCATION,
        PIN_FORGOTTEN,
        CAN_LOCATION
    }
}

class EnterPinScreen(val attemptsLeft: Int = 3,
                     val wasPinChanged: Boolean = false,
                     val lastPin: String? = null,
                     val onPinEntered: (String) -> Unit,
                     val onChangePinClicked: () -> Unit) : EidPinScreen() {
    override fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View {
        val binding = PiFragmentEidEnterPinBinding.inflate(inflater, parentView, false)

        val showAttemptHint = (attemptsLeft == 2)

        binding.pinTitle.text = LocalizedStrings.getString("eid_pin_ident_title")
        binding.pinDescription.text = LocalizedStrings.getString("eid_pin_ident_help")
        binding.pinHelp.text = LocalizedStrings.getString("eid_scan_help_btn")
        binding.pinDigits.text = LocalizedStrings.getString("eid_enter_pin_5_digits")
        binding.pinInput.hint = LocalizedStrings.getString("eid_pin_ident_placeholder")

        binding.pinDigits.isVisible = wasPinChanged.not()

        binding.btnContinue.setOnClickListener {
            onPinEntered(binding.pinInput.text.toString())
        }
        binding.pinHelp.setOnClickListener {
            showBottomSheet()
        }
        binding.pinDigits.setOnClickListener {
            binding.pinDigits.isEnabled = false
            binding.btnContinue.isEnabled = false
            onChangePinClicked()
        }
        binding.attemptsLeft.text = LocalizedStrings.getString("eid_enter_pin_remaining_tries", attemptsLeft)
        binding.attemptsLeft.isVisible = showAttemptHint

        binding.pinInput.addTextChangedListener { binding.btnContinue.isEnabled = it?.length == 6 }

        if (showAttemptHint) {
            val view = PiEidWrongPinDialogBinding.inflate(inflater)
            view.eidPinFirstTitle.text = LocalizedStrings.getString("eid_pin_wrong_pin_title")
            view.pinDescription.text = LocalizedStrings.getHtmlString("eid_wrong_pin_entered_description")
            lastPin.let { view.pinInputDisplay.setText(it) }
            showChoiceDialog(
                context = parentFragment.requireContext(),
                view = view,
                cancelable = true,
                positiveButton = LocalizedStrings.getString(de.post.ident.internal_core.R.string.default_btn_ok)
            )
        }

        setupKeypad(binding, binding.pinInput)
        binding.pinInput.requestFocus()

        val eventContext = mapOf(
                "needsCan" to "false",
                "attemptsLeft" to attemptsLeft.toString()
        )
        EmmiCoreReporter.send(logEvent = LogEvent.EI_PIN_ENTRY, eventContext = eventContext, attemptId = Commons.attemptId)

        setupBottomSheetDialog(
            binding.root.context,
            listOf(
                PinHelpTopics.PIN_LOCATION,
                PinHelpTopics.PIN_FORGOTTEN
            )
        )

        return binding.root
    }
}

class EnterCanScreen(val onPinCanEntered: (pin: String, can: String) -> Unit) : EidPinScreen() {

    private var filledPinField = false
    private var filledCanField = false

    override fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View {
        val binding = PiFragmentEidEnterPinBinding.inflate(inflater, parentView, false)

        binding.pinTitle.text = LocalizedStrings.getString("eid_pin_ident_title")
        binding.pinDescription.text = LocalizedStrings.getString("eid_pin_can_pin_help")
        binding.pinHelp.text = LocalizedStrings.getString("eid_scan_help_btn")
        binding.pinInput.hint = LocalizedStrings.getString("eid_pin_ident_placeholder")
        binding.canInput.hint = LocalizedStrings.getString("eid_pin_can_single_placeholder")
        binding.canInputLayout.isVisible = true
        binding.pinDigits.isVisible = false

        binding.btnContinue.setOnClickListener {
            val pin = binding.pinInput.text.toString()
            val can = binding.canInput.text.toString()
            onPinCanEntered(pin, can)
        }
        binding.pinHelp.setOnClickListener {
            showBottomSheet()
        }

        binding.attemptsLeft.text = LocalizedStrings.getString("eid_enter_pin_remaining_tries", 1)
        binding.attemptsLeft.isVisible = true

        binding.pinInput.addTextChangedListener {
            filledPinField = it?.length == 6
            binding.btnContinue.isEnabled = filledPinField && filledCanField
        }
        binding.canInput.addTextChangedListener {
            filledCanField = it?.length == 6
            binding.btnContinue.isEnabled = filledPinField && filledCanField
        }

        binding.pinInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                setupKeypad(binding, v as NoImeTextInputEditText)
            }
        }
        binding.canInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                setupKeypad(binding, v as NoImeTextInputEditText)
            }
        }

        showAlertDialog(parentFragment.requireContext(), LocalizedStrings.getQuantityString("eid_pin_status_failed_toast", 1, 1))

        binding.pinInput.requestFocus()

        val eventContext = mapOf(
                "needsCan" to "true",
                "attemptsLeft" to "1"
        )
        EmmiCoreReporter.send(logEvent = LogEvent.EI_PIN_ENTRY, eventContext = eventContext, attemptId = Commons.attemptId)

        setupBottomSheetDialog(
            binding.root.context,
            listOf(
                PinHelpTopics.PIN_LOCATION,
                PinHelpTopics.PIN_FORGOTTEN,
                PinHelpTopics.CAN_LOCATION
            )
        )

        return binding.root
    }
}

class EnterTransportPinScreen(
        var attemptsLeft: Int = 3, val canRequired: Boolean,
        val onPinCanEntered: (pin: String, can: String?) -> Unit, val onGoToEnterPinClicked: () -> Unit) : EidPinScreen() {

    private var filledPinField = false
    private var filledCanField = false
    private lateinit var binding: PiFragmentEidEnterPinBinding
    private val titleOnClickListener = object: View.OnClickListener {
        private var clickCounter = 0
        override fun onClick(v: View?) {
            if (++clickCounter == 5) {
                setPinInputLength(6)
                binding.pinInput.text = null
                binding.pinInput.hint = LocalizedStrings.getString("eid_pin_can_pin_placeholder")
            }
        }
        fun reset() { clickCounter = 0 }
    }

    override fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View {
        binding = PiFragmentEidEnterPinBinding.inflate(inflater, parentView, false)

        binding.pinTitle.text = LocalizedStrings.getString("eid_pin_transport_title")
        binding.pinDescription.text = LocalizedStrings.getString("eid_pin_transport_help")
        binding.pinHelp.text = LocalizedStrings.getString("eid_scan_help_btn")
        binding.pinDigits.text = LocalizedStrings.getString("eid_enter_pin_6_digits")
        binding.pinInput.hint = LocalizedStrings.getString("eid_pin_transport_placeholder")

        binding.pinTitle.setOnClickListener(titleOnClickListener)
        binding.pinDigits.isVisible = canRequired.not()

        binding.btnContinue.setOnClickListener {
            val pin = binding.pinInput.text.toString()
            val can = if (canRequired) binding.canInput.text.toString() else null
            onPinCanEntered(pin, can)
        }
        binding.pinHelp.setOnClickListener {
            showBottomSheet()
        }
        binding.pinDigits.setOnClickListener {
            binding.pinDigits.isEnabled = false
            binding.btnContinue.isEnabled = false
            onGoToEnterPinClicked()
        }

        if (canRequired) { attemptsLeft = 1 }
        binding.attemptsLeft.text = LocalizedStrings.getString("eid_enter_pin_remaining_tries", attemptsLeft)
        val showAttemptHint = (attemptsLeft == 1 || attemptsLeft == 2)
        binding.attemptsLeft.isVisible = showAttemptHint
        if (showAttemptHint) {
            showAlertDialog(parentFragment.requireContext(), LocalizedStrings.getQuantityString("eid_transport_pin_status_failed_toast", attemptsLeft, attemptsLeft))
        }

        setPinInputLength(5)
        binding.pinInput.addTextChangedListener {
            it?.let {
                if (it.toString() == "77777") {
                    binding.pinTitle.isClickable = true
                } else {
                    binding.pinTitle.isClickable = false
                    titleOnClickListener.reset()
                }
                filledPinField = it.length >= 5
                binding.btnContinue.isEnabled = if (canRequired) filledPinField && filledCanField else filledPinField
            }
        }

        binding.pinInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                setupKeypad(binding, v as NoImeTextInputEditText)
            }
        }

        if (canRequired) {
            binding.canInput.hint = LocalizedStrings.getString("eid_pin_can_single_placeholder")
            binding.canInputLayout.isVisible = true

            binding.canInput.addTextChangedListener {
                filledCanField = it?.length == 6
                binding.btnContinue.isEnabled = filledPinField && filledCanField
            }

            binding.canInput.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    setupKeypad(binding, v as NoImeTextInputEditText)
                }
            }
        }

        binding.pinInput.requestFocus()

        val eventContext = mapOf(
                "attemptsLeft" to attemptsLeft.toString(),
                "needsCan" to canRequired.toString()
        )
        EmmiCoreReporter.send(logEvent = LogEvent.EI_DISPLAY_CHANGE_PIN, eventContext = eventContext, attemptId = Commons.attemptId)

        if (canRequired) {
            setupBottomSheetDialog(
                binding.root.context,
                listOf(
                    PinHelpTopics.PIN_LOCATION,
                    PinHelpTopics.PIN_FORGOTTEN,
                    PinHelpTopics.CAN_LOCATION
                )
            )
        } else {
            setupBottomSheetDialog(
                binding.root.context,
                listOf(
                    PinHelpTopics.PIN_LOCATION,
                    PinHelpTopics.PIN_FORGOTTEN
                )
            )
        }

        return binding.root
    }

    private fun setPinInputLength(length: Int) {
        binding.pinInput.filters = arrayOf(InputFilter.LengthFilter(length))
    }
}

class EnterNewPinScreen(val transportPin: String, val can: String?,
                        val onNewPinEntered: (transportPin: String, newPin: String, can: String?) -> Unit) : EidScreen {

    override fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View {
        val binding = PiFragmentEidEnterPinBinding.inflate(inflater, parentView, false)

        binding.pinTitle.text = LocalizedStrings.getString("eid_pin_first_title")
        binding.pinDescription.text = LocalizedStrings.getString("eid_pin_first_help")
        binding.pinInput.hint = LocalizedStrings.getString("eid_pin_first_placeholder")
        binding.pinDigits.isVisible = false
        binding.pinHelp.isVisible = false

        binding.btnContinue.setOnClickListener {
            val pin = binding.pinInput.text.toString()
            onNewPinEntered(transportPin, pin, can)
        }

        binding.pinInput.addTextChangedListener {
            binding.btnContinue.isEnabled = it?.length == 6
        }

        setupKeypad(binding, binding.pinInput)
        binding.pinInput.requestFocus()

        NewPinDialog().show(parentFragment.childFragmentManager, "NEW_PIN")

        return binding.root
    }
}

class RepeatNewPinScreen(private val transportPin: String, private val newPin: String, private val can: String?,
                         val onNewPinEntered: (transportPin: String, newPin: String, can: String?) -> Unit) : EidScreen {

    override fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View {
        val binding = PiFragmentEidEnterPinBinding.inflate(inflater, parentView, false)

        binding.pinTitle.text = LocalizedStrings.getString("eid_pin_second_title")
        binding.pinDescription.text = LocalizedStrings.getString("eid_pin_second_help")
        binding.pinInput.hint = LocalizedStrings.getString("eid_pin_second_placeholder")
        binding.pinDigits.isVisible = false
        binding.pinHelp.isVisible = false

        binding.btnContinue.setOnClickListener {
            val pin = binding.pinInput.text.toString()
            if (pin != newPin) {
                binding.attemptsLeft.isVisible = true
                binding.attemptsLeft.text = LocalizedStrings.getString("eid_change_pin_mismatch")
            } else {
                onNewPinEntered(transportPin, pin, can)
            }
        }

        binding.pinInput.addTextChangedListener {
            binding.btnContinue.isEnabled = it?.length == 6
            binding.attemptsLeft.isVisible = false
        }

        setupKeypad(binding, binding.pinInput)
        binding.pinInput.requestFocus()

        return binding.root
    }
}

private fun setupKeypad(binding: PiFragmentEidEnterPinBinding, activeInput: NoImeTextInputEditText) {
    binding.keypadNumber0.setOnClickListener { activeInput.append("0") }
    binding.keypadNumber1.setOnClickListener { activeInput.append("1") }
    binding.keypadNumber2.setOnClickListener { activeInput.append("2") }
    binding.keypadNumber3.setOnClickListener { activeInput.append("3") }
    binding.keypadNumber4.setOnClickListener { activeInput.append("4") }
    binding.keypadNumber5.setOnClickListener { activeInput.append("5") }
    binding.keypadNumber6.setOnClickListener { activeInput.append("6") }
    binding.keypadNumber7.setOnClickListener { activeInput.append("7") }
    binding.keypadNumber8.setOnClickListener { activeInput.append("8") }
    binding.keypadNumber9.setOnClickListener { activeInput.append("9") }
    binding.keypadClear.setOnClickListener { activeInput.text = null }
    binding.keypadBackspace.setOnClickListener {
        activeInput.text?.let { if (it.isNotEmpty()) it.delete(it.length - 1, it.length) }
    }
}

class NewPinDialog : DialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = PiFragmentNewPinDialogBinding.inflate(inflater, container, false)

        binding.eidPinFirstTitle.text = LocalizedStrings.getString("eid_pin_first_title")
        binding.eidPinInfoBox1.text = LocalizedStrings.getString("eid_pin_info_box_1")
        binding.eidPinInfoBox2Title.text = LocalizedStrings.getString("eid_pin_info_box_2_title")
        binding.eidPinInfoBox2Bullet1.text = LocalizedStrings.getHtmlString("eid_pin_info_box_2_bullet_1")
        binding.eidPinInfoBox2Bullet2.text = LocalizedStrings.getHtmlString("eid_pin_info_box_2_bullet_2")
        binding.eidPinInfoBox2Bullet3.text = LocalizedStrings.getHtmlString("eid_pin_info_box_2_bullet_3")

        binding.btnCloseDialog.text = LocalizedStrings.getString(R.string.default_btn_ok)
        binding.btnCloseDialog.setOnClickListener { dismiss() }

        return binding.root
    }
}