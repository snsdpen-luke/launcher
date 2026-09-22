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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
        // メイン(開いた時)の左パネルと右側からは、右側に窓で開く。カバーは全画面
        val inPane = scope.face == com.snsdpen.launcher.model.Face.MAIN || scope.face == com.snsdpen.launcher.model.Face.SIDE || com.snsdpen.launcher.ui.POPUP_DEBUG
        if (app != null) emit(if (inPane) ModuleEvent.LaunchInPane(app) else ModuleEvent.Launch(app))
        else scope.id?.let { emit(ModuleEvent.EditPanel(it)) }
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
    // アイコンは 1 マスいっぱい(床のブロックと同じ大きさ)。名前は小さく、アイコンの下辺に揃える(メリハリ)
    Row(
        Modifier.fillMaxSize().padding(end = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val n0 = app?.let { scope.notifCounts[it.packageName] } ?: 0
        val notified = n0 > 0 || com.snsdpen.launcher.ui.BLINK_DEBUG
        val blink = com.snsdpen.launcher.ui.blinkAlpha(notified)
        // 未読があるときはアイコンの後ろの 1 マスがオレンジで明滅する(アイコン自体は明滅しない)
        Box(Modifier.size(scope.cell), contentAlignment = Alignment.Center) {
            if (notified) Box(Modifier.fillMaxSize().background(com.snsdpen.launcher.ui.NotifOrange.copy(alpha = blink)))
            if (app != null) AppIcon(app = app, size = scope.cell) else Marker(p.fgDim)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            color = if (app != null) p.fg else p.fgDim,
            fontSize = 11.sp, lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(bottom = 1.dp),
        )
        if (app == null) Text("MISSING", color = p.fgDim, fontSize = 9.sp)
        val n = app?.let { scope.notifCounts[it.packageName] } ?: 0
        if (n > 0) Text("$n", color = p.accent, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
