package com.mahmoud.audiorouter

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var hearingText: TextView
    private lateinit var routeSwitch: Switch
    private lateinit var routeSub: TextView
    private lateinit var logText: TextView

    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refresh() } }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refresh() } }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread {
            refresh()
            applyAsync()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFFF7F8FC.toInt()) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val h = Ui.dp(this@MainActivity, 16)
            setPadding(h, Ui.dp(this@MainActivity, 44), h, Ui.dp(this@MainActivity, 28))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(Ui.text(this, "موجّه الصوت", 28f, true))
        root.addView(
            Ui.text(
                this,
                "المكالمات والوسائط للسماعة الطبية، وكل ما عداها يخرج من الجوال.",
                14f, color = Ui.MUTED
            )
        )

        // --- status ---
        statusText = Ui.text(this, "", 16f, true)
        hearingText = Ui.text(this, "", 14f, color = Ui.MUTED)
        root.addView(
            Ui.card(
                this,
                statusText,
                hearingText,
                Ui.gap(this),
                Ui.button(this, "منح صلاحية Shizuku") { requestShizukuPermission() },
                Ui.button(this, "دليل التشغيل بعد إعادة تشغيل الجوال") {
                    startActivity(Intent(this, GuideActivity::class.java))
                }
            )
        )

        // --- routes ---
        routeSwitch = Switch(this).apply {
            text = "تحويل الصوت إلى السماعة الطبية"
            textSize = 17f
            isChecked = Prefs.toHearing(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setToHearing(this@MainActivity, checked)
                updateLabels()
                applyAsync()
            }
        }
        routeSub = Ui.text(this, "", 13f, color = Ui.MUTED)

        root.addView(Ui.card(this, routeSwitch, routeSub))

        root.addView(
            Ui.card(
                this,
                Ui.text(this, "تخرج دائماً من الجوال", 16f, true),
                Ui.text(
                    this,
                    "الإشعارات والرسائل، المنبهات والتذكيرات، نغمة رنين المكالمات الواردة، وأصوات النظام.",
                    14f, color = Ui.MUTED
                )
            )
        )

        // --- actions + log ---
        logText = Ui.text(this, "لم يُطبَّق شيء بعد.", 13f, color = Ui.MUTED).apply {
            setTextIsSelectable(true)
        }
        root.addView(
            Ui.card(
                this,
                Ui.button(this, "تطبيق الآن") { applyAsync() },
                Ui.button(this, "تشخيص ونسخ التقرير") { diagnose() },
                Ui.button(this, "إعادة الوضع الافتراضي لكل الأصوات") { resetAll() },
                Ui.gap(this),
                logText
            )
        )

        updateLabels()

        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permListener)

        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT), 1
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    private fun updateLabels() {
        routeSub.text = if (Prefs.toHearing(this))
            "مفعّل: المكالمات والرسائل الصوتية والوسائط (موسيقى، فيديو، بث) تخرج من السماعة الطبية."
        else
            "متوقف: كل الأصوات تخرج من الجوال."
    }

    private fun shizukuInstalled(): Boolean = try {
        packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun refresh() {
        val running = Router.binderAlive()
        val granted = Router.permissionGranted()
        when {
            !shizukuInstalled() -> {
                statusText.text = "Shizuku غير مثبّت"
                statusText.setTextColor(Ui.RED)
            }
            !running -> {
                statusText.text = "Shizuku متوقف، اتبع دليل التشغيل"
                statusText.setTextColor(Ui.RED)
            }
            !granted -> {
                statusText.text = "Shizuku يعمل، لكن لم تُمنح الصلاحية بعد"
                statusText.setTextColor(Ui.RED)
            }
            else -> {
                statusText.text = "Shizuku جاهز ✓"
                statusText.setTextColor(Ui.GREEN)
            }
        }
        val n = try {
            Router.hearingDevices(this).size
        } catch (_: Throwable) {
            0
        }
        hearingText.text = if (n > 0) "السماعة الطبية ظاهرة للنظام كمخرج صوت ($n)" else "لا تظهر سماعة طبية كمخرج صوت الآن"
    }

    private fun requestShizukuPermission() {
        try {
            if (!Router.binderAlive()) {
                Toast.makeText(this, "شغّل Shizuku أولاً (الدليل)", Toast.LENGTH_SHORT).show()
                return
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(this, "فعّل الإذن من داخل تطبيق Shizuku ← التطبيقات المصرّح لها", Toast.LENGTH_LONG).show()
                return
            }
            Shizuku.requestPermission(100)
        } catch (t: Throwable) {
            Toast.makeText(this, "تعذّر: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun show(text: String) = runOnUiThread { logText.text = text }

    private fun applyAsync() {
        val app = applicationContext
        Thread {
            val r = Router.applyAll(app)
            show(r.toString())
            runOnUiThread { refresh() }
        }.start()
    }

    private fun resetAll() {
        Thread { show(Router.clearAll().toString()) }.start()
    }

    private fun diagnose() {
        Thread {
            val d = Router.diagnostics(applicationContext)
            show(d)
            runOnUiThread {
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("report", d))
                Toast.makeText(this, "نُسخ التقرير، الصقه في المحادثة", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
