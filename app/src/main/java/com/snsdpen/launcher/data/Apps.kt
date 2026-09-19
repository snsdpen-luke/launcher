package com.snsdpen.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val category: Int,
)

/** 自分のホーム入口。コンポーネント単位で除外する(パッケージごと除外すると将来の分割パネル入口も消える) */
private const val HOME_ACTIVITY = "com.snsdpen.launcher.MainActivity"

fun loadLaunchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map {
            AppEntry(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                activityName = it.activityInfo.name,
                category = it.activityInfo.applicationInfo.category,
            )
        }
        .filter { !(it.packageName == context.packageName && it.activityName == HOME_ACTIVITY) }
        .sortedBy { it.label.lowercase() }
}

fun launchApp(context: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
}

/** アプリアイコンのメモリキャッシュ */
object IconCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun get(context: Context, app: AppEntry): ImageBitmap? {
        val key = "${app.packageName}/${app.activityName}"
        cache[key]?.let { return it }
        return runCatching {
            context.packageManager
                .getActivityIcon(ComponentName(app.packageName, app.activityName))
                .toBitmap(128, 128)
                .asImageBitmap()
        }.getOrNull()?.also { cache[key] = it }
    }
}

/** アイコン(非同期ロード + キャッシュ)。ロード中は空 */
@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, app) {
        value = withContext(Dispatchers.IO) { IconCache.get(context, app) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = app.label, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size))
    }
}

/** URL を既定のブラウザ等で開く(スキームが無ければ https:// を補う) */
fun openLink(context: Context, url: String) {
    val full = if (url.contains("://")) url else "https://$url"
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(full))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** 表示用のドメイン(www. を落とす)。解釈できなければ URL のまま */
fun domainOf(url: String): String {
    val full = if (url.contains("://")) url else "https://$url"
    return runCatching { android.net.Uri.parse(full).host }.getOrNull()
        ?.removePrefix("www.")?.ifBlank { null } ?: url
}

/** カレンダーアプリを開く。Google カレンダー → 端末既定 → 日付ビューの順に試す */
fun openCalendarApp(context: Context) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    val candidates = listOfNotNull(
        context.packageManager.getLaunchIntentForPackage("com.google.android.calendar"),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("content://com.android.calendar/time")),
    )
    for (intent in candidates) {
        if (runCatching { context.startActivity(intent.addFlags(flags)); true }.getOrDefault(false)) return
    }
}
