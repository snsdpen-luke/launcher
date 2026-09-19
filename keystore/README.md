# keystore/

`debug.keystore` は開発用の共通署名鍵(Android 標準の debug 鍵と同じ規約)。
どのPCでビルドしても同じ鍵で APK が署名され、実機へ上書きインストールできるよう
リポジトリに同梱している(リリース配布用の鍵ではない)。

## 新しいPCで最初に1回だけやること

同梱の鍵を、そのPCの「ユーザーの debug 鍵」の場所にコピーする(既存があれば上書き)。

- Windows (PowerShell、リポジトリ直下で):
  `Copy-Item .\keystore\debug.keystore "$env:USERPROFILE\.android\debug.keystore" -Force`
- macOS / Linux:
  `cp keystore/debug.keystore ~/.android/debug.keystore`

以降は通常どおり `gradle assembleDebug` → `adb install -r` でよい。
