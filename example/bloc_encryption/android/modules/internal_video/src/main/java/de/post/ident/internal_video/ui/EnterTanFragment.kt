package de.post.ident.internal_video.ui

import android.app.Activity
import android.content.*
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextSwitcher
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.showAlertDialog
import de.post.ident.internal_core.util.ui.MaterialButtonLoadingController
import de.post.ident.internal_core.util.ui.showKeyboard
import de.post.ident.internal_video.*
import de.post.ident.internal_video.databinding.PiFragmentEnterTanBinding
import de.post.ident.internal_video.rest.ChatChangeMessageDTO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.regex.Pattern


class EnterTanFragment : BaseVideoFragment() {
    companion object {
        fun newInstance(): EnterTanFragment = EnterTanFragment()
        private const val SMS_CONSENT_REQUEST = 767
    }

    private lateinit var viewBinding: PiFragmentEnterTanBinding
    private lateinit var submitButtonController: MaterialButtonLoadingController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoManager.currentState.observe(this) {
            onProgressUpdate(it)
        }
        videoManager.novomindEventBus.subscribe(this, ::onNewData)
    }

    private val smsVerificationReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                intent.extras?.let { extras ->
                    val smsRetrieverStatus = extras.getParcelable<Status>(SmsRetriever.EXTRA_STATUS)

                    if (smsRetrieverStatus?.statusCode == CommonStatusCodes.SUCCESS) {
                        extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)?.let { consentIntent ->
                            try {
                                showKeyboard(requireActivity(), false)
                                startActivityForResult(consentIntent, SMS_CONSENT_REQUEST)
                            } catch (e: ActivityNotFoundException) {
                                log("activity couldn't be started", e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentEnterTanBinding.inflate(inflater, container, false)

        initView(viewBinding)
        initSmsRetrieverAPI()

        return viewBinding.root
    }

    override fun onDestroyView() {
        showKeyboard(requireActivity(), false)
        super.onDestroyView()
        requireActivity().unregisterReceiver(smsVerificationReceiver)
    }

    // decide whether to show keyboard on showing screen (might hide layout elements)
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        showKeyboard(true, viewBinding.etTanLayout.editText)
//    }

    private fun initSmsRetrieverAPI() {
        // https://developers.google.com/identity/sms-retriever/user-consent/request
        SmsRetriever.getClient(requireActivity()).startSmsUserConsent(null)

        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        requireActivity().registerReceiver(smsVerificationReceiver, intentFilter)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SMS_CONSENT_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            val tan = parseTanFromSms(message)
            if (tan != null) {
                viewBinding.etTanLayout.editText?.setText(tan)
                sendTan()
            }
        }
    }

    private fun initView(vb: PiFragmentEnterTanBinding) {
        setViewFactory(vb.channelData)

        vb.enterTanTitle.text = LocalizedStrings.getString("enter_tan_title")
        vb.buttonConfirmTanCode.text = LocalizedStrings.getString("default_btn_confirm")
        vb.buttonConfirmTanCode.setOnClickListener { sendTan() }

        vb.etTan.hint = LocalizedStrings.getString("enter_tan_code_hint")
        vb.etTan.addTextChangedListener {
            vb.buttonConfirmTanCode.isEnabled = it?.length == 6
            vb.etTanLayout.error = null
        }
        vb.etTan.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || (event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {
                sendTan()
                return@setOnEditorActionListener true
            }
            false
        }

        submitButtonController = MaterialButtonLoadingController(requireContext(), viewBinding.buttonConfirmTanCode)
    }

    private fun sendTan() {
        viewBinding.buttonConfirmTanCode.isEnabled = false
        submitButtonController.loadingAnimation(true)
        val tan = viewBinding.etTan.text.toString().trim()

        lifecycleScope.launch {
            try {
                videoManager.sendTan(tan)
            } catch (err: Throwable) {
                ensureActive()
                showAlertDialog(context, err)
                showKeyboard(requireActivity(), true, viewBinding.etTanLayout.editText)
            } finally {
                ensureActive()
                viewBinding.buttonConfirmTanCode.isEnabled = true
            }
        }
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

    private fun onNewData(data: NovomindEvent) {
        when (data) {
            is NovomindEvent.WorkflowUserData -> {
                when (data.userData.activeTanChannel) {
                    ChatChangeMessageDTO.WorkflowUserDataDTO.ActiveTanChannelDTO.EMAIL -> {
                        viewBinding.channelLabel.text = LocalizedStrings.getString("enter_tan_email_address")
                        viewBinding.channelData.setText(data.userData.email)
                    }
                    ChatChangeMessageDTO.WorkflowUserDataDTO.ActiveTanChannelDTO.SMS -> {
                        viewBinding.channelLabel.text = LocalizedStrings.getString("enter_tan_mobile_nr")
                        viewBinding.channelData.setText(data.userData.mobileNr)
                    }
                    else -> {}
                }
            }
            is NovomindEvent.TanResult -> {
                submitButtonController.loadingAnimation(false)
                if (data.result) {
                    // redirect is happening due to status change
                    viewBinding.etTanLayout.error = null
                    showKeyboard(requireActivity(), false)
                } else {
                    viewBinding.etTanLayout.error = LocalizedStrings.getString("err_tan_entered_invalid")
                    viewBinding.buttonConfirmTanCode.isEnabled = true
                    showKeyboard(requireActivity(), true, viewBinding.etTanLayout.editText)
                }
            }
            else -> {}
        }
    }

    private fun onProgressUpdate(serverState: VideoState) {
        when (serverState) {
            VideoState.SEND_TAN -> {
                emmiReporter.send(LogEvent.VC_STEP_TAN)
            }
            else -> {}
        }
    }

    private fun parseTanFromSms(message: String?): String? {
        message?.let {
            val pattern = Pattern.compile("[0-9]{6}")
            val matcher = pattern.matcher(it)
            while (matcher.find()) {
                val mr = matcher.toMatchResult()
                return mr.group(0)
            }
        }
        return null
    }
}