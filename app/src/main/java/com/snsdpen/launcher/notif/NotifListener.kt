package com.snsdpen.launcher.notif

import android.app.Notification
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
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
/** 通知 1 件の中身(タイルに出す用) */
data class NotifInfo(val title: String, val text: String, val icon: androidx.compose.ui.graphics.ImageBitmap?, val ongoing: Boolean)

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
        // 本文も配る(タイルに出す用)。常駐通知も含める(ナビの案内は常駐)。大きいアイコンは案内系だけ読む
        val infos = HashMap<String, NotifInfo>()
        for (n in active.sortedByDescending { it.postTime }) {
            if (infos.containsKey(n.packageName)) continue
            val ex = n.notification.extras
            val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = ex?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if (title.isBlank() && text.isBlank()) continue
            val icon = if (n.packageName in IconPackages) runCatching {
                n.notification.getLargeIcon()?.loadDrawable(this)?.toBitmap(96, 96)?.asImageBitmap()
            }.getOrNull() else null
            infos[n.packageName] = NotifInfo(title, text, icon, n.isOngoing)
        }
        _latest.value = infos
        android.util.Log.d("NotifListener", "active=${active.size} counted=${counts.values.sum()} $counts")
    }

    companion object {
        /** 通知の大きいアイコンまで読むパッケージ(ナビの矢印など) */
        private val IconPackages = setOf("com.google.android.apps.maps")
        private val _latest = MutableStateFlow<Map<String, NotifInfo>>(emptyMap())
        /** package → 最新の通知の本文 */
        val latest: StateFlow<Map<String, NotifInfo>> = _latest
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
