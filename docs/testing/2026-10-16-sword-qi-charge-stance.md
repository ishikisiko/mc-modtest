# Sword qi charging stance — 0.56.0-fix3

Owner request: prepare the sword on the left when charging begins, then continue
into the slash rather than starting the preparation only after charging ends.

Runtime/build source: `72d9fd630d5e374c1dea4e4751640c11e7cfa830`.
Modal run: `reports/modal-runs/20261016T125859Z-11a0c81a`.

- Optional `charge_pose_ticks` (default zero) selects the existing horizontal-cut
  preparation prefix. Sword qi holds its left-side coil at tick 3 in first and
  third person. The shared first-person sampler keeps the hand on the item.
- Accepted CHARGING starts presentation; there is no client damage/cost authority
  or gameplay probe. PAL stops exactly at the held key while legs retain walking.
- Release reconstructs the prepared prefix from the existing charge byte; server
  attack/wave delay and client animation/trail offset agree. Partial taps continue
  their partial preparation; a fully prepared charge has two release ticks left.
- Cancellation captures the last local hand pose for blend-out and returns the
  third-person layer to ready idle; expiry and entity/lifecycle cleanup clear it.
- 72 local focused Python tests passed. 58 Java tests passed: loader 18, skill 7,
  animation controller 12, input latch 3, first-person swing 18. XML checked separately.
- Eleven mapped first-person cases passed: unlearned gate, tap/full cast, same-weapon
  slot/switch/damage/menu/dodge cancellations, insufficient power and two 20-TPS casts.
- A second real client observed moving while charging and subsequent release at
  20 TPS. Server-attributed damage reduced target health from 100 to 90.55.
  Developer inspected charging screenshots: left-side first-person hold and a
  third-person held arm with stepping legs. Owner appearance/feel, audio/FPS,
  balance and multiplayer experience remain `not_verified`.
- First run `reports/modal-runs/20261016T125321Z-4454a9de` retained: screenshots
  delayed power readback past regeneration (20 -> 10 despite a logged cost of 12).
  The corrected collector samples immediately after the authoritative event;
  retry reads 20 -> 8, then separately records the later regenerated 10. Exact
  deduction assertions were retained. Both cloud instances were confirmed destroyed.
- Artifacts downloaded and hash-verified. Final jar contents and HTTP download
  verified: `myvillage-0.56.0-fix3.jar`, SHA256 `2011f83f5b0f5d4ed9e8cee2245b4526d771306691ee967adecfdddfa3a8a909`.
