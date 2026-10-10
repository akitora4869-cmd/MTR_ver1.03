# Deep One Stone Spear - Fix16

- Item: 深きものの石槍
- Melee: 〈槍〉 (base 20), damage 1d6 + DB
- Throw: 〈投擲〉 (base 25), damage 1d8; critical=max 8, special=+1
- Durability: custom maximum 96 when the running Paper ItemMeta exposes per-item max damage; otherwise vanilla trident durability is the compatibility fallback.
- Deep One AI projectile: fixed 5 damage, cannot be picked up.
- Deep One corpse: first right-click inspection recovers one stone spear. The corpse persists a recovered flag, so it cannot be farmed repeatedly, including after restart.
