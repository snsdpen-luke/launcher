package com.snsdpen.launcher.model

import android.content.pm.ApplicationInfo
import com.snsdpen.launcher.data.AppEntry
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * 保存されるレイアウト。参照は文字列キー(ref)で、表示名を変えてもキーは変えない。
 *  - "clock"          … 単一モジュール
 *  - "folder:<id>"    … 複数インスタンスのモジュール
 */
@Serializable
data class PlacedRef(
    val ref: String,
    val col: Int,
    val row: Int,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
) {
    val pos: GridPos get() = GridPos(col, row, colSpan, rowSpan)
    fun at(p: GridPos) = copy(col = p.col, row = p.row, colSpan = p.colSpan, rowSpan = p.rowSpan)
}

@Serializable
data class FolderDef(
    val id: String,
    val name: String,
    val apps: List<String>,   // appKey 形式
)

/** URL ショートカット */
@Serializable
data class LinkDef(val id: String, val name: String, val url: String)

/** リンクのまとまり(ボード)。links は LinkDef.id の並び */
@Serializable
data class BoardDef(val id: String, val name: String, val links: List<String> = emptyList())

/** アプリを直接起動するパネル(app は appKey 形式、name は別名。空ならアプリ名) */
@Serializable
data class PanelDef(val id: String, val app: String, val name: String = "")

/** 床のタイル(計測値をブロックの数で表す)。metric = battery / memory / storage / blank、shade = 色の番号(0..2) */
@Serializable
data class MeterDef(val id: String, val metric: String, val shade: Int = 0, val style: String = "blocks")   // style: blocks / text

/** 見出しだけの行(グループ分け用) */
@Serializable
data class LabelDef(val id: String, val text: String)

/** TODO 1 件 */
@Serializable
data class TaskDef(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = 0L,
    /** ISO 日付("2026-09-19")。null = 未設定 */
    val start: String? = null,
    val due: String? = null,
    /** 関係者のメモ(自由記述) */
    val people: String = "",
)

/** (面, ページ) 1 組の配置 */
@Serializable
data class PageLayout(val face: Face, val page: Page, val refs: List<PlacedRef>)

/** 定義は全面・全ページで共有、配置は (面, ページ) ごとに独立 */
@Serializable
data class LayoutState(
    /** 既定値は「最初の版 = 1」に固定し、二度と変えない(version 欠落 = v1 として移行する) */
    val version: Int = 1,
    val folders: List<FolderDef> = emptyList(),
    val links: List<LinkDef> = emptyList(),
    val boards: List<BoardDef> = emptyList(),
    val tasks: List<TaskDef> = emptyList(),
    val panels: List<PanelDef> = emptyList(),
    val labels: List<LabelDef> = emptyList(),
    val meters: List<MeterDef> = emptyList(),
    val layouts: List<PageLayout> = emptyList(),
    /** v1 のみ(面ごとの単一配置)。v2 で layouts へ移し、以後は空 */
    val cover: List<PlacedRef> = emptyList(),
    val main: List<PlacedRef> = emptyList(),
) {
    fun placed(face: Face, page: Page): List<PlacedRef> =
        layouts.firstOrNull { it.face == face && it.page == page }?.refs.orEmpty()

    fun withPlaced(face: Face, page: Page, refs: List<PlacedRef>): LayoutState {
        val rest = layouts.filterNot { it.face == face && it.page == page }
        return copy(layouts = rest + PageLayout(face, page, refs))
    }
}

// ---- バージョンと移行 ----

/**
 * グリッド構造・既定サイズを変えたら必ず上げて、下に 1 段の移行ステップを足す。
 * 既定値付きのフィールド追加やカタログ追加(自動配置しないもの)は上げない。
 *
 * 1: 面ごとの単一配置(cover / main)
 * 2: (面, ページ) ごとの配置(layouts)。v1 の配置は PRIVATE ページへ
 * 3: WORK ページに calendar と tasks を自動配置
 * 4: グリッドを縦横 2 倍(カバー 8x28 / メイン 12x20)。座標とサイズを 2 倍にし、WORK の tasks を既定サイズまで広げる
 * 5: PRIVATE ページを「見出し + アプリパネル」で組み直す(端末にあるアプリから拾う)
 * 6: 5 の配置修正(パネルを見出しの下から置く。FITNESS は 3 本に絞る)
 * 7: ラベルを 1 行にして PRIVATE の下にタイル(BAT / MEM / STO / 飾り)を置く
 * 8: タイルを 1x2(63x54dp、ほぼ正方形)にして下段に 8 枚
 * 9: マスを正方形に(カバー 16x24 / メイン 22x14)。横 2 倍、縦は 25→30dp の比で丸めて置き直す。TILE を METER(ブロック数で表す)に
 * 10: 床を BAT|WIFI|SIG / MEM|STO|NTF に(ステータスバーを隠した代わり)
 * 11: 床の色番号を metric ごとに振り直す(配色の blocks と対応)
 * 16: NTF / MEM / STO のブロック計測を消す(文字表示は残す)
 * 17: 並びを BAT | SIG | WIFI | BT に(WIFI と SIG の位置を入れ替え)\n * 18: 時計を 1 行に(数字の高さ = マス、秒・日付・曜日を右に 1 行で)\n * 19: カバー横(COVER_WIDE 26x15)とメインの側パネル(SIDE 7x24)を追加。横は縦の配置を詰めて生成、SIDE は予定 + タスク
 * 20: 横の自動配置を幅 8 の縦の帯に流す方式に(見出しと中身の並びを保つ)\n * 21: 20 の流す順を「元の左の列 → 右の列」に\n * 22: SIDE を 14x24 に(メインの残り幅いっぱい)。配置は作り直し\n * 23: SIDE の上 2 行にアプリ 4 本(Chrome / Gmail / LINE / マップ)。窓はその下に開く\n * 24: MAIN を 16x24 の独立した左パネルに(初回はカバーの写し)。左のアプリをタップすると右に窓。SIDE は予定 + タスクだけ
 * 12: 通信系(BAT/WIFI/SIG/NTF)を時計の右に集約、MEM/STO は最下段 1 行
 * 13: BT を追加。時計の右に BAT WIFI SIG BT(2 マスずつ、濃さで表す) / NTF(4) MEM STO(2 ずつ)
 * 14: WORK の右上に文字表示の BAT WIFI SIG BT(2 マスずつ)。重なる物は空きへ退ける
 * 15: ボードをやめ、中のリンクを 1 本ずつ link モジュールとして展開
 */
