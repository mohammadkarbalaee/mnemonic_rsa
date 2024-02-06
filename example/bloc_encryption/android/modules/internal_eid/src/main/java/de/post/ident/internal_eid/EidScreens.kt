package de.post.ident.internal_eid

import android.animation.*
import android.annotation.SuppressLint
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.Keep
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.EventStatus
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.CaseResponseDTO
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.rest.IdentStatusDTO
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.ui.MaterialButtonLoadingController
import de.post.ident.internal_eid.databinding.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


interface EidScreen {
    fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View
}

class AccessRightsScreen(private val certInfo: EidCertificateDto?, private val accessRights: EidAccessRightsDto?,
                         val onAccept: () -> Unit) : EidScreen {

    private lateinit var binding: PiFragmentAccessRightsBinding
    private lateinit var parentFragment: Fragment

    override fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View {
        this.parentFragment = parentFragment
        binding = PiFragmentAccessRightsBinding.inflate(inflater, parentView, false)
        binding.acceptButton.setOnClickListener {
            onAccept()
        }
        binding.certInfoCard.setOnClickListener {
            onCertInfoClicked()
        }

        binding.accessRightsTitle.text = LocalizedStrings.getString("eid_data_title")
        binding.acceptButton.text = LocalizedStrings.getString("eid_data_button_text")
        binding.certInfo.text = LocalizedStrings.getString("eid_certificate_information")
        binding.accessRightsDescription.text = LocalizedStrings.getString("eid_data_description")
        fillAccessRights(accessRights)

        return binding.root
    }

    private fun fillAccessRights(accessRights: EidAccessRightsDto?) {
        binding.accessRightsContainer.removeAllViews()

        accessRights?.required?.forEach {
            val view = (LayoutInflater.from(binding.root.context).inflate(R.layout.pi_access_rights_row, null) as TextView)
                    .apply { text = LocalizedStrings.getString("eid_access_rights_$it") }
            binding.accessRightsContainer.addView(view)
        }
    }

    private fun onCertInfoClicked() {
        if (certInfo != null) {
            CertInfoDialog.newInstance(certInfo).show(parentFragment.childFragmentManager, "CERT_INFO")
        }
    }
}

class CertInfoDialog : DialogFragment() {
    companion object {
        private val CERT_INFO_PARAMETER: BundleParameter<EidCertificateDto> = BundleParameter.moshi(CoreEmmiService.moshi, "CERT_INFO")

        fun newInstance(certInfo: EidCertificateDto): CertInfoDialog = CertInfoDialog()
                .withParameter(certInfo, CERT_INFO_PARAMETER)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = PiFragmentCertInfoBinding.inflate(inflater, container, false)
        val certInfo = checkNotNull(CERT_INFO_PARAMETER.getParameter(arguments))

        binding.certInfoTitle.text = LocalizedStrings.getString("eid_termsofuse_title")

        binding.certIssuerTitle.text = LocalizedStrings.getString("eid_info_cert_title")
        binding.certIssuerName.text = certInfo.issuerName
        binding.certIssuerUrl.text = certInfo.issuerUrl

        binding.certValidityTitle.text = LocalizedStrings.getString("eid_info_validity_title")
        binding.certIssuedOnTitle.text = LocalizedStrings.getString("eid_info_validity_issued_on")
        binding.certIssuedOn.text = convertDate(certInfo.validity?.effectiveDate)
        binding.certValidUntilTitle.text = LocalizedStrings.getString("eid_info_validity_valid_until")
        binding.certValidUntil.text = convertDate(certInfo.validity?.expirationDate)

        binding.certInfoTosTitle.text = LocalizedStrings.getString("eid_info_tos_title")
        binding.certInfoTos.text = certInfo.termsOfUsage
        binding.btnCloseDialog.text = LocalizedStrings.getString(R.string.default_btn_ok)
        binding.btnCloseDialog.setOnClickListener { dismiss() }

        EmmiCoreReporter.send(LogEvent.EI_CERTIFICATE_INFO)

        return binding.root
    }

    private fun convertDate(dateString: String?) : String {
        return dateString?.let {
            val date = SimpleDateFormat("yyyy-MM-dd", context?.resources?.configuration?.locale).parse(it)
            SimpleDateFormat.getDateInstance().format(date)
        } ?: ""
    }
}

