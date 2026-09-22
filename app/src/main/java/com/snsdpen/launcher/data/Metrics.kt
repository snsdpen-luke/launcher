package com.snsdpen.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

/** タイルに出す計測値(0..100 の割合と、添える短い語) */
data class Metric(val percent: Int, val note: String = "")

fun readBattery(context: Context): Metric {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return Metric(0)
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return Metric((level * 100 / scale).coerceIn(0, 100), if (charging) "CHG" else "")
}

/** 使用中メモリの割合 */
fun readMemory(context: Context): Metric {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    if (info.totalMem <= 0) return Metric(0)
    val used = info.totalMem - info.availMem
    return Metric((used * 100 / info.totalMem).toInt().coerceIn(0, 100), "${info.availMem / (1024 * 1024 * 1024)}G FREE")
}

/** 内部ストレージの使用割合 */
fun readStorage(): Metric {
    val st = StatFs(Environment.getDataDirectory().path)
    val total = st.totalBytes
    if (total <= 0) return Metric(0)
    val free = st.availableBytes
    return Metric(((total - free) * 100 / total).toInt().coerceIn(0, 100), "${free / (1024L * 1024 * 1024)}G FREE")
}

/** Wi-Fi の強度 0..100(5 段階)。未接続は 0 */
fun readWifi(context: Context): Metric {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager ?: return Metric(0)
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
    val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
    val onWifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
    @Suppress("DEPRECATION")
    if (!wm.isWifiEnabled) return Metric(0, "OFF")
    if (!onWifi) return Metric(0)
    @Suppress("DEPRECATION")
    val rssi = runCatching { wm.connectionInfo.rssi }.getOrDefault(-127)
    val max = wm.maxSignalLevel.coerceAtLeast(1)
    val level = wm.calculateSignalLevel(rssi).coerceIn(0, max)
    return Metric(level * 100 / max)
}

/** モバイル電波の強さ 0..100(0..4 の 5 段階)。権限不要 */
fun readSignal(context: Context): Metric {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager ?: return Metric(0)
    val level = runCatching { tm.signalStrength?.level }.getOrNull() ?: return Metric(0, "OFF")
    return Metric((level.coerceIn(0, 4)) * 100 / 4)
}

/** Bluetooth: オフ 0 / オン 50 / 音声機器が繋がっている 100。権限は要らない */
fun readBluetooth(context: Context): Metric {
    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
    val enabled = runCatching { bm?.adapter?.isEnabled == true }.getOrDefault(false)
    if (!enabled) return Metric(0, "OFF")
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    val connected = am?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.any {
        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER
    } == true
    return if (connected) Metric(100, "LINK") else Metric(50, "ON")
}

/**
 * Bluetooth の切り替え。Android 13 以降はアプリから直接オン/オフできないので、
 * システムの確認ダイアログ(REQUEST_ENABLE / REQUEST_DISABLE)を出す。BLUETOOTH_CONNECT が要る
 */
fun toggleBluetooth(context: Context) {
    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
    val enabled = runCatching { bm?.adapter?.isEnabled == true }.getOrDefault(false)
    val action = if (enabled) "android.bluetooth.adapter.action.REQUEST_DISABLE" else android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** システムの Wi-Fi パネル(画面下の小さなシート。スイッチ付き) */
fun wifiPanelIntent(): Intent = Intent(android.provider.Settings.Panel.ACTION_WIFI)

/** システムのインターネット接続パネル(モバイルデータと Wi-Fi のスイッチ) */
fun internetPanelIntent(): Intent = Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY)

// ---- 明るさ・音量(DRIVE のスライダー用) ----

/** 「システム設定の変更」を許可されているか(明るさの書き込みに要る) */
fun canWriteSettings(context: Context): Boolean = android.provider.Settings.System.canWrite(context)

/** 「システム設定の変更」の許可画面(自分のアプリの行) */
fun writeSettingsIntent(context: Context): Intent =
    Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + context.packageName))

/** 画面の明るさ 0..1 */
fun readBrightness(context: Context): Float = runCatching {
    android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
}.getOrDefault(0.5f).coerceIn(0f, 1f)

/** 画面の明るさを設定(0..1)。自動明るさは切る。許可が無ければ false */
fun setBrightness(context: Context, level: Float): Boolean {
    if (!canWriteSettings(context)) return false
    val cr = context.contentResolver
    return runCatching {
        android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS, (level.coerceIn(0f, 1f) * 255).toInt().coerceIn(1, 255))
    }.getOrDefault(false)
}

/** メディア音量 0..1 */
fun readVolume(context: Context): Float {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return 0f
    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / max
}

/** メディア音量を設定(0..1)。権限不要 */
fun setVolume(context: Context, level: Float) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    runCatching { am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Math.round(level.coerceIn(0f, 1f) * max), 0) }
}
