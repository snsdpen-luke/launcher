# 次期ランチャー 引き継ぎ仕様書

FOLD LAUNCHER (v1.9.1 / 2026-09-18 時点) で得た**設計判断・アルゴリズム・実機で踏んだ罠**を、
まったく新しいランチャーを作る人(と Claude)に引き継ぐための文書。

対象読者は「新プロジェクトの最初のチャットに入る Claude」と、その後の自分。
**コードのコピー元ではなく、判断のコピー元**として使う。

- 現行リポジトリ: `https://github.com/snsdpen-luke/fold-launcher.git`
- 現行規模: Kotlin 約 9,000 行 / 28 ファイル
- 実機: Galaxy Z Fold7 (SM-F971Q) / One UI 8 (Android 16)
- 現行の詳細な変更履歴は `docs/requirements.md`、開発規約は `CLAUDE.md`

---

## 0. 最初に決めること(新アプリの設計判断)

新しいランチャーを作るなら、最初に以下を決めてから書き始める。
現行アプリはこれらを**後から**変えたせいで移行コストを払っている。

| 決めること | 現行の答え | 新アプリへの推奨 |
|---|---|---|
| 配置モデル | 絶対セル座標(col,row,colSpan,rowSpan) | **踏襲**。自由配置より遥かに扱いやすい |
| 画面の数 | カバー / メイン / DRIVE / ロック | 最初は**カバー相当の1面だけ**作る。増やすのは後でよい |
| 状態の持ち方 | `LayoutState` 1個を JSON で DataStore | **踏襲。ただし Flow で公開する**(後述 §3) |
| モジュール追加の手数 | 5 箇所に追記が必要 | **1 箇所(レジストリ)に集約する**(後述 §4) |
| 見た目 | NASAパンク(モノスペース+シアン) | **捨ててよい。ここが新規性の置き場所** |
| 数値表現 | 全数値を 7 セグ描画 | 好みが割れた。**やるなら最初から、部分適用はしない** |

---

## 1. アーキテクチャ(引き継ぐ骨格)

```
MainActivity (HOME + LAUNCHER)          … ホーム本体
PanelActivity (LAUNCHER, 非HOME)        … 分割画面用の入口
OverlayService                          … 常駐サイドパネル(どのアプリの上にも出る)
        └ すべて LauncherApp(surface) という 1 つの Composable を共有

LauncherApp
 ├ HomeScreen        … グリッド + フッター + 編集モード
 │   └ ModuleGrid    … セル座標で絶対配置、ドラッグ/入れ替え/リサイズを一元処理
 │       └ 各 ModuleView (パッシブ。状態を持たない)
 ├ LockScreen        … 消灯検知 → STANDBY → 上スワイプ解除
 ├ BootOverlay       … 起動シーケンス演出
 ├ DriveScreen       … 運転用の別ページ
 ├ AppDrawer / FolderOverlay / 各種シート
 └ LayoutRepository  … DataStore(JSON) 永続化
```

### 1-1. 最重要: 表示面を引数 1 つで切り替える

現行は `LauncherApp(panelMode: Boolean, overlayMode: Boolean)`。**これは後付けで、失敗している**。
新アプリでは最初から enum で持つ。

```kotlin
enum class Surface { HOME, SPLIT_PANEL, OVERLAY_PANEL }

@Composable fun LauncherApp(surface: Surface)
```

面ごとに変わるのは以下だけ。ここを最初に洗い出しておくと後が楽。

| | HOME | SPLIT_PANEL | OVERLAY_PANEL |
|---|---|---|---|
| グリッド | 画面サイズで判定 | 常に狭い方 | 常に狭い方 |
| ロック画面/起動演出 | 出す | 出さない | 出さない |
| 権限リクエスト(ActivityResult) | 使える | 使える | **使えない**(Activity 外) |
| 戻るキー | Activity | Activity | 自前 Dispatcher が要る |

### 1-2. モジュールは受動的に

`ModuleView` は**自分で状態を持たず、イベントを上に投げるだけ**。
タップ/長押し/ドラッグはグリッド側のラッパーが一元処理する。
例外はフェーダーの横ドラッグのみ。この規律を崩すと編集モードとタップが必ず衝突する。

---

## 2. グリッドと配置アルゴリズム