const val LAYOUT_VERSION = 24

/** 旧版を 1 段ずつ移行する。現版に届かなければ null(作り直し) */
fun migrateLayout(s0: LayoutState, apps: List<AppEntry> = emptyList()): LayoutState? {
    var s = s0
    if (s.version == 1) {
        val migrated = listOf(
            PageLayout(Face.COVER, Page.PRIVATE, s.cover),
            PageLayout(Face.MAIN, Page.PRIVATE, s.main),
        )
        val fresh = Face.entries.flatMap { f ->
            Page.entries.filter { it != Page.PRIVATE }.map { p -> PageLayout(f, p, defaultPage(f, p, s.folders)) }
        }
        s = s.copy(version = 2, layouts = migrated + fresh, cover = emptyList(), main = emptyList())
    }
    if (s.version == 2) {
        var t = s
        for (face in Face.entries) {
            var refs = t.placed(face, Page.WORK)
            for (ref in listOf(REF_CALENDAR, REF_TASKS)) {
                if (refs.none { it.ref == ref }) {
                    val size = ModuleRegistry.specOf(ref)?.defaultSize?.invoke(face) ?: Span(2, 1)
                    refs = placeInFirstFree(refs, gridFor(face), ref, size.w, size.h)
                }
            }
            t = t.withPlaced(face, Page.WORK, refs)
        }
        s = t.copy(version = 3)
    }
    if (s.version == 3) {
        var t = s.copy(layouts = s.layouts.map { l ->
            l.copy(refs = l.refs.map { r -> PlacedRef(r.ref, r.col * 2, r.row * 2, r.colSpan * 2, r.rowSpan * 2) })
        })
        for (face in Face.entries) {
            val refs = t.placed(face, Page.WORK)
            val tasks = refs.firstOrNull { it.ref == REF_TASKS } ?: continue
            val want = ModuleRegistry.specOf(REF_TASKS)?.defaultSize?.invoke(face) ?: continue
            val target = GridPos(tasks.col, tasks.row, maxOf(tasks.colSpan, want.w), maxOf(tasks.rowSpan, want.h))
            if (canPlace(target, gridFor(face), refs.filter { it.ref != REF_TASKS })) {
                t = t.moveRef(face, Page.WORK, REF_TASKS, target)
            }
        }
        s = t.copy(version = 4)
    }
    if (s.version == 4) {
        var t = s
        for (face in Face.entries) t = t.seedPrivate(face, apps)
        s = t.copy(version = 5)
    }
    if (s.version == 5) {
        var t = s
        for (face in Face.entries) t = t.seedPrivate(face, apps)
        s = t.copy(version = 6)
    }
    if (s.version == 6) {
        var t = s
        // 既存のラベルを 1 行に(全ページ)
        t = t.copy(layouts = t.layouts.map { l -> l.copy(refs = l.refs.map { r -> if (r.ref.startsWith("label:")) r.copy(rowSpan = 1) else r }) })
        for (face in Face.entries) t = t.seedPrivate(face, apps)
        s = t.copy(version = 7)
    }
    if (s.version == 7) {
        var t = s
        // (v9 で TILE 自体を廃止したので、ここでは配置だけ組み直す)
        for (face in Face.entries) t = t.seedPrivate(face, apps)
        s = t.copy(version = 8)
    }
    if (s.version == 8) {
        var t = s
        // 旧 8x28(1 マス 63x25dp) → 新 16x24(1 マス 30x30dp)。元の順(上→下、左→右)に置き直す
        fun scaleH(h: Int) = (h * 25f / 30f).roundToInt().coerceAtLeast(1)
        t = t.copy(layouts = t.layouts.map { l ->
            val spec = gridFor(l.face)
            var refs: List<PlacedRef> = emptyList()
            l.refs.filterNot { it.ref.startsWith("tile:") }
                .sortedWith(compareBy({ it.row }, { it.col }))
                .forEach { r -> refs = placeInFirstFree(refs, spec, r.ref, (r.colSpan * 2).coerceAtMost(spec.cols), scaleH(r.rowSpan)) }
            l.copy(refs = refs)
        })
        for (face in Face.entries) t = t.seedPrivate(face, apps)
        s = t.copy(version = 9)
    }
    if (s.version == 9) {
        var t = s
        for (face in Face.entries) t = t.seedPrivate(face, apps)
        s = t.copy(version = 10)
    }
    if (s.version == 10) {
        s = s.copy(version = 11, meters = s.meters.map { it.copy(shade = meterShadeFor(it.metric, it.shade)) })
    }
    if (s.version == 11) {
        var t = s
        for (face in Face.entries) t = t.stackStatusBesideClock(face, Page.PRIVATE)
        s = t.copy(version = 12)
    }
    if (s.version == 12) {
        var t = s
        for (face in Face.entries) t = t.stackStatusCompact(face, Page.PRIVATE)
        s = t.copy(version = 13)
    }
    if (s.version == 13) {
        var t = s
        for (face in Face.entries) t = t.seedWorkStatus(face)
        s = t.copy(version = 14)
    }
    if (s.version == 14) {
        s = s.explodeBoards().copy(version = 15)
    }
    if (s.version == 15) {
        s = s.dropMeters(setOf("notif", "memory", "storage")).copy(version = 16)
    }
    if (s.version == 16) {
        s = s.swapMeters("wifi", "signal").copy(version = 17)
    }
    if (s.version == 17) {
        // 時計を 1 行に(数字の高さ = マス)。幅はそのまま
        s = s.copy(layouts = s.layouts.map { l -> l.copy(refs = l.refs.map { if (it.ref == REF_CLOCK) it.copy(rowSpan = 1) else it }) }, version = 18)
    }
    if (s.version == 18) {
        // カバー横(COVER_WIDE)は縦の配置を上から順に詰めて作る。SIDE は予定とタスク
        var t = s
        for (page in Page.entries) {
            t = t.packInto(Face.COVER, Face.COVER_WIDE, page)
            t = t.seedSide(page)
        }
        s = t.copy(version = 19)
    }
    if (s.version == 19) {
        // 横の自動配置を「縦の帯に流す」方式に組み直す(19 の row-major 詰めは見出しと中身がばらけた)
        var t = s
        for (page in Page.entries) {
            t = t.withPlaced(Face.COVER_WIDE, page, emptyList()).packInto(Face.COVER, Face.COVER_WIDE, page)
        }
        s = t.copy(version = 20)
    }
    if (s.version == 20) {
        // 元の左の列 → 右の列の順で流す(左右 2 列だった群が交互に混ざらない)
        var t = s
        for (page in Page.entries) t = t.withPlaced(Face.COVER_WIDE, page, emptyList()).packInto(Face.COVER, Face.COVER_WIDE, page)
        s = t.copy(version = 21)
    }
    if (s.version == 21) {
        // SIDE を 7 列 → 14 列に(メインの残り幅いっぱい)。配置は作り直す
        var t = s
        for (page in Page.entries) t = t.withPlaced(Face.SIDE, page, emptyList()).seedSide(page)
        s = t.copy(version = 22)
    }
    if (s.version == 22) {
        // SIDE の上にアプリ 4 本(窓はその下に開く)
        var t = s
        for (page in Page.entries) t = t.withPlaced(Face.SIDE, page, emptyList()).seedSide(page, apps)
        s = t.copy(version = 23)
    }
    if (s.version == 23) {
        // MAIN の左パネルを独立した配置に(カバーと同じ 16x24。初回はカバーの写し)。SIDE は予定 + タスクだけに戻す
        var t = s
        for (page in Page.entries) {
            t = t.withPlaced(Face.MAIN, page, t.placed(Face.COVER, page))
            t = t.withPlaced(Face.SIDE, page, emptyList()).seedSide(page)
        }
        s = t.copy(version = 24)
    }
    return s.takeIf { it.version == LAYOUT_VERSION }
}

