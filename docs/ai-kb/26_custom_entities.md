# Custom Entities

The first implemented MyVillage custom entity is `yuanxian:simple_fox`. Its
purpose is to exercise the complete low-cost entity path rather than introduce
a new combat or animation system.

## Contract And Runtime Route

The source contract is `genops/contracts/entities/simple_fox.yaml`. The entity
subclasses the mapped Minecraft 1.21.1 `Fox`, so vanilla navigation, goals,
poses, synchronized fox flags, NBT, held-item behavior, and sounds remain
authoritative. The only behavior adaptation is offspring construction: vanilla
`Fox` hardcodes `minecraft:fox`, so `SimpleFoxEntity` creates
`yuanxian:simple_fox` and keeps the inherited parent-variant choice.

`ModEntities` registers the type at vanilla fox dimensions (`0.6 x 0.7`), and
the mod-bus entity events register `Fox.createAttributes()` plus the same
`NO_RESTRICTIONS`/motion-blocking placement tuple and fox ground predicate used
by vanilla. Natural spawning is incomplete unless both that Java registration
and these data resources exist:

```text
data/yuanxian/tags/worldgen/biome/has_simple_fox.json
data/yuanxian/neoforge/biome_modifier/add_simple_fox_spawns.json
```

The initial modifier is intentionally conservative: taiga-family biomes,
weight 8, groups of 1 to 2.

## Client Boundary And Resources

The client renderer subclasses `FoxRenderer`, retaining `FoxModel`, held-item
rendering, and pounce rotation while returning one MyVillage texture. Common
entity and mod-entry classes do not import `net.minecraft.client` types.

The visible/resource set is:

```text
assets/yuanxian/textures/entity/simple_fox/simple_fox.png
assets/yuanxian/models/item/simple_fox_spawn_egg.json
assets/yuanxian/lang/en_us.json
assets/yuanxian/lang/zh_cn.json
data/yuanxian/loot_table/entities/simple_fox.json
```

The spawn egg is in `yuanxian:main`. Sounds are legal vanilla fox events; no
empty OGG or redundant `sounds.json` entry is shipped. The first loot table is
intentionally empty.

## UV And Skill Compatibility

`FoxModel.createBodyLayer()` bakes a `48 x 32` atlas. The exact cuboid UV
coverage, filled mask, deterministic texture builder, provenance, and enlarged
review image live under `art/entities/simple_fox/`. The runtime PNG must remain
48x32 unless the model layer is also replaced.

The custom-entity skill now expresses rectangular dimensions directly:
`final_texture_size` is `48 x 32` and `working_texture_size` is `1152 x 768`, a
uniform 24x scale. Concept and atlas candidates use Codex's built-in imagegen,
never an API-key script. Every atlas result is nearest-normalized and written
back through a local binary composite mask. The repo validator still checks
48x32, exact mask coverage, binary alpha, cleared unused texels, provenance,
and the palette bound; a schema-only contract pass does not prove visual fit.

The built-in concept sheet was adopted as the color/proportion reference. Two
atlas passes both passed mechanical validation, including a second pass with a
48-face cuboid overlay, but both treated the head island too much like one planar
portrait and misplaced directional face details. They were rejected, and the
deterministic native-resolution atlas remains the runtime texture. Candidate
hashes and verdict reasons are recorded without tracking rejected raw images.
The face-local `texture_patches.json` and texture validator additionally pin the
two eyes to `head.north` and the nose to `muzzle.north`, closing the gap between
generic mask coverage and directional low-resolution details.

## Validation And Manual Handoff

```bash
python3 tools/validate_custom_entities.py
python3 -m unittest tools.tests.test_validate_custom_entities
./gradlew test
./gradlew build
./gradlew runAcceptanceServer
```

Game-side smoke commands:

```mcfunction
/summon yuanxian:simple_fox ~ ~ ~
/give @s yuanxian:simple_fox_spawn_egg
/data get entity @e[type=yuanxian:simple_fox,limit=1,sort=nearest]
```

Dedicated-server startup proves side safety and data codec loading, not entity
appearance or natural frequency. The owner completed the summon, spawn-egg,
save/reload, seed/biome frequency, multiplayer, and multi-view client checks and
accepted the simple fox on 2026-07-12.

## See Also

- Runtime spec: [`custom-entity-runtime`](../contracts/combat-entities.md#custom-entity-runtime)
- Hostile beasts with their own model and moves (`yuanxian:demon_wolf`): [38_hostile_beasts.md](38_hostile_beasts.md), [`hostile-beast-runtime`](../contracts/combat-entities.md#hostile-beast-runtime)
- Humanoid NPCs with a generated layered model (`yuanxian:cultivator`): [39_humanoid_npcs.md](39_humanoid_npcs.md), [`humanoid-npc-runtime`](../contracts/combat-entities.md#humanoid-npc-runtime)
- Validation checklist: [09_validation_checklist.md](09_validation_checklist.md)
- Knowledge-base index: [INDEX.md](INDEX.md)
