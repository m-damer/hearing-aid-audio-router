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

    /** Volume (0-100 %) set when the hearing aid connects, so it never starts too loud. */
    fun volumeEnabled(c: Context) = sp(c).getBoolean("vol_enabled", true)
    fun setVolumeEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("vol_enabled", v).apply()
    fun startVolume(c: Context) = sp(c).getInt("vol_start", 50)
    fun setStartVolume(c: Context, v: Int) = sp(c).edit().putInt("vol_start", v.coerceIn(0, 100)).apply()

    /** Read-only GitHub token used to fetch updates from the private repository. */
    fun token(c: Context): String = sp(c).getString("gh_token", "") ?: ""
    fun setToken(c: Context, v: String) = sp(c).edit().putString("gh_token", v.trim()).apply()

    /** The last apply result, so the diagnostics report can include it. */
    fun lastLog(c: Context): String = sp(c).getString("last_log", "") ?: ""
    fun setLastLog(c: Context, v: String) = sp(c).edit().putString("last_log", v).apply()
}