// ---- 参照キー ----

fun appKey(a: AppEntry) = "${a.packageName}/${a.activityName}"
fun folderRef(id: String) = "folder:$id"
fun boardRef(id: String) = "board:$id"
fun panelRef(id: String) = "panel:$id"
fun labelRef(id: String) = "label:$id"
fun linkRef(id: String) = "link:$id"
fun meterRef(id: String) = "meter:$id"
const val REF_CLOCK = "clock"
const val REF_CALENDAR = "calendar"
const val REF_TASKS = "tasks"
const val DEFAULT_BOARD_ID = "links"

// ---- 配置の解決と更新 ----

/** レジストリで解決でき、その窓・ページに出せる参照だけ返す(消えたフォルダ等は落とす) */
fun LayoutState.resolved(face: Face, page: Page, surface: Surface): List<PlacedRef> =
    placed(face, page).filter { r ->
        val spec = ModuleRegistry.specOf(r.ref) ?: return@filter false
        surface in spec.surfaces && page in spec.pages && spec.exists(this, ModuleRegistry.idOf(r.ref))
    }

fun LayoutState.moveRef(face: Face, page: Page, ref: String, pos: GridPos): LayoutState =
    withPlaced(face, page, placed(face, page).map { if (it.ref == ref) it.at(pos) else it })

fun LayoutState.swapRefs(
    face: Face, page: Page, refA: String, posA: GridPos, refB: String, posB: GridPos,
): LayoutState =
    withPlaced(face, page, placed(face, page).map {
        when (it.ref) {
            refA -> it.at(posA)
            refB -> it.at(posB)
            else -> it
        }
    })

