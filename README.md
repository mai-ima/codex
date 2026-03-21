# Neon Sprint

`Neon Sprint` は Android 向けのスマホ用レースゲームです。ネオン調の3レーン道路を走り、左右タップで車線変更しながらライバル車を避け、ブーストリングで加速してスコアを伸ばします。

## ゲーム内容

- **プラットフォーム:** Android ネイティブアプリ
- **操作:** 左右タップで車線変更、画面中央タップで加速
- **ゲーム性:** エンドレスラン型のレースゲーム
- **保存:** ベストスコアを `SharedPreferences` に保存

## 主な実装

- `MainActivity` から `RacingGameView` を直接表示
- `SurfaceView` + `Canvas` で疑似3Dの道路・敵車・ブーストリング・HUDを描画
- タップ操作、スコア管理、速度上昇、ゲームオーバーとリスタートを実装

## ファイル構成

- `app/src/main/java/com/codex/neonsprint/MainActivity.kt`
- `app/src/main/java/com/codex/neonsprint/RacingGameView.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

## ビルド例

Android SDK と JDK 17 または 21 が入った環境で以下を実行してください。

```bash
./gradlew assembleDebug
```
