package com.mahmoud.audiorouter

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Sets a preferred output device per audio "product strategy" (media, phone call, ringtone/alarm, notification...)
 * by calling the hidden IAudioService through Shizuku. This is the same mechanism Android's own
 * "hearing device audio routing" setting uses.
 */
object Router {
    private const val ROLE_OUTPUT = 2
    private const val TYPE_EARPIECE = 1
    private const val TYPE_SPEAKER = 2

    enum class Target { HEARING, EARPIECE, SPEAKER }

    class Report {
        private val sb = StringBuilder()
        var ok = true
        fun line(s: String) {
            sb.appendLine(s)
        }

        fun fail(s: String) {
            ok = false
            sb.appendLine("✗ $s")
        }

        override fun toString() = sb.toString().trim()
    }

    // ---------- Shizuku state ----------

    fun binderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun permissionGranted(): Boolean = try {
        binderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** Used from receivers/tiles, where the process may have just started and Shizuku has not connected yet. */
    fun awaitBinder(timeoutMs: Long): Boolean {
        if (binderAlive()) return true
        val latch = CountDownLatch(1)
        val listener = Shizuku.OnBinderReceivedListener { latch.countDown() }
        Shizuku.addBinderReceivedListener(listener)
        try {
            if (binderAlive()) return true
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            Shizuku.removeBinderReceivedListener(listener)
        }
        return binderAlive()
    }

    // ---------- Devices ----------

    fun hearingDevices(ctx: Context): List<AudioDeviceInfo> {
        val am = ctx.getSystemService(AudioManager::class.java)
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
            it.type == AudioDeviceInfo.TYPE_HEARING_AID || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }

    private fun attrs(type: Int, address: String): Any {
        val cls = Class.forName("android.media.AudioDeviceAttributes")
        val ctor = cls.getConstructor(
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        return ctor.newInstance(ROLE_OUTPUT, type, address)
    }

    // ---------- Hidden API plumbing ----------

    private fun audioService(): Any {
        val sm = Class.forName("android.os.ServiceManager")
        val binder = sm.getMethod("getService", String::class.java).invoke(null, "audio") as IBinder
        val wrapped = ShizukuBinderWrapper(binder)
        val stub = Class.forName("android.media.IAudioService\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, wrapped)!!
    }

    private val strategyCls: Class<*> by lazy { Class.forName("android.media.audiopolicy.AudioProductStrategy") }

    private fun allStrategies(): List<Any> {
        val list = strategyCls.getMethod("getAudioProductStrategies").invoke(null) as List<*>
        return list.filterNotNull()
    }

    private fun strategyId(s: Any): Int = strategyCls.getMethod("getId").invoke(s) as Int

    private fun strategyName(s: Any): String = try {
        strategyCls.getMethod("getName").invoke(s) as String
    } catch (_: Throwable) {
        "?"
    }

    private fun strategyFor(usage: Int): Any? {
        val aa = AudioAttributes.Builder().setUsage(usage).build()
        val m = strategyCls.getMethod("supportsAudioAttributes", AudioAttributes::class.java)
        return allStrategies().firstOrNull { m.invoke(it, aa) as Boolean }
    }

    private fun unwrap(e: Throwable): Throwable = if (e is InvocationTargetException) (e.cause ?: e) else e

    private fun setPreferred(svc: Any, id: Int, devices: List<Any>): Int {
        val iface = Class.forName("android.media.IAudioService")
        val intT = Int::class.javaPrimitiveType
        return try {
            iface.getMethod("setPreferredDevicesForStrategy", intT, List::class.java)
                .invoke(svc, id, devices) as Int
        } catch (_: NoSuchMethodException) {
            val attrCls = Class.forName("android.media.AudioDeviceAttributes")
            iface.getMethod("setPreferredDeviceForStrategy", intT, attrCls)
                .invoke(svc, id, devices.first()) as Int
        }
    }

    private fun clearPreferred(svc: Any, id: Int): Int {
        val iface = Class.forName("android.media.IAudioService")
        val intT = Int::class.javaPrimitiveType
        return try {
            iface.getMethod("removePreferredDevicesForStrategy", intT).invoke(svc, id) as Int
        } catch (_: NoSuchMethodException) {
            iface.getMethod("removePreferredDeviceForStrategy", intT).invoke(svc, id) as Int
        }
    }

    // ---------- Rules ----------

    /** primary = the sounds the switch controls (media, calls). They win if a strategy is shared with other sounds. */
    private class Rule(val label: String, val usage: Int, val target: Target, val primary: Boolean)

    private fun rules(ctx: Context): List<Rule> {
        val hearing = Prefs.toHearing(ctx)
        val media = if (hearing) Target.HEARING else Target.SPEAKER
        val call = if (hearing) Target.HEARING else Target.EARPIECE
        return listOf(
            Rule("الوسائط", AudioAttributes.USAGE_MEDIA, media, true),
            Rule("المكالمات", AudioAttributes.USAGE_VOICE_COMMUNICATION, call, true),
            Rule("نغمة الرنين", AudioAttributes.USAGE_NOTIFICATION_RINGTONE, Target.SPEAKER, false),
            Rule("المنبهات", AudioAttributes.USAGE_ALARM, Target.SPEAKER, false),
            Rule("الإشعارات", AudioAttributes.USAGE_NOTIFICATION, Target.SPEAKER, false),
            Rule("أصوات النظام", AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, Target.SPEAKER, false)
        )
    }

    /** Applies the saved rules. Safe to call repeatedly. */
    fun applyAll(ctx: Context): Report {
        val result = doApply(ctx)
        Prefs.setLastLog(ctx, result.toString())
        return result
    }

    private fun doApply(ctx: Context): Report {
        val r = Report()
        if (!binderAlive()) {
            r.fail("Shizuku غير شغّال")
            return r
        }
        if (!permissionGranted()) {
            r.fail("لم تُمنح صلاحية Shizuku لهذا التطبيق")
            return r
        }
        try {
            val svc = audioService()
            val hearing = hearingDevices(ctx)
            r.line("السماعات الطبية الظاهرة للنظام: ${hearing.size}")
            val hearingAttrs = hearing.map { attrs(it.type, it.address) }
            val speaker = attrs(TYPE_SPEAKER, "")
            val earpiece = attrs(TYPE_EARPIECE, "")

            // One strategy can serve several usages (ringtone + alarm, or even media + system sounds).
            // The sounds the switch controls (media, calls) win, so they can never be forced to the speaker
            // by an unrelated sound sharing their strategy.
            val perStrategy = LinkedHashMap<Int, Triple<String, Target, Boolean>>()
            for (rule in rules(ctx)) {
                val s = strategyFor(rule.usage)
                if (s == null) {
                    r.fail("لم أجد مساراً لـ ${rule.label}")
                    continue
                }
                val id = strategyId(s)
                r.line("  ${rule.label} ← مسار ${strategyName(s)} ($id)")
                val old = perStrategy[id]
                if (old == null || (rule.primary && !old.third)) {
                    if (old != null) r.line("  ملاحظة: «${old.first}» تشترك في المسار نفسه فتتبع «${rule.label}»")
                    perStrategy[id] = Triple(rule.label, rule.target, rule.primary)
                } else if (old.third && !rule.primary) {
                    r.line("  ملاحظة: «${rule.label}» تشترك في المسار نفسه فتتبع «${old.first}»")
                }
            }

            for ((id, entry) in perStrategy) {
                val (label, target, _) = entry
                try {
                    val status = when {
                        target == Target.HEARING && hearingAttrs.isEmpty() -> {
                            clearPreferred(svc, id)
                            r.line("• $label ← لا توجد سماعة ظاهرة، تُرك التوجيه الافتراضي")
                            continue
                        }
                        target == Target.HEARING -> setPreferred(svc, id, hearingAttrs)
                        target == Target.EARPIECE -> setPreferred(svc, id, listOf(earpiece))
                        else -> setPreferred(svc, id, listOf(speaker))
                    }
                    val where = when (target) {
                        Target.HEARING -> "السماعة الطبية"
                        Target.EARPIECE -> "سماعة الأذن"
                        Target.SPEAKER -> "مكبر الجوال"
                    }
                    if (status == 0) r.line("✓ $label ← $where")
                    else r.fail("$label ← $where (رمز الحالة $status)")
                } catch (t: Throwable) {
                    r.fail("$label: ${unwrap(t).javaClass.simpleName}: ${unwrap(t).message}")
                }
            }
        } catch (t: Throwable) {
            val u = unwrap(t)
            r.fail("${u.javaClass.simpleName}: ${u.message}")
            r.line(listPreferredMethods())
        }
        return r
    }

    /** Removes every routing preference this app set, restoring Android's defaults. */
    fun clearAll(): Report {
        val r = Report()
        if (!permissionGranted()) {
            r.fail("Shizuku غير جاهز")
            return r
        }
        try {
            val svc = audioService()
            for (s in allStrategies()) {
                try {
                    clearPreferred(svc, strategyId(s))
                } catch (t: Throwable) {
                    r.fail("${strategyName(s)}: ${unwrap(t).message}")
                }
            }
            if (r.ok) r.line("✓ أُعيد التوجيه الافتراضي لكل أنواع الصوت")
        } catch (t: Throwable) {
            r.fail("${unwrap(t).javaClass.simpleName}: ${unwrap(t).message}")
        }
        return r
    }

    private fun listPreferredMethods(): String = try {
        val iface = Class.forName("android.media.IAudioService")
        "طرق متاحة في النظام:\n" + iface.methods
            .filter { it.name.contains("Preferred") }
            .joinToString("\n") { m -> "  ${m.name}(${m.parameterTypes.joinToString { it.simpleName }})" }
    } catch (t: Throwable) {
        "تعذّر سرد الطرق: ${t.message}"
    }

    /** A report the user can copy and send back for debugging. */
    fun diagnostics(ctx: Context): String {
        val sb = StringBuilder()
        sb.appendLine("== Shizuku ==")
        sb.appendLine("running=${binderAlive()} granted=${permissionGranted()}")
        try {
            if (binderAlive()) sb.appendLine("version=${Shizuku.getVersion()} uid=${Shizuku.getUid()}")
        } catch (t: Throwable) {
            sb.appendLine("shizuku info error: ${t.message}")
        }
        sb.appendLine("== أجهزة الإخراج ==")
        try {
            val am = ctx.getSystemService(AudioManager::class.java)
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach {
                sb.appendLine("type=${it.type} name=${it.productName} addr=${it.address}")
            }
        } catch (t: Throwable) {
            sb.appendLine("error: ${t.message}")
        }
        sb.appendLine("== Strategies ==")
        try {
            allStrategies().forEach { sb.appendLine("id=${strategyId(it)} name=${strategyName(it)}") }
        } catch (t: Throwable) {
            sb.appendLine("error: ${unwrap(t).javaClass.simpleName}: ${unwrap(t).message}")
        }
        sb.appendLine("== الإعداد الحالي: المفتاح=${if (Prefs.toHearing(ctx)) "السماعة" else "الجوال"} ==")
        sb.appendLine("== آخر تطبيق للقواعد ==")
        sb.appendLine(Prefs.lastLog(ctx).ifEmpty { "(لم يُطبَّق بعد)" })
        sb.appendLine("== الجهاز المفضّل المثبّت حالياً لكل مسار ==")
        try {
            val svc = audioService()
            val iface = Class.forName("android.media.IAudioService")
            val get = iface.getMethod("getPreferredDevicesForStrategy", Int::class.javaPrimitiveType)
            for (s in allStrategies()) {
                val id = strategyId(s)
                val devs = get.invoke(svc, id) as List<*>
                sb.appendLine("${strategyName(s)}($id): " + if (devs.isEmpty()) "لا شيء" else devs.joinToString { it.toString() })
            }
        } catch (t: Throwable) {
            sb.appendLine("error: ${unwrap(t).javaClass.simpleName}: ${unwrap(t).message}")
        }
        sb.appendLine("== المخرج الفعلي الذي يختاره النظام لكل نوع صوت ==")
        try {
            val am = ctx.getSystemService(AudioManager::class.java)
            listOf(
                "وسائط" to AudioAttributes.USAGE_MEDIA,
                "مكالمات" to AudioAttributes.USAGE_VOICE_COMMUNICATION,
                "رنين" to AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
                "منبه" to AudioAttributes.USAGE_ALARM,
                "إشعار" to AudioAttributes.USAGE_NOTIFICATION
            ).forEach { (name, usage) ->
                val devs = am.getAudioDevicesForAttributes(AudioAttributes.Builder().setUsage(usage).build())
                sb.appendLine("$name: " + if (devs.isEmpty()) "لا شيء" else devs.joinToString { "type=${it.type}" })
            }
        } catch (t: Throwable) {
            sb.appendLine("error: ${t.javaClass.simpleName}: ${t.message}")
        }
        sb.appendLine("== IAudioService ==")
        sb.appendLine(listPreferredMethods())
        sb.appendLine("== Android ==")
        sb.appendLine("sdk=${android.os.Build.VERSION.SDK_INT} device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        return sb.toString().trim()
    }
}
