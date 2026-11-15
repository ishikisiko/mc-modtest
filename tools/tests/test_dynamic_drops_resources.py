import base64,copy,gzip,hashlib,json,tempfile,unittest
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from province_capitals.game.compile_v11 import refresh_habitat_reference
from wilderness_habitats.generate import generate

class DynamicDropsResourcesTest(unittest.TestCase):
    def test_habitat_has_no_frozen_drop_balance(self):
        authored=json.loads((ROOT/'blueprints/wilderness/habitats.json').read_text())
        self.assertNotIn('loot',authored)
        generated=generate();self.assertNotIn('loot',generated)
        self.assertEqual(generated,json.loads((ROOT/'src/main/resources/data/myvillage/wilderness/habitats.json').read_text()))

    def test_targeted_reference_refresh_preserves_all_other_frozen_inputs(self):
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory);p=out/'data/myvillage/worldplan/frozen_v11.json';p.parent.mkdir(parents=True)
            original={'algorithm':'myvillage:worldplan_v11','habitats':{'resource_sha256':'0'*64},'cities':[{'name':'keep','surface':[1,4,7]}],'opaque':{'untouched':True}}
            p.write_text(json.dumps(original))
            preset=out/'data/myvillage/worldgen/world_preset/cultivation.json';preset.parent.mkdir(parents=True)
            original_preset={'dimensions':{'minecraft:overworld':{'generator':{'type':'myvillage:planned_v11','rules':'old','other':'keep'}}}}
            preset.write_text(json.dumps(original_preset));refresh_habitat_reference(out)
            result=json.loads(p.read_text());expected=copy.deepcopy(original)
            raw=(ROOT/'src/main/resources/data/myvillage/wilderness/habitats.json').read_bytes()
            expected['habitats']={'resource_sha256':hashlib.sha256(raw).hexdigest()};self.assertEqual(expected,result)
            updated=json.loads(preset.read_text());generator=updated['dimensions']['minecraft:overworld']['generator']
            self.assertEqual('keep',generator['other']);self.assertEqual(result,json.loads(gzip.decompress(base64.b64decode(generator['rules'][5:]))))

    def test_default_policy_has_preserved_scenes_and_single_fire_probability(self):
        data=json.loads((ROOT/'src/main/resources/data/myvillage/drops/monster_drops.json').read_text())
        self.assertEqual({'inherit'},{v['mode'] for v in data['entities'].values()})
        self.assertEqual(71,len(data['entities']))
        self.assertEqual(['aptitude_trial','sect_trial','quest_realm','donghuang_habitat'],[v['id'] for v in data['overrides']])
        self.assertEqual(.035,data['additions'][0]['drops'][0]['chance'])
        self.assertNotIn('beast_drop_chance',json.loads((ROOT/'tools/forging/foundry.json').read_text())['fire'])

if __name__=='__main__':unittest.main()