class CanInfoScreen(val onContinue: () -> Unit,
                    val onBackClicked: () -> Unit) : EidScreen {
    override fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View {
        val binding = PiFragmentEidCanInfoBinding.inflate(inflater, parentView, false)
        binding.btnContinue.setOnClickListener { onContinue() }
        binding.canDescriptionTitle.text = LocalizedStrings.getString("eid_can_can_required_title")
        binding.canDescriptionSubtitle.text = LocalizedStrings.getString("eid_can_can_required_description")
        binding.canDescriptionWarning.text = LocalizedStrings.getString("eid_can_can_required_help")
        binding.canDescriptionResetService.text = LocalizedStrings.getHtmlString("eid_wrong_pin_entered_twice")
        binding.canDescriptionResetService.movementMethod = LinkMovementMethod.getInstance()
        binding.btnContinue.text = LocalizedStrings.getString(R.string.default_btn_ok)
        binding.btnContinueAlternative.text = LocalizedStrings.getString("default_btn_back_to_methodselection")
        binding.btnContinueAlternative.isVisible = true
        binding.btnContinueAlternative.setOnClickListener { onBackClicked() }
        return binding.root
    }
}

class ScanInfoScreen(private val identContext: ScanScreen.IdentContext, val onContinueClicked: () -> Unit) : EidScreen {
    private val emmiReporter = EmmiCoreReporter
    private lateinit var binding: PiFragmentEidScanInfoBinding

    override fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View {
        binding = PiFragmentEidScanInfoBinding.inflate(inflater, parentView, false)

        binding.scanBtnContinue.setOnClickListener { onContinueClicked() }
        binding.scanBtnContinue.text = LocalizedStrings.getString("eid_scan_info_button")
        binding.scanTitle.text = LocalizedStrings.getString("eid_scan_info_title")
        binding.scanBullet1.text = LocalizedStrings.getHtmlString("eid_scan_info_bullet_1")
        binding.scanBullet2.text = LocalizedStrings.getHtmlString("eid_scan_info_bullet_2")
        binding.scanBullet3.text = LocalizedStrings.getHtmlString("eid_scan_info_bullet_3")

        emmiReporter.send(logEvent = LogEvent.EI_SCAN_INFO, eventContext = mapOf("currentContext" to identContext.logName))

        return binding.root
    }
}

class ScanScreen(val identContext: IdentContext, val onHelpClicked: () -> Unit) : EidScreen {
    private var isOverlayVisible = false
    private var viewBinding: PiFragmentEidScanBinding? = null
    private val emmiReporter = EmmiCoreReporter
    private var isAnimationRunning = false
    private val fadeObjectAnimationDuration = 1000
    private val delayAnimationDuration = 1000
    private val translationAnimationDuration = 1500
    private val resetAnimationDuration = 250
    private val identProgressbarDuration: Long = 10000
    private val pinChangeProgressbarDuration: Long = 2500
    private lateinit var smartphone: ImageView
    private val setPreviewAnimation = AnimatorSet()
    private var eidAnimation: ValueAnimator? = null

    @Keep
    enum class IdentContext(val logName: String) { IDENT("ident"), PIN_CHANGE("pinChange") }

    override fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View {
        val binding = PiFragmentEidScanBinding.inflate(inflater, parentView, false)
        viewBinding = binding

        binding.eidHelpBtn.text = LocalizedStrings.getString("eid_scan_help_btn")
        binding.eidHelpBtn.setOnClickListener {
            isAnimationRunning = false
            setPreviewAnimation.cancel()
            onHelpClicked()
        }

        binding.eidTitle.text = LocalizedStrings.getString("eid_init_title")
        binding.eidDescription.text = LocalizedStrings.getString("eid_scan_screen_description")
        binding.eidHint.text = LocalizedStrings.getString("eid_scan_screen_hint")

        binding.eidProgressTitle.text = LocalizedStrings.getString("eid_progress_alert_title")
        binding.eidProgressSubtitle.text = LocalizedStrings.getHtmlString("eid_progress_alert_subtitle_bold")
        binding.eidSpinnerSubtitle.text = LocalizedStrings.getString("eid_loading_transfer_data")

        if (!isAnimationRunning) {
            isAnimationRunning = true
            // run delayed to enable measuring of layout elements
            smartphone = binding.eidSmartphone
            smartphone.post {
                try {
                    createAnimationPreview()
                    setPreviewAnimation.start()
                } catch (e: Exception) {
                    log("create preview animation failed")
                }
            }
        }

        showOverlay(isOverlayVisible)

        emmiReporter.send(
            logEvent = LogEvent.EI_WAITING_FOR_CARD,
            eventContext = mapOf(
                "currentContext" to identContext.logName
            ),
            attemptId = Commons.attemptId
        )
        emmiReporter.flush()

        return binding.root
    }

