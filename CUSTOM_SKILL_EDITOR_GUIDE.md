# Custom Skill Editor

- `/customskill create` : 作成GUI
- `/customskill kp` : KP用一覧・使用制限
- `/customskill inspect <player>` : 探索者別確認

## 作成
基本項目は技能名、技能値(1-99)、カテゴリ、説明。これだけで通常の1d100技能として利用可能。
高度設定では成功効果を複数追加できる。DAMAGE / HEAL / SAN / TELEPORT / POTION / MESSAGE / SOUND / PARTICLE を実装。対象は自分または視線先。

## KP制御
オリジナル技能は作成者の探索者に紐づく。KPは内容を確認でき、技能単位で現在のシナリオでの使用を禁止/再許可できる。禁止は判定直前にも検査されるため、クイックスキル等からの迂回使用も不可。

## KPによるシナリオ技能作成
- KP TOOL → 「オリジナル技能管理」 → ネザースター「KP: シナリオ技能を新規作成」
- または `/customskill kpcreate`
- プレイヤー版と同じ直感的なEditorで、判定値・説明・成功効果を設定可能。
- KP作成技能は `SCENARIO` として保存され、作成者自身には自動習得されません。
- `/customskill give <player> <id> [value]` で任意の探索者へ付与できます。
- `/customskill kp` から使用許可/禁止をいつでも切替可能。
- シナリオ技能はデフォルトで session-only。`/session end` 時に所有者割当を解除します。
