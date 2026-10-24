package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snsdpen.launcher.data.ClassTimer
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.REF_TIMER
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.ui.GridGap
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.NotifOrange
import com.snsdpen.launcher.ui.WhileResumed
import com.snsdpen.launcher.ui.blinkAlpha
import kotlinx.coroutines.delay

/**
 * 授業タイマー(1 行)。止まっている時は「TIMER」と 45 / 50 のブロック。押すとおやすみモードにして数え始める。
 * 動いている時は残り時間の数字と、減っていくブロック、右端に STOP。終わったら「おわり」が橙で光り、押すと消える
 */
val TimerSpec = ModuleSpec(
    kind = REF_TIMER,
    name = "TIMER",
    defaultSize = { Span(8, 1) },
    minSize = { Span(7, 1) },
    content = { TimerModule(it) },
)

private val Presets = listOf(45, 50)

@Composable
private fun TimerModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val context = LocalContext.current
    remember { ClassTimer.load(context); true }
    val st by ClassTimer.state.collectAsStateWithLifecycle()
    val cell = scope.cell
    val cols = scope.span.w.coerceAtLeast(1)
    val edit = scope.editMode
    val empty = p.blockEmpty ?: p.fg.copy(alpha = 0.12f)

    when {
        st.finishedAt > 0L -> {
            val a = blinkAlpha(true)
            Row(Modifier.fillMaxSize().then(rememberTap(edit) { ClassTimer.acknowledge(context) }), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(cell).height(cell).background(NotifOrange.copy(alpha = a)))
                Spacer(Modifier.width(8.dp))
                Text("おわり · タップで消す", color = p.fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        st.endAt > 0L -> RunningTimer(st.endAt, st.minutes, cols, cell, empty, edit, onStop = { ClassTimer.stop(context) })
        else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text("TIMER", color = p.fgDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(cell * 3 + GridGap * 2).padding(start = 6.dp))
            for (m in Presets) {
                Box(
                    Modifier
                        .width(cell * 2 + GridGap).fillMaxHeight()
                        .background(p.accent.copy(alpha = 0.22f))
                        .then(rememberTap(edit) {
                            if (!ClassTimer.start(context, m)) runCatching {
                                context.startActivity(ClassTimer.dndAccessIntent().addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }),
                    contentAlignment = Alignment.Center,
                ) { Text("$m", color = p.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(GridGap))
            }
        }
    }
}

/** 動いている間の表示。秒の状態はここに閉じる(表示中だけ 1 秒ごと) */
@Composable
private fun RunningTimer(endAt: Long, minutes: Int, cols: Int, cell: Dp, empty: Color, edit: Boolean, onStop: () -> Unit) {
    val p = LocalPalette.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    WhileResumed(endAt) {
        while (true) { now = System.currentTimeMillis(); delay(1000L - now % 1000L) }
    }
    val left = (endAt - now).coerceAtLeast(0L)
    val mm = left / 60_000; val ss = (left / 1000) % 60
    val total = (minutes * 60_000L).coerceAtLeast(1L)
    val bar = (cols - 5).coerceAtLeast(1)          // 数字 3 マス + STOP 2 マス
    val lit = Math.ceil(left.toDouble() / total * bar).toInt().coerceIn(0, bar)
    val soon = left <= 5 * 60_000L
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "%d:%02d".format(mm, ss),
            color = if (soon) NotifOrange else p.fg,
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(cell * 3 + GridGap * 2).padding(start = 6.dp),
        )
        for (i in 0 until bar) {
            Box(Modifier.width(cell).fillMaxHeight().background(if (i < lit) (if (soon) NotifOrange else p.accent.copy(alpha = 0.75f)) else empty))
            Spacer(Modifier.width(GridGap))
        }
        Box(
            Modifier.width(cell * 2 + GridGap).fillMaxHeight().background(p.fg.copy(alpha = 0.10f)).then(rememberTap(edit, onStop)),
            contentAlignment = Alignment.Center,
        ) { Text("STOP", color = p.fgDim, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}


/**
 * 押せる範囲。受け口(interactionSource)と処理は remember して作り直さない。
 * 1 秒ごとに描き直す行で毎回作り直すと、指が触れている途中のタップが捨てられる。編集中は clickable ごと外す
 */
@Composable
private fun rememberTap(edit: Boolean, action: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val latest by androidx.compose.runtime.rememberUpdatedState(action)
    return if (edit) Modifier else Modifier.clickable(interactionSource = src, indication = null) { latest() }
}
