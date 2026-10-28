"""UV 周期、透明度和非镜像骨骼坐标的独立几何回归。"""
import math
import unittest

from .exporter import ROOT, item_assets, read, split_triangle, wearing_vertex


def area(tri):
    a, b, c = tri
    return abs((b[0]-a[0])*(c[1]-a[1]) - (b[1]-a[1])*(c[0]-a[0]))/2


class ExporterBoundaryTest(unittest.TestCase):
    def test_cross_period_clipping_preserves_area_winding_and_texture_phase(self):
        # 位置本身编码原 UV，裁切后的质心可以独立恢复纹理周期。
        triangle = [[-1.2, -.3, 0, -1.2, -.3, 0, 0, 1],
                    [2.3, -.3, 0, 2.3, -.3, 0, 0, 1],
                    [-1.2, 1.8, 0, -1.2, 1.8, 0, 0, 1]]
        result = split_triangle(triangle)
        self.assertGreater(len(result), 10)
        self.assertAlmostEqual(sum(area(t) for t in result), area(triangle), places=10)
        for tri in result:
            self.assertGreater((tri[1][0]-tri[0][0])*(tri[2][1]-tri[0][1]) -
                               (tri[1][1]-tri[0][1])*(tri[2][0]-tri[0][0]), 0)
            self.assertTrue(all(0 <= v[c] <= 1 for v in tri for c in (3, 4)))
            x, y, u, v = [sum(p[c] for p in tri)/3 for c in (0, 1, 3, 4)]
            self.assertAlmostEqual(u, x-math.floor(x), places=10)
            self.assertAlmostEqual(v, 1-(y-math.floor(y)), places=10)

    def test_integer_edge_does_not_duplicate_or_remove_face(self):
        tri = [[0,0,0,0,0,0,0,1], [1,0,0,1,0,0,0,1], [0,1,0,0,1,0,0,1]]
        split = split_triangle(tri)
        self.assertEqual(len(split), 1)
        self.assertEqual(area(split[0]), .5)

    def test_fit_mapping_flips_png_direction_without_repeating(self):
        tri = [[0,0,0,0,0,0,0,1], [1,0,0,1,0,0,0,1], [0,1,0,0,1,0,0,1]]
        result = split_triangle(tri, tile=False)
        self.assertEqual([v[3:5] for v in result[0]], [[0,1], [1,1], [0,0]])

    def test_standard_transform_preserves_asymmetric_positive_x(self):
        rig = read(ROOT / "equipment/immortal/rig.json")
        origins = read(ROOT / "equipment/poses.json")["bones"]
        v = wearing_vertex([.4,1.1,.2,.1,.3,1,0,0], "left_arm", origins, "standard", rig)
        self.assertAlmostEqual(v[0]+origins['left_arm'][0], 6.4)
        self.assertAlmostEqual(v[1]+origins['left_arm'][1], 6.4)
        self.assertAlmostEqual(v[2], -3.2)
        self.assertEqual(v[5:], [1,0,0])

    def test_wearing_uv_keeps_exact_shared_obj_precision(self):
        rig = read(ROOT / "equipment/immortal/rig.json")
        origins = read(ROOT / "equipment/poses.json")["bones"]
        uv = [.1234567891, .9876543219]
        vertex = [.4,1.1,.2,*uv,1,0,0]
        self.assertEqual(wearing_vertex(vertex, "left_arm", origins, "standard", rig)[3:5], uv)

    def test_slim_anchors_inner_edge_and_preserves_arm_local_height(self):
        rig = read(ROOT / "equipment/immortal/rig.json")
        standard = read(ROOT / "equipment/poses.json")["bones"]
        slim = {**standard, **rig['profiles']['slim']['bone_origin_overrides']}
        for bone, sign in (("left_arm", 1), ("right_arm", -1)):
            for x, expected in ((4, 4), (8, 7)):
                vertex = [sign*x/16,1.1,.2,.1,.3,.6,.8,0]
                a = wearing_vertex(vertex, bone, standard, "standard", rig)
                b = wearing_vertex(vertex, bone, slim, "slim", rig)
                self.assertAlmostEqual(b[0]+slim[bone][0], sign*expected)
                self.assertEqual(a[1], b[1])
                self.assertAlmostEqual(sum(n*n for n in b[5:]), 1, places=7)

    def test_neoforge_mtl_receives_alpha_not_only_ignored_dissolve(self):
        spec = {'id': 'test', 'materials': {'glass': {'opacity': .64}}}
        mesh = {'id': 'crystal', 'material': 'glass', 'vertices':
                [[0,0,0,0,0,0,0,1],[1,0,0,1,0,0,0,1],[0,1,0,0,1,0,0,1]], 'indices': [0,1,2]}
        out = item_assets(spec, [mesh], 'ingot', ROOT)
        mtl = out['models/immortal_equipment/test_ingot.mtl'].decode()
        self.assertIn('Kd 1 1 1 0.64\n', mtl)
        self.assertIn('d 0.64\n', mtl)
        self.assertIn('map_Kd myvillage:immortal_equipment/test/glass', mtl)


if __name__ == '__main__':
    unittest.main()
