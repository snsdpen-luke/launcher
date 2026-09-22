package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.data.Metric
import com.snsdpen.launcher.data.readBattery
import com.snsdpen.launcher.data.readBluetooth
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
import com.snsdpen.launcher.ui.WhileResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 床のタイル。1 マス = 1 ブロックで、計測値を「埋まったブロックの数」で表す。
 * 8 マス幅なら 1 ブロック = 12.5%。伸ばせば細かくなる。数字は書かない。
 * 色は 3 つ(テラコッタ / ベージュ / アンバー)。編集モードで再タップすると次の色。
 */
/** 電池の残量警告色(全配色共通) */
val BatteryOrange = Color(0xFFFF8C00)
val BatteryRed = Color(0xFFFF3B30)

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
        when (def?.metric) {
            "notif" -> emit(ModuleEvent.OpenNotifAccess)
            // 文字表示(WORK)は全体のタップでスイッチ。ブロック表示は右マスだけ(MeterModule の中)
            "wifi" -> if (def.style == "text") emit(ModuleEvent.OpenIntent(com.snsdpen.launcher.data.wifiPanelIntent()))
            "signal" -> if (def.style == "text") emit(ModuleEvent.OpenIntent(com.snsdpen.launcher.data.internetPanelIntent()))
            "bluetooth" -> if (def.style == "text") emit(ModuleEvent.ToggleBluetooth)
        }
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
    "bluetooth" to "BT",
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
    val paletteShade = blocks[def.shade.mod(blocks.size)]
    val empty = p.blockEmpty ?: p.fg.copy(alpha = 0.15f)
    // 表示中(RESUMED)だけ読む。裏に回ると止まる
    var metric by androidx.compose.runtime.remember(def.metric) { androidx.compose.runtime.mutableStateOf<Metric?>(null) }
    // 電池だけは充電ケーブルの抜き差しに即応する(残量か充電状態が変わった時だけ読み直す)
    var batteryKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    if (def.metric == "battery") androidx.compose.runtime.DisposableEffect(context) {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                if (i == null) return
                batteryKey = "${i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)}/${i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)}"
            }
        }
        context.registerReceiver(r, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(r) } }
    }
    WhileResumed(def.metric, tick, batteryKey) {
        if (def.metric == "blank" || def.metric == "notif") return@WhileResumed
        while (true) {
            metric = withContext(Dispatchers.IO) {
                when (def.metric) {
                    "battery" -> readBattery(context)
                    "memory" -> readMemory(context)
                    "storage" -> readStorage()
                    "wifi" -> readWifi(context)
                    "signal" -> readSignal(context)
                    "bluetooth" -> readBluetooth(context)
                    else -> null
                }
            }
            delay(if (def.metric == "wifi" || def.metric == "signal" || def.metric == "bluetooth") 10_000 else 30_000)
        }
    }
    // 電池は全配色で固定: 蛍光グリーン、充電中は黄
    val charging = def.metric == "battery" && metric?.note == "CHG"
    val shade = when {
        def.metric == "battery" && charging -> com.snsdpen.launcher.ui.chargeColor(p)
        def.metric == "battery" -> com.snsdpen.launcher.ui.batteryColor(p)
        def.metric in com.snsdpen.launcher.ui.MeterFixedColors -> com.snsdpen.launcher.ui.meterColor(p, def.metric)
        else -> paletteShade
    }
    val ink = if (shade.luminance() > 0.4f) InkDark else InkLight
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
    val ntfBlink = com.snsdpen.launcher.ui.blinkAlpha(def.metric == "notif" && scope.notifCounts.values.sum() > 0)

    // 文字表示(WORK 向け): 数字と ON/OFF を薄い地の上に
    if (def.style == "text") {
        val m = metric
        val note = m?.note.orEmpty()
        val value = when (def.metric) {
            "battery" -> if (m == null) "—" else "${m.percent}%" + if (note == "CHG") "⚡" else ""
            "wifi", "signal" -> if (m == null) "—" else if (note == "OFF") "OFF" else "${Math.round(m.percent / 25f)}/4"
            "bluetooth" -> if (m == null) "—" else when (note) { "OFF" -> "OFF"; "LINK" -> "LINK"; else -> "ON" }
            "memory", "storage" -> if (m == null) "—" else "${m.percent}%"
            "notif" -> "${scope.notifCounts.values.sum()}"
            else -> ""
        }
        val off = note == "OFF" || m == null
        Box(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(label, color = p.fgDim, fontSize = 7.sp, lineHeight = 8.sp, modifier = Modifier.align(Alignment.TopStart))
            Text(
                value,
                color = if (off) p.fgDim else if (def.metric == "battery") shade else p.fg,
                fontSize = 12.sp, lineHeight = 13.sp,
                modifier = Modifier.align(Alignment.BottomStart).alpha(if (def.metric == "notif") ntfBlink else 1f),
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        return
    }

    // 2 マスの BAT / WIFI / SIG / BT は左右 2 つの独立したマス
    //   BAT: 51% 以上で 2 つ点灯、50% 以下で左だけ。20% 以下はオレンジ、10% 以下は赤(充電中は黄)
    //   WIFI / SIG / BT: 左が強さ(濃さ)、右がスイッチ(タップでシステムのパネル/ダイアログ)
    val split = total == 2 && def.metric in setOf("battery", "wifi", "signal", "bluetooth")
    if (split) {
        val pct = metric?.percent ?: 0
        val note = metric?.note.orEmpty()
        val off = note == "OFF" || metric == null
        val batShade = when {
            charging -> shade
            pct <= 10 -> BatteryRed
            pct <= 20 -> BatteryOrange
            else -> shade
        }
        val emit = scope.emit
        val action: (() -> Unit)? = when (def.metric) {
            "wifi" -> { { emit(ModuleEvent.OpenIntent(com.snsdpen.launcher.data.wifiPanelIntent())) } }
            "signal" -> { { emit(ModuleEvent.OpenIntent(com.snsdpen.launcher.data.internetPanelIntent())) } }
            "bluetooth" -> { { emit(ModuleEvent.ToggleBluetooth) } }
            else -> null
        }
        val horizontal = cols == 2
        @Composable
        fun cellLeft(m: Modifier) {
            if (def.metric == "battery") {
                val on = metric != null
                Box(m.background(if (on) batShade else empty), contentAlignment = Alignment.BottomStart) {
                    Text(if (note == "CHG") "$label·CHG" else label, color = if (on) (if (batShade.luminance() > 0.4f) InkDark else InkLight) else p.fgDim, fontSize = 7.sp, lineHeight = 8.sp, modifier = Modifier.padding(2.dp))
                }
            } else {
                val a = if (off) 0f else 0.12f + 0.88f * (pct / 100f)
                Box(m.background(if (off) empty else shade.copy(alpha = a)), contentAlignment = Alignment.BottomStart) {
                    Text(label, color = if (!off && pct >= 50) ink else p.fgDim, fontSize = 7.sp, lineHeight = 8.sp, modifier = Modifier.padding(2.dp))
                }
            }
        }
        @Composable
        fun cellRight(m: Modifier) {
            if (def.metric == "battery") {
                val on = metric != null && pct > 50
                Box(m.background(if (on) batShade else empty)) {}
            } else {
                val on = !off
                val tap = if (action != null && !scope.editMode) Modifier.clickable(
                    interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null, onClick = action,
                ) else Modifier
                // 文字は左マスのラベルと同じ位置・大きさ(左下 7sp)。ON / OFF / DATA が並んでも段が揃う
                Box(m.then(tap).background(if (on) shade else empty), contentAlignment = Alignment.BottomStart) {
                    Text(
                        if (def.metric == "signal") "DATA" else if (on) "ON" else "OFF",
                        color = if (on) ink else p.fgDim, fontSize = 7.sp, lineHeight = 8.sp, modifier = Modifier.padding(2.dp),
                    )
                }
            }
        }
        if (horizontal) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                cellLeft(Modifier.weight(1f).fillMaxSize()); cellRight(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GridGap)) {
                cellLeft(Modifier.weight(1f).fillMaxWidth()); cellRight(Modifier.weight(1f).fillMaxWidth())
            }
        }
        return
    }

    // 2 マス以下の割合計測は「1 個のブロックの濃さ」で表す(主張を弱める)。3 マス以上はブロック数
    val compact = total <= 2 && def.metric != "blank" && def.metric != "notif"
    if (compact) {
        val pct = metric?.percent ?: 0
        val a = 0.12f + 0.88f * (pct / 100f)
        val note = metric?.note.orEmpty()
        Box(Modifier.fillMaxSize().background(shade.copy(alpha = a)), contentAlignment = Alignment.BottomStart) {
            Text(
                if (note == "OFF") "$label·OFF" else if (note == "CHG") "$label·CHG" else label,
                color = if (pct >= 50) ink else p.fgDim,
                fontSize = 7.sp, lineHeight = 8.sp, modifier = Modifier.padding(2.dp),
            )
        }
        return
    }

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
                            .background(if (on) shade.copy(alpha = if (def.metric == "notif") ntfBlink else 1f) else empty),
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
