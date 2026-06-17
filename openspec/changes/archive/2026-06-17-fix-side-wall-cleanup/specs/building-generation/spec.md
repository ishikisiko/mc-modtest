## ADDED Requirements

### Requirement: Side walls are fully enclosed and free of stray blocks
A generated building's side walls SHALL be complete and structurally coherent. Every
cell of a closed volume's wall plane, from the foundation top to the roofline directly
above it, SHALL be a non-air block unless it is a planned opening (door, window, or
inter-volume connection). No interior furnishing or other block belonging to one volume
SHALL be placed in the exterior wall plane of a different volume.

#### Scenario: A gable end wall is enclosed up to the ridge
- **WHEN** a gabled volume is roofed
- **THEN** the gable plane SHALL be filled to the true ridge height with no apex gap
- **AND** any cell whose only roof block is a stair SHALL be backed by a full block in the wall plane.

#### Scenario: A blacksmith's smithy furniture stays inside its own shed
- **WHEN** the smithy interior zone is furnished beside the main building
- **THEN** anvils, barrels, and furnaces SHALL mount only on the smithy shed's own wall surfaces
- **AND** they SHALL NOT be placed against the main building's exterior side wall.

### Requirement: Gable infill uses a style-appropriate material
The gable triangle SHALL be filled from a style-declared gable material, defaulting to
the volume's primary `WALL_MAIN` material when the style declares no dedicated gable
slot. The generator SHALL NOT hardcode the dark roof plank as gable infill, and a gable
cell SHALL be tagged with the material slot it actually holds.

#### Scenario: A stone-walled style produces a solid gable
- **WHEN** a `cultivation_sect`, `chinese_courtyard`, or `cultivation_town` building is gabled and the style declares no gable-infill slot
- **THEN** the gable SHALL be filled with the `WALL_MAIN` material
- **AND** it SHALL NOT contain dark roof planks scattered through the wall.

#### Scenario: A style opts into timber-infill gables
- **WHEN** a style declares a dedicated gable-infill material
- **THEN** the gable SHALL use that material, and the cell's recorded slot SHALL match the material placed.

## MODIFIED Requirements

### Requirement: Protected cells survive later normal writes
A `PROTECTED` cell SHALL NOT be overwritten by a later normal write. Because the grid
enforces only `PROTECTED` and otherwise lets the last writer win, a pass that adds
interior furnishing, an inter-volume connection, or a chimney SHALL NOT write into a
cell that lies in another volume's wall plane; it SHALL decline the write or route
around the occupied wall rather than rely on pass priority.

#### Scenario: A door op cannot remove a protected entry step
- **WHEN** a later pass attempts to overwrite a `PROTECTED` entry-step cell
- **THEN** the write SHALL be refused unless it is explicitly forced.

#### Scenario: A chimney abuts an attached wing wall
- **WHEN** a chimney column would fall on a cell occupied by an abutting `side_wing` or shed wall
- **THEN** the chimney SHALL NOT force-overwrite that wall's facade cell
- **AND** it SHALL offset around the wing or re-seal the wall so material stays continuous.

### Requirement: Facade planning avoids flat, corner-opening walls
Facade planning SHALL split walls into post-bounded bays, keep openings away from
building corners, avoid occluded attached-wall intervals, and guarantee at least the
style profile's minimum planned window count where possible. Every wall tall enough to
carry it SHALL retain a stone plinth of at least one row, and an inter-volume connection
opening SHALL be carved only on a real (non-open) wall and clear of the parent wall's
post, window, and door columns.

#### Scenario: A facade plan places a window
- **WHEN** a window candidate is selected
- **THEN** its along-wall coordinate SHALL be at least two cells away from both wall ends
- **AND** it SHALL NOT overlap the door bay, a post position, or an occluded interval.

#### Scenario: A short wing keeps its stone plinth
- **WHEN** `wall_frame` builds a wall whose height can carry a plinth
- **THEN** at least one stone plinth row SHALL be placed at its base.

#### Scenario: A connection opening avoids a timber post
- **WHEN** an inter-volume connection is carved into a parent wall
- **THEN** the opening SHALL be placed clear of the parent wall's post, window, and door columns, re-sealing any post column it must cross
- **AND** no connection SHALL be carved into an open shed that has no wall.
