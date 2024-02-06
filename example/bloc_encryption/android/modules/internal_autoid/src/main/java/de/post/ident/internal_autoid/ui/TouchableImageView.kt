package de.post.ident.internal_autoid.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent

class TouchableImageView : androidx.appcompat.widget.AppCompatImageView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private val ON_TOUCH_TRANSLATION_X = 5
    private val ON_TOUCH_TRANSLATION_Y = 10

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                translationX += ON_TOUCH_TRANSLATION_X
                translationY += ON_TOUCH_TRANSLATION_Y
                return true
            }
            MotionEvent.ACTION_UP -> {
                translationX -= ON_TOUCH_TRANSLATION_X
                translationY -= ON_TOUCH_TRANSLATION_Y
                performClick()
                return true
            }
        }
        return false
    }
}