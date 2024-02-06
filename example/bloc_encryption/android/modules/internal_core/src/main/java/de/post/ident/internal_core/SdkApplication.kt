package de.post.ident.internal_core

import android.app.Application
import android.content.Context

class SdkApplication : Application() {

    companion object {
        lateinit var appContext: Context
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}