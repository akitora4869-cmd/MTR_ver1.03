# MCTRPG Rebuild

Minecraft上でTRPGシナリオを**制作・運営・プレイするためのゲームエンジン型Paperプラグイン**です。

旧MCTRPG / TRPGCharacterをベースに、特定シナリオ専用の仕組みから脱却し、KP・シナリオ作成者がゲーム内ツールやGUIを使ってシナリオを構築できる方向へリビルドしています。

> **現在の開発版:** 2.0.0-SNAPSHOT  
> **対象:** Paper 26.2 / Java 25  
> **JAR名:** `MCTRPG-Rebuild-Paper26.2-2.0.0-SNAPSHOT.jar`  
> **Resource Pack:** 基本機能には不要  
> **ModelEngine:** 任意（softdepend）

---

## Rebuildの方針

MCTRPG Rebuildでは、プラグイン内に1本のシナリオをハードコードするのではなく、**MCTRPGを使って複数のTRPGシナリオを作れること**を目標にしています。

- 探索者シート・技能・SAN・HP・MPなどのTRPG基盤を維持
- KPがセッション中に使う機能を **KP TOOL** に集約
- シナリオ制作機能を **EDITOR WAND / 各種Editor GUI** に集約
- コマンドは削除せず、GUIの代替・管理・自動実行用として基本的に維持
- 固定シナリオ依存の機能を、設定可能な汎用システムへ置換
- 旧Dreamlands専用TP・ワールド生成・専用イベントはRebuildから撤去
- Resource Packなしでもプレイ可能。将来的な3Dダイス・専用アイコン・BGM等は追加演出として扱う

---

## 現在の主要機能

### 探索者 / CoC系基盤

- STR / CON / POW / DEX / APP / SIZ / INT / EDU
- HP / MP / SANと派生値
- CoC第6版を基準とした技能
- プレイヤーごとのキャラクターデータ保存
- 探索者シート本
- 能力値・技能の1d100判定
- 通常ダイスロール（`1d100`, `2d6` 等）
- SANチェック / SAN減少
- Minecraft上のHPとの同期
- 技能成功時のMinecraft内効果
- 職業技能 / 趣味技能等の割り振り

### Rebuild技能システム

技能は連打するものではなく、ゲーム内で「行動」として扱います。

- **基本技能クールタイム: 10秒**
- 探索者だけでなく、NPC・敵側の技能判定にも同じ考え方を適用可能な基盤
- 将来的に技能・判定ポイント単位でCT変更や例外設定ができる構造を想定

### ダイスロール演出

Resource Packを必須にせず、Paper側の表示・パーティクル・サウンド等を利用して、単なるチャット出力よりも「ダイスを振っている」感を出す方向で実装しています。

ロールには公開範囲を持たせます。

- **PUBLIC** — 通常の公開ロール
- **SECRET** — 探索者側には `🔒 SECRET ROLL` と `〈？？？〉` のみ表示。技能名・技能値・出目・成否は秘匿
- **HIDDEN** — 探索者には演出も結果も出さず、KP側だけで処理する完全秘匿判定

SECRETでは技能固有の演出を出さず、演出から〈目星〉や〈聞き耳〉などを推測できないようにする方針です。

---

## KP TOOL

`/mctrpgtool` でKP用ツールを取得できます。

現在はRebuild用GUI基盤として、セッション中に頻繁に行う操作をコマンド入力からGUIへ移していく役割を持っています。

現在の方向性:

- 時刻変更
- 天候変更
- セッション進行操作
- 探索者管理
- ダイス / SECRET判定
- 演出操作
- シナリオ編集機能への入口

コマンド自体は原則残すため、GUIを使わない運用や管理用途にも対応します。

---

## Artifact Editor

RebuildではArtifactを「固定性能の特殊アイテム」ではなく、**シナリオ作成者がゲーム内で作成できるカスタムアイテム**へ拡張しています。

