package com.snsdpen.launcher.ui.modules

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
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
private val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d EEE", Locale.ENGLISH)

@Composable
private fun ClockModule(scope: ModuleScope) {
    val p = LocalPalette.current
    // 分の境界まで待って更新。1 秒ループより起きる回数が少ない
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            val ms = System.currentTimeMillis()
            delay((60_000 - ms % 60_000).coerceAtLeast(250L))
            value = LocalDateTime.now()
        }
    }
    // 新グリッド(1 行 ≈ 25dp)では 4 行以上で日付を出す
    val big = scope.span.h >= 3
    // DRIVE では時刻をアクセント色に(視認性)
    val timeColor = if (scope.page == Page.DRIVE) p.accent else p.fg
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(now.format(TimeFmt), color = timeColor, fontSize = if (big) 36.sp else 20.sp, lineHeight = if (big) 40.sp else 22.sp)
        if (big) Text(now.format(DateFmt).uppercase(Locale.ENGLISH), color = p.fgDim, fontSize = 12.sp)
    }
}
