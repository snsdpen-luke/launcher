package com.snsdpen.launcher.ui.modules

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snsdpen.launcher.data.AppIcon
import com.snsdpen.launcher.model.Face
import com.snsdpen.launcher.model.ModuleEvent
import com.snsdpen.launcher.model.ModuleScope
import com.snsdpen.launcher.model.ModuleSpec
import com.snsdpen.launcher.model.Span
import com.snsdpen.launcher.model.appKey
import com.snsdpen.launcher.ui.LocalPalette
import com.snsdpen.launcher.ui.Marker

/** アプリ 1 個を直接起動する 1 行。アイコン + 名前(別名があればそれ) */
val PanelSpec = ModuleSpec(
    kind = "panel",
    name = "APP",
    singleton = false,
    defaultSize = { face -> if (face == Face.COVER) Span(8, 2) else Span(6, 2) },
    minSize = { Span(3, 1) },
    exists = { layout, id -> layout.panels.any { it.id == id } },
    onTap = { scope, emit ->
        val def = scope.layout.panels.firstOrNull { it.id == scope.id }
        val app = def?.let { d -> scope.apps.firstOrNull { appKey(it) == d.app } }
        // メイン(開いた時)の左パネルと右側からは、右側に窓で開く。カバーは全画面
        val inPane = scope.face == com.snsdpen.launcher.model.Face.MAIN || scope.face == com.snsdpen.launcher.model.Face.SIDE || com.snsdpen.launcher.ui.POPUP_DEBUG
        if (app != null) emit(if (inPane) ModuleEvent.LaunchInPane(app) else ModuleEvent.Launch(app))
        else scope.id?.let { emit(ModuleEvent.EditPanel(it)) }
    },
    onEdit = { scope, emit -> scope.id?.let { emit(ModuleEvent.EditPanel(it)) } },
    content = { PanelModule(it) },
)

