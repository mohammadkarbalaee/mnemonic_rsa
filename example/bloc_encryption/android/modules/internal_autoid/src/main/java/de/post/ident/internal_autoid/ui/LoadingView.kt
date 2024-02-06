package de.post.ident.internal_autoid.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.core.view.isVisible
import de.post.ident.internal_autoid.AutoIdentActivity
import de.post.ident.internal_autoid.databinding.PiFragmentLoadingBinding
import de.post.ident.internal_core.util.LocalizedStrings

data class AutoIdLoadingScreenData(val identStep: AutoIdentActivity.Screen, val title: String, val description: String)

val loadingViewDataList = listOf(
    AutoIdLoadingScreenData(
        identStep = AutoIdentActivity.Screen.DOC_CHECK_BACK,
        title = "docCheck_finish_title",
        description = "dvf_finish_description"
    ),
    AutoIdLoadingScreenData(
        identStep = AutoIdentActivity.Screen.FVLC,
        title = "fvlc_waiting_title",
        description = "fvlc_waiting_description"
    )
)

class LoadingView(
    val binding: PiFragmentLoadingBinding,
    private val act: AutoIdentActivity
) {
    private val animation = AnimatorSet()
    fun update() {
        animation.cancel()
        loadingViewDataList.forEach {
            if (it.identStep.name == act.getCurrentIdentStep().name) {
                binding.titleText.text = LocalizedStrings.getString(it.title)
                binding.descriptionText.text = LocalizedStrings.getString(it.description)
                iconVisibility(isDocCheck = AutoIdentActivity.Screen.DOC_CHECK_BACK.name == act.getCurrentIdentStep().name)
            }
        }
    }

    private fun iconVisibility(isDocCheck: Boolean) {
        binding.spinner.isVisible = isDocCheck
        binding.icon.isVisible = !isDocCheck

        if (!isDocCheck) {
            createAnimationPreview()
            animation.start()
        }
    }

    private fun createAnimationPreview() {
        val rotationDuration = 3000L
        val gear: ValueAnimator = ValueAnimator.ofFloat(0f, 360f).setDuration(rotationDuration)
        gear.interpolator = LinearInterpolator()
        gear.addUpdateListener { animation ->
            binding.icon.rotation = (animation.animatedValue as Float)
        }
        animation.playTogether(gear)
        animation.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
            }

            override fun onAnimationEnd(animation: Animator) {
                animation.start()
            }

            override fun onAnimationCancel(animation: Animator) {
                binding.icon.isVisible = false
            }

            override fun onAnimationRepeat(animation: Animator) {
            }
        })
    }
}