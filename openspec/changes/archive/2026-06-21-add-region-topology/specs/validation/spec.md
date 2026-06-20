## ADDED Requirements

### Requirement: Region topology validation checks structural invariants and determinism

The region-topology validator SHALL check generated region graphs for structural invariants: the region count is within 5–7 inclusive; exactly one anchor region exists and is centered; the 连-subgraph connects every non-walled region to the anchor; every 连 edge respects the tier-step limit N = 5; the anchor holds the highest tier; each 隔 edge carries a separator type from the legal palette {特殊山脉, 特殊海洋}; each `walled` region has at most one 连 (关隘) edge with all others 隔; and the same seed reproduces an identical graph. A multi-seed survey SHALL confirm these hold across seeds and report count distribution, connectivity, tier spread, and walled-region presence.

#### Scenario: A generated region graph is validated

- **WHEN** `tools/validate_region_topology.py` succeeds
- **THEN** several seeded region graphs SHALL satisfy the count, single-centered-anchor, 连-connectivity, tier-step, anchor-top-tier, separator-palette, and walled-region invariants
- **AND** a deliberately broken graph SHALL fail with the offending invariant named.

#### Scenario: Determinism is checked

- **WHEN** the validator generates the same seed twice
- **THEN** the two region graphs SHALL be identical
- **AND** validation SHALL fail with a determinism error if they differ.

#### Scenario: A tier-step violation is rejected

- **WHEN** a 连 edge joins two regions whose tier difference exceeds 5
- **THEN** validation SHALL fail naming the offending edge.

#### Scenario: A disconnected region is rejected

- **WHEN** a non-walled region is not reachable from the anchor through 连 edges
- **THEN** validation SHALL fail with a connectivity error naming the unreachable region.