fun LayoutState.resizeRef(face: Face, page: Page, ref: String, w: Int, h: Int): LayoutState =
    withPlaced(face, page, placed(face, page).map {
        if (it.ref == ref) it.copy(colSpan = w, rowSpan = h) else it
    })

// ---- 初期状態 ----

/** ApplicationInfo.category でアプリを大まかに分ける(空のカテゴリは作らない) */
fun buildDefaultFolders(apps: List<AppEntry>): List<FolderDef> {
    val byCat = apps.groupBy { it.category }
    fun cat(vararg c: Int) = c.flatMap { byCat[it].orEmpty() }
    fun of(id: String, name: String, list: List<AppEntry>) =
        if (list.isEmpty()) null else FolderDef(id, name, list.map(::appKey))
    return listOfNotNull(
        of("comms", "COMMS", cat(ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_NEWS)),
        of("media", "MEDIA", cat(ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE)),
        of("games", "GAMES", cat(ApplicationInfo.CATEGORY_GAME)),
        of("nav", "NAV", cat(ApplicationInfo.CATEGORY_MAPS)),
        of("work", "WORK", cat(ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_ACCESSIBILITY)),
        of("tools", "TOOLS", cat(ApplicationInfo.CATEGORY_UNDEFINED)),
    )
}

/** ページごとの初期配置(仮)。時計 + 用途に合うフォルダ */
fun defaultPage(face: Face, page: Page, folders: List<FolderDef>): List<PlacedRef> {
    val spec = gridFor(face)
    var refs: List<PlacedRef> = emptyList()
    fun add(ref: String) {
        val size = ModuleRegistry.specOf(ref)?.defaultSize?.invoke(face) ?: Span(1, 1)
        refs = placeInFirstFree(refs, spec, ref, size.w, size.h)
    }
    val ids = folders.map { it.id }.toSet()
    val wanted: List<String> = when (page) {
        Page.PRIVATE -> folders.map { it.id }
        Page.WORK -> listOf("work", "comms")
        Page.DRIVE -> listOf("nav", "media")
    }
    add(REF_CLOCK)
    if (page == Page.WORK) {
        add(boardRef(DEFAULT_BOARD_ID))
        add(REF_CALENDAR)
        add(REF_TASKS)
    }
    wanted.filter { it in ids }.forEach { add(folderRef(it)) }
    return refs
}

fun defaultLayoutState(apps: List<AppEntry>): LayoutState {
    val folders = buildDefaultFolders(apps)
    val layouts = Face.entries.flatMap { f ->
        Page.entries.map { p -> PageLayout(f, p, defaultPage(f, p, folders)) }
    }
    var s = LayoutState(
        version = LAYOUT_VERSION,
        folders = folders,
        boards = listOf(BoardDef(DEFAULT_BOARD_ID, "LINKS")),
        layouts = layouts,
    )
    for (face in Face.entries) s = s.seedPrivate(face, apps)
    return s
}

// ---- ボードとリンク ----

private fun newId(prefix: String) = prefix + java.lang.Long.toString(System.currentTimeMillis(), 36)

/** ボードを作って現ページの空きに置く。空きが無ければ小さくして再試行し、それでも無ければ定義だけ残す */
fun LayoutState.addBoard(face: Face, page: Page, name: String): LayoutState {
    val def = BoardDef(id = newId("b"), name = name)
    val spec = gridFor(face)
    val refs = placed(face, page)
    val candidates = listOf(Span(8, 10), Span(8, 6), Span(8, 3), Span(8, 2))
    val next = candidates.firstNotNullOfOrNull { s ->
        placeInFirstFree(refs, spec, boardRef(def.id), s.w, s.h).takeIf { it.size > refs.size }
    } ?: refs
    return copy(boards = boards + def).withPlaced(face, page, next)
}

fun LayoutState.updateBoard(board: BoardDef): LayoutState =
    copy(boards = boards.map { if (it.id == board.id) board else it })

/** ボードとそのリンク、全ページの配置を消す */
fun LayoutState.deleteBoard(id: String): LayoutState {
    val board = boards.firstOrNull { it.id == id } ?: return this
    val ref = boardRef(id)
    return copy(
        boards = boards.filterNot { it.id == id },
        links = links.filterNot { it.id in board.links },
        layouts = layouts.map { l -> l.copy(refs = l.refs.filterNot { it.ref == ref }) },
    )
}

/** リンクを追加または更新し、ボードに紐付ける(id が空なら新規) */
fun LayoutState.upsertLink(boardId: String, link: LinkDef): LayoutState {
    val fixed = if (link.id.isBlank()) link.copy(id = newId("l")) else link
    val nextLinks = if (links.any { it.id == fixed.id }) links.map { if (it.id == fixed.id) fixed else it } else links + fixed
    val nextBoards = boards.map { b ->
        if (b.id == boardId && fixed.id !in b.links) b.copy(links = b.links + fixed.id) else b
    }
    return copy(links = nextLinks, boards = nextBoards)
}

fun LayoutState.deleteLink(linkId: String): LayoutState = copy(
    links = links.filterNot { it.id == linkId },
    boards = boards.map { b -> b.copy(links = b.links.filterNot { it == linkId }) },
)

/** ボードのリンクを定義に解決(消えた id は落とす) */
fun LayoutState.linksOf(board: BoardDef): List<LinkDef> {
    val byId = links.associateBy { it.id }
    return board.links.mapNotNull(byId::get)
}

