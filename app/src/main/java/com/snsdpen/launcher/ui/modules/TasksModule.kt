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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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

private val HeaderH = 28.dp   // 見出し(■ TASKS)
/** 行の高さはマス + 目地(グリッドの行に揃う)。チェック箱の見た目は 18dp、当たり判定は 1 マス */
private val BoxSize = 18.dp

/**
 * タスクは「押して完了する物」なので、左に大きめのチェック箱。箱を押すと完了⇄未完了、文字を押すと編集、長押しで配置編集。
 * 期限は今日なら濃く、過ぎていれば橙
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TasksModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val tasks = scope.layout.tasksInOrder()
    val open = tasks.count { !it.done }
    val doneCount = tasks.size - open
    val edit = scope.editMode

    val cell = scope.cell
    val gap = com.snsdpen.launcher.ui.GridGap
    val rowH = cell + gap
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxH = maxHeight
        val rows = ((maxH - HeaderH) / rowH).toInt().coerceAtLeast(0)
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
            val today = LocalDate.now()
            shown.forEach { t ->
                val dueDate0 = parseIsoDate(t.due)
                val overdue0 = !t.done && dueDate0 != null && dueDate0.isBefore(today)
                Row(
                    Modifier.fillMaxWidth().height(rowH).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // チェック箱: 当たり判定は 1 マス(アイコンと同じ位置)、見た目は 18dp。未完了は中抜き、完了は塗り、期限切れは橙の枠
                    Box(
                        Modifier
                            .size(cell)
                            .then(
                                if (edit) Modifier
                                else Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { scope.emit(ModuleEvent.ToggleTask(t.id)) }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(BoxSize)
                                .then(
                                    if (t.done) Modifier.background(p.fgDim)
                                    else if (overdue0) Modifier.border(2.dp, com.snsdpen.launcher.ui.NotifOrange)
                                    else Modifier.border(1.5.dp, p.fg)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (t.done) Text("✓", color = p.bg, fontSize = 12.sp, lineHeight = 13.sp)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (edit) Modifier
                                else Modifier.combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { scope.emit(ModuleEvent.EditTask(t.id)) },
                                    onLongClick = { scope.emit(ModuleEvent.EnterEdit) },
                                )
                            ),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            t.text,
                            color = if (t.done) p.fgDim else p.fg,
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                        )
                        if (t.people.isNotBlank()) {
                            Text(t.people, color = p.fgDim, fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val label = dateLabel(t.start, t.due)
                    if (label != null) {
                        val dueDate = parseIsoDate(t.due)
                        // 期限: 過ぎていれば橙、今日なら濃く、先なら薄く。完了は薄く
                        val overdue = !t.done && dueDate != null && dueDate.isBefore(today)
                        val dueToday = !t.done && dueDate != null && dueDate.isEqual(today)
                        Text(
                            label,
                            color = if (overdue) com.snsdpen.launcher.ui.NotifOrange else if (dueToday) p.fg else p.fgDim,
                            fontSize = 10.sp,
                            fontWeight = if (overdue || dueToday) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                            modifier = Modifier.padding(start = 6.dp),
                        )
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
