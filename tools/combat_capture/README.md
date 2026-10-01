# Combat capture tooling

Collects combat presentation evidence on a headless host: per-move stills from
the client freeze probes, a mapped-click combo run with video and server-side
target health, before/after comparison sheets, and a static review page.
Output is developer evidence only; it never records an owner verdict.

Run everything from the repository root:

```bash
python3 -m tools.combat_capture <command> [options]
```

Standard library only. Host programs: `Xvfb`, `xdotool`, `ffmpeg`, and
ImageMagick `import`, `convert`, `montage` (fonts: DejaVu Sans). Every command
that needs them checks first and exits naming any that is missing. The build
needs the untracked `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar` at the
repository root (in a worktree, symlink it from the main checkout).

## Commands

| Command | What it does |
|---|---|
| `run --label L [--weapon ID]` | Whole pass: session start, stills, combo, session stop, sheets and page. Stops the session on any error and keeps partial output. |
| `session start` / `status` / `stop` | Start Xvfb, the acceptance server and one smoke client under a supervisor that holds the heavy-work lock; show state; stop only what it started. |
| `stills --label L` | First-person and F5 back/front stills of every move at its key ticks, one sheet per view, `manifest.json`, `index.html`. Needs a running session. |
| `combo --label L [--targets dummy\|golem]` | Targets in front of the player, real mapped left clicks through the combo at chain timing, mp4, target health before and after. Adds to an existing capture set with the same label. |
| `reload [--src DIR] [--paths GLOB...]` | Copy changed combat client resources into `build/resources/main`, press F3+T, wait for the rig-loaded log line. |
| `compare A B --label L` | Pair two capture sets by weapon, move, key and view into side-by-side sheets and a page; cells present in one set only are marked `[UNPAIRED]`. |
| `page DIR` | Rebuild the sheets, combo strip and `index.html` of a capture directory. |
| `scene stills\|combo` | Set up the scene only (for manual looks). |
| `rcon CMD...`, `ui-state` | Server commands in the session; whether the client is in game with no screen open. |
| `check`, `ticks` | Host programs and the weapon's data; the moves and key ticks (light, no processes). |

Typical use:

```bash
python3 -m tools.combat_capture run --label before-0.27.1-fix1      # about 6-8 min once the lock is free
# ... change code, rebuild nothing: run again with another label ...
python3 -m tools.combat_capture run --label after-xyz
python3 -m tools.combat_capture compare out/preview/combat_capture/before-0.27.1-fix1 \
    out/preview/combat_capture/after-xyz --label before-vs-after
```

Tuning loop for a first-person rig (no rebuild):

```bash
python3 -m tools.combat_capture session start        # run it in the background; it waits for the lock
python3 -m tools.combat_capture stills --label rig-a --views fp --moves 2
# edit src/main/resources/assets/myvillage/combat/qingfeng_first_person.json
python3 -m tools.combat_capture reload
python3 -m tools.combat_capture stills --label rig-b --views fp --moves 2
python3 -m tools.combat_capture compare out/preview/combat_capture/rig-a out/preview/combat_capture/rig-b --label rig-a-vs-b
python3 -m tools.combat_capture session stop
```

## What it reads

The weapon id resolves through `data/<ns>/combat/index.json` to
`data/<ns>/combat/weapon/<path>.json`, its style
`data/<ns>/combat/style/<path>.json` (the ordered move list) and its
`first_person_rig` (`assets/<ns>/<path>`, the key ticks), all under
`src/main/resources`. Key ticks per move: `idle` 0, `strike_start` =
`strike[0]`, `contact`, `strike_end` = `strike[1]`, `recovery` = the last key
before the final neutral key.

## Session

- One supervisor process (`run-combat-capture/logs/supervisor.log`) takes the
  heavy-work lock with `flock`, then starts `Xvfb :<free> 960x540x24 +extension GLX`,
  `./gradlew runAcceptanceServer`, and
  `./gradlew runClient -Pcombat_smoke_server=127.0.0.1:<port> -Pcombat_smoke_game_dir=run-combat-capture/client -Pcombat_smoke_username=CaptureDev`.
  Both Gradle runs use `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1g` and
  `-x generateAllStructures` (that task rewrites tracked files and is not needed to run).
  The lock is held until every child has exited; the Gradle wrappers inherit
  the lock descriptor, so it also stays held if the supervisor itself is killed.
- Lock file: `$MC_HEAVY_LOCK`, else `.mc-heavy.lock` in the directory that
  holds the main checkout (worktrees resolve to the same file).
- Waits, each with a timeout: lock (90 min), server rcon answering (15 min,
  includes the Gradle build), player joined (15 min), client in game (4 min),
  then 10 s settle. Typical: server 30 s, client in game about 45 s later.
