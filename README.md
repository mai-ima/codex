# Modern SMB-like Platformer Engine (Educational, Extensible)

このリポジトリは、**初代スーパーマリオ的な挙動を参考にした**2Dプラットフォーマーエンジンの土台実装です。

> ⚠️ 注意
> - 任天堂の著作権物（アセット、コード、完全同一仕様）の再配布は行っていません。
> - 本実装は教育目的の「近似再現可能なルールセット」を提供します。

## ここまでの実装（Phase 1+2）

- 固定timestepのゲームループとエンティティ更新
- SMB風のキャラ制御（歩行/走行/空中制御/可変ジャンプ）
- ASCIIマップからワールドを生成するデータ駆動パイプライン
- 入力の録画/再生（Replay）による再現性検証
- Node testによる回帰テスト

## クイックスタート

```bash
npm install
npm test
npm run demo
```

## 開発を容易にする設計ポイント

- **RuleSet差し替え**: `createSMBLikeRuleSet(overrides)` で挙動調整
- **Controller分離**: 入力解釈と物理積分を分離
- **Replay**: 調整前後の挙動比較が容易
- **ASCII level**: プロトタイピング速度が高い
- **Snapshot API**: テスト・デバッグ・将来のネット同期に利用可能

## 次の計画（Phase 3）

1. Render層分離（Canvas/WebGL）
2. 敵AIの行動ツリー/状態機械化
3. 当たり判定をタイル+AABBから拡張（斜面/一方通行床）
4. レベルエディタとホットリロード
5. 入力遅延/補間を加味したネットプレイ検証
