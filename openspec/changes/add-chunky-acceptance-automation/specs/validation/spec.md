## ADDED Requirements

### Requirement: Acceptance prep can include staged Chunky automation

The staged acceptance checklist SHALL include an optional Chunky automation path after offline generation, validation, preview generation, and mod build. The Chunky path SHALL be documented as staged: first Chunky/RCON/server lifecycle validation, then coordinate-command integration, then full optional-mod MyVillage cases, then natural sect worldgen coverage.

#### Scenario: Acceptance checklist documents the staged order

- **WHEN** contributors prepare an in-game acceptance pass with Chunky automation
- **THEN** the documented checklist SHALL run the standalone Chunky/RCON/server lifecycle stage before any MyVillage command integration stage
- **AND** it SHALL identify later full optional-mod and natural sect worldgen stages separately.

### Requirement: Chunky acceptance report participates in validation handoff

When the Chunky acceptance workflow is run, `reports/chunky_acceptance_report.json` SHALL be treated as the handoff artifact for in-game automation status. The report SHALL NOT replace offline validator reports or the public preview server; it SHALL supplement them.

#### Scenario: Chunky report exists after an automated acceptance run

- **WHEN** the Chunky acceptance workflow is included in acceptance prep
- **THEN** the handoff SHALL include `reports/chunky_acceptance_report.json`
- **AND** the regular offline preview handoff SHALL still include the aggregate `out/preview/index.html` and running preview HTTP server.

### Requirement: Automated Chunky acceptance does not replace visual review

Passing Chunky acceptance SHALL mean that the configured server, Chunky pre-generation, coordinate commands, optional-mod registry, and targeted worldgen paths completed without detected automation failure. It SHALL NOT by itself mean the generated structures are visually accepted.

#### Scenario: Chunky acceptance passes

- **WHEN** every requested Chunky acceptance stage passes
- **THEN** the generated world SHALL be considered prepared for in-game visual review
- **AND** final appearance-sensitive acceptance SHALL still require reviewer inspection.
