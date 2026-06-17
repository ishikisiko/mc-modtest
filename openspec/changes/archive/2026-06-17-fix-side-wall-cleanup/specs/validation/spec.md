## MODIFIED Requirements

### Requirement: Build quality check gates export
Generated buildings SHALL pass the build quality check before they are exported into the
building library. The quality check SHALL additionally gate on side-wall integrity: it
SHALL fail a building whose closed-volume wall plane has an unplanned hole between the
foundation top and the roofline (not a door, window, or connection opening), and it
SHALL fail a building that places an interior or protected block in a different volume's
exterior wall plane. These checks SHALL inspect the actual wall plane, not only the
cells a roof op recorded placing.

#### Scenario: A generated building has no door
- **WHEN** quality checking sees no door blockstate
- **THEN** the building SHALL fail with a `no_entrance` error
- **AND** the generator MAY retry with another deterministic seed attempt before giving up.

#### Scenario: A side wall has an unplanned hole
- **WHEN** quality checking finds an air cell in a closed volume's wall plane that is not a planned opening
- **THEN** the building SHALL fail with an `open_side_wall` error reporting the offending coordinate.

#### Scenario: Interior furniture sits on a neighbor's exterior wall
- **WHEN** quality checking finds an `INTERIOR`/`PROTECTED` non-opening block in a different volume's exterior wall plane
- **THEN** the building SHALL fail with a `furniture_on_wall` error.
