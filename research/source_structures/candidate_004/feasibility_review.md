# Candidate 004 Import Feasibility Review

## Scope

This package evaluates whether one Yaodong source structure is worth a future,
formal external-prefab import change. It is research evidence only. No upstream
NBT was added to `research/source_structures/`, `src/main/`, canonical
generation, place/gallery functions, version metadata, or the mod jar.

## Evidence

- Full five-file static inventory: `import_audit.json`
- Four-bucket route decision: `breakdown.json`
- Research-only rewrite policy: `quarantine_policy.json`
- Exact source/output hashes and transformation ledger:
  `sanitization_manifest.json`
- Source preview: `out/preview/candidate_004_source/yaodong_1/`
- Sanitized preview: `out/preview/candidate_004_sanitized/yaodong_1.sanitized/`
- Left/source vs right/sanitized comparison:
  `out/preview/candidate_004_comparison/isometric_source-left_sanitized-right.png`
- Review entry page: `out/preview/candidate_004_comparison/index.html`

## Critical visual judgment

The sanitization pass preserves the source massing closely enough for further
study: the sunken central court, heavy perimeter, and below-grade room ring
remain in the same locations, while jigsaw/gameplay payloads no longer
participate in the preview sample. The source itself should be read critically:
it is a badlands-oriented mud/stone-brick vaulted interpretation, not a complete
or canonical model of exposed-yellow-earth Yaodong architecture.

That is not yet a positive architectural verdict. In the current coarse
isometric preview, the structure reads first as a buried rectangular box with a
square opening. Much of the Yaodong identity is hidden below the surface, and
unmapped block colors flatten the earth/timber distinction. The layer contact
sheet and interactive viewer are more informative than the isometric image, but
neither can prove terrain seams, descent readability, drainage, or player-scale
circulation.

## Technical judgment

`yaodong_1.nbt` is the best feasibility sample because it has no entities and
the smallest block-entity payload count in the five-piece family. The restricted
geometry re-emit removed 31 block-entity payloads, including 19 `Items`-list and
6 loot-table payloads. The 19 `Items` lists were empty; the source piece contained
no actual item-stack nodes. Three jigsaws became stone bricks, one became cave air, and
three `structure_void` connectors were omitted according to their declared
`final_state`.

The result remains blocked from shipping. Its DataVersion is intentionally
preserved at 3953 because no DataFixer migration to the project target 3955 was
performed. The current minimal NBT reader is also not hardened for arbitrary
untrusted input, and this one-off restricted re-emit is not a reusable importer.

## Recommendation

Treat `candidate_004` as a viable `direct_component` research candidate and a
strong `generative_grammar` source, but do not import it into the mod yet. The
next owner decision is whether the preserved geometry is valuable enough to
justify a separate `external-structure-quarantine` capability with a bounded
parser, deterministic sanitizer, DataFixer path, tests, and a later shipping
decision.

Human feasibility verdict: **accepted with changes**. The preserved geometry is
worth a reusable quarantine-tool implementation, but this verdict does not
approve shipping, DataVersion relabeling, or runtime/worldgen integration.
