package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.snsdpen.launcher.model.linksOf
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.Marker

/** リンク・ボード: 見出し + 1 行 1 リンク。高さを伸ばすと行が増える */
val BoardSpec = ModuleSpec(
    kind = "board",
    name = "BOARD",
    singleton = false,
    defaultSize = { face -> if (face == Face.COVER) Span(8, 10) else Span(8, 8) },
    minSize = { Span(4, 2) },
    exists = { layout, id -> layout.boards.any { it.id == id } },
    // 空のボードは枠タップで編集へ。リンクがあれば行が受けるので枠は何もしない
    onTap = { scope, emit ->
        val b = scope.layout.boards.firstOrNull { it.id == scope.id }
        if (b != null && b.links.isEmpty()) emit(ModuleEvent.EditBoard(b.id))
    },
    onEdit = { scope, emit -> scope.id?.let { emit(ModuleEvent.EditBoard(it)) } },
    content = { BoardModule(it) },
)

private val HeaderH = 28.dp
private val RowH = 44.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val board = scope.layout.boards.firstOrNull { it.id == scope.id } ?: return
    val links = scope.layout.linksOf(board)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxH = maxHeight
        val rows = ((maxH - HeaderH) / RowH).toInt().coerceAtLeast(0)
        val shown = links.take(rows)
        val overflow = links.size - shown.size

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(HeaderH).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Marker(p.fg)
                Spacer(Modifier.width(10.dp))
                Text(board.name, color = p.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (overflow > 0) Text("+$overflow", color = p.fgDim, fontSize = 10.sp)
            }
            if (links.isEmpty()) {
                Text(
                    if (scope.editMode) "" else "TAP TO ADD",
                    color = p.fgDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 26.dp, top = 6.dp),
                )
            }
            shown.forEach { link ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(RowH)
                        // 編集モードでは clickable ごと外す(枠の選択が効くように)
                        .then(
                            if (scope.editMode) Modifier
                            else Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { scope.emit(ModuleEvent.OpenLink(link.url)) },
                                onLongClick = { scope.emit(ModuleEvent.EnterEdit) },
                            )
                        )
                        .padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Marker(p.fgDim, size = 4.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        link.name,
                        color = p.fg,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        domainOf(link.url),
                        color = p.fgDim,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}
