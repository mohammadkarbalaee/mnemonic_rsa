package de.post.ident.internal_core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import androidx.core.content.ContextCompat

object UiUtil {

    fun getBitmapFromVectorDrawable(context: Context, vectorDrawableResId: Int) : Bitmap? {
        ContextCompat.getDrawable(context, vectorDrawableResId)?.let { vectorDrawable ->
            val bitmap = Bitmap.createBitmap(
                vectorDrawable.intrinsicWidth,
                vectorDrawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight())
            vectorDrawable.draw(canvas)

            return bitmap
        } ?: run {
            return null
        }
    }

    fun dpToPx(context: Context, dp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()).toInt()
}
