# Investigation Point Editor (Fix11)

EDITOR WAND -> 調査ポイント を選び、対象ブロックをWANDでクリックすると作成/編集します。
設定項目は名前、技能ID、PUBLIC/SECRET/HIDDEN、探索者ごと1回、成功/失敗/クリティカル/ファンブル時Event IDです。
通常プレイヤーは設定ブロックを右クリックして調査します。Editor Viewでは調査ポイントがKPにだけパーティクル表示されます。
結果Actionを直接抱え込まずEvent IDへ接続するため、Event Editor側でメッセージ、演出、TP、HP/SAN変動、別Event連鎖などを自由に組み立てられます。
