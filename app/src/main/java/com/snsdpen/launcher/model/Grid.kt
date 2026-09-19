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
