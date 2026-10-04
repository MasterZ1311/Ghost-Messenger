package org.ghostmessenger

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GhostMessengerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
    }
}
