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
    Row(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marker(p.fgDim, size = 6.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            def.name.ifBlank { domainOf(def.url) },
            color = p.fg,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(domainOf(def.url), color = p.fgDim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
    }
}
