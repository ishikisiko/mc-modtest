## Context

The Qingfeng sword combat (0.26.0 to 0.27.1-fix1) is described in `docs/ai-kb/32_pal_combat_integration.md`. It works, and the owner accepted its look for now ("暂时就这样"). Its facts are spread across Java constants, a Python pose table, a rig JSON, index-aligned camera arrays, and validator literals. `docs/ai-kb/33_combat_framework_comparison.md` records the scale-up assessment: the Java side is the blocker for a second weapon.

This change is infrastructure. It must not change what a player sees or what the server decides for Qingfeng.

## Goals / Non-Goals

**Goals:**

- One data source per move set, read by the Java runtime, the PAL generator, and the validator.
- Weapon selection by held item through a registry; no combat code names a specific item.
- A validator that checks data and cross-file constraints and survives refactors.
- The capture workflow inside the repository, with a third-person freeze probe.

**Non-Goals:**

- New weapons, new moves, or retuning Qingfeng.
- Datapack override, `/reload`, or network sync of style data.
- Parameterizing `tools/gen_qingfeng_sword_model.py`, offline pose preview, move templates, two-handed grips.

## Decisions

### D1. Data lives in bundled JSON under `data/myvillage/combat/` and is loaded from the classpath

```text
src/main/resources/data/myvillage/combat/index.json
src/main/resources/data/myvillage/combat/style/<path>.json
src/main/resources/data/myvillage/combat/weapon/<path>.json
```

`index.json` lists style ids and weapon ids; an id `ns:path` maps to `data/<ns>/combat/style/<path>.json` or `.../weapon/<path>.json`. The loader reads them with `Class#getResourceAsStream` once, on first use, on both sides. The client and the server run the same jar, so they hold the same data without a sync payload, exactly as they held the same Java constants before.

Alternative considered: a server reload listener plus a clientbound sync payload. It would allow `/reload` tuning, but the client rig loader validates strike windows against the style at resource-reload time, before any server is joined, so the ordering has to change too. That is deferred; the loader is written as pure functions over JSON so a later reload-and-sync step is additive. Until then a datapack that shadows these paths has no effect, and the KB note says so.

An index file is used instead of directory listing because classpath directories cannot be listed uniformly from a jar and from unit tests.

### D2. Style file schema (version 1)

Top level: `schema` (1), `id`, `combo_timeout_ticks`, `minimum_intent_interval_ticks`, `animations.ready_idle`, `animations.mode_enter`, `moves` (ordered combo).

Each move:

| Field | Meaning |
|---|---|
| `id` | Move id; also the PAL animation id and the key in the first-person rig |
| `display_key` | Translation key |
| `kind` | `thrust` or `cut`; selects the trail form (streak or band) and the generator's pose checks |
| `total_ticks`, `active_ticks` `[start, end]`, `buffer_start_tick`, `chain_tick` | Server timing, same invariants as `AttackMoveDefinition` |
| `damage_multiplier`, `maximum_targets`, `range` | Damage contract |
| `reaction` | `hitstun_ticks`, `slide_distance`, `lift`, `lateral_bias` |
| `hitbox` | `shape_family`, `horizontal_tolerance`, `vertical_tolerance`, `samples` |
| `step` (optional) | `tick`, `maximum_distance`, `support_depth` |
| `feedback` | `swing_sound`, `swing_pitch`, `hit_sound`, optional `heavy_layer_sound`, `heavy_hit`, `hit_stop_ticks`, `camera_trauma`, `cut_roll_degrees` |
| `camera` | `hit_pitch_kick`, `hit_roll_kick`, `hit_fov_punch`, `swing_lean_degrees`, `step_fov_surge` |

`hitbox.samples` is either an explicit list of `{tick, start:[x,y,z], end:[x,y,z], horizontal_radius, vertical_radius}` or a generator object. The three generators are the existing helper functions with their current constants, parameterized only by what the five moves vary, and sampled over the move's active ticks:

- `{"generator": "thrust", "first_range", "final_range", "radius"}`
- `{"generator": "arc", "range", "start_angle", "end_angle", "height", "radius"}`
- `{"generator": "diagonal", "descending", "radius"}`

Keeping the generators in Java means the loaded samples are the same doubles as before. Explicit samples are for future shapes.

Sounds are sound-event ids resolved through the sound registry, so a new weapon needs no Java enum. `heavy_hit` still selects the heavy particle and spark count. `swing_lean_degrees` is the signed lean (the old sign times `0.3`), and `step_fov_surge` replaces the "step of at least one block" test with an explicit value (`2.0` on the lunge, `0` elsewhere).

