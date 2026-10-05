# MCTRPG Rebuild command inventory

Paper 26.2 migration baseline. This file lists commands currently registered by `plugin.yml`.

## Player / character
- `/status [give|random-confirm|random|revive <player>|reload]` — investigator sheet / character administration.
- `/roll <XdY>` — generic dice roll.
- `/trpgattack <fist|kick>` — select unarmed attack method.
- `/trpgskill <hobby|hobby-reset|occupation|occupation-reset|shortcut|shortcut-reset> ...` — skill allocation.
- `/trpgoccupation <input|toggle> ...` — occupation setup.

## Internal sheet commands
- `/trpgedit <stat|skill> <id>`
- `/trpgroll <stat|skill|derived> <id>`
- `/trpgcombo <combo>`

These are primarily invoked from the character-sheet UI rather than typed directly.

## KP / session
- `/kp <player>` — grant/revoke KP role.
- `/kpbook` — open KP book.
- `/mctrpgtool` — receive the MCTRPG KP TOOL.
- `/create session <session name> <time period>` — create a session.
- `/session join` — join current session.
- `/session leave` — leave current session.
- `/session list` — KP participant list.
- `/session time <early_morning|morning|noon|evening|night|late_night>` — change scenario time period (Japanese aliases are also accepted).
- `/session time start|pause|resume` — scenario clock control.
- `/session time speed <1-600>` — scenario-clock speed.
- `/session time add <minutes>` — manually move scenario time.
- `/session end` — end session and save log.
- `/reset pc <player>` — reset one investigator.
- `/reset players` — reset all investigator data (confirmation required).

## Scenario / content management
- `/clue <mark|hide|show|protect|unprotect|setup|sanreset> ...` — clue points and clue-area management.
- `/mythos <list|summon|sanreset|modelstatus> ...` — mythos creature management.
- `/artifact <list|give|remove> ...` — artifact management.
- `/npc <create|delete|move|say|addline|clearlines|list|reload>` — NPC management.
- `/lock <lock|hardlock|keygive|hammergive|wallmark|wallclear|wallinfo|keyinfo|unlock|clear> ...` — locks, keys and black-wall gimmicks.
- `/cultist <setupaltar|spawn|alert|alarmtest|calm|clear|status|reload> ...` — cultist/altar encounter management.

## Special note: `/stop time`
The project contains a listener for `/stop time`, used to toggle the MCTRPG time-stop feature without replacing the vanilla `/stop` command registration. It is not declared as an MCTRPG command in `plugin.yml`.

## Rebuild direction
Commands remain available as a fallback/API surface, but frequently used KP operations will progressively move into the KP TOOL, and scenario construction operations into the EDITOR WAND.
