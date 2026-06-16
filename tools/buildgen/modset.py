"""Modset profile resolution — one source of truth for generation and validation.

A profile names the active mod set. `vanilla` permits only the `minecraft`
namespace; `full` permits `minecraft` plus the confirmed mod namespaces from the
Phase 0 catalog (`exmod/mod_block_catalog.json`). Generators resolve their slot
namespace filter here (via `style.modset_namespaces`); validators resolve block-id
legality here. Reading both from the same catalog is what keeps the two from
drifting.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import FrozenSet, List, Optional, Set

from .style import (
    MOD_BLOCK_CATALOG_PATH,
    VANILLA_NAMESPACE,
    _block_id,
    _namespace,
    modset_namespaces,
)

SELF_NAMESPACE = "myvillage"


def modset_block_ids(profile: str, catalog_path: Optional[str] = None) -> Set[str]:
    """Legal external-mod block ids for a profile (excludes the minecraft namespace)."""
    if profile == "vanilla":
        return set()
    if profile != "full":
        raise KeyError(f"unknown modset profile {profile!r}")
    path = catalog_path or MOD_BLOCK_CATALOG_PATH
    with open(path, "r", encoding="utf-8") as f:
        catalog = json.load(f)
    confirmed = set(catalog["confirmed_mod_namespaces"])
    ids: Set[str] = set()
    for namespace, entries in catalog["namespaces"].items():
        if namespace not in confirmed:
            continue
        for entry in entries:
            ids.add(entry["id"])
    return ids


@dataclass(frozen=True)
class ModsetProfile:
    name: str
    namespaces: FrozenSet[str]
    mod_block_ids: FrozenSet[str]

    def palette_block_errors(self, palette: List[str]) -> List[str]:
        """Modset-aware legality of non-minecraft palette ids.

        Vanilla (`minecraft:`) ids are never inspected here — they remain the
        concern of the registry-based checks each validator already runs.
        """
        forbidden: Set[str] = set()
        unknown: Set[str] = set()
        for state in palette:
            block = _block_id(state)
            namespace = _namespace(state)
            if namespace in (VANILLA_NAMESPACE, SELF_NAMESPACE):
                continue
            if namespace not in self.namespaces:
                forbidden.add(block)
            elif block not in self.mod_block_ids:
                unknown.add(block)
        errors: List[str] = []
        if forbidden:
            errors.append(f"forbidden_mod_blocks: {sorted(forbidden)}")
        if unknown:
            errors.append(f"unknown_mod_blocks: {sorted(unknown)}")
        return errors


def load_modset(profile: str, catalog_path: Optional[str] = None) -> ModsetProfile:
    namespaces = modset_namespaces(profile, catalog_path)
    ids = modset_block_ids(profile, catalog_path)
    return ModsetProfile(profile, frozenset(namespaces), frozenset(ids))
