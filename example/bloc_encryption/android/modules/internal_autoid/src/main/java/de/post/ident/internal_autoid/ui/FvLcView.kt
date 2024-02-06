package de.post.ident.internal_autoid.ui

import android.os.CountDownTimer
import android.view.View
import androidx.annotation.Keep
import androidx.core.view.isVisible
import de.post.ident.internal_autoid.AutoIdentActivity
import de.post.ident.internal_autoid.R
import de.post.ident.internal_autoid.databinding.PiFragmentFvlcBinding
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.showAlertDialog


@Keep
enum class FaceDirection {
    TOP, RIGHT, DOWN, LEFT, CENTER
}

class FvLcView(
    binding: PiFragmentFvlcBinding,
    activity: AutoIdentActivity,
    buttonClicked: (BtnAction) -> Unit
) {

    private var viewBinding = binding
    private var act = activity
    private var lastFaceDirection = FaceDirection.CENTER
    private var timer: CountDownTimer? = null
    private var isRunning = false

    init {
        binding.piTitle.text = LocalizedStrings.getString("autoid_fvlc_title_preparing")
        binding.continueBtn.text = LocalizedStrings.getString("dvf_button_start")
        binding.piIcon.setOnClickListener { showAlertDialog(activity, LocalizedStrings.getString("autoid_fvlc_info_dialog_text")) }
        binding.continueBtn.setOnClickListener {
            act.showBackButton(false) {}
            binding.titleContainer.isVisible = false
            it.isEnabled = false
            it.visibility = View.INVISIBLE
            binding.countdown.isVisible = true
            timer = object : CountDownTimer(3500, 100) {
                override fun onTick(millisUntilFinished: Long) {
                    binding.countdown.text = ((millisUntilFinished / 1000).toString())
                    // starting the fvlc next run 500ms before setting the the countdown timer to invisible improves the usability
                    if (millisUntilFinished < 1000 && !isRunning) {
                        isRunning = true
                        buttonClicked(BtnAction.START_FVLC)
                    }
                    if (millisUntilFinished < 1000) binding.countdown.isVisible = false
                }

                override fun onFinish() {
                    binding.countdown.text = ""
                    binding.countdown.visibility = View.GONE
                }
            }.start()
        }
        binding.continueBtn.isEnabled = true
    }

    @Keep
    enum class BtnAction {
        START_FVLC
    }

    fun updateFaceDirection(faceDirection: FaceDirection) {
        if (lastFaceDirection != faceDirection && viewBinding.root.isVisible) {
            when (faceDirection) {
                FaceDirection.CENTER -> viewBinding.maskFvlc.setImageResource(R.drawable.pi_mask_fvlc)
                FaceDirection.TOP -> viewBinding.maskFvlc.setImageResource(R.drawable.pi_mask_fvlc_up)
                FaceDirection.DOWN -> viewBinding.maskFvlc.setImageResource(R.drawable.pi_mask_fvlc_down)
                FaceDirection.LEFT -> viewBinding.maskFvlc.setImageResource(R.drawable.pi_mask_fvlc_left)
                FaceDirection.RIGHT -> viewBinding.maskFvlc.setImageResource(R.drawable.pi_mask_fvlc_right)
            }
            act.vibratePhone()
            lastFaceDirection = faceDirection
        }
    }

    fun resetView() {
        isRunning = false
        timer?.cancel()
        viewBinding.countdown.visibility = View.GONE
        viewBinding.titleContainer.isVisible = true
        viewBinding.piTitle.text = LocalizedStrings.getString("autoid_fvlc_title_preparing")
        viewBinding.continueBtn.isVisible = true
        viewBinding.continueBtn.isEnabled = true
        viewBinding.maskFvlc.setImageResource(R.drawable.pi_mask_fvlc)
    }
}