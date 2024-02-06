package de.post.ident.internal_autoid.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import de.post.ident.internal_autoid.AutoIdentActivity
import de.post.ident.internal_autoid.databinding.PiFragmentStepSuccessBinding
import de.post.ident.internal_core.util.LocalizedStrings

data class AutoIdStepSuccessData(val identStep: AutoIdentActivity.Screen, val title: String, val description: String)

val stepSuccessViewDataList = listOf(
    AutoIdStepSuccessData(
        identStep = AutoIdentActivity.Screen.DOC_CHECK_FRONT,
        title = "docCheck_finish_title",
        description = "dvf_finish_description"
    ),
    AutoIdStepSuccessData(
        identStep = AutoIdentActivity.Screen.DVF,
        title = "dvf_finish_title",
        description = "dvf_finish_description"
    )
)

class StepSuccessView(
    val binding: PiFragmentStepSuccessBinding,
    private val act: AutoIdentActivity
) {

    val animation = AnimatorSet()

    private fun createAnimationPreview() {
        binding.let {
            val icon = it.icon
            val rotationDuration = 3000L
            val gear: ValueAnimator = ValueAnimator.ofFloat(0f, 360f).setDuration(rotationDuration)
            gear.interpolator = LinearInterpolator()

            gear.addUpdateListener { animation ->
                icon.rotation = (animation.animatedValue as Float)
            }

            animation.playTogether(gear)
            animation.addListener(object : Animator.AnimatorListener {
                private var canceled = false
                override fun onAnimationStart(animation: Animator) {
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

    fun update() {
        stepSuccessViewDataList.forEach {
            if (it.identStep.name == act.getCurrentIdentStep().name) {
                binding.titleText.text = LocalizedStrings.getString(it.title)
                binding.descriptionText.text = LocalizedStrings.getString(it.description)
            }
        }
        createAnimationPreview()
        animation.start()
    }
}