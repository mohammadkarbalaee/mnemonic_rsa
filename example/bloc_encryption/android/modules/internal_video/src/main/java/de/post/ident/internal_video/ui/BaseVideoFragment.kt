package de.post.ident.internal_video.ui

import androidx.fragment.app.Fragment
import de.post.ident.internal_video.VideoManager
import de.post.ident.internal_video.util.EmmiVideoReporter

abstract class BaseVideoFragment : Fragment() {

    protected val videoManager by lazy { VideoManager.instance() }
    protected val emmiReporter by lazy { EmmiVideoReporter(videoManager) }
}