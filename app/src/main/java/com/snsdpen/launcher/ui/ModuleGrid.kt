package com.snsdpen.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.snsdpen.launcher.data.AppEntry
import com.snsdpen.launcher.model.Face
import com.snsdpen.launcher.model.GridPos
import com.snsdpen.launcher.model.GridSpec
import com.snsdpen.launcher.model.LayoutState
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleRegistry
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.Page
import com.snsdpen.launcher.model.PlacedRef
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.model.Surface
import com.snsdpen.launcher.model.canPlace
import com.snsdpen.launcher.model.resolveSwap
import com.snsdpen.launcher.model.vacantCells
import kotlin.math.roundToInt

/** 空きマスを歩く差し色ブロック */
private data class Walker(val pos: GridPos, val trail: List<GridPos>, val colorIndex: Int)
private const val TrailLength = 8

/**
 * 差し色のブロック 2 個が空きマスを 0.7 秒に 1 マスずつ歩き、跡がグレーで残って消える。
 * 状態と描画をここに閉じ、再構成がグリッド全体に波及しないようにする。表示中(RESUMED)だけ動く。
 */
@Composable
private fun WanderingBlocks(
    vacant: List<GridPos>,
    cell: androidx.compose.ui.unit.Dp,
    gap: androidx.compose.ui.unit.Dp,
    colors: List<androidx.compose.ui.graphics.Color>,
    trail: androidx.compose.ui.graphics.Color,
) {
    val walkers = remember { androidx.compose.runtime.mutableStateListOf<Walker>() }
    com.snsdpen.launcher.ui.WhileResumed(vacant) {
        walkers.clear()
        if (vacant.isEmpty()) return@WhileResumed
        val rnd = kotlin.random.Random(System.currentTimeMillis())
        repeat(2) { i -> walkers += Walker(vacant[rnd.nextInt(vacant.size)], emptyList(), i) }
        val set = vacant.map { it.col to it.row }.toSet()
        while (true) {
            kotlinx.coroutines.delay(700)
            for (i in walkers.indices) {
                val w = walkers[i]
                val here = w.pos
                val prev = w.trail.firstOrNull()
                val around = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
                    .map { (dc, dr) -> GridPos(here.col + dc, here.row + dr) }
                    .filter { (it.col to it.row) in set }
                val forward = around.filter { it.col != prev?.col || it.row != prev?.row }
                val next = (forward.ifEmpty { around }).let { if (it.isEmpty()) vacant[rnd.nextInt(vacant.size)] else it[rnd.nextInt(it.size)] }
                walkers[i] = w.copy(pos = next, trail = (listOf(here) + w.trail).take(TrailLength))
            }
        }
    }
    fun x(c: GridPos) = (cell + gap) * c.col
    fun y(c: GridPos) = (cell + gap) * c.row
    walkers.forEach { w ->
        w.trail.forEachIndexed { age, c ->
            val a = 0.10f * (1f - age.toFloat() / TrailLength)
            Box(Modifier.offset(x(c), y(c)).size(cell, cell).background(trail.copy(alpha = a)))
        }
        val color = colors[w.colorIndex % colors.size]
        Box(Modifier.offset(x(w.pos), y(w.pos)).size(cell, cell).background(color.copy(alpha = 0.85f)))
    }
}

