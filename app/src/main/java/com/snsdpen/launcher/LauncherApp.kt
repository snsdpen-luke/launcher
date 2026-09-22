package com.snsdpen.launcher

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snsdpen.launcher.data.launchApp
import com.snsdpen.launcher.data.openCalendarApp
import com.snsdpen.launcher.data.openEvent
import com.snsdpen.launcher.data.openLink
import com.snsdpen.launcher.model.LabelDef
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.Surface
import com.snsdpen.launcher.model.Face
import com.snsdpen.launcher.model.addBoard
import com.snsdpen.launcher.model.addLabel
import com.snsdpen.launcher.model.addLink
import com.snsdpen.launcher.model.updateLink
import com.snsdpen.launcher.model.addPanel
import com.snsdpen.launcher.model.addTask
import com.snsdpen.launcher.model.addMeter
import com.snsdpen.launcher.model.cycleMeterShade
import com.snsdpen.launcher.model.appKey
import com.snsdpen.launcher.model.clearDoneTasks
import com.snsdpen.launcher.model.deleteBoard
import com.snsdpen.launcher.model.deleteLink
import com.snsdpen.launcher.model.deleteTask
import com.snsdpen.launcher.model.faceOf
import com.snsdpen.launcher.model.gridFor
import com.snsdpen.launcher.model.linksOf
import com.snsdpen.launcher.model.removeRef
import com.snsdpen.launcher.model.resizeRef
import com.snsdpen.launcher.model.resolved
import com.snsdpen.launcher.model.toggleTask
import com.snsdpen.launcher.model.updateBoard
import com.snsdpen.launcher.model.updateLabel
import com.snsdpen.launcher.model.updatePanel
import com.snsdpen.launcher.model.updateTask
import com.snsdpen.launcher.model.upsertLink
import com.snsdpen.launcher.notif.NotifListener
import com.snsdpen.launcher.ui.AddSheet
import com.snsdpen.launcher.ui.AppDrawer
import com.snsdpen.launcher.ui.AppPickerSheet
import com.snsdpen.launcher.ui.BoardEditSheet
import com.snsdpen.launcher.ui.FolderOverlay
import com.snsdpen.launcher.ui.HomeScreen
import com.snsdpen.launcher.ui.HomePane
import com.snsdpen.launcher.ui.LabelDialog
import com.snsdpen.launcher.ui.LinkDialog
import com.snsdpen.launcher.model.LinkDef
import com.snsdpen.launcher.ui.LocalRefreshTick
import com.snsdpen.launcher.ui.PageTheme
import com.snsdpen.launcher.ui.PanelEditSheet
import com.snsdpen.launcher.ui.ThemeOptions
import com.snsdpen.launcher.ui.themeOf
import com.snsdpen.launcher.ui.nextTheme
import com.snsdpen.launcher.ui.TaskDialog
import com.snsdpen.launcher.ui.MeterPickerSheet
import com.snsdpen.launcher.ui.paletteFor
import kotlinx.coroutines.launch
import kotlin.math.abs

/** ページ切替とみなす横移動 / ドロワーとみなす上移動(px) */
private const val PAGE_SWIPE_PX = 100f
private const val DRAWER_SWIPE_PX = 120f

