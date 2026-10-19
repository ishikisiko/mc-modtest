"""Whole-window placement from the courtyard's wall and screen bays.

All spans below are half-open block coordinates. Window materials and shapes
remain owned by assemblies.json; this module only chooses whole-item roots.
"""
from __future__ import annotations


def centered_roots(start, stop, width, count=1, gap=0):
    """Center a row, choosing the lower root on a half-block parity tie."""
    occupied = count * width + (count - 1) * gap
    if count < 1 or width < 1 or gap < 0 or occupied > stop - start:
        raise ValueError("whole windows do not fit in their wall span")
    root = start + (stop - start - occupied) // 2
    return tuple(root + i * (width + gap) for i in range(count))


def place_windows(site):
    """Place nine intact windows in their corresponding architectural bays."""
    variants = site.families['courtyard_window']['variants']
    width = variants[0]['size'][0]
    assert all(v['size'] == [width, 3, 1] for v in variants)

    # Each transverse screen occupies one block in X. Its axis passes through
    # that block's centre; the last bay ends at the main hall's wall at X=24.
    # Center in the clear wall between those structural cells, not in the
    # window's previous approximate location.
    piers = (6, 12, 18, 24)
    for left, right, key in zip(piers, piers[1:], ('octagonal', 'round', 'square')):
        root, = centered_roots(left + 1, right, width)
        site.place((root, 4, 25), 'window', key)

    # The terminal window follows the moon gates' passage axis, Z=28.5.
    # The wall behind the five-block gates includes a separate closing strip;
    # centering across that strip as well would break the axial vista.
    root, = centered_roots(26, 31, width)
    site.place((3, 4, root), 'window', 'octagonal', 'east')

    # The eight-block side-room wall has a six-block pair with one-block
    # reveals on either side. The adjacent whole frames remain untouched.
    for root in centered_roots(11, 19, width, count=2):
        site.place((root, 4, 38), 'window', 'octagonal')

    # The mirrored hall posts are at X=26/32/36/42. Center the rear windows
    # in the two outer bays (axes 29.5 and 39.5), which also gives the complete
    # wall X=24..44 equal four-block outer reveals around its axis X=34.5.
    for left, right in ((26, 32), (36, 42)):
        root, = centered_roots(left + 1, right, width)
        site.place((root, 7, 37), 'window', 'octagonal')

    # This twelve-block wall cannot exactly centre one three-block window on
    # the block lattice. The lower root is only half a block from its axis,
    # and improves the previous one-and-a-half-block displacement.
    root, = centered_roots(26, 38, width)
    site.place((44, 7, root), 'window', 'round', 'east')