    fun showOverlay(isVisible: Boolean) {
        isOverlayVisible = isVisible
        viewBinding?.apply {
            log("Show overlay: $isVisible")
            progressOverlay.isVisible = isOverlayVisible
        }

        if (isVisible) {
            viewBinding?.eidSpinnerSubtitle?.text = LocalizedStrings.getString(when (identContext) {
                IdentContext.IDENT -> "eid_loading_transfer_data"
                IdentContext.PIN_CHANGE -> "eid_change_pin_progress_title"
            })
            createAndStartAnimation(identContext == IdentContext.PIN_CHANGE)
            emmiReporter.send(
                logEvent = LogEvent.EI_CONNECTING_CARD,
                eventContext = mapOf(
                    "currentContext" to identContext.logName
                ),
                attemptId = Commons.attemptId
            )
        } else {
            eidAnimation?.cancel()
        }
    }

    private fun createAndStartAnimation(isPinChange: Boolean) {
        val eidSpinner = viewBinding?.eidSpinner
        val percentage = viewBinding?.eidPercentage
        val (selectedInterpolator, _) = Pair(LinearOutSlowInInterpolator(), Pair(0f, 1f))
        if (eidSpinner != null) {
            eidAnimation = ValueAnimator.ofFloat(0f, eidSpinner.max.toFloat() - 1).apply {
                duration = if (isPinChange) pinChangeProgressbarDuration else identProgressbarDuration
                interpolator = selectedInterpolator
                addUpdateListener { animation ->
                    if (eidSpinner.progress >= eidSpinner.max - 1) {
                        eidSpinner.isIndeterminate = true
                        percentage?.isVisible = false
                    }

                    val progress = animation.animatedValue as Float
                    eidSpinner.progress = progress.roundToInt()
                    percentage?.text = "${eidSpinner.progress}%"
                }
            }
        }
        eidAnimation?.start()
    }