- セル: `GridPos(col, row, colSpan, rowSpan)` / `GridSpec(cols, rows)`
- 現行: カバー `4x14`、メイン `6x10`
- 主要関数(そのまま移植する価値がある):
  - `canPlace(target, spec, others)` … 枠内 & 他と重ならないか
  - `placeInFirstFreeSized(refs, spec, ref, w, h)` … 左上から空きを走査して配置
  - `resolveSwap(pm, target, spec, others)` … ドロップ先が他モジュールなら**双方が収まる場合だけ**入れ替え
  - `minSpanOf(ref, isWide)` … リサイズ下限(モジュールごと)

**表示面の判定は画面の向きに影響されない値で行う。**

```kotlin
// 幅で判定すると「カバーを横向き」にしただけで内側画面と誤判定する(実際にやらかした)
val isWide = LocalConfiguration.current.smallestScreenWidthDp >= 600
```

---

## 3. 永続化(ここは丸ごと引き継ぐ価値がある)

```kotlin
@Serializable data class LayoutState(
    val version: Int,
    val folders: List<FolderDef>,      // 定義は全画面で共有
    val panels: List<PanelDef>,
    val links: List<LinkDef>,
    val cover: List<PlacedRef>,        // 配置は画面ごとに独立
    val main: List<PlacedRef>,
    val drive: List<DriveSlot>,
    val missions: List<MissionDef>,
    val bootMode: String,
)
@Serializable data class PlacedRef(val ref: String, val col: Int, val row: Int,
                                   val colSpan: Int = 1, val rowSpan: Int = 1)
```

- **参照は文字列キー(`ref`)**。`"clock"` / `"folder:<id>"` / `"panel:<id>"` / `"link:<id>"`。
  表示名を変えても**キーは絶対に変えない**(現行の `"mission"` は UI 上 TASK に改名済みだがキーは据え置き)
- 「定義は共有、配置は画面ごと」。片方の画面からだけ外す `[HIDE HERE]` が成立する

### 3-1. マイグレーション規約(最初の日から入れる)

```kotlin
const val LAYOUT_VERSION = 18
fun migrateLayout(s0: LayoutState): LayoutState? {
    var s = s0
    if (s.version == 6) { s = s.copy(version = 7, /* 差分だけ */) }
    if (s.version == 7) { ... }
    // 1 段ずつ。飛ばさない
    return if (s.version == LAYOUT_VERSION) s else null   // null = 作り直し
}
```

規約:
1. **グリッド構造・既定サイズを変えたら必ず版を上げて移行ステップを足す。安易にリセットしない**
2. フィールド追加は**既定値付き**なら版を上げない(`= emptyList()` 等)
3. カタログにモジュールを足すだけなら版を上げない(自動配置しないなら)
4. 移行が起きたら即保存(次回起動で再移行しない)

---

## 4. モジュール追加を 1 箇所にする(現行の反省)

現行はモジュールを 1 つ足すのに **5 箇所**へ追記が必要:
`Module` sealed interface / `REF_*` 定数 / `WidgetCatalog` / `resolveLayout`+`moduleRef` / `ModuleGrid` の `when`。

新アプリではレジストリ 1 枚にまとめる。

```kotlin
data class ModuleSpec(
    val ref: String,
    val name: String,
    val desc: String,
    val defaultSize: Map<Surface, IntSize>,
    val minSize: Map<Surface, IntSize>,
    val surfaces: Set<Surface> = Surface.entries.toSet(),
    val content: @Composable (ModuleScope) -> Unit,   // ← 描画もここに置く
)
val ModuleRegistry: List<ModuleSpec> = listOf(...)
```

こうすると `when` が消え、モジュール追加が 1 ファイルの追記で済む。

---

## 5. 機能カタログ(現行の実装と、その要点)

新アプリで作り直すときに「何が要るか」を思い出すための一覧。
現行ソースの場所を併記しているので、必要なら読みに行く。

### 5-1. ホーム / 編集
| 機能 | 要点 | 現行 |
|---|---|---|
| モジュールグリッド | 絶対配置、ドラッグで移動、重なりは入れ替え | `ui/ModuleGrid.kt` |
| 編集モード | 長押しで開始。タップ=選択 → リサイズハンドル + `[W±][H±]` | `ui/HomeScreen.kt` |
| フォルダ | カテゴリ(`ApplicationInfo.category`)で初期自動分類、中身の並び替え可 | `ui/FolderEditSheet.kt` |
| アプリパネル | アプリ 1 個を直接起動する枠。別名を付けられる | `ui/AppPanelSheet.kt` |
| リンク | URL ショートカット | `ui/LinkEditSheet.kt` |
| ドロワー | フッター行がハンドル(タップ / 上ドラッグ) | `ui/AppDrawer.kt` |
| モジュール追加シート | 画面ごとに ADD/REMOVE。空きが無ければ `NO ROOM` | `ui/WidgetSheet.kt` |

