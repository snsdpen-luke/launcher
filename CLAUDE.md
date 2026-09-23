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
- リモートは GitHub `snsdpen-luke/launcher`(非公開、origin/main)。チーム共有用。push は剛さんの指示で

## 骨格
- `Surface { HOME, SPLIT_PANEL, OVERLAY_PANEL }` … 窓の種類。`LauncherApp(surface)` の引数 1 つ
- `Face { COVER, MAIN }` … 配置面。`faceOf(surface, isWide)`、isWide は smallestScreenWidthDp >= 600
- `LayoutState`(model/Layout.kt)を JSON で DataStore。`LauncherStore`(Application 直下)が StateFlow で公開
- モジュールは `ModuleSpec`(model/ModuleRegistry.kt)。追加は **ui/modules に 1 ファイル + ModuleRegistry.all に 1 行**
  - サイズは Face で引く、出せるかは Surface で引く
  - 参照キー: `"clock"` / `"folder:<id>"`。表示名を変えてもキーは変えない
- グリッド(ui/ModuleGrid.kt): 絶対セル配置。タップ/長押し/ドラッグ/入替/リサイズはラッパーが一元処理。モジュール本体は受動
  - ドロップは `resolveDrop`(model/Grid.kt): 枠内に丸め → そのまま → 1 個と入替 → 2 マス以内の空きへ寄せる → 重なる物を空きへ押しのける。ドラッグ中に落とし先の枠(入る = アクセント、入らない = 赤)
