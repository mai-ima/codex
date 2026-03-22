# Neon Sprint

`Neon Sprint` は Android 向けのスマホ用レースゲームです。複数の車種とコースを選び、ネオン街や海岸線、砂漠、山岳トンネルなどを走る縦持ちレースゲームとして構成しています。

## 追加した内容

- **ガレージUI:** 6種類の車を選択可能
- **コース選択:** 4種類のコースを追加し、色味・路面グリップ・空気抵抗・カーブ量・交通量を変化
- **オプション画面:** Graphics / LOD、Shader Cache、Sound FX、Master Volume を変更可能
- **LOD:** 画質設定に応じて星、道路セグメント、最大敵車数、最大ブースト数、建物数、ミニマップ表示数を変更
- **シェーダーキャッシュ:** 背景、地平線、ボタン、車体グラデーションなどの `LinearGradient` を再利用可能
- **音声:** `ToneGenerator` によるメニュー操作音、ブースト音、クラッシュ音を実装し、オプションから制御可能
- **安定化:** 衝突クールダウン、入力リセット、値のクランプ、オブジェクト数制限で不具合や予期せぬ挙動を抑制

## 実装メモ

- `MainActivity` から `RacingGameView` を直接表示
- `SurfaceView` + `Canvas` で疑似3Dコース、車両、HUD、ガレージ画面、オプション画面を描画
- 車両ダイナミクスは、加速・空気抵抗・横G・グリップ・ドリフト係数・コースごとの路面補正を考慮したカスタム実装
- この環境では商用物理エンジンや Android SDK の導入検証ができないため、依存無しで完結する形にしています

## 主なファイル

- `app/src/main/java/com/codex/neonsprint/CarSpec.kt`
- `app/src/main/java/com/codex/neonsprint/CourseSpec.kt`
- `app/src/main/java/com/codex/neonsprint/GameOptions.kt`
- `app/src/main/java/com/codex/neonsprint/AudioController.kt`
- `app/src/main/java/com/codex/neonsprint/ShaderCache.kt`
- `app/src/main/java/com/codex/neonsprint/MainActivity.kt`
- `app/src/main/java/com/codex/neonsprint/RacingGameView.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

## ビルド例

Android SDK と JDK 17 または 21 が入った環境で以下を実行してください。

```bash
./gradlew assembleDebug
```
