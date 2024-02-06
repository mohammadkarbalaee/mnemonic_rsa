package de.post.ident.internal_eid.util

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.textfield.TextInputEditText

/**
 * custom implementation of TextInputEditText that prevents the soft keyboard from popping up
 */
class NoImeTextInputEditText : TextInputEditText {
    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr)

    override fun onCheckIsTextEditor() = false
}