@Composable
private fun PanelModule(scope: ModuleScope) {
    val p = LocalPalette.current
    val def = scope.layout.panels.firstOrNull { it.id == scope.id } ?: return
    val app = scope.apps.firstOrNull { appKey(it) == def.app }
    val name = def.name.ifBlank { app?.label ?: def.app.substringBefore('/').substringAfterLast('.') }
    // 3 行以上の高さなら「生きたタイル」(DRIVE 用): アプリの色の板に、再生中の曲やナビの案内を埋め込む
    if (scope.span.h >= 3) { BigTile(scope, app, name); return }
    // アイコンは 1 マスいっぱい(床のブロックと同じ大きさ)。名前は小さく、アイコンの縦中央に揃える
    Row(
        Modifier.fillMaxSize().padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val n0 = app?.let { scope.notifCounts[it.packageName] } ?: 0
        val notified = n0 > 0 || com.snsdpen.launcher.ui.BLINK_DEBUG
        val blink = com.snsdpen.launcher.ui.blinkAlpha(notified)
        // 未読があるときはアイコンの後ろの 1 マスがオレンジで明滅する(アイコン自体は明滅しない)
        Box(Modifier.size(scope.cell), contentAlignment = Alignment.Center) {
            if (notified) Box(Modifier.fillMaxSize().background(com.snsdpen.launcher.ui.NotifOrange.copy(alpha = blink)))
            if (app != null) AppIcon(app = app, size = scope.cell) else Marker(p.fgDim)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            color = if (app != null) p.fg else p.fgDim,
            fontSize = 11.sp, lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (app == null) Text("MISSING", color = p.fgDim, fontSize = 9.sp)
        val n = app?.let { scope.notifCounts[it.packageName] } ?: 0
        if (n > 0) Text("$n", color = p.accent, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}


/** アプリの色(既知の物は固定。無ければアイコンの平均色) */
private val BrandColors = mapOf(
    "com.google.android.youtube" to Color(0xFFE62117),
    "com.google.android.apps.youtube.music" to Color(0xFFE62117),
    "com.spotify.music" to Color(0xFF1DB954),
    "com.google.android.apps.maps" to Color(0xFF14919B),   // 青(#1A73E8)はダサいので青緑
)

@Composable
private fun brandColorOf(app: com.snsdpen.launcher.data.AppEntry?, fallback: Color): Color {
    if (app == null) return fallback
    BrandColors[app.packageName]?.let { return it }
    val context = androidx.compose.ui.platform.LocalContext.current
    val avg by androidx.compose.runtime.produceState<Color?>(initialValue = null, app) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bmp = com.snsdpen.launcher.data.IconCache.get(context, app) ?: return@withContext null
            val px = bmp.toPixelMap()
            var r = 0f; var g = 0f; var b = 0f; var n = 0
            var y = 0
            while (y < px.height) {
                var x = 0
                while (x < px.width) {
                    val c = px[x, y]
                    if (c.alpha > 0.6f) { r += c.red; g += c.green; b += c.blue; n++ }
                    x += 4
                }
                y += 4
            }
            if (n == 0) null else Color(r / n, g / n, b / n)
        }
    }
    return avg ?: fallback
}

/**
 * 生きたタイル。板はアプリの色。左上にアイコンと名前、下に再生中の曲名 / アーティスト / ⏮ ⏯ ⏭、
 * 再生情報が無ければ通知の本文(ナビの案内など)。ジャケットや案内の絵は右側に敷く。板のどこを押しても開く
 */
@Composable
private fun BigTile(scope: ModuleScope, app: com.snsdpen.launcher.data.AppEntry?, name: String) {
    val p = LocalPalette.current
    val brand = brandColorOf(app, p.accent)
    val ink = if (brand.luminance() > 0.5f) Color(0xFF111111) else Color(0xFFFFFFFF)
    val dim = ink.copy(alpha = 0.75f)
    val media by com.snsdpen.launcher.data.MediaNow.sessions.collectAsStateWithLifecycle()
    val notes by com.snsdpen.launcher.notif.NotifListener.latest.collectAsStateWithLifecycle()
    val pkg = app?.packageName
    val now = pkg?.let { media[it] }
    val note = pkg?.let { notes[it] }
    val art = now?.art ?: note?.icon
    // 絵はマス単位の幅で右に。目地の線を重ねてタイルの一部に見せる
    val cols = scope.span.w.coerceAtLeast(1)
    val artCols = if (art == null) 0 else (cols * (if (now != null) 0.42f else 0.3f)).let { kotlin.math.round(it).toInt() }.coerceIn(1, cols - 4)
    val gap = com.snsdpen.launcher.ui.GridGap
    val artW = scope.cell * artCols + gap * (artCols - 1)
    val rowsN = scope.span.h.coerceAtLeast(1)
    val pulse = rememberTilePulse(cols * rowsN, now?.playing == true)
    // アイコンと名前の下のマスだけ壁と同じグレー(名前の幅からマス数を出す)。行の残りと絵はそのまま
    val nameStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val nameW = with(density) { measurer.measure(name, nameStyle, maxLines = 1).size.width.toDp() }
    val stride = scope.cell + gap
    val headerCols = (1 + kotlin.math.ceil(((nameW + 8.dp + 6.dp) / stride).toDouble()).toInt()).coerceIn(1, cols - artCols)
    val headerTones = if (p.floor.isNotEmpty()) listOf(p.floor.first(), p.floor.last()) else listOf(p.line, p.bg)
    Box(Modifier.fillMaxSize()) {
        TileMosaic(brand = brand, cols = cols, rows = rowsN, pulse = pulse, headerCols = headerCols, headerTones = headerTones)
        if (art != null) {
            ArtMosaic(
                art = art, artCols = artCols, rows = rowsN, colOffset = cols - artCols, totalCols = cols, pulse = pulse,
                modifier = Modifier.align(Alignment.CenterEnd).width(artW).fillMaxHeight(),
            )
        }
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            // 見出しの段: アイコンは左端の 1 マスにぴったり、名前はその右に縦中央
            Row(Modifier.fillMaxWidth().height(scope.cell), verticalAlignment = Alignment.CenterVertically) {
                if (app != null) AppIcon(app = app, size = scope.cell) else Marker(dim)
                Spacer(Modifier.width(8.dp))
                Text(name, color = p.fg, style = nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(gap))
        androidx.compose.foundation.layout.Column(
            Modifier.weight(1f).fillMaxWidth().padding(end = if (art != null) artW + gap else 0.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            // 文字の領域(余った高さ。入り切らない分は切る)
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.BottomStart) {
                androidx.compose.foundation.layout.Column {
                    when {
                        now != null -> {
                            Text(now.title, color = ink, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (now.artist.isNotBlank()) Text(now.artist, color = dim, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        note != null -> {
                            Text(note.title, color = ink, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(note.text, color = dim, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        app == null -> Text("MISSING", color = dim, fontSize = 12.sp)
                        else -> Text("TAP TO OPEN", color = dim, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                }
            }
            // 操作ボタンは下に固定(見切れない)
            if (now != null && pkg != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    MediaButton("◀◀", ink, scope, pkg, "prev")
                    Spacer(Modifier.width(18.dp))
                    MediaButton(if (now.playing) "❚❚" else "▶", ink, scope, pkg, "toggle")
                    Spacer(Modifier.width(18.dp))
                    MediaButton("▶▶", ink, scope, pkg, "next")
                }
            }
        }
        }
    }
}

/** タイル内の再生ボタン。編集中は押せない(clickable ごと外す) */
@Composable
private fun MediaButton(glyph: String, ink: Color, scope: ModuleScope, pkg: String, action: String) {
    val emit = scope.emit
    val tap = if (!scope.editMode) Modifier.clickable(
        interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
    ) { emit(ModuleEvent.MediaControl(pkg, action)) } else Modifier
    Text(glyph, color = ink, fontSize = 18.sp, lineHeight = 20.sp, modifier = tap.padding(horizontal = 6.dp, vertical = 2.dp))
}


/** タイルの「拍」の状態: 各ブロックの明るさの上乗せ。BigTile が 1 つ持ち、板と絵の両方が読む */
private class TilePulse(n: Int) {
    val cur = FloatArray(n)
    val target = FloatArray(n)
    var frame by androidx.compose.runtime.mutableStateOf(0)
}

/**
 * 再生中は 0.48 秒ごとの「拍」で、ブロックの 3 割がランダムな明るさ(白寄り・黒寄り・中間)へ飛び、
 * 拍の間は滑らかに追いかける。本物の拍は音を録れないので取れない。止まれば静かに 0 へ。
 * 時間の状態はこの中に閉じる(1 秒に 20 回、表示中かつ再生中だけ)
 */
@Composable
private fun rememberTilePulse(n: Int, playing: Boolean): TilePulse {
    val pulse = androidx.compose.runtime.remember(n) { TilePulse(n) }
    com.snsdpen.launcher.ui.WhileResumed(playing, n) {
        val rnd = kotlin.random.Random(System.currentTimeMillis())
        val cur = pulse.cur; val target = pulse.target
        if (!playing) {
            while (cur.any { kotlin.math.abs(it) > 0.01f }) {
                kotlinx.coroutines.delay(50)
                for (i in 0 until n) { target[i] = 0f; cur[i] += (target[i] - cur[i]) * 0.2f }
                pulse.frame++
            }
            return@WhileResumed
        }
        var tick = 0
        while (true) {
            kotlinx.coroutines.delay(50)
            if (tick % 10 == 0) {
                for (i in 0 until n) if (rnd.nextFloat() < 0.3f) {
                    target[i] = when (rnd.nextInt(4)) { 0 -> 0.55f + rnd.nextFloat() * 0.35f; 1 -> -(0.45f + rnd.nextFloat() * 0.35f); else -> (rnd.nextFloat() - 0.5f) * 0.6f }
                } else if (rnd.nextFloat() < 0.5f) target[i] = 0f
            }
            for (i in 0 until n) cur[i] += (target[i] - cur[i]) * 0.28f
            pulse.frame++
            tick++
        }
    }
    return pulse
}

/** タイルの板をモザイクで塗る: 1 マス 1 ブロック、左上が明るく右下が暗い段階 + 1 段の揺らぎ + 拍の上乗せ */
@Composable
private fun TileMosaic(brand: Color, cols: Int, rows: Int, pulse: TilePulse, headerCols: Int = 0, headerTones: List<Color> = emptyList()) {
    val c = cols.coerceAtLeast(1); val r = rows.coerceAtLeast(1)
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") pulse.frame   // 再描画の合図
        val g = com.snsdpen.launcher.ui.GridGap.toPx()
        val cw = (size.width - g * (c - 1)) / c
        val ch = (size.height - g * (r - 1)) / r
        for (row in 0 until r) for (col in 0 until c) {
            if (row == 0 && col < headerCols && headerTones.isNotEmpty()) {
                // 見出し(アイコンと名前の下のマスだけ): 壁と同じグレー(左が明るく右が暗い + 揺らぎ)。拍は乗せない
                val t = (col.toFloat() / (headerCols - 1).coerceAtLeast(1) * 0.7f + ((com.snsdpen.launcher.ui.stableHash(col, row, 9) % 5) - 2) * 0.06f).coerceIn(0f, 1f)
                val color = lerp(headerTones.first(), headerTones.last(), t)
                drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(col * (cw + g), row * (ch + g)), size = androidx.compose.ui.geometry.Size(cw, ch))
                continue
            }
            val d = (col.toFloat() / (c - 1).coerceAtLeast(1)) * 0.5f + (row.toFloat() / (r - 1).coerceAtLeast(1)) * 0.5f
            var f = 0.16f - 0.34f * d
            f += ((com.snsdpen.launcher.ui.stableHash(col, row, 5) % 5) - 2) * 0.02f
            f += pulse.cur[(row * c + col).coerceIn(0, pulse.cur.size - 1)]
            val color = if (f >= 0f) lerp(brand, Color.White, f.coerceIn(0f, 0.9f)) else lerp(brand, Color.Black, (-f).coerceIn(0f, 0.85f))
            drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(col * (cw + g), row * (ch + g)), size = androidx.compose.ui.geometry.Size(cw, ch))
        }
    }
}

/**
 * 絵(サムネイル / ジャケット)をマスごとに切って描く。
 * 溝は絵を 35% で薄く描いた層(線を乗せないので交差点が濃くならない)。
 * 各マスは絵を不透明度付きで描く: 止まっている時は 100%、再生中は拍に合わせて 20%〜100% がランダムに入れ替わる。
 * 透けた分は下のブロック(ブランド色)が見える。横長の絵は 1.35 倍にして黒い帯を切る
 */
@Composable
private fun ArtMosaic(
    art: androidx.compose.ui.graphics.ImageBitmap, artCols: Int, rows: Int, colOffset: Int, totalCols: Int, pulse: TilePulse,
    modifier: Modifier = Modifier, rowOffset: Int = 0,
) {
    androidx.compose.foundation.Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION") pulse.frame
        val g = com.snsdpen.launcher.ui.GridGap.toPx()
        val cw = (size.width - g * (artCols - 1)) / artCols
        val ch = (size.height - g * (rows - 1)) / rows
        // ContentScale.Crop 相当の写像(+ 横長なら 1.35 倍)
        val iw = art.width.toFloat(); val ih = art.height.toFloat()
        val zoom = if (iw > ih * 1.3f) 1.35f else 1f
        val scale = maxOf(size.width / iw, size.height / ih) * zoom
        val srcW = size.width / scale; val srcH = size.height / scale
        val sx0 = (iw - srcW) / 2f; val sy0 = (ih - srcH) / 2f
        // 絵全体を同じ位置・同じ拡大率で描き、マスの形に切り抜く(マスごとに切り出すと丸め誤差で 1px ずれる)
        val dstW = (iw * scale).toInt().coerceAtLeast(1); val dstH = (ih * scale).toInt().coerceAtLeast(1)
        val ox = (-sx0 * scale).toInt(); val oy = (-sy0 * scale).toInt()
        fun draw(x: Float, y: Float, w: Float, h: Float, alpha: Float) {
            if (w <= 0f || h <= 0f || alpha <= 0f) return
            // 半ピクセル広げて、下のブロックの端が縁に透けないようにする
            clipRect(x - 0.5f, y - 0.5f, x + w + 0.5f, y + h + 0.5f) {
                drawImage(art, dstOffset = androidx.compose.ui.unit.IntOffset(ox, oy), dstSize = androidx.compose.ui.unit.IntSize(dstW, dstH), alpha = alpha)
            }
        }
        // 溝: 絵全体を薄く
        draw(0f, 0f, size.width, size.height, 0.35f)
        // マス: 拍で不透明度が変わる(|拍| 0 → 100%、0.8 → 20%)
        for (row in 0 until rows) for (col in 0 until artCols) {
            val f = pulse.cur[((row + rowOffset) * totalCols + colOffset + col).coerceIn(0, pulse.cur.size - 1)]
            val alpha = (1f - kotlin.math.abs(f)).coerceIn(0.2f, 1f)
            draw(col * (cw + g), row * (ch + g), cw, ch, alpha)
        }
    }
}
