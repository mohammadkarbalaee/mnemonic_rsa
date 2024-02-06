package de.post.ident.internal_core.util

import android.content.Context
import android.content.DialogInterface
import android.text.Spanned
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setPadding
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.post.ident.internal_core.R
import de.post.ident.internal_core.rest.getUserMessage

fun showAlertDialog(context: Context?, error: Throwable, onFinish: () -> Unit = {}): AlertDialog? =
        showAlertDialog(context, error.getUserMessage(), onFinish)

fun showAlertDialog(context: Context?, msg: String, onFinish: () -> Unit = {}): AlertDialog? {
    return showAlertDialog(context, null, msg, 0, true, onFinish)
}

fun showHtmlAlertDialog(context: Context?, msg: Spanned, onFinish: () -> Unit = {}): AlertDialog? {
    return showHtmlAlertDialog(context, null, msg, 0, true, onFinish)
}
fun showAlertDialog(context: Context?, title: String, msg: String, onFinish: () -> Unit = {}): AlertDialog? {
    return showAlertDialog(context, title, msg, 0, true, onFinish)
}

fun showAlertDialog(context: Context?, title: String?, msg: String?, imgResId: Int, isCancelable: Boolean? = true, onFinish: () -> Unit = {}): AlertDialog? {
    if (context != null) {
        val okListener = DialogInterface.OnClickListener { dialog, _ -> dialog?.dismiss() }
        val dialogBuilder = MaterialAlertDialogBuilder(context, R.style.PIDialogTheme)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(LocalizedStrings.getString(R.string.default_btn_ok), okListener)
                .setOnDismissListener { onFinish() }
                .setCancelable(isCancelable == true)
        if (imgResId != 0) {
            dialogBuilder.setView(ImageView(context).apply {
                setImageResource(imgResId)
                setPadding(context.resources.getDimensionPixelSize(R.dimen.pi_spacing_32dp))
            })
        }
        val dialog = dialogBuilder.create()
        dialog.show()
        return dialog
    }
    return null
}

fun showHtmlAlertDialog(context: Context?, title: String?, msg: Spanned, imgResId: Int, isCancelable: Boolean? = true, onFinish: () -> Unit = {}): AlertDialog? {
    if (context != null) {
        val okListener = DialogInterface.OnClickListener { dialog, _ -> dialog?.dismiss() }
        val dialogBuilder = MaterialAlertDialogBuilder(context, R.style.PIDialogTheme)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(LocalizedStrings.getString(R.string.default_btn_ok), okListener)
            .setOnDismissListener { onFinish() }
            .setCancelable(isCancelable == true)
        if (imgResId != 0) {
            dialogBuilder.setView(ImageView(context).apply {
                setImageResource(imgResId)
                setPadding(context.resources.getDimensionPixelSize(R.dimen.pi_spacing_32dp))
            })
        }
        val dialog = dialogBuilder.create()
        dialog.show()
        return dialog
    }
    return null
}

fun showChoiceDialog(context: Context?, title: String? = null, msg: String? = null, positiveButton: String?, negativeButton: String? = null,
                     cancelable: Boolean = true, onPositive: () -> Unit = {}, onNegative: () -> Unit = {}, onDismiss: () -> Unit = {}, view: ViewBinding? = null) {
    if (context != null) {
        val positiveListener = DialogInterface.OnClickListener { dialog, _ ->
            dialog.dismiss()
            onPositive()
        }
        val negativeListener = DialogInterface.OnClickListener { dialog, _ ->
            dialog.dismiss()
            onNegative()
        }
        val dBuilder = MaterialAlertDialogBuilder(context, R.style.PIDialogTheme).setCancelable(cancelable)

        view?.let { dBuilder.setView(view.root) }
        title?.let { dBuilder.setTitle(title) }
        msg?.let { dBuilder.setMessage(msg) }
        positiveButton?.let { dBuilder.setPositiveButton(positiveButton, positiveListener) }
        negativeButton?.let { dBuilder.setNegativeButton(negativeButton, negativeListener) }
        dBuilder.setOnDismissListener { onDismiss() }

        val dialog = dBuilder.create()
        if (dialog.isShowing.not()) {
            dialog.show()
        }
    }
}