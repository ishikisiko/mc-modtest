"""Deterministic 反推山形 mountain derivation from a sect terrace profile.

`build-sect-compound` (``buildgen.sect``) exports a terrace profile — terrace
elevations + bounds as the mountain skeleton, the forecourt rectangle, plus
rise/depth/taper/cliff-back-height geometry. ``add-sect-worldgen`` derives the
man-made mountain from exactly that profile rather than searching for matching
natural terrain (反推山形). Inside the compound core (the terraces' bounding box)
and on the forecourt there is no noise: terraces and the forecourt sit at their
floor, the band between two terraces at the upper floor within the upper
terrace's width (else the lower floor), and the taper strips beside the
narrower terraces slope down one block per block from the nearest terrace.
Outside the core a seed-driven noisy flank grades through an outer blend skirt
into the surrounding natural heightmap, a sheer cliff face rises behind the
summit, and — when the compound builds the detached-spire feature — a solitary
peak is raised under the detached volume. (The cloud sea is gone: the retaining
bands used to bury it anyway.)

This module is the offline mirror/validation of the runtime Java derivation in
``SectMountain.java``: both consume the same terrace profile and produce the
same heightfield from ``seed`` xor cell coordinates (no shared RNG), so the same
seed + site yields the same mountain. Keep ``MOUNTAIN_PARITY`` in lock-step with
the Java constants.

Coordinate convention matches ``buildgen.sect``: ``x`` cross-slope, ``z``
fall-line (foot→summit). Heights are absolute world Y. A terrace's platform
*surface* sits at ``elevation - 1`` (the building floor rests at ``elevation``),
so the derived ground under a terrace footprint is exactly ``elevation - 1`` —
no float, no bury.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable, Dict, List, Mapping, Optional, Tuple

Cell2 = Tuple[int, int]

# --- derivation constants (mirror SectMountain.java) -----------------------
DEFAULT_SKIRT_RADIUS = 24          # cells over which derived height grades to natural
DEFAULT_OUTER_SLOPE = 1            # blocks dropped per cell on the bare outer flank
DEFAULT_NOISE_AMP_INTER = 3        # former band-noise amplitude; unused since the core went noise-free
DEFAULT_NOISE_AMP_OUTER = 5        # noise amplitude on the outer flank
SEAM_SLOPE_LIMIT = 6               # max |Δheight| per cell allowed in the skirt
DEFAULT_SPIRE_GAP = 3              # min air gap between spire and main mountain (cells)

# Constants the Java derivation hardcodes; the validator asserts they agree.
MOUNTAIN_PARITY: Dict[str, int] = {
    "SKIRT_RADIUS": DEFAULT_SKIRT_RADIUS,
    "OUTER_SLOPE": DEFAULT_OUTER_SLOPE,
    "NOISE_AMP_INTER": DEFAULT_NOISE_AMP_INTER,
    "NOISE_AMP_OUTER": DEFAULT_NOISE_AMP_OUTER,
    "SEAM_SLOPE_LIMIT": SEAM_SLOPE_LIMIT,
    "SPIRE_GAP": DEFAULT_SPIRE_GAP,
}


# --- deterministic value noise --------------------------------------------


def _hash2(seed: int, x: int, z: int) -> int:
    """64-bit-ish deterministic hash of (seed, x, z). Mirrors the Java mix."""
    h = (seed & 0xFFFFFFFFFFFFFFFF)
    h ^= (x * 0x9E3779B97F4A7C15) & 0xFFFFFFFFFFFFFFFF
    h = (h * 0xC2B2AE3D27D4EB4F) & 0xFFFFFFFFFFFFFFFF
    h ^= (z * 0x165667B19E3779F9) & 0xFFFFFFFFFFFFFFFF
    h = (h * 0x9E3779B97F4A7C15) & 0xFFFFFFFFFFFFFFFF
    h ^= (h >> 31)
    return h & 0xFFFFFFFFFFFFFFFF


def _smooth(values: List[int], window: int) -> List[int]:
    """Centered moving average, to separate the skirt grade from cell noise."""
    half = window // 2
    out: List[int] = []
    for i in range(len(values)):
        lo = max(0, i - half)
        hi = min(len(values), i + half + 1)
        out.append(round(sum(values[lo:hi]) / (hi - lo)))
    return out


def _noise(seed: int, x: int, z: int, amp: int) -> int:
    """Deterministic integer noise in ``[-amp, amp]`` for cell (x, z)."""
    if amp <= 0:
        return 0
    span = 2 * amp + 1
    return (_hash2(seed, x, z) % span) - amp


# --- profile access --------------------------------------------------------


@dataclass(frozen=True)
class TerraceBox:
    index: int
    name: str
    elevation: int
    x0: int
    z0: int
    x1: int
    z1: int
    cliff_back: bool

    def contains(self, x: int, z: int) -> bool:
        return self.x0 <= x <= self.x1 and self.z0 <= z <= self.z1


def _terraces(profile: Mapping[str, object]) -> List[TerraceBox]:
    out: List[TerraceBox] = []
    for t in profile["terraces"]:  # type: ignore[index]
        x0, z0, x1, z1 = t["bounds"]
        out.append(TerraceBox(
            index=int(t["index"]), name=str(t["name"]), elevation=int(t["elevation"]),
            x0=int(x0), z0=int(z0), x1=int(x1), z1=int(z1),
            cliff_back=bool(t.get("cliff_back", False))))
    return out


@dataclass
class MountainParams:
    skirt_radius: int = DEFAULT_SKIRT_RADIUS
    outer_slope: int = DEFAULT_OUTER_SLOPE
    noise_amp_inter: int = DEFAULT_NOISE_AMP_INTER
    noise_amp_outer: int = DEFAULT_NOISE_AMP_OUTER
    spire_gap: int = DEFAULT_SPIRE_GAP


# --- derivation ------------------------------------------------------------


@dataclass
class DerivedMountain:
    seed: int
    profile: Mapping[str, object]
    params: MountainParams
    terraces: List[TerraceBox]
    # core footprint (terraces' union bounds) in local coordinates
    core_x0: int
    core_z0: int
    core_x1: int
    core_z1: int
    cliff_back_top: int
    rise: int
    spire: Optional["SpirePeak"]
    natural_fn: Callable[[int, int], int]
    # forecourt (x0, z0, x1, z1) held level with the gate floor, or None
    apron: Optional[Tuple[int, int, int, int]] = None

    def in_core(self, x: int, z: int) -> bool:
        return self.core_x0 <= x <= self.core_x1 and self.core_z0 <= z <= self.core_z1

    def on_apron(self, x: int, z: int) -> bool:
        a = self.apron
        return a is not None and a[0] <= x <= a[2] and a[1] <= z <= a[3]

    def core_height(self, x: int, z: int) -> int:
        """Noise-free height inside the core (mirrors ``SectMountain.coreHeight``).

        A terrace's floor; in the band in front of a terrace, the upper floor
        within the upper terrace's width and the lower floor within the lower
        one's; elsewhere (the taper strips beside the narrower terraces) the
        nearest terrace's floor minus the Chebyshev distance to it, ties to the
        higher floor.
        """
        for t in self.terraces:
            if t.contains(x, z):
                return t.elevation - 1
        for lower, upper in zip(self.terraces, self.terraces[1:]):
            if lower.z1 < z < upper.z0:
                if upper.x0 <= x <= upper.x1:
                    return upper.elevation - 1
                if lower.x0 <= x <= lower.x1:
                    return lower.elevation - 1
        best: Optional[int] = None
        best_dist: Optional[int] = None
        for t in self.terraces:
            dx = max(t.x0 - x, 0, x - t.x1)
            dz = max(t.z0 - z, 0, z - t.z1)
            d = max(dx, dz)
            h = t.elevation - 1 - self.params.outer_slope * d
            if best_dist is None or d < best_dist or (d == best_dist and h > best):
                best_dist = d
                best = h
        return int(best)

    def _nearest_terrace_height(self, x: int, z: int) -> Tuple[int, int]:
        """Outer-flank skeleton: the surface of the terrace whose z-band the
        column is nearest to, and the Chebyshev distance to the core box."""
        dx = max(self.core_x0 - x, 0, x - self.core_x1)
        dz = max(self.core_z0 - z, 0, z - self.core_z1)
        dist = max(dx, dz)
        nearest = min(self.terraces, key=lambda t: min(abs(z - t.z0), abs(z - t.z1)))
        return nearest.elevation - 1, dist

    def height(self, x: int, z: int) -> int:
        """Derived absolute world Y of the mountain surface at local (x, z)."""
        # detached-spire feature: a solid pillar under the detached volume rising
        # one terrace-rise above the summit surface, so the volume is a solitary
        # peak (孤峰) reachable only across the flying bridge. Only derived when
        # the compound actually builds the spire (see sect.feature_buildable).
        if self.spire is not None:
            sp = self.spire
            if sp.x0 <= x <= sp.x1 and sp.z0 <= z <= sp.z1:
                return sp.top

        # cliff-back: a sheer face rises directly behind the summit's back edge.
        summit = self.terraces[-1]
        if summit.cliff_back and z > summit.z1 and summit.x0 <= x <= summit.x1:
            back_dist = z - summit.z1
            if back_dist <= 2:
                # sheer face: hold the full cliff-back height (no graded slope)
                return self.cliff_back_top
            # then fall off behind the face toward natural ground (still steep)
            dropped = self.cliff_back_top - self.params.outer_slope * 2 * (back_dist - 2)
            return max(dropped, self.natural_fn(x, z))

        # the forecourt is level with the gate floor
        if self.on_apron(x, z):
            return self.terraces[0].elevation - 1
        # the compound core is noise-free
        if self.in_core(x, z):
            return self.core_height(x, z)

        # outer flank: drop from the skeleton along the slope, add flank noise,
        # then blend into the natural heightmap across the skirt radius.
        skel, dist = self._nearest_terrace_height(x, z)
        flank = skel - self.params.outer_slope * dist
        flank += _noise(self.seed, x, z, self.params.noise_amp_outer)
        natural = self.natural_fn(x, z)
        if dist >= self.params.skirt_radius:
            return natural
        frac = dist / self.params.skirt_radius
        blended = round(flank * (1 - frac) + natural * frac)
        # never sink below natural ground (the mountain only adds relief)
        return max(blended, natural)


@dataclass(frozen=True)
class SpirePeak:
    """Solitary peak (孤峰) raised under a detached-spire feature volume."""
    x0: int
    z0: int
    x1: int
    z1: int
    top: int            # platform surface Y the detached volume rests on
    gap: int            # air gap (cells) separating the spire from the main core


def _build_spire(
    profile: Mapping[str, object],
    terraces: List[TerraceBox],
    core: Tuple[int, int, int, int],
    params: MountainParams,
) -> Optional[SpirePeak]:
    feature = profile.get("feature") if isinstance(profile, dict) else None
    # the terrace profile itself does not carry the feature; callers pass it via
    # the merged plan dict. Accept either embedded feature or absence.
    if not feature:
        return None
    x0, z0, x1, z1 = feature["detached_bounds"]  # type: ignore[index]
    summit = terraces[-1]
    rise = int(profile["geometry"]["terrace_rise"])  # type: ignore[index]
    # the detached volume rests one rise above the summit surface so it stands
    # clear of the platform as a solitary peak; the spire is solid up to it.
    top = (summit.elevation - 1) + rise
    return SpirePeak(x0=int(x0), z0=int(z0), x1=int(x1), z1=int(z1),
                     top=top, gap=params.spire_gap)


def flat_natural(base_y: int) -> Callable[[int, int], int]:
    """A flat natural heightmap at ``base_y`` (validation default)."""
    return lambda x, z: base_y


def noisy_natural(base_y: int, seed: int, amp: int = 4) -> Callable[[int, int], int]:
    """A gently rolling natural heightmap (validation realism)."""
    return lambda x, z: base_y + _noise(seed ^ 0x5DEECE66D, x // 6, z // 6, amp)


def derive_mountain(
    seed: int,
    profile: Mapping[str, object],
    natural_fn: Optional[Callable[[int, int], int]] = None,
    params: Optional[MountainParams] = None,
    feature: Optional[Mapping[str, object]] = None,
) -> DerivedMountain:
    """Derive the mountain heightfield from a terrace profile (反推山形).

    ``feature`` carries the detached spire's ``detached_bounds`` only when the
    compound builds it (``sect.feature_buildable``); the forecourt comes from
    the profile's ``apron``.
    """
    params = params or MountainParams()
    terraces = _terraces(profile)
    if not terraces:
        raise ValueError("terrace profile has no terraces")
    base_y = int(profile["footprint"]["base_y"])  # type: ignore[index]
    natural_fn = natural_fn or flat_natural(base_y)

    core_x0 = min(t.x0 for t in terraces)
    core_z0 = min(t.z0 for t in terraces)
    core_x1 = max(t.x1 for t in terraces)
    core_z1 = max(t.z1 for t in terraces)

    cliff_back_height = int(profile["geometry"]["cliff_back_height"])  # type: ignore[index]
    rise = int(profile["geometry"]["terrace_rise"])  # type: ignore[index]
    summit = terraces[-1]
    cliff_back_top = summit.elevation + cliff_back_height

    # merge the feature (carried separately from the terrace profile) so the
    # spire can be derived from the same plan the realizer uses.
    merged = dict(profile)
    if feature is not None:
        merged["feature"] = feature
    spire = _build_spire(merged, terraces, (core_x0, core_z0, core_x1, core_z1), params)

    apron_raw = profile.get("apron") if isinstance(profile, Mapping) else None
    apron = (tuple(int(v) for v in apron_raw)  # type: ignore[union-attr]
             if apron_raw else None)

    return DerivedMountain(
        seed=seed, profile=profile, params=params, terraces=terraces,
        core_x0=core_x0, core_z0=core_z0, core_x1=core_x1, core_z1=core_z1,
        cliff_back_top=cliff_back_top, rise=rise,
        spire=spire, natural_fn=natural_fn, apron=apron)


# --- validation ------------------------------------------------------------


def validate_mountain(mountain: DerivedMountain) -> dict:
    """Assert the derivation honors the 反推山形 contract.

      * every terrace cell rests at its planned floor (no float/bury);
      * the core and the forecourt are noise-free: bands at the upper floor
        within the upper width else the lower floor, the forecourt at the gate
        floor, taper strips sloping one block per block from the nearest
        terrace, and none of it changes with the seed;
      * the outer flank is noise-textured, not bare steps;
      * the outer blend skirt has no abrupt seam (except the intended cliff-back);
      * a sheer cliff face stands behind the summit;
      * a spire (when the feature is built) stands under the detached volume,
        separated from the main mountain by a gap.
    """
    errors: List[str] = []
    terraces = mountain.terraces

    # terraces at planned elevations (sample corners + center, skipping any cell
    # standing under the raised spire pillar — those are intentionally lifted).
    def _under_spire(x: int, z: int) -> bool:
        sp = mountain.spire
        return sp is not None and sp.x0 <= x <= sp.x1 and sp.z0 <= z <= sp.z1

    for t in terraces:
        bad = [(x, z) for x in range(t.x0, t.x1 + 1) for z in range(t.z0, t.z1 + 1)
               if not _under_spire(x, z) and mountain.height(x, z) != t.elevation - 1]
        if bad:
            errors.append(f"terrace_not_at_elevation:{t.name}:{bad[0]}:"
                          f"{mountain.height(*bad[0])}!={t.elevation - 1}")

    errors.extend(_validate_core(mountain, _under_spire))

    # slopes are textured (noise present on the tall side flank near the summit,
    # where the mountain actually has relief above natural ground)
    summit = terraces[-1]
    flank_heights = set()
    side_x = mountain.core_x1 + max(2, mountain.params.skirt_radius // 4)
    for z in range(summit.z0, summit.z1 + 1, 3):
        flank_heights.add(mountain.height(side_x, z))
    if len(flank_heights) <= 1:
        errors.append("outer_flank_not_textured")

    # blend skirt: the derived relief must grade into natural ground with no
    # cut-off cliff. Per-cell slope noise is expected texture, so test a smoothed
    # transect (5-cell window) off the summit's tall side and require its grade
    # to stay gentle, and require the outer skirt edge to meet natural height.
    transect_z = (summit.z0 + summit.z1) // 2
    raw = [mountain.height(mountain.core_x1 + dx, transect_z)
           for dx in range(0, mountain.params.skirt_radius + 6)]
    smooth = _smooth(raw, 5)
    for i in range(1, len(smooth)):
        if abs(smooth[i] - smooth[i - 1]) > SEAM_SLOPE_LIMIT:
            errors.append(f"skirt_seam:side:dx={i}:|Δ|={abs(smooth[i] - smooth[i - 1])}")
            break
    edge = mountain.height(mountain.core_x1 + mountain.params.skirt_radius + 4, transect_z)
    natural_edge = mountain.natural_fn(
        mountain.core_x1 + mountain.params.skirt_radius + 4, transect_z)
    if abs(edge - natural_edge) > mountain.params.noise_amp_outer:
        errors.append(f"skirt_edge_not_natural:{edge}!={natural_edge}")

    # cliff-back: a sheer face stands directly behind the summit
    if summit.cliff_back:
        face = mountain.height((summit.x0 + summit.x1) // 2, summit.z1 + 1)
        if face < summit.elevation + 1:
            errors.append(f"cliff_back_not_sheer:{face}<{summit.elevation + 1}")

    # spire: a pillar rising one rise above the summit under the detached volume,
    # standing clear of the platform around it (a solitary peak reached only by
    # the bridge). The volume rests on the pillar top (no float).
    if mountain.spire is not None:
        sp = mountain.spire
        pillar = mountain.height((sp.x0 + sp.x1) // 2, (sp.z0 + sp.z1) // 2)
        if pillar != sp.top:
            errors.append(f"spire_top_wrong:{pillar}!={sp.top}")
        # every cell immediately outside the footprint stands more than one block
        # below the pillar top, so the volume cannot be stepped onto from terrain
        # and is reachable only across the bridge.
        separated = True
        ring_cells = [(x, z) for x in range(sp.x0 - 1, sp.x1 + 2) for z in (sp.z0 - 1, sp.z1 + 1)]
        ring_cells += [(x, z) for z in range(sp.z0 - 1, sp.z1 + 2) for x in (sp.x0 - 1, sp.x1 + 1)]
        for x, z in ring_cells:
            if mountain.height(x, z) > sp.top - 2:
                separated = False
        if not separated:
            errors.append("spire_not_isolated")

    return {"passed": not errors, "errors": errors,
            "cliff_back_top": mountain.cliff_back_top,
            "spire": (sp_to_dict(mountain.spire) if mountain.spire else None)}


def _validate_core(mountain: DerivedMountain, under_spire) -> List[str]:
    """The compound core and the forecourt carry no noise.

    Checks every core/forecourt cell against the band/forecourt/taper-strip
    rules stated independently here, and that a different seed yields the same
    core (no seed-driven term reaches it).
    """
    errors: List[str] = []
    terraces = mountain.terraces
    gate_floor = terraces[0].elevation - 1
    other = derive_mountain(mountain.seed ^ 0x2545F4914F6CDD1D, mountain.profile,
                            mountain.natural_fn, mountain.params)
    cells = [(x, z) for x in range(mountain.core_x0, mountain.core_x1 + 1)
             for z in range(mountain.core_z0, mountain.core_z1 + 1)]
    if mountain.apron is not None:
        ax0, az0, ax1, az1 = mountain.apron
        cells += [(x, z) for x in range(ax0, ax1 + 1) for z in range(az0, az1 + 1)
                  if not mountain.in_core(x, z)]
    reported = set()
    for x, z in cells:
        if under_spire(x, z):
            continue
        h = mountain.height(x, z)
        if mountain.on_apron(x, z):
            kind, want = "apron", gate_floor
        elif any(t.contains(x, z) for t in terraces):
            continue                       # checked cell by cell above
        else:
            kind, want = _core_expectation(terraces, x, z)
        if want is not None and h != want and kind not in reported:
            errors.append(f"core_{kind}_height:{(x, z)}:{h}!={want}")
            reported.add(kind)
        if h != other.height(x, z) and "seed" not in reported:
            errors.append(f"core_not_noise_free:{(x, z)}")
            reported.add("seed")
    return errors


def _core_expectation(terraces: List[TerraceBox], x: int, z: int) -> Tuple[str, Optional[int]]:
    for lower, upper in zip(terraces, terraces[1:]):
        if lower.z1 < z < upper.z0:
            if upper.x0 <= x <= upper.x1:
                return "band", upper.elevation - 1
            if lower.x0 <= x <= lower.x1:
                return "band_strip", lower.elevation - 1
    # taper strip: one block down per block from the nearest platform (ties to
    # the higher floor)
    candidates = []
    for t in terraces:
        d = max(t.x0 - x, 0, x - t.x1, t.z0 - z, 0, z - t.z1)
        candidates.append((d, -(t.elevation - 1 - d)))
    d, neg_h = min(candidates)
    return "taper_strip", -neg_h


def sp_to_dict(sp: SpirePeak) -> dict:
    return {"bounds": [sp.x0, sp.z0, sp.x1, sp.z1], "top": sp.top, "gap": sp.gap}


def validate_mountain_reproducibility(
    seeds, profile: Mapping[str, object], feature: Optional[Mapping[str, object]] = None,
) -> dict:
    """Same seed + profile yields an identical heightfield sample."""
    errors: List[str] = []
    base_y = int(profile["footprint"]["base_y"])  # type: ignore[index]
    samples = [(x, z) for x in range(0, 60, 7) for z in range(0, 180, 11)]
    for seed in seeds:
        a = derive_mountain(seed, profile, flat_natural(base_y), feature=feature)
        b = derive_mountain(seed, profile, flat_natural(base_y), feature=feature)
        ha = [a.height(x, z) for x, z in samples]
        hb = [b.height(x, z) for x, z in samples]
        if ha != hb:
            errors.append(f"mountain_not_reproducible:{seed}")
    return {"passed": not errors, "errors": errors}
