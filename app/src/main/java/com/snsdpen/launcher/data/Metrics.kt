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
    if (!onWifi) return Metric(0, "OFF")
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
