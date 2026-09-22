package com.snsdpen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.BuildConfig
import com.snsdpen.launcher.data.AppEntry
import com.snsdpen.launcher.model.Face
import com.snsdpen.launcher.model.GridPos
import com.snsdpen.launcher.model.GridSpec
import com.snsdpen.launcher.model.LayoutState
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.Page
import com.snsdpen.launcher.model.PlacedRef
import com.snsdpen.launcher.model.Surface

/**
 * 1 ページ分のグリッド + フッター(ドロワーのハンドル / 編集モードの DONE / ページ名)。
 * ページ切替と上スワイプのジェスチャは LauncherApp 側で受ける。
 */
/** 2 枚目のグリッド(メインの空いた側)。面と配置と書き戻しを 1 組で渡す */
data class HomePane(
    val face: Face,
    val spec: GridSpec,
    val placed: List<PlacedRef>,
    val onSelect: (String?) -> Unit,
    val onArrange: (List<PlacedRef>) -> Unit,
    val onResize: (String, Int, Int) -> Unit,
    /** パネルの画面上の矩形(px)。アプリをこの位置のポップアップで開くのに使う */
    val onBounds: (android.graphics.Rect) -> Unit = {},
)

@Composable
fun HomeScreen(
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
    onExitEdit: () -> Unit,
    onAdd: () -> Unit,
    onRemoveSelected: () -> Unit,
    /** 今の色味の名前(候補が 1 つなら null で名前を出さない) */
    themeName: String?,
    onCycleTheme: () -> Unit,
    /** 色の組をシャッフルできる配色なら true(フッター右に■を出す) */
    onOpenDrawer: () -> Unit,
    onEvent: (ModuleEvent) -> Unit,
    notifCounts: Map<String, Int> = emptyMap(),
    onArrange: (List<PlacedRef>) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    /** メイン(開いた時): 左にカバー縦の配置そのまま、右にこのパネル */
    side: HomePane? = null,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    Column(modifier.fillMaxSize().background(p.bg)) {
        if (side != null) {
            // カバー縦のマス目を高さいっぱいに置き、残りの幅を側パネルに
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                val cell = ((maxHeight - com.snsdpen.launcher.ui.GridGap * (spec.rows - 1)) / spec.rows).coerceAtLeast(0.dp)
                val coverW = cell * spec.cols + com.snsdpen.launcher.ui.GridGap * (spec.cols - 1)
                Row(Modifier.fillMaxSize()) {
                    ModuleGrid(
                        placed = placed, spec = spec, face = face, page = page, surface = surface, layout = layout, apps = apps,
                        editMode = editMode, selectedRef = selectedRef, onSelect = onSelect, onEnterEdit = onEnterEdit, onEvent = onEvent,
                        notifCounts = notifCounts, onArrange = onArrange, onResize = onResize,
                        modifier = Modifier.width(coverW).fillMaxHeight(),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(16.dp))
                    ModuleGrid(
                        placed = side.placed, spec = side.spec, face = side.face, page = page, surface = surface, layout = layout, apps = apps,
                        editMode = editMode, selectedRef = selectedRef, onSelect = side.onSelect, onEnterEdit = onEnterEdit, onEvent = onEvent,
                        notifCounts = notifCounts, onArrange = side.onArrange, onResize = side.onResize,
                        modifier = Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { c ->
                            val b = c.boundsInWindow()
                            side.onBounds(android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()))
                        },
                    )
                }
            }
        } else ModuleGrid(
            placed = placed,
            spec = spec,
            face = face,
            page = page,
            surface = surface,
            layout = layout,
            apps = apps,
            editMode = editMode,
            selectedRef = selectedRef,
            onSelect = onSelect,
            onEnterEdit = onEnterEdit,
            onEvent = onEvent,
            notifCounts = notifCounts,
            onArrange = onArrange,
            onResize = onResize,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Footer(
            page = page, editMode = editMode, hasSelection = selectedRef != null,
            themeName = themeName, onCycleTheme = onCycleTheme,
            onOpenDrawer = onOpenDrawer, onExitEdit = onExitEdit, onAdd = onAdd, onRemoveSelected = onRemoveSelected,
        )
    }
}

@Composable
private fun Footer(
    page: Page,
    editMode: Boolean,
    hasSelection: Boolean,
    onOpenDrawer: () -> Unit,
    onExitEdit: () -> Unit,
    onAdd: () -> Unit,
    onRemoveSelected: () -> Unit,
    themeName: String? = null,
    onCycleTheme: () -> Unit = {},
) {
    val p = LocalPalette.current
    val openDrawer = rememberUpdatedState(onOpenDrawer)
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("v${BuildConfig.VERSION_NAME}", color = p.fgDim, fontSize = 10.sp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (editMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasSelection) TextLink("REMOVE", Color(0xFFE05A5A), onRemoveSelected)
                    else TextLink("+ ADD", p.fgDim, onAdd)
                    TextLink("DONE", p.accent, onExitEdit)
                }
            } else {
                Text(
                    "▲ APPS",
                    color = p.fgDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            var dy = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dy = 0f },
                                onVerticalDrag = { change, amount -> dy += amount; change.consume() },
                                onDragEnd = { if (dy < -40f) openDrawer.value() },
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenDrawer,
                        )
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
        // ページ名 + 色味名。タップで色味を順に切り替える(候補が 1 つなら名前だけ)
        Text(
            if (themeName != null) "${page.name} · $themeName" else page.name,
            color = if (editMode) p.accent else p.fgDim,
            fontSize = 10.sp,
            modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCycleTheme)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        )
    }
}