/** ランチャー本体。どの窓(surface)から呼ばれても同じ Composable */
@Composable
fun LauncherApp(surface: Surface) {
    val context = LocalContext.current
    val store = remember { context.launcherStore }
    val coroutineScope = rememberCoroutineScope()
    val apps by store.apps.collectAsStateWithLifecycle()
    val layout by store.layout.collectAsStateWithLifecycle()
    val page by store.page.collectAsStateWithLifecycle()
    val themes by store.themes.collectAsStateWithLifecycle()
    val notifCounts by NotifListener.counts.collectAsStateWithLifecycle()

    // メイン/カバーは smallestScreenWidthDp で判定(幅だと横向きカバーを誤判定する)
    val isWide = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val face0 = faceOf(surface, isWide, isLandscape)
    // メイン(開いた時)は左に MAIN(独立した配置。カバーと同じ形)、右に SIDE(窓の置き場)を並べる
    val split = face0 == Face.MAIN
    val face = face0
    val spec = gridFor(face)
    // 選択中のモジュールがどちらのグリッドの物か(追加・削除の宛先)
    var selectedFace by remember { mutableStateOf(Face.MAIN) }
    // サイドパネルの画面上の矩形(px)。無ければ画面の右半分
    var sideBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val activeFace = if (split) selectedFace else face

    // 復帰時: アプリ一覧を読み直し、外部データ(予定)の再読込を合図する
    var refreshTick by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        coroutineScope.launch { store.refreshApps() }
        NotifListener.ensureBound(context)
        refreshTick++
        onPauseOrDispose { }
    }
    // 権限リクエスト(Activity の上でだけ使える。オーバーレイ面では別経路が要る)
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTick++ }
    // 再生情報(メディアセッション)は表示中だけ読む
    com.snsdpen.launcher.ui.WhileResumed(Unit) {
        com.snsdpen.launcher.data.MediaNow.start(context)
        try { kotlinx.coroutines.awaitCancellation() } finally { com.snsdpen.launcher.data.MediaNow.stop() }
    }
    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) com.snsdpen.launcher.data.toggleBluetooth(context) }

    var editMode by remember { mutableStateOf(false) }
    var selectedRef by remember { mutableStateOf<String?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var editBoardId by remember { mutableStateOf<String?>(null) }
    var addingTask by remember { mutableStateOf(false) }
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    var addSheet by remember { mutableStateOf(false) }
    var tilePicker by remember { mutableStateOf(false) }
    /** "new" = 新しいパネル、それ以外 = 差し替えるパネルの id */
    var pickAppFor by remember { mutableStateOf<String?>(null) }
    var editPanelId by remember { mutableStateOf<String?>(null) }
    /** id 空 = 新規 */
    var labelDialog by remember { mutableStateOf<LabelDef?>(null) }
    /** id 空 = 新規 */
    var linkDialog by remember { mutableStateOf<LinkDef?>(null) }
    // 直前のページ移動の向き(アニメの向きに使う)
    var forward by remember { mutableStateOf(true) }

    BackHandler(enabled = editMode) {
        editMode = false
        selectedRef = null
    }

    val onEvent: (ModuleEvent) -> Unit = { e ->
        when (e) {
            is ModuleEvent.OpenFolder -> openFolderId = e.id
            is ModuleEvent.Launch -> launchApp(context, e.app)
            is ModuleEvent.MediaControl -> com.snsdpen.launcher.data.MediaNow.control(e.pkg, e.action)
            is ModuleEvent.LaunchInPane -> {
                val dm = context.resources.displayMetrics
                val pane = sideBounds ?: android.graphics.Rect(dm.widthPixels / 2, 0, dm.widthPixels, dm.heightPixels)
                // 窓はアプリの行(panel)が並ぶ高さより下に出す。行が隠れず、押し直せる。残りが 4 割未満なら全面
                val sideSpec = gridFor(Face.SIDE)
                val gapPx = 4f * dm.density
                val cellPx = (pane.height() - gapPx * (sideSpec.rows - 1)) / sideSpec.rows
                val lowestPanelRow = layout?.placed(Face.SIDE, page).orEmpty().filter { r -> r.ref.startsWith("panel:") }.maxOfOrNull { r -> r.row + r.rowSpan } ?: 0
                val top = pane.top + (lowestPanelRow * (cellPx + gapPx)).toInt()
                val bounds = if (lowestPanelRow > 0 && pane.bottom - top >= pane.height() * 0.4f) android.graphics.Rect(pane.left, top, pane.right, pane.bottom) else pane
                launchApp(context, e.app, bounds)
            }
            is ModuleEvent.OpenIntent -> runCatching {
                context.startActivity(e.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            is ModuleEvent.OpenLink -> openLink(context, e.url)
            is ModuleEvent.EditBoard -> editBoardId = e.id
            is ModuleEvent.EditPanel -> editPanelId = e.id
            is ModuleEvent.EditLabel -> labelDialog = layout?.labels?.firstOrNull { it.id == e.id }
            is ModuleEvent.EditLink -> linkDialog = layout?.links?.firstOrNull { it.id == e.id }
            is ModuleEvent.CycleMeter -> store.update { it.cycleMeterShade(e.id) }
            ModuleEvent.OpenNotifAccess -> NotifListener.openSettings(context)
            is ModuleEvent.SetBrightness -> if (!com.snsdpen.launcher.data.setBrightness(context, e.level)) runCatching { context.startActivity(com.snsdpen.launcher.data.writeSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            is ModuleEvent.SetVolume -> com.snsdpen.launcher.data.setVolume(context, e.level)
            ModuleEvent.RequestWriteSettings -> runCatching { context.startActivity(com.snsdpen.launcher.data.writeSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            ModuleEvent.ToggleBluetooth -> {
                val granted = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) com.snsdpen.launcher.data.toggleBluetooth(context) else btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            ModuleEvent.EnterEdit -> { editMode = true; selectedRef = null }
            ModuleEvent.OpenCalendar -> openCalendarApp(context)
            ModuleEvent.RequestCalendarPermission -> calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            is ModuleEvent.OpenEvent -> openEvent(context, e.event)
            ModuleEvent.AddTask -> addingTask = true
            is ModuleEvent.ToggleTask -> store.update { it.toggleTask(e.id) }
            is ModuleEvent.EditTask -> editingTaskId = e.id
            ModuleEvent.ClearDoneTasks -> store.update { it.clearDoneTasks() }
        }
    }

    // ジェスチャ検出器は作り直さず、最新値は rememberUpdatedState から読む
    val pageLatest = rememberUpdatedState(page)
    val goPage = rememberUpdatedState<(Boolean) -> Unit> { next ->
        forward = next
        store.setPage(if (next) pageLatest.value.next() else pageLatest.value.prev())
    }
    val openDrawer = rememberUpdatedState { drawerOpen = true }

    // ページの地色を補間し、白基調ではステータスバーのアイコンを黒にする
    val palette = paletteFor(page, themes[page])
    val bg by animateColorAsState(palette.bg, tween(220), label = "bg")
    val view = LocalView.current
    SideEffect {
        var c: android.content.Context? = view.context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        (c as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = palette.isLight
                isAppearanceLightNavigationBars = palette.isLight
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            // IME は含めない(含めるとキーボードが出た瞬間にグリッド全体が縮む)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        val state = layout ?: return@Box   // 読み込み前は背景のみ
        CompositionLocalProvider(LocalRefreshTick provides refreshTick) {

        Box(
            Modifier
                .fillMaxSize()
                .then(
                    // 横スワイプ=ページ切替(ループ)、上スワイプ=ドロワー。編集モード中は付けない
                    if (!editMode) Modifier.pointerInput(Unit) {
                        var dx = 0f
                        var dy = 0f
                        detectDragGestures(
                            onDragStart = { dx = 0f; dy = 0f },
                            onDrag = { change, amount ->
                                dx += amount.x
                                dy += amount.y
                                change.consume()
                            },
                            onDragEnd = {
                                when {
                                    abs(dx) > abs(dy) && abs(dx) > PAGE_SWIPE_PX -> goPage.value(dx < 0)
                                    dy < -DRAWER_SWIPE_PX -> openDrawer.value()
                                }
                            },
                        )
                    } else Modifier
                ),
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(220)) { dir * it } togetherWith
                        slideOutHorizontally(tween(220)) { -dir * it })
                },
                label = "page",
            ) { p ->
                PageTheme(p, themes[p]) {
                HomeScreen(
                    placed = state.resolved(face, p, surface),
                    spec = spec,
                    face = face,
                    page = p,
                    surface = surface,
                    layout = state,
                    apps = apps,
                    editMode = editMode,
                    selectedRef = selectedRef,
                    onSelect = { selectedRef = it; selectedFace = face },
                    onEnterEdit = { editMode = true; selectedRef = null },
                    onExitEdit = { editMode = false; selectedRef = null },
                    onAdd = { addSheet = true },
                    onRemoveSelected = {
                        selectedRef?.let { ref -> store.update { it.removeRef(activeFace, p, ref) } }
                        selectedRef = null
                    },
                    themeName = if ((ThemeOptions[p]?.size ?: 1) > 1) themeOf(p, themes[p]).name else null,
                    onCycleTheme = { store.setTheme(p, nextTheme(p, themes[p])) },
                    onOpenDrawer = { drawerOpen = true },
                    onEvent = onEvent,
                    notifCounts = notifCounts,
                    onArrange = { refs -> store.update { it.withPlaced(face, p, refs) } },
                    onResize = { ref, w, h -> store.update { it.resizeRef(face, p, ref, w, h) } },
                    side = if (split) HomePane(
                        face = Face.SIDE, spec = gridFor(Face.SIDE), placed = state.resolved(Face.SIDE, p, surface),
                        onSelect = { selectedRef = it; selectedFace = Face.SIDE },
                        onArrange = { refs -> store.update { it.withPlaced(Face.SIDE, p, refs) } },
                        onResize = { ref, w, h -> store.update { it.resizeRef(Face.SIDE, p, ref, w, h) } },
                        onBounds = { sideBounds = it },
                    ) else null,
                )
                }
            }
        }

        PageTheme(page, themes[page]) {
        openFolderId?.let { id ->
            val folder = state.folders.firstOrNull { it.id == id }
            if (folder == null) {
                openFolderId = null
            } else {
                val byKey = apps.associateBy { "${it.packageName}/${it.activityName}" }
                FolderOverlay(
                    folder = folder,
                    apps = folder.apps.mapNotNull(byKey::get),
                    onLaunch = { launchApp(context, it); openFolderId = null },
                    onClose = { openFolderId = null },
                )
            }
        }

        editBoardId?.let { id ->
            val board = state.boards.firstOrNull { it.id == id }
            if (board == null) {
                editBoardId = null
            } else {
                BoardEditSheet(
                    board = board,
                    links = state.linksOf(board),
                    onRename = { n -> store.update { it.updateBoard(board.copy(name = n)) } },
                    onUpsertLink = { l -> store.update { it.upsertLink(id, l) } },
                    onDeleteLink = { lid -> store.update { it.deleteLink(lid) } },
                    onDeleteBoard = { store.update { it.deleteBoard(id) }; editBoardId = null },
                    onClose = { editBoardId = null },
                )
            }
        }

        if (addingTask) {
            TaskDialog(
                initial = null,
                onSave = { t -> store.update { it.addTask(t) }; addingTask = false },
                onCancel = { addingTask = false },
            )
        }
        editingTaskId?.let { id ->
            val task = state.tasks.firstOrNull { it.id == id }
            if (task == null) {
                editingTaskId = null
            } else {
                TaskDialog(
                    initial = task,
                    onSave = { t -> store.update { it.updateTask(t) }; editingTaskId = null },
                    onDelete = { store.update { it.deleteTask(id) }; editingTaskId = null },
                    onCancel = { editingTaskId = null },
                )
            }
        }

        if (addSheet) {
            AddSheet(
                onApp = { addSheet = false; pickAppFor = "new" },
                onLabel = { addSheet = false; labelDialog = LabelDef("", "") },
                onTile = { addSheet = false; tilePicker = true },
                onLink = { addSheet = false; linkDialog = LinkDef("", "", "") },
                onClose = { addSheet = false },
            )
        }

        if (tilePicker) {
            MeterPickerSheet(
                onPick = { m -> store.update { it.addMeter(activeFace, page, m) }; tilePicker = false },
                onClose = { tilePicker = false },
            )
        }

        editPanelId?.let { id ->
            val panel = state.panels.firstOrNull { it.id == id }
            if (panel == null) {
                editPanelId = null
            } else {
                PanelEditSheet(
                    panel = panel,
                    app = apps.firstOrNull { appKey(it) == panel.app },
                    onRename = { n -> store.update { it.updatePanel(panel.copy(name = n)) } },
                    onChangeApp = { pickAppFor = id },
                    onClose = { editPanelId = null },
                )
            }
        }

        pickAppFor?.let { target ->
            AppPickerSheet(
                apps = apps,
                onPick = { app ->
                    if (target == "new") store.update { it.addPanel(activeFace, page, app) }
                    else store.update { s -> s.panels.firstOrNull { it.id == target }?.let { s.updatePanel(it.copy(app = appKey(app))) } ?: s }
                    pickAppFor = null
                },
                onClose = { pickAppFor = null },
            )
        }

        linkDialog?.let { def ->
            LinkDialog(
                initial = def,
                onSave = { l ->
                    if (def.id.isBlank()) store.update { it.addLink(activeFace, page, l) } else store.update { it.updateLink(l) }
                    linkDialog = null
                },
                onCancel = { linkDialog = null },
            )
        }

        labelDialog?.let { def ->
            LabelDialog(
                initial = def,
                onSave = { text ->
                    if (def.id.isBlank()) store.update { it.addLabel(activeFace, page, text) }
                    else store.update { it.updateLabel(def.copy(text = text)) }
                    labelDialog = null
                },
                onCancel = { labelDialog = null },
            )
        }

        if (drawerOpen) {
            AppDrawer(
                apps = apps,
                onLaunch = { launchApp(context, it); drawerOpen = false },
                onClose = { drawerOpen = false },
            )
        }
        }
        }
    }
}
