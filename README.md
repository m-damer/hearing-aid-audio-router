# موجّه الصوت / Hearing Aid Audio Router

Android app (Kotlin, no UI libraries) that routes audio **by type** using Shizuku:

| Sound | Output |
|---|---|
| Calls, voice messages | Hearing aid (switchable to phone) |
| Media: music, video, streaming | Hearing aid (switchable to phone) |
| Notifications, alarms, reminders, ringtone, system sounds | Always phone speaker |

It calls the hidden `IAudioService.setPreferredDevicesForStrategy` through Shizuku (the same mechanism
Android's own hearing-device routing setting uses), and re-applies the rules whenever a Bluetooth device connects.

## Build
Push to `main` → GitHub Actions builds `app-debug.apk` (Actions tab → latest run → Artifacts → `audio-router-apk`).
A fixed debug keystore is committed so new builds install over old ones.

## First use
1. Install Shizuku, start it via Wireless debugging (on Xiaomi also enable "USB debugging (Security settings)").
2. Open the app → grant the Shizuku permission → toggles apply immediately.
3. Add the "الوسائط" / "المكالمات" tiles to Quick Settings for one-tap switching.
4. After each phone reboot, follow the in-app guide to restart Shizuku.

If something fails, use "تشخيص ونسخ التقرير" and send the report.