/**
 * セル座標でモジュールを絶対配置するグリッド。
 * タップ/長押し/ドラッグ/入れ替え/リサイズはここのラッパーが一元処理し、モジュール本体は受動。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModuleGrid(
    placed: List<PlacedRef>,
    spec: GridSpec,
    face: Face,
    page: Page,
    surface: Surface,
    layout: LayoutState,
    apps: List<AppEntry>,
    editMode: Boolean,
    selectedRef: String?,
    onSelect: (String?) -> Unit,
    onEnterEdit: () -> Unit,
    onEvent: (ModuleEvent) -> Unit,
    notifCounts: Map<String, Int> = emptyMap(),
    onMove: (ref: String, pos: GridPos) -> Unit,
    onSwap: (refA: String, posA: GridPos, refB: String, posB: GridPos) -> Unit,
    onResize: (ref: String, w: Int, h: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = LocalPalette.current
    BoxWithConstraints(modifier) {
        // maxWidth/maxHeight はローカルに退避してから使う(入れ子ラムダで直接触らない)
        val gap = GridGap
        // マスは正方形。幅と高さの両方に収まる方の一辺を使い、余りは下に残す
        val byW = ((maxWidth - gap * (spec.cols - 1)) / spec.cols).coerceAtLeast(0.dp)
        val byH = ((maxHeight - gap * (spec.rows - 1)) / spec.rows).coerceAtLeast(0.dp)
        val cellW = if (byW < byH) byW else byH
        val cellH = cellW
        val density = LocalDensity.current
        val strideWPx = with(density) { (cellW + gap).toPx() }
        val strideHPx = with(density) { (cellH + gap).toPx() }

        // 検出器のキーは固定し、最新値は rememberUpdatedState から読む(古い値を掴まない)
        val placedLatest = rememberUpdatedState(placed)
        val onMoveLatest = rememberUpdatedState(onMove)
        val onSwapLatest = rememberUpdatedState(onSwap)
        val onResizeLatest = rememberUpdatedState(onResize)

        var dragKey by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var resizePreview by remember { mutableStateOf<GridPos?>(null) }

        fun xOf(p: GridPos) = (cellW + gap) * p.col
        fun yOf(p: GridPos) = (cellH + gap) * p.row
        fun wOf(p: GridPos) = cellW * p.colSpan + gap * (p.colSpan - 1)
        fun hOf(p: GridPos) = cellH * p.rowSpan + gap * (p.rowSpan - 1)

        // ---- 空きマスを歩く差し色(配色が持っていれば。通常モードのみ) ----
        // 状態は WanderingBlocks の中に閉じる。ここで読むと 0.7 秒ごとに全モジュールが再構成される
        if (!editMode && pal.vacantAccents.isNotEmpty()) {
            val vacant = remember(placed, spec) { vacantCells(placed, spec) }
            WanderingBlocks(vacant = vacant, cell = cellW, gap = gap, colors = pal.vacantAccents, trail = pal.fg)
        }

        // ---- 空きセル(編集モードのみ薄く描く。タップで選択解除) ----
        if (editMode) {
            vacantCells(placed, spec).forEach { p ->
                Box(
                    Modifier
                        .offset(xOf(p), yOf(p))
                        .size(wOf(p), hOf(p))
                        .border(1.dp, pal.line.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(null) },
                )
            }
        }

        placed.forEach { pr ->
            val spec2 = ModuleRegistry.specOf(pr.ref) ?: return@forEach
            val key = pr.ref
            val isSelected = editMode && selectedRef == key
            val shownPos = if (isSelected) (resizePreview ?: pr.pos) else pr.pos
            val isDragging = dragKey == key
            val scope = ModuleScope(
                ref = pr.ref,
                id = ModuleRegistry.idOf(pr.ref),
                face = face,
                page = page,
                surface = surface,
                span = Span(shownPos.colSpan, shownPos.rowSpan),
                editMode = editMode,
                layout = layout,
                apps = apps,
                emit = onEvent,
                notifCounts = notifCounts,
            )

            Box(
                Modifier
                    .offset(xOf(shownPos), yOf(shownPos))
                    .size(wOf(shownPos), hOf(shownPos))
                    .zIndex(if (isDragging) 2f else if (isSelected) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                        }
                    }
                    .then(
                        if (editMode) Modifier.border(if (isSelected) 2.dp else 1.dp, pal.accent)
                        else Modifier
                    )
                    .then(
                        // ドラッグ検出は clickable より前に置く(ドラッグ中はタップが発火しない)
                        if (editMode) Modifier.pointerInput(key, spec) {
                            detectDragGestures(
                                onDragStart = {
                                    dragKey = key
                                    dragOffset = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                },
                                onDragCancel = { dragKey = null },
                                onDragEnd = {
                                    dragKey = null
                                    if (strideWPx <= 0f || strideHPx <= 0f) return@detectDragGestures
                                    val newCol = (pr.col + dragOffset.x / strideWPx).roundToInt()
                                    val newRow = (pr.row + dragOffset.y / strideHPx).roundToInt()
                                    val target = GridPos(newCol, newRow, pr.colSpan, pr.rowSpan)
                                    if (target == pr.pos) return@detectDragGestures
                                    val others = placedLatest.value.filter { it.ref != pr.ref }
                                    if (canPlace(target, spec, others)) {
                                        onMoveLatest.value(pr.ref, target)
                                    } else {
                                        resolveSwap(pr, target, spec, others)?.let { (other, otherPos) ->
                                            onSwapLatest.value(pr.ref, target, other.ref, otherPos)
                                        }
                                    }
                                },
                            )
                        } else Modifier
                    )
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (editMode) {
                                val edit = spec2.onEdit
                                if (selectedRef == key && edit != null) edit(scope, onEvent)
                                else onSelect(if (selectedRef == key) null else key)
                            } else {
                                spec2.onTap(scope, onEvent)
                            }
                        },
                        onLongClick = { if (!editMode) onEnterEdit() },
                    ),
            ) {
                spec2.content(scope)

                // ---- 選択中: サイズ表示 + 右下のリサイズハンドル(マス単位にスナップ) ----
                if (isSelected) {
                    val minSpan = spec2.minSize(face)
                    Text(
                        text = "${shownPos.colSpan}×${shownPos.rowSpan}",
                        fontSize = 9.sp,
                        color = pal.accent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(pal.bg.copy(alpha = 0.85f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .background(pal.accent)
                            .pointerInput(key, spec) {
                                var acc = Offset.Zero
                                detectDragGestures(
                                    onDragStart = { acc = Offset.Zero; resizePreview = pr.pos },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        if (strideWPx <= 0f || strideHPx <= 0f) return@detectDragGestures
                                        acc += amount
                                        val baseW = strideWPx * pr.colSpan
                                        val baseH = strideHPx * pr.rowSpan
                                        val cs = ((baseW + acc.x) / strideWPx).roundToInt()
                                            .coerceIn(minSpan.w, (spec.cols - pr.col).coerceAtLeast(minSpan.w))
                                        val rs = ((baseH + acc.y) / strideHPx).roundToInt()
                                            .coerceIn(minSpan.h, (spec.rows - pr.row).coerceAtLeast(minSpan.h))
                                        val target = GridPos(pr.col, pr.row, cs, rs)
                                        val others = placedLatest.value.filter { it.ref != pr.ref }
                                        // 重なるサイズにはしない(直前の有効サイズを維持)
                                        if (canPlace(target, spec, others)) resizePreview = target
                                    },
                                    onDragCancel = { resizePreview = null },
                                    onDragEnd = {
                                        val p = resizePreview
                                        resizePreview = null
                                        if (p != null && (p.colSpan != pr.colSpan || p.rowSpan != pr.rowSpan)) {
                                            onResizeLatest.value(pr.ref, p.colSpan, p.rowSpan)
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("◢", color = pal.bg, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
