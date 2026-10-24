package com.snsdpen.launcher.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snsdpen.launcher.launcherStore
import com.snsdpen.launcher.model.Page
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * ページの自動切り替え。
 * - 車(カーオーディオ / ハンズフリーの機器)に Bluetooth で繋がると DRIVE。切れると時間割のページへ
 * - 時間割: 平日 7:30〜17:30 は WORK、それ以外は PRIVATE。**境目をまたいだ時だけ**切り替える
 * - 手でページを変えた日は、時間割では動かさない(車の接続・切断は常に効く)
 * 判定はホームに戻った時と表示中の 1 分ごと(`tick`)、車は Bluetooth の放送(`CarReceiver`)。裏で回し続けない
 */
object AutoPage {
    val WorkStart: LocalTime = LocalTime.of(7, 30)
    val WorkEnd: LocalTime = LocalTime.of(17, 30)

    private fun prefs(c: Context) = c.getSharedPreferences("autopage", Context.MODE_PRIVATE)

    /** 時間割で決まるページ */
    fun schedulePage(now: LocalDateTime = LocalDateTime.now()): Page {
        val weekday = now.dayOfWeek != DayOfWeek.SATURDAY && now.dayOfWeek != DayOfWeek.SUNDAY
        val t = now.toLocalTime()
        return if (weekday && !t.isBefore(WorkStart) && t.isBefore(WorkEnd)) Page.WORK else Page.PRIVATE
    }

    /** 時間割の区切り(日付 + ページ)。これが変わった時が「境目をまたいだ時」 */
    private fun slot(now: LocalDateTime = LocalDateTime.now()) = "${now.toLocalDate()}-${schedulePage(now)}"

    /** ホームに戻った時と、表示中の 1 分ごとに呼ぶ */
    fun tick(context: Context) {
        val p = prefs(context)
        if (p.getBoolean("car", false)) return
        val s = slot()
        if (s == p.getString("slot", null)) return
        p.edit().putString("slot", s).apply()
        if (p.getLong("manualDay", -1L) == LocalDate.now().toEpochDay()) return
        context.launcherStore.setPage(schedulePage())
    }

    /** 手でページを変えた(この日は時間割で動かさない) */
    fun markManual(context: Context) {
        prefs(context).edit().putLong("manualDay", LocalDate.now().toEpochDay()).apply()
    }

    @SuppressLint("MissingPermission")
    private fun isCar(device: BluetoothDevice?): Boolean = runCatching {
        val cls = device?.bluetoothClass?.deviceClass ?: return false
        cls == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO || cls == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
    }.getOrDefault(false)

    fun onCarConnected(context: Context, device: BluetoothDevice?) {
        if (!isCar(device)) return
        prefs(context).edit().putBoolean("car", true).putString("carAddr", device?.address).apply()
        context.launcherStore.setPage(Page.DRIVE)
    }

    fun onCarDisconnected(context: Context, device: BluetoothDevice?) {
        val p = prefs(context)
        if (!p.getBoolean("car", false)) return
        if (device?.address != null && device.address != p.getString("carAddr", null)) return
        p.edit().putBoolean("car", false).putString("slot", slot()).apply()
        context.launcherStore.setPage(schedulePage())
    }
}

/** 車の Bluetooth の接続・切断(ACL の放送はマニフェストで受けられる例外) */
class CarReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> AutoPage.onCarConnected(context, device)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> AutoPage.onCarDisconnected(context, device)
        }
    }
}
