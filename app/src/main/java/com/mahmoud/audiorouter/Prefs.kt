package com.mahmoud.audiorouter

import android.content.Context

/** true = hearing aid, false = phone. Everything else (notifications, alarms, ringtone, system sounds) is always phone. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("router", Context.MODE_PRIVATE)

    fun mediaToHearing(c: Context) = sp(c).getBoolean("media_hearing", true)
    fun callToHearing(c: Context) = sp(c).getBoolean("call_hearing", true)

    fun setMediaToHearing(c: Context, v: Boolean) = sp(c).edit().putBoolean("media_hearing", v).apply()
    fun setCallToHearing(c: Context, v: Boolean) = sp(c).edit().putBoolean("call_hearing", v).apply()
}
