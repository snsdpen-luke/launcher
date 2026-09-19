package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.data.EventItem
import com.snsdpen.launcher.data.hasCalendarPermission
import com.snsdpen.launcher.data.loadUpcomingEvents
import com.snsdpen.launcher.model.Face
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.REF_CALENDAR
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.LocalRefreshTick
import com.snsdpen.launcher.ui.Marker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 見出しは今日の日付(タップでカレンダーアプリ)。高さがあれば 7 日分の予定を行で並べる。
 * 権限が無ければ「TAP TO ALLOW」の行を出し、その場で求める。
 */
val CalendarSpec = ModuleSpec(
    kind = REF_CALENDAR,
    name = "CALENDAR",
    defaultSize = { face -> if (face == Face.COVER) Span(8, 10) else Span(8, 8) },
    minSize = { Span(6, 1) },
    onTap = { _, emit -> emit(ModuleEvent.OpenCalendar) },
    content = { CalendarModule(it) },
)

private val HeaderH = 28.dp
private val RowH = 44.dp
private val DayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d EEE", Locale.ENGLISH)
private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val tick = LocalRefreshTick.current
    val granted = remember(tick) { hasCalendarPermission(context) }
    val events by produceState(initialValue = emptyList<EventItem>(), tick, granted) {
        value = if (granted) withContext(Dispatchers.IO) { loadUpcomingEvents(context) } else emptyList()
    }
    val edit = scope.editMode

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxH = maxHeight
        val rows = ((maxH - HeaderH) / RowH).toInt().coerceAtLeast(0)
        val shown = events.take(rows)
        val overflow = events.size - shown.size

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(HeaderH).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Marker(p.fg)
                Spacer(Modifier.width(10.dp))
                Text("CALENDAR", color = p.fg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (overflow > 0) Text("+$overflow  ", color = p.fgDim, fontSize = 10.sp)
                Text(LocalDate.now().format(DayFmt).uppercase(Locale.ENGLISH), color = p.fgDim, fontSize = 10.sp)
            }
            if (rows == 0) return@Column
            if (!granted) {
                Text(
                    "TAP TO ALLOW",
                    color = p.fg,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .then(
                            if (edit) Modifier
                            else Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { scope.emit(ModuleEvent.RequestCalendarPermission) }
                        )
                        .padding(start = 26.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                )
                return@Column
            }
            if (events.isEmpty()) {
                Text("NO EVENTS · 7 DAYS", color = p.fgDim, fontSize = 11.sp, modifier = Modifier.padding(start = 26.dp, top = 6.dp))
            }
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            shown.forEach { e ->
                // 終日予定の begin は UTC の 0 時。端末の時差で日付がズレないよう UTC で読む
                val dayZone = if (e.allDay) ZoneOffset.UTC else zone
                val day = Instant.ofEpochMilli(e.begin).atZone(dayZone).toLocalDate()
                val endDay = Instant.ofEpochMilli(e.end).atZone(dayZone).toLocalDate()
                val ongoing = !day.isAfter(today) && endDay.isAfter(today.minusDays(if (e.allDay) 1 else 0)) && !day.isEqual(today)
                val dayLabel = if (day == today || ongoing) "TODAY" else day.format(DayFmt).uppercase(Locale.ENGLISH)
                val isToday = day == today || ongoing
                val time = if (e.allDay) "ALL DAY" else {
                    val b = Instant.ofEpochMilli(e.begin).atZone(zone).toLocalTime().format(TimeFmt)
                    val en = Instant.ofEpochMilli(e.end).atZone(zone).toLocalTime().format(TimeFmt)
                    "$b–$en"
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(RowH)
                        .then(
                            if (edit) Modifier
                            else Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { scope.emit(ModuleEvent.OpenEvent(e)) },
                                onLongClick = { scope.emit(ModuleEvent.EnterEdit) },
                            )
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Marker(if (isToday) p.fg else p.fgDim, size = 4.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = p.fg, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$dayLabel  $time", color = p.fgDim, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
