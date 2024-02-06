package de.post.ident.internal_video.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.post.ident.internal_video.R

class IncomingCallFragment : Fragment() {
    companion object {
        fun newInstance() = IncomingCallFragment()
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.pi_fragment_incoming_call, container, false)
    }
}