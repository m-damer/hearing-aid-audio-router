package com.mahmoud.audiorouter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification

/** Re-applies the routing when a Bluetooth device connects, and reminds you to restart Shizuku after boot. */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        // Shizuku never survives a reboot without root, so the realistic outcome here is a reminder.
                        if (Router.awaitBinder(4000)) Router.applyAll(app) else Notifier.remindStartShizuku(app)
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        Thread.sleep(2500) // let the hearing aid register as an audio output first
                        if (Router.awaitBinder(3000)) Router.applyAll(app)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

object Notifier {
    private const val CHANNEL = "reminder"

    fun remindStartShizuku(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "تذكير تشغيل Shizuku", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, GuideActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("شغّل Shizuku لتفعيل توجيه الصوت")
                .setContentText("أُعيد تشغيل الجوال. اضغط لفتح الدليل المصوّر بالخطوات.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(1, n)
        } catch (_: Throwable) {
        }
    }
}
