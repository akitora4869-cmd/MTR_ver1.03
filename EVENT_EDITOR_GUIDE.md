# MCTRPG Rebuild - Event Editor / EDITOR WAND (Fix10)

Fix10から、シナリオイベントをゲーム内GUIで作成する Event Editor と、マップ上の範囲を直接指定する EDITOR WAND を実装しました。

## 開き方

1. `/mctrpgtool` で KP TOOL を取得
2. KP TOOL → `EDITOR WANDを受け取る`
3. EDITOR WANDを右クリック → `EVENT EDITOR`

## Eventの基本構造

`Trigger -> Condition -> Action` で構成されます。

### Trigger
- `MANUAL`: Event Editorのテスト実行などから手動起動
- `AREA_ENTER`: 設定したCuboidエリアへ探索者が侵入した瞬間に起動

### Repeat
- `ONCE`: サーバー/シナリオ全体で一度
- `PLAYER_ONCE`: 探索者ごとに一度
- `REPEAT`: 条件を満たすたびに起動

### Condition (Fix10)
- `HAS_ITEM`: Event作成時に手に持っていたMaterialを所持している
- `HP_BELOW`: 現在HPが指定値以下（初期GUIでは5）

ConditionはEvent Engine側で判定されます。今後、手掛かり、Artifact、技能結果、フラグ、NPC状態などを追加できる構造です。

### Action (Fix10)
- MESSAGE
- TITLE
- SOUND
- WAIT (tick)
- TELEPORT_HERE
- SET_TIME
- WEATHER

ActionはGUI上の並び順に実行されます。WAITを挟むことで、音→待機→Title→待機→TPのようなタイムライン演出が作れます。
Actionを左クリックすると値をチャット入力で編集、右クリックすると削除します。

## エリア設定

Event設定画面で「エリア設定」を押した後、EDITOR WANDで
- 左クリック: 地点A
- 右クリック: 地点B

を指定します。2点を対角とした直方体がTriggerエリアになります。

## Editor View

EDITOR WANDメニューの `Editor View ON/OFF` を使うと、KP本人だけにEventエリア境界をParticle表示します。通常プレイヤーには見えません。

## データ

イベント定義は `plugins/MCTRPG-Rebuild/events.yml` に保存されます。
