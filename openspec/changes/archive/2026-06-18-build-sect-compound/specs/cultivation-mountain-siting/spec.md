## ADDED Requirements

### Requirement: The siting requirements bind to a realized terraced compound

The compound layer's terrace hierarchy, ritual axis, covered-gallery/flying-bridge links, and siting context SHALL be realized by a concrete sect-compound assembler, not only described. The assembler SHALL expose a default terrace skeleton (gate / disciple / assembly / scripture / summit, count parametric 4–6) and the geometry parameters terrace rise, terrace depth, terrace width taper, axis-stair width, and cliff-back height, so that downstream worldgen can derive terrain from the same parameters.

#### Scenario: The abstract siting is realized, not just specified

- **WHEN** the `cultivation_sect` group is built
- **THEN** the terrace hierarchy, ground-to-summit ritual axis, importance-by-terrace grading, covered galleries, and cliff-back placement SHALL be produced by the realized compound
- **AND** the compound SHALL expose its terrace skeleton and geometry parameters for downstream consumption.

#### Scenario: Links are realized circulation, not decoration

- **WHEN** the compound places a covered gallery or the flying-bridge feature
- **THEN** each SHALL be realized as a circulation/structure link with both endpoints resting on the volumes or terraces it joins
- **AND** SHALL NOT be emitted as a decoration patch.
