"""Massing Graph layer.

A building is a graph of attached volumes/patches, never one big box.
Coordinate conventions (matching test_house_01..03):
  - front of a building is at LOW z; back at high z
  - +x = "east", -x = "west", -z front outward = "north", +z = "south"
  - y=0 is the bottom of the foundation (placed at ground level in game)
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

Pos = Tuple[int, int, int]

# outward direction vectors per wall name (front wall faces -z)
WALL_OUTWARD = {
    "front": (0, -1),
    "back": (0, 1),
    "west": (-1, 0),
    "east": (1, 0),
}

# blockstate facing names, following the visually verified conventions in
# docs/blockstate_notes.md (trapdoor trim facing = outward, door/stair = inward)
OUTWARD_FACING = {"front": "north", "back": "south", "west": "west", "east": "east"}
INWARD_FACING = {"front": "south", "back": "north", "west": "east", "east": "west"}


@dataclass
class Node:
    id: str
    type: str
    origin: Pos
    size: Tuple[int, int, int]
    attach_to: Optional[str] = None
    side: Optional[str] = None
    orientation: str = "front"
    priority: int = 20
    tags: List[str] = field(default_factory=list)
    meta: dict = field(default_factory=dict)

    @property
    def x0(self) -> int: return self.origin[0]
    @property
    def y0(self) -> int: return self.origin[1]
    @property
    def z0(self) -> int: return self.origin[2]
    @property
    def x1(self) -> int: return self.origin[0] + self.size[0] - 1
    @property
    def y1(self) -> int: return self.origin[1] + self.size[1] - 1
    @property
    def z1(self) -> int: return self.origin[2] + self.size[2] - 1

    def to_dict(self) -> dict:
        d = {
            "id": self.id,
            "type": self.type,
            "origin": list(self.origin),
            "size": list(self.size),
            "orientation": self.orientation,
            "priority": self.priority,
            "tags": list(self.tags),
        }
        if self.attach_to:
            d["attach_to"] = self.attach_to
        if self.side:
            d["side"] = self.side
        if self.meta:
            d["meta"] = {k: v for k, v in self.meta.items() if not k.startswith("_")}
        return d


VOLUME_TYPES = {"main_volume", "side_wing", "shed", "rear_shed"}


@dataclass
class MassingGraph:
    nodes: List[Node] = field(default_factory=list)
    meta: dict = field(default_factory=dict)

    def add(self, node: Node) -> Node:
        if any(n.id == node.id for n in self.nodes):
            raise ValueError(f"duplicate node id {node.id}")
        self.nodes.append(node)
        return node

    def get(self, node_id: str) -> Node:
        for n in self.nodes:
            if n.id == node_id:
                return n
        raise KeyError(node_id)

    def by_type(self, *types: str) -> List[Node]:
        return [n for n in self.nodes if n.type in types]

    def volumes(self) -> List[Node]:
        return [n for n in self.nodes if n.type in VOLUME_TYPES]

    def to_dict(self) -> dict:
        return {"meta": self.meta, "nodes": [n.to_dict() for n in self.nodes]}
