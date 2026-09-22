package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.model.Face
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.ui.LocalPalette
import java.util.Locale

/** 見出しだけの行(グループ分け)。配色が labelChips を持てば色チップになる。編集で文言を変える */
val LabelSpec = ModuleSpec(
    kind = "label",
    name = "LABEL",
    singleton = false,
    defaultSize = { face -> if (face == Face.COVER) Span(8, 1) else Span(6, 1) },
    minSize = { Span(1, 1) },
    exists = { layout, id -> layout.labels.any { it.id == id } },
    onEdit = { scope, emit -> scope.id?.let { emit(ModuleEvent.EditLabel(it)) } },
    content = { LabelModule(it) },
)

@Composable
private fun LabelModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val def = scope.layout.labels.firstOrNull { it.id == scope.id } ?: return
    val text = def.text.uppercase(Locale.ROOT)
    if (p.labelChips.isNotEmpty()) {
        // 色チップ: ラベルの並び順で色を回す(隣同士が同じ色にならない)。文字は色の明るさで黒か白
        val index = scope.layout.labels.indexOfFirst { it.id == def.id }.coerceAtLeast(0)
        val chip = p.labelChips[index % p.labelChips.size]
        // 床のブロックと同じ形: 高さは 1 マス、幅は文字に合わせてマス単位に切り上げ(目地に揃う)。色は差し色を薄く
        val style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val textW = with(density) { measurer.measure(text, style, maxLines = 1).size.width.toDp() }
        val stride = scope.cell + com.snsdpen.launcher.ui.GridGap
        val cells = if (stride > 0.dp) kotlin.math.ceil(((textW + 12.dp) / stride).toDouble()).toInt().coerceIn(1, scope.span.w.coerceAtLeast(1)) else 1
        val w = scope.cell * cells + com.snsdpen.launcher.ui.GridGap * (cells - 1)
        // 1 マスずつ独立したブロックにして目地を見せる(床のタイルと同じ並び)。文字はその上に乗せる
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            androidx.compose.foundation.layout.Row(
                Modifier.width(w).fillMaxHeight(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(com.snsdpen.launcher.ui.GridGap),
            ) {
                repeat(cells) { Box(Modifier.width(scope.cell).fillMaxHeight().background(chip.copy(alpha = 0.4f))) }
            }
            Box(Modifier.width(w).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                Text(
                    text,
                    color = p.fg,
                    style = style,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    } else {
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.BottomStart) {
            Text(
                text,
                color = p.fgDim,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}
