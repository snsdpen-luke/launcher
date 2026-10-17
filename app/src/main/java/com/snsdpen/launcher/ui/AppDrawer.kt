package com.snsdpen.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.data.AppEntry
import com.snsdpen.launcher.data.AppIcon

/**
 * 全アプリ一覧(全画面)。閉じ方は 3 つ: 戻る / ✕ / **下にスワイプ**。
 * 下スワイプは一覧が一番上にある時だけ効く(途中なら普通にスクロールする)。指に合わせて一覧ごと下がり、
 * 120dp 以上引くか、下向きに速く弾くと閉じる。足りなければ元に戻る
 */
@Composable
fun AppDrawer(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val p = LocalPalette.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val closePx = with(density) { 120.dp.toPx() }
    var pull by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val close = androidx.compose.runtime.rememberUpdatedState(onClose)
    val nested = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            // 引いている途中で上に戻す指は、まず引いた分を戻すのに使う
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (pull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -pull)
                    pull += used
                    return androidx.compose.ui.geometry.Offset(0f, used)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            // 一覧が一番上で、まだ下に余った指 = 引き下げ
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y > 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    pull += available.y
                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pull > 0f) {
                    if (pull >= closePx || available.y > 2500f) close.value() else pull = 0f
                    return available
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = pull; alpha = 1f - (pull / (closePx * 3f)).coerceIn(0f, 0.4f) }
            .nestedScroll(nested)
            .background(p.bg)
            // 背面(ホーム)へタップを通さない
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        // 見出しの行: 掴んで下に引いても閉じる。上に小さな取っ手(引けることの目印)
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).background(p.fgDim.copy(alpha = 0.5f)))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (pull >= closePx) close.value() else pull = 0f },
                        onDragCancel = { pull = 0f },
                    ) { change, dy -> change.consume(); pull = (pull + dy).coerceAtLeast(0f) }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("APPS", color = p.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${apps.size}", color = p.fgDim, fontSize = 12.sp)
            Text(
                "✕",
                color = p.fgDim,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 76.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(apps, key = { "${it.packageName}/${it.activityName}" }) { app ->
                AppTile(app = app, onClick = { onLaunch(app) })
            }
        }
    }
}

@Composable
fun AppTile(app: AppEntry, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app = app, size = 44.dp)
        Text(
            app.label,
            color = p.fg,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(72.dp)
                .padding(top = 4.dp),
        )
    }
}
