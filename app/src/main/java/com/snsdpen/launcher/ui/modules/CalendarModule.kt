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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
    minSize = { Span(4, 1) },
    onTap = { _, emit -> emit(ModuleEvent.OpenCalendar) },
    content = { CalendarModule(it) },
)

private val HeaderH = 28.dp
private val DayH = 22.dp
private val EventH = 30.dp
private val DayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d EEE", Locale.ENGLISH)
private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/** 1 日分(見出し + その日の予定) */
private class DayGroup(val day: LocalDate, val label: String, val isToday: Boolean, val events: MutableList<EventItem> = mutableListOf())

/**
 * 予定を日ごとにまとめる。進行中の複数日予定は今日の欄に入れる。
 * 日付見出しが 1 行、その下に予定。予定の左は時刻(終日は ALL DAY)、右に題名 1 行
 */
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
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    // 日ごとにまとめる
    val groups = remember(events) {
        val map = LinkedHashMap<LocalDate, DayGroup>()
        for (e in events) {
            val dayZone = if (e.allDay) ZoneOffset.UTC else zone
            val day = Instant.ofEpochMilli(e.begin).atZone(dayZone).toLocalDate()
            val endDay = Instant.ofEpochMilli(e.end).atZone(dayZone).toLocalDate()
            val ongoing = day.isBefore(today) && endDay.isAfter(today.minusDays(if (e.allDay) 1 else 0))
            val key = if (ongoing) today else day
            val g = map.getOrPut(key) {
                val isToday = key == today
                val label = if (isToday) "TODAY · " + key.format(DayFmt).uppercase(Locale.ENGLISH)
                else if (key == today.plusDays(1)) "TOMORROW · " + key.format(DayFmt).uppercase(Locale.ENGLISH)
                else key.format(DayFmt).uppercase(Locale.ENGLISH)
                DayGroup(key, label, isToday)
            }
            g.events += e
        }
        map.values.sortedBy { it.day }
    }

    // 7 日分の件数(帯用)
    val counts = remember(groups) { (0 until 7).map { i -> groups.firstOrNull { it.day == today.plusDays(i.toLong()) }?.events?.size ?: 0 } }
    val stripH = scope.cell + com.snsdpen.launcher.ui.GridGap

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 高さに入るだけ詰める(見出し 22dp + 予定 30dp ずつ)。入らない分は件数だけ
        var budget = maxHeight - HeaderH - stripH
        val shown = ArrayList<Pair<DayGroup, List<EventItem>>>()
        var hidden = 0
        for (g in groups) {
            if (budget < DayH + EventH) { hidden += g.events.size; continue }
            budget -= DayH
            val n = ((budget / EventH).toInt()).coerceIn(0, g.events.size)
            shown += g to g.events.take(n)
            hidden += g.events.size - n
            budget -= EventH * n
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(HeaderH).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Marker(p.fg)
                Spacer(Modifier.width(10.dp))
                Text("CALENDAR", color = p.fg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (hidden > 0) Text("+$hidden", color = p.fgDim, fontSize = 10.sp)
            }
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
            // 7 日の帯: 1 日 1 マス(マス目に揃える)。今日は塗り、予定が多い日ほど濃い
            Row(Modifier.fillMaxWidth().height(stripH), verticalAlignment = Alignment.Top) {
                for (i in 0 until 7) {
                    val d = today.plusDays(i.toLong())
                    val n = counts[i]
                    val isToday = i == 0
                    val fill = when {
                        isToday -> p.fg
                        n == 0 -> p.fg.copy(alpha = 0.06f)
                        n == 1 -> p.fg.copy(alpha = 0.14f)
                        n == 2 -> p.fg.copy(alpha = 0.22f)
                        else -> p.fg.copy(alpha = 0.32f)
                    }
                    Box(
                        Modifier.size(scope.cell).background(fill).then(
                            if (edit) Modifier else Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                            ) { scope.emit(ModuleEvent.OpenCalendar) }
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            color = if (isToday) p.bg else if (d.dayOfWeek.value >= 6) p.fgDim else p.fg,
                            fontSize = 11.sp, lineHeight = 12.sp,
                            fontWeight = if (isToday) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                        )
                    }
                    if (i < 6) Spacer(Modifier.width(com.snsdpen.launcher.ui.GridGap))
                }
            }
            if (events.isEmpty()) {
                Text("NO EVENTS · 7 DAYS", color = p.fgDim, fontSize = 11.sp, modifier = Modifier.padding(start = 26.dp, top = 6.dp))
            }
            for ((g, list) in shown) {
                // 日付見出し: 今日は濃く、他は薄く
                Row(Modifier.fillMaxWidth().height(DayH).padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        g.label,
                        color = if (g.isToday) p.fg else p.fgDim,
                        fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                for (e in list) {
                    val time = if (e.allDay) "ALL DAY" else Instant.ofEpochMilli(e.begin).atZone(zone).toLocalTime().format(TimeFmt)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(EventH)
                            .then(
                                if (edit) Modifier
                                else Modifier.combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { scope.emit(ModuleEvent.OpenEvent(e)) },
                                    onLongClick = { scope.emit(ModuleEvent.EnterEdit) },
                                )
                            )
                            .padding(start = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 左は時刻の列(幅を揃える)。終日は薄く小さく
                        Text(
                            time,
                            color = if (e.allDay) p.fgDim else p.fg,
                            fontSize = if (e.allDay) 9.sp else 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.width(50.dp),
                        )
                        Text(
                            e.title,
                            color = if (g.isToday) p.fg else p.fg.copy(alpha = 0.8f),
                            fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
