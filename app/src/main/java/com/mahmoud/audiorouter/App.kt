package com.mahmoud.audiorouter

import android.app.Application
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // The audio routing calls live in hidden framework classes; this lifts the reflection restriction.
        try {
            HiddenApiBypass.addHiddenApiExemptions("L")
        } catch (_: Throwable) {
        }
    }
}
