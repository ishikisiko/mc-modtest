"""空间语义与拒绝边界；这些检查不声称 Minecraft 寻路或玩家验收。"""
import copy
import unittest

from .generate import ROOT, Planner, generate, perimeter, read, rect, search_path


class PlanTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.planner, cls.data = generate()

    def test_complete_coverage_of_active_sources(self):
        p, d = self.planner, self.data
        city = read(ROOT / p.spec["sources"]["city"])
        expected = {b["id"] for b in city["buildings"]} - set(p.removed)
        self.assertEqual(expected, {b["id"] for b in d["plots"]})
        self.assertEqual({g["id"] for g in p.env["gardens"]}, {g["id"] for g in d["gardens"]})
        self.assertFalse(d["checks"]["issues"])

    def test_routes_are_continuous_with_bounded_steps(self):
        for g in self.data["gardens"]:
            with self.subTest(g=g["id"]):
                self.assertEqual(g["gate"], g["path"][0])
                for path in (g["path"], g["internal_path"]):
                    self.assertTrue(path)
                    for a,b in zip(path,path[1:]):
                        self.assertEqual(1,abs(a[0]-b[0])+abs(a[2]-b[2]))
                        self.assertLessEqual(abs(a[1]-b[1]),1)
                if g["access"]=="public":
                    x,y,z=g["path"][-1]
                    self.assertEqual(y,self.planner.model.road_columns[x,z][0])

    def test_private_routes_never_cross_public_space(self):
        p=self.planner
        for g in self.data["gardens"]:
            if g["access"]!="private":continue
            with self.subTest(g=g["id"]):
                columns={(x,z) for x,y,z in g["path"]} | rect(g["bounds"])
                self.assertFalse(columns & p.model.road_columns.keys())
                self.assertFalse(columns & p.model.court_columns.keys())
                self.assertEqual(g["owner"],g["door"]["owner"])
                self.assertEqual(g["door"]["outside"],g["path"][-1])
                self.assertTrue(p.clear(tuple(g["door"]["inside"]),2))
                self.assertIn((g["door"]["inside"][0],g["door"]["inside"][2]),p.model.footprints[g["owner"]])

    def test_fixtures_preserve_garden_internal_path(self):
        for g in self.data["gardens"]:
            blocked={(x,z) for x,y,z in g["internal_path"]}
            gx,_,gz=g["gate"]
            wall={p for p in perimeter(g["bounds"]) if abs(p[0]-gx)+abs(p[1]-gz)>1}
            self.assertFalse(blocked & wall,g["id"])
            for f in g["fixtures"]:
                x,y,z=f["position"];w,h,d=f["size"]
                cells=rect([x,z,x+w-1,z+d-1])
                self.assertFalse(cells & blocked,(g["id"],f["kind"]))
                self.assertTrue(all(self.planner.clear((xx,y,zz),h) for xx,zz in cells))
                blocked |= cells

    def test_wrong_elevation_binding_is_rejected(self):
        p=self.planner;g=self.data["gardens"][0]
        with self.assertRaisesRegex(ValueError,"标高不同"):
            p.garden_path(g,{"access":"private","owner":"house_020"})

    def test_private_takeover_of_public_well_is_rejected(self):
        g=next(g for g in self.data["gardens"] if g["id"]=="lingyue_garden_10")
        with self.assertRaisesRegex(ValueError,"公共前场"):
            self.planner.garden_path(g,{"access":"private","owner":"house_030"})

    def test_path_cannot_jump_cliff_or_exceed_limit(self):
        cells={(0,10,0),(1,12,0)}
        self.assertFalse(search_path({(0,10,0)},{(1,12,0)},cells.__contains__,20))
        cells={(0,10,0),(1,11,0),(2,12,0)}
        self.assertFalse(search_path({(0,10,0)},{(2,12,0)},cells.__contains__,1))
        self.assertEqual(3,len(search_path({(0,10,0)},{(2,12,0)},cells.__contains__,2)))

    def test_unsupported_width_is_rejected(self):
        spec=copy.deepcopy(self.planner.spec);spec["rules"]["path_width"]=3
        with self.assertRaisesRegex(ValueError,"不能忽略"):
            Planner(spec,read(ROOT/spec["sources"]["city"]),self.planner.env,self.planner.catalog)

    def test_earthwork_is_bounded_and_explicit(self):
        p=self.planner;authorized=set()
        for g in self.data["gardens"]:
            authorized |= rect(g["bounds"]) | {(x,z) for x,y,z in g["path"]}
        self.assertEqual(p.original_terrain,p.model.terrain)
        for e in self.data["earthwork"]:
            self.assertIn(tuple(e["column"]),authorized)
            self.assertLessEqual(abs(e["air_y"]-e["before_air_y"]),p.rules["local_earthwork_limit"])


if __name__=="__main__":
    unittest.main()
