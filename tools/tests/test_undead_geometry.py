"""检查实际体积与通道，防止用贴图黑线代替头骨和肋骨的空隙。"""
import json
import unittest

from tools.beastgen.undead import appearance, build_model
from tools.gen_undead_roster import catalogue


def occupied(model, bone, point):
    """Point is in authored vanilla units, before the half-scale export."""
    return any(all(c.origin[i] / 2 < point[i] < (c.origin[i] + c.size[i]) / 2 for i in range(3))
               for c in model.bone(bone).cubes)


def connected(model, first, second, first_names=None):
    from tools.beastgen.cuboid import mat_apply
    matrices=model.rest_matrices()
    def bounds(bone,cube):
        low=mat_apply(matrices[bone],cube.origin)
        high=mat_apply(matrices[bone],tuple(a+b for a,b in zip(cube.origin,cube.size)))
        return low,high
    for a in model.bone(first).cubes:
        if first_names is not None and a.name not in first_names:
            continue
        al,ah=bounds(first,a)
        for b in model.bone(second).cubes:
            bl,bh=bounds(second,b)
            overlap=[min(x,y)-max(u,v) for x,y,u,v in zip(ah,bh,al,bl)]
            if min(overlap)>=-1e-9 and sum(x>1e-9 for x in overlap)>=2:
                return True
    return False


class UndeadGeometryTest(unittest.TestCase):
    def test_shoulders_connect_the_ribcage_to_both_arms(self):
        for v in catalogue()['variants']:
            model=build_model(v['id'])
            for side in ('left','right'):
                self.assertTrue(connected(model,'body','arm_'+side),(v['id'],side))

    def test_cranium_connects_directly_to_the_neck(self):
        for v in catalogue()['variants']:
            model=build_model(v['id'])
            self.assertTrue(connected(model,'body','head',('neck',)),v['id'])

    def test_every_variant_has_individually_authored_geometry(self):
        data = appearance()['variants']
        self.assertEqual({v['id'] for v in catalogue()['variants']}, set(data))
        for family in ('corpse', 'archer'):
            shapes = []
            for v in catalogue()['variants']:
                if v['family'] != family:
                    continue
                model = build_model(v['id'])
                # Compare physical head/torso shapes, ignoring names, textures, bows and UV packing.
                shapes.append(json.dumps([(b.name, [(c.origin, c.size) for c in b.cubes])
                                          for b in model.bones if b.name in ('head', 'body')]))
                self.assertFalse(model.problems(), v['id'])
                self.assertEqual(.5, model.scale)
            self.assertEqual(9, len(set(shapes)), family)

    def test_every_skull_has_deep_open_eye_sockets_and_nasal_cavity(self):
        for v in catalogue()['variants']:
            if v['family'] != 'archer':
                continue
            model = build_model(v['id'])
            for x, y in [(-1.75, -5), (1.75, -5), (0, -3.25)]:
                for z in (-3.9, -3, -2, -1, 0, 1, 2):
                    self.assertFalse(occupied(model, 'head', (x, y, z)), (v['id'], x, y, z))
                self.assertTrue(occupied(model, 'head', (x, y, 3)), v['id'])

    def test_every_ribcage_has_a_passage_from_front_to_back(self):
        for v in catalogue()['variants']:
            if v['family'] != 'archer':
                continue
            model = build_model(v['id'])
            for y in (-9, -6.5):
                openings = [x for x in (-1.75, 1.75)
                            if all(not occupied(model, 'body', (x, y, z / 4)) for z in range(-12, 21))]
                self.assertTrue(openings, (v['id'], y))
            self.assertFalse(occupied(model, 'body', (1.1, -1.7, 0)), v['id'])

    def test_forearm_bones_are_separated_and_join_a_solid_palm(self):
        for v in catalogue()['variants']:
            if v['family'] != 'archer':
                continue
            model = build_model(v['id'])
            for side in ('left', 'right'):
                bone = 'arm_' + side
                self.assertFalse(occupied(model, bone, (.1, 6, 0)), v['id'])
                self.assertTrue(occupied(model, bone, (-.5, 6, 0)), v['id'])
                self.assertTrue(occupied(model, bone, (.5, 6, 0)), v['id'])
                self.assertTrue(occupied(model, bone, (.1, 9, 0)), v['id'])

    def test_all_soles_stay_on_the_existing_ground_plane(self):
        from tools.beastgen.cuboid import mat_apply
        for v in catalogue()['variants']:
            model = build_model(v['id']); rest = model.rest_matrices()
            for side in ('left', 'right'):
                bone = model.bone('leg_' + side)
                bottom = max(mat_apply(rest[bone.name],
                                      (c.origin[0], c.origin[1] + c.size[1], c.origin[2]))[1]
                             for c in bone.cubes)
                self.assertAlmostEqual(24, bottom, msg=v['id'])

    def test_idle_never_changes_attack_arm_aim(self):
        from tools.beastgen.undead import clips
        for v in catalogue()['variants']:
            model = build_model(v['id'])
            idle = next(c for c in clips(model, None) if c.name == 'idle')
            for key in idle.keys:
                self.assertFalse(set(key.pose) & {'body', 'arm_left', 'arm_right', 'bow'}, v['id'])
