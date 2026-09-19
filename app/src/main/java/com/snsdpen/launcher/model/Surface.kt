package com.snsdpen.launcher.model

/**
 * どの窓に描いているか。LauncherApp(surface) の引数 1 つで切り替える。
 * HOME 以外は席だけ確保(現時点では未配線)。
 */
enum class Surface { HOME, SPLIT_PANEL, OVERLAY_PANEL }

/** 配置(レイアウト)を持つ物理面。定義(フォルダ等)は共有、配置は面ごとに独立 */
enum class Face { COVER, MAIN }

/**
 * ユーザーが横スワイプで切り替えるページ(用途)。順にループする。
 * DRIVE も特別な画面ではなく、同じグリッドの 1 ページ。専用計器はモジュールとして足す。
 */
enum class Page {
    PRIVATE, WORK, DRIVE;

    fun next(): Page = entries[(ordinal + 1) % entries.size]
    fun prev(): Page = entries[(ordinal - 1 + entries.size) % entries.size]
}

/**
 * 面の決定。HOME だけ画面サイズで判定し、パネル系は常に狭い方(カバー相当)。
 * isWide は smallestScreenWidthDp >= 600 で求める(幅で判定すると横向きカバーを誤判定する)。
 */
fun faceOf(surface: Surface, isWide: Boolean): Face =
    if (surface == Surface.HOME && isWide) Face.MAIN else Face.COVER

// v9: 正方形のマス。一辺は幅から決める(カバー 555dp → 1 マス ≈ 30dp)。変えるときは LAYOUT_VERSION を上げて移行する
val CoverGrid = GridSpec(cols = 16, rows = 24)
val MainGrid = GridSpec(cols = 22, rows = 14)

fun gridFor(face: Face): GridSpec = when (face) {
    Face.COVER -> CoverGrid
    Face.MAIN -> MainGrid
}
