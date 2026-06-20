"""Region (洲) topology: data model, rules, and constructive generator.

Single shared module so the offline generator (``tools/generate_region_topology.py``)
and the offline validator (``tools/validate_region_topology.py``) agree on one
source of truth. The model + ruleset are JSON under the mod worldgen data tree
(``data/myvillage/worldgen/``); this module loads them and builds a per-seed
region graph constructively.

Design (see ``openspec/changes/add-region-topology/design.md``):

- Topology authored as **rules**; geometry randomized per seed (B-mode).
- **Constructive** generation — connectivity and the tier-step limit are built
  in, never validated-then-retried. There is no re-roll and no global
  backtracking.
- A 连 spanning tree over non-walled regions makes the world globally
  traversable by construction; remaining geometric-neighbour edges are typed
  连 / 隔 by rule; a 隔 carries a separator from {特殊山脉, 特殊海洋}.
- A ``walled`` region (魔域-style) is sealed except for at most one 关隘.
- Same seed → identical graph (deterministic), parity-ready for a future Java
  runtime placement-director (the RNG primitives mirror ``town_hash.py``).

The authored ``tier`` on a region profile is its **nominal** tier (catalog
identity). The generator assigns each region a concrete **assigned tier**
outward from the anchor (which holds the top of ``tier_range``). The generated
graph records the assigned tier as ``tier`` and keeps the nominal as
``nominal_tier`` for traceability; the tier-step invariant is checked against
the assigned tier.
"""

from __future__ import annotations

import json
import math
from collections import defaultdict, deque
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, FrozenSet, List, Mapping, Optional, Sequence, Set, Tuple

# --------------------------------------------------------------------------- #
# Deterministic, parity-ready RNG primitives.
#
# splitmix64 finalizer over a tagged FNV-1a hash of the seed bytes — the same
# construction as ``tools/buildgen/town_hash.py`` (whose Java mirror is
# ``TownHash.java``), so a future runtime placement-director can reproduce a
# graph bit-for-bit from the same (seed, tag). Pure-integer, platform-independent.
# --------------------------------------------------------------------------- #

_MASK64 = (1 << 64) - 1
_FNV_OFFSET = 0xCBF29CE484222325
_FNV_PRIME = 0x100000001B3
_SPLITMIX_GOLDEN = 0x9E3779B97F4A7C15
_SPLITMIX_M1 = 0xBF58476D1CE4E5B9
_SPLITMIX_M2 = 0x94D049BB133111EB

# Separator palette ids (values are the human glyphs used in data/visualization).
SEP_MOUNTAIN = "特殊山脉"
SEP_OCEAN = "特殊海洋"

# Edge type ids.
EDGE_LIAN = "连"
EDGE_GE = "隔"

# Pass label for a walled region's retained 连 edge.
PASS_GUANAI = "关隘"


def _fnv1a_tagged(seed: int, tag: str) -> int:
    """64-bit FNV-1a over the bytes of ``f"seed={seed_u};tag={tag}"``."""
    seed_u = int(seed) & _MASK64
    h = _FNV_OFFSET
    payload = f"seed={seed_u};tag={tag}".encode("utf-8")
    for b in payload:
        h ^= b
        h = (h * _FNV_PRIME) & _MASK64
    return h & _MASK64


def _splitmix64(z: int) -> int:
    z = (z + _SPLITMIX_GOLDEN) & _MASK64
    z = (z ^ (z >> 30)) & _MASK64
    z = (z * _SPLITMIX_M1) & _MASK64
    z = (z ^ (z >> 27)) & _MASK64
    z = (z * _SPLITMIX_M2) & _MASK64
    return (z ^ (z >> 31)) & _MASK64


def hash64(seed: int, tag: str) -> int:
    """Deterministic unsigned 64-bit hash of ``(seed, tag)``."""
    return _splitmix64(_fnv1a_tagged(int(seed), str(tag)))