- 面 `Face { COVER, MAIN, COVER_WIDE, SIDE }`。COVER = カバー縦 16x24 / COVER_WIDE = カバー横 26x15(マスの大きさは縦と同じ。配置は独立、初回は縦の配置を幅 8 の縦の帯に流して生成 `packInto`) / MAIN = 開いた時の**左パネル**。カバーと同じ 16x24 だが**配置は独立**(v24、初回はカバーの写し。大画面で見たい物だけ残す) / SIDE = 開いた時の右側 14x24。**窓の置き場**で、空いている時は予定 + タスク(`seedSide`)。`HomeScreen(side = HomePane)` で 2 枚並べる。`faceOf(surface, isWide, isLandscape)`。メインで選択・追加・削除の宛先は `selectedFace`
- **他アプリを「隣」に出す**: ホームは分割画面の相棒になれない(`type=home nonResizable`、LAUNCH_ADJACENT も全画面になる。実機で確認済み 2026-09-22)。代わりに Samsung の自由配置(ポップアップ)ウィンドウを使う: **MAIN(左)か SIDE(右)のアプリをタップ** → `ModuleEvent.LaunchInPane` → `launchApp(context, app, bounds)` が `ActivityOptions.setLaunchBounds` で **右側(SIDE の矩形、`HomePane.onBounds`)に窓を出す**。SIDE にアプリ行があればその下から。ランチャーは背景に残る。カバーでは全画面起動。`POPUP_DEBUG = true` でカバーでも右半分に出して確認できる
- グリッド寸法: カバー **16x24** / メイン 22x14(model/Surface.kt、v9)。**マスは正方形**で一辺は幅と高さの小さい方から決める(カバー 555x876dp → 1 マス ≈ 28.5dp)。**マス目全体は左右中央**に置き、余りは両側の余白(半端なマスは作らない)
- ページ `Page { PRIVATE, WORK, DRIVE }`。配置は (Face, Page) ごと、定義(フォルダ/リンク/ボード)は共有
- 配色はページごとの `Palette`(ui/Theme.kt)。画面もモジュールも `LocalPalette.current` を読む
  - 1 ページに複数の色味を持てる(`ThemeOptions`)。フッター右のページ名タップで順に切替、選択は DataStore の `theme_<PAGE>`
  - PRIVATE は **NATURE**(深い青緑の海の地に白文字、青緑 #00AA90 と海の青が差し色。既定)→ **LEMON**(Ultimate Gray #C5C1C0 の紙に鉄色の文字、Illuminating #F5DF4D のチップ、DENIM #1A2930 が 2 色目。昼向き。白地は「ブロックが目立つ」で不採用)。どちらも見出しは**床のブロックと同じ形のチップ**(1 マスずつ独立したブロックで目地を見せる。幅は文字に合わせてマス数を切り上げ、`labelChips` の色を 40% で透かす。文字はその上に fg で)、空きマスをマスコットが歩く。アイコンは色付き。VIVID(色の組の時間回転と■シャッフル)・FOREST・BLACK・CREAM・紺の COLORFUL は不採用(2026-09-22)
  - **床のモザイク**は `Palette.floor`(上→下で明→暗の単色 5 段。うっすら)。ModuleGrid の `MosaicFloor` が全マスの後ろに 1 マス 1 ブロックで敷く(マス目ぴったり。余りは余白)。`stableHash` で 1 段だけ揺れ、**30 秒に 1 回マスの 1/4 を選び直して 0.6 秒でにじむ**(表示中のみ)。ブロック自体は単色(1 個ずつのグラデーションは不採用)
  - 空きマスを歩くのは **Claude Code のマスコット**(ui/Mascot.kt `ClaudeMark`、ドット絵を自前で描く。本体は `vacantAccents[0]`、目は暗い方の色)。1 体だけ。跡が薄く残る
  - 演出は `Palette` のフィールドで切る: `monoIcons` / `labelChips` / `vacantAccents`(歩くブロック)。位置や順番で決まる擬似乱数 `stableHash`(起動のたびに変わらない)
  - WORK の壁は極薄(`WorkPalette.floor`、白の 5 段)
  - 色味を足す = `Palette` を 1 個書いて `ThemeOptions` に 1 行
- **DRIVE**: 濃いグレーの壁(モザイク、`DrivePalette.floor`)に、時計(白とグレーだけ) + BAT | SIG | WIFI | BT + 生きたタイル 3 枚 + 明るさ・音量のスライダー。計測色は `DrivePalette.meterColors`
  - タイル = `PanelDef` を 3 行以上の高さで置くと `BigTile` 描画。板は `TileMosaic`(1 マス 1 ブロック。**アイコンと名前の下のマスだけ壁と同じグレー**(名前の幅からマス数を出す)、アイコンは左端の 1 マスにぴったり。残りはブランド色で左上→右下の明→暗 + 揺らぎ。絵は全高。再生中は 0.48 秒ごとの「拍」でブロックの 3 割がランダムな明るさ(白寄り・黒寄り・中間)へ飛び、間は滑らかに追う。本物の拍は取れない)。色は `BrandColors`(YouTube 赤 / Spotify 緑 / マップ青緑、無ければアイコンの平均色)。中身は再生中の曲名・アーティスト・ジャケット(右にマス単位の幅で置き、`ArtMosaic` がマスごとに切って描く: 溝は絵を 35% で薄く描いた層(線は乗せない)、各マスは不透明度付き(止まっていれば 100%、再生中は拍で 20〜100% がランダムに入れ替わり、透けた分は下のブランド色が見える)。横長の絵は 1.35 倍にして黒い帯を切る。拍は `TilePulse` を板と絵で共有)・⏮⏯⏭(下に固定、文字側を切る)(`data/MediaNow` = メディアセッション、通知アクセスで読める。表示中だけ start/stop。操作は `ModuleEvent.MediaControl`)、再生が無ければ通知本文(`NotifListener.latest`、マップのナビ案内と矢印)
  - スライダー = `slider:brightness` / `slider:volume`(ui/modules/SliderModule.kt、定義不要の固定 ref)。1 マス 1 ブロック(BRT 琥珀 / VOL 橙赤)、押した位置の段に一発、なぞれば連続(編集中は触れない)。明るさは WRITE_SETTINGS(初回は許可画面へ。自動明るさは切る)、音量は権限不要。表示中 1 秒ごとに読み直す
- アプリは `PanelDef`(`panel:<id>`、1 行。アイコンは 1 マスいっぱい = 床のブロックと同じ大きさ、名前は 11sp でアイコンの縦中央に揃える)、見出しは `LabelDef`(`label:<id>`)。編集フッターの `+ ADD` から APP / LABEL / BOARD
- PRIVATE の初期配置は model/Layout.kt の `PrivateSeed`(パッケージ名の表)。端末にあるものだけ置く
- 時計は **1 行**(既定 8x1、最小 6x1)。時刻の数字の高さをモジュールの高さ(= マス)の 75% に(`TimeHeight`)、下辺をマスの下辺に合わせ、秒・日付・曜日を右にベースライン揃えで並べる。Text ではなく `TextMeasurer` + `drawText` で自分で描く(フォントの上下余白でマス目とずれるのを避ける。Roboto の数字の高さ = 0.711em)。v18 で既存の時計を 1 行に詰めた
- WORK の予定(`CalendarModule`)は見出しの下に **7 日のブロックの帯**(1 日 1 マス、今日は塗り、件数で濃さ。タップでカレンダー)、その下に **日ごとの見出し**(TODAY · 9/22 TUE / TOMORROW · … / 9/24 THU)の下に予定を並べ、左に時刻の列(終日は ALL DAY を小さく薄く)、右に題名 1 行。高さに入るだけ詰め、入らない分は見出し右の +N。タスク(`TasksModule`)は行の高さをマス + 目地に揃え、左に **チェック箱**(見た目 18dp、当たり判定は 1 マス。箱を押すと完了⇄未完了、文字を押すと編集、長押しで配置編集)。完了は取り消し線。期限は過ぎていれば橙、今日なら濃く太く
- 計測は `MeterDef`(`meter:<id>`、metric = battery/wifi/signal/bluetooth/memory/storage/notif/blank)。**2 マスの BAT/WIFI/SIG/BT は左右 2 つの独立したマス**: BAT は 51% 以上で 2 つ点灯、50% 以下で左だけ、20% 以下オレンジ、10% 以下赤、充電中は黄。WIFI/SIG/BT は左が強さ(濃さ)、右がスイッチ(ON/OFF/DATA。タップで Wi-Fi パネル / インターネットパネル / BT の確認ダイアログ。アプリから直接は切れない、BLUETOOTH_CONNECT を初回に要求)。右マスの `clickable` は編集中は外す。3 マス以上はブロック数、1 マスは濃さ。**色相は全配色で共通**(BAT 黄緑 / SIG 紫 / WIFI 黄 / BT 水色)、色味は `Palette.meterColors` で地に合わせる(無ければ `MeterFixedColors`)。`meterColor(p, metric)` で引く。`MeterDef.style = "text"` は数字と ON/OFF の文字表示(タップで同じスイッチ。WORK も v28 でブロック表示に変えたので今は未使用)。WORK の計測色は `WorkPalette.meterColors`(薄い 4 色)。NTF/MEM/STO のブロックは v16 で消した(文字表示は残る)。並びは v17 で BAT | SIG | WIFI | BT。編集で再タップ = 色を回す(固定色の 4 つには効かない)
- 通知の明滅は **アイコンの後ろの 1 マス**(`ModuleScope.cell`)を `NotifOrange` で塗って `blinkAlpha`。アイコンと件数の文字は明滅しない。確認は `BLINK_DEBUG = true` で全マスを光らせる(通知を横取りしていた QuietInbox は 2026-09-22 に削除済み。実通知で点滅する)
- ステータスバーは MainActivity で隠している(上端スワイプで一時表示)。代わりが床の WIFI/SIG/BAT と NTF、パネルの件数。通知件数は notif/NotifListener(通知アクセスを設定でオン、復帰時に requestRebind)。未読があるとアイコン・件数・NTF が明滅(`blinkAlpha`、`BLINK_DEBUG = true` で強制点滅して動作確認)
- リンクは `LinkDef` を `link:<id>` モジュールで 1 本ずつ画面に置く(半幅 1 行。アプリの行と同じ形で、先頭は自前の鎖アイコン `LinkIcon`(1 マス)、名前は 11sp で縦中央揃え。ドメインは出さない。タップで開く、編集で名前と URL)。追加は `+ ADD → LINK` か共有シート(`ShareActivity`、ACTION_SEND text/plain → 両面の WORK に置く)
  - ボード(`board:<id>`)は v15 で廃止。コードは残っているが + ADD からは足せない。既存のボードは中身を link に展開済み

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