/** 共有テキストから URL と名前を取り出す。URL が無ければ null */
fun parseSharedLink(text: String?, subject: String?): LinkDef? {
    val body = text.orEmpty()
    val url = Regex("""https?://\S+""").find(body)?.value?.trimEnd('.', ',', ')', '>') ?: return null
    val rest = body.replace(url, "").trim().lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }
    val name = subject?.trim()?.ifBlank { null } ?: rest ?: ""
    return LinkDef(id = "", name = name, url = url)
}

/** ボードを新設して、両面の WORK ページに置く(共有から初回に呼ばれる) */
fun LayoutState.addBoardOnWork(name: String): Pair<LayoutState, String> {
    val id = newId("b")
    var s = copy(boards = boards + BoardDef(id = id, name = name))
    for (face in Face.entries) {
        val spec = gridFor(face)
        val refs = s.placed(face, Page.WORK)
        val size = ModuleRegistry.specOf(boardRef(id))?.defaultSize?.invoke(face) ?: Span(2, 4)
        s = s.withPlaced(face, Page.WORK, placeInFirstFree(refs, spec, boardRef(id), size.w, size.h))
    }
    return s to id
}

// ---- タスク ----

fun LayoutState.addTask(task: TaskDef): LayoutState {
    val t = task.text.trim()
    if (t.isEmpty()) return this
    return copy(tasks = tasks + task.copy(id = newId("t"), text = t, createdAt = System.currentTimeMillis()))
}

fun LayoutState.updateTask(task: TaskDef): LayoutState =
    copy(tasks = tasks.map { if (it.id == task.id) task.copy(text = task.text.trim()) else it })

fun LayoutState.deleteTask(id: String): LayoutState = copy(tasks = tasks.filterNot { it.id == id })

fun LayoutState.toggleTask(id: String): LayoutState =
    copy(tasks = tasks.map { if (it.id == id) it.copy(done = !it.done) else it })

fun LayoutState.clearDoneTasks(): LayoutState = copy(tasks = tasks.filterNot { it.done })

/** 表示順: 未完了(期限が近い順、期限なしは後ろ、同じなら古い順) → 完了 */
fun LayoutState.tasksInOrder(): List<TaskDef> =
    tasks.sortedWith(compareBy({ it.done }, { it.due == null }, { it.due ?: "" }, { it.createdAt }))

/** そのページの配置から外す(定義は残る) */
fun LayoutState.removeRef(face: Face, page: Page, ref: String): LayoutState =
    withPlaced(face, page, placed(face, page).filterNot { it.ref == ref })

// ---- アプリパネルとラベル ----

fun LayoutState.addPanel(face: Face, page: Page, app: AppEntry): LayoutState {
    val def = PanelDef(id = newId("p"), app = appKey(app))
    val size = ModuleRegistry.specOf(panelRef(def.id))?.defaultSize?.invoke(face) ?: Span(4, 2)
    return copy(panels = panels + def)
        .withPlaced(face, page, placeInFirstFree(placed(face, page), gridFor(face), panelRef(def.id), size.w, size.h))
}

fun LayoutState.updatePanel(panel: PanelDef): LayoutState =
    copy(panels = panels.map { if (it.id == panel.id) panel else it })

fun LayoutState.addLabel(face: Face, page: Page, text: String): LayoutState {
    val def = LabelDef(id = newId("lb"), text = text)
    val spec = gridFor(face)
    val refs = placed(face, page)
    val half = spec.cols / 2
    // 半幅 → 全幅 → 1/4 → 1/8 の順に空きを探す(横に 2 列並べられるように半幅が既定)
    val next = listOf(half, spec.cols, half / 2, half / 4).map { it.coerceAtLeast(1) }.firstNotNullOfOrNull { w ->
        placeInFirstFree(refs, spec, labelRef(def.id), w, 1).takeIf { it.size > refs.size }
    } ?: refs
    return copy(labels = labels + def).withPlaced(face, page, next)
}

fun LayoutState.updateLabel(label: LabelDef): LayoutState =
    copy(labels = labels.map { if (it.id == label.id) label else it })

/** PRIVATE の初期構成: グループ見出しと、端末にあれば置くアプリ(パッケージ名の順) */
private val PrivateSeed: List<Pair<String, List<String>>> = listOf(
    "FITNESS" to listOf(
        "jp.kintorelog.app",                 // 筋トレログ
        "jp.mealmanagement.app",             // 食事管理
        "jp.healthplanet.healthplanetapp",   // 体重(HealthPlanet)
    ),
    "ASSETS" to listOf(
        "com.moneyforward.android.app",
        "jp.moneytree.moneytree",
        "jp.mufg.moneycanvas",
        "jp.co.rakutensec.shisankeisei",
        "jp.co.rakuten_bank.rakutenbank",
    ),
    "AI" to listOf(
        "com.openai.chatgpt",
        "com.anthropic.claude",
        "ai.perplexity.app.android",
        "ai.x.grok",
        "com.microsoft.copilot",
    ),
)

/**
 * PRIVATE ページを「時計 + 見出し + アプリパネル」で組み直す。
 * 定義の id は固定("seed-…")なので何度呼んでも重複しない。既存のフォルダは余った所に戻す。
 */
