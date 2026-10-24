package com.snsdpen.launcher.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** タイマーの状態。endAt > 0 なら動いている、finishedAt > 0 なら終わって未確認 */
data class TimerState(val endAt: Long = 0L, val minutes: Int = 0, val finishedAt: Long = 0L)

/**
 * 授業タイマー。始めるとおやすみモード(優先のみ)にして、終わったら元に戻し、端末を震わせる。
 * 終わりは端末の目覚まし(AlarmManager の正確なアラーム)で起こすので、他のアプリを使っていても時間どおり。
 * おやすみモードの切り替えには「通知へのアクセス(マナーモード)」の許可が要る。無ければ設定画面を開いて始めない
 */
object ClassTimer {
    private const val REQ = 4501
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state
    private var loaded = false

    private fun prefs(c: Context) = c.getSharedPreferences("timer", Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _state.value = TimerState(p.getLong("endAt", 0L), p.getInt("minutes", 0), p.getLong("finishedAt", 0L))
        // 端末の再起動などで過ぎていたら終わりにする
        if (_state.value.endAt in 1..System.currentTimeMillis()) finish(context)
    }

    private fun save(context: Context, s: TimerState) {
        _state.value = s
        prefs(context).edit().putLong("endAt", s.endAt).putInt("minutes", s.minutes).putLong("finishedAt", s.finishedAt).apply()
    }

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun hasDndAccess(context: Context): Boolean = nm(context)?.isNotificationPolicyAccessGranted == true

    fun dndAccessIntent(): Intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQ, Intent(context, TimerReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 始める。許可が無ければ false(呼び手が設定画面を開く) */
    fun start(context: Context, minutes: Int): Boolean {
        if (!hasDndAccess(context)) return false
        val n = nm(context) ?: return false
        val p = prefs(context)
        // 元のおやすみモードを覚えて、優先のみにする(アラームや大事な電話は通る)
        if (_state.value.endAt == 0L) p.edit().putInt("prevFilter", n.currentInterruptionFilter).apply()
        runCatching { n.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
        val end = System.currentTimeMillis() + minutes * 60_000L
        val am = context.getSystemService(AlarmManager::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarmIntent(context))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarmIntent(context))
        }
        save(context, TimerState(endAt = end, minutes = minutes))
        return true
    }

    /** 途中でやめる(震わせない) */
    fun stop(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
        restoreDnd(context)
        save(context, TimerState())
    }

    /** 時間になった(TimerReceiver から) */
    fun finish(context: Context) {
        restoreDnd(context)
        save(context, TimerState(finishedAt = System.currentTimeMillis()))
        vibrate(context)
    }

    /** 「おわり」を確認した */
    fun acknowledge(context: Context) = save(context, TimerState())

    private fun restoreDnd(context: Context) {
        val n = nm(context) ?: return
        if (!hasDndAccess(context)) return
        val prev = prefs(context).getInt("prevFilter", NotificationManager.INTERRUPTION_FILTER_ALL)
        // こちらが優先のみにしたままの時だけ戻す(その間に手で変えていたら触らない)
        if (n.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) runCatching { n.setInterruptionFilter(prev) }
    }

    private fun vibrate(context: Context) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        runCatching { v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 700), -1)) }
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ClassTimer.load(context)
        ClassTimer.finish(context)
    }
}
