"""Combat retargeting invariants independent of visual acceptance."""
import unittest
from tools.npcgen import combat
from tools.npcgen.build import Built, DEFINITIONS
from tools import gen_sword_pal_anims as sword
from tools.beastgen import anim, cuboid


class NpcCombatAssetsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.looks = [Built(name) for name in DEFINITIONS]
        cls.moves = sword.bind(sword.BASIC_SWORD, sword.combat_styles()[sword.BASIC_SWORD.style_id])

    def test_every_move_uses_shared_duration_and_known_bones(self):
        for built in self.looks:
            clips = {c.name: c for c in built.clips}
            for move in self.moves:
                clip = clips[move.move_id.split(':', 1)[1]]
                self.assertEqual(move.total / 20, clip.length)
                self.assertFalse(clip.loop)
                self.assertEqual((move.active_start, move.active_end), clip.notes['active'])
                for name, target, keys in clip.channels():
                    self.assertIn(name, [bone.name for bone in built.model.bones])
                    self.assertEqual(0, keys[0][0])
                    self.assertEqual(clip.length, keys[-1][0])

    def test_grips_are_the_actual_hand_cube_centres_for_all_looks(self):
        for built in self.looks:
            for side in ('right', 'left'):
                bone, cube = built.model.cube('hand_' + side)
                grip = built.model.bone('grip_' + side)
                self.assertEqual(bone.name, grip.parent)
                self.assertEqual(tuple(cube.origin[i] + cube.size[i] / 2 for i in range(3)), grip.pivot)

    def test_contact_and_recovery_keep_the_lowest_foot_on_the_floor(self):
        for built in self.looks:
            for move in self.moves:
                keys = sword.resolve(move.keys)
                for tick in (0, move.active_start, move.active_end, move.total):
                    pose = combat.pose(built.model, sword.sample(move.keys, keys, tick))
                    clip = anim.Clip('probe', 1, False, [anim.Key(0, pose, 'linear')])
                    offsets = {}
                    anim.clip_offsets(clip.channels(), 1, False, 0, into=offsets)
                    matrices = anim.posed_matrices(built.model, offsets)
                    lowest = max(cuboid.mat_apply(matrices[b.name],
                        tuple(c.origin[i] + corner[i] * c.size[i] for i in range(3)))[1]
                        for b, c in built.model.cubes() if b.name.startswith('leg_')
                        for corner in ((x, y, z) for x in (0, 1) for y in (0, 1) for z in (0, 1)))
                    self.assertAlmostEqual(24, lowest, places=4, msg=(built.name, move.move_id, tick))


if __name__ == '__main__':
    unittest.main()
