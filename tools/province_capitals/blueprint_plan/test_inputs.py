"""跨城输入边界：真实几何预留、旋转及 authored 调整不能缩小保护范围。"""
import copy
import unittest

from .generate import environment_input


class InputTests(unittest.TestCase):
    def setUp(self):
        self.spec = {"city_id": "capital_sample"}
        self.env = {"region": "sample", "gardens": [], "landmark_origin": [10, 20, 30],
                    "landmark_scale": 1, "landmark_rotation_quarters": 1}
        self.catalog = {"materials": {"stone": {}},
                        "landmarks": [{"region": "sample", "recipe": "test"}],
                        "recipes": {"test": [{"op": "box", "a": [0, 0, 0], "b": [2, 1, 1], "m": "stone"}]}}

    def test_missing_reserve_uses_rotated_closed_raster_extent(self):
        before = copy.deepcopy(self.env)
        result = environment_input(self.spec, self.env, self.catalog)
        self.assertEqual([8, 30, 9, 32], result["reserve"])
        self.assertEqual(before, self.env)

    def test_explicit_reserve_can_expand_but_cannot_cut_the_landmark(self):
        spec = {**self.spec, "reserve": [7, 29, 10, 33]}
        self.assertEqual(spec["reserve"], environment_input(spec, self.env, self.catalog)["reserve"])
        for bounds in ([9, 30, 9, 32], [8, 30, 9, 31]):
            with self.subTest(bounds=bounds), self.assertRaisesRegex(ValueError, "不能小于"):
                environment_input({**self.spec, "reserve": bounds}, self.env, self.catalog)

    def test_reserve_uses_scaled_fractional_edges(self):
        env = {**self.env, "landmark_scale": .5}
        self.assertEqual([9, 30, 9, 31], environment_input(self.spec, env, self.catalog)["reserve"])

    def test_malformed_reserve_and_rotation_reject(self):
        for bounds in ([8, 30, 9], [8, 30, 9, 32.0], [9, 30, 8, 32]):
            with self.subTest(bounds=bounds), self.assertRaisesRegex(ValueError, "闭区间"):
                environment_input({**self.spec, "reserve": bounds}, self.env, self.catalog)
        with self.assertRaisesRegex(ValueError, "朝向"):
            environment_input(self.spec, {**self.env, "landmark_rotation_quarters": 4}, self.catalog)

    def test_unknown_garden_override_and_wrong_city_reject(self):
        with self.assertRaisesRegex(ValueError, "不存在"):
            environment_input({**self.spec, "garden_overrides": {"missing": {"y": 1}}}, self.env, self.catalog)
        with self.assertRaisesRegex(ValueError, "身份"):
            environment_input({"city_id": "capital_other"}, self.env, self.catalog)


if __name__ == "__main__":
    unittest.main()
