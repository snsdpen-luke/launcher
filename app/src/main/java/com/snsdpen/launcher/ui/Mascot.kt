package com.snsdpen.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** マスコットの姿勢。歩く・寝る・踊る・食べるで描き分ける */
data class MascotPose(
    val eyesClosed: Boolean = false,   // 寝ている(目が横線)
    val mouthOpen: Boolean = false,    // 食べている
    val armsUp: Boolean = false,       // 喜んでいる(腕が上)
    val step: Int = 0,                 // 脚の左右(0 / 1)
    val happyEyes: Boolean = false,    // 喜んでいる(目が ^ ^)
)

/**
 * Claude Code のマスコット(ドット絵)。胴体・両脇の腕・目 2 つ・脚 4 本を四角で描く。
 * 自前で描くので色は配色に合わせられる: 本体は [body]、目は [body] と [ink] の暗い方。
 * 空きマスを歩く演出に使う。1 マス(≈30dp)の中に収まる寸法。
 */
@Composable
fun ClaudeMark(body: Color, ink: Color, modifier: Modifier = Modifier, pose: MascotPose = MascotPose()) {
    val eye = if (ink.luminance() < body.luminance()) ink else Color(0xFF111111)
    Canvas(modifier) {
        val s = size.minDimension
        val u = s / 12f                      // 1 ドット
        // 全体 10.8u x 6.7u をマスの中央に
        val ox = (size.width - 10.8f * u) / 2f
        val oy = (size.height - 6.7f * u) / 2f
        fun rect(c: Color, x: Float, y: Float, w: Float, h: Float) =
            drawRect(c, topLeft = Offset(ox + x * u, oy + y * u), size = Size(w * u, h * u))
        rect(body, 1.4f, 0f, 8f, 4.5f)          // 胴体
        if (pose.armsUp) {
            rect(body, 0f, 0.4f, 1.4f, 1.6f)    // 左腕(上げる)
            rect(body, 9.4f, 0.4f, 1.4f, 1.6f)  // 右腕(上げる)
        } else {
            rect(body, 0f, 2.5f, 1.4f, 1.6f)    // 左腕
            rect(body, 9.4f, 2.5f, 1.4f, 1.6f)  // 右腕
        }
        when {
            pose.eyesClosed -> {                // 閉じた目: 横線
                rect(eye, 2.4f, 2.1f, 1.4f, 0.4f)
                rect(eye, 6.9f, 2.1f, 1.4f, 0.4f)
            }
            pose.happyEyes -> {                 // 喜びの目: ^ ^
                rect(eye, 2.4f, 1.9f, 0.5f, 0.5f); rect(eye, 2.85f, 1.45f, 0.5f, 0.5f); rect(eye, 3.3f, 1.9f, 0.5f, 0.5f)
                rect(eye, 6.9f, 1.9f, 0.5f, 0.5f); rect(eye, 7.35f, 1.45f, 0.5f, 0.5f); rect(eye, 7.8f, 1.9f, 0.5f, 0.5f)
            }
            else -> {
                rect(eye, 2.7f, 1.4f, 0.8f, 1.2f)   // 左目
                rect(eye, 7.2f, 1.4f, 0.8f, 1.2f)   // 右目
            }
        }
        if (pose.mouthOpen) rect(eye, 4.6f, 3.0f, 1.6f, 0.9f)   // もぐもぐ
        // 脚 4 本。歩く・踊る時は 1 本おきに少し短く(交互に上がる)
        floatArrayOf(2.1f, 3.7f, 6.4f, 8.0f).forEachIndexed { i, x ->
            val lifted = (i % 2) == pose.step % 2 && pose.step >= 0
            rect(body, x, 4.5f, 0.8f, if (lifted) 1.6f else 2.2f)
        }
    }
}

/** ドット絵のハート(喜んでいる印) */
@Composable
fun PixelHeart(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 7f
        val dots = listOf(
            "0110110",
            "1111111",
            "1111111",
            "0111110",
            "0011100",
            "0001000",
        )
        dots.forEachIndexed { r, line -> line.forEachIndexed { c, ch -> if (ch == '1') drawRect(color, Offset(c * u, r * u), Size(u, u)) } }
    }
}

/** ごはんのブロック(おにぎり)。[left] 3 → 0 で、マスコットのいる左側から 1 口ずつ減る */
@Composable
fun FoodBlock(left: Int, modifier: Modifier = Modifier) {
    if (left <= 0) return
    Canvas(modifier) {
        val u = size.minDimension / 7f
        val rice = Color(0xFFF7F4EA)
        val nori = Color(0xFF1E2A26)
        val shape = listOf(
            "0001000",
            "0011100",
            "0111110",
            "1111111",
            "1122211",
            "1122211",
        )
        // 3 → 全部、2 → 左 2 列を食べた、1 → 左 4 列を食べた
        val minCol = when (left) { 3 -> 0; 2 -> 2; else -> 4 }
        shape.forEachIndexed { r, line -> line.forEachIndexed { c, ch ->
            if (ch == '0' || c < minCol) return@forEachIndexed
            drawRect(if (ch == '2') nori else rice, Offset(c * u, (r + 1) * u), Size(u, u))
        } }
    }
}