### 5-2. 計器モジュール(12 種)
`CLOCK` / `CALENDAR`(ドット月表示) / `TELEMETRY`(PWR・STO・MEM) / `VOL FADER` / `BRT FADER` /
`SCOPE`(メディア波形) / `RF`(WIFI・BT・NET) / `RADAR`(リング + 通知/アラーム/稼働時間) /
`WEATHER`(Open-Meteo・メイン専用) / `NEXT MISSION`(端末カレンダー・メイン専用) /
`TASK LIST`(TODO。1x1 は残件数のみ、タップで一覧) / `HOME APP`(ホームアプリ切替)

### 5-3. システム連携(移植時に効く知識)
| 対象 | API | 権限 | 罠 |
|---|---|---|---|
| 通知件数 | `NotificationListenerService` → StateFlow | 通知アクセス | 設定画面から手動許可 |
| メディア | `MediaSessionManager`(通知リスナー経由) | 同上 | アートワークは ALBUM_ART→ART→DISPLAY_ICON の順に Bitmap→URI |
| 音量 | `AudioManager` | なし | |
| 輝度 | `Settings.System.SCREEN_BRIGHTNESS` | `WRITE_SETTINGS` | **下限 0.14 を 1 箇所で丸める。真っ暗にすると戻せない** |
| Wi-Fi/BT | `WifiManager` / `BluetoothAdapter` | `ACCESS_WIFI_STATE`,`BLUETOOTH_CONNECT` | 設定画面を開く Intent は `NEW_TASK` だけだと既存タスクが前面に出るだけ。**`CLEAR_TOP|SINGLE_TOP` を併用** |
| 回線種別 | `TelephonyManager` | `READ_PHONE_STATE` | |
| 天気 | Open-Meteo (HTTP) | `INTERNET` + 位置 | API キー不要 |
| 予定 | `CalendarContract` | `READ_CALENDAR` | |
| 速度 | `LocationManager` GPS | `ACCESS_FINE_LOCATION` | 表示中のみ購読して止める |
| 方位 | `TYPE_ROTATION_VECTOR` | なし | 同上 |
| 触覚 | `VibrationEffect.EFFECT_CLICK` | `VIBRATE` | |
| アプリ一覧 | `queryIntentActivities` | `<queries>` 宣言 | **`PACKAGE_ADDED/REMOVED/...` の受信には `addDataScheme("package")` が必須**。起動時 1 回だけにすると新規インストールが出ない |

### 5-4. 折りたたみ機特有(Fold で作るなら必読)

**A. 分割画面(OS の機能に乗る)**
- **分割画面に入る公開 API は存在しない**(システム権限が要る)。入口は必ず OS 側の操作
- **HOME カテゴリを持つ Activity は分割画面に入れられない** → 分割用に**別 Activity**(非 HOME、`resizeableActivity`、ランチャー入口あり)を用意する
- 分割中に `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_LAUNCH_ADJACENT` で起動すると**反対側のペインだけ差し替わる**。左右どちらに置いても OS が「隣」を決めるので設定項目は不要
- 判定は `activity.isInMultiWindowMode`。分割していないときは何も足さない(挙動不変)
- One UI 8 の分割バーには**アプリペア保存ボタンが無い**(レイアウト変更と入れ替えのみ)。
  復元はユーザー操作(履歴 → カードの上のアイコン → 分割画面表示で起動)になる
- **自アプリのランチャー入口をドロワーから除外していると、その別 Activity も消える**(実際にやらかした)

**B. 常駐サイドパネル(オーバーレイ / こちらの方が実用的だった)**
- `TYPE_APPLICATION_OVERLAY` の窓を自前で貼る。分割に一切依存しない
- **`createDisplayContext(display).createWindowContext(TYPE_APPLICATION_OVERLAY, null)` から出す。**
  Service の Context で `getSystemService(WindowManager)` すると、窓は作られるのに**一度も描画されない**
