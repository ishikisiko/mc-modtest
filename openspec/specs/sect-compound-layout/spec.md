# Sect Compound Layout

## Purpose

This spec captures the deterministic plan for a cultivation sect compound: a terraced stack symmetric about a single ritual axis, a paved axis corridor from a forecourt to the principal hall, importance-by-terrace massing, mirrored flanking volumes beside the axis, solid retaining bands and projecting grand stairs between terraces with a cliff-backed summit, and the optional detached-spire flying-bridge feature.

## Requirements

### Requirement: A sect compound is a terraced stack ascending a single ritual axis

The sect-compound plan SHALL compose an ordered stack of terraces along one fall-line, joined by a single ritual axis that runs from a levelled forecourt in front of the mountain gate (山门) on the lowest terrace to the principal hall (主殿) on the highest. Every terrace SHALL be symmetric about the axis. The plan SHALL be deterministic on its seed: the same seed and site SHALL produce the same terrace count, terrace bounds, axis, and slot assignment. The default skeleton SHALL be five terraces — gate, disciple, assembly, scripture, summit — and the terrace count SHALL be a parameter in the range 4–6.

#### Scenario: The axis ascends from gate to hall

- **WHEN** a sect compound plan is produced
- **THEN** the plan SHALL record an ordered terrace stack with each terrace at a higher elevation than the one below it
- **AND** a single ritual axis SHALL run on the fall-line from the mountain gate on the lowest terrace to the principal hall on the highest terrace
- **AND** the gate, the intermediate terraces' buildings, the scripture pavilions, and the principal hall SHALL be ordered along that axis from foot to summit
- **AND** only the gate and the principal hall SHALL stand on the axis.

#### Scenario: The skeleton is deterministic and parametric

- **WHEN** the same seed and site are planned twice
- **THEN** the two plans SHALL have identical terrace count, terrace bounds, axis cells, and slot-to-terrace assignment
- **AND** the terrace count SHALL be within 4–6 with a default of five.

### Requirement: Slot importance and massing grade with terrace level

Within the compound, a slot's importance tier SHALL be non-decreasing with its terrace level, so a slot on a higher terrace receives an importance tier at least as high as one below it. The principal hall SHALL receive the compound's top tier and the scripture pavilions the next, and therefore taller massing and finer roof grade than foot-level slots such as the gate and utilitarian rooms. Building-piece selection per slot SHALL be driven by terrace level, not by an unconstrained random roll.

#### Scenario: Summit volumes outrank foot volumes

- **WHEN** slots are assigned across the terraces
- **THEN** a slot on a higher terrace SHALL have an importance tier greater than or equal to any slot below it
- **AND** the principal hall and the scripture pavilions SHALL hold the compound's highest importance tiers
- **AND** their massing height and roof grade SHALL exceed those of the gate-terrace slots.

### Requirement: Flanking volumes are mirrored beside the axis and keep off it

Non-axis volumes SHALL be placed in mirrored pairs about the ritual axis (bell and drum towers, disciple quarters, alchemy rooms, scripture pavilions), each sized and aligned by its actual template. A flank's inner edge SHALL be one block clear of the terrace's on-axis building where the terrace has one, and otherwise at the fixed flank inner edge beside the axis corridor, so no flank stands on the corridor or on a stair. The scripture terrace SHALL hold two scripture pavilions and no on-axis building; pagodas SHALL NOT stand on terraces (they remain a detached-spire variant). The plan SHALL record no covered galleries in this version; a walkable gallery is future work.

#### Scenario: Flanks mirror across the axis

- **WHEN** a sect compound plan places flanking volumes on a terrace
- **THEN** those volumes SHALL appear as pairs mirrored across the ritual axis, with the same template and the same z span
- **AND** the bell tower and drum tower SHALL flank the gate building symmetrically, clear of it.

#### Scenario: Nothing but the gate and the hall stands on the axis