`/artifact editor` でArtifact Editorを開きます。

### 現在GUIから設定できる内容

- 内部ID
- 表示上の名前
- ベースアイテム
- 説明文 / Lore
- クールダウン
- 複数効果の登録

### 現在実装済みの効果

**ダメージ**  
固定値のほか、`1d6+2` のようなダイス式を使用できます。

**テレポート**  
Artifact作成時の現在地点を転送先として保存できます。

**守護**  
指定回数、受けるダメージを無効化するArtifactを作成できます。

1つのArtifactに複数効果を組み合わせることもできます。

例:

```text
名称: 古びた護符
説明: 何者かの祈りが込められた護符。

効果:
  - 守護 × 1回
  - TP → 登録した祭壇

Cooldown: 30秒
```

Artifact定義は `artifacts.yml` に保存され、アイテム側はArtifact IDを使って定義を参照します。

```text
ArtifactDefinition
 ├─ id
 ├─ displayName
 ├─ material
 ├─ lore
 ├─ effects
 └─ cooldown
```

これにより、シナリオごとに新しいArtifactを作るたびJavaコードを書き換える必要を減らします。

### Artifactコマンド

```text
/artifact editor
/artifact list
/artifact give <player> <id>
/artifact remove ...
```

従来Artifactとの互換処理も残しています。

---

## シナリオ制作システム

MCTRPG Rebuildでは、最終的にシナリオ作成者がゲーム内で以下を設定できることを目標としています。

```text
EDITOR WAND
 ├─ 調査ポイント
 ├─ 技能判定ポイント
 ├─ SANチェック
 ├─ 手掛かり
 ├─ 鍵 / 扉
 ├─ NPC
 ├─ 敵
 ├─ Artifact
 ├─ エリア
 ├─ TPポイント
 ├─ 音 / パーティクル / BGM
 └─ イベント / トリガー
```

例えば、将来的には次のようなイベント連鎖をゲーム内設定だけで作れる構成を目指します。

```text
魔法陣を調査
   ↓
SECRET技能判定
   ↓
SANチェック
   ↓
手掛かり取得
   ↓
地下扉を解錠
   ↓
照明OFF
   ↓
敵出現
```

---

## 既存のシナリオ機能

旧MCTRPGから以下のシステムを引き継ぎ、Rebuild向けに整理しています。

- 手掛かり / Clue
- SANチェック履歴
- 鍵 / 扉 / HARD LOCK
- 黒壁ギミック
- NPC
- 神話生物
- ModelEngine連携（任意）
- 狂信者 / 祭壇 / 警報 / 増援
- 隠れる / 忍び歩き / 索敵連動
- セッション時間管理
- セッションログ / 探索者管理

これらのうち固定シナリオ色が強いものは、今後Editorから設定可能な汎用イベントへ順次置き換えます。

---

## コマンド

### 探索者 / プレイヤー

```text
/status
/status give
/status random
/roll <XdY>
/trpgattack <fist|kick>
/trpgskill ...
/trpgoccupation ...
```

`/trpgedit`, `/trpgroll`, `/trpgcombo` は主に探索者シートUIから呼び出す内部用コマンドです。

### KP / セッション

```text
/kp <player>
/kpbook
/mctrpgtool
/create session <セッション名> <時間帯>
/session join
/session leave
/session list
/session time <時間帯>
/session time start
/session time pause
/session time resume
/session time speed <1-600>
/session time add <minutes>
/session end
/reset pc <player>
/reset players
```

時間帯には `early_morning / morning / noon / evening / night / late_night` を使用できます。日本語エイリアスにも対応しています。

### シナリオ管理

```text
/clue <mark|hide|show|protect|unprotect|setup|sanreset> ...
/mythos <list|summon|sanreset|modelstatus> ...
/artifact <editor|list|give|remove> ...
/npc <create|delete|move|say|addline|clearlines|list|reload>
/lock <lock|hardlock|keygive|hammergive|wallmark|wallclear|wallinfo|keyinfo|unlock|clear> ...
/cultist <setupaltar|spawn|alert|alarmtest|calm|clear|status|reload> ...
```