Unknown fields are rejected, so a misspelled field fails at load instead of being ignored.

### D3. Weapon file schema (version 1)

`schema` (1), `item`, `style`, `first_person_rig`, `geometry`. The rig and geometry are client asset locations kept as plain ids; common code never resolves them. Two weapons may share a style and a rig and differ only in geometry.

### D4. Runtime shape

- `CombatStyles` (common, `combat/definition`): the registry. Lookups by style id, by item, and by move id (move ids are unique across styles). A held item with no weapon entry is not a combat weapon.
- `AttackMoveDefinition` gains `kind`, feedback, and camera cues; `MoveFeedback` carries sound ids. `BasicSwordStyle` is deleted.
- The server session is created with the style of the held weapon; switching to another registered weapon mid-action stops with `WEAPON_CHANGED` as today, and the next intent starts a session with the new weapon's style.
- `CombatImpactPayload` carries `moveId`; protocol `7`. The two C2S payloads stay empty.
- Client: rigs and geometry load per weapon on resource reload; the item extension is registered for every registered weapon item; `Qingfeng*` client classes are renamed to weapon-neutral names.
- A load failure of the bundled data is a startup error on both sides, not a silent fallback. A missing or invalid client rig or geometry keeps the current behavior: that weapon uses the vanilla hold.

### D5. Equivalence is proven, not assumed

Before `BasicSwordStyle` is deleted, a test asserts that the definition loaded from `basic_sword.json` equals the legacy definition, feedback list, and camera arrays value for value. The test then keeps those expectations as literals. `tools/gen_sword_pal_anims.py --check` must report the committed `sword_combat.json` unchanged. A capture of the five moves before and after is compared per move and tick.

### D6. Generator reads timing from the style file

`tools/gen_sword_pal_anims.py` keeps its pose tables (they are the authored third-person source) but takes `total`, active window, `chain_tick`, step tick, and `kind` from the style file. Pose tables are grouped per style so a second style is an added table, not an edit to shared code.

### D7. Validator checks data

`tools/validate_sword_combat_foundation.py` validates the index, style, and weapon files against the schema and the timing invariants, then cross-checks: every move has a PAL animation of length `total_ticks / 20`, a rig entry whose `strike` window covers the active ticks, a translation, and resolvable sound events; every weapon's item, rig, and geometry exist. It carries no per-move literal expectations. Source checks are limited to refactor-stable invariants: PAL and client imports stay under `client/combat`, the C2S payloads are empty records, no vanilla `attack(` call in combat code, the impact payload is clientbound only and carries no damage or health. The accepted Qingfeng numbers are pinned once, in a regression test.

### D8. Smoke probes

`/myvillage_pal_smoke move <n>`, `first_person <n> <tick>`, and the new `third_person <n> <tick>` use the style of the weapon in the main hand; `<n>` is one-based within that style. `third_person` plays the move's PAL animation from `<tick>` at rate zero on the local player and holds it until `third_person release`, `stop`, or a real action. `first_person release` is unchanged. Each probe logs one `PAL_SMOKE` line with move and tick. They remain client pose probes that send nothing.

### D9. Capture tooling

`tools/combat_capture/` uses only the standard library plus the host's `Xvfb`, `xdotool`, `ffmpeg`, and ImageMagick. It drives the repository's own run tasks (`runAcceptanceServer`, `runClient -Pcombat_smoke_*`), not the lab's separate project. It reads move lists from the style file and key ticks from the rig, so it works for any registered weapon. Output goes under `out/preview/combat_capture/<label>/`. Its output is developer evidence; it never records an owner verdict.

## Risks / Trade-offs

- Behavior drift in a large refactor → D5 equivalence test, byte-identical generated animation, and a before/after capture.
- The validator loses coverage when source-string checks are dropped → each dropped check is either replaced by a data check or a Java unit test, and the change's tasks list the mapping.
- Bundled data under `data/` looks datapack-overridable but is not → documented in the KB note; revisit with the reload-and-sync step.
- The host has 4 cores and 7 GB of memory → Gradle and Minecraft runs are serialized across workers with a shared lock.

## Migration Plan

One release. Payload protocol `7` requires matching client and server jars, as every earlier protocol bump did. Rollback is reverting the change.

## Open Questions

None blocking. Reload-and-sync of style data is the natural next step if server-side tuning speed becomes the bottleneck.