fun LayoutState.seedPrivate(face: Face, apps: List<AppEntry>): LayoutState {
    val byPkg = apps.associateBy { it.packageName }
    var labels = this.labels
    var panels = this.panels
    val spec = gridFor(face)
    var refs: List<PlacedRef> = emptyList()
    fun place(ref: String, minRow: Int = 0) {
        val size = ModuleRegistry.specOf(ref)?.defaultSize?.invoke(face) ?: Span(4, 2)
        refs = placeInFirstFreeFrom(refs, spec, ref, size.w, size.h, minRow)
    }
    place(REF_CLOCK)
    val seeded = mutableSetOf(REF_CLOCK)
    for ((title, pkgs) in PrivateSeed) {
        val found = pkgs.mapNotNull(byPkg::get)
        if (found.isEmpty()) continue
        val labelId = "seed-" + title.lowercase()
        if (labels.none { it.id == labelId }) labels = labels + LabelDef(labelId, title)
        place(labelRef(labelId))
        seeded += labelRef(labelId)
        // パネルは見出しの下の行から(左上の空きに吸われない)
        val below = refs.first { it.ref == labelRef(labelId) }.let { it.row + it.rowSpan }
        for (app in found) {
            val id = "seed-" + app.packageName
            if (panels.none { it.id == id }) panels = panels + PanelDef(id, appKey(app))
            place(panelRef(id), minRow = below)
            seeded += panelRef(id)
        }
    }
    // 床のタイル(METER)を一番下の 2 行に敷く。上段 BAT 6 | WIFI 5 | SIG 5、下段 MEM 6 | STO 5 | NTF 5
    var meters = this.meters
    val floor = listOf(
        listOf("battery" to 0, "signal" to 2, "wifi" to 1),
    )
    val unit = spec.cols / 16f
    floor.forEachIndexed { r, rowDefs ->
        val row = spec.rows - 2 + r
        var col = 0
        rowDefs.forEachIndexed { i, (metric, shade) ->
            val id = "seed-meter-$metric"
            if (meters.none { it.id == id }) meters = meters + MeterDef(id, metric, shade)
            val w = ((if (i == 0) 6 else 5) * unit).roundToInt().coerceAtLeast(1)
            val pos = GridPos(col, row, w, 1)
            if (canPlace(pos, spec, refs)) refs = refs + PlacedRef(meterRef(id), col, row, w, 1)
            seeded += meterRef(id)
            col += w
        }
    }
    // 元からあった他のモジュール(自分で足した物)は余った所へ。seed 由来で今回外れた物とフォルダは落とす
    val groupsEnd = refs.filterNot { it.ref.startsWith("meter:") }.maxOf { it.row + it.rowSpan }
    placed(face, Page.PRIVATE)
        .filter { it.ref !in seeded && !it.ref.contains(":seed-") && !it.ref.startsWith("folder:") }
        .forEach { place(it.ref, minRow = groupsEnd) }
    return copy(labels = labels, panels = panels, meters = meters).withPlaced(face, Page.PRIVATE, refs)
}

// ---- 床のタイル(METER) ----

const val METER_SHADES = 7

/** metric ごとの既定の色番号(配色の blocks の並びと対応) */
fun meterShadeFor(metric: String, fallback: Int): Int = when (metric) {
    "battery" -> 0; "wifi" -> 1; "signal" -> 2; "memory" -> 3; "storage" -> 4; "notif" -> 5; "bluetooth" -> 6
    else -> fallback
}

fun LayoutState.addMeter(face: Face, page: Page, metric: String): LayoutState {
    val shade = meterShadeFor(metric, meters.size % METER_SHADES)
    val def = MeterDef(id = newId("mt"), metric = metric, shade = shade, style = if (page == Page.WORK) "text" else "blocks")
    val size = ModuleRegistry.specOf(meterRef(def.id))?.defaultSize?.invoke(face) ?: Span(8, 1)
    return copy(meters = meters + def)
        .withPlaced(face, page, placeInFirstFree(placed(face, page), gridFor(face), meterRef(def.id), size.w, size.h))
}

/** 色を次に回す */
fun LayoutState.cycleMeterShade(id: String): LayoutState =
    copy(meters = meters.map { if (it.id == id) it.copy(shade = (it.shade + 1) % METER_SHADES) else it })

/**
 * 通信系の床(BAT / WIFI / SIG / NTF)を時計の右に集約し、MEM / STO は一番下の 1 行に置く。
 * 時計の右に空きが無ければ、その 4 つは左上から順の空きに落とす。
 */
fun LayoutState.stackStatusBesideClock(face: Face, page: Page): LayoutState {
    val spec = gridFor(face)
    val ids = meters.associateBy { it.metric }
    val statusRefs = listOf("battery", "wifi", "signal", "notif").mapNotNull { ids[it]?.let { d -> meterRef(d.id) } }
    val bottomRefs = listOf("memory", "storage").mapNotNull { ids[it]?.let { d -> meterRef(d.id) } }
    var refs = placed(face, page).filterNot { it.ref in statusRefs || it.ref in bottomRefs }
    val clock = refs.firstOrNull { it.ref == REF_CLOCK }
    val left = clock?.let { it.col + it.colSpan } ?: 0
    val top = clock?.row ?: 0
    val w = ((spec.cols - left) / 2).coerceAtLeast(1)
    statusRefs.forEachIndexed { i, ref ->
        val pos = GridPos(left + (i % 2) * w, top + i / 2, w, 1)
        refs = if (canPlace(pos, spec, refs)) refs + PlacedRef(ref, pos.col, pos.row, pos.colSpan, pos.rowSpan)
        else placeInFirstFree(refs, spec, ref, w, 1)
    }
    val half = spec.cols / 2
    bottomRefs.forEachIndexed { i, ref ->
        val pos = GridPos(i * half, spec.rows - 1, half, 1)
        refs = if (canPlace(pos, spec, refs)) refs + PlacedRef(ref, pos.col, pos.row, pos.colSpan, pos.rowSpan)
        else placeInFirstFree(refs, spec, ref, half, 1)
    }
    return withPlaced(face, page, refs)
}

