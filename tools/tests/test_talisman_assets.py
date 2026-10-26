"""制符资源闭环与关键服务器事务边界；不启动 Minecraft。"""
import json
import unittest
from pathlib import Path
from tools import gen_talisman_assets as gen

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT/'src/main/java/com/example/myvillage'

class TalismanAssetsTest(unittest.TestCase):
    def test_every_owned_output_matches_generator_and_recipes_have_real_inputs(self):
        for path, data in gen.outputs().items():
            with self.subTest(path=path): self.assertEqual(data, (ROOT/path).read_bytes())
        recipes=gen.read('recipes.json')
        self.assertEqual({'talisman_desk','talisman_paper','spiritual_ink'},set(recipes))
        for recipe in recipes.values():
            self.assertTrue(recipe.get('ingredients') or recipe.get('key'))
            self.assertGreater(recipe['result']['count'],0)
    def test_all_items_have_models_textures_names_and_registration(self):
        names=gen.read('rules.json')['names']; content=(JAVA/'talisman/TalismanContent.java').read_text()
        lang=json.loads((ROOT/gen.ASSETS/'lang/zh_cn.json').read_text())
        for item,name in names.items():
            self.assertIn('"'+item+'"',content)
            self.assertTrue((ROOT/gen.ASSETS/f'models/item/{item}.json').is_file())
            if item!='talisman_desk': self.assertTrue((ROOT/gen.ASSETS/f'textures/item/{item}.png').is_file())
            self.assertEqual(name,lang[('block' if item=='talisman_desk' else 'item')+'.myvillage.'+item])
        rules=json.loads((ROOT/gen.DATA/'talisman/rules.json').read_text())
        self.assertNotIn('names',rules)
        self.assertEqual(4,len(rules['lightning']['damage']))
        self.assertEqual(4,len(rules['snare']['root_ticks']))
        self.assertEqual(4,len(rules['ward']['shield']))
    def test_block_drops_only_one_sealed_inventory_and_has_no_automation(self):
        block=(JAVA/'talisman/TalismanDeskBlock.java').read_text()
        self.assertIn('desk.saveToItem',block);self.assertNotIn('dropContents',block)
        desk=(JAVA/'talisman/TalismanDeskBlockEntity.java').read_text()
        self.assertIn('getSlotsForFace(Direction d) { return new int[0]; }',desk)
        self.assertIn('session.pulse(',desk);self.assertIn('needsRelease = true',desk)
        self.assertIn('ContainerHelper.saveAllItems',desk);self.assertIn('t.put("Batch", batch.save())',desk)
    def test_three_shared_cooldowns_and_paid_shield_use_existing_authority(self):
        runtime=(JAVA/'talisman/TalismanRuntime.java').read_text()
        for item in ('LIGHTNING','SNARE','WARD'):
            self.assertIn(f'addCooldown(TalismanContent.{item}.get(), ticks)',runtime)
        self.assertIn('ShieldRuntime.grantPaid',runtime)
        self.assertIn('CultivationService.spendSpiritualPower',runtime)
        self.assertIn('CombatDamageService.hurtDerived',runtime)
        self.assertIn('ControlService.root',runtime);self.assertIn('CombatActors.mayHarm',runtime)
        self.assertNotIn('EntityType.LIGHTNING_BOLT',runtime)
        self.assertNotIn('MeditationManager',runtime)
    def test_common_package_cannot_import_client_and_status_schema_is_stable(self):
        for source in (JAVA/'talisman').glob('*.java'):
            self.assertNotIn('import net.minecraft.client.',source.read_text())
        source=(JAVA/'talisman/TalismanCommands.java').read_text()
        self.assertIn('"TALISMAN_STATUS "',source);self.assertIn('addProperty("schema", 1)',source)
        self.assertNotIn('spendSpiritualPower',source)
        data=(JAVA/'talisman/TalismanDeskBlockEntity.java').read_text()
        for key in ('progress','required','batch','frozenQuality','holding','owner','inventory'):
            self.assertIn('"'+key+'"',data)

if __name__=='__main__':unittest.main()
