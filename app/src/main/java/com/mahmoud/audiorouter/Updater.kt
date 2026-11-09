package com.mahmoud.audiorouter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from the GitHub Releases of this (private) repository.
 * A read-only fine-grained token, entered once by the user, authorises the requests.
 */
object Updater {
    private const val REPO = "m-damer/hearing-aid-audio-router"

    class ReleaseInfo(val tag: String, val notes: String, val apkApiUrl: String, val size: Long)

    private fun open(url: String, token: String?, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "hearing-aid-audio-router")
        if (!token.isNullOrBlank()) {
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        return c
    }

    fun fetchLatest(token: String): ReleaseInfo {
        val c = open("https://api.github.com/repos/$REPO/releases/latest", token, "application/vnd.github+json")
        try {
            when (val code = c.responseCode) {
                200 -> {}
                401 -> throw IllegalStateException("الرمز غير صالح أو منتهي")
                403 -> throw IllegalStateException("GitHub رفض الطلب (403). تحقق من صلاحيات الرمز")
                404 -> throw IllegalStateException("لا يوجد إصدار منشور، أو أن الرمز لا يملك صلاحية قراءة المستودع")
                else -> throw IllegalStateException("خطأ من GitHub: $code")
            }
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return ReleaseInfo(json.getString("tag_name"), json.optString("body"), a.getString("url"), a.optLong("size"))
                }
            }
            throw IllegalStateException("الإصدار لا يحتوي ملف APK بعد. انتظر دقيقتين ثم أعد المحاولة")
        } finally {
            c.disconnect()
        }
    }

    fun isNewer(remoteTag: String, local: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remoteTag)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun download(ctx: Context, token: String, info: ReleaseInfo, onProgress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "update.apk")
        if (out.exists()) out.delete()

        var c = open(info.apkApiUrl, token, "application/octet-stream")
        var code = c.responseCode
        if (code in 301..308) {
            // GitHub redirects to its storage server; the token must not be sent there.
            val location = c.getHeaderField("Location")
            c.disconnect()
            c = open(location, null, "application/octet-stream")
            code = c.responseCode
        }
        if (code != 200) {
            c.disconnect()
            throw IllegalStateException("فشل التنزيل ($code)")
        }
        val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
        c.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var sum = 0L
                var last = -1
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    o.write(buf, 0, read)
                    sum += read
                    val p = if (total > 0) (sum * 100 / total).toInt() else 0
                    if (p != last) {
                        last = p
                        onProgress(p)
                    }
                }
            }
        }
        c.disconnect()
        if (out.length() < 100_000L) throw IllegalStateException("الملف المُنزَّل غير مكتمل")
        return out
    }

    /** Hands the APK to Android's installer; the system asks the user to confirm the update. */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val sessionId = installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        )
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                ctx, sessionId, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            session.commit(pending.intentSender)
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(
                ctx, "فشل التثبيت: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
