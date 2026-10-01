## Why

Adding a combat move today means editing five places that repeat the same facts: `BasicSwordStyle.java`, the timing columns in `tools/gen_sword_pal_anims.py`, the first-person rig, the index-aligned camera arrays in `CombatCameraFx`, and the literal expectations plus Java-source string matches in `tools/validate_sword_combat_foundation.py`. The runtime also names `ModItems.QINGFENG_SWORD` directly, so a second weapon cannot be added without touching the session manager, client input, trails, and arm renderer. The capture tooling that made the 0.27.x iterations fast lives outside the repository in a folder that is not under version control, and the repository has no third-person freeze probe.

## What Changes

- Add bundled combat data files under `data/myvillage/combat/`: one style file per move set (timing, hit shapes, step, reaction, feedback, camera cues), one weapon file per item (style, first-person rig, geometry contract), and an index.
- Load those files into the existing definition records at startup and resolve style, rig, and geometry by held item through a registry. Remove `BasicSwordStyle` and every direct `ModItems.QINGFENG_SWORD` combat check.
- Move the per-move sound ids, hit-stop, camera kicks, and step FOV surge from index-aligned Java tables into the style file.
- **BREAKING** (network): `CombatImpactPayload` carries the move id instead of a move index; payload protocol goes from `6` to `7`.
- Make `tools/gen_sword_pal_anims.py` read move timing from the style file instead of repeating it; the generated `sword_combat.json` stays byte-identical.
- Rework `tools/validate_sword_combat_foundation.py` to validate the data files and their cross-file constraints; keep only refactor-stable source checks for the authority and side boundaries.
- Add `/myvillage_pal_smoke third_person <move> <tick>` and `third_person release`, and make the smoke commands follow the held weapon's style.
- Add `tools/combat_capture/`: session control, hot reload, per-move first- and third-person stills at key ticks, before/after comparison sheets, and one command that produces a review page under `out/preview/`.
- Qingfeng's five moves, timing, damage, feel, and resources do not change.

## Capabilities

### New Capabilities

- `combat-style-data`: The combat data files, their schema and invariants, the loader and weapon registry, the authority boundary, and the smoke probes that follow the held weapon.
- `combat-data-validation`: What the focused validator and generator checks must prove about the data files and their consistency with rigs, animations, sounds, and translations.
- `combat-capture-tooling`: The in-repository capture workflow and its evidence boundary.

### Modified Capabilities

None. The earlier `add-sword-combat-foundation` change was removed unarchived, so no combat capability exists under `openspec/specs/`.

## Impact

- Java: `combat/definition`, `combat/session`, `combat/runtime`, `combat/network`, `client/combat`, `ModPayloads` protocol version, and their tests.
- Resources: new `data/myvillage/combat/**`; existing rig, geometry, animation, model, and sound resources are unchanged.
- Tools: `gen_sword_pal_anims.py`, `validate_sword_combat_foundation.py`, their tests, and new `tools/combat_capture/`.
- Docs: `README.md`, `AGENTS.md`, `docs/ai-kb/32_pal_combat_integration.md`, a new KB note, `docs/ai-kb/INDEX.md`, `CHANGELOG.md`.
- Out of scope: new weapons or moves, a parameterized sword-model generator, an offline pose preview, move templates, two-handed grips, datapack override or server-to-client sync of style data.
