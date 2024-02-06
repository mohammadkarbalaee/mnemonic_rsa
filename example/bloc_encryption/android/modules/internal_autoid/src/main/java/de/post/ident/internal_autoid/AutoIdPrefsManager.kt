package de.post.ident.internal_autoid

import android.content.Context
import android.content.SharedPreferences

class AutoIdPrefsManager(context: Context) {
    private val PREFS_NAME = "autoIdPrefs"

    private val prefs: SharedPreferences

    init {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setTermsAccepted(caseId: String) = prefs.edit().putBoolean(caseId, true).apply()
    fun isTermsAccepted(caseId: String) = prefs.getBoolean(caseId, false)
}
