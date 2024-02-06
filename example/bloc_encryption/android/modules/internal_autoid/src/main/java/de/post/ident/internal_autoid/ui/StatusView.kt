package de.post.ident.internal_autoid.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.*
import androidx.annotation.Keep
import androidx.constraintlayout.widget.ConstraintSet
import de.post.ident.internal_autoid.AutoIdentActivity
import de.post.ident.internal_autoid.R
import de.post.ident.internal_autoid.databinding.PiFragmentStatusBinding
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.util.LocalizedStrings

@Keep
enum class AutoIdStatus(
    val titleTextKey: String? = null,
    val descriptionTextKey: String? = null,
    val iconResId: Int = R.drawable.pi_ic_check,
    var userFeedback: String? = null
) {
    RESULT_AGENT_REQUIRED(titleTextKey = "autoid_in_progress",descriptionTextKey = "autoid_thanks_pending", iconResId = R.drawable.pi_ic_gear),
    RESULT_DECLINED,
    RESULT_SUCCESS(descriptionTextKey = "autoid_status_success"),
    RESULT_INCOMPLETE(titleTextKey = "autoid_not_done"),
    REENTRY_IN_PROGRESS(titleTextKey = "autoid_in_progress", descriptionTextKey = "autoid_in_progress_description", iconResId = R.drawable.pi_ic_gear),
    REENTRY_INCOMPLETE(titleTextKey = "autoid_incomplete"),
    REENTRY_SUCCESS(titleTextKey = "autoid_already_complete", descriptionTextKey = "autoid_already_complete_description")
}

class StatusView(
    val binding: PiFragmentStatusBinding,
    private val act: AutoIdentActivity,
    buttonClicked: (BtnAction) -> Unit
) {

    val animation = AnimatorSet()

    init {
        binding.endBtn.text = LocalizedStrings.getString("default_btn_quit")
        binding.endBtn.setOnClickListener {
            buttonClicked(BtnAction.STOP_AUTOID)
            it.isEnabled = false
        }
    }

    fun update() {
        val status = act.status

        if (status == AutoIdStatus.RESULT_DECLINED) {
            binding.icon.visibility = View.GONE
        } else {
            binding.icon.visibility =  View.VISIBLE
            binding.icon.setImageResource(status.iconResId)
        }
        binding.titleText.text = status.titleTextKey?.let { LocalizedStrings.getString(it) }
        val descriptionTextKey = status.userFeedback ?: status.descriptionTextKey
        binding.statusText.text = descriptionTextKey?.let { LocalizedStrings.getString(it) }

        //modify constraints for "declined" layout
        val constraintLayout = binding.root
        val constraintSet = ConstraintSet().apply { clone(constraintLayout) }
        if (status == AutoIdStatus.RESULT_DECLINED) {
            constraintSet.connect(
                    binding.statusText.id,
                    ConstraintSet.BOTTOM,
                    binding.guideline2.id,
                    ConstraintSet.TOP
            )
            constraintSet.setGuidelinePercent(binding.guideline2.id, 0.45f)
        } else {
            constraintSet.clear(binding.statusText.id, ConstraintSet.BOTTOM)
            constraintSet.setGuidelinePercent(binding.guideline2.id, 0.5f)
        }
        constraintSet.applyTo(constraintLayout)

        when (status) {
            AutoIdStatus.REENTRY_IN_PROGRESS,
            AutoIdStatus.RESULT_AGENT_REQUIRED -> {
                createAnimationPreview()
                animation.start()
            }
            else -> { if (animation.isRunning) animation.end() }
        }

        binding.endBtn.text = when (status) {
            AutoIdStatus.REENTRY_IN_PROGRESS -> {
                if (CoreConfig.isSdk) {
                    LocalizedStrings.getString("identification_canceled_button_text_back_to_principal",
                        act.getProcessDescriptionData().principalDisplayName)
                } else {
                    LocalizedStrings.getString("default_btn_quit")
                }
            }
            AutoIdStatus.REENTRY_SUCCESS -> LocalizedStrings.getString("default_btn_close")
            else -> LocalizedStrings.getString("default_btn_quit")
        }
    }

    private fun createAnimationPreview() {
        binding.let {
            val icon = it.icon

            val rotationDuration = 2000L
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

    @Keep
    enum class BtnAction {
        STOP_AUTOID
    }
}