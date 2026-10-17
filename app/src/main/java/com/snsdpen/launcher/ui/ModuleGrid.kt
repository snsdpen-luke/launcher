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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
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
import com.snsdpen.launcher.model.reservedCorner
import androidx.compose.ui.graphics.Color
import com.snsdpen.launcher.model.LayoutState
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleRegistry
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.Page
import com.snsdpen.launcher.model.PlacedRef
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.model.Surface
import com.snsdpen.launcher.model.canPlace
import com.snsdpen.launcher.model.resolveDrop
import com.snsdpen.launcher.model.vacantCells
import kotlin.math.roundToInt

/**
 * 床のモザイク。マスごとに単色のブロックを 1 個。段は上→下に流れ(tones の先頭が上)、
 * マスの位置から決まる擬似乱数で 1 段だけ揺れる(手貼りのタイルに見せる)。
 * 30 秒に 1 回、マスの 1/4 だけ揺れを選び直し、0.6 秒でにじむように切り替える(表示中のみ)。
 */
@Composable
private fun MosaicFloor(spec: GridSpec, cell: androidx.compose.ui.unit.Dp, gap: androidx.compose.ui.unit.Dp, tones: List<Color>) {
    val n = tones.size
    val cols = spec.cols.coerceAtLeast(1); val rows = spec.rows.coerceAtLeast(1)
    fun toneAt(col: Int, r: Int, seed: Int): Int {
        val t = r.toFloat() / (rows - 1).coerceAtLeast(1)
        val jitter = when (stableHash(col, r, seed) % 6) { 0 -> -1; 1 -> 1; else -> 0 }
        return ((t * n).toInt() + jitter).coerceIn(0, n - 1)
    }
    // 各マスの「今の段」と「次の段」。progress が 0→1 で次に寄る
    var from by remember(spec, n) { mutableStateOf(IntArray(cols * rows) { toneAt(it % cols, it / cols, 7) }) }
    var to by remember(spec, n) { mutableStateOf(from) }
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    com.snsdpen.launcher.ui.WhileResumed(spec, n) {
        var seed = 8
        while (true) {
            kotlinx.coroutines.delay(30_000)
            seed++
            val next = to.copyOf()
            for (i in next.indices) if (stableHash(i, seed, 3) % 4 == 0) next[i] = toneAt(i % cols, i / cols, seed)
            from = to; to = next
            progress.snapTo(0f)
            progress.animateTo(1f, androidx.compose.animation.core.tween(600))
        }
    }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val c = cell.toPx(); val g = gap.toPx()
        val k = progress.value
        // 壁はマス目ぴったり(半端なタイルは描かない。余りは両側の余白)
        val drawCols = cols
        val drawRows = rows
        for (r in 0 until drawRows) for (col in 0 until drawCols) {
            val x0 = col * (c + g); val y0 = r * (c + g)
            val w = minOf(c, size.width - x0); val h = minOf(c, size.height - y0)
            if (w <= 0f || h <= 0f) continue
            val color = if (r < rows && col < cols) {
                val i = r * cols + col
                if (from[i] == to[i] || k >= 1f) tones[to[i]] else androidx.compose.ui.graphics.lerp(tones[from[i]], tones[to[i]], k)
            } else tones[toneAt(col, r.coerceAtMost(rows - 1), 7)]
            drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(x0, y0), size = androidx.compose.ui.geometry.Size(w, h))
        }
    }
}

/** 空きマスを歩く Claude Code のマスコット(本体は差し色)。跡が薄く残る */
private data class Walker(val pos: GridPos, val trail: List<GridPos>, val colorIndex: Int)
private const val TrailLength = 8

/** 確認用: true にすると昼でも寝る */
private const val MASCOT_FORCE_NIGHT = false

/** 夜 9 時〜朝 5 時は寝る */
private fun isSleepTime(): Boolean {
    if (MASCOT_FORCE_NIGHT) return true
    val h = java.time.LocalTime.now().hour
    return h >= 21 || h < 5
}

/** ごはんの流れ(ミリ秒): 0〜1400 踊る / 1400〜3200 食べる(3 口)/ 3400 で終わり */
private const val PartyDance = 1400L
private const val PartyEnd = 3400L

