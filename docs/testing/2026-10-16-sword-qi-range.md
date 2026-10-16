# Sword qi range — 0.56.0-fix5

Owner requested 20-block base travel and 30 blocks at full charge.
Runtime/build source: `ed7f0e6e65dbc084983911f97c5282e2f15c90bf`.
Modal run: `reports/modal-runs/20261016T134922Z-71c19b21`; passed, artifacts hash-verified, termination confirmed.

- Configured base `range` is 20; `range(level)` multiplies it by the existing
  charge scale (1.5 at full charge). Intermediate charge uses the authoritative
  charge byte. Range, final-step clamp, flight ticks and the external action's
  active window use the same computed value. Java/Python loading limits now
  account for maximum charged range and flight time.
- Client visual positions remain server-fed. The owner-accepted waist draw,
  speed, size, damage, costs and charging duration were not retuned.
- Local Python: 70 data/capture/Modal tests passed. Cloud Java: 32 tests passed
  (loader 18, skill/session 8, wave resolver 6). Session tests cover every server
  charge tick from zero to full, exact endpoint clamping, no early external
  action expiry, and wall stop. Zero-charge 20 blocks is covered by these tests.
- Six mapped-input scenarios passed: target at 20 blocks hit by a short hold;
  target at 25 missed by a short hold but hit by full charge; target at 30 hit
  by full charge; target at 32 missed; solid wall stopped the wave without damage.
- Short mapped holds accumulated 3/4 server ticks (charge bytes 38/51), so actual
  travel was 21.490196/22 blocks, not falsely reported as exact zero-charge travel.
  All three full-charge trials stopped at z=30.5 from z=0.5 (exact 30-block travel).
  Each open-path endpoint was checked against its released charge byte, not just
  target health. Full-charge hits reduced health 100 -> 90.55; misses stayed 100.
- Build/test: 43.961 s; real capture/cleanup: 113.402 s. No retry or full release
  suite. Cloud Sandbox destruction confirmed. Owner feel/balance, audio/FPS and
  broad multiplayer experience remain `not_verified`.

Jar: `myvillage-0.56.0-fix5.jar`.
SHA256: `95b9988f7014d2ada093ed9adc2340efb6d675fa0fcca0132c46ee0a05c757a6`. Bundled base range and HTTP-served jar hash verified.
Preview/evidence: `http://43.156.135.198:8765/sword_qi_range/`.
Follow-up changes only record this evidence; packaged source matches the tested commit.
