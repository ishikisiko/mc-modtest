#!/usr/bin/env python3
"""Presentation-only pill fixtures: colored frames, names and intact stack counts."""
import argparse
import json
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.forging_quality_evidence import QualityEvidence, spec
from tools.forging_evidence import POS
from tools.combat_capture import scene

QUALITY = 'myvillage:pill_quality'


def pill(kind, quality, explicit=False, named=False):
    parts = []
    if quality != 1 or explicit:
        parts.append(QUALITY + '=' + str(quality))
    if named:
        parts.append('minecraft:custom_name=' + json.dumps(json.dumps({'text': 'Saved medicine'})))
    return 'myvillage:pill_' + kind + ('[' + ','.join(parts) + ']' if parts else '')


class PillFrames(QualityEvidence):
    def __init__(self, out):
        super().__init__(out)
        self.report.update(kind='pill_quality_frames', selected=['hotbar', 'all_pills_inventory', 'tooltip_borders',
            'legacy_and_named', 'chest', 'weapon_regression', 'count_visibility'], completed=[])

    def counts(self, image, slots, reference):
        s = self.gui()[2]
        def pixel(pos, dx, dy):
            return image.getpixel(((pos[0] + dx) * s + s // 2, (pos[1] + dy) * s + s // 2))
        # Stone has no white icon pixels here. These are the actual vanilla "16" glyphs.
        mask = [(x, y) for x in range(7, 18) for y in range(8, 18) if pixel(reference, x, y) == (255, 255, 255)]
        self.check('count_reference_visible', len(mask) >= 8, {'white_glyph_pixels': len(mask)})
        for name, pos in slots:
            hits = sum(pixel(pos, x, y) == (255, 255, 255) for x, y in mask)
            self.check('count_preserved_' + name, hits == len(mask), {'matching': hits, 'required': len(mask)})

    def run(self):
        self.start()
        u = self.s.username
        for command in (*scene.world_rules(), *scene.player_setup(u, 'minecraft:air'), 'myvillage world pause',
                        'forceload add -16 -16 32 32', f'clear {u}', f'setblock {POS} minecraft:chest'):
            self.rc(command)
        for q in range(1, 5):
            self.rc(f'item replace entity {u} hotbar.{q-1} with {pill("huiling", q)} 16')
            self.rc(f'item replace entity {u} inventory.{q-1} with {pill("yangyuan", q)} 16')
            self.rc(f'item replace entity {u} inventory.{q+8} with {pill("ningshen", q)} 16')
            self.rc(f'item replace block {POS} container.{q-1} with {pill("ningshen", q)} 16')
        self.rc(f'item replace entity {u} hotbar.4 with {pill("huiling", 1, explicit=True)} 16')
        self.rc(f'item replace entity {u} hotbar.5 with {pill("huiling", 4, named=True)} 16')
        self.rc(f'item replace entity {u} hotbar.6 with {spec("qingfeng_sword", 3)}')
        self.rc(f'item replace entity {u} hotbar.7 with myvillage:xuantie_gauntlet')
        self.rc(f'item replace entity {u} hotbar.8 with minecraft:stone 16')
        before = self.inventory()
        self.check('legacy_lower_has_no_component', QUALITY not in before[0].get('components', {}), before[0])
        self.check('explicit_lower_fixture', before[4]['components'][QUALITY] == 1, before[4])
        w, h, s = self.gui()
        self.s.game.key('7')
        time.sleep(.8)
        image = self.capture('pill-hotbar.png')
        for q in range(1, 5):
            self.frame(image, w // 2 - 88 + 20 * (q - 1), h - 19, q, 'hotbar_pill_' + str(q), True)
        self.report['completed'].append('hotbar')

        self.s.game.key('e')
        self.poll(self.s.game.pointer_grabbed, lambda v: not v, 8, 'mapped pill inventory')
        left, top = (w - 176) // 2, (h - 166) // 2
        self.move(0, 0)
        image = self.capture('pill-inventory-all-qualities.png')
        slots = []
        for kind, y in [('huiling', 142), ('yangyuan', 84), ('ningshen', 102)]:
            for q in range(1, 5):
                pos = (left + 8 + 18 * (q - 1), top + y)
                self.frame(image, *pos, q, kind + '_frame_' + str(q), True)
                slots.append((kind + '_' + str(q), pos))
        self.counts(image, slots, (left + 8 + 18 * 8, top + 142))
        self.report['completed'] += ['all_pills_inventory', 'count_visibility']
        for q in range(1, 5):
            self.tooltip(left + 16 + 18 * (q - 1), top + 150, q, 'pill-quality-' + str(q))
        self.tooltip(left + 16 + 18 * 3, top + 92, 4, 'yangyuan-perfect')
        self.tooltip(left + 16 + 18 * 2, top + 110, 3, 'ningshen-upper')
        self.report['completed'].append('tooltip_borders')
        self.tooltip(left + 16 + 18 * 4, top + 150, 1, 'explicit-lower')
        self.tooltip(left + 16 + 18 * 5, top + 150, 4, 'renamed-perfect')
        self.report['completed'].append('legacy_and_named')
        self.frame(image, left + 8 + 18 * 6, top + 142, 3, 'sword_frame_unchanged')
        self.frame(image, left + 8 + 18 * 7, top + 142, 0, 'mundane_weapon_frame_unchanged')
        self.tooltip(left + 16 + 18 * 6, top + 150, 3, 'weapon-regression')
        self.report['completed'].append('weapon_regression')
        self.close_menu()

        self.open_table()
        left, top = (w - 176) // 2, (h - 168) // 2
        self.move(0, 0)
        image = self.capture('pill-chest.png')
        for q in range(1, 5):
            self.frame(image, left + 8 + 18 * (q - 1), top + 18, q, 'chest_pill_' + str(q), True)
        self.tooltip(left + 16 + 18, top + 26, 2, 'chest-middle-pill')
        self.close_menu()
        self.check('display_preserves_stack_components_and_counts', self.inventory() == before, self.inventory())
        self.report['completed'].append('chest')
        self.report['status'] = 'pass'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT / 'out/preview/pill_quality_frames/runtime')
    evidence = PillFrames(parser.parse_args().out)
    try:
        evidence.run()
    except BaseException as error:
        evidence.report['status'] = 'fail'
        evidence.report['failures'].append(repr(error))
        evidence.save()
        raise
    finally:
        if evidence.s:
            evidence.stop()
        evidence.save()


if __name__ == '__main__':
    main()
