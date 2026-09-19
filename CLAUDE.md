# LAUNCHER 開発メモ(仮名。アプリ名とコンセプトは未決)

Galaxy Z Fold7 (SM-F971Q) 向け自作ランチャー。カバー画面が主戦場。
前任 FOLD LAUNCHER の設計だけ引き継ぎ、コードは書き直している。

## 進め方
- 実装前に方針を短く説明する
- ビルドが通ったら adb インストールして報告する
- UI 変更はスクリーンショットで確認する
- 前任プロジェクトの知見は docs/handover.md にある。**触る前に必ず読む**

## ビルド(Android Studio 不使用)
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 19)
~/gradle/gradle-8.13/bin/gradle -p ~/launcher assembleDebug -q
~/Library/Android/sdk/platform-tools/adb install -r ~/launcher/app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb shell am start -n com.snsdpen.launcher/.MainActivity
```
- JDK Corretto 19 / Gradle 8.13(~/gradle/gradle-8.13) / compileSdk 35 / minSdk 31
- 実機の合成入力: タップは通る。長押し/ドラッグは `input swipe` で **1000ms**(600ms だと移動が拾われないことがある)
- 権限: READ_CALENDAR(CALENDAR モジュール)。再インストールで消えたら「TAP TO ALLOW」で再取得
- 実機は無線 ADB。スクショはディスプレイ ID 指定:
  `adb exec-out screencap -p -d 4630947123231501204 > cover.png`(カバー)/ `-d 4630947004648141459`(メイン)
- バージョンは app/build.gradle.kts の versionName だけ。UI は BuildConfig.VERSION_NAME を読む
- debug 署名鍵は keystore/debug.keystore(~/.android/debug.keystore と同一)

## 骨格
- `Surface { HOME, SPLIT_PANEL, OVERLAY_PANEL }` … 窓の種類。`LauncherApp(surface)` の引数 1 つ
- `Face { COVER, MAIN }` … 配置面。`faceOf(surface, isWide)`、isWide は smallestScreenWidthDp >= 600
- `LayoutState`(model/Layout.kt)を JSON で DataStore。`LauncherStore`(Application 直下)が StateFlow で公開
- モジュールは `ModuleSpec`(model/ModuleRegistry.kt)。追加は **ui/modules に 1 ファイル + ModuleRegistry.all に 1 行**
  - サイズは Face で引く、出せるかは Surface で引く
  - 参照キー: `"clock"` / `"folder:<id>"`。表示名を変えてもキーは変えない
- グリッド(ui/ModuleGrid.kt): 絶対セル配置。タップ/長押し/ドラッグ/入替/リサイズはラッパーが一元処理。モジュール本体は受動
- グリッド寸法: カバー **16x24** / メイン 22x14(model/Surface.kt、v9)。**マスは正方形**で一辺は幅から決める(カバー 555x876dp → 1 マス ≈ 30dp)。余りは下に残す
- ページ `Page { PRIVATE, WORK, DRIVE }`。配置は (Face, Page) ごと、定義(フォルダ/リンク/ボード)は共有
- 配色はページごとの `Palette`(ui/Theme.kt)。画面もモジュールも `LocalPalette.current` を読む
  - 1 ページに複数の色味を持てる(`ThemeOptions`)。フッター右のページ名タップで順に切替、選択は DataStore の `theme_<PAGE>`
  - PRIVATE は VIVID(黒の地に色の組 `VividSets` が 1 時間ごと・■タップで回る。見出しは色チップ、空きマスを差し色のブロック 2 個が歩く(跡がグレーで消える)。アイコンは色付き) → FOREST(深緑)。BLACK・CREAM・紺の COLORFUL は不採用
  - 演出は `Palette` のフィールドで切る: `monoIcons` / `labelChips` / `vacantAccents`(歩くブロック)。位置や順番で決まる擬似乱数 `stableHash`(起動のたびに変わらない)
  - 色味を足す = `Palette` を 1 個書いて `ThemeOptions` に 1 行
- アプリは `PanelDef`(`panel:<id>`、アイコン + 名前の 1 行)、見出しは `LabelDef`(`label:<id>`)。編集フッターの `+ ADD` から APP / LABEL / BOARD
- PRIVATE の初期配置は model/Layout.kt の `PrivateSeed`(パッケージ名の表)。端末にあるものだけ置く
- 床のタイルは `MeterDef`(`meter:<id>`、metric = battery/wifi/signal/memory/storage/notif/blank)。1 マス = 1 ブロックで、値は埋まったブロックの数。色は **配色の `Palette.blocks`**(番号 0..5 = BAT WIFI SIG MEM STO NTF、`meterShadeFor`)。文字は色の明るさで白/濃紺を自動。編集で再タップ = 色を回す
- ステータスバーは MainActivity で隠している(上端スワイプで一時表示)。代わりが床の WIFI/SIG/BAT と NTF、パネルの件数。通知件数は notif/NotifListener(通知アクセスを設定でオン、復帰時に requestRebind)
- リンクは `BoardDef`(ボード)にまとめて `board:<id>` モジュールで表示。追加経路は 2 つ:
  編集シート(BoardEditSheet)と共有シート(`ShareActivity`、ACTION_SEND text/plain)

## 絶対に守る
- レイアウトのグリッド構造・既定サイズを変えたら LAYOUT_VERSION を上げて migrateLayout に移行ステップを足す
- 輝度は下限で丸める(1 箇所で)
- 無効化したいタップは clickable ごと外す(enabled=false は消費する)
- サイズ由来の値は 0 と負を潰す(無限ループ・例外の元)
- 画面の識別は smallestScreenWidthDp で行う
- 文字だけのボタンは clickable の後ろに padding を付ける
- 常時アニメ・定期の読み直しは表示中のみ。ループは `WhileResumed`(ui/Theme.kt、repeatOnLifecycle RESUMED)で包む。裏で 0.0% を実機で確認済み(2026-09-19)
- 毎秒・毎 0.7 秒の状態は自分の Composable に閉じる(グリッド本体のラムダで読むと全モジュールが再構成される)
- LayoutState.version の既定値は 1 に固定(変えない)。Json は encodeDefaults = true(既定値省略で移行が空振りする)
