"""Deterministic, parity-safe hash primitives for seed-derived town geometry.

Every seed-derived geometry parameter (perimeter vocabulary selection, grid
jitter, modifier presence) routes through this module so the Python planner and
the Java realizer reproduce identical parameters from the same ``(seed, tag)``
pair without sharing an RNG stream. See
``src/main/java/com/example/myvillage/town/TownHash.java`` for the bit-identical
mirror.

The primitives are pure-integer (no float) and platform-independent:

- ``hash64(seed, tag)`` — splitmix64-style finalizer over a tagged FNV-1a hash
  of the seed bytes. Returns a non-negative Python int in the unsigned 64-bit
  range; the Java mirror returns the equivalent ``long`` interpreted as
  unsigned via ``Long.rotateRight`` / bit ops over the signed 64-bit value.
- ``range64(seed, tag, lo, hi)`` — inclusive integer range ``[lo, hi]`` derived
  from ``hash64``. ``lo`` and ``hi`` MUST be ints with ``lo <= hi``.
- ``pick(seed, tag, options)`` — deterministic selection from a non-empty
  sequence by ``hash64(seed, tag) % len(options)``.

Design notes (parity-critical):

- The finalizer is splitmix64 (``z = (z + GOLDEN) ; z = (z ^ (z >> 30)) * M1 ;
  z = (z ^ (z >> 27)) * M2 ; z ^= z >> 31``), operating on the unsigned 64-bit
  value. Both ends mask to 64 bits after every arithmetic step.
- The pre-mixer is FNV-1a over the UTF-8 bytes of ``"seed=<n>;tag=<s>"`` so
  different tags with identical seed bytes hash to unrelated outputs. The
  encoding is stable across Python/Java (ASCII seed decimal + tag bytes).
"""

from __future__ import annotations

from typing import Sequence, TypeVar

# splitmix64 constants (Knuth / Steele & Marsaglia).
_SPLITMIX_GOLDEN = 0x9E3779B97F4A7C15
_SPLITMIX_M1 = 0xBF58476D1CE4E5B9
_SPLITMIX_M2 = 0x94D049BB133111EB
_MASK64 = (1 << 64) - 1

T = TypeVar("T")


def _fnv1a_tagged(seed: int, tag: str) -> int:
    """64-bit FNV-1a over the bytes of ``f"seed={seed_u};tag={tag}"``.

    ``seed`` is masked to its unsigned 64-bit representation before encoding so
    a negative Java ``long`` and the corresponding Python int produce identical
    bytes (``Long.toUnsignedString`` / ``int & MASK`` agree on the decimal
    digits). The byte encoding is exactly what the Java mirror constructs, so
    the intermediate hash is bit-identical across ends.
    """
    seed_u = int(seed) & _MASK64
    h = 0xCBF29CE484222325  # FNV-1a 64-bit offset basis
    payload = f"seed={seed_u};tag={tag}".encode("utf-8")
    for b in payload:
        h ^= b
        h = (h * 0x100000001B3) & _MASK64  # FNV-1a 64-bit prime
    return h & _MASK64


def _splitmix64(z: int) -> int:
    """splitmix64 finalizer over an unsigned 64-bit input -> unsigned 64-bit."""
    z = (z + _SPLITMIX_GOLDEN) & _MASK64
    z = (z ^ (z >> 30)) & _MASK64
    z = (z * _SPLITMIX_M1) & _MASK64
    z = (z ^ (z >> 27)) & _MASK64
    z = (z * _SPLITMIX_M2) & _MASK64
    return (z ^ (z >> 31)) & _MASK64


def hash64(seed: int, tag: str) -> int:
    """Deterministic unsigned 64-bit hash of ``(seed, tag)``.

    Bit-identical to ``TownHash.hash64`` on the Java side (the Java mirror
    returns the same bit pattern interpreted through a signed ``long``).
    """
    return _splitmix64(_fnv1a_tagged(int(seed), str(tag)))


def range64(seed: int, tag: str, lo: int, hi: int) -> int:
    """Inclusive deterministic integer in ``[lo, hi]`` derived from ``hash64``.

    ``lo`` and ``hi`` MUST be integers with ``lo <= hi``. Uses integer-only
    arithmetic so the Java mirror reproduces it bit-for-bit (Java computes the
    same modulus over the unsigned-interpreted 64-bit value).
    """
    if lo > hi:
        raise ValueError(f"range64: lo ({lo}) > hi ({hi})")
    span = hi - lo
    return lo + (hash64(seed, tag) % (span + 1))


def pick(seed: int, tag: str, options: Sequence[T]) -> T:
    """Deterministic selection from a non-empty ``options`` sequence."""
    if not options:
        raise ValueError("pick: options must be non-empty")
    return options[hash64(seed, tag) % len(options)]


__all__ = ["hash64", "range64", "pick"]
