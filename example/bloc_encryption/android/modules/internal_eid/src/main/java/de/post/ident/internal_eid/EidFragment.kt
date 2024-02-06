package de.post.ident.internal_eid

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.post.ident.internal_core.util.log
import de.post.ident.internal_eid.databinding.*
import kotlinx.coroutines.CoroutineScope

class EidFragment : Fragment(), CoroutineScope {

    override val coroutineContext = lifecycleScope.coroutineContext
    private lateinit var viewBinding: PiFragmentEidBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentEidBinding.inflate(inflater, container, false)
        val eidManager = EidManager.instance()

        eidManager.uiState.observe(viewLifecycleOwner) { show(it) }
        eidManager.uiState.value?.let { show(it) }

        return viewBinding.root
    }

    private fun show(newScreen: EidScreen) {
        log("Show screen: $newScreen")
        viewBinding.root.removeAllViews()
        viewBinding.root.addView(newScreen.inflate(layoutInflater, viewBinding.root, this))
    }
}
