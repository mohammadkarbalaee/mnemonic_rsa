package de.post.ident.internal_video.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.observe
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_video.VideoState
import de.post.ident.internal_video.databinding.PiFragmentGoodbyeBinding


class GoodbyeFragment : BaseVideoFragment() {
    companion object {
        fun newInstance(): GoodbyeFragment = GoodbyeFragment()
    }

    private lateinit var viewBinding: PiFragmentGoodbyeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoManager.currentState.observe(this) {
            onProgressUpdate(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentGoodbyeBinding.inflate(inflater, container, false)

        viewBinding.identStatusTitle.text = LocalizedStrings.getString("identification_successful_title")

        return viewBinding.root
    }

    private fun onProgressUpdate(serverState: VideoState) {
        when (serverState) {
            VideoState.END_CHAT -> {
                emmiReporter.send(LogEvent.VC_STEP_ENDING)
            }
            else -> {}
        }
    }
}