class RegionRng:
    """A single seeded RNG stream over the world seed.

    Each draw carries a unique monotonically-numbered tag so no two draws
    collide, while remaining a pure function of ``(seed, tag)`` — regenerating
    with the same seed replays the exact draw sequence. The numbered-tag scheme
    is cheap for a future Java mirror to replicate.
    """

    def __init__(self, seed: int, stream: str = "region") -> None:
        self.seed = int(seed)
        self.stream = stream
        self._n = 0

    def _tag(self, label: str) -> str:
        self._n += 1
        return f"{self.stream}:{self._n}:{label}"

    def range(self, lo: int, hi: int, label: str = "r") -> int:
        """Inclusive deterministic integer in ``[lo, hi]``."""
        if lo > hi:
            raise ValueError(f"RegionRng.range: lo ({lo}) > hi ({hi})")
        span = hi - lo
        return lo + (hash64(self.seed, self._tag(label)) % (span + 1))

    def pick(self, options: Sequence[Any], label: str = "p") -> Any:
        """Deterministic selection from a non-empty sequence."""
        if not options:
            raise ValueError("RegionRng.pick: options must be non-empty")
        return options[hash64(self.seed, self._tag(label)) % len(options)]

    def chance(self, p: float, label: str = "c") -> bool:
        """Deterministic boolean true with probability ``p`` in ``[0.0, 1.0]``."""
        if p <= 0.0:
            return False
        if p >= 1.0:
            return True
        return hash64(self.seed, self._tag(label)) % 1000 < int(p * 1000)


# --------------------------------------------------------------------------- #
# Data model.
# --------------------------------------------------------------------------- #


def _as_pair(value: Sequence[int]) -> Tuple[int, int]:
    if len(value) != 2 or value[0] > value[1]:
        raise ValueError(f"expected a [lo, hi] pair with lo <= hi, got {list(value)!r}")
    return (int(value[0]), int(value[1]))


@dataclass(frozen=True)
class RegionProfile:
    """An authored region (洲) identity — the catalog entry."""

    id: str
    display_name: str
    tier: int  # nominal (catalog) tier
    qi: Tuple[int, int]
    danger: Tuple[int, int]
    role: str  # anchor | peripheral | walled
    admitted_subjects: Tuple[str, ...]

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "RegionProfile":
        role = data["role"]
        if role not in ("anchor", "peripheral", "walled"):
            raise ValueError(f"region {data.get('id')!r}: unknown role {role!r}")
        return cls(
            id=str(data["id"]),
            display_name=str(data["display_name"]),
            tier=int(data["tier"]),
            qi=_as_pair(data["qi"]),
            danger=_as_pair(data["danger"]),
            role=role,
            admitted_subjects=tuple(str(s) for s in data.get("admitted_subjects", [])),
        )


@dataclass(frozen=True)
class Ruleset:
    """The topology rules — all tunables live here, not in code."""

    id: str
    region_count: Tuple[int, int]
    tier_range: Tuple[int, int]
    tier_step: int
    separator_palette: Tuple[str, ...]
    peripheral_ring_radius: float
    walled_ring_radius: float
    peripheral_nearest_peers: int
    non_tree_edge_lian_chance: float
    walled_gate_chance: float
    walled_pass_label: str
    catalog_dir: str

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "Ruleset":
        palette = tuple(data["separator_palette"])
        for sep in palette:
            if sep not in (SEP_MOUNTAIN, SEP_OCEAN):
                raise ValueError(f"separator {sep!r} not in palette {{{SEP_MOUNTAIN}, {SEP_OCEAN}}}")
        embedding = data.get("embedding", {})
        edge_rules = data.get("edge_rules", {})
        role_rules = data.get("role_rules", {})
        walled_rule = role_rules.get("walled", {})
        anchor_place = embedding.get("anchor_placement", "center")
        if anchor_place != "center":
            raise ValueError(f"anchor_placement must be 'center', got {anchor_place!r}")
        return cls(
            id=str(data["id"]),
            region_count=_as_pair(data["region_count"]),
            tier_range=_as_pair(data["tier_range"]),
            tier_step=int(data["tier_step"]),
            separator_palette=palette,
            peripheral_ring_radius=float(embedding.get("peripheral_ring_radius", 1.0)),
            walled_ring_radius=float(embedding.get("walled_ring_radius", 1.4)),
            peripheral_nearest_peers=int(embedding.get("peripheral_nearest_peers", 2)),
            non_tree_edge_lian_chance=float(edge_rules.get("non_tree_edge_lian_chance", 0.5)),
            walled_gate_chance=float(edge_rules.get("walled_gate_chance", 0.7)),
            walled_pass_label=str(walled_rule.get("pass_label", PASS_GUANAI)),
            catalog_dir=str(data.get("catalog", "data/myvillage/worldgen/region_profile")),
        )


