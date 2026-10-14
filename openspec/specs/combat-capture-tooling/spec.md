# combat-capture-tooling Specification

## Purpose
Define the in-repository workflow for collecting combat presentation evidence on a headless host and the boundary of that evidence. See-also `docs/ai-kb/34_combat_data_and_capture.md` and `tools/combat_capture/README.md`.

See also: [Spiritual Power and Sword Qi](../../../docs/ai-kb/45_spiritual_power_sword_qi.md).

See also: [Modal Test Runner](../../../docs/ai-kb/46_modal_test_runner.md).
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

### Requirement: Beast evidence is captured from server ticks
The tool SHALL provide a `beast` command that, for a beast id (default `myvillage:demon_wolf`), captures in-game evidence into `out/preview/<name>/ingame/` in six parts selectable with `--parts`: `idle` (stills from four sides and a scale still), `moves` (stills of every move at its key ticks taken with the world frozen (`/tick freeze`, `/tick step`) after starting the move with `/myvillage beast move`, plus the first grounded tick and two ticks later for a move whose lunge leaves the ground), `locomotion` (walk and run footage), `fight` (a recorded fight with the server's `BEAST_DEBUG` log and per-beast counts of moves, hits, damage, and staggers inside and outside immune windows), `dodge` (slowed standing-still, strafing, and early-swing trials), and `slowmo` (every move at a slowed tick rate with server ticks polled against video time). It SHALL read the moves and key ticks from the beast's data file, SHALL start and stop its own session unless one is running, and its page and manifest MUST NOT state or imply an owner verdict.

#### Scenario: Move stills follow the data file
- **WHEN** the beast command runs for `myvillage:demon_wolf`
- **THEN** each move still is labelled with its move id, key, and server tick taken from `data/myvillage/beast/demon_wolf.json`

#### Scenario: Re-running one part keeps the others
- **WHEN** the beast command runs with `--parts fight` against an existing output directory
- **THEN** the fight evidence is replaced and the other parts' entries stay on the page and in the manifest

### Requirement: NPC evidence is captured in game
The tool SHALL provide an `npc` command that, for an NPC id (default `myvillage:cultivator`), captures in-game evidence into `out/preview/<name>/ingame/` in two parts selectable with `--parts`: `idle` (full-figure stills from four sides with the world frozen, close-ups of the layered parts, and a scale still beside the player) and `walk` (footage of NPCs strolling in a barrier pen). It SHALL start and stop its own session unless one is running, and its page and manifest MUST NOT state or imply an owner verdict.

#### Scenario: Stills cover the layered parts
- **WHEN** the npc command runs for `myvillage:cultivator`
- **THEN** it writes stills from the front, side, and both three-quarter views and close-ups of the face, collar, belt, sleeve, hem, and back panel

### Requirement: Comparison and review page
The tool SHALL pair two capture sets by weapon, move, key, and view into before/after sheets and SHALL write a static review page under `out/preview/combat_capture/<label>/`. Its output is developer evidence and MUST NOT state or imply an owner verdict.

#### Scenario: Before and after are paired
- **WHEN** two capture sets of the same weapon are compared
- **THEN** the page shows each move and key with the two frames side by side and marks cells present in only one set

### Requirement: Sword-qi evidence uses real input and measured sessions
The sword-qi acceptance workflow SHALL avoid DevBridge and drive the Minecraft client through mapped press/release input for action evidence. RCON MAY prepare fixtures and read server results, but direct server invocation and pose probes SHALL NOT substitute for input evidence. Each measured run SHALL record UTC start/end, elapsed seconds, command, exit status, environment/cache context, available CPU time and peak RSS, and retain failed attempts and reruns. Setup, build, server readiness and client work SHALL remain distinguishable.

#### Scenario: A mapped release is verified
- **WHEN** the client releases use after a recorded charge
- **THEN** evidence SHALL correlate the input with server action/cost/hit output and label owner visual or feel judgment separately

### Requirement: Capture sessions expose bounded language and restart options
Capture sessions SHALL default to `en_us` and support explicit `zh_cn` for bilingual HUD evidence. A reuse-world option SHALL preserve only the isolated capture world; default startup SHALL retain its fresh-world behavior. Reuse SHALL fail before launch if the capture world is missing. Documentation SHALL require clean stage-1 preparation before capture persistence checks because both use `run-acceptance`; preserving the profile with `--no-clean` SHALL NOT count as the clean acceptance baseline. The journey mode SHALL exercise mapped dialogue, borrowing, manual study and use without granting membership, a manual or learned technique by command; an eligible-prospect setup SHALL be labelled as a fixture. HUD captures SHALL cover the three documented GUI sizes with idle and charge states.

#### Scenario: Chinese HUD is captured after a saved session
- **WHEN** a new session starts with the isolated world reuse option and explicit `zh_cn`
- **THEN** it SHALL retain that capture world, select Chinese, and leave unrelated worlds untouched

#### Scenario: Journey evidence begins from a fixture
- **WHEN** the journey prepares an eligible prospect before mapped joining and study
- **THEN** its manifest SHALL state that preparation and SHALL NOT claim a complete from-new-character progression run

#### Scenario: A preparation step removed the saved capture world
- **WHEN** reuse is requested after clean stage-1 preparation deleted the shared profile
- **THEN** capture SHALL fail with a missing-world diagnostic before starting the server
- **AND** evidence SHALL NOT present the new world's defaults as a product persistence failure or successful same-world restart

### Requirement: Remote capture keeps evidence and world isolation boundaries
A Modal client/capture run SHALL reuse the repository's mapped-input evidence workflow without DevBridge and SHALL keep all world state in an ephemeral run workspace, outside the persistent dependency cache. Remote client capture SHALL be advertised as experimental until validated in a real authenticated Sandbox. Software-rendered output SHALL remain developer evidence and SHALL NOT establish owner physical-client sound, FPS or look/feel acceptance.

#### Scenario: Remote client startup succeeds without mapped actions
- **WHEN** a remote Minecraft client reaches readiness but mapped capture has not been exercised
- **THEN** the ledger SHALL record startup only and leave interaction/capture unverified

#### Scenario: A run finishes after modifying a capture world
- **WHEN** remote capture cleanup terminates its Sandbox
- **THEN** that run's world SHALL NOT be retained in the dependency cache or reused as a later run's starting world

### Requirement: Natural monster evidence is an explicit capture option
Capture sessions SHALL disable natural monsters by default and SHALL offer an explicit `--natural-monsters` option that enables the isolated server's monster spawning setting. The session state SHALL record the option, and stopping SHALL restore the prior server properties. The wilderness evidence script SHALL enable this option before claiming observed natural births; a game-rule change alone MUST NOT be treated as enabling a disabled server setting.

#### Scenario: Existing capture behavior
- **WHEN** a capture session is started without the option
- **THEN** natural monster spawning remains disabled

#### Scenario: Wilderness trial
- **WHEN** a wilderness evidence session explicitly enables natural monsters and its trial enables the spawning game rule
- **THEN** the normal server spawn path can produce entities, with observed births distinguished from administrator fixtures