/**
 * 時計の右に詰める: 上段 BAT WIFI SIG BT(2 マスずつ)、下段 NTF(4) MEM STO(2 ずつ)。
 * BT の定義が無ければ作る。空きが無ければ左上から順の空きに落とす。
 */
fun LayoutState.stackStatusCompact(face: Face, page: Page): LayoutState {
    var s = this
    if (s.meters.none { it.metric == "bluetooth" }) {
        s = s.copy(meters = s.meters + MeterDef("seed-meter-bluetooth", "bluetooth", meterShadeFor("bluetooth", 6)))
    }
    val spec = gridFor(face)
    val byMetric = s.meters.associateBy { it.metric }
    val rows = listOf(
        listOf("battery" to 2, "signal" to 2, "wifi" to 2, "bluetooth" to 2),
    )
    val all = rows.flatten().mapNotNull { (m, _) -> byMetric[m]?.let { meterRef(it.id) } }.toSet()
    var refs = s.placed(face, page).filterNot { it.ref in all }
    val clock = refs.firstOrNull { it.ref == REF_CLOCK }
    val left = clock?.let { it.col + it.colSpan } ?: 0
    val top = clock?.row ?: 0
    rows.forEachIndexed { r, row ->
        var col = left
        for ((metric, w) in row) {
            val def = byMetric[metric] ?: continue
            val ref = meterRef(def.id)
            val pos = GridPos(col, top + r, w, 1)
            refs = if (canPlace(pos, spec, refs)) refs + PlacedRef(ref, pos.col, pos.row, w, 1)
            else placeInFirstFree(refs, spec, ref, w, 1)
            col += w
        }
    }
    return s.withPlaced(face, page, refs)
}

/**
 * WORK の右上(1 行目の右端)に文字表示の BAT WIFI SIG BT を 2 マスずつ置く。
 * 定義は WORK 専用("work-meter-…"、style = text)。重なっているモジュールは空きへ退ける。
 */
fun LayoutState.seedWorkStatus(face: Face): LayoutState {
    var s = this
    val metrics = listOf("battery", "wifi", "signal", "bluetooth")
    for (m in metrics) {
        val id = "work-meter-$m"
        if (s.meters.none { it.id == id }) s = s.copy(meters = s.meters + MeterDef(id, m, meterShadeFor(m, 0), style = "text"))
    }
    val spec = gridFor(face)
    val page = Page.WORK
    val w = 2
    val targets = metrics.mapIndexed { i, m -> meterRef("work-meter-$m") to GridPos(spec.cols - w * (metrics.size - i), 0, w, 1) }
    var refs = s.placed(face, page).filterNot { r -> targets.any { it.first == r.ref } }
    // 重なる物を外して、後で空きへ戻す
    val evicted = refs.filter { r -> targets.any { it.second.overlaps(r.pos) } }
    refs = refs.filterNot { it in evicted }
    targets.forEach { (ref, pos) -> refs = refs + PlacedRef(ref, pos.col, pos.row, pos.colSpan, pos.rowSpan) }
    evicted.forEach { r -> refs = placeInFirstFree(refs, spec, r.ref, r.colSpan, r.rowSpan) }
    return s.withPlaced(face, page, refs)
}

// ---- リンク(単体モジュール) ----

/** リンクを 1 本作って現ページの空きに置く(半幅) */
fun LayoutState.addLink(face: Face, page: Page, link: LinkDef): LayoutState {
    val def = if (link.id.isBlank()) link.copy(id = newId("l")) else link
    val spec = gridFor(face)
    val refs = placed(face, page)
    val half = spec.cols / 2
    val next = listOf(half, spec.cols, half / 2).map { it.coerceAtLeast(1) }.firstNotNullOfOrNull { w ->
        placeInFirstFree(refs, spec, linkRef(def.id), w, 1).takeIf { it.size > refs.size }
    } ?: refs
    val links2 = if (links.any { it.id == def.id }) links.map { if (it.id == def.id) def else it } else links + def
    return copy(links = links2).withPlaced(face, page, next)
}

fun LayoutState.updateLink(link: LinkDef): LayoutState =
    copy(links = links.map { if (it.id == link.id) link else it })

/** 共有から: 両面の WORK に置く */
fun LayoutState.addLinkOnWork(link: LinkDef): LayoutState {
    val def = if (link.id.isBlank()) link.copy(id = newId("l")) else link
    var s = this
    for (face in Face.entries) s = s.addLink(face, Page.WORK, def)
    return s
}

