## Why

Even with every building corrected (companion change `rebuild-cultivation-building-form`), a sect still reads as buildings standing in a row on flat ground. The 仙气/缥缈 of a 仙府 comes from how the complex *sits*: a central axis climbing a mountain through stacked terraces (蹬道/石阶), halls rising level by level, pavilions and pagodas linked by covered galleries and flying bridges (廊桥/飞桥), backed by cliff and fronted by water and cloud-sea. Today the sect compound (`sect_terraced_axial_compound`) has only a lower/upper two-platform split with an axial gate→scripture→hall ordering (`compound.py:894-966`) — a flat diagram of the idea, not a mountain — and the town's civic anchor (`town_shrine`) is dropped as a generic parcel rather than terminating a ritual axis. The composition layer is where the remaining gap lives.

## What Changes

- Deepen the sect compound from two platforms to a **multi-level terraced ascent**: N stacked level platforms climbing the central axis, each level a courtyard (院落) with its own building slots, joined by monumental stairways (蹬道).
- Add **covered-gallery and flying-bridge links** (廊桥/飞桥) that connect pavilions, pagodas, and the two sides of the axis as first-class circulation/structure elements, not decoration.
- Add **siting context**: the compound declares a mountain/cliff/water/cloud context so terraces cut into rising ground, the rear hall backs a cliff, and pavilions may cantilever (悬空) over water or void; export hooks expose the context and relative terrace levels for in-game placement.
- Make the cultivation **town a ritual-axis settlement**: a central axis terminating at the (newly-formed) 神庙/道观 shrine, with a plaza, a 牌坊 gate, and a lantern-lined approach — so the shrine anchors the town instead of sitting as a generic parcel.
- Grade **importance by terrace level**: gate at the foot, utilitarian/disciple levels mid-slope, the great hall and scripture pagoda at the summit.

**Depends on `rebuild-cultivation-building-form`**: this change composes the building forms that change produces — sweeping-eave halls, pagodas, 山门, 神庙 — into the mountain complex. It assumes those forms exist.

Out of scope: the per-building form itself (companion change); live-world terrain generation (this change emits relative terraces + a siting context; binding them to actual world terrain is tracked separately under the runtime town work).

## Capabilities

### New Capabilities
- `cultivation-mountain-siting`: covered-gallery / flying-bridge links, a mountain/cliff/water/cloud siting context, a settlement ritual axis (sect mountain axis and town plaza axis), and importance graded by terrace level.

### Modified Capabilities
- `courtyard-compound`: the sect terraced-axial layout deepens from two platforms to a multi-level climbing axis with a per-level courtyard and inter-level monumental stairways.

## Impact

- **Code**: `tools/buildgen/compound.py` (multi-level terraces, per-level courtyards, gallery/bridge link elements, siting-context meta, level-graded importance), `tools/buildgen/town.py` (town ritual axis terminating at the shrine), `tools/buildgen/export.py` (emit siting context + relative terrace levels), validators under `tools/` (terrace/level/bridge connectivity + axial hierarchy).
- **Specs**: new `cultivation-mountain-siting`; delta to `courtyard-compound`.
- **Compatibility**: the `chinese_courtyard` one-courtyard layout and the medieval/civic libraries are untouched. Cultivation town/sect compounds are regenerated. This change is layered on top of the companion's building forms and does not itself change any single building's silhouette.
