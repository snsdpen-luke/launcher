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
import androidx.compose.ui.text.drawText
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
    defaultSize = { face -> if (face == Face.COVER) Span(8, 1) else Span(8, 1) },
    minSize = { Span(6, 1) },
    onTap = { _, emit ->
        emit(ModuleEvent.OpenIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS)))
    },
    content = { ClockModule(it) },
)

private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val SecFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("ss", Locale.ROOT)
private val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d", Locale.ROOT)
private val DowFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

/** Roboto の大文字・数字の高さ(em 比)。数字の高さをマスに合わせる計算に使う */
private const val CapHeight = 0.711f
/** 時刻の数字の高さ(マスに対する比)。1.0 だと強すぎた */
private const val TimeHeight = 0.75f

/**
 * 時計は 1 行。時刻の数字の高さをモジュールの高さ(= マス)にぴったり合わせ、
 * 秒・日付・曜日はその右に、ベースラインをマスの下辺に揃えて 1 行に並べる。
 * フォントの上下の余白に頼らず、文字を測って自分で描く(マス目とずれない)。
 */
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
    // DRIVE は差し色を使わず白とグレーだけ(琥珀の時刻はタイルの色とけんかする)
    val drive = scope.page == Page.DRIVE
    val timeColor = p.fg
    // 曜日の色: 配色の系列色を曜日で回す(日=0 … 土=6)。系列が無ければアクセント
    val dow = now.dayOfWeek.value % 7
    val dowColor = if (drive) p.fg else if (p.series.isNotEmpty()) p.series[dow % p.series.size] else p.accent
    val secColor = if (drive) p.fgDim else p.accent
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val time = now.format(TimeFmt)
    val sec = now.format(SecFmt)
    val date = now.format(DateFmt)
    val dowText = now.format(DowFmt).uppercase(Locale.ENGLISH)

    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val h = size.height
        if (h <= 0f) return@Canvas
        // 数字の高さ = モジュールの高さの 75%(マスいっぱいは強調しすぎ)。下辺はマスの下辺。fontSize(px) = 高さ / CapHeight
        val timeStyle = androidx.compose.ui.text.TextStyle(fontSize = (h * TimeHeight / CapHeight).toSp(), color = timeColor)
        val smallStyle = androidx.compose.ui.text.TextStyle(fontSize = (h * 0.40f).toSp(), fontWeight = FontWeight.Medium)
        val gap = h * 0.22f
        var x = 0f
        fun put(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color) {
            val layout = measurer.measure(text, style, maxLines = 1)
            // ベースラインをマスの下辺に置く(数字の上端がマスの上辺に来る)
            drawText(layout, color = color, topLeft = androidx.compose.ui.geometry.Offset(x, h - layout.firstBaseline))
            x += layout.size.width + gap
        }
        put(time, timeStyle, timeColor)
        put(sec, smallStyle, secColor)
        put(date, smallStyle, p.fgDim)
        put(dowText, smallStyle.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), dowColor)
    }
}
