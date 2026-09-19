package com.snsdpen.launcher.ui.modules

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.model.Face
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.Page
import com.snsdpen.launcher.model.REF_CLOCK
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.WhileResumed
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val ClockSpec = ModuleSpec(
    kind = REF_CLOCK,
    name = "CLOCK",
    defaultSize = { face -> if (face == Face.COVER) Span(16, 3) else Span(8, 2) },
    minSize = { Span(6, 2) },
    onTap = { _, emit ->
        emit(ModuleEvent.OpenIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS)))
    },
    content = { ClockModule(it) },
)

private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val SecFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("ss", Locale.ROOT)
private val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d", Locale.ROOT)
private val DowFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

@Composable
private fun ClockModule(scope: ModuleScope) {
    val p = LocalPalette.current
    // 秒の境界に合わせて毎秒更新。表示中(RESUMED)だけ動き、裏に回ると止まる
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(LocalDateTime.now()) }
    WhileResumed {
        now = LocalDateTime.now()
        while (true) {
            val ms = System.currentTimeMillis()
            delay((1000 - ms % 1000).coerceAtLeast(50L))
            now = LocalDateTime.now()
        }
    }
    // 高さで 3 段階: 3 行以上 = 大、2 行 = 中(日付あり)、1 行 = 時刻だけ
    val h = scope.span.h
    val big = h >= 3
    val showDate = h >= 2
    val timeSize = if (big) 36.sp else if (h == 2) 26.sp else 18.sp
    val timeLine = if (big) 38.sp else if (h == 2) 28.sp else 20.sp
    val timeColor = if (scope.page == Page.DRIVE) p.accent else p.fg
    // 曜日の色: 配色の系列色を曜日で回す(日=0 … 土=6)。系列が無ければアクセント
    val dow = now.dayOfWeek.value % 7
    val dowColor = if (p.series.isNotEmpty()) p.series[dow % p.series.size] else p.accent

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(now.format(TimeFmt), color = timeColor, fontSize = timeSize, lineHeight = timeLine)
            Spacer(Modifier.width(6.dp))
            Text(
                now.format(SecFmt),
                color = p.accent,
                fontSize = if (big) 16.sp else 11.sp,
                lineHeight = if (big) 30.sp else 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = if (big) 4.dp else 3.dp),
            )
        }
        if (showDate) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(now.format(DateFmt), color = p.fgDim, fontSize = if (big) 12.sp else 11.sp, lineHeight = if (big) 14.sp else 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    now.format(DowFmt).uppercase(Locale.ENGLISH),
                    color = dowColor,
                    fontSize = if (big) 12.sp else 11.sp,
                    lineHeight = if (big) 14.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}
