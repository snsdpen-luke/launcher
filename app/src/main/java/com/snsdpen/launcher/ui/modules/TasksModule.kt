package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.model.Face
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.REF_TASKS
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.model.tasksInOrder
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.Marker
import com.snsdpen.launcher.ui.parseIsoDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** TODO。見出しの + で即入力、行タップで完了⇄未完了、CLEAR で完了分を一掃 */
val TasksSpec = ModuleSpec(
    kind = REF_TASKS,
    name = "TASKS",
    defaultSize = { face -> if (face == Face.COVER) Span(8, 10) else Span(8, 8) },
    minSize = { Span(4, 2) },
    // 枠タップ(行の外)は追加へ
    onTap = { _, emit -> emit(ModuleEvent.AddTask) },
    content = { TasksModule(it) },
)

private val HeaderH = 28.dp
private val RowH = 44.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TasksModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val tasks = scope.layout.tasksInOrder()
    val open = tasks.count { !it.done }
    val doneCount = tasks.size - open
    val edit = scope.editMode

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxH = maxHeight
        val rows = ((maxH - HeaderH) / RowH).toInt().coerceAtLeast(0)
        val shown = tasks.take(rows)
        val overflow = tasks.size - shown.size

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(HeaderH).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Marker(p.fg)
                Spacer(Modifier.width(10.dp))
                Text("TASKS", color = p.fg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (overflow > 0) Text("+$overflow", color = p.fgDim, fontSize = 10.sp, modifier = Modifier.padding(end = 6.dp))
                if (!edit && doneCount > 0) HeaderAction("CLEAR", p.fgDim) { scope.emit(ModuleEvent.ClearDoneTasks) }
                if (!edit) HeaderAction("+", p.fg) { scope.emit(ModuleEvent.AddTask) }
            }
            if (tasks.isEmpty() && !edit) {
                Text("TAP TO ADD", color = p.fgDim, fontSize = 11.sp, modifier = Modifier.padding(start = 26.dp, top = 6.dp))
            }
            shown.forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(RowH)
                        .then(
                            if (edit) Modifier
                            else Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { scope.emit(ModuleEvent.ToggleTask(t.id)) },
                                onLongClick = { scope.emit(ModuleEvent.EditTask(t.id)) },
                            )
                        )
                        .padding(start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 未完了は中抜き、完了は塗り(薄い)
                    Marker(if (t.done) p.fgDim else p.fg, filled = t.done)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.text,
                            color = if (t.done) p.fgDim else p.fg,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (t.people.isNotBlank()) {
                            Text(t.people, color = p.fgDim, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val label = dateLabel(t.start, t.due)
                    if (label != null) {
                        val dueDate = parseIsoDate(t.due)
                        // 期限が今日以前なら濃く、先なら薄く。完了は薄く
                        val hot = !t.done && dueDate != null && !dueDate.isAfter(LocalDate.now())
                        Text(label, color = if (hot) p.fg else p.fgDim, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

private val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d", Locale.ENGLISH)

/** "9/25" / "9/20–9/25" / "9/20–"。両方 null なら null */
private fun dateLabel(start: String?, due: String?): String? {
    val s = parseIsoDate(start)?.format(ShortDate)
    val d = parseIsoDate(due)?.format(ShortDate)
    return when {
        s != null && d != null -> "$s–$d"
        d != null -> d
        s != null -> "$s–"
        else -> null
    }
}
