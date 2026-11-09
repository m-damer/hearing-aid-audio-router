package com.mahmoud.audiorouter

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/** Sets the media and call volume to the saved starting level (no Shizuku needed). */
object Volume {
    private val streams = intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_VOICE_CALL)

    fun applyStart(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val pct = Prefs.startVolume(ctx)
        for (stream in streams) {
            try {
                val min = am.getStreamMinVolume(stream)
                val max = am.getStreamMaxVolume(stream)
                val index = (min + (max - min) * pct / 100f).roundToInt()
                am.setStreamVolume(stream, index, 0)
            } catch (_: Throwable) {
            }
        }
    }

    fun currentMusicPercent(ctx: Context): Int {
        val am = ctx.getSystemService(AudioManager::class.java)
        val min = am.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= min) return 50
        return ((am.getStreamVolume(AudioManager.STREAM_MUSIC) - min) * 100f / (max - min)).roundToInt()
    }
}
