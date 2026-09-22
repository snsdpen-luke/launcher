package com.snsdpen.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.data.domainOf
import com.snsdpen.launcher.model.BoardDef
import com.snsdpen.launcher.model.LinkDef

/** ボードの編集(名前・リンクの追加/修正/削除・ボード削除)。全画面 */
@Composable
fun BoardEditSheet(
    board: BoardDef,
    links: List<LinkDef>,
    onRename: (String) -> Unit,
    onUpsertLink: (LinkDef) -> Unit,
    onDeleteLink: (String) -> Unit,
    onDeleteBoard: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val p = LocalPalette.current
    var editing by remember { mutableStateOf<LinkDef?>(null) }   // 編集中(id 空 = 新規)
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("BOARD", color = p.fgDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextLink("CLOSE", p.fg, onClose)
        }
        Spacer(Modifier.height(8.dp))
        var name by remember(board.id) { mutableStateOf(board.name) }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; onRename(it) },
            label = { Text("NAME") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("LINKS  ${links.size}", color = p.fgDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextLink("+ ADD LINK", p.accent) { editing = LinkDef("", "", "") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(links, key = { it.id }) { link ->
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(link.name, color = p.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(domainOf(link.url), color = p.fgDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextLink("EDIT", p.fgDim) { editing = link }
                    TextLink("✕", p.fgDim) { onDeleteLink(link.id) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            TextLink("DELETE BOARD", Color(0xFFE05A5A)) { confirmDelete = true }
        }
    }

    editing?.let { link ->
        LinkDialog(
            initial = link,
            onSave = { onUpsertLink(it); editing = null },
            onCancel = { editing = null },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("DELETE BOARD?") },
            text = { Text("${board.name} とリンク ${links.size} 件を消す。戻せない。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteBoard() }) { Text("DELETE") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("CANCEL") } },
        )
    }
}

@Composable
fun LinkDialog(initial: LinkDef, onSave: (LinkDef) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    val valid = url.isNotBlank()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (initial.id.isBlank()) "ADD LINK" else "EDIT LINK") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("NAME") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val n = name.trim().ifBlank { domainOf(url.trim()) }
                    onSave(initial.copy(name = n, url = url.trim()))
                },
            ) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("CANCEL") } },
    )
}

/** 文字だけのボタン。clickable の後ろに padding を付けてタップ領域を確保 */
@Composable
fun TextLink(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 12.sp,
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
