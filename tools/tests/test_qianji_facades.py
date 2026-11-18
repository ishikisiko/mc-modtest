"""分阁立面必须在各层实际落块。中层勘图窗应能由楼梯走入。"""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from qianji.branch_facades import BranchFacade
from qianji.enclosure import build_enclosed, rules
from qianji.model import AIR


class BranchFacadeTests(unittest.TestCase):
    def test_storeys_have_infill_windows_and_solid_upper_landings(self):
        for region in rules()["towns"]:
            model, _ = build_enclosed(region, validate=False)
            plaster = BranchFacade(region).materials["plaster"]
            for room in model.rooms:
                for index, level in enumerate(room["levels"]):
                    with self.subTest(region=region, room=room["id"], storey=index+1):
                        x, z, w, d = level["box"]
                        y, h = level["floor_y"], level["storey_height"]
                        # 两个山面都应有实墙和一处采光或通风口，八角折面也在搜索带内。
                        for px in (x, x+w-1):
                            surface = {model.blocks.get((px+inset, yy, pz), AIR)
                                       for inset in (0, 1 if px==x else -1)
                                       for pz in range(z+2, z+d-2)
                                       for yy in range(y+2, y+h-2)}
                            self.assertIn(plaster, surface)
                            self.assertTrue(any("stained_glass_pane" in s or "_copper_grate" in s for s in surface))
                            self.assertNotIn(model.material["rail"], surface)
                        if index:
                            # 上层后墙中央曾有一个直接通向室外的落空门洞。
                            p = (x+w//2, y, z+d-1)
                            self.assertNotEqual(AIR, model.blocks.get(p, AIR))

    def test_map_alcove_and_central_dial_are_built_on_each_principal_hall(self):
        for region in rules()["towns"]:
            with self.subTest(region=region):
                model, _ = build_enclosed(region, validate=False)
                _, middle, top = model.rooms[-1]["levels"]
                x, z, w, _ = middle["box"]
                cx, y = x+w//2, middle["floor_y"]
                for depth in (-1, 0):
                    self.assertNotEqual(AIR, model.blocks.get((cx, y-1, z+depth), AIR))
                    self.assertTrue(all(model.blocks.get((cx, y+dy, z+depth), AIR)==AIR for dy in (0, 1)))
                sightline = any(model.blocks.get((u, y+1, z-2), AIR).startswith("minecraft:glass_pane[")
                                and model.blocks.get((u, y+1, z-3), AIR) == AIR
                                and "type=top" in model.blocks.get((u, y, z-3), AIR)
                                for u in range(cx-2, cx+3))
                self.assertTrue(sightline, "站在挑窗内须能越过窗台看到院中")
                model.target("勘图窗", (cx, y, z-1))
                self.assertEqual(len(model.targets)+1, model.connected()["reachable_targets"])
                x, z, w, _ = top["box"]
                y = top["floor_y"]+(top["storey_height"]-1)//2
                pin = BranchFacade(region).materials["pin"]
                self.assertEqual([(x+w//2, y, z)], [pos for pos,s in model.blocks.items() if s==pin])
