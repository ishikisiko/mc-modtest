# Crafting Aptitudes (0.51.0)

The owner selected a hard ceiling on **same-recipe product quality**, not on
Huang/Xuan/Di/Tian recipe grade. This foundation adds two persistent player
aptitudes and authoritative ceilings. It does not yet grade the existing pills,
change furnace transactions, implement forging, or award crafting experience.

## Sources and boundaries

- `cultivation/crafting/CraftingAptitudes`: immutable version1 codec, explicit
  uninitialized state and independent alchemy/forging scores. Format bounds0..100.
- `CultivationAttachments.CRAFTING`: serialized `myvillage:crafting_aptitudes`
  with copyOnDeath. `CultivationProfile` stays v4; no manual Clone copy.
- `CultivationService`: sole replacement owner. Once-only initialization on
  awakened legacy login and before the owning snapshot after root assignment.
  Generated data survives root clearing/reawakening and profile resets.
- `CraftingGenerator`: deterministic worldseed/UUID generation with separate
  permanent profession domains; does not consume the root/world RNG stream.
- `data/myvillage/crafting/rules.json` / `CraftingRules`: initial roll range and
  ordered product-quality thresholds, strict schema/bounds validation. State
  quality eligibility rejects uninitialized players even though initialized0
  legitimately maps to lower quality. Rebuild/restart for rule changes.
- `CultivationSnapshotPayload`: existing clientbound snapshot includes crafting
  state and two server-computed caps (0 pending,1..4 lower/middle/upper/perfect).
  Protocol20; no new payload or cache. `ClientCultivationState` keeps the same
  three caches and clears the added state with the owning snapshot on disconnect.
- `CraftingPage` through `PanelContext`/`PanelTheme`: read-only H page, appended
  View. Page-local diagnostic `CRAFTING_UI` records geometry/visibility for
  mapped evidence, not a client result authority.
- `cultivation crafting` / `xiulian jiyi`: shared status/set command trees with
  English/pinyin aliases; setters require initialized awakened target and keep
  the other aptitude. Exact commands and initial thresholds are in README.

## Validation

`tools/validate_crafting_aptitudes.py` validates data and ownership/package
boundaries. Tests cover deterministic domain separation, once-only state,
codecs/bounds, all score-to-quality boundaries, snapshot roundtrip, aliases and
read-only client access. Do not weaken the initiation validator's client guard
when adding a legitimate getter; mutation paths must still fail.

Run focused checks then the release build and clean stage1 server. Afterwards
`python3 tools/crafting_aptitude_evidence.py` owns two capture sessions and tests
mapped awakening, six bilingual H layouts, read-only clicks, admin boundary
fixtures, death, root/profile resets and save/restart. Legacy live migration and
unobserved lifecycle/owner surfaces remain not_verified. No DevBridge.

See [crafting-aptitudes](../../openspec/specs/crafting-aptitudes/spec.md),
[cultivation-state-synchronization](../../openspec/specs/cultivation-state-synchronization/spec.md),
[cultivation-persistence-lifecycle](../../openspec/specs/cultivation-persistence-lifecycle/spec.md),
[cultivation-debug-commands](../../openspec/specs/cultivation-debug-commands/spec.md),
[validation](../../openspec/specs/validation/spec.md), [panel](37_cultivation_panel.md)
and [alchemy](49_alchemy.md).
