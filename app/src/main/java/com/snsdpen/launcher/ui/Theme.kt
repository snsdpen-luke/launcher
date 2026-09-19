package com.snsdpen.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    /** 空きマスを歩く差し色ブロックの色。空なら歩かない(2 個が 1 マスずつ動き、跡がグレーで残る) */
    val vacantAccents: List<Color> = emptyList(),
    /** アプリのアイコンをグレースケールで描く */
    val monoIcons: Boolean = false,
    /** 見出しを色チップにする(ラベルごとにハッシュで色を選ぶ)。空なら文字だけ */
    val labelChips: List<Color> = emptyList(),
)

/** WORK: 暖かい白基調・黒文字 */
val WorkPalette = Palette(
    bg = Color(0xFFF7F5F0),
    fg = Color(0xFF1A1A1A),
    fgDim = Color(0xFF8A857D),
    line = Color(0xFFD9D4CA),
    accent = Color(0xFF1A1A1A),
    isLight = true,
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

/** PRIVATE: 黒・白・グレーの地に、色の組(VividSets)を差す。組は 1 時間ごと、または■タップで回る */
val VividPalette = Palette(
    bg = Color(0xFF141414),
    fg = Color(0xFFFFFFFF),
    fgDim = Color(0xFF9A9A9A),
    line = Color(0xFF2C2C2C),
    accent = Color(0xFFF2542D),   // 組で上書きされる(withAccents)
    series = listOf(Color(0xFFF2542D), Color(0xFF2EAADC), Color(0xFFB4D335), Color(0xFFF7C948)),
    blocks = listOf(Color(0xFFF2542D), Color(0xFF2EAADC), Color(0xFFB4D335), Color(0xFF9A9A9A), Color(0xFF666666), Color(0xFFF2542D)),
    blockEmpty = Color(0x1FFFFFFF),
    vacantAccents = listOf(Color(0xFFF2542D), Color(0xFF2EAADC), Color(0xFFB4D335), Color(0xFFF7C948)),
    monoIcons = false,   // アイコンは色付きのまま(モノクロは直感的でなかった)
    labelChips = listOf(Color(0xFFF2542D), Color(0xFF2EAADC), Color(0xFFB4D335), Color(0xFFF7C948)),
)

/** PRIVATE の 2 つ目: 深い緑の地に生成りの文字、マスタードのアクセント。夜向き */
val ForestPalette = Palette(
    bg = Color(0xFF1E3A32),
    fg = Color(0xFFF1EBDD),
    fgDim = Color(0xFF9DB3A5),
    line = Color(0xFF2F5247),
    accent = Color(0xFFE0B04F),   // マスタード
    series = listOf(
        Color(0xFFE0B04F),        // マスタード
        Color(0xFFC9703F),        // 銅
        Color(0xFF8FB39A),        // セージ
        Color(0xFFF1EBDD),        // 生成り
    ),
    blocks = listOf(
        Color(0xFFE0B04F),        // 0 BAT マスタード
        Color(0xFF8FB39A),        // 1 WIFI セージ
        Color(0xFFC9703F),        // 2 SIG 銅
        Color(0xFF4F7A68),        // 3 MEM 緑
        Color(0xFF3B6252),        // 4 STO 深い緑
        Color(0xFFD65A3A),        // 5 NTF 錆(要注意)
    ),
    blockEmpty = Color(0x1FFFFFFF),
)

/** ページごとの色味の候補。先頭が既定。フッターのページ名タップで順に切り替わる */
data class ThemeOption(val name: String, val palette: Palette)

val ThemeOptions: Map<Page, List<ThemeOption>> = mapOf(
    Page.PRIVATE to listOf(ThemeOption("VIVID", VividPalette), ThemeOption("FOREST", ForestPalette)),
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

val LocalPalette = staticCompositionLocalOf { VividPalette }

val GridGap = 4.dp

/** ページのパレットを配る。Text の既定色も合わせる */
@Composable
fun PageTheme(page: Page, variant: String? = null, setIndex: Int = 0, content: @Composable () -> Unit) {
    val p = paletteFor(page, variant, setIndex)
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
            background = VividPalette.bg,
            surface = VividPalette.bg,
            onBackground = VividPalette.fg,
            onSurface = VividPalette.fg,
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

// ---- VIVID の色の組(時間や操作で回る) ----

data class AccentSet(val name: String, val colors: List<Color>)

/** 4 色: [0] アクセント(通知の件数・BAT) [1] WIFI [2] SIG [3] 差し色。チップとモザイクは 4 色を順に */
val VividSets: List<AccentSet> = listOf(
    AccentSet("SUMMER", listOf(Color(0xFFF2542D), Color(0xFF2EAADC), Color(0xFFB4D335), Color(0xFFF7C948))),
    AccentSet("TROPICAL", listOf(Color(0xFF1FA7A0), Color(0xFFFFD23F), Color(0xFFFF6B6B), Color(0xFFF26430))),
    AccentSet("SUNSET", listOf(Color(0xFFFF7A5A), Color(0xFFFFB347), Color(0xFFC44569), Color(0xFF6C4AB6))),
    AccentSet("CITRUS", listOf(Color(0xFFF9C80E), Color(0xFFF86624), Color(0xFFEA3546), Color(0xFF43BCCD))),
    AccentSet("VAPOR", listOf(Color(0xFFFF77A9), Color(0xFF9D4EDD), Color(0xFFC8B6FF), Color(0xFF8ECAE6))),
    AccentSet("RETRO", listOf(Color(0xFFE63946), Color(0xFFF1FAEE), Color(0xFFA8DADC), Color(0xFF457B9D))),
)

/** 今の時刻から決まる組の番号(1 時間ごとに変わる。同じ時間帯は同じ) */
fun hourlyVividIndex(): Int {
    val now = java.time.LocalDateTime.now()
    return ((now.toLocalDate().toEpochDay() * 24 + now.hour) % VividSets.size).toInt().let { if (it < 0) it + VividSets.size else it }
}

/** VIVID の地に色の組を当てる */
fun Palette.withAccents(set: AccentSet): Palette = copy(
    accent = set.colors[0],
    series = set.colors,
    blocks = listOf(set.colors[0], set.colors[1], set.colors[2], Color(0xFF9A9A9A), Color(0xFF666666), set.colors[0]),
    vacantAccents = set.colors,
    labelChips = set.colors,
)

/** ページの配色。VIVID なら色の組(setIndex)を当てる */
fun paletteFor(page: Page, variant: String?, setIndex: Int): Palette {
    val opt = themeOf(page, variant)
    return if (opt.name == "VIVID") opt.palette.withAccents(VividSets[setIndex.mod(VividSets.size)]) else opt.palette
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
