# Combat ruins (0.55.0)

断戈营垒 (`duange_ruin`) is a three-stage route through a ruined garrison. Nearby survival
players activate each stage in order; skipping to the visible cache does not grant loot.
伏妖残殿 (`fuyao_ruin`) is a sunken sanctuary. Right-click its sealed cache to begin a
three-wave demon-wolf trial. No puzzle, consumable entry key, time limit or separate dimension.
Both are native Overworld jigsaw structures, found in newly generated chunks via `/locate`.

## Ownership and resources

`data/myvillage/ruins/{duange_ruin,fuyao_ruin}.json` owns enemy definitions, realms, spawn and
trigger positions, stage delays, radii, rewards, geometry anchors, palettes and worldgen.
`tools/gen_combat_ruins.py` owns both templates, structure/pool/set/biome tags, the cache models
and bilingual text. Native pool placement explicitly ignores inherited waterlogging, so
buried fences/stairs do not retain old pond water as sources inside the combat floor. Its groups are registered in `tools/buildgen/groups.py`; halls use semantic
Nodes and the registered Chinese roof handler. `generate_all_structures.py` regenerates them
after cleanup. All four native rotations are supported; mirrored admin placement is not an
advertised entry point. Do not edit generated NBT, models or language entries directly.

The `battle_cache` block has no item, creative entry or drop. It is immovable, explosion-resistant
and unbreakable in survival, including after completion. Its generated static coffer model has
wood, bronze straps and a red seal that lights cyan on completion. It uses the vanilla 27-slot
container menu; it does not animate the lid or expose hopper/item-handler access.

## Shared server state

`BattleProgress` is a pure state machine: `DORMANT / READY / ACTIVE / CLEARED / REWARDED`.
Only authored templates start READY. Missing or invalid state fails closed. Each current wave
has one saved UUID and defeat flag per guard slot. Players, distance, difficulty, loaded chunks,
collision and solid support are checked before spawning. Blocked positions stay pending and
never overwrite player construction. No absent/unloaded guard is counted as dead.

`BattleCacheBlockEntity` owns progress and the reward inventory in the same saved block entity.
Until all bound guardians die, no rewards exist in the cache. It validates every reward before
committing REWARDED and assigning the inventory once. Menus require the server-owned state;
repeat clicks, another player and normal restarts do not replenish looted slots. The loot is
shared per structure, not per player. Normal save/restart is supported, not crash-atomic writes
across the entity/chunk/SavedData files.

`BattleReceipts` stores active UUID receipts in Overworld SavedData. Deaths can be delivered
while the cache is unloaded or an enemy has moved dimensions. Peaceful removal marks a retry,
not a defeat: already defeated slots remain cleared; only removed surviving slots respawn when
non-Peaceful play resumes. No automatic duplicate spawn occurs merely because an entity is
unloaded. Conversion of registered guards (e.g. a zombie becoming drowned) is cancelled to
preserve its identity. Active guards cannot naturally despawn, pick up gear, or drop their
issued equipment. Helmets prevent sunlight from clearing the garrison for free. Existing
vanilla mob and demon-wolf moves remain unchanged. Elite denotes a configured higher cultivation stage,
not a new boss or new move set.

Guard health/realm is initialized only on spawn; normal reload does not heal or reroll it.
Loaded guards far from home can return without healing. Unknown/admin-deleted guards fail
closed; creative/admin block removal and external world editors are not supported recovery
paths. Players can retreat, die or log out and resume later. The server does not imprison them.

## Checks

- Local: `python3 -m unittest tools.tests.test_combat_ruins tools.tests.test_danxia_ruin`;
  `python3 tools/gen_combat_ruins.py --check`.
- Modal `--preset combat-ruins-build`: these generation/path tests, resource check,
  `ruins.*` Java tests and a jar using checked committed templates.
- Modal `--preset combat-ruins-runtime`: same bounded checks plus
  `tools/combat_ruins_evidence.py`. Natural locate/chunk readback, mapped clicks/attacks,
  authority, peaceful/restart and shared loot are recorded separately from fixtures.
- Evidence uses frozen mobs and enhanced player strength to isolate state transitions.
  Administrative remote-death and rotated-placement fixtures are labeled separately.

Owner appearance, combat balance/feel, audio/FPS and multiplayer experience remain
`not_verified` until the owner plays the delivered build. Runtime evidence is recorded under
[the 0.55.0 delivery record](../testing/2026-10-16-combat-ruins.md): 76 actual-game checks,
including two real clients and two normal restarts, passed. The preset definition alone is
not evidence of acceptance.
