# Neon Sprint

`Neon Sprint` は Android 向けのスマホ用レースゲームです。複数の車種からマシンを選び、ネオン街のハイウェイを走り抜ける縦持ちレースゲームとして作り直しました。

## 追加した内容

- **ガレージUI:** 6種類の車から選択可能
- **車種ごとの挙動差:** 最高速、加速、ブレーキ、グリップ、ハンドリング、ドリフト特性、重量を個別設定
- **レースHUD:** スピード表示、RPM、ニトロ、グリップ、簡易ミニマップを追加
- **操作UI:** 左手でステア、右手でアクセル/ブレーキを操作できるタッチUI
- **保存:** ベストスコアと選択車種を `SharedPreferences` に保存

## 実装メモ

- `MainActivity` から `RacingGameView` を直接表示
- `SurfaceView` + `Canvas` で疑似3Dコース、車両、HUD、ガレージ画面を描画
- 車両ダイナミクスは、加速・空気抵抗・横G・グリップ・ドリフト係数を考慮したカスタム実装
- この環境では商用物理エンジンや Android SDK の導入検証ができないため、依存無しで完結する形にしています

## 主なファイル

- `app/src/main/java/com/codex/neonsprint/CarSpec.kt`
- `app/src/main/java/com/codex/neonsprint/MainActivity.kt`
- `app/src/main/java/com/codex/neonsprint/RacingGameView.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

## ビルド例

Android SDK と JDK 17 または 21 が入った環境で以下を実行してください。

```bash
./gradlew assembleDebug
```
