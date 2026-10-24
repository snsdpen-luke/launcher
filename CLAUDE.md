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
- リモートは GitHub `snsdpen-luke/launcher`(公開、origin/main)。AI(Claude / ChatGPT / Gemini)にレビューさせる用。論点は docs/review-brief.md。push は剛さんの指示で

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
- **他アプリを「隣」に出す**: ホームは分割画面の相棒になれない(`type=home nonResizable`)。Samsung の自由配置(ポップアップ)ウィンドウを使う: **MAIN(左)か SIDE(右)のアプリをタップ** → `ModuleEvent.LaunchInPane` → `launchApp(context, app, bounds)` が `ActivityOptions.setLaunchBounds` で **右側(SIDE の矩形、`HomePane.onBounds`)に窓を出す**。SIDE にアプリ行があればその下から。ランチャーは背景に残る。カバーでは全画面起動。`POPUP_DEBUG = true` でカバーでも右半分に出して確認できる
  - 2 画面モード(非 HOME の別 Activity `PanelActivity` を分割の片側に置き、`FLAG_ACTIVITY_LAUNCH_ADJACENT` で反対側に開く)は v0.37 で試した。Android 17 ではアプリから分割が始まり、反対側の差し替えも動いたが、剛さんが「挙動がおかしい」として 2026-10-18 に取り下げた
- グリッド寸法: カバー **16x24** / メイン 22x14(model/Surface.kt、v9)。**マスは正方形**で一辺は幅と高さの小さい方から決める(カバー 555x876dp → 1 マス ≈ 28.5dp)。**マス目全体は左右中央**に置き、余りは両側の余白(半端なマスは作らない)
- ページ `Page { PRIVATE, WORK, TASK, DRIVE }`。配置は (Face, Page) ごと、定義(フォルダ/リンク/ボード)は共有
- **TASK ページ(v0.38)**: グリッドを持たず、全面が Trinity(Mac の秘書とタスク帳)の `?embed=1`(ui/TrinityPage.kt、WebView を 1 個だけ作って使い回す)。
  **タスクの正本は Trinity の帳面(all-db の BOARD.md)に一本化**。WORK の `TasksModule` は Trinity の要点(判断待ち・剛さんの番)を映すだけで、押すと TASK へ。
  初回に手元の未完了タスクを一度だけ Trinity へ送る(`Trinity.migrateOnce`、印は SharedPreferences `trinity/migrated`。手元のタスクは消さない)。
  URL は **local.properties の `trinity.url`**(鍵つき。**この repo は公開なのでコードに書かない**)→ BuildConfig.TRINITY_URL。空なら従来の手元のタスク。
  WebView が指を取るので、横スワイプは Trinity の画面が拾って JS の `Launcher.page(next)` で返す。confirm/alert/prompt は WebChromeClient で Activity に出す(無いと confirm が常に「いいえ」)。
  Trinity 以外の URL(Claude アプリの入口など)は外のアプリで開く
