## MODIFIED Requirements

### Requirement: Per-move stills use the freeze probes
For a registered weapon the tool SHALL capture stills of every move at its key ticks (idle, strike start, contact, strike end, recovery) using the first-person and third-person smoke probes, reading the move list from the style file and the key ticks from the rig. Without `--views` it SHALL choose the views from the weapon's held length (the contract's `overall_y` span times the item model's third-person scale): first person plus F5 back and front for a weapon shorter than 1.8 blocks, first person plus the back-right and front-left quarter views for a longer one. Each still SHALL be kept once the frame holds still, either two consecutive grabs byte-identical or three consecutive grabs pairwise within 2 colour levels per channel, and otherwise kept and flagged unstable. Each still SHALL be labelled with weapon, move, key, tick, and view. A probe refusal logged by the client SHALL stop the wait at once with an error naming the reason. The manifest SHALL record the first-person rig the client actually loaded (its hash, whether it declares an off hand, and whether it equals the source tree's copy).

#### Scenario: Stills cover every move
- **WHEN** the capture command runs for `myvillage:qingfeng_sword`
- **THEN** it writes one labelled sheet per view with a row for each of the five moves and a manifest listing every frame's move and tick

#### Scenario: Long weapon gets quarter views
- **WHEN** the capture command runs for `myvillage:lingxiao_spear` without `--views`
- **THEN** it captures the `fp`, `tp_back_right`, and `tp_front_left` views

#### Scenario: Refused probe fails fast
- **WHEN** the client logs `PAL_SMOKE third_person rejected reason=missing_animation` for a still
- **THEN** the tool stops waiting at once and reports the reason instead of timing out

## ADDED Requirements

### Requirement: Cropped quarter-view sheets
In views without a barrier wall the sheet SHALL crop every frame of the view to one fixed box (the union of the projected standing body and the area where frames differ from their per-pixel median, plus a margin) and enlarge it by an integer factor; the box and rule SHALL be stored in the manifest and named on the sheet and page. Straight views and first person SHALL keep full frames, and files under `frames/` SHALL never be modified. A comparison SHALL crop a view only when both sets crop it, with the union of their boxes.

#### Scenario: Sword sheets are unchanged
- **WHEN** a Qingfeng capture with the default views builds its sheets
- **THEN** no sheet is cropped

### Requirement: Manual views, single shots, and motion
The tool SHALL provide `view VIEW` (put the camera into one view without a probe), `shot PATH` (grab one stable frame; refused while a screen is open), and `motion` (third-person video of each move's PAL animation played on the client at game speed in the chosen views, optionally the mode entry through the real toggle and seconds of the ready idle). `motion` sends no combat intent; its output SHALL be labelled as client animation playback without hits, steps, or world trails.

#### Scenario: Motion records a quarter view
- **WHEN** `motion --label L --views tp_back_right` runs against a session holding the spear
- **THEN** it writes `motion/tp_back_right.mp4` and lists each move's playback in the manifest

### Requirement: Combo options and hit attribution
`combo` SHALL accept `--layout default|sweep|line` (one target ahead with two out of reach; an arc 2.5 blocks out; three in a line ahead), `--tick-rate N` (the run at `/tick rate N`, clicks and recording scaled, the rate restored afterwards also on failure), and `--camera back` (the same run filmed from F5 back). During the run it SHALL poll every target's health and attribute each loss to the latest client move start before it, recording `hits` and `hits_by_move`, and SHALL record the client's hit-stop and resync log lines (`fp_log`), the camera, the layout, and the tick rate in the manifest.

#### Scenario: Sweep layout attributes hits by move
- **WHEN** `combo --layout sweep` runs with the spear
- **THEN** `hits_by_move` shows which targets the sweep struck and the target health before and after is recorded

#### Scenario: Slowed run restores the tick rate
- **WHEN** `combo --tick-rate 5` fails part-way
- **THEN** the server's tick rate is set back to 20

### Requirement: Captures are comparable after a combo
The scene setup SHALL reset the player's experience to zero, so that the experience bar is the same in captures taken before and after a combo that killed targets.

#### Scenario: Stills after a combo
- **WHEN** stills are captured in a session after a combo run
- **THEN** the experience bar row matches stills captured before any combo
