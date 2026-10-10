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

## Fix11 additions
- AREA_EXIT trigger.
- HP_CHANGE / SAN_CHANGE / RUN_EVENT actions.
- Investigation Points can branch to Event IDs for success, failure, critical and fumble.


## Fix12 - Clue / Artifact integration
- Action `GRANT_CLUE`: clues.yml のIDを指定し、探索者へ手掛かりを直接取得させます。情報表示・設定済み報酬・SANチェックにも接続します。
- Action `GIVE_ARTIFACT`: artifacts.yml のIDを指定し、Artifactを付与します。
- Condition `HAS_CLUE`: Event経由で取得済みのClue IDを要求します。
- Condition `HAS_ARTIFACT`: 指定Artifactの所持を要求します。
- EVENT SETTINGSのCondition欄に最大3件を表示し、左クリックで値編集、右クリックで削除できます。

## Fix13 - World / Spawn / Trigger / Branch expansion

Implemented in this build in the requested order:

1. Door / lighting actions
   - `TOGGLE_DOOR`: toggles any Bukkit `Openable` block at a stored world,x,y,z.
   - `SET_LIGHT`: sets a `Lightable` block on/off; LIGHT blocks are supported with level 15/0.
   - Event Settings slots 41 captures the block currently looked at (within 8 blocks).

2. NPC / enemy spawn actions
   - `SPAWN_NPC`: spawns a named Villager at a stored location. Value format: `name|world,x,y,z`.
   - `SPAWN_ENEMY`: spawns an existing Mythos creature definition through MythosManager. Value format: `mythos_id|world,x,y,z`.

3. Trigger expansion
   - `BLOCK_INTERACT`: runs when the configured block is clicked. Trigger value: `world,x,y,z`.
   - `PLAYER_DEATH`: runs when a player dies.
   - `ENTITY_DEATH`: runs when an EntityType or Mythos ID dies. Trigger value: e.g. `ZOMBIE` or a Mythos ID.
   - Trigger target can be edited from Event Settings slot 9.

4. Event branch expansion
   - `CONDITION_BRANCH`: `CONDITION_TYPE|value|true_event|false_event`.
   - `RANDOM_BRANCH`: `chance_percent|success_event|failure_event`.
   - Branch events reuse the existing Event engine, so conditions/repeat/actions on the destination event still apply.

This remains backward-compatible with existing Fix10-Fix12 `events.yml`; the new `trigger-value` field defaults to empty.
