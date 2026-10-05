# Artifact Editor (Rebuild)

`/artifact editor` でシナリオ作成者向けGUIを開きます。

現在GUIから設定できる項目:
- 表示名
- ベースアイテム（手に持っているアイテムを採用）
- 説明文（`|` で複数行）
- クールダウン
- ダメージ効果（固定値 / `1d6+2` 等）
- TP効果（クリックした時点の現在地を保存）
- 守護効果（指定回数の被ダメージを無効化）
- 複数効果の同時登録

保存された定義は `plugins/TRPGCharacter/artifacts.yml` に永続化され、`/artifact give <player> <id>` で配布できます。
