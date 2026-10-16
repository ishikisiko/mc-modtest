# Horizontal thin-blade sword qi — 0.56.0-fix2

Owner direction: horizontal slash plus a horizontal thin blade. The earlier fix1
area change did not correct the diagonal-animation/horizontal-wave mismatch or the
swollen, translucent front silhouette. This revision addresses those presentation issues.

## Implementation

- Select the existing `basic_sword_02_horizontal_cut` animation and emit at tick 5.
  Authored third-person blade yaw crosses zero then; the first-person rig contact is
  4.9 ticks and its strike plane is horizontal. Existing melee pose/rig outputs are unchanged.
- Add optional `reaction_move`, defaulting to `feedback_move` for older skill files,
  to retain the previous hitstun/slide/lift independently from animation selection.
  Java and Python loaders validate both references. Horizontal visual/audio feedback
  follows the selected horizontal cut; charge cost, damage, width/height and range
  retain their existing skill values.
- Replace the swollen wedge with a narrow bright rim, thin fading crescent and three
  detached short wisps. Art dimensions/colors live in the authored presentation file
  `assets/myvillage/combat/sword_qi_wave.json`; the collision envelope is still the
  wider fix1 envelope. Mesh positions stay within the received lateral/vertical bounds.
- Seed first WAVE interpolation from the matching server RELEASED origin. No drawing
  occurs during wind-up, and the mesh never extrapolates ahead of the last server position.

## Evidence

Fixed runtime/artifact source: `51400ba6f90c0a7ad672beb882a8b464f6fd29ac`.
Modal run: `reports/modal-runs/20261016T123241Z-b8da7702/`.

- 70 local focused Python tests passed. The two presentation tests were strengthened
  afterwards to check first-person contact/plane as well, and passed again; this
  test-only follow-up does not change the packaged/runtime source.
- 36 Java tests passed: CombatDataLoaderTest 18; JianqiJueSkillTest 6;
  CombatWaveResolverTest 6; SwordQiWaveShapeTest 6. Downloaded XML checked separately.
- Twelve actual first-person casts passed: center tap/charged, expanded lateral and
  upper edges, outside-width controls, complete/edge walls, and two normal-20-TPS casts.
  Assertions require server events, target health and correct power deduction.
- A second real client recorded a side view of a mapped full-charge cast at normal
  20 TPS; target health fell from 100 to 90.55 and the server attributed the hit.
  Observer recording is presentation evidence, not a claim of multiplayer balance.
- Developer inspected both normal-speed videos via extracted frame strips: horizontal
  screen sweep, matching side-view slash plane, thin bright front edge and no swollen
  diamond body. Owner appearance/feel, audio/FPS, balance and multiplayer experience
  remain `not_verified` until owner acceptance.
- The cloud run passed; artifact hashes and Sandbox termination were confirmed. One
  transient Modal exit-status retry recovered. A nonessential live-video download raced
  teardown and failed; the verified completed artifact archive was used instead.
- Jar contents and served hash checked: `myvillage-0.56.0-fix2.jar`, SHA256
  `f199b0bede6894e9fac32b6497fcddc8947ccfade04110aa5956af9d5451414a`.
  Includes the demon lure pearl feature from 0.56.0.