    private fun createAnimationPreview() {

        viewBinding?.let {
            val idCard = it.eidIdCard
            val eidLogo = it.eidLogo
            val spinner = it.eidSpinnerPreview
            val smartphoneCardDetected = it.eidSmartphoneCardDetected

            val pivotY: Float = (idCard.height / 2).toFloat()

            //--- init objects
            val showIdCard: ObjectAnimator = ObjectAnimator.ofFloat(idCard, View.ALPHA, 0f, 1f).setDuration(resetAnimationDuration.toLong())
            val showLogo: ObjectAnimator = ObjectAnimator.ofFloat(eidLogo, View.ALPHA, 0f, 1f).setDuration(resetAnimationDuration.toLong())
            val initObjectsSet = AnimatorSet()
            initObjectsSet.playTogether(showIdCard, showLogo)

            //--- ready - wait a little, then move id card
            val delayBeforeAnimation: ObjectAnimator = ObjectAnimator.ofFloat(smartphone, View.ALPHA, 1f, 1f).setDuration(delayAnimationDuration.toLong())
            val moveIdCardDown: ObjectAnimator = ObjectAnimator.ofFloat(idCard, View.TRANSLATION_Y, 0f, pivotY).setDuration(translationAnimationDuration.toLong())
            moveIdCardDown.interpolator = AccelerateDecelerateInterpolator()

            //--- change state of icons
            val smartphoneWaiting: ObjectAnimator = ObjectAnimator.ofFloat(smartphone, View.ALPHA, 1f, 0f).setDuration(0)
            val smartphoneDetected: ObjectAnimator = ObjectAnimator.ofFloat(smartphoneCardDetected, View.ALPHA, 0f, 1f).setDuration(0)
            val hideLogo: ObjectAnimator = ObjectAnimator.ofFloat(eidLogo, View.ALPHA, 1f, 0f).setDuration(0)
            val showSpinner: ObjectAnimator = ObjectAnimator.ofFloat(spinner, View.ALPHA, 0f, 1f).setDuration(fadeObjectAnimationDuration.toLong())
            val rotateSpinner: ValueAnimator = ValueAnimator.ofInt(0, 100).setDuration((fadeObjectAnimationDuration * 2).toLong())
            rotateSpinner.addUpdateListener { animation ->
                spinner.progress = animation.animatedValue as Int
            }
            val showSpinnerSet = AnimatorSet()
            showSpinnerSet.playTogether(smartphoneWaiting, smartphoneDetected, hideLogo, showSpinner, rotateSpinner)
            val hideSpinner: ObjectAnimator = ObjectAnimator.ofFloat(spinner, View.ALPHA, 1f, 0f).setDuration(0)
            val stateChangeSet = AnimatorSet()
            stateChangeSet.playSequentially(showSpinnerSet, hideSpinner)

            //--- hide objects again
            val hideIdCard: ObjectAnimator = ObjectAnimator.ofFloat(idCard, View.ALPHA, 1f, 0f).setDuration(0)
            val moveIdCardUp: ObjectAnimator = ObjectAnimator.ofFloat(idCard, View.TRANSLATION_Y, pivotY, 0f).setDuration(resetAnimationDuration.toLong())
            val hideSmartphoneDetected = ObjectAnimator.ofFloat(smartphoneCardDetected, View.ALPHA, 1f, 0f).setDuration(0)
            val resetViews = AnimatorSet()
            resetViews.playTogether(hideIdCard, moveIdCardUp, hideSmartphoneDetected)

            //---
            setPreviewAnimation.playSequentially(initObjectsSet, delayBeforeAnimation, moveIdCardDown, stateChangeSet, resetViews)
            setPreviewAnimation.addListener(object : Animator.AnimatorListener {
                private var canceled = false
                override fun onAnimationStart(animation: Animator) {
                    isAnimationRunning = true
                    canceled = false
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!canceled) {
                        animation.start()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    canceled = true
                }

                override fun onAnimationRepeat(animation: Animator) {
                    //;
                }
            })
        }
    }
}

class SuccessScreen(private val identStatus: IdentStatusDTO?, private val isPinChange: Boolean = false, val onCloseClicked: () -> Unit) : EidScreen {
    override fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View {
        val binding = PiFragmentEidSuccessBinding.inflate(inflater, parentView, false)
        val continueButtonController = MaterialButtonLoadingController(inflater.context, binding.btnQuit)

        binding.btnQuit.setOnClickListener {
            continueButtonController.loadingAnimation(true)
            onCloseClicked()
        }

        binding.eidSuccessTitle.text = if (isPinChange) LocalizedStrings.getHtmlString("eid_success_change_pin_title") else identStatus?.title
        binding.eidSuccessSubtitle.text = if (isPinChange) LocalizedStrings.getHtmlString("eid_success_change_pin_long_text") else identStatus?.subtitle
        binding.eidSuccessIcon.isVisible = isPinChange.not()
        binding.eidSuccessHint.isVisible = isPinChange.not()
        binding.eidSuccessHint.text = LocalizedStrings.getHtmlString("eid_status_hint")

        binding.btnQuit.text = if (isPinChange) {
            LocalizedStrings.getString("eid_start_ident")
        } else if (CoreConfig.isSdk.not() && identStatus?.continueButton != null) {
            identStatus.continueButton?.text
        } else {
            LocalizedStrings.getString("default_btn_quit")
        }

        val emmiReporter = EmmiCoreReporter
        emmiReporter.send(logEvent = if (isPinChange) LogEvent.EI_TRANSPORT_SUCCESS else LogEvent.EI_SUCCESS)
        emmiReporter.flush()

        return binding.root
    }
}

@Suppress("FunctionName")
fun HelpScreen(activity: EidIdentActivity, caseResponse: CaseResponseDTO,
               onRetryClicked: () -> Unit, onBackClicked: () -> Unit, attemptId: String?
): EidScreen = ErrorScreen(activity = activity, caseResponse = caseResponse,
        onRetryClicked = onRetryClicked, onBackClicked = onBackClicked, attemptId = attemptId)

