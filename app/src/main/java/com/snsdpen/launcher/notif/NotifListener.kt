package com.snsdpen.launcher.notif

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 通知の件数をパッケージごとに配る(ステータスバーを隠した代わり)。
 * 常駐通知とグループのまとめは数えない。
 */
class NotifListener : NotificationListenerService() {
    override fun onListenerConnected() { publish() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { publish() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { publish() }
    override fun onListenerDisconnected() { _counts.value = emptyMap() }

    private fun publish() {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        val counts = HashMap<String, Int>()
        for (n in active) {
            if (n.isOngoing) continue
            val flags = n.notification.flags
            if (flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            counts[n.packageName] = (counts[n.packageName] ?: 0) + 1
        }
        _counts.value = counts
        android.util.Log.d("NotifListener", "active=${active.size} counted=${counts.values.sum()} $counts")
    }

    companion object {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        /** package → 件数 */
        val counts: StateFlow<Map<String, Int>> = _counts

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /**
         * 繋ぎ直しを頼む。強制終了やクラッシュの後は OS が勝手に再接続しないので、復帰のたびに呼ぶ。
         * 許可が無ければ何もしない。
         */
        fun ensureBound(context: Context) {
            if (!isEnabled(context)) return
            runCatching { requestRebind(android.content.ComponentName(context, NotifListener::class.java)) }
        }

        /** 「通知へのアクセス」の設定画面を開く */
        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            runCatching { context.startActivity(intent) }
        }
    }
}