### `/stop time` について

`/stop time` は `plugin.yml` に独立したMCTRPGコマンドとして登録せず、既存コマンドとの競合を避けるため監視処理でMCTRPGの時間停止を切り替える特殊仕様です。

詳細な現行コマンド一覧は `COMMANDS_REBUILD.md` を参照してください。

---

## Resource Packについて

**MCTRPG Rebuildの基本機能にResource Packは必要ありません。**

以下のような演出は将来的なオプション拡張として扱います。

- 3Dダイス
- KP TOOL / EDITOR WAND専用アイコン
- Artifact専用モデル
- 武器モデル
- 独自BGM / SE
- ホラー向け画面・環境演出

RPがなくても技能・ダイス・SAN・Artifact・シナリオ進行等が成立する設計を維持します。

---

## ModelEngine

ModelEngineは**任意依存**です。

導入されている場合は神話生物やNPCの高度なモデル表現に利用できます。導入されていない環境でもMCTRPG本体が起動できる構成を維持します。

---

## 動作環境

```text
Minecraft / Server : Paper 26.2系
Java               : 25
MCTRPG              : 2.0.0-SNAPSHOT
```

旧1.20.1 / Java 17版とは別系統として扱ってください。

JAR名にも `Rebuild` を付け、旧版と区別します。

```text
MCTRPG-Rebuild-Paper26.2-2.0.0-SNAPSHOT.jar
```

---

## ビルド

### GitHub Actions

リポジトリのルートが次のようになるようアップロードします。

```text
MCTRPG-Rebuild/
├─ .github/
│  └─ workflows/
│     └─ build.yml
├─ pom.xml
├─ build.gradle.kts
├─ settings.gradle.kts
├─ README.md
└─ src/
```

GitHubの **Actions → Build MCTRPG Rebuild → Run workflow** から実行できます。

成功するとArtifactsに以下が生成されます。

```text
MCTRPG-Rebuild-Paper-26.2
└─ MCTRPG-Rebuild-Paper26.2-2.0.0-SNAPSHOT.jar
```

### Maven

Java 25環境でプロジェクトルートから実行します。

```bash
mvn clean package
```

生成先:

```text
target/MCTRPG-Rebuild-Paper26.2-2.0.0-SNAPSHOT.jar
```

---

## サーバーへの導入

1. Paper 26.2系サーバーとJava 25を用意します。
2. サーバーを停止します。
3. `MCTRPG-Rebuild-Paper26.2-2.0.0-SNAPSHOT.jar` を `plugins/` に配置します。
4. サーバーを起動します。
5. `/status` と `/mctrpgtool` で基本動作を確認します。
6. Artifact Editorを確認する場合は `/artifact editor` を使用します。

旧MCTRPGのJARを同時に入れるとコマンド・プラグイン機能が競合する可能性があるため、Rebuildの検証時は旧版を外してください。

---

## 権限

```text
trpg.admin   # 管理機能。標準ではOP
trpg.keeper  # KP機能
```

KP権限の操作には `/kp <player>` を使用できます。

---

## 主な設定・保存データ

機能に応じて、プラグインのデータフォルダへ設定・プレイヤーデータ・Artifact定義等を保存します。

代表例:

```text
plugins/TRPGCharacter/
├─ config.yml
├─ skills.yml
├─ players.yml
└─ artifacts.yml
```

※ Rebuild移行中のため、データフォルダ名や旧版データとの互換性は今後整理する可能性があります。旧データを使用する場合はバックアップを推奨します。

---

## 開発ステータス

MCTRPG Rebuildは現在も開発中です。

### 実装済み / 移行済み