- **非フォーカスのオーバーレイ窓では Compose の `pointerInput` にタッチが届かない。**
  ハンドルの入力は `View.setOnTouchListener` で受ける(描画だけ Compose)
- パネル本体の窓は**パネル幅だけ**にして `FLAG_NOT_TOUCH_MODAL`(フォーカスは取る)。
  → 枠の外は下のアプリへ素通しで、**開いたまま隣のアプリを操作できる**。
  全画面+暗転にすると「隣が触れない」ので意味が無くなる(最初これで失敗した)
- Android は**「設定」アプリの上ではオーバーレイを隠す**(タップジャッキング対策)。動作確認は普通のアプリで
- オーバーレイで Compose を動かすには Lifecycle / SavedState / ViewModelStore /
  OnBackPressedDispatcher を**自前で用意**する(`overlay/OverlayOwners.kt` がそのまま使える)
- 折りたたみ/展開でディスプレイが入れ替わるので `Service.onConfigurationChanged` で作り直す
- 常駐には前面サービス(`foregroundServiceType="specialUse"`)が要る = 通知が 1 本常駐する

---

## 6. 実機で踏んだ罠(これが一番の資産)

### 6-1. クラッシュ / フリーズ
1. **描画ループの進み幅ゼロ** — `while (x < w) { x += r * 1.5f }` で `r ≤ 0` になると無限ループ →
   メインスレッド停止 → ANR → 強制終了。**サイズ由来の値は必ず下限を設ける + 反復回数に上限を置く**
2. **負の Dp** — `padding(start = 負値)` / `size(負値)` は `IllegalArgumentException`。
   セルが極端に狭くなる状況(横向き・分割)で発生する。`coerceIn` で丸める
3. **サイズ 0 での除算** — `dragAmount / size.width` が `NaN` → 状態が壊れる。`if (w <= 0) return`
4. **`aspectRatio(1f)` は幅基準**。`fillMaxSize().aspectRatio(1f)` は縦長セルで枠からはみ出す。
   `BoxWithConstraints` + `size(min(maxWidth, maxHeight))` にする

### 6-2. 入力
5. **`clickable(enabled = false)` でもタップは消費される。**
   モジュール全面がタップ領域だと、編集モードで**枠を選択できない = リサイズ不能**になる。
   無効化するときは**修飾子ごと付けない**:
   ```kotlin
   fun Modifier.tapUnlessEdit(editMode: Boolean, onClick: () -> Unit) =
       if (editMode) this else this.clickable(...)
   ```
6. **文字だけのボタンはタップ領域が足りない。** `clickable` の**後ろ**に `padding` を付けて 16×11dp 以上にする
   (前に付けると領域が広がらない)
7. **スクロールとスワイプの取り合い。** グリッドが縦スクロールすると上スワイプ(ドロワー)を食う。
   縦画面ではスクロールさせない設計にした
8. ジェスチャ検出に渡す値は `rememberUpdatedState` で包む(古い値を掴んだまま動く)

### 6-3. レイアウト / 構成変更
9. **ホーム Activity は `configChanges` で回転・折りたたみを自分で吸収する**
   (`screenSize|smallestScreenSize|screenLayout|orientation|density|keyboard|keyboardHidden|navigation|uiMode`)
10. **画面の識別を「幅」でやらない**(§2)。折りたたみ機では横向きにしただけで誤判定する
11. `BoxWithConstraints` の `maxWidth/maxHeight` は入れ子のラムダで直接触らず、ローカル変数に退避する

### 6-4. 端末・開発環境
12. **複数 PC で開発するなら debug 署名鍵をリポジトリに同梱する。**
    さもないと `INSTALL_FAILED_UPDATE_INCOMPATIBLE` でデータごと消すことになる
13. **エミュレータを実機の実寸にして検証する**:
    `adb shell wm size 1248x1972; adb shell wm density 360`(カバー) /
    `2448x1848`(メイン)。実機は合成タップを無視することがあるので、**タップ検証はエミュレータ、見た目確認は実機**
14. 実機スクリーンショットは**ディスプレイ ID 指定**が要る:
    `adb exec-out screencap -p -d <id> > out.png`(`dumpsys SurfaceFlinger --display-id` で ID)
15. 無線 ADB: `adb mdns services` でペアリング用ポートが拾える → `adb pair <ip>:<port> <code>`
16. **バージョン文字列が 2 箇所にある**(`build.gradle.kts` とフッター)。片方だけ上げる事故が起きた。
    **新アプリでは `BuildConfig.VERSION_NAME` を UI から読む**

