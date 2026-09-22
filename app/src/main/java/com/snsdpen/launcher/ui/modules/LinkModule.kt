package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.data.domainOf
import com.snsdpen.launcher.model.Face
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.Marker

/** リンク 1 本を画面に直接置く 1 行。タップで開く。編集で名前と URL を直す */
val LinkSpec = ModuleSpec(
    kind = "link",
    name = "LINK",
    singleton = false,
    defaultSize = { face -> if (face == Face.COVER) Span(8, 1) else Span(6, 1) },
    minSize = { Span(3, 1) },
    exists = { layout, id -> layout.links.any { it.id == id } },
    onTap = { scope, emit ->
        scope.layout.links.firstOrNull { it.id == scope.id }?.let { emit(ModuleEvent.OpenLink(it.url)) }
    },
    onEdit = { scope, emit -> scope.id?.let { emit(ModuleEvent.EditLink(it)) } },
    content = { LinkModule(it) },
)

@Composable
private fun LinkModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val def = scope.layout.links.firstOrNull { it.id == scope.id } ?: return
    // アプリの行と同じ形: 1 マスのアイコン(鎖の輪)+ 小さい名前をアイコンの縦中央に揃える。ドメインは出さない(名前が空の時だけ)
    Row(
        Modifier.fillMaxSize().padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkIcon(size = scope.cell, tile = p.fg.copy(alpha = 0.08f), ink = p.fg)
        Spacer(Modifier.width(6.dp))
        Text(
            def.name.ifBlank { domainOf(def.url) },
            color = p.fg,
            fontSize = 11.sp, lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}


/**
 * リンクのアイコン(自前)。アプリのアイコンと同じ大きさの角丸の板に、鎖の輪 2 つを斜めに描く。
 * 色は配色の文字色(WORK ならモノクロの紙面になじむ)
 */
@Composable
fun LinkIcon(size: androidx.compose.ui.unit.Dp, tile: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(tile, cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f))
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.085f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // 輪 2 つ: 45 度に傾けた角丸の長方形を、中心をずらして重ねる
        rotate(-45f) {
            val w = s * 0.34f; val h = s * 0.20f
            val cx = s / 2f; val cy = s / 2f
            val r = androidx.compose.ui.geometry.CornerRadius(h / 2f)
            drawRoundRect(ink, topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.78f, cy - h / 2f), size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = r, style = stroke)
            drawRoundRect(ink, topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.22f, cy - h / 2f), size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = r, style = stroke)
        }
    }
}
