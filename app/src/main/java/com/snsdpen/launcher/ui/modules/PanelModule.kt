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
import com.snsdpen.launcher.data.AppIcon
import com.snsdpen.launcher.model.Face
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.model.appKey
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.Marker

/** アプリ 1 個を直接起動する 1 行。アイコン + 名前(別名があればそれ) */
val PanelSpec = ModuleSpec(
    kind = "panel",
    name = "APP",
    singleton = false,
    defaultSize = { face -> if (face == Face.COVER) Span(8, 2) else Span(6, 2) },
    minSize = { Span(3, 1) },
    exists = { layout, id -> layout.panels.any { it.id == id } },
    onTap = { scope, emit ->
        val def = scope.layout.panels.firstOrNull { it.id == scope.id }
        val app = def?.let { d -> scope.apps.firstOrNull { appKey(it) == d.app } }
        if (app != null) emit(ModuleEvent.Launch(app)) else scope.id?.let { emit(ModuleEvent.EditPanel(it)) }
    },
    onEdit = { scope, emit -> scope.id?.let { emit(ModuleEvent.EditPanel(it)) } },
    content = { PanelModule(it) },
)

@Composable
private fun PanelModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val def = scope.layout.panels.firstOrNull { it.id == scope.id } ?: return
    val app = scope.apps.firstOrNull { appKey(it) == def.app }
    val name = def.name.ifBlank { app?.label ?: def.app.substringBefore('/').substringAfterLast('.') }
    Row(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null) AppIcon(app = app, size = 24.dp) else Marker(p.fgDim)
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            color = if (app != null) p.fg else p.fgDim,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (app == null) Text("MISSING", color = p.fgDim, fontSize = 9.sp)
        val n = app?.let { scope.notifCounts[it.packageName] } ?: 0
        if (n > 0) Text("$n", color = p.accent, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
