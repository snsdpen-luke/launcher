package com.snsdpen.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.data.AppEntry
import com.snsdpen.launcher.data.AppIcon
import com.snsdpen.launcher.model.LabelDef
import com.snsdpen.launcher.model.PanelDef

/** 下から出る小さなシートの器。外側タップと戻るで閉じる */
@Composable
private fun BottomSheetFrame(onClose: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onClose)
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(p.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) { content() }
    }
}

@Composable
private fun SheetRow(label: String, hint: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marker(p.fg)
        Spacer(Modifier.width(10.dp))
        Text(label, color = p.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(hint, color = p.fgDim, fontSize = 10.sp)
    }
}

/** 何を足すか */
@Composable
fun AddSheet(onApp: () -> Unit, onLabel: () -> Unit, onTile: () -> Unit, onBoard: () -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    BottomSheetFrame(onClose) {
        Text("ADD", color = p.fgDim, fontSize = 11.sp)
        SheetRow("APP", "アプリを 1 個", onApp)
        SheetRow("LABEL", "見出しの行", onLabel)
        SheetRow("METER", "床のタイル(ブロック数で表す)", onTile)
        SheetRow("BOARD", "リンクの束", onBoard)
    }
}

/** 床のタイルに出す計測値を選ぶ */
@Composable
fun MeterPickerSheet(onPick: (String) -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    BottomSheetFrame(onClose) {
        Text("METER", color = p.fgDim, fontSize = 11.sp)
        SheetRow("BATTERY", "残量(テラコッタ)", { onPick("battery") })
        SheetRow("MEMORY", "使用中(ベージュ)", { onPick("memory") })
        SheetRow("STORAGE", "使用中(アンバー)", { onPick("storage") })
        SheetRow("WIFI", "Wi-Fi の強さ", { onPick("wifi") })
        SheetRow("SIGNAL", "電波の強さ", { onPick("signal") })
        SheetRow("NOTIF", "未読の通知の数", { onPick("notif") })
        SheetRow("BLANK", "全部埋め(飾り)", { onPick("blank") })
    }
}

/** アプリを選ぶ(検索付き) */
@Composable
fun AppPickerSheet(apps: List<AppEntry>, onPick: (AppEntry) -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("PICK APP", color = p.fgDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextLink("CLOSE", p.fg, onClose)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("SEARCH") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { "${it.packageName}/${it.activityName}" }) { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(app) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app = app, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = p.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, color = p.fgDim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** パネルの編集: 別名と、アプリの差し替え */
@Composable
fun PanelEditSheet(panel: PanelDef, app: AppEntry?, onRename: (String) -> Unit, onChangeApp: () -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    var name by remember(panel.id) { mutableStateOf(panel.name) }
    BottomSheetFrame(onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("APP", color = p.fgDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextLink("CLOSE", p.fg, onClose)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onChangeApp)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app != null) AppIcon(app = app, size = 32.dp) else Marker(p.fgDim)
            Spacer(Modifier.width(12.dp))
            Text(app?.label ?: "(missing)", color = p.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("CHANGE", color = p.fgDim, fontSize = 11.sp)
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; onRename(it) },
            label = { Text("ALIAS (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** 見出しの文言 */
@Composable
fun LabelDialog(initial: LabelDef, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial.text) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (initial.id.isBlank()) "ADD LABEL" else "EDIT LABEL") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("CANCEL") } },
    )
}
