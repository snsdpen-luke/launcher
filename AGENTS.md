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
- グリッド寸法: カバー 4x14 / メイン 6x10(model/Surface.kt)

## 絶対に守る
- レイアウトのグリッド構造・既定サイズを変えたら LAYOUT_VERSION を上げて migrateLayout に移行ステップを足す
- 輝度は下限で丸める(1 箇所で)
- 無効化したいタップは clickable ごと外す(enabled=false は消費する)
- サイズ由来の値は 0 と負を潰す(無限ループ・例外の元)
- 画面の識別は smallestScreenWidthDp で行う
- 文字だけのボタンは clickable の後ろに padding を付ける
- 常時アニメは表示中のみ
- LayoutState.version の既定値は 1 に固定(変えない)。Json は encodeDefaults = true(既定値省略で移行が空振りする)
