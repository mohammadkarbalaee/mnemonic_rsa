package de.post.ident.internal_core.util.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.core.view.marginStart
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.post.ident.internal_core.R
import kotlin.math.max

class MaterialButtonLoadingController(private val ctx: Context, private val btn: MaterialButton) {
    private val initialText = btn.text
    private val initialIcon = btn.icon
    private val spinnerColor = ContextCompat.getColor(ctx, R.color.pi_primary_brand_color)

    fun loadingAnimation(start: Boolean, btnEnabled: Boolean = true) = if (start) {
        btn.icon = null
        btn.startCircularProgress(ctx, spinnerColor, "")
        btn.isEnabled = !btnEnabled
    } else {
        btn.text = initialText
        btn.icon = initialIcon
        btn.isEnabled = btnEnabled
    }
}

fun Button.startCircularProgress(
        context: Context,
        spinnerColor: Int,
        buttonText: String
) {
    val progressDrawable = CircularProgressDrawable(context).apply {
        setStyle(CircularProgressDrawable.DEFAULT)
        setColorSchemeColors(spinnerColor)
        val size = (centerRadius + strokeWidth).toInt() * 2
        setBounds(0, 0, size, size)
    }

    val drawableSpan = object : DynamicDrawableSpan() {
        override fun getDrawable(): Drawable = progressDrawable
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            val rect = drawable.bounds
            fm?.apply {
                val fontMetrics = paint.fontMetricsInt
                val lineHeight = fontMetrics.bottom - fontMetrics.top
                val drHeight = max(lineHeight, rect.bottom - rect.top)
                val centerY = fontMetrics.top + lineHeight / 2

                ascent = centerY - drHeight / 2
                descent = centerY + drHeight / 2
                top = ascent
                bottom = descent
            }
            return rect.width() + marginStart
        }

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            canvas.save()
            val fontMetrics = paint.fontMetricsInt
            val lineHeight = fontMetrics.bottom - fontMetrics.top
            val centerY = y + fontMetrics.bottom - lineHeight / 2
            val transY = centerY - drawable.bounds.height() / 2
            canvas.translate(x + marginStart, transY.toFloat())
            drawable.draw(canvas)
            canvas.restore()
        }
    }

    val spannableString = SpannableString("$buttonText ").apply {
        setSpan(drawableSpan, length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    progressDrawable.apply {
        callback = object : Drawable.Callback {
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {}
            override fun invalidateDrawable(who: Drawable) = invalidate()
            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
        }
        start()
    }

    text = spannableString
}

fun FloatingActionButton.startCircularProgress(context: Context, spinnerColor: Int) = setImageDrawable(
        CircularProgressDrawable(context).apply {
            setStyle(CircularProgressDrawable.DEFAULT)
            setColorSchemeColors(spinnerColor)
            val size = (centerRadius + strokeWidth).toInt() * 2
            setBounds(0, 0, size, size)
            start()
        }
)