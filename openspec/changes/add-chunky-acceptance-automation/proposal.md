## Why

The current acceptance flow stops at offline validators, generated previews, and a manually launched in-game review. That leaves the most failure-prone runtime path - launching a full optional-mod server, generating chunks, and running `/myvillage` commands against real world terrain - dependent on manual setup.

Chunky can automate the chunk-generation side of that review, but it must be introduced in stages: first prove that the test server, Chunky, RCON, and progress detection are reliable by themselves, then layer the MyVillage command and full optional-mod acceptance cases on top.

## What Changes

- Add a staged Chunky acceptance workflow:
  - Stage 1 proves the standalone Chunky/RCON/server lifecycle using a small generated region and no MyVillage command assertions beyond server startup.
  - Stage 2 adds coordinate-addressable `/myvillage ...at` commands so RCON can drive acceptance without a player entity.
  - Stage 3 runs full optional-mod acceptance cases using the external mod jars staged in `exmod/mod_jars.zip`.
  - Stage 4 adds natural `myvillage:sect` worldgen coverage by locating a sect and pre-generating the surrounding chunks with Chunky.
- Add coordinate debug commands that mirror existing player-position commands:
  - `/myvillage placeat <structure_id> <x> <y> <z>`
  - `/myvillage galleryat <all|original|cultivation> <x> <y> <z>`
  - `/myvillage townat <seed> <x> <y> <z>`
  - `/myvillage sectat <seed> <x> <y> <z>`
  - `/myvillage sectat worldgen <seed> <variant|none> <x> <y> <z>`
- Add an acceptance server profile that assembles a temporary full-modset server with MyVillage, Chunky, and the optional decor/runtime dependency jars.
- Add a report artifact, `reports/chunky_acceptance_report.json`, summarizing stages, commands, Chunky version, world seed, timing, completion status, and log errors.
- Update acceptance documentation so Chunky automation is a staged handoff path, while final visual acceptance remains a reviewer decision rather than a claim of full automation.
- No breaking changes to existing `/myvillage place`, `/myvillage gallery`, `/myvillage town`, or `/myvillage sect` commands.

## Capabilities

### New Capabilities

- `chunky-acceptance-automation`: staged in-game acceptance automation using Chunky, RCON, an isolated server profile, full optional-mod jars, and a machine-readable acceptance report.

### Modified Capabilities

- `resource-export`: add coordinate-addressable debug commands for template placement and gallery placement so generated resources can be reviewed from RCON without a player.
- `town-realization`: add `/myvillage townat <seed> <x> <y> <z>` with the same planning, force-loading, fallback, and determinism contracts as `/myvillage town <seed>`, but anchored at an explicit coordinate.
- `sect-compound-realization`: add `/myvillage sectat <seed> <x> <y> <z>` with the same live-terrain sect realization contracts as `/myvillage sect <seed>`, but anchored at an explicit coordinate.
- `sect-worldgen-structure`: add coordinate-addressable force generation for worldgen-style sect review through `/myvillage sectat worldgen <seed> <variant|none> <x> <y> <z>`.
- `validation`: extend staged acceptance preparation to include the optional Chunky automation path, staged pass criteria, and the generated acceptance report.

## Impact

- Java command surface: `MyVillageMod`, `TownGenerator`, and `SectGenerator` need player-position entrypoints split from coordinate-based core methods.
- Acceptance tooling: new scripts or Gradle tasks will assemble an isolated test server, extract `exmod/mod_jars.zip`, install Chunky, enable RCON, send commands, poll progress, collect logs, and write `reports/chunky_acceptance_report.json`.
- Runtime dependencies: Chunky is an acceptance/test-server dependency only; it is not packed into the MyVillage mod jar. The full optional-mod server uses the staged optional jars plus their dependency jars from `exmod/mod_jars.zip`.
- Documentation: update `README.md`, `AGENTS.md`, `docs/ai-kb/09_validation_checklist.md`, and relevant specs with the staged flow and new coordinate commands.
- Versioning: this is a feature change and must include the coordinated version bump and changelog update required by `openspec/config.yaml`.
