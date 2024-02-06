package de.post.ident.internal_core.start

import android.app.DatePickerDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Patterns
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import de.post.ident.internal_core.R
import de.post.ident.internal_core.databinding.PiFormChoicesDropdownBinding
import de.post.ident.internal_core.databinding.PiFormChoicesRadioBinding
import de.post.ident.internal_core.databinding.PiFormChoicesRadioItemBinding
import de.post.ident.internal_core.databinding.PiFormTextBinding
import de.post.ident.internal_core.rest.ChoicesDTO
import de.post.ident.internal_core.rest.DataFieldDTO
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.ui.showKeyboard
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

interface FormField {
    val data: DataFieldDTO
    val binding: ViewBinding
    fun validate(): Boolean
    fun userInput(): String?
    fun error(errorText: String?)
}

class FormRadioField(inflater: LayoutInflater, parent: ViewGroup, override val data: DataFieldDTO) : FormField {

    override val binding = PiFormChoicesRadioBinding.inflate(inflater, parent, true)

    private data class ChoicesItem(val view: RadioButton, val data: ChoicesDTO)

    private val choicesItems = mutableListOf<ChoicesItem>()
    init {
        check(data.choices.isNullOrEmpty().not())
        binding.piFormChoicesDisplay.text = data.display
        binding.piFormChoicesDisplay.hint = data.hint
        binding.piFormChoicesDisplay.contentDescription = data.key
        val radioGroup = binding.piFormChoicesRadioGroup
        data.choices?.forEach { choicesItem ->
            val radioButton = PiFormChoicesRadioItemBinding.inflate(inflater, radioGroup, true).root
            radioButton.contentDescription = data.key
            radioButton.text = choicesItem.display
            choicesItems.add(ChoicesItem(radioButton, choicesItem))
            radioButton.isSelected = data.preselectedValue == choicesItem.key //Not able to test this. (So maybe it will never happen)
        }
        binding.piFormChoicesRadioGroup.setOnCheckedChangeListener { _, _ ->
            validate()
        }
        binding.piFormChoicesRadioGroup.contentDescription = data.key
        binding.piFormChoicesField.helperText = enforceSpaceWhenEmpty(data.hint) //Needed to make sure height of radio group will not change on error
        binding.piFormChoicesField.contentDescription = data.key
    }

    override fun validate(): Boolean {
        val input = userSelection()
        val errorText = when {
            data.mandatory && input == null -> LocalizedStrings.getString("contact_data_error_mandatory")
            else -> null
        }
        error(errorText)
        return errorText == null
    }

    override fun userInput(): String? = userSelection()?.key

    override fun error(errorText: String?) {
        binding.piFormChoicesField.error = errorText
    }

    private fun userSelection(): ChoicesDTO? = choicesItems.firstOrNull { it.view.isChecked }?.data
}

class FormTextField(inflater: LayoutInflater, parent: ViewGroup, override val data: DataFieldDTO,
                    val onFocusChanged: (field: FormTextField, hasFocus: Boolean) -> Unit = { _,_ -> },
                    val actionDone: () -> Unit) : FormField {

    override val binding = PiFormTextBinding.inflate(inflater, parent, true)

    init {
        binding.piFormTextField.hint = data.display
        binding.piFormTextField.editText?.apply {
            contentDescription = data.key
            inputType = when (data.type) {
                DataFieldDTO.Type.PHONE -> InputType.TYPE_CLASS_PHONE
                DataFieldDTO.Type.EMAIL -> InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                DataFieldDTO.Type.POSTAL_CODE -> InputType.TYPE_CLASS_NUMBER
                DataFieldDTO.Type.ID_CARD -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            setOnKeyListener { _, _, _ ->
                binding.piFormTextField.error = null
                false
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) actionDone()
                return@setOnEditorActionListener false
            }
            setOnFocusChangeListener { v, hasFocus ->
                onFocusChanged(this@FormTextField, hasFocus)
                Handler(Looper.getMainLooper()).postDelayed({
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(binding.piFormTextField.editText, 0)
                }, 200)
            }
        }
        binding.piFormTextField.helperText = enforceSpaceWhenEmpty(data.hint) //Needed to make sure height of radio group will not change on error
    }

    override fun validate(): Boolean {
        val input = userInput()
        val errorText = when {
            data.mandatory && input.isNullOrEmpty() -> LocalizedStrings.getString("contact_data_error_mandatory")
            hasValidCharacters(input).not() -> LocalizedStrings.getString("contact_data_error_invalid_chars")
            hasValidLength(input, data.maxLength).not() -> LocalizedStrings.getString("contact_data_error_max_length", data.maxLength)
            data.type == DataFieldDTO.Type.EMAIL && isEmailAddressValid(input).not() -> LocalizedStrings.getString("contact_data_error_invalid_email")
            data.type == DataFieldDTO.Type.PHONE && isPhoneNumberValid(input).not() -> LocalizedStrings.getString("contact_data_error_invalid_mobile")
            else -> null
        }
        error(errorText)
        return errorText == null
    }

    override fun userInput(): String? = binding.piFormTextField.editText?.text?.toString()

    override fun error(errorText: String?) {
        binding.piFormTextField.error = errorText
    }

    private fun hasValidLength(data: String?, maxLength: Int): Boolean {
        return data == null || data.length < maxLength + 1
    }

    private fun hasValidCharacters(data: String?): Boolean {
        val special = "<|>|\\{|\\}"
        val pattern = ".*[" + Pattern.quote(special) + "].*"
        return data == null || !data.matches(pattern.toRegex())
    }

    private fun isPhoneNumberValid(mobilePhone: String?): Boolean {
        val special = "(?!^\\+49$)(?!\\+490)(?!00490)^(\\+[1-9][0-9\\s]{1,18}|0[0-9\\s]{1,19})$"
        return mobilePhone != null && mobilePhone.matches(special.toRegex())
    }

    private fun isEmailAddressValid(email: String?): Boolean {
        return email.isNullOrEmpty().not() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}

class FormDropdownField(inflater: LayoutInflater, parent: ViewGroup, override val data: DataFieldDTO,
                        val onFocusChanged: (field: FormDropdownField, hasFocus: Boolean) -> Unit = { _,_ -> }) : FormField {

    override val binding = PiFormChoicesDropdownBinding.inflate(inflater, parent, true)

    private val items = checkNotNull(data.choices)

    init {
        check(items.isNotEmpty())
        val preselectedText = if (data.preselectedValue != null) items.find { it.key == data.preselectedValue } else null
        binding.piFormChoicesAutocomplete.setText(preselectedText?.display ?: "", false)
        binding.piFormChoicesField.hint = data.display
        val adapter = ArrayAdapter(inflater.context, R.layout.pi_form_choices_dropdown_item, items.map { it.display })
        binding.piFormChoicesAutocomplete.apply {
            setAdapter(adapter)
            setOnItemClickListener { _, _, _, _ ->
                focusSearch(View.FOCUS_DOWN)?.requestFocus()
                error(null)
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) focusSearch(View.FOCUS_DOWN)?.requestFocus()
                return@setOnEditorActionListener false
            }
            setOnFocusChangeListener { _, hasFocus ->
                onFocusChanged(this@FormDropdownField, hasFocus)
            }
        }
        binding.piFormChoicesField.helperText = enforceSpaceWhenEmpty(data.hint) //Needed to make sure height of radio group will not change on error
    }

    override fun validate(): Boolean {
        val input = userSelection()
        val errorText = when {
            data.mandatory && input == null -> LocalizedStrings.getString("contact_data_error_mandatory")
            else -> null
        }
        error(errorText)
        return errorText == null
    }

    override fun userInput(): String? = userSelection()?.key
    override fun error(errorText: String?) {
        binding.piFormChoicesField.error = errorText
    }

    private fun userSelection(): ChoicesDTO? = items.find { binding.piFormChoicesAutocomplete.text.toString() == it.display }
}