# Known worldgen subjects the mod owns today. An admitted_subjects entry must
# name one of these. Extend here as new self-generating subjects land.
KNOWN_SUBJECTS: FrozenSet[str] = frozenset({"sect"})


class UnsatisfiableRuleset(Exception):
    """Raised when the authored ruleset/catalog cannot yield a legal graph.

    Generation is constructive (no re-roll), so this only fires on bad *input*,
    never mid-generation — it is the explicit report the design requires instead
    of looping.
    """


# --------------------------------------------------------------------------- #
# Loaders.
# --------------------------------------------------------------------------- #

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RULESET_PATH = (
    REPO_ROOT
    / "src"
    / "main"
    / "resources"
    / "data"
    / "myvillage"
    / "worldgen"
    / "region_topology.json"
)
DEFAULT_CATALOG_DIR = (
    REPO_ROOT
    / "src"
    / "main"
    / "resources"
    / "data"
    / "myvillage"
    / "worldgen"
    / "region_profile"
)


def load_ruleset(path: Optional[Path] = None) -> Ruleset:
    path = Path(path) if path is not None else DEFAULT_RULESET_PATH
    with path.open("r", encoding="utf-8") as f:
        return Ruleset.from_dict(json.load(f))


def load_catalog_dir(catalog_dir: Optional[Path] = None) -> List[RegionProfile]:
    catalog_dir = Path(catalog_dir) if catalog_dir is not None else DEFAULT_CATALOG_DIR
    profiles: List[RegionProfile] = []
    for p in sorted(catalog_dir.glob("*.json")):
        with p.open("r", encoding="utf-8") as f:
            profiles.append(RegionProfile.from_dict(json.load(f)))
    return profiles


def validate_inputs(ruleset: Ruleset, catalog: Sequence[RegionProfile]) -> None:
    """Reject an unsatisfiable ruleset/catalog up front (no looping)."""
    lo, hi = ruleset.region_count
    if not (1 <= lo <= hi):
        raise UnsatisfiableRuleset(f"region_count range invalid: {ruleset.region_count}")
    tlo, thi = ruleset.tier_range
    if not (tlo <= thi):
        raise UnsatisfiableRuleset(f"tier_range invalid: {ruleset.tier_range}")
    if ruleset.tier_step < 0:
        raise UnsatisfiableRuleset(f"tier_step must be >= 0, got {ruleset.tier_step}")

    anchors = [r for r in catalog if r.role == "anchor"]
    if len(anchors) != 1:
        raise UnsatisfiableRuleset(
            f"catalog must declare exactly one anchor region, found {len(anchors)}"
        )
    if len(catalog) < hi:
        raise UnsatisfiableRuleset(
            f"catalog has {len(catalog)} regions but region_count max is {hi}"
        )
    if len(catalog) < lo:
        raise UnsatisfiableRuleset(
            f"catalog has {len(catalog)} regions but region_count min is {lo}"
        )

    for r in catalog:
        if not (tlo <= r.tier <= thi):
            raise UnsatisfiableRuleset(
                f"region {r.id!r} nominal tier {r.tier} outside tier_range {ruleset.tier_range}"
            )
        bad = [s for s in r.admitted_subjects if s not in KNOWN_SUBJECTS]
        if bad:
            raise UnsatisfiableRuleset(
                f"region {r.id!r} admits unknown subjects {bad}; known = {sorted(KNOWN_SUBJECTS)}"
            )

    # Enough peripheral regions to fill the smallest graph around the anchor
    # (and any walled regions, which are always included).
    n_walled = sum(1 for r in catalog if r.role == "walled")
    n_periph = sum(1 for r in catalog if r.role == "peripheral")
    need_periph = lo - 1 - n_walled
    if need_periph < 0:
        # region_count min smaller than anchor + walled is fine (we just include fewer).
        need_periph = 0
    if n_periph < need_periph:
        raise UnsatisfiableRuleset(
            f"need >= {need_periph} peripheral regions to reach region_count min {lo}, "
            f"catalog has {n_periph}"
        )