/** ボードの枠を外し、中のリンクを 1 本ずつ link モジュールに展開する(ボードのあった場所から下へ) */
fun LayoutState.explodeBoards(): LayoutState {
    val boardsById = boards.associateBy { it.id }
    return copy(layouts = layouts.map { l ->
        val spec = gridFor(l.face)
        var refs = l.refs.filterNot { it.ref.startsWith("board:") }
        l.refs.filter { it.ref.startsWith("board:") }.forEach { b ->
            val board = boardsById[b.ref.removePrefix("board:")] ?: return@forEach
            val w = b.colSpan.coerceAtLeast(1)
            var row = b.row
            board.links.forEach { lid ->
                if (links.none { it.id == lid }) return@forEach
                val ref = linkRef(lid)
                if (refs.any { it.ref == ref }) return@forEach
                val pos = GridPos(b.col, row, w, 1)
                refs = if (canPlace(pos, spec, refs)) refs + PlacedRef(ref, pos.col, pos.row, w, 1)
                else placeInFirstFreeFrom(refs, spec, ref, w, 1, row)
                row = (refs.firstOrNull { it.ref == ref }?.row ?: row) + 1
            }
        }
        l.copy(refs = refs)
    })
}

/** 指定の metric のブロック計測(文字表示は除く)を、定義と全面・全ページの配置から消す */
fun LayoutState.dropMeters(metrics: Set<String>): LayoutState {
    val ids = meters.filter { it.metric in metrics && it.style != "text" }.map { meterRef(it.id) }.toSet()
    if (ids.isEmpty()) return this
    return copy(
        meters = meters.filterNot { meterRef(it.id) in ids },
        layouts = layouts.map { l -> l.copy(refs = l.refs.filterNot { it.ref in ids }) },
    )
}

/** 2 つの metric のブロック計測の置き場所を、全面・全ページで入れ替える(大きさが同じ時だけ) */
fun LayoutState.swapMeters(a: String, b: String): LayoutState {
    val ra = meters.filter { it.metric == a && it.style != "text" }.map { meterRef(it.id) }.toSet()
    val rb = meters.filter { it.metric == b && it.style != "text" }.map { meterRef(it.id) }.toSet()
    return copy(layouts = layouts.map { l ->
        val pa = l.refs.firstOrNull { it.ref in ra }
        val pb = l.refs.firstOrNull { it.ref in rb }
        if (pa == null || pb == null || pa.colSpan != pb.colSpan || pa.rowSpan != pb.rowSpan) l
        else l.copy(refs = l.refs.map {
            when (it.ref) {
                pa.ref -> it.copy(col = pb.col, row = pb.row)
                pb.ref -> it.copy(col = pa.col, row = pa.row)
                else -> it
            }
        })
    })
}

/**
 * [from] の配置を [to] のマス目に流し直す。[to] に既に配置があれば何もしない。
 * 縦の並び(見出し → その下のアプリ)を保つため、[to] を幅 8 の縦の帯に分け、
 * 元の (行, 列) 順に左の帯から上へ詰め、入らなければ次の帯へ。どの帯にも入らなければ全体の最初の空きへ
 */
fun LayoutState.packInto(from: Face, to: Face, page: Page): LayoutState {
    if (placed(to, page).isNotEmpty()) return this
    val spec = gridFor(to)
    val bandW = 8
    val bands = (spec.cols / bandW).coerceAtLeast(1)
    var refs = emptyList<PlacedRef>()
    // 元の左の列(col < 8)を先に、次に右の列。列の中は上から
    for (r in placed(from, page).sortedWith(compareBy({ it.col / bandW }, { it.row }, { it.col }))) {
        val w = r.colSpan.coerceIn(1, spec.cols)
        val h = r.rowSpan.coerceIn(1, spec.rows)
        var done = false
        if (w <= bandW) {
            band@ for (b in 0 until bands) {
                val col = b * bandW
                for (row in 0..(spec.rows - h)) {
                    val pos = GridPos(col, row, w, h)
                    if (canPlace(pos, spec, refs)) { refs = refs + PlacedRef(r.ref, col, row, w, h); done = true; break@band }
                }
            }
        }
        if (!done) refs = placeInFirstFree(refs, spec, r.ref, w, h)
    }
    return withPlaced(to, page, refs)
}

/**
 * SIDE(メインの右側)の初期配置: 予定とタスク。apps を渡すと上 2 行にアプリ 4 本も置く(既定は置かない)。既に配置があれば何もしない
 */
/** v23 で SIDE に置いていたアプリ(v24 で外した。左パネルから右に窓を出す方式に)。apps を渡した時だけ使う */
private val SideSeedApps = listOf("com.android.chrome", "com.google.android.gm", "jp.naver.line.android", "com.google.android.apps.maps")

fun LayoutState.seedSide(page: Page, apps: List<AppEntry> = emptyList()): LayoutState {
    if (placed(Face.SIDE, page).isNotEmpty()) return this
    val spec = gridFor(Face.SIDE)
    var panels = this.panels
    var refs = emptyList<PlacedRef>()
    val half = spec.cols / 2
    val found = SideSeedApps.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    found.forEachIndexed { i, app ->
        val id = "side-" + app.packageName
        if (panels.none { it.id == id }) panels = panels + PanelDef(id, appKey(app))
        refs = refs + PlacedRef(panelRef(id), (i % 2) * half, i / 2, half, 1)
    }
    val top = if (found.isEmpty()) 0 else (found.size + 1) / 2
    val rest = spec.rows - top
    refs = refs + PlacedRef(REF_CALENDAR, 0, top, spec.cols, rest / 2)
    refs = refs + PlacedRef(REF_TASKS, 0, top + rest / 2, spec.cols, rest - rest / 2)
    return copy(panels = panels).withPlaced(Face.SIDE, page, refs)
}
