## Context

The cultivation sect exists as specs (`cultivation-mountain-siting`, `settlement-group`) and shipped building `.nbt` pieces, but no runtime assembles a compound from them. The town path is the model: a deterministic Python planner (`tools/buildgen/town.py`) and a structurally-equivalent Java realizer (`TownGenerator.java`) that force-loads a footprint, site-fits parcels against the heightmap, and routes palette ids through the mod-fallback resolver. This change builds the sect equivalent so the compound is buildable and acceptable before the follow-on `add-sect-worldgen` change sites it in the world and derives the mountain.

The defining difference from the town: the town spreads districts across a flat-ish footprint and skips parcels above `MAX_SLOPE`; the sect deliberately wants relief and expresses it as an **ordered terrace stack ascending one axis**, with importance graded by height. The terrace profile this change produces is also the input the worldgen change will use to derive the mountain (反推山形), so the geometry parameters are first-class outputs, not internal details.

## Goals / Non-Goals

**Goals:**
- A deterministic terraced axial sect-compound plan (Python) + structurally-equivalent Java realizer, reproducible per seed.
- `/myvillage sect [seed]` builds a complete compound at the player against terrain, carving retaining + on-axis stairs, reusing the town's chunk-ticket and mod-fallback paths.
- Importance graded by terrace level; symmetric flanks joined by covered galleries; cliff-backed summit.
- The detached-spire flying-bridge feature as 3 deterministic form variants, selectable per-seed and optionally absent.
- Expose terrace skeleton + geometry parameters for `add-sect-worldgen` to consume.

**Non-Goals:**
- No worldgen, no custom `Structure`, no automatic mountain raising, no cloud-sea/fog — all deferred to `add-sect-worldgen`.
- No new authored `.nbt` pieces; galleries/retaining/stairs/bridge are block-placed; the detached volume reuses a shipped pavilion/pagoda piece.
- No change to the town planner/realizer.
- The solitary peak's terrain under the flying-bridge feature is not built here (the feature is realized on whatever terrain exists; `add-sect-worldgen` supplies the spire).

## Decisions

### D1: Mirror the town's planner/realizer parity, do not Java-only
A `tools/buildgen/sect.py` planner produces the deterministic `_SectPlan` (terraces, axis, links, slots, variant pick); `SectGenerator.java` mirrors it for the in-world build, as `TownGenerator` mirrors `town.py`. This keeps previews/validation in Python and the live build in Java without a shared RNG, matching the established determinism contract.

### D2: Terrace profile is the contract with worldgen
The plan emits terrace count, per-terrace elevation/bounds, rise, depth, width taper, axis-stair width, and cliff-back height as explicit outputs. `add-sect-worldgen` derives the mountain from exactly these, so the seam is the parameter set, not a re-derivation.

### D3: Build on its own terraces now, blend into real mountains later
Run via command, the realizer carves and retains its own terraces against the heightmap (like the town's site-fit, but stepping rather than skipping). It does not raise a mountain. When worldgen wraps it, the derived mountain supplies the relief and the same retaining/stair logic dresses the seam.

### D4: Flying-bridge feature is structural here, terrain there
This change defines the 3 form variants (detached volume + bridge link + spire offset) and selects one per seed, placing the detached volume + bridge on existing ground. The solitary peak under it, the random worldgen appearance, and the force-generate/variant-select command are `add-sect-worldgen`'s scope.

## Risks / Trade-offs

- **No real mountain yet** — built by command on flat-ish ground, terraces will step a shallow grade and the cliff-back may read weakly until worldgen supplies relief. Accepted: this change is about form, not siting.
- **Planner/realizer drift** — two implementations of the same geometry can diverge; mitigated by a same-seed parity assertion in validation, as the town does.
- **Footprint size** — a 5-terrace compound plus a detached spire can be large; the chunk-ticket force-load and the report-don't-skip contract bound the blast radius.