class ErrorScreen(val activity: EidIdentActivity, val caseResponse: CaseResponseDTO,
                  val eidException: EidException? = null, val onRetryClicked: () -> Unit,
                  val onBackClicked: () -> Unit, val attemptId: String?
) : EidScreen {
    private lateinit var binding: PiFragmentEidHelpErrorBinding
    private val emmiReporter = EmmiCoreReporter

    @SuppressLint("SetTextI18n")
    override fun inflate(inflater: LayoutInflater, parentView: ViewGroup, parentFragment: Fragment): View {
        binding = PiFragmentEidHelpErrorBinding.inflate(inflater, parentView, false)

        if (eidException != null) {
            binding.errorTitle.text = eidException.errorTitle
            binding.errorMessage.text = "${eidException.errorMessage} (Code: ${eidException.errorCodeId})"

            if (eidException.isRecoverable.not()) {
                eidException.errorImageRes?.let {
                    binding.errorImage.setImageResource(it)
                    binding.errorImage.isVisible = true
                }
                eidException.errorHint?.let {
                    binding.errorHint.text = it
                    binding.errorHint.isVisible = true
                }
                eidException.errorAdditionalInfo?.let {
                    binding.errorDescription.text = it
                    binding.errorDescription.movementMethod = LinkMovementMethod.getInstance()
                    binding.errorDescription.isVisible = true
                }
                binding.errorSeparator.isVisible = false
            }
        } else {
            binding.errorTextContainer.isVisible = false
        }

        if (eidException == null || eidException.isRecoverable) {
            binding.videoContainer.isVisible = true
            binding.videoTitle.text = LocalizedStrings.getString("youtube_player_title")

            if (CoreConfig.appConfig.eIdHelpVideoId?.isNotEmpty() == true) {
                activity.volumeControlStream = AudioManager.STREAM_MUSIC

                binding.youtubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.cueVideo(CoreConfig.appConfig.eIdHelpVideoId!!, 0f)
                    }

                    override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerState) {
                        val eventContext: MutableMap<String, String> = HashMap()
                        if (state == PlayerState.PLAYING) {
                            eventContext["state"] = "started"
                        } else if (state == PlayerState.ENDED) {
                            eventContext["state"] = "ended"
                        }
                        if (eventContext.isNotEmpty()) {
                            emmiReporter.send(logEvent = LogEvent.EI_ERROR_SCREEN_VIDEO_ACTION, eventContext = eventContext)
                        }
                    }
                })

                activity.lifecycle.addObserver(binding.youtubePlayerView)
            } else { // no video id, hide player
                binding.youtubePlayerView.isVisible = false
                binding.videoSeparator.piSeparator.isVisible = false
            }

            binding.btnRetry.apply {
                isVisible = true
                text = if (eidException != null) LocalizedStrings.getString("default_btn_retry") else LocalizedStrings.getString(R.string.default_btn_ok)
                setOnClickListener { onRetryClicked() }
            }
        }

        binding.helpBullet1.text = LocalizedStrings.getString("eid_help_bullet_1")
        binding.helpBullet2.text = LocalizedStrings.getString("eid_help_bullet_2")
        binding.helpBullet3.text = LocalizedStrings.getString("eid_help_bullet_3")
        binding.helpBullet4.text = LocalizedStrings.getString("eid_help_bullet_4")

        binding.btnMethodSelection.apply {
            text = if (Commons.skipMethodSelection) LocalizedStrings.getString("default_btn_quit") else activity.getCaseResponse().toMethodSelection?.text
            setOnClickListener { onBackClicked() }
        }

        val eventContext = if (eidException != null) listOfNotNull(
                "errorCode" to eidException.errorCodeName,
                "errorCodeIdentifier" to eidException.errorCodeId.toString(),
                "errorSource" to eidException.errorSource,
                "isHelpScreen" to "false",
            if (eidException.errorCodeName == EidError.BAD_STATE.exception().errorCodeName || eidException.errorCodeName == EidError.TRUSTED_CHANNEL_FAILURE.exception().errorCodeName) "isReading" to activity.getIsReadingNfc().toString() else null
        ).toMap() else mapOf(
                "isHelpScreen" to "true"
        )

        emmiReporter.send(logEvent = LogEvent.EI_ERROR_SCREEN, errorCode = eidException?.loggingErrorCode, message = eidException?.loggingMessage, eventContext = eventContext, attemptId = attemptId, eventStatus = EventStatus.FAILURE)

        return binding.root
    }
}