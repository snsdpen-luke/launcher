package com.snsdpen.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.snsdpen.launcher.model.Page

/**
 * ページごとの配色。モジュールや画面は LocalPalette.current を読むだけで、
 * ページを足しても配線は増えない。
 */
@Immutable
data class Palette(
    val bg: Color,
    val fg: Color,
    val fgDim: Color,
    val line: Color,
    val accent: Color,
    /** 系列色(DRIVE のフォルダ等、複数を塗り分ける用途)。空なら accent のみ */
    val series: List<Color> = emptyList(),
    /** 白基調なら true(ステータスバーのアイコンを黒にする) */
    val isLight: Boolean = false,
    /** 床のタイル(METER)の色。metric ごとに番号で引く(0 BAT, 1 WIFI, 2 SIG, 3 MEM, 4 STO, 5 NTF)。空なら茶 3 色 */
    val blocks: List<Color> = emptyList(),
    /** 空のブロックの色。null なら文字色を 15% で透かす */
    val blockEmpty: Color? = null,
    /** 空きマスを歩く差し色ブロックの色。空なら歩かない(1 個が 1 マスずつ動き、跡がグレーで残る) */
    val vacantAccents: List<Color> = emptyList(),
    /** アプリのアイコンをグレースケールで描く */
    val monoIcons: Boolean = false,
    /** 見出しを色チップにする(ラベルごとにハッシュで色を選ぶ)。空なら文字だけ */
    val labelChips: List<Color> = emptyList(),
    /** 計測ブロックの色(BAT/SIG/WIFI/BT)。色相は全配色で共通、色味だけ地に合わせる。空なら MeterFixedColors */
    val meterColors: Map<String, Color> = emptyMap(),
    /** 床のモザイク。全マスの後ろに単色ブロックを敷き、上→下で段を選ぶ(先頭が上。少し揺らぐ)。うっすらで良い。空なら敷かない */
    val floor: List<Color> = emptyList(),
)

/** WORK: 暖かい白基調・黒文字。アイコンもモノクロ */
val WorkPalette = Palette(
    bg = Color(0xFFF7F5F0),
    fg = Color(0xFF1A1A1A),
    fgDim = Color(0xFF8A857D),
    line = Color(0xFFD9D4CA),
    accent = Color(0xFF1A1A1A),
    isLight = true,
    monoIcons = true,   // 白・黒・グレーの紙面に合わせてアイコンもモノクロ
)

/** DRIVE: 濃紺の地に高コントラストの系列色。運転中に読める明るさを優先 */
val DrivePalette = Palette(
    bg = Color(0xFF07101F),
    fg = Color(0xFFFFFFFF),
    fgDim = Color(0xFF8FA3BF),
    line = Color(0xFF1E2E48),
    accent = Color(0xFFFFB020),   // 琥珀
    series = listOf(
        Color(0xFFFFB020),        // 琥珀
        Color(0xFF2ED3F0),        // シアン
        Color(0xFF4ADE80),        // 緑
        Color(0xFFF472B6),        // マゼンタ
    ),
)

