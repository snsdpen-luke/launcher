package com.snsdpen.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.data.Torch
import com.snsdpen.launcher.data.canWriteSettings
import com.snsdpen.launcher.data.readBrightness
import com.snsdpen.launcher.data.readVolume
import com.snsdpen.launcher.data.setBrightness
import com.snsdpen.launcher.data.setVolume
import com.snsdpen.launcher.data.writeSettingsIntent
import kotlinx.coroutines.delay

private val LightShade = Color(0xFFFFF1B8)   // 白っぽい黄(ライト)
private val BrtShade = Color(0xFFFFB020)     // 琥珀(明るさ。DRIVE と同じ)
private val VolShade = Color(0xFFFF6A3D)     // 橙赤(音量。DRIVE と同じ)

/**
 * 隠しパネル: 背面ライト・画面の明るさ・音量をブロックの段で。フッター右上の薄い印から開く。
 * 右下の予約席の隠しボタンから開く。縦の 3 本で、縦になぞって調整。外を押す / 戻る で閉じる。閉じてもライトは点いたまま(消すのは LIGHT の OFF)
 */
@Composable
fun QuickPanel(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val p = LocalPalette.current
    val context = LocalContext.current
    val torchMax = remember { Torch.maxLevel(context) }
    var torch by remember { mutableIntStateOf(0) }
    // 外(通知パネル等)で点けた・消した・段を変えたのも拾う
    androidx.compose.runtime.DisposableEffect(Unit) {
        val m = context.getSystemService(android.content.Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
        val id = Torch.cameraId(context)
        val cb = object : android.hardware.camera2.CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId != id) return
                torch = if (!enabled) 0 else if (torch == 0) runCatching { if (android.os.Build.VERSION.SDK_INT >= 33) m!!.getTorchStrengthLevel(cameraId) else 1 }.getOrDefault(1) else torch
            }
            override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
                if (cameraId == id && torch != 0) torch = newStrengthLevel
            }
        }
        runCatching { m?.registerTorchCallback(cb, android.os.Handler(android.os.Looper.getMainLooper())) }
        onDispose { runCatching { m?.unregisterTorchCallback(cb) } }
    }
    var brt by remember { mutableStateOf(readBrightness(context)) }
    var vol by remember { mutableStateOf(readVolume(context)) }
    // 外(音量キー等)で変わった分を 1 秒ごとに拾う(開いている間だけ)
    WhileResumed(Unit) {
        while (true) { delay(1000); vol = readVolume(context); if (canWriteSettings(context)) brt = readBrightness(context) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // 右下(隠しボタンの上)に縦の 3 本。中の空き所を押しても閉じない
        Row(
            Modifier
                .padding(end = 12.dp, bottom = 84.dp)
                .background(p.bg)
                .border(1.dp, p.line)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BlockSliderColumn("LIGHT", LightShade, levels = torchMax, current = torch, withOff = true) { n ->
                torch = n; Torch.set(context, n)
            }
            BlockSliderColumn("BRT", BrtShade, levels = 12, current = Math.round(brt * 12)) { n ->
                val v = n / 12f
                if (setBrightness(context, v)) brt = v
                else runCatching { context.startActivity(writeSettingsIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            BlockSliderColumn("VOL", VolShade, levels = 12, current = Math.round(vol * 12)) { n ->
                vol = n / 12f; setVolume(context, vol)
            }
        }
    }
}

private val ColumnW = 52.dp
private val ColumnH = 300.dp

/**
 * 縦のブロックの段 1 本。下から上へ [levels] 個(withOff なら一番下が OFF)、下にラベル。
 * 押した段に一発、縦になぞれば連続。点いたブロックは下が暗く上が明るい
 */
@Composable
private fun BlockSliderColumn(label: String, shade: Color, levels: Int, current: Int, withOff: Boolean = false, onSet: (Int) -> Unit) {
    val p = LocalPalette.current
    val n = levels.coerceAtLeast(1)
    val cells = n + if (withOff) 1 else 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(ColumnW).height(ColumnH)) {
            // y(上から)→ 段。下端が 0 番
            fun pick(y: Float, h: Float): Int {
                if (h <= 0f) return current
                val i = ((1f - y / h) * cells).toInt().coerceIn(0, cells - 1)
                return if (withOff) i else i + 1
            }
            var last by remember { mutableIntStateOf(-1) }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(cells) { detectTapGestures { o -> onSet(pick(o.y, size.height.toFloat())) } }
                    .pointerInput(cells) {
                        detectVerticalDragGestures(onDragStart = { o -> last = pick(o.y, size.height.toFloat()); onSet(last) }) { c, _ ->
                            c.consume()
                            val v = pick(c.position.y, size.height.toFloat())
                            if (v != last) { last = v; onSet(v) }
                        }
                    },
            ) {
                val g = GridGap.toPx()
                val ch = (size.height - g * (cells - 1)) / cells
                val empty = p.blockEmpty ?: p.fg.copy(alpha = 0.12f)
                for (i in 0 until cells) {
                    val level = if (withOff) i else i + 1
                    val color = when {
                        withOff && i == 0 -> if (current == 0) p.fg.copy(alpha = 0.55f) else empty
                        level <= current -> lerp(lerp(shade, Color.Black, 0.35f), shade, (level - 1f) / (n - 1).coerceAtLeast(1))
                        else -> empty
                    }
                    val top = size.height - (i + 1) * ch - i * g
                    drawRect(color, topLeft = Offset(0f, top), size = Size(size.width, ch))
                }
            }
            if (withOff) Text(
                "OFF",
                color = if (current == 0) p.bg else p.fgDim,
                fontSize = 8.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = p.fgDim, fontSize = 10.sp)
    }
}

/** 隠しボタンの印: つまみの付いた縦線 3 本(薄く) */
@Composable
fun SlidersGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.width(size).height(size)) {
        val w = this.size.width; val h = this.size.height
        val t = w * 0.1f
        val knobs = floatArrayOf(0.62f, 0.30f, 0.50f)
        for (i in 0 until 3) {
            val x = w * (0.2f + i * 0.3f)
            drawRect(color, topLeft = Offset(x - t / 2f, 0f), size = Size(t, h))
            drawRect(color, topLeft = Offset(x - t * 1.6f, h * knobs[i] - t), size = Size(t * 3.2f, t * 2f))
        }
    }
}