/**
 * 空きマスを歩く Claude Code のマスコット。0.7 秒に 1 マスずつ歩き、跡が薄く残る。
 * 夜 9 時〜朝 5 時は寝る(止まって目を閉じ、z が浮かぶ)。タップすると踊ってごはんを食べる(寝ていても起きて食べ、また寝る)。
 * 状態はこの中に閉じる(グリッド本体で読むと全モジュールが再構成される)。動くのは表示中だけ
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
    var sleeping by remember { mutableStateOf(isSleepTime()) }
    var zPhase by remember { mutableStateOf(0) }
    var partyKey by remember { mutableStateOf(0) }        // タップのたびに増える(ループをやり直す合図)
    var partyT by remember { mutableStateOf(-1L) }        // ごはんの経過ミリ秒(-1 = していない)
    com.snsdpen.launcher.ui.WhileResumed(vacant, partyKey) {
        if (vacant.isEmpty()) { walkers.clear(); return@WhileResumed }
        val rnd = kotlin.random.Random(System.currentTimeMillis())
        // 居場所が空きでなくなっていたら置き直す(タップでやり直す時は今の場所のまま)
        if (walkers.isEmpty() || walkers.any { it.pos !in vacant }) {
            walkers.clear()
            walkers += Walker(vacant[rnd.nextInt(vacant.size)], emptyList(), 0)
        }
        val set = vacant.map { it.col to it.row }.toSet()
        // ごはんの途中でやり直しになった(配置が変わった等)なら、ごはんは打ち切る
        if (partyT > 0L) partyT = -1L
        // ごはん(タップされた直後だけ)。踊って、食べる
        if (partyKey > 0 && partyT == 0L) {
            val t0 = System.currentTimeMillis()
            while (true) {
                val t = System.currentTimeMillis() - t0
                if (t >= PartyEnd) break
                partyT = t.coerceAtLeast(1L)
                kotlinx.coroutines.delay(80)
            }
            partyT = -1L
        }
        while (true) {
            sleeping = isSleepTime()
            if (sleeping) {
                // 寝ている: 止まって z を浮かべる。跡は消す
                if (walkers.isNotEmpty() && walkers[0].trail.isNotEmpty()) walkers[0] = walkers[0].copy(trail = emptyList())
                zPhase++
                kotlinx.coroutines.delay(700)
                continue
            }
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
            zPhase++   // 歩く時の脚の左右にも使う
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
        val t = partyT
        val partying = t >= 0
        val dancing = partying && t < PartyDance
        val eating = partying && t >= PartyDance
        val beat = (t / 175L).toInt()
        val bounce = if (dancing && beat % 2 == 0) -(cell * 0.18f) else 0.dp
        val pose = when {
            dancing -> com.snsdpen.launcher.ui.MascotPose(armsUp = beat % 2 == 0, happyEyes = true, step = beat % 2)
            eating -> com.snsdpen.launcher.ui.MascotPose(mouthOpen = (t / 150L) % 2 == 0L, happyEyes = true, step = -1)
            sleeping -> com.snsdpen.launcher.ui.MascotPose(eyesClosed = true, step = -1)
            else -> com.snsdpen.launcher.ui.MascotPose(step = zPhase % 2)
        }
        val tap = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Box(
            Modifier
                .offset(x(w.pos), y(w.pos) + bounce)
                .size(cell, cell)
                .clickable(interactionSource = tap, indication = null) {
                    if (partyT < 0) { partyT = 0L; partyKey++ }
                },
        ) {
            com.snsdpen.launcher.ui.ClaudeMark(body = color, ink = trail, modifier = Modifier.fillMaxSize(), pose = pose)
        }
        // 踊っている間: 頭の上にハート
        if (dancing) {
            com.snsdpen.launcher.ui.PixelHeart(
                color = androidx.compose.ui.graphics.Color(0xFFFF5A7A),
                modifier = Modifier.offset(x(w.pos) + cell * 0.32f, y(w.pos) - cell * 0.42f + bounce).size(cell * 0.36f),
            )
        }
        // 食べている間: 右横におにぎり。1 口ずつ欠ける
        if (eating) {
            val left = when { t < 1800L -> 3; t < 2400L -> 2; t < 3000L -> 1; else -> 0 }
            com.snsdpen.launcher.ui.FoodBlock(
                left = left,
                modifier = Modifier.offset(x(w.pos) + cell * 0.88f, y(w.pos) + cell * 0.22f).size(cell * 0.62f),
            )
        }
        // 寝ている間: z が 3 つ、ずれて浮かんでは消える
        if (sleeping && !partying) {
            for (k in 0 until 3) {
                val ph = (zPhase + k * 2) % 6                 // 0..5
                val a = when (ph) { 0 -> 0.25f; 1 -> 0.6f; 2 -> 0.9f; 3 -> 0.7f; 4 -> 0.35f; else -> 0f }
                if (a <= 0f) continue
                Text(
                    "z",
                    color = trail.copy(alpha = a * 0.8f),
                    fontSize = (9 + ph).sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.offset(x(w.pos) + cell * (0.70f + ph * 0.08f), y(w.pos) - cell * (0.30f + ph * 0.14f)),
                )
            }
        }
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
    /** ドロップの結果(そのページの配置全体) */
    onArrange: (List<PlacedRef>) -> Unit,
    onResize: (ref: String, w: Int, h: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 右下の予約席の隠しボタン(null なら出さない) */
    onQuick: (() -> Unit)? = null,
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
        val onArrangeLatest = rememberUpdatedState(onArrange)
        val onResizeLatest = rememberUpdatedState(onResize)

        var dragKey by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var resizePreview by remember { mutableStateOf<GridPos?>(null) }
        /** ドラッグ中の落とし先(解決後の位置)と、置けるかどうか */
        var dropPreview by remember { mutableStateOf<Pair<GridPos, Boolean>?>(null) }

        fun xOf(p: GridPos) = (cellW + gap) * p.col
        fun yOf(p: GridPos) = (cellH + gap) * p.row
        fun wOf(p: GridPos) = cellW * p.colSpan + gap * (p.colSpan - 1)
        fun hOf(p: GridPos) = cellH * p.rowSpan + gap * (p.rowSpan - 1)
        // マス目全体を左右中央に置く。余りは両側の余白にする(半端なマスは作らない)
        val gridW = cellW * spec.cols + gap * (spec.cols - 1)
        Box(Modifier.width(gridW).fillMaxHeight().align(Alignment.TopCenter)) {

        // ---- 床のモザイク(配色が持っていれば)。全マスの後ろに単色ブロックを敷く ----
        if (pal.floor.isNotEmpty()) MosaicFloor(spec = spec, cell = cellW, gap = gap, tones = pal.floor)

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

        // ---- 右下の予約席: 隠しボタン(薄い印。押すとライト・明るさ・音量のパネル)。編集中は出さない ----
        val corner = spec.reservedCorner
        if (onQuick != null && corner != null && !editMode) {
            Box(
                Modifier
                    .offset(xOf(corner), yOf(corner))
                    .size(cellW, cellH)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onQuick),
                contentAlignment = Alignment.Center,
            ) { com.snsdpen.launcher.ui.SlidersGlyph(pal.fg.copy(alpha = 0.28f), cellW * 0.42f) }
        }

        // ---- ドラッグ中の落とし先(置けるならアクセント、置けないなら赤) ----
        dropPreview?.let { (pos, ok) ->
            Box(
                Modifier
                    .offset(xOf(pos), yOf(pos))
                    .size(wOf(pos), hOf(pos))
                    .zIndex(3f)
                    .border(2.dp, if (ok) pal.accent else androidx.compose.ui.graphics.Color(0xFFE05A5A)),
            )
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
                cell = cellW,
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
                                    if (strideWPx > 0f && strideHPx > 0f) {
                                        val t = GridPos(
                                            (pr.col + dragOffset.x / strideWPx).roundToInt(),
                                            (pr.row + dragOffset.y / strideHPx).roundToInt(),
                                            pr.colSpan, pr.rowSpan,
                                        )
                                        val others = placedLatest.value.filter { it.ref != pr.ref }
                                        val result = resolveDrop(pr, t, spec, others)
                                        val landed = result?.firstOrNull { it.ref == pr.ref }?.pos ?: t
                                        dropPreview = landed to (result != null)
                                    }
                                },
                                onDragCancel = { dragKey = null; dropPreview = null },
                                onDragEnd = {
                                    dragKey = null
                                    dropPreview = null
                                    if (strideWPx <= 0f || strideHPx <= 0f) return@detectDragGestures
                                    val newCol = (pr.col + dragOffset.x / strideWPx).roundToInt()
                                    val newRow = (pr.row + dragOffset.y / strideHPx).roundToInt()
                                    val target = GridPos(newCol, newRow, pr.colSpan, pr.rowSpan)
                                    if (target == pr.pos) return@detectDragGestures
                                    val others = placedLatest.value.filter { it.ref != pr.ref }
                                    resolveDrop(pr, target, spec, others)?.let { onArrangeLatest.value(it) }
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
                    // 1 行しか無いモジュールでは文字に被らないよう小さく
                    val handle = if (pr.rowSpan <= 1) 20.dp else 28.dp
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(handle)
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
                        Text("◢", color = pal.bg, fontSize = if (pr.rowSpan <= 1) 9.sp else 12.sp)
                    }
                }
            }
        }
        }
    }
}
