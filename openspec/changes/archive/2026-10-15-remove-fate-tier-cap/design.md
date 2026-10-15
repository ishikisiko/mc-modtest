## Context

FateSelection.validate is shared by server confirmation, client button eligibility and legal-triple candidate generation. Capture tooling also selects a valid triple.

## Goals / Non-Goals

Goals: remove only the high-tier count cap consistently across those paths. Non-goals: changing budget defaults/range, costs, weights, eligible definitions, count, groups, persistence or protocol.

## Decisions

Remove the tier-count branch from the shared validator rather than adding another configuration toggle. Keep every remaining rejection and authority gate. Delete obsolete high-tier translations and remove the redundant capture condition. Add synthetic high-tier definitions in tests, since the shipped pool currently contains only one purple-or-higher fate; do not alter shipped balance data for testing.

## Risks / Trade-offs

A configuration budget can still prevent expensive combinations: two 4-point fates plus one 1-point fate require nine points. Tests must prove multi-high confirmation succeeds at sufficient budgets and fails for excess cost or duplicate groups. Existing save formats and protocol remain unchanged.
