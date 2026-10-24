package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.data.EventItem
import com.snsdpen.launcher.data.hasCalendarPermission
import com.snsdpen.launcher.data.loadUpcomingEvents
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.REF_NEXT
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.ui.GridGap
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.LocalRefreshTick
import com.snsdpen.launcher.ui.NotifOrange
import com.snsdpen.launcher.ui.WhileResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 次の予定のカウントダウン(1 行)。「あと 25 分 · 職員会議」/ 最中なら「いま · 職員会議 · 残り 12 分」。
 * 右のブロックは 10 分で 1 個、残りに合わせて減る(最後の 10 分は橙)。終日の予定は外す。24 時間以内に無ければ薄く「予定なし」。
 * タップでその予定を開く
 */
val NextEventSpec = ModuleSpec(
    kind = REF_NEXT,
    name = "NEXT",
    defaultSize = { Span(12, 1) },
    minSize = { Span(6, 1) },
    onTap = { scope, emit ->
        if (NextState.current == null && NextState.needsPermission) emit(ModuleEvent.RequestCalendarPermission)
        else NextState.current?.let { emit(ModuleEvent.OpenEvent(it)) }
    },
    content = { NextEventModule(it) },
)

/** 確認用: true にすると「25 分後にテスト会議」を混ぜる(カレンダーには書かない) */
private const val NEXT_DEBUG = false

/** タップ用に今出している予定を覚えておく(描画側が書く) */
private object NextState {
    var current: EventItem? = null
    var needsPermission: Boolean = false
}

@Composable
private fun NextEventModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val tick = LocalRefreshTick.current
    var events by remember { mutableStateOf<List<EventItem>>(emptyList()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var granted by remember { mutableStateOf(hasCalendarPermission(context)) }
    // 予定は 5 分ごと、時刻は 30 秒ごとに読み直す(表示中だけ)
    WhileResumed(tick) {
        var n = 0
        while (true) {
            granted = hasCalendarPermission(context)
            if (n % 10 == 0 && granted) events = withContext(Dispatchers.IO) { loadUpcomingEvents(context, days = 2) }
            now = System.currentTimeMillis()
            n++
            delay(30_000)
        }
    }
    val timed = (if (NEXT_DEBUG) listOf(EventItem(-1, "テスト会議", now + 25 * 60_000L, now + 75 * 60_000L, false)) else emptyList()) + events.filter { !it.allDay }
    val ongoing = timed.firstOrNull { it.begin <= now && now < it.end }
    val next = timed.firstOrNull { it.begin > now && it.begin - now <= 24 * 3_600_000L }
    val target = ongoing ?: next
    NextState.current = target
    NextState.needsPermission = !granted

    val remainMin = when {
        ongoing != null -> ((ongoing.end - now + 59_999) / 60_000).toInt()
        next != null -> ((next.begin - now + 59_999) / 60_000).toInt()
        else -> 0
    }
    fun span(m: Int): String = when {
        m < 60 -> "${m} 分"
        else -> "${m / 60} 時間" + if (m % 60 != 0) " ${m % 60} 分" else ""
    }
    val lead: String
    val title: String
    when {
        !granted -> { lead = "NEXT"; title = "TAP TO ALLOW" }
        ongoing != null -> { lead = "いま"; title = "${ongoing.title} · 残り ${span(remainMin)}" }
        next != null -> {
            lead = if (remainMin <= 180) "あと ${span(remainMin)}"
            else java.time.Instant.ofEpochMilli(next.begin).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString().take(5) + " から"
            title = next.title
        }
        else -> { lead = "NEXT"; title = "予定なし" }
    }
    val cols = scope.span.w.coerceAtLeast(1)
    val blocks = (cols - 6).coerceIn(0, 6)
    val lit = if (target == null) 0 else ((remainMin + 9) / 10).coerceIn(0, blocks)
    val soon = target != null && remainMin <= 10

    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).padding(start = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                lead,
                color = if (target == null) p.fgDim else if (soon) NotifOrange else p.fg,
                fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = if (target == null) p.fgDim else p.fg.copy(alpha = 0.85f),
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // 残りのブロック(左から点く。10 分で 1 個)
        if (blocks > 0) Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            val empty = p.blockEmpty ?: p.fg.copy(alpha = 0.12f)
            for (i in 0 until blocks) {
                val on = i < lit
                Box(
                    Modifier
                        .size(scope.cell)
                        .background(if (!on) empty else if (soon) NotifOrange else p.accent.copy(alpha = 0.55f + 0.45f * (i + 1) / blocks)),
                )
                if (i < blocks - 1) Spacer(Modifier.width(GridGap))
            }
        }
    }
}