# --------------------------------------------------------------------------- #
# Generated graph types.
# --------------------------------------------------------------------------- #


@dataclass
class GenRegion:
    id: str
    display_name: str
    tier: int  # assigned tier (authoritative for invariants)
    role: str
    qi: Tuple[int, int]
    danger: Tuple[int, int]
    admitted_subjects: Tuple[str, ...]
    nominal_tier: int
    position: Tuple[float, float]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "display_name": self.display_name,
            "tier": self.tier,
            "role": self.role,
            "qi": list(self.qi),
            "danger": list(self.danger),
            "admitted_subjects": list(self.admitted_subjects),
            "nominal_tier": self.nominal_tier,
            "position": [round(self.position[0], 4), round(self.position[1], 4)],
        }


@dataclass
class GenEdge:
    a: str
    b: str
    type: str  # 连 | 隔
    separator: Optional[str] = None  # set for 隔
    is_pass: bool = False  # set for a walled region's 关隘 连 edge

    def to_dict(self) -> Dict[str, Any]:
        d: Dict[str, Any] = {"from": self.a, "to": self.b, "type": self.type}
        if self.type == EDGE_GE:
            d["separator"] = self.separator
        if self.is_pass:
            d["pass"] = PASS_GUANAI
        return d


@dataclass
class RegionGraph:
    seed: int
    ruleset_id: str
    count: int
    tier_range: Tuple[int, int]
    tier_step: int
    regions: List[GenRegion]
    edges: List[GenEdge]

    def region_by_id(self, rid: str) -> GenRegion:
        for r in self.regions:
            if r.id == rid:
                return r
        raise KeyError(rid)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "seed": self.seed,
            "ruleset": self.ruleset_id,
            "count": self.count,
            "tier_range": list(self.tier_range),
            "tier_step": self.tier_step,
            "regions": [r.to_dict() for r in self.regions],
            "edges": [e.to_dict() for e in self.edges],
        }


# --------------------------------------------------------------------------- #
# Constructive generator.
# --------------------------------------------------------------------------- #


def _key_pair(a: str, b: str) -> Tuple[str, str]:
    """Return the endpoint ids of an edge pair in a stable order."""
    return (a, b) if a <= b else (b, a)


def _dist(p: Tuple[float, float], q: Tuple[float, float]) -> float:
    return math.hypot(p[0] - q[0], p[1] - q[1])


def _select_regions(
    rng: RegionRng, ruleset: Ruleset, catalog: Sequence[RegionProfile]
) -> List[RegionProfile]:
    """Select the regions for this seed: the anchor, all walled, and a seeded
    sample of peripherals to reach the seed-chosen count."""
    anchor = next(r for r in catalog if r.role == "anchor")
    walled = sorted((r for r in catalog if r.role == "walled"), key=lambda r: r.id)
    peripherals = sorted((r for r in catalog if r.role == "peripheral"), key=lambda r: r.id)

    lo, hi = ruleset.region_count
    count = rng.range(lo, hi, label="region_count")
    need_periph = count - 1 - len(walled)
    if need_periph < 0:
        need_periph = 0
    if need_periph > len(peripherals):
        # Should be prevented by validate_inputs; guard anyway rather than re-roll.
        raise UnsatisfiableRuleset(
            f"need {need_periph} peripherals but catalog has {len(peripherals)}"
        )

    # Deterministic sample: seeded Fisher–Yates over id-sorted peripherals.
    pool = list(peripherals)
    chosen: List[RegionProfile] = []
    for i in range(need_periph):
        j = rng.range(i, len(pool) - 1, label=f"pick_periph:{i}")
        pool[i], pool[j] = pool[j], pool[i]
        chosen.append(pool[i])
    chosen.sort(key=lambda r: r.id)

    return [anchor] + chosen + walled


