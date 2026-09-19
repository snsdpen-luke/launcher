package com.snsdpen.launcher.model

import android.content.Intent
import androidx.compose.runtime.Composable
import com.snsdpen.launcher.data.AppEntry
import com.snsdpen.launcher.ui.modules.BoardSpec
import com.snsdpen.launcher.ui.modules.CalendarSpec
import com.snsdpen.launcher.ui.modules.TasksSpec
import com.snsdpen.launcher.ui.modules.MeterSpec
import com.snsdpen.launcher.ui.modules.ClockSpec
import com.snsdpen.launcher.ui.modules.FolderSpec
import com.snsdpen.launcher.ui.modules.LabelSpec
import com.snsdpen.launcher.ui.modules.PanelSpec

/**
 * モジュール描画に渡す文脈。モジュールは状態を持たず、ここから読むだけ。
 * タップ/長押し/ドラッグはグリッド側のラッパーが一元処理する。
 */
class ModuleScope(
    val ref: String,
    /** "folder:abc" → "abc"。単一モジュールは null */
    val id: String?,
    val face: Face,
    val page: Page,
    val surface: Surface,
    val span: Span,
    val editMode: Boolean,
    val layout: LayoutState,
    val apps: List<AppEntry>,
    /** モジュール内部の操作(ボードの行タップ等)から上へ投げる */
    val emit: (ModuleEvent) -> Unit,
    /** 通知の件数(package → 件数)。通知アクセスが無ければ空 */
    val notifCounts: Map<String, Int> = emptyMap(),
)

/** モジュールが上へ投げるイベント。処理するのは LauncherApp */
sealed interface ModuleEvent {
    data class OpenFolder(val id: String) : ModuleEvent
    data class Launch(val app: AppEntry) : ModuleEvent
    data class OpenIntent(val intent: Intent) : ModuleEvent
    data class OpenLink(val url: String) : ModuleEvent
    data class EditBoard(val id: String) : ModuleEvent
    data class EditPanel(val id: String) : ModuleEvent
    data class EditLabel(val id: String) : ModuleEvent
    /** 床のタイル(METER)の色を次に回す */
    data class CycleMeter(val id: String) : ModuleEvent
    /** 「通知へのアクセス」の設定画面を開く */
    data object OpenNotifAccess : ModuleEvent
    /** モジュール内部の長押しから編集モードへ */
    data object EnterEdit : ModuleEvent
    data object OpenCalendar : ModuleEvent
    data object RequestCalendarPermission : ModuleEvent
    data class OpenEvent(val event: com.snsdpen.launcher.data.EventItem) : ModuleEvent
    /** タスクの追加ダイアログを開く */
    data object AddTask : ModuleEvent
    data class ToggleTask(val id: String) : ModuleEvent
    data class EditTask(val id: String) : ModuleEvent
    data object ClearDoneTasks : ModuleEvent
}

/**
 * モジュール 1 種の定義。描画・タップ・サイズ・存在判定をここに集約し、
 * 追加は「ui/modules に 1 ファイル + [ModuleRegistry.all] に 1 行」で済ませる。
 *
 * サイズは Face(グリッド寸法が違う)で引き、出せるかどうかは Surface で引く。
 */
data class ModuleSpec(
    val kind: String,
    val name: String,
    /** true なら ref == kind。false なら ref == "kind:<id>" */
    val singleton: Boolean = true,
    val surfaces: Set<Surface> = Surface.entries.toSet(),
    /** 出せるページ(例: 速度計は DRIVE のみ) */
    val pages: Set<Page> = Page.entries.toSet(),
    val defaultSize: (Face) -> Span,
    val minSize: (Face) -> Span = { Span(1, 1) },
    /** 参照先が生きているか(フォルダ削除など)。単一モジュールは常に true */
    val exists: (LayoutState, String?) -> Boolean = { _, _ -> true },
    /** 通常モードのタップ。編集モードではグリッドが選択に使うため呼ばれない */
    val onTap: (ModuleScope, (ModuleEvent) -> Unit) -> Unit = { _, _ -> },
    /** 編集モードで選択中にもう一度タップ。null なら選択解除のみ */
    val onEdit: ((ModuleScope, (ModuleEvent) -> Unit) -> Unit)? = null,
    val content: @Composable (ModuleScope) -> Unit,
)

object ModuleRegistry {
    val all: List<ModuleSpec> = listOf(
        ClockSpec,
        FolderSpec,
        BoardSpec,
        PanelSpec,
        LabelSpec,
        MeterSpec,
        CalendarSpec,
        TasksSpec,
    )
    private val byKind = all.associateBy { it.kind }

    fun kindOf(ref: String) = ref.substringBefore(':')
    fun idOf(ref: String): String? = ref.substringAfter(':', "").ifEmpty { null }
    fun specOf(ref: String): ModuleSpec? = byKind[kindOf(ref)]
}
