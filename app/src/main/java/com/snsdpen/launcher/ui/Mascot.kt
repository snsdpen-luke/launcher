package com.snsdpen.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Claude Code のマスコット(ドット絵)。胴体・両脇の腕・目 2 つ・脚 4 本を四角で描く。
 * 自前で描くので色は配色に合わせられる: 本体は [body]、目は [body] と [ink] の暗い方。
 * 空きマスを歩く演出に使う。1 マス(≈30dp)の中に収まる寸法。
 */
@Composable
fun ClaudeMark(body: Color, ink: Color, modifier: Modifier = Modifier) {
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
        rect(body, 0f, 2.5f, 1.4f, 1.6f)        // 左腕
        rect(body, 9.4f, 2.5f, 1.4f, 1.6f)      // 右腕
        rect(eye, 2.7f, 1.4f, 0.8f, 1.2f)       // 左目
        rect(eye, 7.2f, 1.4f, 0.8f, 1.2f)       // 右目
        for (x in floatArrayOf(2.1f, 3.7f, 6.4f, 8.0f)) rect(body, x, 4.5f, 0.8f, 2.2f)   // 脚 4 本
    }
}
