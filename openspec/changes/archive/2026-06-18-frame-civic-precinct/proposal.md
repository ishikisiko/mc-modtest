## Why

The `civic_core` district is 31% empty (1104 of 3504 cells on seed 42), and the void is not scattered — ~800 cells sit in a single contiguous block at the core's south (gate-facing) half: an ~528-cell entry band (z111–118) and a ~270-cell forecourt between the two civic halls (z119–128). The result is that after walking the spine into the sacred precinct you cross ~18 blocks of bare grass before reaching the plaza, the built mass all clusters in the northern 40 rows, and the tall landmark band meets the `fringe` spirit-fields across a 2-cell dead seam with no transition. The skeleton is correct; the precinct lacks an approach sequence, plaza enclosure, and a defined edge.

## What Changes

- **Stage a processional approach in the south of the core (primary fill for the ~800-cell void).** Plant a precinct gate (山门牌坊) at the core's south edge and a dressed spirit way (神道) — guardian statues / 灵兽, lantern poles, stele rows — flanking a widened ceremonial path from the gate edge up to the plaza, so the plaza reads as the culmination of a `门→神道→广场→牌坊→神庙` sequence rather than an isolated patch.
- **Enclose the plaza with side halls and a colonnade.** Add small flanking 配殿 (side halls) in the forecourt voids between the civic halls, and run a 廊庑 (colonnade) along the core's east/west edges, consuming the forecourt and the 2–3-wide edge slivers and giving the plaza a courtyard enclosure.
- **Wrap the core in a precinct wall with side gates (this is also the fringe transition).** Enclose the `civic_core` perimeter (at minimum the south and lateral edges) with a low 坊墙 punctuated by side gates, so the core reads as a walled compound and the `fringe` 灵田/药圃 become "outside the wall" — converting the hard tall-to-flat seam into an intentional precinct boundary.
- **Keep the existing ritual axis, landmarks, and skyline relief intact.** The shrine remains the sole dominant landmark terminating the axis; pagoda/bell-drum landmarks and the plaza/paifang/lantern axis are preserved and extended, not replaced.

## Capabilities

### New Capabilities
- `civic-precinct-framing`: The `civic_core` district SHALL be framed as a walled precinct with a staged processional approach (precinct gate + dressed spirit way) leading to an enclosed plaza (flanking side halls + edge colonnade), wrapped by a precinct wall with side gates that forms the transition to the surrounding `fringe`. The framing SHALL consume the core's south entry band and forecourt voids and SHALL preserve the existing ritual axis, dominant landmark, and skyline relief.

### Modified Capabilities
<!-- None. This is additive: the town-plan ritual-axis, town-districts civic_core
     hierarchy, vertical-landmark skyline relief, and town-realization invariants
     are honored and built upon, not changed. New precinct elements are reserved
     plan structures and realized fill; no existing requirement's behavior changes. -->

## Impact

- Code (planner): `tools/buildgen/town.py` — `_layout` (precinct gate / spirit-way band, side-hall and colonnade parcels, precinct-wall cells) and the `civic_core` branch of `_subdivide_district` (currently returns early after halls + landmarks, [town.py:703-733](tools/buildgen/town.py#L703-L733)); ritual-axis metadata extended with the approach sequence.
- Code (realizer): `src/main/java/com/example/myvillage/town/TownGenerator.java` — civic-core realization to place the precinct wall, side gates, spirit-way props, side halls, and colonnade, mirroring the planner exactly for determinism.
- Specs: new `civic-precinct-framing`; existing `town-plan`, `town-districts`, `vertical-landmark`, `town-realization` behavior is honored, not changed.
- Validation/reports: `reports/town_generation_validation.json` and the layout/town-plan previews regenerate; the empty-core metric and precinct-enclosure invariants are added to validation.
- Scope note: independent of the in-progress `densify-cultivation-town` change, which targets market/residential interiors and explicitly does not touch `civic_core`.