class FormDateField(fragment: Fragment, inflater: LayoutInflater, parent: ViewGroup, override val data: DataFieldDTO,
                    val onFocusChanged: (field: FormDateField, hasFocus: Boolean) -> Unit = { _,_ -> }) : FormField {

    override val binding = PiFormTextBinding.inflate(inflater, parent, true)

    private var selectedDate: Calendar? = null

    init {
        binding.piFormTextField.hint = data.display
        binding.piFormTextField.editText?.apply {
            inputType = InputType.TYPE_NULL
            contentDescription = data.key
            showSoftInputOnFocus = false
            setOnFocusChangeListener { _, focused ->
                if (focused) {
                    showDatePicker(inflater.context, data)
                    showKeyboard(fragment.requireActivity(), false, this)
                }
                onFocusChanged(this@FormDateField, focused)
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    clearFocus()
                    requestFocus() // trigger focus change listener
                    performClick()
                }
                false
            }
            maxLines = 1
            isSingleLine = true
            isCursorVisible = false
        }
        binding.piFormTextField.helperText = enforceSpaceWhenEmpty(data.hint) //Needed to make sure height of radio group will not change on error

    }

    override fun validate(): Boolean {
        val input = userInput()
        val errorText = when {
            data.mandatory && input == null -> LocalizedStrings.getString("contact_data_error_mandatory")
            else -> null
        }
        error(errorText)
        return errorText == null
    }

    override fun error(errorText: String?) {
        binding.piFormTextField.error = errorText
    }

    override fun userInput(): String? {
        val date = selectedDate
        return if (date == null) {
            ""
        } else {
            //Convert to ISO date format
            val targetFormat = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
            targetFormat.timeZone = date.timeZone
            targetFormat.format(date.time)
        }
    }

    private fun showDatePicker(context: Context, data: DataFieldDTO) {
        // needed for testautomation - otherwise it is not possible to select dates with appium
        val dateInput = binding.piFormTextField.editText?.text
        if (!dateInput.isNullOrEmpty()) {
            try {
                val cal = Calendar.getInstance()
                cal.time = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).parse(dateInput.toString()) as Date
                selectedDate = cal
            } catch (e: ParseException) {
                dateInput.clear()
            }
        }

        val cal = Calendar.getInstance()
        val date: Calendar = selectedDate ?: Calendar.getInstance(Locale.getDefault())
        val dialog = DatePickerDialog(
            context,
            R.style.PIDatePickerTheme,
            { _, year, month, day ->
                cal.set(Calendar.DAY_OF_MONTH, day)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.YEAR, year)
                val newDate = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = cal.timeInMillis }
                val displayText = SimpleDateFormat.getDateInstance().format(newDate.time)
                selectedDate = newDate
                binding.piFormTextField.editText?.apply {
                    contentDescription = data.key
                    setText(displayText)
                    focusSearch(View.FOCUS_DOWN)?.requestFocus()
                }
                error(null)
            },
            date.get(Calendar.YEAR) , date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)
        )
        dialog.setTitle(data.display)
        dialog.datePicker.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }
}

private fun enforceSpaceWhenEmpty(text: String?) = if (text.isNullOrEmpty()) null else text