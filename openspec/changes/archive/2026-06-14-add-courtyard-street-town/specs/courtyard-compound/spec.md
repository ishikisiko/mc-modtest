## ADDED Requirements

### Requirement: Small-courtyard unit layout
The compound layer SHALL provide a small-courtyard unit layout that produces a compact walled `CompoundGraph` reusing the existing parcel machinery and per-building pass pipeline. A small courtyard SHALL enclose two to four roster buildings around a single small 天井 with a four-sided `perimeter_wall` broken by exactly one gate, at a footprint smaller than the one-진 `chinese_courtyard` layout.

#### Scenario: A small courtyard is generated
- **WHEN** a small-courtyard unit is generated for a seed
- **THEN** the result SHALL be a `CompoundGraph` reusing `ParcelNode` and `BuildingSlot` parcel elements
- **AND** it SHALL enclose between two and four building slots around a single small 天井
- **AND** its `perimeter_wall` SHALL have exactly one gate opening.

#### Scenario: The small courtyard is more compact than the one-courtyard layout
- **WHEN** a small-courtyard unit and a one-진 `chinese_courtyard` compound are generated
- **THEN** the small courtyard's lot footprint SHALL be smaller than the one-courtyard layout's lot footprint.

#### Scenario: Small-courtyard buildings respect landscape and walls
- **WHEN** a small courtyard places its building slots, 天井, and perimeter wall
- **THEN** no building footprint SHALL overlap the 天井 landscape cells or the wall cells.
