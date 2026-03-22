# Neon Sprint

`Neon Sprint` は Android 向けのスマホ用レースゲームです。複数の車種とコースを選び、ネオン街や海岸線、砂漠、山岳トンネルなどを走る縦持ちレースゲームとして構成しています。

## 追加した内容

- **ガレージUI:** 6種類の車を選択可能
- **コース選択:** 4種類のコースを追加し、色味・路面グリップ・空気抵抗・カーブ量・交通量を変化
- **車種ごとの挙動差:** 最高速、加速、ブレーキ、グリップ、ハンドリング、ドリフト、重量を個別設定
- **レースHUD:** スピード表示、RPM、ニトロ、グリップ、コースカーブ量、簡易ミニマップを追加
- **安定化:** 衝突クールダウン、入力リセット、値のクランプ、オブジェクト数制限で不具合や予期せぬ挙動を抑制
- **最適化:** 描画ループ中の `Paint` 再生成やランダム/配列生成を減らし、描画時のGC負荷を軽減

## 実装メモ

- `MainActivity` から `RacingGameView` を直接表示
- `SurfaceView` + `Canvas` で疑似3Dコース、車両、HUD、ガレージ画面を描画
- 車両ダイナミクスは、加速・空気抵抗・横G・グリップ・ドリフト係数・コースごとの路面補正を考慮したカスタム実装
- この環境では商用物理エンジンや Android SDK の導入検証ができないため、依存無しで完結する形にしています

## 主なファイル

- `app/src/main/java/com/codex/neonsprint/CarSpec.kt`
- `app/src/main/java/com/codex/neonsprint/CourseSpec.kt`
- `app/src/main/java/com/codex/neonsprint/MainActivity.kt`
- `app/src/main/java/com/codex/neonsprint/RacingGameView.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

## ビルド例

Android SDK と JDK 17 または 21 が入った環境で以下を実行してください。

```bash
./gradlew assembleDebug
```
