package com.snsdpen.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.data.AppEntry
import com.snsdpen.launcher.model.FolderDef

/** フォルダの中身。外側タップか戻るで閉じる */
@Composable
fun FolderOverlay(
    folder: FolderDef,
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 480.dp)
                .background(p.bg)
                .border(1.dp, p.line)
                // 内側のタップは閉じない
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(folder.name, color = p.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${apps.size}", color = p.fgDim, fontSize = 12.sp)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 76.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(apps, key = { "${it.packageName}/${it.activityName}" }) { app ->
                    AppTile(app = app, onClick = { onLaunch(app) })
                }
            }
        }
    }
}
