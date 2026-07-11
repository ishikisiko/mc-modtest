## ADDED Requirements

### Requirement: Pagoda landmarks ship as three large deterministic profiles
The build system SHALL emit `pagoda_001..003` from three deterministic profile
definitions rather than random rolls of one massing. The profiles SHALL include
two five-storey towers and one seven-storey tower, SHALL use body footprints of
at least `15x15`, and SHALL preserve the existing structure ids and placement
roles.

#### Scenario: Pagoda library exposes three scale profiles
- **WHEN** the cultivation-town building library generates the pagoda family
- **THEN** it SHALL emit exactly `pagoda_001`, `pagoda_002`, and `pagoda_003`
- **AND** their profile signatures SHALL be pairwise distinct
- **AND** at least one profile SHALL have seven occupied storeys.

### Requirement: Every pagoda storey is legible in the exterior silhouette
Each pagoda profile SHALL taper through a non-decreasing inset schedule with at
least two real footprint reductions. Every boundary above an occupied storey
SHALL carry its own projecting eave band with bracket support and lifted corner
cues. The top storey SHALL terminate in a pyramidal crown and finial rather than
a second generic building volume.

#### Scenario: A five-storey pagoda has five roof rhythms
- **WHEN** a five-storey pagoda is generated
- **THEN** four intermediate eave levels SHALL be present below the crown
- **AND** the crown SHALL have a pyramidal roof skin and non-empty finial cells
- **AND** the reported inset schedule SHALL contain at least two increases.

### Requirement: Expanded pagodas remain contained by placement parcels
Python town/sect planning and Java runtime realization SHALL use measured
footprint bounds that contain each shipped pagoda resource. A parcel that
selects a pagoda SHALL NOT retain a smaller legacy bound that permits the
structure to overlap a neighboring parcel or terrace slot.

#### Scenario: Largest pagoda fits its mirrored footprint
- **WHEN** generated resource dimensions are compared with Python and Java
  pagoda footprint tables
- **THEN** every table entry SHALL be at least the corresponding NBT width and
  depth
- **AND** the town and sect placement validators SHALL pass without overlap.
