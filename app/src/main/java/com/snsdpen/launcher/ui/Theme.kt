package com.snsdpen.launcher.ui

import androidx.compose.material3.MaterialTheme
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
)

/** PRIVATE: ダークモード。青みを抜いた暖かい黒に生成り寄りの文字(ブルーライトを減らす)。床は白黒グレーの濃淡 */
val PrivatePalette = Palette(
    bg = Color(0xFF121110),
    fg = Color(0xFFE8E0D2),
    fgDim = Color(0xFF8C857A),
    line = Color(0xFF2A2825),
    accent = Color(0xFFD9B36A),   // 柔らかい琥珀(通知の件数)
    series = listOf(Color(0xFFE8E0D2), Color(0xFFA8A196), Color(0xFF6E685F), Color(0xFFD9B36A)),
    blocks = listOf(
        Color(0xFFD9D2C6),        // 0 BAT 明るいグレー
        Color(0xFFB3AB9E),        // 1 WIFI
        Color(0xFF8C857A),        // 2 SIG
        Color(0xFF6E685F),        // 3 MEM
        Color(0xFF56514A),        // 4 STO(空 #2A2927 とは離す)
        Color(0xFFD9B36A),        // 5 NTF 琥珀(要注意)
    ),
    blockEmpty = Color(0x1AFFFFFF),
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

/** PRIVATE の 2 つ目: 黒・白・グレーの地に、印刷の CMY(マゼンタ・シアン・イエロー)を差す */
val VividPalette = Palette(
    bg = Color(0xFF141414),
    fg = Color(0xFFFFFFFF),
    fgDim = Color(0xFF9A9A9A),
    line = Color(0xFF2C2C2C),
    accent = Color(0xFFFF2D8F),   // マゼンタ(通知の件数)
    series = listOf(
        Color(0xFFFF2D8F),        // マゼンタ
        Color(0xFF22D3EE),        // シアン
        Color(0xFFFFE600),        // イエロー
        Color(0xFFFFFFFF),        // 白
    ),
    blocks = listOf(
        Color(0xFFFFE600),        // 0 BAT イエロー
        Color(0xFF22D3EE),        // 1 WIFI シアン
        Color(0xFFFF2D8F),        // 2 SIG マゼンタ
        Color(0xFF9A9A9A),        // 3 MEM グレー
        Color(0xFF666666),        // 4 STO 濃いグレー(空 #2C2C2C とは離す)
        Color(0xFFFF2D8F),        // 5 NTF マゼンタ(要注意)
    ),
    blockEmpty = Color(0x1FFFFFFF),
)

/** PRIVATE の 3 つ目: 深い緑の地に生成りの文字、マスタードのアクセント。夜向き */
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
    Page.PRIVATE to listOf(ThemeOption("BLACK", PrivatePalette), ThemeOption("VIVID", VividPalette), ThemeOption("FOREST", ForestPalette)),
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

val LocalPalette = staticCompositionLocalOf { PrivatePalette }

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
            background = PrivatePalette.bg,
            surface = PrivatePalette.bg,
            onBackground = PrivatePalette.fg,
            onSurface = PrivatePalette.fg,
        ),
        content = content,
    )
}

/** 復帰や権限付与のたびに増える。外部データ(予定など)を読み直す合図 */
val LocalRefreshTick = androidx.compose.runtime.compositionLocalOf { 0 }
