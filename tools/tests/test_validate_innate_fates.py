import json
from pathlib import Path
import shutil
import tempfile
import unittest
from tools.validate_innate_fates import ROOT, JAVA, DATA, validate

class FateValidatorTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        for rel in [DATA,'src/main/resources/data/myvillage/fates','src/main/resources/assets/myvillage/lang',JAVA+'cultivation/fate']:
            shutil.copytree(ROOT/rel,self.root/rel)
        for rel in [JAVA+'cultivation/CultivationService.java',JAVA+'cultivation/CultivationAttachments.java',JAVA+'cultivation/network/FateIntentPayload.java',JAVA+'client/cultivation/panel/FatesPage.java']:
            p=self.root/rel;p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/rel,p)
    def edit(self,name,fn):
        p=self.root/DATA/(name+'.json');d=json.loads(p.read_text());fn(d);p.write_text(json.dumps(d))
    def test_current_contract(self):self.assertEqual([],validate(self.root))
    def test_unknown_effect(self):
        self.edit('robust',lambda d:d['effects'].update(phoenix=1));self.assertTrue(any('UNKNOWN' in x for x in validate(self.root)))
    def test_effect_bounds(self):
        self.edit('robust',lambda d:d['effects'].update(health=8));self.assertTrue(any('VALUE' in x for x in validate(self.root)))
    def test_recovery_requires_cooldown(self):
        self.edit('alchemist_descendant',lambda d:d['effects'].pop('recovery_cooldown'));self.assertTrue(any('RECOVERY' in x for x in validate(self.root)))
    def test_tier_cost_match(self):
        self.edit('robust',lambda d:d.update(cost=6));self.assertTrue(any('COST' in x for x in validate(self.root)))
    def test_three_ordinary_groups(self):
        self.edit('bright',lambda d:d.update(group='robust'));self.assertTrue(any('LOW_BUDGET' in x for x in validate(self.root)))
    def test_bilingual_names(self):
        self.edit('robust',lambda d:d.update(translation_key='missing'));self.assertTrue(any('LANG' in x for x in validate(self.root)))
    def test_external_attachment_writer(self):
        (self.root/(JAVA+'cultivation/fate/Evil.java')).write_text('player.setData(CultivationAttachments.FATES.get(), injected);')
        self.assertTrue(any('WRITER' in x for x in validate(self.root)))
    def test_authority_payload(self):
        p=self.root/(JAVA+'cultivation/network/FateIntentPayload.java');p.write_text(p.read_text().replace('byte action, long revision, byte value','double damage'))
        self.assertTrue(any('INTENT' in x for x in validate(self.root)))
    def test_missing_copy_on_death(self):
        p=self.root/(JAVA+'cultivation/CultivationAttachments.java');p.write_text(p.read_text().replace('FateState.CODEC).copyOnDeath()','FateState.CODEC)'))
        self.assertTrue(any('PERSISTENCE' in x for x in validate(self.root)))
