"""Packaging reuse and cross-generator consistency, without Minecraft or Pillow."""
import copy
import unittest
from pathlib import Path

from tools.gen_alchemy_assets import png
from tools.pill_art import render as art


class PillArtTest(unittest.TestCase):
    def test_changing_body_preserves_every_support_pixel_outside_body(self):
        spec = art.catalogue()["huiling"]["body"]
        changed = {**spec, "base": "881122", "light": "ffccaa", "motif": "flame"}
        a, b = art.body_layer(spec), art.body_layer(changed)
        self.assertNotEqual(a, b)
        for support in art.SUPPORTS:
            first, second = art.compose(a, support), art.compose(b, support)
            for y in range(art.SIZE):
                for x in range(art.SIZE):
                    if not a[y][x][3] and not b[y][x][3]:
                        self.assertEqual(first[y][x], second[y][x])

    def test_layers_are_independent_and_composition_does_not_mutate_body(self):
        body = art.body_layer(art.catalogue()["zhuji"]["body"])
        original = copy.deepcopy(body)
        for support in art.SUPPORTS:
            back, front = art.support_layers(support)
            expected = art.compose(body, support)
            back[0][0] = front[0][0] = (255, 0, 0, 255)
            self.assertEqual(expected, art.compose(body, support))
        self.assertEqual(original, body)

    def test_same_body_can_use_all_three_supports(self):
        body = art.body_layer(art.catalogue()["qingzhang"]["body"])
        icons = [art.compose(body, support) for support in art.SUPPORTS]
        self.assertEqual(3, len({png(icon) for icon in icons}))
        for icon in icons:
            self.assertEqual(body[:14], icon[:14])

    def test_every_registered_body_stays_inside_shared_anchor(self):
        for entry in art.catalogue().values():
            self.assertIn(entry["support"], art.SUPPORTS)
            body = art.body_layer(entry["body"])
            self.assertTrue(all(p[3] in (0,255) for row in body for p in row))
            for y, row in enumerate(body):
                for x, p in enumerate(row):
                    if p[3]:
                        self.assertLessEqual((x-art.CENTER[0])**2+(y-art.CENTER[1])**2, art.RADIUS**2)

    def test_both_resource_owners_publish_the_shared_composition(self):
        root = Path(__file__).resolve().parents[2]
        for kind in art.catalogue():
            path = root / f"src/main/resources/assets/myvillage/textures/item/pill_{kind}.png"
            self.assertEqual(png(art.pill_icon(kind)), path.read_bytes(), kind)

    def test_invalid_composition_fails_instead_of_silently_changing_support(self):
        with self.assertRaises(ValueError):
            art.support_layers("unknown")
        with self.assertRaises(ValueError):
            art.compose([[(0,0,0,0)]], "leaf")


if __name__ == "__main__":
    unittest.main()
