package com.mahmoud.audiorouter

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Step-by-step restart guide: Shizuku must be started again after every phone reboot. */
class GuideActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFFF7F8FC.toInt()) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val h = Ui.dp(this@GuideActivity, 16)
            setPadding(h, Ui.dp(this@GuideActivity, 44), h, Ui.dp(this@GuideActivity, 28))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(Ui.text(this, "تشغيل Shizuku بعد إعادة تشغيل الجوال", 24f, true))
        root.addView(
            Ui.text(
                this,
                "بعد كل إعادة تشغيل يتوقف Shizuku، فيتوقف توجيه الصوت حتى تشغّله من جديد. الخطوات تستغرق دقيقة.",
                14f, color = Ui.MUTED
            )
        )

        status = Ui.text(this, "", 16f, true)
        root.addView(Ui.card(this, status, Ui.button(this, "تحقق من الحالة الآن") { refresh() }))

        step(
            root, "1", "اتصل بشبكة Wi-Fi وأوقف الـ VPN",
            "التصحيح اللاسلكي يحتاج Wi-Fi، وقد يتعارض مع الـ VPN.",
            "فتح إعدادات Wi-Fi" to { open(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        )
        step(
            root, "2", "فعّل التصحيح اللاسلكي",
            "من خيارات المطوّر: فعّل «Wireless debugging». وتأكد أن «USB debugging (Security settings)» ما زال مفعّلاً (شرط شاومي).",
            "فتح التصحيح اللاسلكي" to {
                open(Intent("android.settings.ADB_WIFI_SETTINGS"), fallback = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            },
            "فتح خيارات المطوّر" to { open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        )
        step(
            root, "3", "شغّل Shizuku",
            "في Shizuku اضغط «Start via Wireless debugging» ثم «Start». إن طلب الإقران (Pairing) فاضغط «Pairing»، وأدخل الرمز المكوّن من 6 أرقام من إشعار التصحيح اللاسلكي.",
            "فتح Shizuku" to { launchShizuku() },
            "شرح Shizuku الرسمي" to { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/"))) }
        )
        step(
            root, "4", "عد إلى هذا التطبيق",
            "اضغط «تحقق من الحالة»، ثم «تطبيق الآن» في الشاشة الرئيسية. إن ظهرت الحالة «جاهز» فالتوجيه يعمل.",
            "فتح الشاشة الرئيسية" to {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
        )
        root.addView(
            Ui.card(
                this,
                Ui.text(this, "إن لم يعمل", 16f, true),
                Ui.text(
                    this,
                    "• أعد تشغيل التصحيح اللاسلكي (أطفئه ثم شغّله) وأعد الخطوة 3.\n" +
                        "• افتح خيارات المطوّر وتأكد أن «USB debugging (Security settings)» مفعّل.\n" +
                        "• من الشاشة الرئيسية اضغط «تشخيص ونسخ التقرير» وأرسله لي.",
                    14f, color = Ui.MUTED
                )
            )
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val ok = Router.permissionGranted()
        val running = Router.binderAlive()
        status.text = when {
            ok -> "الحالة: Shizuku جاهز ✓"
            running -> "الحالة: Shizuku يعمل، امنح الصلاحية من الشاشة الرئيسية"
            else -> "الحالة: Shizuku متوقف"
        }
        status.setTextColor(if (ok) Ui.GREEN else Ui.RED)
    }

    private fun step(root: LinearLayout, num: String, title: String, body: String, vararg actions: Pair<String, () -> Unit>) {
        val views = ArrayList<View>()
        views.add(Ui.text(this, "$num. $title", 17f, true))
        views.add(Ui.text(this, body, 14f, color = Ui.MUTED))
        views.add(Ui.gap(this))
        actions.forEach { (label, fn) -> views.add(Ui.button(this, label, fn)) }
        root.addView(Ui.card(this, *views.toTypedArray()))
    }

    private fun open(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            if (fallback != null) open(fallback)
            else Toast.makeText(this, "تعذّر فتح هذه الشاشة، افتحها يدوياً", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            if (fallback != null) open(fallback)
        }
    }

    private fun launchShizuku() {
        val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (i != null) startActivity(i)
        else open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases")))
    }
}
