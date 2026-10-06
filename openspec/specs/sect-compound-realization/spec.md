# Sect Compound Realization

## Purpose

This spec captures the live-world realization of a sect compound: the `/myvillage sect` command that builds the planned terraced compound against terrain, solid retaining bands and grand stairs, the walkable axis corridor and gate passage, guarded template and feature placement, runtime palette resolution through the mod-fallback resolver, and a validator that enforces the compound invariants.

## Requirements

### Requirement: A command builds a sect compound in the live world against terrain

The mod SHALL expose `/myvillage sect [seed]`, planning and building a complete terraced sect compound at the player's location. The realizer SHALL acquire chunk-load tickets across the planned footprint before placement and release them afterward; if a region cannot be force-loaded or built, the command SHALL report the affected extent rather than silently skipping it. The build SHALL realize the forecourt, the paved axis corridor with the passage through the gate building, the terraces with their slotted volumes, the retaining bands and grand stairs, and — when the seed selects it and it clears the compound — the detached-spire flying-bridge feature.

#### Scenario: Summoning a sect compound

- **WHEN** an operator runs `/myvillage sect` at a location
- **THEN** the mod SHALL force-load the planned footprint (including the forecourt) and build a sect compound with a forecourt, a mountain gate with a through-passage, ascending terraces, slotted volumes graded by terrace level, grand stairs, a paved axis corridor, and a cliff-backed principal hall
- **AND** if a region of the footprint cannot be force-loaded or built, the command SHALL report that extent rather than fail silently.

#### Scenario: Same seed rebuilds the same compound

- **WHEN** `/myvillage sect <seed>` is run twice on equivalent terrain
- **THEN** the two compounds SHALL be structurally identical in terrace layout, slot assignment, axis, stairs, and feature selection.

### Requirement: A coordinate command builds a live-terrain sect compound

The mod SHALL expose `/myvillage sectat <seed> <x> <y> <z>`, planning and building the same live-terrain terraced sect compound as `/myvillage sect <seed>` but anchored at the explicit coordinate instead of the executing player's position. The command SHALL use the same force-loading, terrace realization, runtime fallback resolution, reporting, and deterministic seed-and-site behavior as the player-position command.

#### Scenario: RCON builds a sect without a player

- **WHEN** an operator runs `/myvillage sectat 20260618 -512 80 0` from RCON or the server console
- **THEN** the mod SHALL build a live-terrain terraced sect compound at that explicit site
- **AND** the command SHALL not require a `ServerPlayer`.

#### Scenario: Coordinate sect mirrors player-command behavior

- **WHEN** `/myvillage sectat <seed> <x> <y> <z>` and `/myvillage sect <seed>` are run with equivalent anchor positions and terrain
- **THEN** both commands SHALL produce the same terrace plan, force-load the same footprint, realize the same volumes/stairs/corridor, and report the same placed/skipped/fallback-substitution counts.

### Requirement: Terraces meet the ground through carved retaining, not floating slabs

Realizing a terrace SHALL join it to terrain and to the terrace below so terraces step the slope rather than floating above a hollow or being buried. Each terrace platform SHALL be filled down to the natural ground and carved open above. The band in front of each upper terrace SHALL be filled solid up to the upper floor: a stone core, a stone-brick face toward the lower terrace with a chiseled coping course at the top, stone-brick sides, and no battlements, railings, or wall blocks (the generator SHALL write no `*_wall` block anywhere). Band cells inside the lower terrace's width but outside the upper one's SHALL be lower floor. No one-block air gap SHALL remain beneath a terrace platform or a slotted volume's footprint.

#### Scenario: A terrace steps the slope

- **WHEN** a terrace is realized on sloping terrain
- **THEN** it SHALL be joined to the terrace below by a solid retaining band with a stone-brick face and chiseled coping, and by a grand stair
- **AND** no one-block air gap SHALL remain under the terrace platform or under any volume placed on it
- **AND** no block the generator writes SHALL be a wall block.

#### Scenario: The summit hall backs solid ground

- **WHEN** the summit terrace declares a cliff-back edge
- **THEN** the principal hall SHALL be realized against solid ground at that edge, not floating over a drop.

### Requirement: Grand stairs climb between terraces in single steps

Each grand stair SHALL be realized as the last terrace element, so no band fill, retaining face, or terrain overwrites a tread or its headroom: south-facing stone-brick stair treads across the stair width, the first flight starting on the lower terrace's floor and rising one block per row, a full-block landing, and a second flight ending level with the upper floor. Every tread and the landing SHALL stand on solid stone brick down to the lower floor with four blocks of air above. Cheek walls of solid stone brick SHALL flank the treads one block higher than the tread in each row, ending one block above the upper floor.

