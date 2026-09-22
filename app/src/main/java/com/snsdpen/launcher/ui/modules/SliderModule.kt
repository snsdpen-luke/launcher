package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.snsdpen.launcher.data.readBrightness
import com.snsdpen.launcher.data.readVolume
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.ui.GridGap
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.WhileResumed
import com.snsdpen.launcher.ui.stableHash
import kotlinx.coroutines.delay

/**
 * ブロックのスライダー(DRIVE 用): 明るさ(slider:brightness)と音量(slider:volume)。
 * 1 マス 1 ブロック、左から点いている数が今の値。押した位置の段に一発、なぞれば連続。編集中は触れない
 */
val SliderSpec = ModuleSpec(
    kind = "slider",
    name = "SLIDER",
    singleton = false,
    defaultSize = { face -> if (face == com.snsdpen.launcher.model.Face.COVER) Span(16, 2) else Span(8, 2) },
    minSize = { Span(4, 1) },
    exists = { _, id -> id == "brightness" || id == "volume" },
    content = { SliderModule(it) },
)

private val BrightnessShade = Color(0xFFFFB020)   // 琥珀
private val VolumeShade = Color(0xFFFF6A3D)       // 橙寄りの赤(明るさの琥珀と見分けやすく)

@Composable
private fun SliderModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val isBrightness = scope.id == "brightness"
    val shade = if (isBrightness) BrightnessShade else VolumeShade
    val label = if (isBrightness) "BRT" else "VOL"
    // 今の値。表示中は 1 秒ごとに読み直す(音量キーなど外で変わった分)。触っている間は指の値
    var level by remember { mutableStateOf(0.5f) }
    var touching by remember { mutableStateOf(false) }
    WhileResumed(scope.id) {
        while (true) {
            if (!touching) level = if (isBrightness) readBrightness(context) else readVolume(context)
            delay(1000)
        }
    }
    val emit = scope.emit
    val cols = scope.span.w.coerceAtLeast(1)
    val rows = scope.span.h.coerceAtLeast(1)
    fun apply(x: Float, width: Float) {
        if (width <= 0f) return
        val v = (x / width).coerceIn(0f, 1f)
        // 押した所のブロックまで点ける(ブロック単位に丸める)
        val n = kotlin.math.ceil(v * cols - 0.0001f).toInt().coerceIn(0, cols)
        level = n.toFloat() / cols
        emit(if (isBrightness) ModuleEvent.SetBrightness(level) else ModuleEvent.SetVolume(level))
    }
    val gesture = if (!scope.editMode) Modifier
        .pointerInput(scope.id) { detectTapGestures { o -> apply(o.x, size.width.toFloat()) } }
        .pointerInput(scope.id) {
            var x = 0f
            detectHorizontalDragGestures(
                onDragStart = { o -> touching = true; x = o.x; apply(x, size.width.toFloat()) },
                onDragEnd = { touching = false },
                onDragCancel = { touching = false },
            ) { change, dx -> change.consume(); x += dx; apply(x, size.width.toFloat()) }
        }
    else Modifier
    Box(Modifier.fillMaxSize().then(gesture)) {
        Canvas(Modifier.fillMaxSize()) {
            val g = GridGap.toPx()
            val cw = (size.width - g * (cols - 1)) / cols
            val ch = (size.height - g * (rows - 1)) / rows
            val lit = Math.round(level * cols)
            val empty = p.blockEmpty ?: p.fg.copy(alpha = 0.15f)
            for (r in 0 until rows) for (c in 0 until cols) {
                val on = c < lit
                // 点いているブロックは左が暗く右が明るい(値が上がるほど明るく見える)。1 段の揺らぎで手貼りに
                val t = c.toFloat() / (cols - 1).coerceAtLeast(1)
                val jitter = ((stableHash(c, r, 11) % 5) - 2) * 0.02f
                val color = if (on) lerp(lerp(shade, Color.Black, 0.35f), shade, (t + jitter).coerceIn(0f, 1f)) else empty
                drawRect(color, topLeft = Offset(c * (cw + g), r * (ch + g)), size = Size(cw, ch))
            }
        }
        Text(label, color = p.fg.copy(alpha = 0.85f), fontSize = 7.sp, lineHeight = 8.sp, modifier = Modifier.align(Alignment.BottomStart).padding(3.dp))
    }
}