- Server: the `run-acceptance/` game directory of `runAcceptanceServer`. The
  checkout's own `server.properties` is saved to `run-combat-capture/` and
  restored at stop (or removed if there was none). The capture config uses a
  fresh superflat world `combat_capture_world` (deleted at every start), two
  free ports from 25610 upwards (never 8765), rcon enabled with a random
  password, `broadcast-rcon-to-ops=false`, difficulty normal (husk targets
  despawn on peaceful).
- Client: game directory `run-combat-capture/client`, options forced at every
  start (FOV 70, clouds off so frames can settle, no pause on lost focus, no
  onboarding screens, F5 and T at their defaults).
- State: `run-combat-capture/session.json` (mode 600; holds the rcon password,
  PIDs with their start times, display, ports). `stop` signals the supervisor,
  which sends rcon `stop`, then SIGTERM/SIGKILL by PID to the client tree, the
  server tree and Xvfb. If the supervisor is gone, `stop` kills the recorded
  PIDs directly, each checked against its recorded start time. Nothing is
  found by name pattern.

## Stills

Scene: player at (0.5, -60, 0.5) facing south, survival, weapon in the main
hand, noon, clear weather, no other entities. Cultivation combat mode is
confirmed through the player's `combat_preference` attachment over rcon; if
it is vanilla, the tool presses R once and checks the server again.

- First person: `/myvillage_pal_smoke first_person <n> <tick>`, confirmed by
  the client log line `PAL_SMOKE first_person move=<n> tick=<tick>`; ends with
  `first_person release`.
- Third person, F5 back and F5 front: `/myvillage_pal_smoke third_person <n> <tick>`,
  confirmed by `PAL_SMOKE third_person move=<n> tick=<tick>`; ends with
  `third_person release`. An invisible barrier wall stops the F5 camera 2.4
  blocks from the eye. Back view pitch 30 (looking down over the shoulder),
  front view pitch 15 (camera low in front). The player's pitch also tilts the
  PAL arm pose, so these values are in the manifest and must match between
  compared captures.
- Each still is kept only after two consecutive full-screen grabs are
  byte-identical (up to 6 s; otherwise it is kept, flagged `stable: false` and
  labelled `[UNSTABLE]`).

## Combo run

Three targets: `dummy` (default) is a husk with AI on but speed 0, follow
range 0, knockback resistance 0 and 80 HP, so it shows knockback and hitstun;
`golem` is a NoAI iron golem. Target t1 stands 2.5 blocks straight ahead,
t2/t3 to the sides. Clicks are `xdotool mousedown/up` without moving the
pointer. Move 1 is clicked at t=0; the click for move n+1 lands midway between
move n's `buffer_start_tick` and `chain_tick`. Health is read with
`data get entity <uuid> Health` before and after. The manifest also records
the clicks actually sent, the player position before and after, and the
client's `PAL_SMOKE play` animation starts during the run. This is the
mapped-click and server-damage evidence; the freeze probes never substitute
for it. There is no audio device on the capture host, so sound is not captured.

## Output

`out/preview/combat_capture/<label>/` (change with `--out-root`):

```
manifest.json            weapon, style, rig, commit, file hashes, camera settings, moves and key ticks, frames, combo
sheet_fp.png             rows = moves, columns = key ticks; every cell names weapon, move, key, tick, view
sheet_tp_back.png
sheet_tp_front.png
frames/<view>/m<n>_<key>.png   full 960x540 frames
combo/combo.mp4          combo/combo_strip.png
index.html
```

A comparison directory holds `compare_<view>.png`, `comparison.json`,
`a/combo/`, `b/combo/` and `index.html`. `out/preview/` is served publicly
from the capture host, so manifests and pages hold only capture facts: no
absolute paths, ports, host names or credentials.

## Pitfalls

- Keys typed while no screen is open hit game binds (R combat mode, V/B
  meditation, X stop, F1 hides the held item). Every keystroke is preceded by
  a UI-state check: the game holds the X pointer grab only when it is in game
  with no screen open (a throw-away `XGrabPointer` on the root window).
- F3+T is sent as `keydown F3; key t; keyup F3`. The rig-loaded line is
  `Loaded first-person swing rig`; pass `--rig-pattern` if it is renamed.
- Never `pkill -f`: it matches your own shell. Use `session stop`.
- Xvfb is started and stopped by the supervisor; do not start it with `&`
  from a shell that is about to exit.
- Software rendering runs at about 30-45 fps; the combo video is still
  encoded at a constant 30 fps.
- `ffmpeg` start-up takes a few tenths of a second, so the first click shows
  up slightly before `lead_seconds` in the video.

## Tests

```bash
python3 -m unittest tools.tests.test_combat_capture
```
