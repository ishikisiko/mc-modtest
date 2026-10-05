"""Helper for writing beast definitions in one design space.

Design space is the root bone's space: ground plane y = 0, up is -Y, front -Z, the animal's left +X.
`bone(..., at=...)` takes a pivot in design space and converts it to the parent-relative pivot the
file stores (through the parent's rest pose, so rotated parents are fine); `box(..., lo=...)` takes
a cube corner in design space and needs the bone's rest pose to be unrotated in total; `box_local`
takes bone-local coordinates. Pivots written with `at_local` are parent-relative already.
"""
from __future__ import annotations

from .cuboid import Bone, Cube, Model, mat_apply, mat_inverse_rigid, mat_mul


class Builder:
    def __init__(self, model_id, root_pivot=(0.0, 24.0, 0.0), look=None, shadow_radius=0.5):
        self.model = Model(model_id, [Bone("root", None, root_pivot)], look, shadow_radius)
        self._root_inv = mat_inverse_rigid(self.model.bone("root").rest_matrix())

    def _rest(self, name):
        return self.model.rest_matrices()[name]

    def _design(self, name):
        """Bone -> design-space matrix (root space)."""
        return mat_mul(self._root_inv, self._rest(name))

    def bone(self, name, parent, at=None, at_local=None, rot=(0.0, 0.0, 0.0)):
        if (at is None) == (at_local is None):
            raise ValueError(f"{name}: give exactly one of at / at_local")
        if at is not None:
            pivot = mat_apply(mat_inverse_rigid(self._design(parent)), at)
        else:
            pivot = at_local
        self.model.bones.append(Bone(name, parent, pivot, rot))
        return name

    def box(self, bone, name, lo, size, **kw):
        m = self._design(bone)
        rotated = any(abs(m[i][j] - (1.0 if i == j else 0.0)) > 1e-9 for i in range(3) for j in range(3))
        if rotated:
            raise ValueError(f"{name}: bone {bone} is rotated in total; use box_local")
        origin = mat_apply(mat_inverse_rigid(m), lo)
        return self.box_local(bone, name, origin, size, **kw)

    def box_local(self, bone, name, origin, size, inflate=0.0, mirror=False, uv_from=None):
        self.model.bone(bone).cubes.append(Cube(name, origin, size, inflate, mirror, uv_from))
        return name

    def design_point(self, bone, local):
        return mat_apply(self._design(bone), local)

    def local_point(self, bone, design):
        return mat_apply(mat_inverse_rigid(self._design(bone)), design)