def _embed(
    rng: RegionRng, ruleset: Ruleset, selected: Sequence[RegionProfile]
) -> Dict[str, Tuple[float, float]]:
    """Place the anchor at the center and embed the rest on radial sectors.

    Walled regions sit on an outer ring (sealed/peripheral read); peripherals
    on the inner ring. Geometry is cosmetic (the data contract is the typed
    edge list) but drives geometric-neighbour detection.
    """
    anchor = next(r for r in selected if r.role == "anchor")
    positions: Dict[str, Tuple[float, float]] = {anchor.id: (0.0, 0.0)}

    peripherals = sorted((r for r in selected if r.role == "peripheral"), key=lambda r: r.id)
    walled = sorted((r for r in selected if r.role == "walled"), key=lambda r: r.id)

    def place_ring(members: Sequence[RegionProfile], radius: float, base_angle: float) -> None:
        n = len(members)
        if n == 0:
            return
        for i, r in enumerate(members):
            angle = base_angle + 2.0 * math.pi * i / n
            # small deterministic jitter on radius so neighbours sort distinctly
            jitter = (hash64(rng.seed, f"embed:{r.id}") % 101 - 50) / 1000.0
            positions[r.id] = (
                round((radius + jitter) * math.cos(angle), 4),
                round((radius + jitter) * math.sin(angle), 4),
            )

    place_ring(peripherals, ruleset.peripheral_ring_radius, base_angle=0.0)
    # walled ring offset by half a sector so a walled region sits between peripherals
    place_ring(
        walled,
        ruleset.walled_ring_radius,
        base_angle=math.pi / max(1, len(peripherals)),
    )
    return positions


def _geometric_edges(
    ruleset: Ruleset,
    selected: Sequence[RegionProfile],
    positions: Mapping[str, Tuple[float, float]],
    anchor_id: str,
) -> Set[Tuple[str, str]]:
    """Candidate adjacency edges from geometry.

    The anchor is geometrically adjacent to every other region (it is the
    central hub); each non-anchor region is additionally adjacent to its
    ``peripheral_nearest_peers`` nearest peers. This graph is connected through
    the anchor, which is what lets the 连 spanning tree be built by construction.
    """
    edges: Set[Tuple[str, str]] = set()
    non_anchor = [r for r in selected if r.id != anchor_id]
    for r in non_anchor:
        edges.add(_key_pair(anchor_id, r.id))
    k = max(0, ruleset.peripheral_nearest_peers)
    for r in non_anchor:
        others = [o for o in non_anchor if o.id != r.id]
        others.sort(key=lambda o: (_dist(positions[r.id], positions[o.id]), o.id))
        for o in others[:k]:
            edges.add(_key_pair(r.id, o.id))
    return edges


def _spanning_tree(
    non_walled_ids: Set[str],
    geo_edges: Set[Tuple[str, str]],
    anchor_id: str,
) -> Tuple[Set[Tuple[str, str]], Dict[str, str]]:
    """BFS spanning tree over non-walled regions rooted at the anchor.

    Returns the tree-edge set and a child -> parent map. Connected by
    construction (the anchor hubs the geometric graph); if the non-walled
    geometric subgraph were somehow disconnected the input is unsatisfiable.
    """
    adj: Dict[str, Set[str]] = defaultdict(set)
    for a, b in geo_edges:
        if a in non_walled_ids and b in non_walled_ids:
            adj[a].add(b)
            adj[b].add(a)
    tree_edges: Set[Tuple[str, str]] = set()
    parent: Dict[str, str] = {}
    visited: Set[str] = {anchor_id}
    frontier: deque[str] = deque([anchor_id])
    while frontier:
        u = frontier.popleft()
        for v in sorted(adj[u]):
            if v not in visited:
                visited.add(v)
                parent[v] = u
                tree_edges.add(_key_pair(u, v))
                frontier.append(v)
    if visited != non_walled_ids:
        missing = sorted(non_walled_ids - visited)
        raise UnsatisfiableRuleset(
            f"non-walled geometric graph disconnected; unreachable: {missing}"
        )
    return tree_edges, parent


