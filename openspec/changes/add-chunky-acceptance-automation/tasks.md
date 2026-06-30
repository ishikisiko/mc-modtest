## 1. Stage 1: Chunky/RCON/server lifecycle smoke

- [x] 1.1 Decide and document the acceptance server launch path (`./gradlew runServer` first; standalone NeoForge server fallback if external jars cannot load cleanly).
- [x] 1.2 Add an isolated acceptance server profile builder that creates a disposable profile directory with `eula.txt`, `server.properties`, dedicated world name, fixed seed, RCON enabled, and a generated local RCON password without mutating `run/server.properties`.
- [x] 1.3 Add Chunky jar provisioning for the acceptance profile, including pinned version/checksum metadata and clear failure output if the jar is missing or cannot be fetched/provided.
- [x] 1.4 Add a scriptable RCON command runner or local RCON client wrapper that can send commands, capture responses, and fail with useful diagnostics on connection/auth/timeouts.
- [x] 1.5 Implement Stage 1 only: start the acceptance server, wait for ready, run a small bounded `chunky start` task, poll `chunky progress`, run `save-all`, stop the server, and write `reports/chunky_acceptance_report.json`.
- [x] 1.6 Run Stage 1 from a clean profile and confirm it passes before starting MyVillage command integration work.

## 2. Coordinate command surface

- [x] 2.1 Refactor `/myvillage place` so player-position lookup is a thin wrapper over a coordinate-based placement core.
- [x] 2.2 Add `/myvillage placeat <structure_id> <x> <y> <z>` and verify it preserves existing template lookup, runtime fallback resolution, success/failure messages, and non-test Y offset behavior.
- [x] 2.3 Refactor `/myvillage gallery` so player-position lookup is a thin wrapper over a coordinate-based gallery core.
- [x] 2.4 Add `/myvillage galleryat <all|original|cultivation> <x> <y> <z>` and verify it preserves grouping, filtering, ordering, spacing, and fallback resolution.
- [x] 2.5 Refactor `TownGenerator.generate` so player-position lookup is a thin wrapper over `generateAt(source, seed, BlockPos)`.
- [x] 2.6 Add `/myvillage townat <seed> <x> <y> <z>` and verify it can run from server console/RCON without `ServerPlayer`.
- [x] 2.7 Refactor `SectGenerator.generate` and `SectGenerator.generateForced` so player-position lookup is a thin wrapper over coordinate-based live-terrain and worldgen-style cores.
- [x] 2.8 Add `/myvillage sectat <seed> <x> <y> <z>` and `/myvillage sectat worldgen <seed> <variant|none> <x> <y> <z>`.
- [x] 2.9 Add focused tests or automated command smoke coverage proving the `...at` commands do not throw `ServerPlayer` errors when run without a player.

## 3. Stage 2: MyVillage command automation

- [x] 3.1 Extend the acceptance workflow with Stage 2 that runs `myvillage list`, `placeat`, `galleryat cultivation`, `townat`, `sectat`, and `sectat worldgen` from RCON against bounded coordinates.
- [x] 3.2 Teach the report writer to record every MyVillage command, response, expected success marker, and failed/skipped status.
- [x] 3.3 Run Stage 1 + Stage 2 on a minimal acceptance server and confirm Stage 2 does not start if Stage 1 fails.
- [x] 3.4 Add timeout and failure handling for long-running `townat` / `sectat worldgen` commands so the server is saved/stopped cleanly on failure.

## 4. Stage 3: Full optional-mod acceptance

- [ ] 4.1 Extend the profile builder to extract `exmod/mod_jars.zip` into the acceptance profile's `mods/` directory for full-modset runs.
- [ ] 4.2 Verify the extracted full optional-mod jars include the confirmed runtime mods and support dependencies: Ars Nouveau, Farmer's Delight, Supplementaries, Fetzi's Displays, Macaw's Furniture, Macaw's Windows, Moonlight, Curios, and GeckoLib.
- [ ] 4.3 Add startup diagnostics that fail Stage 3 clearly when an optional mod or support dependency is missing or incompatible.
- [ ] 4.4 Add Stage 3 command cases that place a static gallery, a town, and a worldgen-style sect in the full optional-mod server.
- [ ] 4.5 Add report fields that confirm full-modset mode, extracted jar filenames, and whether optional-mod registry startup completed.
- [ ] 4.6 Run Stage 1 + Stage 2 + Stage 3 and confirm authored optional-mod blocks remain registry-present in the full server path.

## 5. Stage 4: Natural sect worldgen with Chunky

- [ ] 5.1 Add an acceptance command step that runs `/locate structure myvillage:sect` and parses the located coordinate or records `sect_not_located`.
- [ ] 5.2 Add a bounded Chunky generation task centered on the located sect site; keep the radius small enough for routine acceptance and do not sweep the 4000-block region-runtime radius.
- [ ] 5.3 Parse logs and command responses for chunk-generation errors, crashes, stalls, and timeout state during the natural sect worldgen pass.
- [ ] 5.4 Record the located coordinate, Chunky center/radius, completion state, and log summary in `reports/chunky_acceptance_report.json`.
- [ ] 5.5 Run Stage 4 after Stage 3 passes and confirm it is skipped or failed with a clear reason when no sect is locatable.

## 6. Documentation and acceptance checklist

- [x] 6.1 Update `README.md` command documentation with the new `/myvillage placeat`, `galleryat`, `townat`, and `sectat` commands, including RCON/automation examples.
- [x] 6.2 Update `docs/ai-kb/09_validation_checklist.md` with the staged Chunky acceptance flow and make Stage 1 the documented prerequisite for later Chunky integration stages.
- [x] 6.3 Update `AGENTS.md` acceptance guidance to mention the Chunky automation handoff, full optional-mod server profile, and generated report.
- [x] 6.4 Add or update `docs/ai-kb/` documentation for Chunky acceptance automation and list it in `docs/ai-kb/INDEX.md` with see-also links to the new `chunky-acceptance-automation` spec and validation checklist.
- [x] 6.5 Keep the offline preview handoff documented as still required: aggregate `out/preview/index.html`, served HTTP review URL, and reviewer visual sign-off remain separate from Chunky pass/fail.

## 7. Validation, build, and release metadata

- [x] 7.1 Run OpenSpec validation/status checks for `add-chunky-acceptance-automation` and fix any proposal/spec/task format issues.
- [ ] 7.2 Run the existing automated validation suite relevant to command and generator changes: `./gradlew test`, Python validators, and the acceptance checklist items that are practical for the change.
- [ ] 7.3 Build the mod jar and confirm Chunky is not packaged into the MyVillage jar and is not declared as a MyVillage dependency.
- [ ] 7.4 Run the final staged Chunky acceptance flow through the requested stages and attach/report `reports/chunky_acceptance_report.json`.
- [ ] 7.5 Bump the mod version as a feature change and update the four required files together per `openspec/config.yaml`: `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md`.
