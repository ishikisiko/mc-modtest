## Context

The alchemy script currently requires a long ordered scenario chain. Release and runtime presets use fresh separate workspaces. Heavy work must remain on Modal.

## Goals / Non-Goals

Goals: selectable independent fixtures, honest partial coverage, one fixed-source combined delivery workspace, preserved final evidence.
Non-goals: gameplay changes, persistent build/world caches, live editing of a running Sandbox, automatic retry, dropping clean stage1, automatic impact analysis.

## Decisions

- Accept a bounded comma-separated --cases selection only for alchemy-quality. Default all; reject empty/unknown/duplicates before cloud allocation. Canonical order, independently initialized actor/furnace fixtures. Single-case success is explicitly partial.
- Combined release-alchemy always runs all cases after release and clean stage1 in the same ephemeral workspace. Generic release performs its full gate once without hardcoded alchemy tests. Keep build inputs immutable during the run.
- UI and persistence share the required bilingual/restart session when both selected. Persistence explicitly prepares its own higher-quality medicine and inventory; medicine and multiplayer do not depend on brewing.
- Record selected/completed/not-run cases and phase timing. Stop after an assertion failure, preserve the failed case, and never overwrite earlier evidence. Validate the changed case before final full delivery.

## Risks / Trade-offs

Fixture leakage → reset scene per case and verify brewing begins unawakened; test case dispatch and actual isolated scenarios.
Misleading partial pass → separate selected-case status from full acceptance, and do not allow subsets on release-alchemy.
No speed guarantee → report measured phases and avoid comparing different coverage as equivalent.

## Migration Plan

Existing commands retain all-case behavior. New targeted commands are diagnostic; combined delivery replaces the two-command happy path. Rollback reverts Python tooling; save formats are unchanged.
