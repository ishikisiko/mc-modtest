"""Bounded routing and preservation checks; no Java, game or cloud execution."""
from copy import deepcopy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from tools.alchemy_array_evidence import (
    ArrayEvidence, frozen_identity, menu_point, saved_items, world_pos,
)
from tools.modal_runner import remote


class ArraySelectionTests(unittest.TestCase):
    def test_runtime_scope_is_canonical_and_does_not_mutate_preset(self):
        before = deepcopy(remote.PRESETS['array-runtime'])
        command = remote.commands_for('array-runtime', 'lifecycle,automation')[-1]
        self.assertEqual(command[-2:], ['--cases', 'automation,lifecycle'])
        self.assertEqual(remote.PRESETS['array-runtime'], before)
        for bad in ('', 'unknown', 'mapped,mapped'):
            with self.subTest(value=bad), self.assertRaises(ValueError):
                remote.commands_for('array-play', bad)
        with self.assertRaises(ValueError):
            remote.commands_for('array-build', 'mapped')

    def test_focused_build_keeps_world_assets_and_never_uses_chunky_or_release(self):
        for preset in ('array-build', 'array-runtime', 'array-play'):
            commands = remote.commands_for(preset)
            flat = [argument for command in commands for argument in command]
            self.assertNotIn('tools/release_gate.py', flat)
            self.assertNotIn('tools/run_chunky_acceptance.py', flat)
            self.assertNotIn('generateWorldPlanAssets', flat)
            self.assertNotIn('generateNpcLifePlaces', flat)
            self.assertNotIn('build', flat)
            self.assertIn('jar', flat)
        self.assertIn('com.example.myvillage.alchemy.*', remote.PRESETS['array-build'][1])
        self.assertNotIn('test', remote.PRESETS['array-play'][0])


class FixtureAndReadbackTests(unittest.TestCase):
    def test_port_positions_rotate_outside_the_central_air_chamber(self):
        cell = {'x':1, 'y':-1, 'z':1}
        self.assertEqual(world_pos((0,-59,3),'north',cell), (1,-60,4))
        self.assertEqual(world_pos((0,-59,3),'east',cell), (-1,-60,4))
        self.assertEqual(world_pos((0,-59,3),'south',cell), (-1,-60,2))
        self.assertEqual(world_pos((0,-59,3),'west',cell), (1,-60,2))

    def test_menu_mouse_points_use_the_actual_368_pixel_menu(self):
        self.assertEqual(menu_point((960,540),193,148), (498,316))
        self.assertEqual(menu_point((960,540),200,113,(304,222)), (576,274))

    @staticmethod
    def active():
        return {'Batch':{'Version':1,'Brewer':[1,-2,3,-4],'Recipe':'huiling','Score':100,'Cap':4,'Quality':3},
                'Reserved':{'Items':[{'Slot':0,'id':'myvillage:spirit_leaf','count':1}]},
                'SelectedRecipe':'huiling','Authorizer':[1,-2,3,-4],'Identity':[5,6,7,8],
                'Score':100,'Cap':4,'Progress':12}

    def test_restart_identity_retains_quality_authorization_and_reservations(self):
        before = self.active()
        changed = deepcopy(before); changed['Progress'] += 1
        self.assertEqual(frozen_identity(before), frozen_identity(changed))
        for name in ('Authorizer','Reserved','Identity'):
            changed = deepcopy(before); changed[name] = []
            self.assertNotEqual(frozen_identity(before), frozen_identity(changed))
        changed = deepcopy(before); changed['Batch']['Quality'] = 2
        self.assertNotEqual(frozen_identity(before), frozen_identity(changed))
        for name in ('Batch','Reserved','Authorizer'):
            changed = deepcopy(before); del changed[name]
            with self.assertRaises(ValueError):
                frozen_identity(changed)
        changed = deepcopy(before); del changed['Batch']['Quality']
        with self.assertRaises(ValueError):
            frozen_identity(changed)

    def test_inventory_component_identity_is_preserved_and_duplicates_rejected(self):
        stack = {'Slot':3,'id':'myvillage:pill_huiling','count':2,
                 'components':{'myvillage:pill_quality':4,'minecraft:custom_data':{'value':[1,-2,3]}}}
        self.assertEqual(saved_items({'Items':[stack]})[3]['components'],stack['components'])
        with self.assertRaises(ValueError):
            saved_items({'Items':[stack,stack]})

    def test_export_only_packages_the_owned_world_and_preserves_machine_owner_data(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); world=root/'server'/'preview'; world.mkdir(parents=True)
            for name in ('level.dat','playerdata/fixture.dat','region/r.0.0.mca','session.lock','stats/fixture.json'):
                path=world/name; path.parent.mkdir(parents=True,exist_ok=True); path.write_text(name)
            outside=root/'owner-save.dat'; outside.write_text('must never export')
            (world/'link.dat').symlink_to(outside)
            evidence=ArrayEvidence.__new__(ArrayEvidence)
            evidence.out=root/'out'; evidence.out.mkdir(); evidence.report={}
            with patch('tools.alchemy_array_evidence.cs.SERVER_DIR',world.parent), \
                 patch('tools.alchemy_array_evidence.cs.LEVEL_NAME','preview'):
                evidence.export_world()
            with zipfile.ZipFile(evidence.out/'alchemy_array_preview_world.zip') as archive:
                names=[name.split('/',1)[1] for name in archive.namelist()]
            self.assertEqual(names,['level.dat','playerdata/fixture.dat','region/r.0.0.mca'])
            self.assertEqual(outside.read_text(),'must never export')


if __name__=='__main__':
    unittest.main()
