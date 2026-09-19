package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** 見出しだけの行(グループ分け)。タップは何もしない、編集で文言を変える */
val LabelSpec = ModuleSpec(
    kind = "label",
    name = "LABEL",
    singleton = false,
    defaultSize = { face -> if (face == Face.COVER) Span(16, 1) else Span(12, 1) },
    minSize = { Span(1, 1) },
    exists = { layout, id -> layout.labels.any { it.id == id } },
    onEdit = { scope, emit -> scope.id?.let { emit(ModuleEvent.EditLabel(it)) } },
    content = { LabelModule(it) },
)

@Composable
private fun LabelModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val def = scope.layout.labels.firstOrNull { it.id == scope.id } ?: return
    Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.BottomStart) {
        Text(
            def.text.uppercase(Locale.ROOT),
            color = p.fgDim,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}