def generate(seed: int, ruleset: Ruleset, catalog: Sequence[RegionProfile]) -> RegionGraph:
    """Construct a region graph for ``seed``. Deterministic; never re-rolls.

    Most-constrained-first placement order (so a satisfiable ruleset never
    dead-ends):

    1. anchor — must be centered and hold the top tier (most constrained).
    2. spanning-tree BFS outward — each node's tier is fixed by its already
       placed parent.
    3. walled regions — tier fixed from a chosen neighbour; all incident edges
       隔 except possibly one 关隘.
    """
    validate_inputs(ruleset, catalog)
    rng = RegionRng(seed)
    tlo, thi = ruleset.tier_range
    n_step = ruleset.tier_step

    selected = _select_regions(rng, ruleset, catalog)
    positions = _embed(rng, ruleset, selected)
    anchor = next(r for r in selected if r.role == "anchor")
    geo_edges = _geometric_edges(ruleset, selected, positions, anchor.id)

    walled_ids = {r.id for r in selected if r.role == "walled"}
    non_walled_ids = {r.id for r in selected if r.role != "walled"}

    tree_edges, parent = _spanning_tree(non_walled_ids, geo_edges, anchor.id)

    # --- Tier assignment (constructive, outward from the anchor). ---
    tiers: Dict[str, int] = {anchor.id: thi}  # anchor holds the top tier.
    # BFS order is the placement order; the parent is always already placed.
    order: List[str] = [anchor.id]
    seen: Set[str] = {anchor.id}
    queue: deque[str] = deque([anchor.id])
    while queue:
        u = queue.popleft()
        children = sorted(c for c, p in parent.items() if p == u)
        for c in children:
            if c in seen:
                continue
            seen.add(c)
            # The anchor's direct children step down by at least 1 so the anchor
            # is unambiguously the highest tier; deeper tree levels may hold
            # (d == 0). Both clamp into tier_range, which can only narrow |Δ|.
            lo_d = 1 if (u == anchor.id and n_step >= 1) else 0
            d = rng.range(lo_d, n_step, label=f"tier:{c}")
            tiers[c] = max(tlo, tiers[u] - d)
            order.append(c)
            queue.append(c)

    # --- Type edges. ---
    typed: Dict[Tuple[str, str], GenEdge] = {}

    # Tree edges are 连 by construction (tier-step guaranteed: child = parent - d, d <= N).
    for a, b in tree_edges:
        typed[(a, b)] = GenEdge(a=a, b=b, type=EDGE_LIAN)

    # Non-tree edges among non-walled regions: rule-typed.
    for (a, b) in sorted(geo_edges):
        if (a, b) in typed:
            continue
        if a in walled_ids or b in walled_ids:
            continue  # handled by the walled rule below
        dtier = abs(tiers[a] - tiers[b])
        if dtier > n_step:
            sep = rng.pick(ruleset.separator_palette, label=f"sep:{a}:{b}")
            typed[(a, b)] = GenEdge(a=a, b=b, type=EDGE_GE, separator=sep)
        else:
            if rng.chance(ruleset.non_tree_edge_lian_chance, label=f"lian:{a}:{b}"):
                typed[(a, b)] = GenEdge(a=a, b=b, type=EDGE_LIAN)
            else:
                sep = rng.pick(ruleset.separator_palette, label=f"sep:{a}:{b}")
                typed[(a, b)] = GenEdge(a=a, b=b, type=EDGE_GE, separator=sep)

    # --- Walled-region rule: sealed except at most one 关隘. ---
    def _other_of(edge: Tuple[str, str], wid: str) -> str:
        return edge[1] if edge[0] == wid else edge[0]

    for w in sorted((r for r in selected if r.role == "walled"), key=lambda r: r.id):
        # Order incident edges by true distance from the walled region to the
        # OTHER endpoint, so the nearest neighbour is the natural 关隘 candidate.
        w_edges = sorted(
            (e for e in geo_edges if w.id in e),
            key=lambda e: (_dist(positions[w.id], positions[_other_of(e, w.id)]), _other_of(e, w.id)),
        )

        gate: Optional[Tuple[str, str]] = None
        if w_edges and rng.chance(ruleset.walled_gate_chance, label=f"gate:{w.id}"):
            # Retain one 关隘 to the nearest neighbour; fix the walled tier from
            # it so the 连 edge respects the tier-step by construction.
            gate = w_edges[0]
            other = _other_of(gate, w.id)
            d = rng.range(0, n_step, label=f"wtier:{w.id}")
            tiers[w.id] = max(tlo, tiers[other] - d)
            typed[gate] = GenEdge(
                a=gate[0], b=gate[1], type=EDGE_LIAN, is_pass=True
            )
        else:
            # Fully sealed; still give the walled region a tier derived from its
            # nearest neighbour for downstream consumers.
            if w_edges:
                other = _other_of(w_edges[0], w.id)
                d = rng.range(0, n_step, label=f"wtier:{w.id}")
                tiers[w.id] = max(tlo, tiers[other] - d)
            else:
                tiers[w.id] = thi
        # All remaining walled-incident edges are 隔.
        for e in w_edges:
            if e == gate:
                continue
            sep = rng.pick(ruleset.separator_palette, label=f"wsep:{w.id}:{_other_of(e, w.id)}")
            typed[e] = GenEdge(a=e[0], b=e[1], type=EDGE_GE, separator=sep)

    # --- Assemble. ---
    regions = [
        GenRegion(
            id=r.id,
            display_name=r.display_name,
            tier=tiers[r.id],
            role=r.role,
            qi=r.qi,
            danger=r.danger,
            admitted_subjects=r.admitted_subjects,
            nominal_tier=r.tier,
            position=positions[r.id],
        )
        for r in selected
    ]
    regions.sort(key=lambda r: r.id)
    edges = [typed[e] for e in sorted(typed)]

    return RegionGraph(
        seed=seed,
        ruleset_id=ruleset.id,
        count=len(regions),
        tier_range=ruleset.tier_range,
        tier_step=ruleset.tier_step,
        regions=regions,
        edges=edges,
    )


