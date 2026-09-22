package com.snsdpen.launcher.model

/** セル座標(左上原点)と占有マス数 */
data class GridPos(val col: Int, val row: Int, val colSpan: Int = 1, val rowSpan: Int = 1)

data class GridSpec(val cols: Int, val rows: Int)

/** マス数 (幅, 高さ) */
data class Span(val w: Int, val h: Int)

fun GridPos.fits(spec: GridSpec): Boolean =
    col >= 0 && row >= 0 && colSpan >= 1 && rowSpan >= 1 &&
        col + colSpan <= spec.cols && row + rowSpan <= spec.rows

fun GridPos.overlaps(o: GridPos): Boolean =
    col < o.col + o.colSpan && o.col < col + colSpan &&
        row < o.row + o.rowSpan && o.row < row + rowSpan

/** 枠内 かつ 他モジュールと重ならない */
fun canPlace(target: GridPos, spec: GridSpec, others: List<PlacedRef>): Boolean =
    target.fits(spec) && others.none { target.overlaps(it.pos) }

/**
 * ドロップ先 [target] が他モジュール 1 つと重なるとき、双方が収まる場合だけ入れ替えを返す。
 * 相手はドラッグ元の左上へ、サイズを保ったまま移る。不可なら null。
 */
fun resolveSwap(
    dragged: PlacedRef,
    target: GridPos,
    spec: GridSpec,
    others: List<PlacedRef>,
): Pair<PlacedRef, GridPos>? {
    if (!target.fits(spec)) return null
    val other = others.filter { target.overlaps(it.pos) }.singleOrNull() ?: return null
    val rest = others.filter { it.ref != other.ref }
    val otherNew = GridPos(dragged.col, dragged.row, other.colSpan, other.rowSpan)
    if (!canPlace(target, spec, rest)) return null
    if (!canPlace(otherNew, spec, rest)) return null
    if (otherNew.overlaps(target)) return null
    return other to otherNew
}

/** 左上から走査して [w]x[h] の空きを探し配置。空きが無ければそのまま返す */
fun placeInFirstFree(
    refs: List<PlacedRef>,
    spec: GridSpec,
    ref: String,
    w: Int,
    h: Int,
): List<PlacedRef> {
    for (r in 0..(spec.rows - h)) {
        for (c in 0..(spec.cols - w)) {
            val pos = GridPos(c, r, w, h)
            if (canPlace(pos, spec, refs)) return refs + PlacedRef(ref, c, r, w, h)
        }
    }
    return refs
}

/** 占有されていないセルの一覧(編集モードの空き表示用) */
fun vacantCells(refs: List<PlacedRef>, spec: GridSpec): List<GridPos> = buildList {
    for (r in 0 until spec.rows) for (c in 0 until spec.cols) {
        val p = GridPos(c, r)
        if (refs.none { p.overlaps(it.pos) }) add(p)
    }
}

/** [minRow] 以降の行から空きを探して配置(グループ見出しの下に置く用途) */
fun placeInFirstFreeFrom(
    refs: List<PlacedRef>,
    spec: GridSpec,
    ref: String,
    w: Int,
    h: Int,
    minRow: Int,
): List<PlacedRef> {
    for (r in minRow.coerceAtLeast(0)..(spec.rows - h)) {
        for (c in 0..(spec.cols - w)) {
            val pos = GridPos(c, r, w, h)
            if (canPlace(pos, spec, refs)) return refs + PlacedRef(ref, c, r, w, h)
        }
    }
    return refs
}

/**
 * ドロップ先を解決して、新しい配置(全体)を返す。置けなければ null。
 * 1. 枠外なら中に丸める
 * 2. そのまま置ける → 置く
 * 3. 1 個とだけ重なり、双方が収まる → 入れ替え
 * 4. 2 マス以内の近くに空きがある → そこへ寄せる
 * 5. 重なっている物を空きへ押しのけられる → 押しのけて置く
 */
fun resolveDrop(dragged: PlacedRef, wanted: GridPos, spec: GridSpec, others: List<PlacedRef>): List<PlacedRef>? {
    val target = GridPos(
        wanted.col.coerceIn(0, (spec.cols - wanted.colSpan).coerceAtLeast(0)),
        wanted.row.coerceIn(0, (spec.rows - wanted.rowSpan).coerceAtLeast(0)),
        wanted.colSpan, wanted.rowSpan,
    )
    if (!target.fits(spec)) return null
    if (canPlace(target, spec, others)) return others + dragged.at(target)
    resolveSwap(dragged, target, spec, others)?.let { (other, otherPos) ->
        return others.map { if (it.ref == other.ref) it.at(otherPos) else it } + dragged.at(target)
    }
    for (d in 1..2) {
        for (dr in -d..d) for (dc in -d..d) {
            if (kotlin.math.abs(dr) + kotlin.math.abs(dc) != d) continue
            val p = GridPos(target.col + dc, target.row + dr, target.colSpan, target.rowSpan)
            if (canPlace(p, spec, others)) return others + dragged.at(p)
        }
    }
    val overlapping = others.filter { target.overlaps(it.pos) }
    var refs = others.filterNot { o -> overlapping.any { it.ref == o.ref } } + dragged.at(target)
    for (o in overlapping) {
        val next = placeInFirstFree(refs, spec, o.ref, o.colSpan, o.rowSpan)
        if (next.size == refs.size) return null
        refs = next
    }
    return refs
}
