# Chunky Acceptance Automation

See-also specs: [`chunky-acceptance-automation`](../../openspec/changes/add-chunky-acceptance-automation/specs/chunky-acceptance-automation/spec.md), [`validation`](../../openspec/changes/add-chunky-acceptance-automation/specs/validation/spec.md).

Stages 1 and 2 are implemented. Stage 1 verifies the server lifecycle, Chunky installation, RCON command channel, bounded chunk generation, save, stop, and report writing. Stage 2 runs the coordinate-addressable MyVillage commands from RCON after Stage 1 passes.

Launch path:

```bash
python3 tools/run_chunky_acceptance.py --stage 1
python3 tools/run_chunky_acceptance.py --stage 2
```

The script uses `./gradlew --no-daemon runAcceptanceServer` as the primary launch path. That Gradle run uses the isolated `run-acceptance/` game directory, not the normal `run/` profile. A standalone NeoForge server profile remains the fallback path if later full optional-mod loading cannot be made reliable through the dev server run.

The script creates `run-acceptance/eula.txt`, `run-acceptance/server.properties`, `run-acceptance/mods/`, a dedicated world, a generated local RCON password, and `reports/chunky_acceptance_report.json`. It downloads the pinned NeoForge-compatible Chunky jar from the metadata in `tools/chunky_acceptance_metadata.json`, verifies its sha512, and copies it into the isolated profile's `mods/` directory.

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

The acceptance profile sets `max-tick-time=-1` so long synchronous generation commands are not killed by the server watchdog during automation. Later full optional-mod and natural sect worldgen stages remain pending.
