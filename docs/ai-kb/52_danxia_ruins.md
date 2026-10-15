# Danxia ruins (0.54.0)

丹霞旧观是主世界原生 jigsaw 单模板结构，不是独立维度或自动刷新的副本。
`/locate structure myvillage:danxia_ruin` 可定位新生成区块中的遗迹；旧区块不会回填。
地表有残门、荒芜药园、侧院和破损主殿；铜渠引向地下丹室，后殿塌洞可攀藤进入。
墙体、地形和残阵均可挖掘；挖掘不会凭空获得尚未复原的内库奖励。

## Sources and generation

- `data/myvillage/ruins/danxia.json` owns geometry anchors, plant targets, reward ids/counts,
  confirmation range/time, guardian realms, biomes and structure spacing.
- `tools/gen_danxia_ruin.py` owns the template, pool, structure/set, biome tag, outer loot,
  core block models/blockstate and its bilingual messages. It uses the `danxia_ruin`
  group in `tools/buildgen/groups.py`, semantic hall Nodes and the registered Chinese roof handler.
- `generate_all_structures.py` calls this generator after its normal cleanup so the template
  remains in the jar. Scoped chest/sign/core NBT is assembled here; the general JSON DSL has
  not gained arbitrary block-entity support. The canonical NBT writer keeps gzip headers stable.
- All natural placement and saved pieces use vanilla jigsaw chunk clipping/rotation.
  The core's north-facing authored offsets rotate with its block state. Mirror placement is
  not an advertised entry point; use ordinary placement or one of the four rotations.

## Server-owned shared choice

`RuinCoreBlock` is a structure-only block: no item, creative entry, recipe or drop, and no
piston movement. A hand-placed core without the template's saved `Choice=CHARGED` is exhausted.
Empty-hand right-click previews the garden; sneak-right-click previews the archive.
Repeat the same action within the configured window to commit. Pending confirmation is
bound to that player, action and server time, and is not persisted across restart.

`RuinCoreBlockEntity` checks same dimension, survival/creative build rights, interaction range,
loaded destinations, original coarse-dirt plant beds or an empty archive barrel, and valid
registered crop/item/manual definitions. It refuses changed targets without spending charge.
No destination loads chunks or overwrites unrelated player blocks. A spent choice is shared
by all players, not a personal chest reset. The server thread commits before reward writes.

- **Garden:** restores mature existing herbs and spirit soil at configured garden sites;
  harvesting supplies planting stock for the established farming/alchemy loop.
- **Archive:** restores a component-bearing `qingmu_changchun_gong` manual, the existing
  Ningshen formula and spirit stones in the inner barrel. The manual uses normal study rules;
  this is an alternate acquisition source, not a newly exclusive technique or new pill effect.
- Broken/moved archive barrel or altered garden beds can be restored to their original
  empty-barrel/coarse-dirt state before choosing. Removing the core destroys the opportunity.
- `RuinChoice` persists `CHARGED/GARDEN/VAULT/EXHAUSTED`; missing/invalid saves fail closed.
  Ordinary save/restart is supported; cross-chunk writes are not a custom crash-atomic transaction.

Embedded demon wolves receive configuration-owned realms once on first entity join through
`RuinGuardians`; pending birth is cleared and subsequent reloads preserve health and realm.
These are persistent structure encounters, not rerolled regional natural births or ledger people.
Peaceful difficulty follows normal hostile-mob removal. Existing beast AI/moves are unchanged.

## Focused checks and evidence

Use Modal `--preset danxia-build` for generation closure/path tests, affected capture/runner
tests, `ruins.*` Java tests and a jar build. `--preset danxia-runtime` checks generated inputs,
packages the committed resources, then runs `tools/danxia_ruin_evidence.py` in a natural world.
The latter uses `/locate`, bounded loaded-area saved-chunk readback, real client mouse input,
an explicitly separate rotated template fixture for the second branch, and a normal restart.
It retains commands, screenshots and failures; RCON setup/readback is not player input evidence.

`SessionConfig.natural_terrain=True` explicitly selects normal terrain/structure generation;
existing capture sessions remain flat with structures off by default. No local game fallback.
Owner appearance, exploration/combat balance, audio/FPS and multiplayer remain `not_verified`.

0.54.0 [cloud evidence and delivery](../testing/2026-10-16-danxia-ruins.md): 42 runtime
checks including two actual clients and restart passed; owner acceptance remains separate.
