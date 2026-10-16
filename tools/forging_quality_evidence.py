#!/usr/bin/env python3
"""Cloud visual checks for weapon quality text, item frames and tooltip borders.

Administrator-created stacks cover every quality; mapped input opens/inspects real
menus. This proves presentation, not new crafting, balance or multiplayer behavior.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from PIL import Image
from tools.forging_evidence import ForgingEvidence, MARK, POS
from tools.combat_capture import scene

# Acceptance palette supplied by the user, independent of the Java renderer.
COLORS = [0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA55FF, 0xFFAA00]


def spec(item, quality, **extra):
    components = dict(extra)
    if quality:
        components[MARK] = {'trait': 'sharp', 'quality': quality}
    suffix = ','.join(k + '=' + json.dumps(v, separators=(',', ':')) for k, v in components.items())
    return 'myvillage:' + item + ('[' + suffix + ']' if suffix else '')


class QualityEvidence(ForgingEvidence):
    def __init__(self, out):
        super().__init__(out)
        self.report.update(kind='forging_quality_presentation',
            method='administrator stack fixtures; mapped menu opening and hover; raw window captures and pixel checks',
            selected=['hotbar', 'forging_inventory', 'five_tooltip_borders', 'survival_inventory', 'chest', 'named_enchanted_damaged'],
            completed=[], owner_appearance='not_verified', gameplay='not_selected', multiplayer='not_selected')

    def gui(self):
        w, h = self.s.game.size
        scale = max(1, min(w // 320, h // 240))
        return w // scale, h // scale, scale

    def move(self, x, y):
        scale = self.gui()[2]
        self.s.game.xdo('mousemove', str(x * scale), str(y * scale))
        time.sleep(.25)

    def capture(self, name):
        # Unlike the inherited screenshot helper, preserve the hover position.
        game = self.s.game
        window = game.xdo('search', '--name', 'Minecraft').split()[0]
        path = self.out / name
        subprocess.run(['import', '-silent', '-window', window, str(path)], env=game.env, check=True, timeout=30)
        return Image.open(path).convert('RGB')

    def frame(self, image, x, y, quality, name):
        s = self.gui()[2]
        rgb = tuple((COLORS[quality] >> shift) & 255 for shift in (16, 8, 0))
        edge = ([(i, -1) for i in range(-1, 17)] + [(i, 16) for i in range(-1, 17)]
                + [(-1, j) for j in range(16)] + [(16, j) for j in range(16)])
        hits = sum(image.getpixel(((x + i) * s + s // 2, (y + j) * s + s // 2)) == rgb for i, j in edge)
        self.check(name, hits >= 64, {'quality': quality, 'matching_border_pixels': hits, 'sampled': len(edge)})

    def tooltip(self, x, y, quality, name):
        self.move(x, y)
        image = self.capture(name + '.png')
        rgb = tuple((COLORS[quality] >> shift) & 255 for shift in (16, 8, 0))
        longest = 0
        for y in range(image.height):
            run = 0
            for x in range(image.width):
                run = run + 1 if image.getpixel((x, y)) == rgb else 0
                longest = max(longest, run)
        # A tooltip's horizontal border is much longer than an icon edge or glyph.
        self.check(name + '_border', longest >= 80, {'quality': quality, 'longest_solid_color_run': longest})

    def run(self):
        self.start()
        u = self.s.username
        for cmd in (*scene.world_rules(), *scene.player_setup(u, 'minecraft:air'), 'myvillage world pause',
                    'forceload add -16 -16 32 32', f'setblock {POS} myvillage:forging_table',
                    f'myvillage cultivation awaken {u}', f'myvillage cultivation crafting set {u} forging 100', f'clear {u}'):
            self.rc(cmd)
        for q in range(5):
            self.rc(f'item replace entity {u} hotbar.{q} with {spec("qingfeng_sword", q)}')
            self.rc(f'item replace entity {u} inventory.{q} with {spec("lingxiao_spear", q)}')
        enchanted = {'minecraft:enchantments': {'levels': {'minecraft:unbreaking': 1}}, 'minecraft:damage': 100}
        self.rc(f'item replace entity {u} hotbar.5 with {spec("qingfeng_sword", 3, **enchanted)}')
        self.rc(f'item replace entity {u} hotbar.6 with {spec("qingfeng_sword", 0, **enchanted)}')
        named = {'minecraft:custom_name': json.dumps({'text': 'Old friend'}), 'minecraft:damage': 100}
        self.rc(f'item replace entity {u} hotbar.7 with {spec("qingfeng_sword", 1, **named)}')
        self.rc(f'give {u} minecraft:diamond 64')
        before = self.inventory()
        self.check('mundane_has_no_forge_mark', MARK not in before[0].get('components', {}), before[0])
        self.s.game.key('5')
        time.sleep(.8)
        w, h, s = self.gui()
        image = self.capture('hotbar-five-qualities.png')
        for q in range(5):
            self.frame(image, w // 2 - 88 + 20 * q, h - 19, q, 'hotbar_frame_' + str(q))
        self.report['completed'].append('hotbar')

        self.open_table()
        left, top = (w - 304) // 2, (h - 238) // 2
        self.move(0, 0)
        image = self.capture('forging-inventory-five-qualities.png')
        for q in range(5):
            self.frame(image, left + 70 + 18 * q, top + 214, q, 'sword_inventory_frame_' + str(q))
            self.frame(image, left + 70 + 18 * q, top + 156, q, 'spear_inventory_frame_' + str(q))
        self.report['completed'].append('forging_inventory')
        for q in range(5):
            self.tooltip(left + 78 + 18 * q, top + 222, q, 'quality-' + str(q) + '-tooltip')
        self.report['completed'].append('five_tooltip_borders')
        for slot, q, label in [(5, 3, 'enchanted-damaged-upper'), (6, 0, 'enchanted-mundane'), (7, 1, 'renamed-damaged-lower')]:
            self.tooltip(left + 78 + 18 * slot, top + 222, q, label)
        self.report['completed'].append('named_enchanted_damaged')
        self.close_menu()

        self.s.game.key('e')
        self.poll(self.s.game.pointer_grabbed, lambda v: not v, 8, 'mapped survival inventory')
        left, top = (w - 176) // 2, (h - 166) // 2
        self.move(0, 0)
        image = self.capture('survival-inventory-five-qualities.png')
        for q in range(5):
            self.frame(image, left + 8 + 18 * q, top + 142, q, 'survival_inventory_frame_' + str(q))
        self.tooltip(left + 16 + 18 * 3, top + 92, 3, 'spear-upper-tooltip')
        self.close_menu()
        self.report['completed'].append('survival_inventory')

        self.rc(f'setblock {POS} minecraft:chest')
        for q in range(5):
            self.rc(f'item replace block {POS} container.{q} with {spec("lingxiao_spear", q)}')
        self.open_table()
        left, top = (w - 176) // 2, (h - 168) // 2
        self.move(0, 0)
        image = self.capture('chest-five-qualities.png')
        for q in range(5):
            self.frame(image, left + 8 + 18 * q, top + 18, q, 'chest_frame_' + str(q))
        self.tooltip(left + 16 + 18 * 2, top + 26, 2, 'chest-middle-tooltip')
        self.close_menu()
        self.check('display_does_not_mutate_inventory', self.inventory() == before, self.inventory())
        self.report['completed'].append('chest')
        self.report['status'] = 'pass'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, default=ROOT / 'out/preview/forging_quality/runtime')
    e = QualityEvidence(p.parse_args().out)
    try:
        e.run()
    except BaseException as error:
        e.report['status'] = 'fail'
        e.report['failures'].append(repr(error))
        e.save()
        raise
    finally:
        if e.s:
            e.stop()
        e.save()


if __name__ == '__main__':
    main()
