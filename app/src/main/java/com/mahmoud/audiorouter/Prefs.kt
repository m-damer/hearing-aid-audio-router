package com.mahmoud.audiorouter

import android.content.Context

/**
 * One switch: true = calls + media go to the hearing aid, false = everything comes from the phone.
 * Notifications, alarms, reminders, ringtone and system sounds always come from the phone.
 */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("router", Context.MODE_PRIVATE)

    fun toHearing(c: Context) = sp(c).getBoolean("to_hearing", true)
    fun setToHearing(c: Context, v: Boolean) = sp(c).edit().putBoolean("to_hearing", v).apply()

    /** The last apply result, so the diagnostics report can include it. */
    fun lastLog(c: Context): String = sp(c).getString("last_log", "") ?: ""
    fun setLastLog(c: Context, v: String) = sp(c).edit().putString("last_log", v).apply()
}
