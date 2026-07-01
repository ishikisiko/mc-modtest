## Context

The repository already has a strong offline acceptance surface: generators, validators, static PNG previews, interactive HTML viewers, and a Gradle build that packs `myvillage` resources. The missing part is repeatable **in-game** acceptance. Today a reviewer still has to launch a game/server, install optional mods, place structures, generate towns/sects, and manually notice whether chunk generation stalls, optional-mod blocks disappear, or worldgen sects join incorrectly across chunk boundaries.

Chunky is a good fit for the chunk-generation part because it delegates real chunk generation to the running server. That is exactly the path we need to exercise for `myvillage:sect` worldgen and for a full optional-mod server. But Chunky itself introduces operational complexity: a server profile, jar installation, RCON control, progress polling, timeout handling, and log parsing. Those concerns should be proven before they are mixed with MyVillage command acceptance.

Two existing constraints shape the design:

1. Most `/myvillage` review commands currently anchor at `ServerPlayer.blockPosition()`. RCON has no player, so a reliable automation flow needs coordinate-addressable commands.
2. The shipped tree is a `full` modset by default, while optional external mods remain optional at runtime. The full acceptance server should load the staged jars from `exmod/mod_jars.zip` so authored external-mod blocks are placed unchanged, not only fallback-tested.

## Goals / Non-Goals

**Goals:**

- Add an isolated, repeatable Chunky acceptance workflow that can run without a human player.
- Split acceptance into explicit phases, with Stage 1 proving the Chunky/RCON/server lifecycle before any MyVillage command automation.
- Add coordinate-addressable `/myvillage ...at` commands that mirror the existing player-position commands.
- Run acceptance in a full optional-mod environment using the staged external mod jars plus Chunky.
- Produce a machine-readable `reports/chunky_acceptance_report.json` for every run.
- Keep existing player-facing debug commands unchanged.

**Non-Goals:**

- Do not make Chunky a runtime dependency of the MyVillage mod jar.
- Do not replace offline validators or HTML previews.
- Do not claim automated visual acceptance is complete; the generated world remains a review artifact.
- Do not add passive town worldgen. `/myvillage townat` is still an explicit command.
- Do not use Chunky to compensate for incorrect chunk-generation algorithms. It is an acceptance driver, not a workaround for generator bugs.

## Decisions

### D1: Stage Chunky before MyVillage integration

The workflow is split into gates:

```
Stage 1: server + Chunky + RCON smoke
  - assemble isolated server profile
  - install Chunky
  - enable RCON
  - run chunky start/progress on a small square
  - stop cleanly and write a report

Stage 2: coordinate MyVillage commands
  - add /myvillage placeat, galleryat, townat, sectat
  - prove commands run from RCON without a player

Stage 3: full optional-mod acceptance
  - extract exmod/mod_jars.zip into test mods/
  - verify optional mod ids stay registry-present
  - run static gallery, townat, and sectat cases

Stage 4: natural sect worldgen acceptance
  - locate myvillage:sect
  - Chunky-generate a bounded area around the located site
  - inspect logs/report for stalls or generation errors
```

Rationale: when a run fails, the failing layer should be obvious. A broken Chunky install should not be confused with a broken `/myvillage townat`; an optional-mod dependency failure should not be confused with a sect worldgen regression.

Alternative considered: implement one all-in-one acceptance command. Rejected because it would hide failure causes and make the first implementation hard to debug.

### D2: Use an isolated acceptance server profile

The workflow should build or prepare a separate directory such as `run-acceptance/`, not mutate the normal `run/world`. The profile owns its own `server.properties`, `eula.txt`, `mods/`, `world/`, logs, and temporary RCON password. It may be deleted and recreated between runs.

Rationale: acceptance should be reproducible and disposable. The existing `run/server.properties` has `enable-rcon=false`; changing it in-place would make ordinary local runs less predictable.

Alternative considered: reuse `run/`. Rejected because old worlds, old configs, or a stale mod list would make reports ambiguous.

### D3: Prefer RCON over a fake player or interactive console

The orchestration script should control the server through RCON. RCON can run Chunky commands, vanilla commands, `/locate`, `save-all`, and `stop` without a client. It also keeps the server process non-interactive and scriptable.

Rationale: a fake player or test client would add another mod/dependency layer. Console stdin control is possible but less structured and harder to parallel with progress polling.

Alternative considered: run commands through stdin. Rejected as the primary path because parsing command responses from logs is weaker than a request/response RCON channel.

### D4: Split player-position commands from coordinate core functions

Existing commands remain player-oriented wrappers. New `...at` commands call the same underlying placement/generation core with an explicit `BlockPos`.

