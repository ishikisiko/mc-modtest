# Sword qi waist draw — 0.56.0-fix4

Owner requested the grip at the left waist, blade nearly horizontal toward the
rear, held throughout charging and flowing into the horizontal release.

Runtime/build source: `ebd48c9e32f9428cb327b8a77e549dfe432f8da3`.
Successful Modal run: `reports/modal-runs/20261016T133356Z-8e0f97e3`. Artifacts hash-verified; Sandbox termination confirmed.

## Change and inspected visuals

- Authored PAL anticipation/coil lowers the right hand across the left waist;
  resolved coil grip is approximately (-0.233, 0.908, 0.246) blocks from the feet
  (right/up/forward), blade yaw -165 degrees and elevation near zero. The blade
  axis clears the torso by 1.15 model pixels. Generator output was regenerated.
- First-person grip stays low-left and visible; the backward tip is intentionally
  outside/behind the view. The two-bone arm bends back with held wrist flexion
  near -1 degree and deviation near 29 degrees, instead of the initial candidate's
  excessive wrist fold. Release and recovery return through the existing keys.
- This preparation is shared with normal horizontal combo move 2. Other move keys,
  server timings, costs, damage, hit geometry and protocol are unchanged.
- Developer inspected offline front/side/top poses and real first-person plus
  second-client videos. The 20 TPS first-person hold and subsequent horizontal
  release are visible; the observer sees the waist-held backward blade while
  the actor walks, then the cast hits the target (100 -> 90.55 health).
- The observer collector's direct `observer_charging.png` arrived during release.
  It is retained unchanged in the run artifacts. Delivered `observer_charging.png`
  instead comes from the actual video at 1.05 s, cropped 416x234 at (230,306) and
  enlarged with nearest-neighbor sampling. `observer_held_full.png` preserves its
  full frame. Neither image is a pose probe or a generated game scene.

## Checks and retained failure

- 138 local pose-generator / combat-data / presentation tests passed; updated
  presentation and Modal runner selection checks passed (23 tests).
- Generator `--check` passes for all three owned animation outputs.
- Cloud Java: FirstPersonSwingTest 19 tests, FirstPersonPreviewParityTest 1 test,
  no failures/errors/skips. The latter generates the updated parity golden.
- Cloud Python independently checks the Java golden and presentation geometry:
  6 tests passed. Downloaded golden rechecked locally: 3 parity tests passed.
- Eleven mapped first-person scenarios passed: unlearned gate, tap/full charge,
  same-weapon slot/switch/damage/menu/dodge cancellation, insufficient power, and
  both tap/full charge at 20 TPS. Second real client observed the moving charge
  and server-confirmed hit. No full release suite was run.
- Retained initial failure: `reports/modal-runs/20261016T133026Z-4a7d7afa`.
  Three old visual tests assumed every preparation keeps the tip in front of
  the camera; one dereferenced the deliberately unprojectable backward tip.
  The waist-draw prefix now checks hilt visibility and the full spatial arc;
  other moves retain the screen checks. Added held direction and wrist bounds.
  Retry changed test expectations to the explicitly requested rearward pose,
  without further runtime/data changes. Initial Sandbox termination confirmed.
- Second run had a transient Modal exec-status transport retry; game work was
  not relaunched. It completed successfully and teardown was confirmed.

Build/test phase: 34.804 s. Game capture/cleanup: 173.389 s.
Download and teardown: 3.585 s / 0.96 s.
These are measured phases, not an attribution of residual time to model reasoning.

Jar: `myvillage-0.56.0-fix4.jar`, SHA256 `e423d49624c0f5cd511d3ce9075ae5d7c9225b81ec434e370ba4b4277521e167`.
Bundled rig and PAL bytes match source; HTTP-served jar hash verified.
The follow-up source changes are the generated test golden and documentation;
no packaged runtime/resource inputs differ from the tested commit.

Preview: `http://43.156.135.198:8765/sword_qi_waist/`.
Owner appearance/feel, sound, FPS, balance and broad multiplayer experience remain
`not_verified`; cloud checks and developer inspection are not owner acceptance.

## Owner follow-up

On 2026-10-16 the owner said “动作就这样吧”, accepting this animation version
for retention. Animation direction is owner-accepted; this does not assert
separate acceptance of sound, FPS, balance or broad multiplayer experience.
