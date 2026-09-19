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
import com.snsdpen.launcher.model.Face
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.Page
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.Marker

val FolderSpec = ModuleSpec(
    kind = "folder",
    name = "FOLDER",
    singleton = false,
    defaultSize = { face -> if (face == Face.COVER) Span(8, 2) else Span(6, 2) },
    minSize = { Span(3, 1) },
    exists = { layout, id -> layout.folders.any { it.id == id } },
    onTap = { scope, emit -> scope.id?.let { emit(ModuleEvent.OpenFolder(it)) } },
    content = { FolderModule(it) },
)

/** 枠なし。「■ NAME  件数」のメモ帳の 1 行 */
@Composable
private fun FolderModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val index = scope.layout.folders.indexOfFirst { it.id == scope.id }
    val def = scope.layout.folders.getOrNull(index) ?: return
    // 中身はアプリ一覧に実在するものだけ数える
    val keys = scope.apps.map { "${it.packageName}/${it.activityName}" }.toSet()
    val count = def.apps.count { it in keys }

    // DRIVE は行頭マークを系列色に(遠目に判別)。他は文字色
    val marker = if (scope.page == Page.DRIVE && p.series.isNotEmpty())
        p.series[index.coerceAtLeast(0) % p.series.size] else p.fg

    Row(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marker(marker)
        Spacer(Modifier.width(10.dp))
        Text(
            def.name,
            color = p.fg,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val n = scope.apps.filter { "${it.packageName}/${it.activityName}" in def.apps.toSet() }
            .sumOf { scope.notifCounts[it.packageName] ?: 0 }
        if (n > 0) Text("$n", color = p.accent, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
        Text("$count", color = p.fgDim, fontSize = 10.sp)
    }
}
