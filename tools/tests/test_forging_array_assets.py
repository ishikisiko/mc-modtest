"""Focused asset closure, mesh/physics contract and shared-file merge checks."""
import contextlib
import io
import json
import math
from pathlib import Path
import shutil
import struct
import tempfile
import unittest

from tools import gen_forging_array_assets as gen
from tools.forging_array.formed_model import build

ROOT = Path(__file__).resolve().parents[2]


class ForgingArrayAssetsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mesh = build()
        cls.assets = ROOT/"src/main/resources/assets/myvillage"
        cls.files = gen.outputs()

    def test_generated_assets_are_current(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0,gen.generate(check=True))

    def test_local_bounds_groups_normals_and_collision_volumes(self):
        mesh = self.mesh
        self.assertEqual(1,mesh["version"])
        self.assertEqual([[-2.5,0,-2.5],[2.5,4.08,2.5]],mesh["bounds"])
        self.assertEqual({"body","ring","fire"},{f["g"] for f in mesh["faces"]})
        for face in mesh["faces"]:
            self.assertEqual({"p","n","m","g","glow"},set(face))
            self.assertEqual(4,len(face["p"]))
            self.assertIs(type(face["glow"]),bool)
            self.assertIn(face["m"],range(16))
            self.assertAlmostEqual(1,sum(x*x for x in face["n"]),places=4)
            for p in face["p"]:
                self.assertTrue(all(math.isfinite(x) for x in p))
                self.assertTrue(all(mesh["bounds"][0][i] <= p[i] <= mesh["bounds"][1][i] for i in range(3)))
            u = [face["p"][1][i]-face["p"][0][i] for i in range(3)]
            v = [face["p"][2][i]-face["p"][0][i] for i in range(3)]
            n = [u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
            self.assertGreater(sum(a*b for a,b in zip(n,face["n"])),1e-8)
            if face["g"] == "fire":
                self.assertTrue(face["glow"])
            if face["g"] == "ring":
                self.assertTrue(all(3.25 <= p[1] <= 3.37 for p in face["p"]))
        for lo,hi in mesh["collision_boxes"]:
            self.assertTrue(all(mesh["bounds"][0][i] <= lo[i] < hi[i] <= mesh["bounds"][1][i] for i in range(3)))

    def test_collision_keeps_the_bowl_fire_door_and_ring_open(self):
        def occupied(point):
            return any(all(lo[i] < point[i] < hi[i] for i in range(3))
                       for lo,hi in self.mesh["collision_boxes"])
        for p in ((0,2.5,0),(0,1.8,-1.35),(0,3.5,0),(1.2,3.31,0)):
            self.assertFalse(occupied(p),p)
        for p in ((0,.1,0),(0,1.2,0),(1.3,2.3,0),(0,2.5,1.3),(-1.12,.8,-1.12),(2,1,2),(1.73,3.5,0)):
            self.assertTrue(occupied(p),p)

    def test_atlas_and_every_component_resource_close(self):
        atlas = self.files[self.assets/"textures/block/forging_array_formed.png"]
        self.assertEqual(b"\x89PNG\r\n\x1a\n",atlas[:8])
        self.assertEqual((64,64),struct.unpack(">II",atlas[16:24]))
        self.assertEqual("myvillage:textures/block/forging_array_formed.png",self.mesh["texture"])
        for kind in gen.KINDS:
            bid = gen.block_id(kind)
            block = json.loads(self.files[self.assets/f"models/block/{bid}.json"])
            for el in block["elements"]:
                self.assertTrue(all(el["from"][i] < el["to"][i] for i in range(3)))
                for f in el["faces"].values():
                    self.assertTrue(all(0 <= x <= 16 for x in f["uv"]))
            item = json.loads(self.files[self.assets/f"models/item/{bid}.json"])
            self.assertEqual(f"myvillage:block/{bid}",item["parent"])
            state = json.loads(self.files[self.assets/f"blockstates/{bid}.json"])
            for facing,_ in gen.DIRECTIONS:
                for lit in ("false","true"):
                    matches = [p for p in state["multipart"] if all(
                        {"facing":facing,"lit":lit}.get(k) == v for k,v in p["when"].items())]
                    self.assertEqual(1,len(matches))
                    model_id = matches[0]["apply"]["model"].split(":")[1]
                    self.assertIn(self.assets/f"models/{model_id}.json",self.files)
            data = ROOT/"src/main/resources/data/myvillage"
            loot = json.loads(self.files[data/f"loot_table/blocks/{bid}.json"])
            self.assertEqual(f"myvillage:{bid}",loot["pools"][0]["entries"][0]["name"])
            recipe = json.loads(self.files[data/f"recipe/{bid}.json"])
            self.assertEqual(f"myvillage:{bid}",recipe["result"]["id"])

    def test_recipe_inputs_resolve_existing_registry_not_imaginary_black_iron(self):
        registry = (ROOT/"src/main/java/com/example/myvillage/item/ModItems.java").read_text()
        allowed = {"minecraft:iron_ingot","minecraft:stone_bricks","minecraft:copper_ingot","minecraft:redstone","myvillage:xuantie_ingot"}
        for recipe in gen.config()["recipes"].values():
            for value in recipe["key"].values():
                self.assertIn(value,allowed)
                if value.startswith("myvillage:"):
                    self.assertIn('registerItem("'+value.split(":")[1]+'"',registry)

    def test_shared_merges_preserve_parallel_edits_and_check_never_writes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            authored = root/"tools/forging_array"
            authored.mkdir(parents=True)
            shutil.copy(ROOT/"tools/forging_array/visual.json",authored/"visual.json")
            lang = root/"src/main/resources/assets/myvillage/lang/zh_cn.json"
            lang.parent.mkdir(parents=True)
            lang.write_text(json.dumps({"foreign.key":"并行新增","block.myvillage.forging_ding":"旧炼器鼎"}))
            tag = root/"src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json"
            tag.parent.mkdir(parents=True)
            tag.write_text(json.dumps({"replace":False,"values":["myvillage:old_block",{"id":"optional:block","required":False}]}))
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0,gen.generate(root))
            obj = json.loads(lang.read_text())
            self.assertEqual("并行新增",obj["foreign.key"])
            self.assertEqual("旧炼器鼎",obj["block.myvillage.forging_ding"])
            obj["another.parallel.key"] = "保留"
            lang.write_text(json.dumps(obj))
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0,gen.generate(root))
            self.assertEqual("保留",json.loads(lang.read_text())["another.parallel.key"])
            self.assertEqual(["myvillage:old_block",{"id":"optional:block","required":False}],json.loads(tag.read_text())["values"][:2])
            before = {p:p.read_bytes() for p in root.rglob("*") if p.is_file()}
            owned = root/"src/main/resources/assets/myvillage/forging_array/formed_mesh.json"
            owned.write_text("{}")
            before[owned] = b"{}"
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1,gen.generate(root,check=True))
            self.assertEqual(before,{p:p.read_bytes() for p in root.rglob("*") if p.is_file()})


if __name__ == "__main__":
    unittest.main()
