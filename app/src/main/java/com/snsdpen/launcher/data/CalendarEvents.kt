package com.snsdpen.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

data class EventItem(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
)

fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/** 今日の 0 時から [days] 日分の予定(終了済みは除く)。権限が無ければ空 */
fun loadUpcomingEvents(context: Context, days: Int = 7, limit: Int = 40): List<EventItem> {
    if (!hasCalendarPermission(context)) return emptyList()
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val end = start + days * 86_400_000L
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(start.toString())
        .appendPath(end.toString())
        .build()
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
    )
    val out = mutableListOf<EventItem>()
    runCatching {
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val e = EventItem(
                    id = c.getLong(0),
                    title = c.getString(1)?.ifBlank { null } ?: "(no title)",
                    begin = c.getLong(2),
                    end = c.getLong(3),
                    allDay = c.getInt(4) != 0,
                )
                if (e.end >= now && out.none { it.title == e.title && it.begin == e.begin && it.allDay == e.allDay }) out += e
            }
        }
    }
    return out
}

/** 予定をカレンダーアプリで開く */
fun openEvent(context: Context, e: EventItem) {
    val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return
    openCalendarApp(context)
}