- Paper 26.2 / Java 25向けプロジェクト基盤
- Rebuild専用JAR名
- UTF-8統一
- Dreamlands専用システム撤去
- KP TOOL基盤
- 技能10秒CT基盤
- ダイス演出 / SECRETロール基盤
- Artifact Editor
- Artifact複数効果
- Artifactダメージ / TP / 守護
- 旧MCTRPG主要コマンドの維持

### 今後の主な拡張予定

- EDITOR WAND本格実装
- 調査ポイントのGUI設置
- SANチェックポイントのGUI設置
- 鍵 / 扉Editor
- NPC / 敵Editor
- イベント / トリガーEditor
- Artifact効果追加（回復、SAN、MP、バフ、範囲効果、技能補正、Sound、Particle等）
- Artifactの使用回数 / 消滅 / 装備・所持パッシブ
- KP TOOLの各種コマンドGUI化
- 固定シナリオ機能の汎用化
- オプションResource Packによる演出強化

---

## 付属ドキュメント

より詳しい仕様は以下を参照してください。

- `COMMANDS_REBUILD.md` — 現行コマンド一覧
- `ARTIFACT_EDITOR_GUIDE.md` — Artifact Editor
- `MIGRATION_26_2.md` — Paper 26.2移行
- `REBUILD_PHASE1.md` — Rebuild初期変更
- `KP_BEGINNER_GUIDE.md` — KP向けガイド
- `CLUE_AUTO_REWARD_GUIDE.md` — 手掛かり
- `STEALTH_DETECTION_GUIDE.md` — 隠れる / 忍び歩き / 索敵
- `ALARM_DOOR_REINFORCEMENT_GUIDE.md` — 警報 / 扉 / 増援
- `CULTIST_ALTAR_GUIDE.md` — 狂信者 / 祭壇
- `BLACK_WALL_GIMMICK_GUIDE.md` — 黒壁ギミック
- `DETECTION_ALARM_LIGHTING_GUIDE.md` — 発見時警報 / 照明演出

---

## MCTRPG Rebuildが目指す形

**「MinecraftでTRPGを遊ぶプラグイン」から、「MinecraftでTRPGゲームそのものを作れるツール」へ。**

KP・シナリオ作成者は可能な限りJavaやYAMLを直接編集せず、ゲーム内GUI・設置ツール・Editorを使ってシナリオを構築できるようにしていきます。一方で、コマンドや設定ファイルは高度な運用・自動化・トラブル時の代替手段として維持します。

## 探索者シートGUI / 技能ダイス

Rebuildでは探索者シートを「本だけ」に依存させません。

- **探索者シート本を右クリック**: 新しい探索者GUIを開く
- **Shift + 本を右クリック**: 従来のクリック可能な本シートを開く
- **技能ダイスを右クリック**: 最大9枠のクイックスキルを開く
- **Shift + 技能ダイスを右クリック**: 全技能カテゴリを開く
- 技能GUIでは **左クリックで即判定 / 右クリックでクイックスキル登録・解除**
- `/status give` は探索者シート本と技能ダイスを配布
- `/status skilltool` は技能ダイスのみ再発行

技能判定は既存のDice/Roll Engineを使用するため、10秒共通クールタイム、ダイス演出、成長判定など既存仕様と共通です。探索者データ本体は `players.yml` 側に保持されるため、本や技能ダイスを紛失してもキャラクターデータは失われません。


## 探索者作成ウィザード
`/status wizard` または `/status create` で、GUIだけで探索者作成を進められます。探索者シートGUIの「探索者作成ウィザード」からも開始できます。

1. CoC第6版標準式で能力値を生成
2. occupations.yml の職業から選択
3. 必要なら職業の選択技能を決定
4. 職業技能ポイントを +5 / +10 単位で割り振り
5. 趣味技能ポイント（INT×10）を割り振り
6. 最終確認して完成

完成時に探索者シート本と技能ダイスを配布します。割り振り画面の星を Shift+クリックすると、その区分の割り振りをリセットできます。
