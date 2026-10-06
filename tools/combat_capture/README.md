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
| `stills --label L [--views V,...]` | Stills of every move at its key ticks in each view (default views: see "Views"), one sheet per view, `manifest.json`, `index.html`. Needs a running session. |
| `combo --label L [--targets dummy\|golem] [--layout default\|sweep\|line] [--tick-rate N] [--camera first\|back] [--pitch P]` | Targets in front of the player (`--layout`, see "Combo run"), real mapped left clicks through the combo at chain timing, mp4, target health before and after and per move (`hits_by_move`), the client's hit-stop and resync lines (`fp_log`); `--tick-rate` slows the run. Adds to an existing capture set with the same label. `--camera back`: the same run filmed from F5 back at vanilla distance (pitch 30 unless `--pitch`). |
| `motion --label L [--views V,...] [--enter] [--idle S] [--moves N,...]` | Third-person video per view (default: the weapon's third-person default views) of each move's PAL animation played on the client at game speed; `--enter` first records the mode-enter animation through the real R toggle, `--idle` seconds of the ready idle. No server action. |
| `reload [--src DIR] [--paths GLOB...]` | Copy changed combat client resources into `build/resources/main`, press F3+T, wait for the rig-loaded log line. |
| `compare A B --label L` | Pair two capture sets by weapon, move, key and view into side-by-side sheets and a page; cells present in one set only are marked `[UNPAIRED]`. |
| `page DIR` | Rebuild the sheets, combo strip and `index.html` of a capture directory. |
| `scene stills\|combo` | Set up the scene only (for manual looks). Works for a weapon without a first-person rig. |
| `view VIEW` | Put the camera into one view (walls, stand, body alignment, F5) without any probe, for manual looks. |
| `shot PATH [--max-wait S]` | Grab one full frame to a PNG once it holds still (same rule as the stills); refused while a screen is open. Types and clicks nothing. |
| `rcon CMD...`, `ui-state` | Server commands in the session; whether the client is in game with no screen open. |
| `beast [--beast ID] [--parts idle,moves,locomotion,fight,dodge,slowmo] [--out DIR]` | Beast evidence (default `myvillage:demon_wolf`, output `out/preview/<name>/ingame/`). `idle`: stills from four sides and a scale still beside the player. `moves`: exact-tick stills per move (start, aim lock, lunge, first/last active, mid recovery; `/tick freeze` + `/tick step` + `/myvillage beast move`, side and three-quarter front, spectator camera, HUD hidden); a move whose lunge has `up > 0` is run again and stepped one tick at a time after the lunge to add `landing` (first tick the server reports `ground=true`) and `landing_plus2` stills. `locomotion`: walk and run videos (barrier pen and corridor). `fight`: 60 s video, survival player with no armour, no effects and `naturalRegeneration` off, Qingfeng sword, cultivation combat mode, F5 back, standing still and swinging whenever the beast is within 3.6 blocks; one beast at a time, the player healed between beasts and put back after a death (`deathCount` scoreboard). The server's `BEAST_DEBUG` lines and polled `/myvillage beast status` go to `fight_log.txt`; `manifest.json` gets a stagger summary and per-beast rounds (beast ticks: time to kill, moves started/completed/cancelled, hits and damage on the player, player hits resisted or staggered inside/outside immune windows, ticks from first in `use_range` to the first landed hit per move). `dodge`: at `/tick rate 5`, the close-range move (smallest `use_range` maximum) against a player standing still, strafing from move tick 0 and from the turn lock, then one sword swing at move tick 2 of every move (does an early hit cancel it or is it resisted?); health, distance moved and server lines per trial. `slowmo`: every move at `/tick rate 5` from a low side camera (world frozen until the move is forced), with the server's move tick, ground flag and position polled against video time in `manifest.json`. Starts and stops its own session unless one is running; `--parts` re-runs a subset and merges into the existing page. |
| `dodge [--beast ID] [--technique ID] [--out DIR]` | Player movement dodge trials (default `myvillage:demon_wolf`, `myvillage:taxue_wuhen`, output `out/preview/movement_dodge/`). Survival player with no armour and `naturalRegeneration` off, Qingfeng sword, cultivation combat mode, the technique learned through `/myvillage cultivation learn`; the dodge key is Left Alt (left dodge: A held two move ticks before; no direction is a backstep). At `/tick rate 5`, against a fresh beast forced into each move (bite 2.9 blocks ahead: no dodge, backsteps at move ticks 4, 7, 9, 10, left at 9; pounce 6 blocks ahead: no dodge, backsteps at 14, 17, 18, 19, left at 18), pressed when the polled `/myvillage beast status` first shows the tick; then two presses 5 game ticks apart (expect `rejected reason=COOLDOWN`) and two sword swings with a press at action tick 3 (expect `TIMING`) and two ticks after the first move's last active tick (expect `started`). Per trial: health before and after, distance moved, the server's `DODGE_DEBUG` lines and the beast's hits on the player, `bitten` / `dodged` / `cancelled_damage`; one F5-back video per beast move; `manifest.json`, `index.html`, `dodge_log.txt`. Starts and stops its own session unless one is running. |
| `npc [--npc ID] [--look LOOK] [--parts idle,walk] [--out DIR]` | NPC evidence (default `myvillage:cultivator`, output `out/preview/<name>/ingame/`). `--look` (`default`, `f_novice`, `f_adept`) summons with `{Look:"<look>"}` and writes `out/preview/<name>/ingame_<look>/`; `default` is the plain summon. `python3 tools/npc_looks_page.py` then puts the three looks side by side in `out/preview/cultivator/looks/index.html`. `idle`: with the world frozen and a spectator camera, full-figure stills from four sides, close-ups (face, head from front and back, collar, belt, sleeve, hem, back panel), and a scale still beside the player (F5 front). `walk`: two NPCs strolling in a barrier pen, 40 s from the front and 40 s from the pen's end. Starts and stops its own session unless one is running; `--parts` re-runs a subset and merges into the existing page. |
| `check`, `ticks` | Host programs and the weapon's data (held length, default views, rig present?); the moves and key ticks (light, no processes). |

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
- Third person: `/myvillage_pal_smoke third_person <n> <tick>`, confirmed by
  `PAL_SMOKE third_person move=<n> tick=<tick>`; ends with
  `third_person release`. The probe holds every bone of the move's PAL
  animation, so there is no idle sway in these stills.
- A refused probe logs `PAL_SMOKE <probe> rejected reason=<reason> ...`
  (e.g. `no_weapon`, `index_out_of_range`, `tick_out_of_range`, or the move's
  animation not loaded). The wait stops at that line at once with an error
  naming the reason (no retry: the same command would be refused again);
  `motion` does the same for `move <n>`.
- The manifest's `rig_loaded` records the first-person rig the client reads
  (`build/resources/main`, which `reload` writes): its sha256, whether it
  declares an off hand (`rig.off_hand`) and whether it equals the source
  tree's copy (it does not after `reload --src`). The client's rig-loaded log
  line says neither. `source.files.rig` is always the source tree's file.
- Each still is kept once the picture holds still: two consecutive
  full-screen grabs byte-identical, or three consecutive grabs pairwise within
  2 colour levels per channel everywhere (whole-frame rendering noise; a pose
  still moving changes edge pixels by tens of levels, and a slow drift shows
  between the first and third grab). Up to 6 s; otherwise it is kept, flagged
  `stable: false` and labelled `[UNSTABLE]`. The frame record says `match`:
  `exact` or `noise` (with `max_level_diff`). Neither rule can tell a pose that
  never arrived from one that did; the probe's log confirmation covers that.

### Views

| View | F5 | Camera | Pitch | Look yaw |
|---|---|---|---|---|
| `fp` | first | | 0 | 0 |
| `tp_back` | back | barrier wall 2.4 blocks behind the eye | 30 | 0 |
| `tp_front` | front | barrier wall 2.4 blocks in front of the eye | 15 | 0 |
| `tp_back_right` | back | vanilla 4 blocks, behind the right shoulder | 30 | -50 |
| `tp_back_left` | back | vanilla 4 blocks, behind the left shoulder | 30 | +50 |
| `tp_front_right` | front | vanilla 4 blocks, in front on the player's right | 15 | +50 |
| `tp_front_left` | front | vanilla 4 blocks, in front on the player's left | 15 | -50 |

The body always faces south (yaw 0, the action facing) and the PAL animation
poses the figure relative to the body. The F5 camera follows the look, and
vanilla lets a standing player's head turn up to 50 degrees off the body
before the body follows (`LivingEntity.tickHeadTurn`; the body also follows
when the player moves or swings). So the quarter views turn the look 50
degrees and see the posed body from 50 degrees off its axis. The head bone is
keyed by the animations, so the head shows the animation's pose, not the
look. Every third-person view sets the body deterministically by teleporting
(server `tp`, same position, 0.4 s apart) through the yaws
`[-s*70, s*50, look]` (s = the sign of the look yaw, + for 0): the first leaves
the body 20-120 degrees on the far side whatever it was, the second is then
70-170 degrees off it, so vanilla clamps the body to exactly 0, and the last
turns only the head. Two frames of the same probe taken after different body
yaws are byte-identical. Quarter views use no barrier wall: 4 blocks is the
farthest F5 camera there is without Java, and a long weapon needs it.

The player's pitch also tilts the PAL arm pose, so a quarter view uses the
pitch of the straight view on its side and shows the same pose. These values
are in the manifest (`capture.third_person_camera`, keyed by the view without
`tp_`) and must match between compared captures; `compare` warns and the page
lists any camera field that differs for a paired view.

Default views when `--views` is not given: the weapon's held length is its
geometry contract's `overall_y` span times its item model's
`thirdperson_righthand` scale, in blocks (`geometry` in the weapon file).
Shorter than 1.8 blocks (Qingfeng sword: 1.225), or no geometry:
`fp,tp_back,tp_front`, as before. 1.8 or longer (Lingxiao spear: 2.7):
`fp,tp_back_right,tp_front_left`; the straight views look along the shaft of a
weapon held pointing forward.

Framing limits measured with the spear (armor-stand stand-ins, frames under
`out/preview/lingxiao_spear/capture_views/`): the back quarters keep a forward
tip 3.5 blocks ahead of the player and sideways sweeps 2.5 blocks out well
inside the frame. The front quarters keep a forward tip up to about 3.1 blocks
ahead inside the frame (tp_front_left about 45 px from the edge, tp_front_right
about 12 px, for a right-hand thrust); at 3.5 blocks it is cut at the side
edge. The straight `tp_front` cuts a forward-held spear off at the top.

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

`--layout` places the three targets (offsets from the player; +x is the
player's left): `default` (t1 2.5 ahead, t2/t3 5 blocks out, out of reach of
every move), `sweep` (an arc 2.5 blocks out: ahead and 45 degrees to either
side) and `line` (2.5, 3.5 and 4.5 blocks straight ahead). During the run a
second rcon connection polls every target's health about every 0.05 s; each
loss is put on the latest client move start (`PAL_SMOKE play`, a prediction
and its confirmation counted once) before it, so the manifest's `hits` and
`hits_by_move` say who was hit by which move. The client's
`PAL_SMOKE fp_hit_stop` and `fp_resync` lines during the run go to `fp_log`.
`--tick-rate N` (1-20) slows the game with `/tick rate N` for the run only
(5 = quarter speed) and scales the clicks and the recording to it; the rate
is set back to 20 afterwards, also on failure.

`--camera back` films the same run (same position, targets, clicks) from F5
back, 4 blocks, look yaw 0. A quarter view cannot be held through a real
combo: each action snaps the body and head to the look yaw it faces (server)
and vanilla turns the body to the look while the arm swings (client), so the
F5 camera ends on the body's axis. For quarter-view motion use `motion`: it
plays each move's animation on the client with
`/myvillage_pal_smoke move <n>` (typed in chat, so the chat line shows for a
moment); nothing reaches the server, so there are no hits, no lunge movement
and no world trail, and the body keeps its facing. Videos go to
`motion/<view>.mp4`, events and their times to the manifest's `motion` list.

## Output

`out/preview/combat_capture/<label>/` (change with `--out-root`):

```
manifest.json            weapon, style, rig, loaded rig, commit, file hashes, camera settings, moves and key ticks, frames, crops, combo
sheet_fp.png             rows = moves, columns = key ticks; every cell names weapon, move, key, tick, view
sheet_tp_back.png ...     one per captured view
frames/<view>/m<n>_<key>.png   full 960x540 frames
combo/combo.mp4          combo/combo_strip.png
index.html
```

### Cropped third-person sheets

In the quarter views (third person without a barrier wall, camera 4 blocks
away) the figure covers a small part of the frame, so their sheets crop every
frame to one box per view and enlarge it by an integer factor (1-3, point
filter, cell at most 720 px wide; 480 px per side in a comparison). The box
is fixed for the view and the capture set, so the cells stay comparable:

1. body box: the standing player's volume (0.5 blocks either side of the body
   axis in x and z, from below the soles to above the head, relative to the
   eye at the screen centre) projected through the view's recorded camera
   (F5 side, distance, pitch, look yaw, FOV 70, 960x540);
