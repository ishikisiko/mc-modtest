"""Physical contact between partial blocks (slabs, stairs, trapdoors).

Every block is modelled as the set of octants (half-block cubes) of its cell it
fills: a bottom slab fills the four lower octants, a top slab the four upper
ones, a stair its slab half plus one, two or three octants of the other half
(``shape``), an open trapdoor the four octants against its hinge side. Two
neighbouring cells touch when the octants on their shared face overlap, so:

  * a bottom slab touches a bottom slab or bottom stair beside it, and a full
    block beside or below it;
  * a top slab one block above a bottom slab does NOT touch it (half a block
    of air between them), nor does a bottom slab sitting on a fence or wall
    post (posts are thin and do not count as support).

Posts (walls, fences), panes, lanterns, signs and other thin blocks fill no
octants: they neither count as support nor are checked themselves.
"""

from __future__ import annotations

from typing import Callable, Dict, FrozenSet, Iterable, List, Optional, Tuple

Pos = Tuple[int, int, int]
Octant = Tuple[int, int, int]  # (x, y, z) each 0 or 1 inside the cell

AIR_BLOCKS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air",
              "minecraft:structure_void"}
# Block-id fragments of thin / non-full blocks that never count as support.
THIN_MARKERS = (
    "_wall", "_fence", "fence_gate", "pane", "iron_bars", "lantern", "chain",
    "torch", "button", "carpet", "_door", "sign", "banner", "flower_pot",
    "bell", "ladder", "rail", "candle", "plaque", "brazier", "pedestal",
    "_bed", "water", "lava", "awning", "rope", "lever", "pressure_plate",
    "_head", "skull", "campfire", "cake", "snow", "scaffolding", "vine",
    "short_grass", "tall_grass", "sapling", "tripwire", "sconce", "end_rod",
    "chandelier", "shutter", "curtain_rod", "item_shelf", "jar", "basket",
    "rack", "holder",
)
# Full cubes whose ids happen to contain a thin-block fragment.
FULL_EXCEPTIONS = {"minecraft:sea_lantern", "minecraft:jack_o_lantern",
                   "minecraft:snow_block"}
PARTIAL_SUFFIXES = ("_slab", "_stairs", "_trapdoor")
FACES: Tuple[Pos, ...] = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0),
                          (0, 0, 1), (0, 0, -1))
HORIZONTAL = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
_ALL: FrozenSet[Octant] = frozenset(
    (x, y, z) for x in (0, 1) for y in (0, 1) for z in (0, 1))


def block_id(state: str) -> str:
    return state.split("[", 1)[0]


def props(state: str) -> Dict[str, str]:
    if "[" not in state:
        return {}
    body = state[state.index("[") + 1:state.rindex("]")]
    out: Dict[str, str] = {}
    for kv in body.split(","):
        if "=" in kv:
            k, v = kv.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def is_partial(state: str) -> bool:
    """Slabs, stairs and trapdoors: the cells the contact rule checks."""
    return block_id(state).endswith(PARTIAL_SUFFIXES)


def _on_side(o: Octant, dx: int, dz: int) -> bool:
    return ((dx == 0 or o[0] == (1 if dx > 0 else 0))
            and (dz == 0 or o[2] == (1 if dz > 0 else 0)))