- **書体(v0.38)**: IBM Plex Sans JP(`res/font/ibm_plex_sans_jp_{regular,medium,bold}.ttf`、SIL OFL 1.1。条件の文は `licenses/` と `assets/licenses/` に同梱。**消さない**)に詰め組み `palt` と -0.01em。`Theme.kt` の `appTypography()` を MaterialTheme に渡す。時計の数字(TextMeasurer)は対象外。TASK ページ(Trinity)も同じ書体
- 配色はページごとの `Palette`(ui/Theme.kt)。画面もモジュールも `LocalPalette.current` を読む
  - 1 ページに複数の色味を持てる(`ThemeOptions`)。フッター右のページ名タップで順に切替、選択は DataStore の `theme_<PAGE>`
  - PRIVATE は **NATURE**(深い青緑の海の地に白文字、青緑 #00AA90 と海の青が差し色。既定)→ **LEMON**(Ultimate Gray #C5C1C0 の紙に鉄色の文字、Illuminating #F5DF4D のチップ、DENIM #1A2930 が 2 色目。昼向き。白地は「ブロックが目立つ」で不採用)。どちらも見出しは**床のブロックと同じ形のチップ**(1 マスずつ独立したブロックで目地を見せる。幅は文字に合わせてマス数を切り上げ、`labelChips` の色を 40% で透かす。文字はその上に fg で)、空きマスをマスコットが歩く。アイコンは色付き。VIVID(色の組の時間回転と■シャッフル)・FOREST・BLACK・CREAM・紺の COLORFUL は不採用(2026-09-22)
  - **床のモザイク**は `Palette.floor`(上→下で明→暗の単色 5 段。うっすら)。ModuleGrid の `MosaicFloor` が全マスの後ろに 1 マス 1 ブロックで敷く(マス目ぴったり。余りは余白)。`stableHash` で 1 段だけ揺れ、**30 秒に 1 回マスの 1/4 を選び直して 0.6 秒でにじむ**(表示中のみ)。ブロック自体は単色(1 個ずつのグラデーションは不採用)
  - 空きマスを歩くのは **Claude Code のマスコット**(ui/Mascot.kt `ClaudeMark`、ドット絵を自前で描く。本体は `vacantAccents[0]`、目は暗い方の色。姿勢は `MascotPose`)。1 体だけ。跡が薄く残る。**夜 9 時〜朝 5 時は寝る**(止まって目が横線、頭の上に z が 3 つ浮かぶ。`isSleepTime`)。**タップでごはん**: 1.4 秒踊る(跳ねる・腕を上げる・目が ^ ^・ドット絵のハート `PixelHeart`)→ 1.8 秒でおにぎり `FoodBlock` を 3 口(左から欠ける)→ 歩く / 寝直す。状態は `WanderingBlocks` の中に閉じる。確認は `MASCOT_FORCE_NIGHT = true`(止まるのでタップしやすい)
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
- **フッター**は Box に 3 つを重ねて置く(Row だと左右の幅の差で APPS が中央からずれる): 左端に近道 3 つ(設定・カメラ・マップ。絵は自前 `GearGlyph` / `CameraGlyph` / `PinGlyph`、穴は Offscreen レイヤーで Clear。`FooterButton` は当たり判定 44x36dp、押している間だけ薄い四角)、真ん中に「^ APPS」(山形は自前で描く `ChevronUpGlyph`。フォントの ▲ は書体が混ざって欠けることがある。文字は行の高さを決め打ち)、右端は 2 段で上にページ名・色味(タップで切替)、下に小さく版。編集中は近道を出さず、真ん中が + ADD / REMOVE / DONE
- **隠しパネル**(ui/QuickPanel.kt): **壁の右下の 1 マス**(予約席、薄い印 `SlidersGlyph`)から開く。右下の上に**縦のブロックの段 3 本**: LIGHT(背面ライト。`data/Torch` = CameraManager、権限不要。段は端末が決める、SM-F971Q は 1..5。一番下が OFF。外で点けた分も TorchCallback で拾う)・BRT(12 段、WRITE_SETTINGS)・VOL(12 段)。押した段に一発、縦になぞれば連続。外を押す / 戻る で閉じる。閉じてもライトは点いたまま
- **右下の 1 マスは予約席**(`GridSpec.reservedCorner`、SIDE 以外)。`GridPos.fits` が弾くので、ドロップ・リサイズ・新規配置・移行のどれでもそこには置けない。歩くマスコットも入らない。v29 で既にかかっていた物は幅(無理なら高さ)を 1 縮めて退かせた(DRIVE の音量の段、WORK の右下のリンク等)
- **ページの自動切り替え**(data/AutoPage.kt): 車(Bluetooth の機器の種類がカーオーディオ / ハンズフリー)に繋がると DRIVE、切れると時間割のページ(`CarReceiver`、ACL の放送はマニフェストで受けられる)。時間割は平日 7:30〜17:30 が WORK、それ以外 PRIVATE(`WorkStart` / `WorkEnd`)。**境目をまたいだ時だけ**動かす(`slot` = 日付 + ページ)。手でスワイプした日は時間割で動かさない(`markManual`)。車は常に効く。判定はホーム復帰時と表示中 1 分ごと
- **次の予定**(`next`、ui/modules/NextEventModule.kt、既定 12x1): 「あと 25 分 · 職員会議」/ 最中は「いま · … · 残り N 分」/ 3 時間超は「14:30 から」。右のブロックは 10 分 1 個(最大 6、残り 10 分で橙)。終日は外す。24 時間以内に無ければ「予定なし」。予定は 5 分ごと、時刻は 30 秒ごと(表示中のみ)。確認は `NEXT_DEBUG = true`(25 分後の偽予定、カレンダーには書かない)
- **授業タイマー**(`timer`、ui/modules/TimerModule.kt + data/ClassTimer.kt、既定 8x1): 45 / 50 のブロックで開始 → おやすみモード(優先のみ。元の設定を覚える)+ 正確なアラーム(USE_EXACT_ALARM、`TimerReceiver`)。動作中は残り時間 + 減るブロック + STOP。終わるとおやすみモードを戻して震わせ、「おわり」が橙で光る(押すと消える)。おやすみモードの許可(ACCESS_NOTIFICATION_POLICY の設定画面)が無ければ開始せず設定を開く。1 秒ごとに描き直す行の押せる所は `rememberTap`(受け口を作り直すとタップが捨てられる)
- `+ ADD` に NEXT / TIMER(そのページに無ければ空きに置く `addSingleRef`)。v30 で PRIVATE / DRIVE の時計の下に next、WORK に timer を置いた
- **アプリ一覧**(`AppDrawer`)の閉じ方は 戻る / ✕ / **下スワイプ**。下スワイプは一覧が一番上の時だけ(NestedScrollConnection で余った下向きを拾う)、見出しの行を掴んでも可。指に合わせて一覧が下がり、120dp 以上か速く弾くと閉じる
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