2. motion box: the union over all the view's frames of where a frame differs
   from the per-pixel median of those frames by more than 4 % grey (the
   background is the same in every frame of a view, so this covers the
   weapon and the limbs in every pose);
3. the union of both, plus 16 px on every side, at least 160x120, clamped to
   the frame.

A view with fewer than 3 frames is not cropped. The box and rule are stored
in the manifest's `crops` and named in the sheet title and on the page.
`compare` crops both sides of a view with the union of the two sets' boxes,
and only when the view is cropped in both. The straight views (`tp_back`,
`tp_front`, barrier wall 2.4 blocks) and `fp` keep their full frames, so the
sword's sheets and comparisons are unchanged. Files under `frames/` are never
modified; the crop happens when the sheet is drawn. `page DIR` rebuilds the
sheets of an existing capture set with the crop (offline).

A comparison directory holds `compare_<view>.png`, `comparison.json`,
`a/combo/`, `b/combo/` and `index.html`. `out/preview/` is served publicly
from the capture host, so manifests and pages hold only capture facts: no
absolute paths, ports, host names or credentials.

## Pitfalls

- Killed targets drop experience, and a filling XP bar is part of every
  frame: the scene setup sets the player's experience to 0, so captures made
  after a combo still compare with captures made before one. Captures taken
  before this reset (up to `sword-v2`) can differ in the XP bar row
  (y 482-491) alone.
- `tp` with a large yaw change leaves a standing player's body up to 50
  degrees off the look until it moves or swings; `view` and the stills align
  it (see "Views"). A manual `tp` before `shot` does not.
- In the vanilla hold (no probe, e.g. a weapon without animations) the arms
  sway with the game time and `/tick freeze` does not stop players, so a
  `shot` of it does not settle; it is still saved, with `stable: false`.
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
