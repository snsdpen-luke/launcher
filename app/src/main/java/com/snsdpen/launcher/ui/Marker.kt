package com.snsdpen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 文字の左に置く小さな正方形(メモ帳の行頭マーク)。filled=false で中抜き */
@Composable
fun Marker(color: Color, size: Dp = 8.dp, filled: Boolean = true, modifier: Modifier = Modifier) {
    Box(modifier.size(size).then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color)))
}
