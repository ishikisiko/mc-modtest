"""Family-aware blockstate orientation adapter for generated block families."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Dict, Mapping, Tuple

Orientation = Mapping[str, object]
Resolver = Callable[[str, str, Orientation], str]


@dataclass(frozen=True)
class BlockFamily:
    name: str
    properties: Tuple[str, ...]
    resolver: Resolver


_FAMILIES: Dict[str, BlockFamily] = {}


def register_family(name: str, properties: Tuple[str, ...],
                    resolver: Resolver) -> None:
    _FAMILIES[name] = BlockFamily(name, properties, resolver)


def family(name: str) -> BlockFamily:
    try:
        return _FAMILIES[name]
    except KeyError:
        raise ValueError(f"unregistered block orientation family {name!r}") from None


def orient_block(family_name: str, block_id: str, role: str,
                 **orientation: object) -> str:
    return family(family_name).resolver(block_id, role, orientation)


def registered_families() -> Tuple[str, ...]:
    return tuple(sorted(_FAMILIES))


def _require(orientation: Orientation, key: str) -> str:
    value = orientation.get(key)
    if value is None:
        raise ValueError(f"missing orientation value {key!r}")
    return str(value)


def _bool(value: object) -> str:
    return "true" if bool(value) else "false"


def _vertical_bottom(orientation: Orientation) -> bool:
    if "bottom" in orientation:
        return bool(orientation["bottom"])
    return str(orientation.get("vertical", "bottom")) in (
        "bottom", "lower", "underside")


def _vanilla_stairs(block_id: str, role: str, orientation: Orientation) -> str:
    facing = _require(orientation, "facing")
    half = str(orientation.get("half", "bottom"))
    shape = str(orientation.get("shape", "straight"))
    waterlogged = _bool(orientation.get("waterlogged", False))
    return (
        f"{block_id}[facing={facing},half={half},shape={shape},"
        f"waterlogged={waterlogged}]"
    )


def _vanilla_slab(block_id: str, role: str, orientation: Orientation) -> str:
    kind = str(orientation.get("kind", orientation.get("type", "bottom")))
    waterlogged = _bool(orientation.get("waterlogged", False))
    return f"{block_id}[type={kind},waterlogged={waterlogged}]"


def _supplementaries_awning(block_id: str, role: str,
                            orientation: Orientation) -> str:
    facing = _require(orientation, "facing")
    sloped_roles = {"eave", "canopy", "roof_edge"}
    slanted = bool(orientation.get("slanted", role in sloped_roles))
    bottom = _vertical_bottom(orientation)
    return (
        f"{block_id}[facing={facing},bottom={_bool(bottom)},"
        f"slanted={_bool(slanted)}]"
    )


register_family(
    "vanilla_stairs",
    ("facing", "half", "shape", "waterlogged"),
    _vanilla_stairs,
)
register_family(
    "vanilla_slab",
    ("type", "waterlogged"),
    _vanilla_slab,
)
register_family(
    "supplementaries:awning",
    ("facing", "bottom", "slanted"),
    _supplementaries_awning,
)