/** PRIVATE: NATURE。深い青緑の海の地に白い文字、青緑(AOMIDORI)と海の青が差し色。夜の紺も少し */
val NaturePalette = Palette(
    bg = Color(0xFF0F2A33),
    fg = Color(0xFFF2F4F3),
    fgDim = Color(0xFF7FA3A8),
    line = Color(0xFF1D3F49),
    accent = Color(0xFF00AA90),   // 青緑
    series = listOf(
        Color(0xFF00AA90),        // 青緑
        Color(0xFF2E7FD6),        // 海の青
        Color(0xFF3FC1C9),        // 浅い青緑
        Color(0xFFA9D6DC),        // 霞んだ水色
    ),
    blocks = listOf(
        Color(0xFF00AA90),        // 0 BAT (固定色で上書きされる)
        Color(0xFF3FC1C9),        // 1 WIFI 浅い青緑
        Color(0xFF2E7FD6),        // 2 SIG 海の青
        Color(0xFF1F5A66),        // 3 MEM 深い青緑
        Color(0xFF173F4A),        // 4 STO もっと深い青緑
        Color(0xFF8E2F4A),        // 5 NTF 夜の紅(要注意)
        Color(0xFFA9D6DC),        // 6 BT 霞んだ水色
    ),
    blockEmpty = Color(0x1FFFFFFF),
    vacantAccents = listOf(Color(0xFF00AA90), Color(0xFF2E7FD6)),
    labelChips = listOf(Color(0xFF00AA90), Color(0xFF2E7FD6), Color(0xFF3FC1C9)),
    floor = listOf(Color(0xFF15333D), Color(0xFF122E38), Color(0xFF0F2A33), Color(0xFF0D262E), Color(0xFF0B2129)),   // 上が明るく下が濃い
    meterColors = mapOf(
        "battery" to Color(0xFF8FD35A),    // 苔の黄緑
        "signal" to Color(0xFF9F86D2),     // 藤紫
        "wifi" to Color(0xFFDDC45A),       // 枯れた黄
        "bluetooth" to Color(0xFF5FAFD3),  // 海の水色
    ),
)

/** PRIVATE の 2 つ目: LEMON。Ultimate Gray の紙に Illuminating の黄。文字は鉄色。昼向きの明るいモード */
val LemonPalette = Palette(
    bg = Color(0xFFC5C1C0),       // SCREEN
    fg = Color(0xFF0A1612),       // STEEL
    fgDim = Color(0xFF5C6164),
    line = Color(0xFFA9A6A4),
    accent = Color(0xFFF5DF4D),   // ILLUMINATING
    series = listOf(
        Color(0xFFF5DF4D),        // 黄
        Color(0xFF1A2930),        // DENIM
        Color(0xFF0A1612),        // STEEL
        Color(0xFF8A8785),        // 濃いグレー
    ),
    isLight = true,
    blocks = listOf(
        Color(0xFFF5DF4D),        // 0 BAT (固定色で上書きされる)
        Color(0xFFF5DF4D),        // 1 WIFI 黄
        Color(0xFF1A2930),        // 2 SIG DENIM
        Color(0xFFDEDCDA),        // 3 MEM 薄いグレー
        Color(0xFF8A8785),        // 4 STO 濃いグレー
        Color(0xFFFF7A00),        // 5 NTF オレンジ(要注意)
        Color(0xFF0A1612),        // 6 BT STEEL
    ),
    blockEmpty = Color(0x14000000),
    vacantAccents = listOf(Color(0xFFF5DF4D), Color(0xFF1A2930)),
    monoIcons = false,   // アイコンは色付きのまま
    labelChips = listOf(Color(0xFFF5DF4D)),
    floor = listOf(Color(0xFFCFCCCA), Color(0xFFCAC7C5), Color(0xFFC5C1C0), Color(0xFFBCB9B7), Color(0xFFB3B0AE)),   // 上が明るく下が濃い(うっすら)
    meterColors = mapOf(
        "battery" to Color(0xFF9DC84A),    // 若草
        "signal" to Color(0xFFA995CF),     // 薄い藤
        "wifi" to Color(0xFFE6CC55),       // ILLUMINATING 寄りの黄
        "bluetooth" to Color(0xFF7AB6D4),  // 曇りの水色
    ),
)

/** ページごとの色味の候補。先頭が既定。フッターのページ名タップで順に切り替わる */
data class ThemeOption(val name: String, val palette: Palette)

val ThemeOptions: Map<Page, List<ThemeOption>> = mapOf(
    Page.PRIVATE to listOf(ThemeOption("NATURE", NaturePalette), ThemeOption("LEMON", LemonPalette)),
    Page.WORK to listOf(ThemeOption("WHITE", WorkPalette)),
    Page.DRIVE to listOf(ThemeOption("NAVY", DrivePalette)),
)

fun themeOf(page: Page, variant: String?): ThemeOption {
    val list = ThemeOptions[page].orEmpty()
    return list.firstOrNull { it.name == variant } ?: list.first()
}

fun paletteFor(page: Page, variant: String? = null): Palette = themeOf(page, variant).palette

