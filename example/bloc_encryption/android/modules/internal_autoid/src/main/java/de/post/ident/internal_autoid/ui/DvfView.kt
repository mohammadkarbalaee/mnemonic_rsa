package de.post.ident.internal_autoid.ui

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.text.Spanned
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import androidx.annotation.Keep
import androidx.core.view.isVisible
import de.post.ident.internal_autoid.AutoIdentActivity
import de.post.ident.internal_autoid.databinding.PiFragmentDvfBinding
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.showHtmlAlertDialog
import kotlinx.coroutines.DelicateCoroutinesApi

@OptIn(DelicateCoroutinesApi::class)
class DvfView(
    val binding: PiFragmentDvfBinding,
    val activity: AutoIdentActivity,
    val buttonClicked: (BtnAction) -> Unit
) : TiltingOverlay.AnimationValueListener {
    private val OVERLAY_ANIMATION_TILT_DEGREES = 6f
    private val OVERLAY_ANIMATION_SWITCH_DELAY = 6000L
    private val OVERLAY_ANIMATION_TILT_DURATION = 1500L
    private val ARROW_MIN_ALPHA = 0.5f
    private val animationHandler by lazy { Handler(Looper.getMainLooper()) }

    var vb: PiFragmentDvfBinding = binding
    var btnClicked = buttonClicked

    private val titleTextKeys = mapOf(
        DocumentType.ID_CARD to "dvf_title_intro_id_html",
        DocumentType.PASSPORT to "dvf_title_intro_pass_html"
    )
    private val titleTextStartedKeys = mapOf(
        DocumentType.ID_CARD to "dvf_title_started",
        DocumentType.PASSPORT to "dvf_title_started_pass"
    )
    private val infoText = mapOf(
        DocumentType.ID_CARD to "autoid_dvf_id_info_dialog_html",
        DocumentType.PASSPORT to "autoid_dvf_pass_info_dialog_html"
    )

    init {
        binding.continueBtn.text = LocalizedStrings.getString("dvf_button_start")
        binding.piIcon.setOnClickListener { showHtmlAlertDialog(activity, LocalizedStrings.getHtmlString(infoText.getValue(activity.documentType))) }
        binding.continueBtn.setOnClickListener {
            changeTitleText(LocalizedStrings.getHtmlString(titleTextStartedKeys.getValue(activity.documentType)))
            buttonClicked(BtnAction.START_DFV)
            updateButton(isStarted = true)
//            binding.continueBtn.strokeColor = null
//            binding.continueBtn.strokeWidth = 0
        }
        vb.flashlightButton.setOnClickListener {
            btnClicked(BtnAction.FLASHLIGHT)
        }
    }

    fun updateUi() {
        vb.tiltingOverlay.documentType = activity.documentType
    }

    fun start() {
        startTiltingAnimation()
        changeTitleText(LocalizedStrings.getHtmlString(titleTextKeys.getValue(activity.documentType)))
        Handler(Looper.getMainLooper()).postDelayed(
            {
                binding.continueBtn.isEnabled = true
//                binding.continueBtn.strokeWidth = UiUtil.dpToPx(activity, 2f)
//                binding.continueBtn.strokeColor = ColorStateList(
//                    arrayOf(intArrayOf(android.R.attr.state_enabled)),
//                    intArrayOf(activity.resources.getColor(R.color.pi_btn_focus))
//                )
//                changeTitleText(LocalizedStrings.getString("dvf_title_prepared"))
//                Handler(Looper.getMainLooper()).postDelayed(
//                    {
//                        changeTitleText(LocalizedStrings.getString(titleTextKeys.getValue(activity.documentType)))
//                    },
//                    3500)
            },
            3000)
    }

    private fun startTiltingAnimation() {
        vb.tiltingOverlay.listener = this
        vb.tiltingOverlay.post {
            vb.tiltingOverlay.startAnimation(
                TiltingOverlay.TiltMode.VERTICAL,
                OVERLAY_ANIMATION_TILT_DEGREES,
                OVERLAY_ANIMATION_TILT_DURATION
            )
            setArrowVisibilities()
            animationHandler.postDelayed({
                switchTiltMode()
            }, OVERLAY_ANIMATION_SWITCH_DELAY)
        }
    }

    fun stopAnimation() = vb.tiltingOverlay.stopAnimation()

    private fun changeTitleText(newText: Spanned) {
        val animationDuration = 200L
        binding.piTitle.startAnimation(
            AlphaAnimation(1.0f, 0.0f).apply {
                duration = animationDuration
                setAnimationListener(object: AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationRepeat(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        binding.piTitle.text = newText
                        binding.piTitle.startAnimation(
                            AlphaAnimation(0.0f, 1.0f).apply {
                                duration = animationDuration
                            }
                        )
                    }
                })
            }
        )
    }

    private fun switchTiltMode() {
        if (binding.root.isVisible.not()) return
        vb.tiltingOverlay.switchTiltMode()
        setArrowVisibilities()
        animationHandler.postDelayed({
            switchTiltMode()
        }, OVERLAY_ANIMATION_SWITCH_DELAY)
    }

    override fun onAnimationValue(value: Float) {
        val transposedAnimationValue = (value + OVERLAY_ANIMATION_TILT_DEGREES)
        val transposedAnimationDegrees = OVERLAY_ANIMATION_TILT_DEGREES * 2
        val animationPercentage = transposedAnimationValue / transposedAnimationDegrees

        setArrowAlphas(animationPercentage, 1f - animationPercentage)
    }

    private fun setArrowAlphas(aAlpha: Float, bAlpha: Float) {
        when (vb.tiltingOverlay.tiltMode) {
            TiltingOverlay.TiltMode.VERTICAL -> {
                vb.arrowLeft.alpha = correctAlphaForMin(aAlpha)
                vb.arrowRight.alpha = correctAlphaForMin(bAlpha)
            }
            TiltingOverlay.TiltMode.HORIZONTAL -> {
                vb.arrowDown.alpha = correctAlphaForMin(aAlpha)
                vb.arrowUp.alpha = correctAlphaForMin(bAlpha)
            }
        }
    }

    private fun correctAlphaForMin(alpha: Float) = if (alpha >= ARROW_MIN_ALPHA) alpha else ARROW_MIN_ALPHA

    private fun setArrowVisibilities() {
        val tiltModeHorizontal = vb.tiltingOverlay.tiltMode == TiltingOverlay.TiltMode.HORIZONTAL
        setArrowVisibility(vb.arrowUp, tiltModeHorizontal)
        setArrowVisibility(vb.arrowDown, tiltModeHorizontal)
        setArrowVisibility(vb.arrowLeft, !tiltModeHorizontal)
        setArrowVisibility(vb.arrowRight, !tiltModeHorizontal)
    }

    private fun setArrowVisibility(arrow: View, visible: Boolean) {
        arrow.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    private fun updateButton(isStarted: Boolean) {
        vb.continueBtn.isEnabled = !isStarted
        vb.continueBtn.isVisible = !isStarted
        vb.piIcon.isVisible = !isStarted
    }

    fun reset() {
        updateButton(false)
        binding.piTitle.text = LocalizedStrings.getHtmlString(titleTextKeys.getValue(activity.documentType))
    }

    @Keep
    enum class BtnAction {
        START_DFV, FLASHLIGHT
    }
}