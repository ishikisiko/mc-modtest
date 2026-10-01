# combat-capture-tooling Specification

## Purpose
Define the in-repository workflow for collecting combat presentation evidence on a headless host and the boundary of that evidence. See-also `docs/ai-kb/34_combat_data_and_capture.md` and `tools/combat_capture/README.md`.

## Requirements
### Requirement: Capture tooling lives in the repository
The repository SHALL provide `tools/combat_capture/` for collecting combat presentation evidence on a headless host. It SHALL use only the Python standard library and the host programs `Xvfb`, `xdotool`, `ffmpeg`, and ImageMagick, SHALL drive the repository's own acceptance-server and smoke-client run tasks, and MUST NOT depend on files outside the repository.

#### Scenario: Missing host program is reported
- **WHEN** a required host program is not installed
- **THEN** the tool exits before starting any process and names the missing program

### Requirement: Session control is bounded and clean
The tool SHALL start and stop the acceptance server and one smoke client, wait for readiness with timeouts, and stop only the processes it started, by process id. It SHALL hold the shared heavy-work lock for as long as its server or client runs.

#### Scenario: Cleanup leaves no process behind
- **WHEN** a capture session ends normally or by error
- **THEN** the server and client processes the tool started have exited and the lock is released

### Requirement: Hot reload applies data edits without a rebuild
The tool SHALL copy edited combat client resources into the running client's resource output, trigger a client resource reload, and wait for the rig-loaded log line before continuing.

#### Scenario: Rig edit is visible after reload
- **WHEN** a developer edits a first-person rig and runs the reload command against a running session
- **THEN** the client logs the rig load and the next frozen frame shows the edited pose

### Requirement: Per-move stills use the freeze probes
For a registered weapon the tool SHALL capture first-person and third-person stills of every move at its key ticks (idle, strike start, contact, strike end, recovery) using the first-person and third-person smoke probes, reading the move list from the style file and the key ticks from the rig. Each still SHALL be taken only after the frame is stable, and each SHALL be labelled with weapon, move, key, tick, and view.

#### Scenario: Stills cover every move
- **WHEN** the capture command runs for `myvillage:qingfeng_sword`
- **THEN** it writes one labelled sheet per view with a row for each of the five moves and a manifest listing every frame's move and tick

### Requirement: Comparison and review page
The tool SHALL pair two capture sets by weapon, move, key, and view into before/after sheets and SHALL write a static review page under `out/preview/combat_capture/<label>/`. Its output is developer evidence and MUST NOT state or imply an owner verdict.

#### Scenario: Before and after are paired
- **WHEN** two capture sets of the same weapon are compared
- **THEN** the page shows each move and key with the two frames side by side and marks cells present in only one set