def octants(state: Optional[str]) -> FrozenSet[Octant]:
    if not state:
        return frozenset()
    bid = block_id(state)
    if bid in AIR_BLOCKS:
        return frozenset()
    p = props(state)
    if bid.endswith("_slab"):
        kind = p.get("type", "bottom")
        if kind == "double":
            return _ALL
        level = 1 if kind == "top" else 0
        return frozenset(o for o in _ALL if o[1] == level)
    if bid.endswith("_stairs"):
        base = 1 if p.get("half", "bottom") == "top" else 0
        dx, dz = HORIZONTAL[p.get("facing", "north")]
        lx, lz = dz, -dx           # the stair's left, seen walking up it
        rx, rz = -lx, -lz
        shape = p.get("shape", "straight")
        upper = [o for o in _ALL if o[1] != base]
        if shape == "outer_left":
            step = [o for o in upper if _on_side(o, dx, dz) and _on_side(o, lx, lz)]
        elif shape == "outer_right":
            step = [o for o in upper if _on_side(o, dx, dz) and _on_side(o, rx, rz)]
        elif shape == "inner_left":
            step = [o for o in upper if _on_side(o, dx, dz) or _on_side(o, lx, lz)]
        elif shape == "inner_right":
            step = [o for o in upper if _on_side(o, dx, dz) or _on_side(o, rx, rz)]
        else:
            step = [o for o in upper if _on_side(o, dx, dz)]
        return frozenset([o for o in _ALL if o[1] == base] + step)
    if bid.endswith("_trapdoor"):
        if p.get("open") == "true":
            dx, dz = HORIZONTAL[p.get("facing", "north")]
            # An open trapdoor lies against the side opposite its facing.
            return frozenset(o for o in _ALL if _on_side(o, -dx, -dz))
        level = 1 if p.get("half") == "top" else 0
        return frozenset(o for o in _ALL if o[1] == level)
    if bid not in FULL_EXCEPTIONS and any(m in bid for m in THIN_MARKERS):
        return frozenset()
    return _ALL


def is_full_block(state: Optional[str]) -> bool:
    return len(octants(state)) == len(_ALL)


def _face(occ: Iterable[Octant], d: Pos) -> FrozenSet[Tuple[int, int]]:
    axis = 0 if d[0] else (1 if d[1] else 2)
    side = 1 if d[axis] > 0 else 0
    return frozenset(tuple(c for i, c in enumerate(o) if i != axis)
                     for o in occ if o[axis] == side)


def touches(state_at: Callable[[Pos], Optional[str]], pos: Pos,
            ground_y: Optional[int] = 0) -> bool:
    """True when the block at ``pos`` shares face area with a neighbour.

    ``ground_y``: cells at this y rest on the terrain below (a template's
    bottom layer replaces the ground block). ``None`` disables that.
    """
    mine = octants(state_at(pos))
    if not mine:
        return False
    for d in FACES:
        if ground_y is not None and d == (0, -1, 0) and pos[1] == ground_y:
            if _face(mine, d):
                return True
            continue
        n = (pos[0] + d[0], pos[1] + d[1], pos[2] + d[2])
        theirs = octants(state_at(n))
        if not theirs:
            continue
        if _face(mine, d) & _face(theirs, (-d[0], -d[1], -d[2])):
            return True
    return False


def detached_partials(cells: Dict[Pos, str],
                      ground_y: Optional[int] = 0) -> List[Tuple[Pos, str]]:
    """Slab/stairs/trapdoor cells that touch no neighbour face to face."""
    out = []
    for pos, state in cells.items():
        if is_partial(state) and not touches(cells.get, pos, ground_y):
            out.append((pos, state))
    return sorted(out)


def top_height(state: Optional[str]) -> int:
    """Highest filled level of a cell in half blocks (0, 1 or 2)."""
    occ = octants(state)
    if not occ:
        return 0
    return 2 if any(o[1] == 1 for o in occ) else 1


def face_height(state: Optional[str], direction: str) -> int:
    """Height in half blocks the cell reaches on its ``direction`` face.

    A straight eave stair climbing inward is 1 on its outer face; an
    outer-corner stair whose raised quarter sits on the outer tip is 2 there.
    """
    dx, dz = HORIZONTAL[direction]
    occ = [o for o in octants(state) if _on_side(o, dx, dz)]
    if not occ:
        return 0
    return 2 if any(o[1] == 1 for o in occ) else 1


def is_attached_upturn(state_at: Callable[[Pos], Optional[str]],
                       pos: Pos) -> bool:
    """An upturned eave corner as the contact rule allows it.

    The corner must be a slab or stair cell that shares face area with a
    neighbour (an eave cell on its own level, or a full block beside or
    below it). A slab lifted a block above the eave line with air around it
    fails.
    """
    state = state_at(pos)
    return bool(state) and is_partial(state) and touches(state_at, pos, None)