- **WHEN** the plan's slots are compared with the axis corridor and the stair rectangles
- **THEN** no slot other than the gate and the principal hall SHALL intersect the corridor
- **AND** no slot SHALL intersect a stair or its cheek walls
- **AND** the plan SHALL contain no covered-gallery links.

### Requirement: Terraces are linked by retaining bands and grand stairs on a paved axis corridor, with the summit backed by a cliff

Each terrace SHALL meet the terrace above it through a solid retaining band whose height equals the inter-terrace rise. The ritual axis SHALL cross between terraces by a grand stair wider than the corridor that projects forward onto the lower terrace, climbs in single steps in two flights separated by a landing, and has a cheek wall on each side. A paved axis corridor SHALL run from the front of the forecourt to the row in front of the principal hall, crossing the stairs and passing through the gate building, so the compound is walkable from the forecourt to the hall. The summit terrace SHALL declare a cliff-back edge, and the principal hall SHALL be placed against that cliff-back edge. The plan SHALL expose the geometry parameters terrace rise, depth, width and taper, axis corridor width, stair width and projection, flank inner edges, forecourt rows, and cliff-back height (`TERRACE_RISE 8`, `TERRACE_DEPTH 28`, `TERRACE_WIDTH 59`, `SUMMIT_TAPER 8`, `AXIS_W 7`, `STAIR_W 9`, `STAIR_PROJECT 3`, `FLANK_INNER_LEFT_X1 25`, `FLANK_INNER_RIGHT_X0 37`, `APRON_ROWS 12`, `CLIFF_BACK_HEIGHT 12`), mirrored by the Python planner.

#### Scenario: Adjacent terraces are joined and traversable

- **WHEN** two adjacent terraces are planned
- **THEN** a retaining band of height equal to the inter-terrace rise SHALL be recorded between them
- **AND** a grand stair SHALL connect them on the axis, projecting onto the lower terrace, rising in single steps with a landing between two flights, flanked by cheek walls
- **AND** the axis corridor SHALL continue over the stair so the axis is walkable from the lower terrace to the higher one.

#### Scenario: The principal hall backs the cliff

- **WHEN** the summit terrace is planned
- **THEN** it SHALL declare a cliff-back edge
- **AND** the principal hall SHALL be placed against that cliff-back edge, centred on the axis.

#### Scenario: Terraces taper symmetrically toward the summit

- **WHEN** the terrace widths are derived
- **THEN** the terrace width SHALL be non-increasing from foot to summit and every terrace SHALL be centred on the axis
- **AND** the geometry parameters SHALL be recorded in the plan.

### Requirement: The detached-spire flying-bridge feature ships as three deterministic form variants

The plan SHALL define an optional detached-spire feature: one volume placed on a detached outcrop reachable only by a flying bridge (飞桥) that spans a gap and is recorded as a circulation/structure link whose endpoints rest on the compound and the detached volume. The feature SHALL be built only where its detached bounds, grown by one block, clear every slot, terrace and stair; otherwise it stays in the plan but is skipped at realization. The feature SHALL ship as exactly three form variants that differ on at least two of: which volume is detached, the bridge span and shape, and the spire's offset/bearing from the axis. The active variant SHALL be selected deterministically per seed, and the feature MAY be absent on a given seed.

#### Scenario: A present feature is one of three recorded variants

- **WHEN** a sect compound plan includes the detached-spire feature
- **THEN** the feature SHALL match exactly one of the three defined form variants
- **AND** the detached volume SHALL be reachable only by a flying bridge whose endpoints rest on the compound and on the detached volume
- **AND** the chosen variant SHALL be the same for the same seed.

#### Scenario: The feature may be absent

- **WHEN** a seed does not select the feature
- **THEN** the compound SHALL still form a complete terraced axial plan with all required terraces and the ritual axis intact.

#### Scenario: The three variants are distinct

- **WHEN** the three variants are compared
- **THEN** each pair SHALL differ on at least two of: detached volume, bridge span/shape, and spire offset/bearing.
