import json
from pathlib import Path
import unittest

from tools.lord_manors import presentation
from tools.lord_manors.geometry import cells

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return json.loads((ROOT / path).read_text())


class PresentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules = read('blueprints/lord_manors/presentation.json')

    def test_authored_uses_distinguish_window_roof_stairs_and_structural_axes(self):
        cases = [
            ([0, 0, 0, 1, 2, .2, 'window', 'gate', 'body'], 'window'),
            ([0, 0, 0, 7, .5, 1, 'roof', 'hall', 'roof'], 'roof'),
            ([0, 0, 0, 4, 1, 1, 'stone', 'route', 'stairs'], 'stone_stairs'),
            ([0, 0, 0, .5, 6, .5, 'frame', 'hall', 'structure'], 'wood_column'),
            ([0, 0, 0, 6, .5, .5, 'frame', 'hall', 'structure'], 'wood_beam'),
            ([0, 0, 0, .5, .5, 6, 'frame', 'hall', 'structure'], 'wood_beam'),
        ]
        for source, expected in cases:
            with self.subTest(expected=expected, source=source):
                self.assertEqual(expected, presentation.source_part(source, self.rules))
        # 同一造型跨用途复用时保守回退，不能把所有木件都叫木柱。
        self.assertEqual('woodwork', presentation.choose({'wood_column', 'wood_beam'}, self.rules))
        self.assertEqual('wood_stone', presentation.choose({'wood_column', 'wall'}, self.rules))

    def test_source_labels_follow_actual_full_cell_replacement_and_overlay_rules(self):
        small = [.25, .5, .5, .5, 1, 1, 'frame', 'hall', 'structure']
        whole = [.5, .5, .5, 1, 1, 1, 'wall', 'hall', 'body']
        scene = {'boxes': [small, whole]}
        self.assertEqual([whole], presentation.source_boxes(scene, [])[0, 0, 0])
        self.assertEqual(['wall'], [box[6] for box in cells(scene)[0, 0, 0]])
        self.assertEqual([small, whole], presentation.source_boxes(scene, ['wall'])[0, 0, 0])
        self.assertEqual(['frame', 'wall'], [box[6] for box in cells(scene, overlay_roles=['wall'])[0, 0, 0]])

    def test_every_generated_shape_has_a_translated_name_and_exact_material_list(self):
        catalog = read('src/main/resources/data/myvillage/lord_manors/catalog.json')
        display = read('src/main/resources/data/myvillage/lord_manors/presentation.json')
        lang = read('src/main/resources/assets/myvillage/lang/zh_cn.json')
        self.assertEqual({bank['id'] for bank in catalog['banks']}, set(display['banks']))
        for bank in catalog['banks']:
            self.assertNotRegex(bank['name'], r'精雕|\d')
            profiles = display['banks'][bank['id']]['profiles']
            self.assertEqual(32, len(profiles))
            for source, info in zip(bank['profiles'], profiles):
                self.assertIn(info['name'], lang)
                self.assertNotRegex(lang[info['name']], r'精雕|\d')
                roles = sorted({box[6] for box in source['boxes']})
                expected = ['manor_material.myvillage.' + ('plaque' if role.startswith('plaque_') else role) for role in roles]
                self.assertEqual(expected, info['materials'])
                for name in info['materials']:
                    self.assertIn(name, lang)
        self.assertEqual({m['id'] for m in catalog['materials']} - {'manor_survey_stone'}, set(display['materials']))

    def test_real_lingyue_window_is_named_per_state_and_preserves_all_materials(self):
        display = read('src/main/resources/data/myvillage/lord_manors/presentation.json')
        bank = display['banks']['manor_lingyue_carved_7']
        info = bank['profiles'][21]
        self.assertEqual('lingyue', bank['city'])
        self.assertEqual('manor_part.myvillage.window', info['name'])
        self.assertEqual(['manor_material.myvillage.frame', 'manor_material.myvillage.window', 'manor_material.myvillage.wood'], info['materials'])
        self.assertGreater(len({profile['name'] for profile in bank['profiles']}), 1)


if __name__ == '__main__':
    unittest.main()