```
/myvillage town <seed>
  -> player.blockPosition()
  -> generateTownAt(source, seed, pos)

/myvillage townat <seed> <x> <y> <z>
  -> explicit BlockPos
  -> generateTownAt(source, seed, pos)
```

The same pattern applies to `place`, `gallery`, `sect`, and force-generated worldgen-style sects.

Rationale: the coordinate commands must not fork behavior. They should prove the same realizer that the player command uses.

Alternative considered: spawn or teleport a bot/player. Rejected because the generator already supports coordinate math internally; the missing piece is command surface, not simulation.

### D5: Treat Chunky as an acceptance dependency only

Chunky is installed into the acceptance server's `mods/` directory. It is not declared as a MyVillage dependency and not packed into the MyVillage jar. The acceptance script should pin the Chunky jar version or checksum so the workflow is reproducible.

Rationale: Chunky is only needed to exercise chunk generation during review. Users of the mod should not need Chunky.

Alternative considered: declare Chunky as optional dependency. Rejected because MyVillage does not call Chunky APIs and should not imply runtime integration.

### D6: Full optional-mod server is assembled from staged jars

Stage 3 extracts all jars in `exmod/mod_jars.zip` into the acceptance server's `mods/` directory, together with MyVillage and Chunky. This zip already carries the confirmed optional mods and support dependencies: Ars Nouveau, Farmer's Delight, Supplementaries, Fetzi's Displays, Macaw's Furniture, Macaw's Windows, Moonlight, Curios, and GeckoLib.

Rationale: the full profile should test the actual authored external-mod blocks. A vanilla/fallback-only server would miss registry, dependency, and blockstate integration failures.

Alternative considered: download optional mods during the run. Rejected for the first implementation because the repository already has staged jars, and network downloads make acceptance less repeatable.

### D7: Report structured outcomes, not just logs

Every run writes `reports/chunky_acceptance_report.json` with:

- tool versions and jar filenames/checksums where practical
- world seed and server profile path
- stages executed, commands sent, start/end timestamps, and durations
- Chunky regions, radii, completion state, and timeout state
- MyVillage command results and expected success markers
- log error/warn summary, including crash indicators

Rationale: generated reports are git-ignored and already used for deterministic validator output. A structured report gives future automation and reviewers a stable artifact without requiring them to read a whole server log.

Alternative considered: only keep `latest.log`. Rejected because logs are too noisy and too implementation-specific for acceptance status.

## Risks / Trade-offs

- **Chunky or NeoForge server startup fails before MyVillage is tested** -> Stage 1 isolates this and reports it as an environment/tooling failure.
- **RCON command responses vary by mod/version** -> treat log markers and timeout state as secondary evidence, and keep the Chunky version pinned.
- **Optional mod dependencies are incomplete** -> Stage 3 should fail during server startup with a clear missing-mod summary before running MyVillage cases.
- **Chunky pre-generation takes too long** -> keep bounded radii for acceptance cases, use per-stage timeouts, and skip large 4000-block region sweeps.
- **Coordinate commands drift from player commands** -> implement `...at` commands as thin wrappers over shared core methods and add command tests or acceptance cases covering both paths where practical.
- **Automated world review is mistaken for visual sign-off** -> docs and specs must state that automation prepares and proves the review world; final appearance acceptance still belongs to the reviewer.

## Migration Plan

1. Add the coordinate command core/wrappers while preserving existing commands.
2. Add Stage 1 acceptance tooling and prove Chunky/RCON/server lifecycle in isolation.
3. Add Stage 2 command automation against a minimal server.
4. Add Stage 3 full optional-mod server assembly from `exmod/mod_jars.zip`.
5. Add Stage 4 natural sect worldgen coverage.
6. Update README, AGENTS, validation checklist, specs, changelog, and version.

Rollback is straightforward: the existing player commands and offline validation chain remain unchanged. If the Chunky flow is unstable, it can be omitted from acceptance prep while the `...at` commands remain useful for manual operator review.

## Open Questions

- Should Stage 1 install only Chunky + MyVillage, or Chunky alone plus NeoForge for the narrowest possible smoke? Default proposal: MyVillage included, but no MyVillage commands asserted, so modpack startup is still represented.
- Should the first implementation use `./gradlew runServer` or a standalone NeoForge server assembled from the built jar? Default proposal: try the repo's dev server path first; fall back to standalone if external jars do not load cleanly in the dev run.
- Which Chunky jar checksum should be pinned in the repo docs? Default proposal: pin the current NeoForge 1.21.1-compatible jar discovered during implementation.
