package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.data.Metric
import com.snsdpen.launcher.data.readBattery
import com.snsdpen.launcher.data.readMemory
import com.snsdpen.launcher.data.readSignal
import com.snsdpen.launcher.data.readStorage
import com.snsdpen.launcher.data.readWifi
import com.snsdpen.launcher.notif.NotifListener
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.ui.GridGap
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.LocalRefreshTick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 床のタイル。1 マス = 1 ブロックで、計測値を「埋まったブロックの数」で表す。
 * 8 マス幅なら 1 ブロック = 12.5%。伸ばせば細かくなる。数字は書かない。
 * 色は 3 つ(テラコッタ / ベージュ / アンバー)。編集モードで再タップすると次の色。
 */
val MeterSpec = ModuleSpec(
    kind = "meter",
    name = "METER",
    singleton = false,
    defaultSize = { Span(8, 1) },
    minSize = { Span(1, 1) },
    exists = { layout, id -> layout.meters.any { it.id == id } },
    // NTF は通知アクセスが無いときだけ、タップで設定画面へ
    onTap = { scope, emit ->
        val def = scope.layout.meters.firstOrNull { it.id == scope.id }
        if (def?.metric == "notif") emit(ModuleEvent.OpenNotifAccess)
    },
    onEdit = { scope, emit -> scope.id?.let { emit(ModuleEvent.CycleMeter(it)) } },
    content = { MeterModule(it) },
)

val MeterShades: List<Color> = listOf(
    Color(0xFFB5563A),   // 0 テラコッタ(BAT)
    Color(0xFFD9B68C),   // 1 ベージュ(MEM)
    Color(0xFF6B4226),   // 2 アンバー(STO)
)
private val InkLight = Color(0xFFF3E9DC)
private val InkDark = Color(0xFF14204A)

val MeterMetrics: List<Pair<String, String>> = listOf(
    "battery" to "BAT",
    "memory" to "MEM",
    "storage" to "STO",
    "wifi" to "WIFI",
    "signal" to "SIG",
    "notif" to "NTF",
    "blank" to "",
)

@Composable
private fun MeterModule(scope: ModuleScope) {
    val def = scope.layout.meters.firstOrNull { it.id == scope.id } ?: return
    val context = LocalContext.current
    val tick = LocalRefreshTick.current
    // 色は配色から(無ければ茶 3 色)。文字は色の明るさで白か濃紺を選ぶ
    val p = LocalPalette.current
    val blocks = p.blocks.ifEmpty { MeterShades }
    val shade = blocks[def.shade.mod(blocks.size)]
    val ink = if (shade.luminance() > 0.4f) InkDark else InkLight
    val empty = p.blockEmpty ?: p.fg.copy(alpha = 0.15f)
    // 表示中だけ 30 秒ごとに読む(ページを離れると止まる)
    val metric by produceState<Metric?>(initialValue = null, def.metric, tick) {
        if (def.metric == "blank" || def.metric == "notif") return@produceState
        while (true) {
            value = withContext(Dispatchers.IO) {
                when (def.metric) {
                    "battery" -> readBattery(context)
                    "memory" -> readMemory(context)
                    "storage" -> readStorage()
                    "wifi" -> readWifi(context)
                    "signal" -> readSignal(context)
                    else -> null
                }
            }
            delay(if (def.metric == "wifi" || def.metric == "signal") 10_000 else 30_000)
        }
    }
    val cols = scope.span.w.coerceAtLeast(1)
    val rows = scope.span.h.coerceAtLeast(1)
    val total = cols * rows
    val filled = when {
        def.metric == "blank" -> total
        // 通知: 未読の件数 = ブロックの数(上限はマス数)
        def.metric == "notif" -> scope.notifCounts.values.sum().coerceIn(0, total)
        metric == null -> 0
        else -> (metric!!.percent / 100f * total).roundToInt().coerceIn(0, total)
    }
    // 通知アクセスが無いときは先頭に ! を出す(タップで設定へ)
    val notifOff = def.metric == "notif" && !NotifListener.isEnabled(context)
    val label = MeterMetrics.firstOrNull { it.first == def.metric }?.second.orEmpty()

    // 1 ブロック = 1 マス。間隔をグリッドと同じにして床のタイルに見せる
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GridGap)) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                for (c in 0 until cols) {
                    val i = r * cols + c
                    val on = i < filled
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(if (on) shade else empty),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        if (i == 0 && label.isNotEmpty()) {
                            Text(if (notifOff) "$label!" else label, color = if (on) ink else p.fgDim, fontSize = 7.sp, lineHeight = 8.sp, modifier = Modifier.padding(2.dp))
                        }
                    }
                }
            }
        }
    }
}
