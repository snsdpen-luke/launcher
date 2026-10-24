package com.snsdpen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.size
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
    /** 隠しパネル(ライト・明るさ・音量)を開く */
    onOpenQuick: () -> Unit = {},
    /** TASK ページの Trinity の画面から届いた横スワイプ(true = 次のページ) */
    onTrinitySwipe: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    Column(modifier.fillMaxSize().background(p.bg)) {
        // TASK はグリッドを持たない。全面が Trinity の「秘書とタスク」。フッターは他のページと同じ
        if (page == Page.TASK) TrinityPage(onSwipe = onTrinitySwipe, modifier = Modifier.weight(1f).fillMaxWidth())
        else if (side != null) {
            // カバー縦のマス目を高さいっぱいに置き、残りの幅を側パネルに
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                val cell = ((maxHeight - com.snsdpen.launcher.ui.GridGap * (spec.rows - 1)) / spec.rows).coerceAtLeast(0.dp)
                val coverW = cell * spec.cols + com.snsdpen.launcher.ui.GridGap * (spec.cols - 1)
                Row(Modifier.fillMaxSize()) {
                    ModuleGrid(
                        placed = placed, spec = spec, face = face, page = page, surface = surface, layout = layout, apps = apps,
                        editMode = editMode, selectedRef = selectedRef, onSelect = onSelect, onEnterEdit = onEnterEdit, onEvent = onEvent,
                        notifCounts = notifCounts, onArrange = onArrange, onResize = onResize,
                        modifier = Modifier.width(coverW).fillMaxHeight(), onQuick = onOpenQuick,
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
            onQuick = onOpenQuick,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Footer(
            page = page, editMode = editMode, hasSelection = selectedRef != null,
            themeName = themeName, onCycleTheme = onCycleTheme,
            onOpenDrawer = onOpenDrawer, onExitEdit = onExitEdit, onAdd = onAdd, onRemoveSelected = onRemoveSelected,
            onOpenQuick = onOpenQuick,
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
    onOpenQuick: () -> Unit = {},
) {
    val p = LocalPalette.current
    val openDrawer = rememberUpdatedState(onOpenDrawer)
    // 3 つを重ねて置く: 左端に近道、画面の真ん中に ▲ APPS、右端にページ名と版(2 段)。
    // Row で並べると左右の幅の差で APPS が中央からずれるので Box で揃える
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 8.dp),
    ) {
        // 近道: 設定 / カメラ / マップ。絵は自前で描く(絵文字は機種で見た目が変わる)。編集中は出さない
        if (!editMode) {
            val context = androidx.compose.ui.platform.LocalContext.current
            fun open(intent: android.content.Intent?) {
                if (intent == null) return
                runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                FooterButton(p.fgDim, onClick = { open(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) }) { GearGlyph(it, 16.dp) }
                FooterButton(p.fgDim, onClick = { open(android.content.Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }) { CameraGlyph(it, 16.dp) }
                FooterButton(p.fgDim, onClick = {
                    open(
                        context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                            ?: android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0"))
                    )
                }) { PinGlyph(it, 16.dp) }
            }
        }
        Box(Modifier.align(Alignment.Center)) {
            if (editMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasSelection) TextLink("REMOVE", Color(0xFFE05A5A), onRemoveSelected)
                    else TextLink("+ ADD", p.fgDim, onAdd)
                    TextLink("DONE", p.accent, onExitEdit)
                }
            } else {
                // ▲ はフォントの記号だと書体が混ざって欠けることがあるので、山形は自前で描く。
                // 文字は行の高さを決め打ちにして、上下の余白で切れないようにする
                Row(
                    Modifier
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChevronUpGlyph(p.fgDim, 10.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "APPS",
                        color = p.fgDim,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                            ),
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        // 右端: 上にページ名 + 色味名(タップで色味を順に切替)、下に小さく版
        androidx.compose.foundation.layout.Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.End) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCycleTheme)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                if (themeName != null) "${page.name} · $themeName" else page.name,
                color = if (editMode) p.accent else p.fgDim,
                fontSize = 10.sp, lineHeight = 12.sp,
            )
            Text("v${BuildConfig.VERSION_NAME}", color = p.fgDim.copy(alpha = 0.7f), fontSize = 8.sp, lineHeight = 10.sp)
        }
        }
    }
}


/** 歯車(設定)。外周に 8 本の歯、中に穴 */
@Composable
private fun GearGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    // 穴を Clear で抜くので別レイヤーに描く(そのままだと窓まで抜けて黒くなる)
    androidx.compose.foundation.Canvas(Modifier.size(size).graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)) {
        val c = this.center
        val r = this.size.minDimension / 2f
        val tooth = r * 0.28f
        val body = r - tooth * 0.9f
        for (i in 0 until 8) {
            rotate(i * 45f, c) {
                drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(c.x - tooth / 2f, c.y - r), size = androidx.compose.ui.geometry.Size(tooth, tooth * 1.2f))
            }
        }
        drawCircle(color, radius = body, center = c)
        drawCircle(Color.Transparent, radius = body * 0.42f, center = c, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
    }
}


/**
 * フッターの近道ボタン。当たり判定は 44x36dp(絵は 16dp)。押している間だけ薄い四角が出る(押せた手応え)
 */
@Composable
private fun FooterButton(color: Color, onClick: () -> Unit, glyph: @Composable (Color) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .size(width = 44.dp, height = 36.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (pressed) Box(Modifier.size(30.dp).background(color.copy(alpha = 0.18f)))
        glyph(color)
    }
}

/** カメラ: 角丸の胴 + 上のでっぱり + レンズの穴 */
@Composable
private fun CameraGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size).graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)) {
        val w = this.size.width; val h = this.size.height
        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.14f), size = androidx.compose.ui.geometry.Size(w * 0.40f, h * 0.16f))
        drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.26f), size = androidx.compose.ui.geometry.Size(w, h * 0.62f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f))
        val c = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.57f)
        drawCircle(Color.Transparent, radius = w * 0.22f, center = c, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
        drawCircle(color, radius = w * 0.12f, center = c)
    }
}

/** 地図のピン: 丸 + 下向きの三角 + 穴 */
@Composable
private fun PinGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size).graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)) {
        val w = this.size.width; val h = this.size.height
        val r = w * 0.34f
        val c = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.38f)
        drawCircle(color, radius = r, center = c)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x - r * 0.86f, c.y + r * 0.5f)
            lineTo(c.x + r * 0.86f, c.y + r * 0.5f)
            lineTo(c.x, h * 0.98f)
            close()
        }
        drawPath(path, color)
        drawCircle(Color.Transparent, radius = r * 0.42f, center = c, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
    }
}


/** 上向きの山形(アプリ一覧を開く目印) */
@Composable
private fun ChevronUpGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.08f, h * 0.72f)
            lineTo(w * 0.5f, h * 0.28f)
            lineTo(w * 0.92f, h * 0.72f)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.16f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}
