"""入门女修像素脸候选：原生纹素、左右采样与人物换色的局部检查。"""
import unittest

from tools.npcgen import build, recolour, roles
from tools.npcgen.humanoid import _rgb, _tone, _xhz


class NoviceFaceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.built = build.Built("cultivator_f_novice")
        cls.d = cls.built.d
        cls.face = {}
        for t in cls.built.texels:
            if t.cube == "skull" and t.face == "NORTH":
                x, h, _ = _xhz(t)
                cls.face[int(x + cls.d.HEAD_SIZE / 2), int(cls.d.HEAD_TOP - h)] = t

    def colour(self, t, rows=None):
        return (self.built.texture if rows is None else rows)[t.v][t.u]

    def test_native_face_is_bilateral_at_resampling_ties(self):
        """14 宽画面必须左右一致；简单 floor(8*x/14) 会在边界把一只眼加宽。"""
        width = self.d.HEAD_SIZE
        self.assertEqual(width * width, len(self.face))
        for (x, y), t in self.face.items():
            other = self.face[width - 1 - x, y]
            self.assertEqual(self.colour(t), self.colour(other), (x, y))
            self.assertEqual(self.built.roles[t.v][t.u], self.built.roles[other.v][other.u], (x, y))

    def test_mouth_is_a_short_line_above_clear_chin(self):
        lip = {pos for pos, t in self.face.items() if self.colour(t) == _rgb(self.d.LIP) + (255,)}
        self.assertEqual({(6, 11), (7, 11)}, lip)
        for y in (12, 13):
            for x in range(self.d.HEAD_SIZE):
                self.assertEqual(_tone(self.d.SKIN, 5.5) + (255,), self.colour(self.face[x, y]))

    def test_iris_forms_two_separate_small_eyes(self):
        iris = {pos for pos, t in self.face.items() if self.built.roles[t.v][t.u][0] == roles.MATERIAL_IRIS}
        self.assertEqual({(x, y) for x in (3, 4, 9, 10) for y in (7, 8, 9)}, iris)
        highlights = {pos for pos in iris if self.built.roles[self.face[pos].v][self.face[pos].u][1] == roles.IRIS_WHITE_MIX}
        self.assertEqual({(3, 7), (10, 7)}, highlights)
        for t in self.built.texels:
            if self.built.roles[t.v][t.u][0] == roles.MATERIAL_IRIS:
                self.assertEqual(("skull", "NORTH"), (t.cube, t.face))

    def test_face_colours_follow_the_runtime_role_map(self):
        b = self.built
        for hair, eye in (("ink_violet", "wood"), ("silver", "water"), ("chestnut", "fire")):
            with self.subTest(hair=hair, eye=eye):
                actual = recolour.compose(b.texture, b.roles, hair, eye)
                fresh = roles.repaint(b, recolour.hair_ramp7(recolour.hair_ramp5(hair)), recolour.eye_ramp(eye)[:3])
                self.assertEqual(fresh, actual)
                for t in self.face.values():
                    if b.roles[t.v][t.u] == roles.KEEP:
                        self.assertEqual(self.colour(t), self.colour(t, actual))


if __name__ == "__main__":
    unittest.main()
