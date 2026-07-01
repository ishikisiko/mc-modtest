# Chunky Acceptance Automation

## Purpose

This spec captures the staged Chunky/RCON acceptance workflow used to prepare in-game review worlds without making Chunky a MyVillage runtime dependency.

See also (narrative): [docs/ai-kb/17_chunky_acceptance.md](../../../docs/ai-kb/17_chunky_acceptance.md).

## Requirements

### Requirement: Chunky acceptance runs in explicit stages

The acceptance automation SHALL run Chunky in explicit stages. Stage 1 SHALL validate only the acceptance server lifecycle, RCON command channel, Chunky installation, Chunky task start/progress polling, timeout handling, clean save, and clean stop. Later stages SHALL NOT run until Stage 1 succeeds.

#### Scenario: Stage 1 succeeds before integration stages

- **WHEN** the Chunky acceptance workflow is run from a clean acceptance server profile
- **THEN** it SHALL start the server, connect by RCON, run a bounded `chunky start` task, poll `chunky progress` until completion, save the world, stop the server cleanly, and record Stage 1 as passed.
- **AND** only after Stage 1 passes SHALL the workflow continue to MyVillage command stages.

#### Scenario: Stage 1 failure blocks later stages

- **WHEN** the server fails to start, RCON cannot connect, Chunky is absent, or the Chunky task times out during Stage 1
- **THEN** the workflow SHALL mark Stage 1 failed
- **AND** it SHALL skip all MyVillage integration stages.

### Requirement: Acceptance server profile is isolated and disposable

The Chunky acceptance workflow SHALL use an isolated server profile directory separate from the normal development `run/` world. The profile SHALL own its own `server.properties`, `eula.txt`, `mods/`, world directory, logs, and RCON credentials. The workflow SHALL NOT require mutating the normal `run/server.properties`.

#### Scenario: Acceptance does not mutate the normal run profile

- **WHEN** the workflow prepares the acceptance server
- **THEN** it SHALL write server configuration under the acceptance profile directory
- **AND** it SHALL leave the normal `run/server.properties` RCON settings unchanged.

### Requirement: Chunky is an acceptance dependency only

Chunky SHALL be installed into the acceptance server profile for test execution. MyVillage SHALL NOT declare Chunky as a required or optional runtime dependency, and the MyVillage mod jar SHALL NOT bundle Chunky.

#### Scenario: MyVillage jar is built for users

- **WHEN** the MyVillage mod jar is built
- **THEN** Chunky SHALL NOT be packaged into that jar
- **AND** the mod metadata SHALL NOT require Chunky.

### Requirement: Full optional-mod acceptance uses staged external jars

The full optional-mod acceptance stage SHALL assemble the acceptance server from the staged runtime jars in `exmod/mod_jars.zip`, plus MyVillage and Chunky. Missing support dependencies from that staged set SHALL fail the stage during startup rather than silently falling back to vanilla.

#### Scenario: Full optional-mod server starts

- **WHEN** Stage 3 prepares the full optional-mod acceptance server
- **THEN** it SHALL extract the staged optional-mod jars and support dependency jars into the acceptance profile's `mods/` directory
- **AND** the server SHALL start with the full optional-mod registry present before MyVillage full-profile command cases run.

#### Scenario: A required support dependency is missing

- **WHEN** the full optional-mod server cannot start because a staged optional mod's dependency is missing
- **THEN** the workflow SHALL mark the full optional-mod stage failed
- **AND** it SHALL report the missing dependency or startup error in `reports/chunky_acceptance_report.json`.

### Requirement: Acceptance report is machine-readable

Every Chunky acceptance run SHALL write `reports/chunky_acceptance_report.json`. The report SHALL include the server profile path, world seed, Chunky jar identity, MyVillage artifact identity, stages executed, commands sent, start/end timestamps, durations, Chunky completion state, timeout state, MyVillage command results, and a log error summary.

#### Scenario: A successful run writes a report

- **WHEN** the Chunky acceptance workflow completes all requested stages
- **THEN** it SHALL write `reports/chunky_acceptance_report.json`
- **AND** the report SHALL mark each executed stage as passed or failed with supporting command/log evidence.

#### Scenario: A failed run writes a report

- **WHEN** the workflow fails before completing all requested stages
- **THEN** it SHALL still write `reports/chunky_acceptance_report.json`
- **AND** the report SHALL identify the failed stage and skip status for downstream stages.

### Requirement: Natural sect worldgen acceptance is bounded by located site

The natural sect worldgen stage SHALL use `/locate structure myvillage:sect` or equivalent server command output to identify a sect site, then run Chunky over a bounded area around that site. It SHALL NOT pre-generate the full region-runtime 4000-block graph radius as part of routine acceptance.

#### Scenario: A located sect is generated by Chunky

- **WHEN** the natural sect worldgen stage locates a `myvillage:sect` site
- **THEN** it SHALL run a bounded Chunky generation task centered on that site
- **AND** the report SHALL record the located coordinate, radius, Chunky completion state, and any server log errors during generation.

#### Scenario: No sect is locatable

- **WHEN** `/locate structure myvillage:sect` fails for the acceptance world seed and search constraints
- **THEN** the workflow SHALL mark the natural sect worldgen stage failed or skipped with reason `sect_not_located`
- **AND** it SHALL NOT attempt an unbounded Chunky generation sweep.
