## Context

The base is 0.45.1 (`f061835`). The existing profile already stores spiritual power; manual study, sect scripture borrowing, combat styles, server damage, and dodge already exist. The owner's narrower authorization supersedes the larger untracked ranged-combat brief: one resource loop and one sword skill, with free dodge and no DevBridge. CRAFT planning provenance is recorded before implementation.

## Goals / Non-Goals

**Goals:** Make advancement affect combat resource capacity; make a studied technique usable with a familiar charge/release input; prove resource, collision and learning behavior with layered evidence; retain test timings for environment sizing.

**Non-Goals:** Dodge costs, bleed, extra families, multi-move active combos, general projectiles, flying-sword control, or an inferred owner aesthetic verdict.

## Decisions

1. Reuse `currentSpiritualPower` and `CultivationService`. A separate balance or attachment would create competing persistence and synchronization paths. Derive capacity from stage data; keep recovery rules in `data/myvillage/qi/rules.json`. Clamp over-cap legacy values at the service/lifecycle boundary, not by rewriting old codecs.
2. Charge/release are empty intents. The server holds transient charge state, cancels invalid sessions, spends once only at accepted launch, and advances the wave on server ticks. Client prediction may show anticipation but cannot commit a launch or resource mutation.
3. Register one data-backed skill linked from `jianqi_jue`. Reuse family resolution and existing damage/dodge policy. Keep skill tuning out of Java item branches. An advancing wave needs no persistent projectile entity in this slice.
4. Reuse generated manuals and existing sect scripture/study rules. Test the natural loop separately from admin fixtures; a command-granted learned technique proves runtime access but not acquisition.
5. Keep existing melee and dodge baselines. Adding a new resource should first add an optional decision, not change the accepted defense cost.
6. Measure commands with a common wall/CPU/RSS wrapper and a host snapshot. Preserve cold/warm labels, setup/readiness phases, failures and reruns; elapsed time on this host is evidence, not a universal benchmark. Serialize heavy Gradle/Minecraft work using the shared lock.
7. Test without DevBridge: pure tests, validators, jar/server checks, then the existing real client under virtual display with mapped keyboard/mouse, RCON fixture/readback, and logs/recording. Automated observation and owner physical-client acceptance stay separate.

## Risks / Trade-offs

- Wave sample gaps or wall-edge leakage → swept collision/occlusion tests plus wall/near/far real-runtime trials.
- Duplicate release or interrupted charge spends twice → one server state transition and atomic service spend tests.
- Recovery rounding or core-factor mistakes → deterministic fractional/ceiling boundary tests and data-derived values.
- Input capture steals normal interactions → bound interception to the matching combat mode/weapon/learned action and test off-mode usage.
- First-client startup dominates timings → separate setup, readiness, action, and build phases with explicit cache context.
- Virtual-display evidence does not establish owner FPS or feel → explicit `not_verified` review ledger.

## Migration Plan

Regenerate catalogue outputs, validate skill references, build the practical jar, and inspect packaged data. Preserve the existing profile codec and normalize over-cap power on login through the service. Transient charges do not survive logout or server restart. Publish commands and a bounded acceptance checklist with the release candidate. Release metadata follows `openspec/config.yaml` `rules.tasks`; target version is 0.46.0. Rollback uses the prior jar and a pre-upgrade world backup if new technique references or modified worlds require full state rollback.

## Open Questions

The owner's final visual, sound, feel, FPS and multiplayer verdict remain pending until observed. Exact test durations and machine limits are measured during this run, never estimated as results.
