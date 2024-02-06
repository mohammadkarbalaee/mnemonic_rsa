package de.post.ident.internal_core.util.ui

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

fun showKeyboard(activity: Activity, show: Boolean, activeView: View? = null) {
    val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    if (show && activeView != null) {
        activeView.requestFocus()
        imm.showSoftInput(activeView, InputMethodManager.SHOW_IMPLICIT)
    } else {
        imm.hideSoftInputFromWindow(activity.window.decorView.rootView.windowToken, 0)
    }
}