def graph_from_dict(data: Mapping[str, Any]) -> RegionGraph:
    """Reconstruct a graph from its serialized form (for the validator)."""
    regions = [
        GenRegion(
            id=str(r["id"]),
            display_name=str(r["display_name"]),
            tier=int(r["tier"]),
            role=str(r["role"]),
            qi=_as_pair(r["qi"]),
            danger=_as_pair(r["danger"]),
            admitted_subjects=tuple(str(s) for s in r.get("admitted_subjects", [])),
            nominal_tier=int(r.get("nominal_tier", r["tier"])),
            position=(float(r["position"][0]), float(r["position"][1])),
        )
        for r in data["regions"]
    ]
    edges = [
        GenEdge(
            a=str(e["from"]),
            b=str(e["to"]),
            type=str(e["type"]),
            separator=str(e["separator"]) if e.get("separator") is not None else None,
            is_pass=bool(e.get("pass") == PASS_GUANAI),
        )
        for e in data["edges"]
    ]
    return RegionGraph(
        seed=int(data["seed"]),
        ruleset_id=str(data.get("ruleset", "")),
        count=int(data["count"]),
        tier_range=_as_pair(data["tier_range"]),
        tier_step=int(data["tier_step"]),
        regions=regions,
        edges=edges,
    )


def assert_deterministic(
    seed: int, ruleset: Ruleset, catalog: Sequence[RegionProfile]
) -> None:
    """Regenerate twice and assert byte-identical output (no re-roll drift)."""
    g1 = generate(seed, ruleset, catalog)
    g2 = generate(seed, ruleset, catalog)
    if json.dumps(g1.to_dict(), sort_keys=True) != json.dumps(g2.to_dict(), sort_keys=True):
        raise AssertionError(f"region topology not deterministic for seed {seed}")


__all__ = [
    "EDGE_GE",
    "EDGE_LIAN",
    "PASS_GUANAI",
    "SEP_MOUNTAIN",
    "SEP_OCEAN",
    "GenEdge",
    "GenRegion",
    "RegionGraph",
    "RegionProfile",
    "RegionRng",
    "Ruleset",
    "UnsatisfiableRuleset",
    "assert_deterministic",
    "generate",
    "graph_from_dict",
    "hash64",
    "load_catalog_dir",
    "load_ruleset",
    "validate_inputs",
]