#### Scenario: A stair is walkable and reads as built

- **WHEN** a grand stair is realized between two terraces
- **THEN** walking up any tread column SHALL rise by at most one block per row, never descend, and climb exactly one terrace rise
- **AND** every tread SHALL be solid underneath with open headroom above
- **AND** cheek walls SHALL stand on both sides.

### Requirement: The axis is walkable from the forecourt to the principal hall

The realizer SHALL level the forecourt in front of the gate terrace with the gate terrace's floor and clear it above, pave the axis corridor in polished andesite with a chiseled centre line on the forecourt and the terrace floors, cut a three-wide, four-high through-passage along the axis through the gate building after the building is placed (removing doors, the back wall and furniture in it, and paving its floor on the building's plinth), and set a row of polished-andesite stairs in front of and behind the passage for the plinth's one-block step. As its final pass, the realizer SHALL clear five blocks of air above the corridor floor everywhere from the forecourt's front row to the row before the principal hall, except inside the gate building (where the passage provides the way through).

#### Scenario: Nothing blocks the axis

- **WHEN** a compound is realized by either build path
- **THEN** along the corridor's inner five columns (the passage's three inside the gate building) the walking surface SHALL change by at most one block per row from the forecourt to the principal hall, with at least three blocks of air above it.

### Requirement: Template and feature placement leave nothing floating

Before a sect building template is placed, every non-air template block that touches no other non-air template block on any of its six faces SHALL be dropped, so no lone stair, slab or trapdoor of a template is left floating. The detached-spire feature SHALL be built only when its detached bounds, grown by one block, are disjoint from every slot, terrace and stair rectangle; otherwise it SHALL be skipped, counted, and logged once, and the derived mountain SHALL not raise its peak.

#### Scenario: No floating fragments

- **WHEN** a compound is realized
- **THEN** no block of the result SHALL be free of a non-air neighbour on all six faces.

#### Scenario: A spire that would overlap the compound is skipped

- **WHEN** the selected detached-spire variant's bounds overlap a slot, terrace or stair
- **THEN** neither the detached volume nor its bridge SHALL be built
- **AND** the build report SHALL say the feature was skipped.

### Requirement: Runtime sect placement resolves palette ids through the mod-fallback resolver

Runtime template placement for `/myvillage sect` SHALL route every structure-palette block id through the runtime mod-fallback resolver before the block reaches the world, so a palette id absent from the live registry is placed as its vanilla fallback rather than as air. Registry-present ids SHALL remain unchanged, and a template whose palette names only registry-present ids SHALL place identically to placement without the resolver.

#### Scenario: A sect is built without optional decor mods

- **WHEN** `/myvillage sect` places a piece whose palette contains an absent optional-mod block id
- **THEN** the realizer SHALL load the template through the fallback-patching helper
- **AND** the fallback block state SHALL be placed instead of air.

#### Scenario: A vanilla-only piece is unaffected

- **WHEN** placement realizes a piece whose palette names only registry-present ids
- **THEN** the placed result SHALL be identical to placement without the resolver.

### Requirement: A validator enforces sect compound invariants

Realization SHALL be checked by a validator that confirms: the ritual axis is ordered and traversable from the gate terrace to the summit; importance tiers are non-decreasing up the terraces with the principal hall and the scripture pavilions at the top tiers; no slot other than the gate and the principal hall intersects the axis corridor and no slot intersects a stair; any present flying bridge has both endpoints resting on the volumes/terraces it joins; and the build is reproducible for a fixed seed. The validator SHALL emit a report rather than failing silently.

#### Scenario: Invariants are reported

- **WHEN** a sect compound is realized and validated
- **THEN** the validator SHALL confirm axis ordering and traversability, importance-by-terrace, a clear axis corridor and stairs, and bridge endpoint anchoring
- **AND** SHALL emit a report recording any violated invariant and the affected extent.

### Requirement: The compound realizer places onto worldgen-derived terrain

The sect compound realizer SHALL be reusable by the worldgen path: given a terrace profile and the mountain derived from it, the realizer SHALL place the compound's volumes, stairs, corridor, and the flying-bridge feature (when it clears the compound) onto the derived terrain using the same deterministic geometry as the `/myvillage sect [seed]` on-the-spot build. The existing on-the-spot command behavior SHALL be preserved unchanged when no derived terrain is supplied.

#### Scenario: The same realizer serves command and worldgen

- **WHEN** the worldgen path supplies a terrace profile and its derived mountain
- **THEN** the compound realizer SHALL place the same volumes, stairs, corridor, and feature it would for `/myvillage sect [seed]`, sat on the derived terrain
- **AND** the on-the-spot `/myvillage sect [seed]` build SHALL remain unchanged when no derived terrain is supplied.
