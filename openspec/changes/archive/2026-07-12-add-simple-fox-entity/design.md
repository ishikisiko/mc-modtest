## Context

The mod currently registers blocks, items, structures, and runtime commands but no custom `EntityType`. The requested skill test spans both logical sides of NeoForge: common/server registration and data resources, plus a client-only renderer and visible texture. The implementation must stay compatible with Minecraft 1.21.1, NeoForge 21.1.233, and Java 21, and it must not disturb the active architectural-component work on its separate branch.

## Goals / Non-Goals

**Goals:**

- Exercise the complete low-cost custom-entity path: contract, registration, inherited AI/state, texture, spawn egg, loot, natural spawn, validation, dedicated server, docs, and visual handoff.
- Keep the server/client class boundary explicit and verifiable.
- Produce deterministic, reviewable source evidence for the custom texture.

**Non-Goals:**

- Custom geometry, GeckoLib, bespoke animation controllers, emissive rendering, custom OGG audio, combat skills, taming changes, riding, equipment, or multiple custom variants.
- Treating a successful build or file-presence check as an accepted visual verdict.

## Decisions

### Reuse the vanilla fox implementation

`SimpleFoxEntity` subclasses the mapped vanilla `Fox` class. This preserves the proven fox navigation, goals, animation state, sound hooks, synced entity data, NBT behavior, and ordinary fox breeding while still exercising an independent `EntityType`. A narrow offspring override creates `myvillage:simple_fox` rather than the vanilla `minecraft:fox`; no genetics or new interaction rules are added.

The alternative was a new `Animal` implementation. That would duplicate AI and state machinery and would make this a poor test of the smallest viable workflow.

### Use the vanilla renderer stack with a custom texture

The client registers a dedicated renderer backed by the vanilla `FoxModel` and fox model layer. It always returns `myvillage:textures/entity/simple_fox/simple_fox.png`, keeping the visible identity independent from the inherited vanilla fox variant. No client model or renderer class is referenced from common/server code.

The alternative was GeckoLib 4. It adds a runtime dependency and Blockbench export surface without improving this deliberately plain cuboid fox.

### Treat the vanilla texture layout as the UV truth source

The checked Minecraft 1.21.1 fox texture and its alpha footprint define the 48x32 texture canvas and filled island mask. The Entity Contract records the rectangular dimensions directly and uses a 1152x768 uniform-scale work canvas. Concept and atlas candidates are generated only through Codex's built-in imagegen tool. The accepted or rejected candidate, local references, deterministic composite boundary, and validation result are recorded without API keys, SDK calls, or fabricated provenance.

The generated concept is retained as direction evidence. Two atlas candidates passed mask, alpha, size, and palette checks but placed directional head details across adjacent cuboid faces, even after a `part.face` overlay was supplied. Both are rejected. The deterministic native-resolution atlas remains the runtime asset because model-space correctness outranks generator use.

Face-local native patch data pins the paired eyes to `head.north` and the nose to `muzzle.north`; the texture validator checks those exact runtime pixels in addition to generic UV coverage.

### Keep custom state empty

The first iteration adds no custom synchronized or persisted fields. The Entity Contract explicitly lists inherited fox state and verifies that save/reload is provided by the base class. This avoids meaningless state added only to satisfy a checklist.

### Pair data-driven spawning with Java placement registration

Natural generation uses a conservative taiga biome tag and NeoForge biome modifier plus `SpawnPlacements` registration with the vanilla fox's `NO_RESTRICTIONS` placement type, motion-blocking heightmap, and ground predicate. The pair is validated together; either half missing is a failure.

### Reuse legal vanilla sounds and ship an intentional empty loot table

The entity inherits fox ambient, hurt, death, and step sounds. No empty OGG placeholders are created. The first-iteration loot table contains no pools, avoiding an invented reward while still proving the resource resolves.

## Risks / Trade-offs

- [Vanilla `Fox#getBreedOffspring` creates `minecraft:fox`] -> Override only offspring construction and preserve the inherited parent variant selection.
- [Renderer generics and spawn APIs differ across mappings] -> Resolve signatures from the current Gradle dependency and compile after each boundary.
- [A deterministic recolor is less distinctive than bespoke art] -> Accept the narrow orange/cream palette for this skill smoke test and leave the human visual verdict pending.
- [A mechanically valid generated atlas can still misunderstand cuboid faces] -> Provide a per-face overlay, inspect native UV semantics, and keep deterministic 1-to-3-pixel details when a candidate crosses face boundaries.
- [Natural-spawn frequency cannot be proven by a short server boot] -> Validate codecs and placement registration automatically, then leave seed/time frequency observation as a documented in-game step.
- [No headless renderer proves entity appearance] -> Review the atlas evidence now and require in-game multi-view approval before visual acceptance.
