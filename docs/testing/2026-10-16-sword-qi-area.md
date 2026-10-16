# Sword qi area correction — 0.56.0-fix1

Runtime source: `1209f9d6653fc5f4d6be2e4a500b122a8021df6b`. Modal run: `reports/modal-runs/20261016T121101Z-231115ee`.
The only gameplay change is the authored half-width 1.1 → 2.0 and half-height
0.25 → 0.45. Existing client mesh and server swept collision read this same file.
Base full width/thickness is now 4.0/0.9 blocks; full charge is 6.0/1.35.
The crescent shape, damage, target cap, power cost, speed and range are unchanged.

## Evidence

- 31 local Python tests passed (capture/controller), including explicit outside controls.
- 16 Java tests passed: CombatWaveResolverTest 6, SwordQiWaveShapeTest 5,
  JianqiJueSkillTest 5. XML independently checked with zero errors/failures.
- Ten mapped-input actual-client trials passed: tap/charged central hits,
  tap/charged lateral expanded edges, tap/charged outside-width misses,
  tap/charged high edges, full wall and edge wall.
- Expanded horizontal targets are centered 2.6 and 3.6 blocks off axis, with
  0.7-block golem half-width. They lie outside the old envelope and inside the new.
  Upper-edge targets use NoGravity in the administrator fixture to prevent falling.
- Hits require both attributed server hit events and measured health loss; misses
  and walls require unchanged health and explicit terminal wave reason. All trials
  also passed their power-cost check. RCON is fixture/readback only, not casting.
- The client mesh test reads authored dimensions at all sampled charge levels,
  bounds every vertex and verifies visible width and thickness. Full charge stays
  clear of a horizontal ground plane. Charged frame at 3 seconds was inspected.
- Game recordings are at `artifacts/out/preview/sword_qi_area/` in the run directory.
  No player machine or DevBridge used. No full-release/other gameplay scenarios rerun.
- Modal run passed, artifact download hashes verified, Sandbox termination confirmed.
  A final read-only log query raced normal teardown and reported Sandbox not found;
  the completed result and downloaded artifacts were used instead. No test retry needed.
- Final jar and HTTP-served hash verified: `myvillage-0.56.0-fix1.jar`,
  SHA256 `5302aa0d27afed676ef93dab999e9092298664818eb20c241e24d73620e78c1f`. Includes the preceding demon lure pearl feature.

Owner appearance/feel, balance, audio/FPS and multiplayer remain `not_verified`.
Developer recordings do not replace owner acceptance.
