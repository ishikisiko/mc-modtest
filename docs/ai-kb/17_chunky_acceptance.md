# Chunky Acceptance Automation

See-also specs: [`chunky-acceptance-automation`](../../openspec/changes/add-chunky-acceptance-automation/specs/chunky-acceptance-automation/spec.md), [`validation`](../../openspec/changes/add-chunky-acceptance-automation/specs/validation/spec.md).

Stages 1 through 4 are implemented. Stage 1 verifies the server lifecycle, Chunky installation, RCON command channel, bounded chunk generation, save, stop, and report writing. Stage 2 runs the coordinate-addressable MyVillage commands from RCON after Stage 1 passes. Stage 3 extracts the full optional-mod jar set, verifies expected mod ids plus mandatory jar dependencies, and then runs full-mod gallery/town/worldgen-sect cases if the preflight passes. Stage 4 locates a natural `myvillage:sect` and runs a bounded Chunky task around that site after Stage 3 passes.

Launch path:

```bash
python3 tools/run_chunky_acceptance.py --stage 1
python3 tools/run_chunky_acceptance.py --stage 2
python3 tools/run_chunky_acceptance.py --stage 3
python3 tools/run_chunky_acceptance.py --stage 4
```

The script uses `./gradlew --no-daemon runAcceptanceServer` as the primary launch path. That Gradle run uses the isolated `run-acceptance/` game directory, not the normal `run/` profile. A standalone NeoForge server profile remains the fallback path if later full optional-mod loading cannot be made reliable through the dev server run.

The script creates `run-acceptance/eula.txt`, `run-acceptance/server.properties`, `run-acceptance/mods/`, a dedicated world, a generated local RCON password, and `reports/chunky_acceptance_report.json`. It downloads the pinned NeoForge-compatible Chunky jar from the metadata in `tools/chunky_acceptance_metadata.json`, verifies its sha512, and copies it into the isolated profile's `mods/` directory.
For Stage 3, it also extracts `exmod/mod_jars.zip`, records jar filenames and hashes, checks the expected full optional-mod ids, and checks mandatory dependencies declared by the jars before server startup. If the staged zip lacks a required dependency jar, the report records `missing_mandatory_dependencies` and no server is launched.

Stage 1 runs:

```text
chunky world minecraft:overworld
chunky shape square
chunky center 0 0
chunky radius 64
chunky selection
chunky start
chunky progress
save-all
stop
```

Stage 2 then runs:

```text
myvillage list
myvillage placeat small_house_001 0 80 192
myvillage galleryat cultivation 256 80 192
myvillage townat 20260618 1024 80 192
myvillage sectat 20260618 -1024 80 192
myvillage sectat worldgen 20260618 none -1024 80 768
```

The acceptance profile sets `max-tick-time=-1` so long synchronous generation commands are not killed by the server watchdog during automation.
Stage 3 then runs:

```text
myvillage galleryat cultivation 1536 80 192
myvillage townat 20260618 2304 80 192
myvillage sectat worldgen 20260618 none 2304 80 768
```

The local staged `exmod/mod_jars.zip` must include `architectury` because
Fetzi's Displays declares it as mandatory. With
`architectury-13.0.8-neoforge.jar` present, the full Stage 4 run passed on
2026-06-30: Stage 3 loaded 10 jars with no missing dependency, and Stage 4
located `myvillage:sect` at `[5568, ~, 1888]`.
Stage 4 runs:

```text
locate structure myvillage:sect
chunky world minecraft:overworld
chunky shape square
chunky center <located_x> <located_z>
chunky radius 96
chunky selection
chunky start
chunky progress
```

If `/locate` cannot find a sect, the stage records `sect_not_located` and skips
the Chunky task. The radius is deliberately small and does not sweep the
4000-block region-runtime radius.
