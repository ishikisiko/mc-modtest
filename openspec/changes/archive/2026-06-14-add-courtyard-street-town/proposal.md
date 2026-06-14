## Why

The `cultivation_town` (凡人镇) currently ships as **standalone independent buildings** scattered by the game's village placement. That was a deliberate minimal first step (模型A), but it reads as a "Chinese-skinned village," not a real 古风坊市. The intended town identity is a dense market town with **continuous street frontage and lanes** — which the standalone model structurally cannot produce. This change delivers the deferred 模型B: **小合院拼街 (courtyard-street blocks)**.

## What Changes

- Introduce a **小合院 (small walled courtyard)** as the town's base unit: a few small buildings around a tiny 天井, enclosed by 院墙, generated as a *scaled-down* compound — distinct from the existing one-진 `chinese_courtyard` and the monumental `cultivation_sect`.
- Introduce a **street-tiling layout strategy**: tile small courtyards wall-to-wall along streets so adjacent 院墙 form continuous street frontage, with lanes/巷道 between courtyard rows for circulation.
- Bind `cultivation_town` to the new courtyard-street layout via the settlement-group descriptor (replacing its standalone-library binding).
- Generate, validate, and export a `cultivation_town` courtyard-street library (NBT) and document its `/myvillage` commands for staged manual acceptance, consistent with the existing courtyard and sect libraries.
- Reuse the existing `CompoundGraph` parcel machinery and per-building pass pipeline — no new block forms required.

## Capabilities

### New Capabilities
- `courtyard-street-town`: A town-scale layout that composes multiple small walled courtyards tiled along streets, producing continuous street frontage and traversable lanes, with non-overlap guarantees between courtyards, streets, and landscape.

### Modified Capabilities
- `courtyard-compound`: Add a **small-courtyard unit** layout (scaled-down walled courtyard) as a reusable compound that the town tiling consumes, alongside the existing one-courtyard and sect strategies.

## Impact

- `tools/buildgen/compound.py`: new small-courtyard generator + street-tiling composition layer over `CompoundGraph`.
- `tools/buildgen/groups.py`: rebind `cultivation_town` group from the standalone library model to the courtyard-street layout.
- `tools/buildgen/export.py` / library generators / validators: emit and validate a `cultivation_town` courtyard-street library.
- `src/main/resources/data/myvillage/structure/`: new town-block NBT outputs.
- Command docs: add `cultivation_town` place/gallery entries.
- No change to `medieval_village`, `chinese_courtyard`, or `cultivation_sect` outputs (regression-stable). MC 1.21.1, vanilla blocks only.
