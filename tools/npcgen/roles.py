"""The role map of a look: which texels are hair or iris, and at which tone, so the client can recolour
a baked texture to a person's portrait colours (Java `portrait/NpcSkinComposer`).

Standard library only. The map has the texture's size, RGBA:
  (0, 0, 0, 0)          keep the baked texel
  (1, index, 0, 255)    hair: the painter's half-step tone 0..12 on the 7-step HAIR ramp
  (2, index, 0, 255)    iris: row 0..2 (IRIS_TOP, IRIS, IRIS_LOW), or 3 for IRIS_LOW mixed half way
                        toward EYE_WHITE

It is found by probing, not by instrumenting the painter's arithmetic: a painter is a pure function of
the texel and of module-level colour constants looked up at call time, so the texture is painted again
with HAIR (the def's global and the painter's `HAIR` attribute) swapped for a grey probe ramp, and
again with the three IRIS constants swapped for probe colours; the texels that change are the role
texels, and the probe colour they take says the index.
"""
from __future__ import annotations

import contextlib

from .humanoid import _mix, _rgb

MATERIAL_HAIR = 1
MATERIAL_IRIS = 2
HAIR_TONES = 13            # half steps 0..12 on a 7-step ramp
IRIS_WHITE_MIX = 3         # the iris index of IRIS_LOW mixed toward EYE_WHITE
EYE_WHITE = "#F8FAFF"      # every def's EYE_WHITE; Java NpcSkinComposer.EYE_WHITE
KEEP = (0, 0, 0, 0)

# Two grey hair probes: step i is (v, v, v) with v = 20 i, then v = 255 - 20 i. A half-step tone k
# lands on v = 10 k (or 255 - 10 k) exactly, since every mix of two neighbouring steps is a whole
# number. A hair texel always differs between the two probes; anything else paints the same.
_HAIR_PROBES = (
    (tuple((20 * i,) * 3 for i in range(7)), lambda g: g),
    (tuple((255 - 20 * i,) * 3 for i in range(7)), lambda g: 255 - g),
)
_IRIS_PROBE = ("#0A0A0A", "#141414", "#1E1E1E")   # IRIS_TOP, IRIS, IRIS_LOW
_IRIS_NAMES = ("IRIS_TOP", "IRIS", "IRIS_LOW")


class RoleMapError(ValueError):
    """A texel the role map cannot describe (hair mixed with something else, an unknown iris colour)."""


def _where(t):
    return f"texel {t.cube} {t.face} (u={t.u}, v={t.v})"


def _rgba(c):
    if c is None:
        return None
    c = tuple(c)
    return c + (255,) if len(c) == 3 else c


@contextlib.contextmanager
def _patched(module, values):
    """Set module globals for the duration; restored even when painting fails."""
    saved = {k: getattr(module, k) for k in values}
    try:
        for k, v in values.items():
            setattr(module, k, v)
        yield
    finally:
        for k, v in saved.items():
            setattr(module, k, v)


def _paint(built, values=None, hair=None):
    """Every texel's colour (RGBA, or None for a hole) with the def's globals set to `values` and, when
    given, the painter's HAIR attribute set to `hair`."""
    d = built.d
    with _patched(d, values or {}):
        painter = d.painter(built.model)
        if hair is not None:
            painter.HAIR = hair  # the instance attribute shadows _Paint.HAIR, read as self.HAIR
        return [_rgba(painter(t)) for t in built.texels]


def _hair_index(t, colour, decode):
    r, g, b, _ = colour
    if not (r == g == b):
        raise RoleMapError(f"{_where(t)}: hair probe gave {colour}, not a grey (hair mixed with something else?)")
    v = decode(g)
    if v % 10 or not 0 <= v <= 10 * (HAIR_TONES - 1):
        raise RoleMapError(f"{_where(t)}: hair probe value {g} is not a half-step tone (hair mixed with something else?)")
    return v // 10


def export(built):
    """The role map of a built look (`build.Built`), as RGBA rows."""
    d = built.d
    if _rgb(d.EYE_WHITE) != _rgb(EYE_WHITE):
        raise RoleMapError(f"{d.NAME}: EYE_WHITE {d.EYE_WHITE} is not the composer's {EYE_WHITE}")
    base = _paint(built)
    for t, c in zip(built.texels, base):
        if c is not None and built.texture[t.v][t.u] != c:
            raise RoleMapError(f"{_where(t)}: the painter is not deterministic")

    probes = [_paint(built, {"HAIR": ramp}, hair=ramp) for ramp, _ in _HAIR_PROBES]
    iris_probe = _paint(built, dict(zip(_IRIS_NAMES, _IRIS_PROBE)))
    iris_colours = {_rgb(h) + (255,): i for i, h in enumerate(_IRIS_PROBE)}
    iris_colours[_mix(_rgb(_IRIS_PROBE[2]), _rgb(d.EYE_WHITE), 0.5) + (255,)] = IRIS_WHITE_MIX

    w, h = built.model.texture_size
    rows = [[KEEP] * w for _ in range(h)]
    for k, t in enumerate(built.texels):
        a = base[k]
        seen = [p[k] for p in probes] + [iris_probe[k]]
        if a is None:
            if any(c is not None for c in seen):
                raise RoleMapError(f"{_where(t)}: a probe filled a hole")
            continue
        if any(c is None or c[3] != a[3] for c in seen):
            raise RoleMapError(f"{_where(t)}: a probe changed the texel's alpha")
        role = None
        if any(c != a for c in seen[:2]):
            indices = {_hair_index(t, c, decode) for c, (_, decode) in zip(seen[:2], _HAIR_PROBES)}
            if len(indices) != 1:
                raise RoleMapError(f"{_where(t)}: the two hair probes disagree ({sorted(indices)})")
            role = (MATERIAL_HAIR, indices.pop(), 0, 255)
        if seen[2] != a:
            if role is not None:
                raise RoleMapError(f"{_where(t)}: both hair and iris")
            if seen[2] not in iris_colours:
                raise RoleMapError(f"{_where(t)}: iris probe gave {seen[2]}, not an iris row or the white mix")
            role = (MATERIAL_IRIS, iris_colours[seen[2]], 0, 255)
        if role is not None:
            rows[t.v][t.u] = role
    return rows


def repaint(built, hair, iris):
    """The look painted afresh by its own painter with HAIR set to `hair` (7 RGB tuples) and the iris
    rows (IRIS_TOP, IRIS, IRIS_LOW) set to `iris` (3 RGB tuples), as RGBA rows. The recolour of the
    baked texture through the role map must equal it (tools/tests/test_npcgen_roles.py)."""
    hair = tuple(tuple(c) for c in hair)
    values = {"HAIR": hair}
    values.update(zip(_IRIS_NAMES, ("#%02X%02X%02X" % tuple(c) for c in iris)))
    w, h = built.model.texture_size
    rows = [[KEEP] * w for _ in range(h)]
    for t, c in zip(built.texels, _paint(built, values, hair=hair)):
        if c is not None:
            rows[t.v][t.u] = c
    return rows


def counts(rows):
    """{(material, index): texel count} of a role map."""
    out = {}
    for row in rows:
        for r, g, _, a in row:
            if a:
                out[(r, g)] = out.get((r, g), 0) + 1
    return out