### 6-5. 省電力・作法
17. **常時アニメは「表示中のみ」に限定する。** `CompositionLocal` で on/off を配れば、
    ロック画面の裏でホームのアニメが回り続ける事故を防げる
18. 権限が無い機能は**その場で誘導**する(`TAP:PERM` のような表示 → タップで設定画面)

---

## 7. ビルドと検証(Android Studio 不使用)

```bash
# 環境: JDK 17+ / Gradle 8.13 / compileSdk 35 / minSdk 31
gradle assembleDebug -q
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n <pkg>/.MainActivity
```

検証で使うコマンド:
```bash
adb logcat -b crash -d -t 200            # クラッシュ
adb shell dumpsys window windows         # ウィンドウ状態(オーバーレイの描画確認)
adb shell dumpsys activity activities    # 前面 Activity / 分割状態
adb shell ls -t /data/anr/               # ANR トレース(要 root or 実機の設定)
```

作業サイクル(これを守ると事故が減る):
**方針説明 → 実装 → ビルド → インストール → 実機/エミュ確認 → OK ならコミット & push**

---

## 8. 新アプリを始めるときの手順

1. 新リポジトリを作る(現行を fork しない。**設計だけ持っていく**)
2. この文書を `docs/handover.md` として置く
3. `CLAUDE.md` を置く(下のテンプレ)
4. 骨格から作る: `Surface` enum → `LayoutState` + マイグレーション → `ModuleRegistry` →
   グリッド → 最小のモジュール 2〜3 個(時計・フォルダ・ドロワー)
5. **見た目は最後に決める。**骨格が動いてからデザインを当てる

### 8-1. 新リポジトリの CLAUDE.md テンプレ

```markdown
# <アプリ名> 開発メモ

Galaxy Z Fold7 (SM-F971Q) 向け自作ランチャー。<デザインコンセプトを1行で>

## 進め方
- 実装前に方針を短く説明する
- ビルドが通ったら adb インストールして報告する
- UI 変更はスクリーンショットで確認する
- 前任プロジェクトの知見は docs/handover.md にある。**触る前に必ず読む**

## 絶対に守る
- レイアウトのグリッド構造を変えたら LAYOUT_VERSION を上げて移行ステップを足す
- 輝度は下限で丸める(1 箇所で)
- 無効化したいタップは clickable ごと外す(enabled=false は消費する)
- サイズ由来の値は 0 と負を潰す(無限ループ・例外の元)
- 画面の識別は smallestScreenWidthDp で行う
```

### 8-2. 新しいチャットに貼る最初の指示(テンプレ)

```
新しいランチャーアプリを作る。ベースは既存の FOLD LAUNCHER。

まず https://github.com/snsdpen-luke/fold-launcher.git を ~/Documents/fold-launcher-app に clone して、
docs/handover-next-launcher.md を読んで設計と罠を把握して。コードは移植せず、設計だけ引き継ぐ。

作るもの: <アプリ名>。<コンセプト。例: 余白と大きな文字で、モノトーン+1色のミニマルなランチャー>
端末: Galaxy Z Fold7 (SM-F971Q)。カバー画面を主戦場にする。

環境は Mac に構築済み(JDK Corretto 19 / Gradle 8.13 は ~/gradle/gradle-8.13 / Android SDK は
~/Library/Android/sdk)。実機は無線 ADB で繋ぐ。

最初の作業:
1. 新規プロジェクトの骨格(Surface enum / LayoutState + migrateLayout / ModuleRegistry / グリッド)
2. 時計・フォルダ・ドロワーだけの最小構成で起動するところまで
3. ビルド → インストール → スクショで確認

実装前に方針を説明して。デザインは骨格が動いてから決める。
```

---

## 9. 現行アプリから「持っていかないもの」

- **NASAパンクの見た目一式**(色・モノスペース・`// TITLE` 見出し・7 セグ描画)
  — 新しさを出す場所なので、まっさらにする
- **DRIVE MODE** — 3 回作り直した。要求が固まっていない領域なので、必要になってから作る
- **起動シーケンス演出 / STANDBY 画面** — 楽しいが本質ではない。後回しでよい
- **7 セグ全数値化** — 可読性と手間の両方でコストが高い。やるなら最初から全面で