/** 次の色味の名前(候補が 1 つなら同じ物) */
fun nextTheme(page: Page, variant: String?): String {
    val list = ThemeOptions[page].orEmpty()
    val i = list.indexOfFirst { it.name == variant }.coerceAtLeast(0)
    return list[(i + 1) % list.size].name
}

val LocalPalette = staticCompositionLocalOf { NaturePalette }

val GridGap = 4.dp

/** ページのパレットを配る。Text の既定色も合わせる */
@Composable
fun PageTheme(page: Page, variant: String? = null, content: @Composable () -> Unit) {
    val p = paletteFor(page, variant)
    val scheme = if (p.isLight) {
        lightColorScheme(background = p.bg, surface = p.bg, onBackground = p.fg, onSurface = p.fg, primary = p.accent)
    } else {
        darkColorScheme(background = p.bg, surface = p.bg, onBackground = p.fg, onSurface = p.fg, primary = p.accent)
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** アプリ全体の土台。実際の配色は PageTheme が上書きする */
@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = NaturePalette.bg,
            surface = NaturePalette.bg,
            onBackground = NaturePalette.fg,
            onSurface = NaturePalette.fg,
        ),
        content = content,
    )
}

/** 復帰や権限付与のたびに増える。外部データ(予定など)を読み直す合図 */
val LocalRefreshTick = androidx.compose.runtime.compositionLocalOf { 0 }

/** 位置や id から決まる擬似乱数(0 以上)。起動のたびに変わらない「ランダム」 */
fun stableHash(vararg parts: Int): Int {
    var h = 0x9E3779B1.toInt()
    for (p in parts) h = (h xor (p * 0x85EBCA6B.toInt())).let { it xor (it ushr 13) } * 0xC2B2AE35.toInt()
    return (h xor (h ushr 16)) and 0x7FFFFFFF
}

/**
 * 表示中(RESUMED)のあいだだけ [block] を回す。裏に回ると止まり、戻ると最初からやり直す。
 * 時計の秒・歩くブロック・計測の読み直しはこれで包む(引き継ぎの罠 17: 常時アニメは表示中のみ)。
 */
@Composable
fun WhileResumed(vararg keys: Any?, block: suspend () -> Unit) {
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.LaunchedEffect(lifecycle, *keys) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { block() }
    }
}


/** デバッグ: true にすると未読が無くても点滅する(動作確認用。本番は false) */
const val BLINK_DEBUG = false
/** true にするとカバーのアプリも右半分のポップアップで開く(自由配置ウィンドウの動作確認用) */
const val POPUP_DEBUG = false

/**
 * 通知の点滅。active のとき 1 ↔ 0.35 を 0.7 秒で往復する透け。
 * ページが表示中のときだけ合成されるので、裏では動かない。
 */
@Composable
fun blinkAlpha(active: Boolean): Float {
    if (!active && !BLINK_DEBUG) return 1f
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "blink")
    val a by t.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "a",
    )
    return a
}

// ---- 全配色で固定の色 ----
/** 電池: 蛍光グリーン。白い紙面では濃い緑 */
fun batteryColor(p: Palette): Color = meterColor(p, "battery")
/** 計測の色: 配色に表があればそれ、無ければ固定色 */
fun meterColor(p: Palette, metric: String): Color = p.meterColors[metric] ?: MeterFixedColors.getValue(metric)
/** 充電中: 黄。白い紙面では濃い黄 */
fun chargeColor(p: Palette): Color = if (p.isLight) Color(0xFFC99A00) else Color(0xFFFFD400)
/** 計測ブロックの色は全配色で固定(明るく、互いに区別が付く 4 色) */
val MeterFixedColors: Map<String, Color> = mapOf(
    "battery" to Color(0xFFA6FF2E),    // 蛍光の黄緑
    "wifi" to Color(0xFFFFE84A),       // 黄
    "signal" to Color(0xFFC77DFF),     // 紫
    "bluetooth" to Color(0xFF4FC3F7),  // 水色
)
/** 通知: オレンジ(アイコンのマスが明滅する) */
val NotifOrange = Color(0xFFFF7A00)
