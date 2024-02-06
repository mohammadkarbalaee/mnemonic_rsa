package de.post.ident.internal_video.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.observe
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_video.R
import de.post.ident.internal_video.VideoState
import de.post.ident.internal_video.databinding.PiFragmentCaptureDataBinding


class CaptureDataFragment : BaseVideoFragment() {
    companion object {
        fun newInstance(): CaptureDataFragment = CaptureDataFragment()
    }

    private lateinit var viewBinding: PiFragmentCaptureDataBinding

    init {
        videoManager.currentState.observe(this, ::onProgressUpdate)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentCaptureDataBinding.inflate(inflater, container, false)

        val captureUserFacePhoto = (activity as VideoIdentActivity).getCaseResponse().modules.videoChat?.captureUserFacePhoto ?: false
        if (captureUserFacePhoto.not()) {
            viewBinding.layoutCaptureProfilePicture.visibility = View.GONE
        }

        viewBinding.captureDataTitle.text = LocalizedStrings.getString("capture_data_title")
        viewBinding.textFront.text = LocalizedStrings.getString("capture_data_text_front")
        viewBinding.textBack.text = LocalizedStrings.getString("capture_data_text_back")
        viewBinding.textProfile.text = LocalizedStrings.getString("capture_data_text_face")

        return viewBinding.root
    }

    private fun onProgressUpdate(serverState: VideoState) {
        when (serverState) {
            VideoState.DECLARATION_OF_CONSENT -> {
                emmiReporter.send(LogEvent.VC_STEP_CONSENT)
                setDrawableState(viewBinding.imageFrontCheck, viewBinding.textFront, checked = false, active = false)
                setDrawableState(viewBinding.imageBackCheck, viewBinding.textBack, checked = false, active = false)
                setDrawableState(viewBinding.imageProfileCheck, viewBinding.textProfile, checked = false, active = false)
            }
            VideoState.CAPTURE_SCREENSHOT_IDCARD_FRONT -> {
                emmiReporter.send(LogEvent.DR_STEP_PHOTO_FRONT)
                setDrawableState(viewBinding.imageFrontCheck, viewBinding.textFront, checked = false, active = true)
                setDrawableState(viewBinding.imageBackCheck, viewBinding.textBack, checked = false, active = false)
                setDrawableState(viewBinding.imageProfileCheck, viewBinding.textProfile, checked = false, active = false)
            }
            VideoState.CAPTURE_SCREENSHOT_IDCARD_BACK -> {
                emmiReporter.send(LogEvent.DR_STEP_PHOTO_BACK)
                setDrawableState(viewBinding.imageFrontCheck, viewBinding.textFront, checked = true, active = true)
                setDrawableState(viewBinding.imageBackCheck, viewBinding.textBack, checked = false, active = true)
                setDrawableState(viewBinding.imageProfileCheck, viewBinding.textProfile, checked = false, active = false)
            }
            VideoState.CAPTURE_SCREENSHOT_OF_FACE -> {
                emmiReporter.send(LogEvent.DR_STEP_PHOTO_PORTRAIT)
                setDrawableState(viewBinding.imageFrontCheck, viewBinding.textFront, checked = true, active = true)
                setDrawableState(viewBinding.imageBackCheck, viewBinding.textBack, checked = true, active = true)
                setDrawableState(viewBinding.imageProfileCheck, viewBinding.textProfile, checked = false, active = true)
            }
            VideoState.GRAB_IDCARD_NUMBER -> {
                emmiReporter.send(LogEvent.DR_STEP_DOCUMENTNUMBER)
                setDrawableState(viewBinding.imageFrontCheck, viewBinding.textFront, checked = true, active = true)
                setDrawableState(viewBinding.imageBackCheck, viewBinding.textBack, checked = true, active = true)
                setDrawableState(viewBinding.imageProfileCheck, viewBinding.textProfile, checked = true, active = true)
            }
            else -> {}
        }
    }

    private fun setDrawableState(imageView: ImageView, textView: TextView, checked: Boolean, active: Boolean) {
        if (checked) {
            imageView.visibility = View.VISIBLE
            imageView.animate().alpha(1f)
        } else {
            imageView.visibility = View.GONE
        }

        if (active) {
            setTextAppearance(textView, R.style.PITextAppearance_Default)
        } else {
            setTextAppearance(textView, R.style.PITextAppearance_Disabled)
        }
    }

    private fun setTextAppearance(textView: TextView, appearance: Int) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            textView.setTextAppearance(activity, appearance)
        } else {
            textView.setTextAppearance(appearance)
        }
    }
}