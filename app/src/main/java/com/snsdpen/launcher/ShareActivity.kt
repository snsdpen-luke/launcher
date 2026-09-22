package com.snsdpen.launcher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snsdpen.launcher.data.domainOf
import com.snsdpen.launcher.model.LinkDef
import com.snsdpen.launcher.model.Page
import com.snsdpen.launcher.model.addLinkOnWork
import com.snsdpen.launcher.model.parseSharedLink
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.PageTheme
import com.snsdpen.launcher.ui.TextLink

/**
 * 共有シートの「ボードに追加」。ACTION_SEND text/plain を受け、
 * URL とタイトルを取り出して、選んだボードにリンクとして保存する。
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = parseSharedLink(
            intent?.getStringExtra(Intent.EXTRA_TEXT),
            intent?.getStringExtra(Intent.EXTRA_SUBJECT),
        )
        if (link == null) {
            Toast.makeText(this, "URL が見つからない", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val store = launcherStore
        setContent {
            PageTheme(Page.WORK) {
                val layout by store.layout.collectAsStateWithLifecycle()
                val state = layout ?: return@PageTheme
                ShareSheet(
                    link = link,
                    onSave = { l ->
                        store.update { s -> s.addLinkOnWork(l) }
                        Toast.makeText(this, "WORK に追加", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun ShareSheet(
    link: LinkDef,
    onSave: (link: LinkDef) -> Unit,
    onCancel: () -> Unit,
) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf(link.name) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel)
            .safeDrawingPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(p.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text("ADD TO BOARD", color = p.fgDim, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("NAME") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                domainOf(link.url),
                color = p.fgDim,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text("WORK ページに 1 行として置く", color = p.fgDim, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                TextLink("CANCEL", p.fgDim, onCancel)
                TextLink("SAVE", p.accent) {
                    val n = name.trim().ifBlank { domainOf(link.url) }
                    onSave(link.copy(name = n))
                }
            }
        }
    }
}
