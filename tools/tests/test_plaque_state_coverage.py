import itertools
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/yuanxian'
BLOCKS = ('wall_plaque', 'hanging_plaque', 'wall_plaque_vertical', 'hanging_plaque_vertical')


class PlaqueStateCoverageTest(unittest.TestCase):
    def test_every_live_java_state_has_a_model(self):
        java = (ROOT / 'src/main/java/com/example/myvillage/block/PlaqueBlock.java').read_text()
        def values(name):
            section = java.split('public enum ' + name + ' ', 1)[1].split('private final String', 1)[0]
            return re.findall(r'[A-Z][A-Z_0-9]*\("([a-z0-9_]+)"\)', section)
        expected = set(itertools.product(('north', 'east', 'south', 'west'), values('Frame'), values('Row'), values('Col')))
        self.assertEqual(1152, len(expected))
        for block in BLOCKS:
            with self.subTest(block=block):
                variants = json.loads((ASSETS / f'blockstates/{block}.json').read_text())['variants']
                actual = set()
                for key in variants:
                    state = dict(part.split('=', 1) for part in key.split(','))
                    actual.add(tuple(state[k] for k in ('facing', 'frame', 'row', 'col')))
                self.assertEqual(0, len(expected - actual), f'{block} 缺少 Java 方块状态')
                self.assertEqual(expected, actual)

    def test_every_mapping_keeps_its_frame_and_resolves_a_real_model(self):
        for block in BLOCKS:
            variants = json.loads((ASSETS / f'blockstates/{block}.json').read_text())['variants']
            for key, entry in variants.items():
                state = dict(part.split('=', 1) for part in key.split(','))
                self.assertIn('/' + state['frame'] + '/', entry['model'], key)
                namespace, path = entry['model'].split(':', 1)
                self.assertEqual('yuanxian', namespace)
                self.assertTrue((ASSETS / f'models/{path}.json').is_file(), entry['model'])


if __name__ == '__main__':
    unittest.main()
