# Deep One AI Guide (Fix14)

`deep_one` now has a dedicated combat controller.

- Idle: retains vanilla wandering/pathfinding.
- Detection: nearest valid investigator within 24 blocks with line of sight.
- Ranged combat: throws a trident up to 18 blocks.
- Spacing: when the target is closer than 6 blocks, moves away from the target.
- Throw cooldown: 2.5 seconds.
- Death: creates a persistent `深きものの死骸` through `CorpseManager`; normal drops and XP are disabled.

The corpse stores `mythos-id: deep_one`, uses Deep One CustomModelData 93210 for its body display, and has dedicated inspection text.

## Fix15 - Deep One Stone Spear
- Added the unique item `深きものの石槍`.
- Base material is TRIDENT, so a player can use normal melee attacks and vanilla-style charged throws.
- The item is identified by PDC `deep_one_spear`; its identity is not based on display name.
- Deep One ranged AI now creates its projectile through `DeepOneSpearManager`, tagging it as the same special spear projectile.
- Deep One projectiles remain non-pickup to prevent infinite spear farming.
- KP/admin test command: `/mythos spear [player]`.

## Fix17 target expansion
- Deep Ones now scan all nearby living entities, not only players.
- Valid targets: investigators, Villagers, Wandering Traders, Iron Golems, and Animals.
- Other mythos creatures are treated as hostile factions by default.
- Other `deep_one` entities are allies and are excluded from targeting.
- Deep One stone-spear friendly fire against another `deep_one` is cancelled.
- Retreat/ranged-combat behavior is shared for every supported target type.
