package com.snsdpen.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.model.TaskDef
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d EEE", Locale.ENGLISH)

fun parseIsoDate(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * タスクの追加/編集。追加は文字だけで即 ADD できる(ワンタッチ)。
 * DETAILS を開くと開始日・期限日(日付ピッカー)と関係者。
 * initial が非 null なら編集(DELETE あり)。
 */
@Composable
fun TaskDialog(
    initial: TaskDef?,
    onSave: (TaskDef) -> Unit,
    onDelete: (() -> Unit)? = null,
    onCancel: () -> Unit,
) {
    val editing = initial != null
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var people by remember { mutableStateOf(initial?.people.orEmpty()) }
    var start by remember { mutableStateOf(parseIsoDate(initial?.start)) }
    var due by remember { mutableStateOf(parseIsoDate(initial?.due)) }
    var details by remember { mutableStateOf(editing) }
    var picking by remember { mutableStateOf<String?>(null) }   // "start" / "due"
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!editing) focus.requestFocus() }

    fun submit() {
        if (text.isBlank()) { onCancel(); return }
        val base = initial ?: TaskDef(id = "", text = "")
        onSave(base.copy(text = text.trim(), people = people.trim(), start = start?.toString(), due = due?.toString()))
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (editing) "EDIT TASK" else "ADD TASK") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (!details) {
                    Text(
                        "+ DETAILS",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { details = true }
                            .padding(top = 10.dp, bottom = 4.dp, end = 12.dp),
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                    DateRow("START", start, onPick = { picking = "start" }, onClear = { start = null })
                    DateRow("DUE", due, onPick = { picking = "due" }, onClear = { due = null })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = people,
                        onValueChange = { people = it },
                        label = { Text("PEOPLE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = text.isNotBlank()) { Text(if (editing) "SAVE" else "ADD") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("DELETE") }
                TextButton(onClick = onCancel) { Text("CANCEL") }
            }
        },
    )

    picking?.let { which ->
        val current = if (which == "start") start else due
        DatePickerModal(
            initial = current ?: LocalDate.now(),
            onPick = { d -> if (which == "start") start = d else due = d; picking = null },
            onCancel = { picking = null },
        )
    }
}

@Composable
private fun DateRow(label: String, date: LocalDate?, onPick: () -> Unit, onClear: () -> Unit) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = dim, modifier = Modifier.padding(end = 12.dp))
        Text(
            date?.format(DateFmt)?.uppercase(Locale.ENGLISH) ?: "—",
            fontSize = 14.sp,
            modifier = Modifier
                .weight(1f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPick)
                .padding(vertical = 6.dp),
        )
        if (date != null) {
            Text(
                "✕",
                fontSize = 12.sp,
                color = dim,
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(initial: LocalDate, onPick: (LocalDate) -> Unit, onCancel: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) else onCancel()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("CANCEL") } },
    ) {
        DatePicker(state = state)
    }
}
