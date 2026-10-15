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
        for rel in [JAVA+'cultivation/CultivationService.java',JAVA+'cultivation/CultivationAttachments.java',JAVA+'cultivation/network/FateIntentPayload.java',JAVA+'client/cultivation/panel/FatesPage.java',JAVA+'client/cultivation/AwakeningScreen.java',JAVA+'cultivation/network/CultivationPayloads.java',JAVA+'cultivation/network/AwakeningOpenPayload.java',JAVA+'cultivation/network/AwakeningClosePayload.java',JAVA+'block/SpiritTestingSteleBlock.java']:
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

    def mutate_source(self, rel, before, after):
        p = self.root / (JAVA + rel)
        source = p.read_text()
        self.assertIn(before, source, 'Mutation must change production code')
        p.write_text(source.replace(before, after))

    def test_h_page_cannot_send_selection(self):
        p=self.root/(JAVA+'client/cultivation/panel/FatesPage.java')
        p.write_text(p.read_text()+'\n class Unsafe { void pick() { ClientCultivationIntentSender.sendFate(2,0,7,1); } }')
        self.assertTrue(any('PANEL_READONLY' in e for e in validate(self.root)))

    def test_independent_screen_required(self):
        p=self.root/(JAVA+'client/cultivation/AwakeningScreen.java')
        p.write_text(p.read_text().replace('extends Screen', 'extends PanelPage'))
        self.assertTrue(any('AWAKENING_SCREEN' in e for e in validate(self.root)))

    def test_screen_must_close_session(self):
        p=self.root/(JAVA+'client/cultivation/AwakeningScreen.java')
        p.write_text(p.read_text().replace('AwakeningClosePayload', 'MissingClosePayload').replace('sendAwakeningClose', 'missingClose'))
        self.assertTrue(any('SESSION_CLOSE' in e for e in validate(self.root)))

    def test_session_token_cannot_be_authority_fields(self):
        self.mutate_source('cultivation/network/FateIntentPayload.java','long sessionToken','double damage')
        self.assertTrue(any('FATE_INTENT' in e for e in validate(self.root)))

    def test_close_payload_is_token_only(self):
        self.mutate_source('cultivation/network/AwakeningClosePayload.java','long sessionToken','long sessionToken, boolean confirmed')
        self.assertTrue(any('SESSION_PAYLOAD' in e for e in validate(self.root)))

    def test_stele_gate_components_required(self):
        for token in ('SPIRIT_TESTING_STELE','isAlive','isRemoved','isSpectator','dimension','hasChunkAt','canInteractWithBlock'):
            with self.subTest(token=token):
                p=self.root/(JAVA+'cultivation/fate/SteleAwakeningSessions.java');original=p.read_text()
                self.assertIn(token,original)
                p.write_text(original.replace(token,'omittedGate'))
                self.assertTrue(any('STELE_GATE' in e for e in validate(self.root)))
                p.write_text(original)

    def test_lifecycle_invalidation_required(self):
        for token in ('PlayerLoggedOutEvent','PlayerChangedDimensionEvent','LivingDeathEvent','EntityTeleportEvent','ServerStoppingEvent','ServerTickEvent'):
            with self.subTest(token=token):
                p=self.root/(JAVA+'cultivation/fate/SteleAwakeningSessions.java');original=p.read_text()
                self.assertIn(token,original)
                p.write_text(original.replace(token,'MissingEvent'))
                self.assertTrue(any('SESSION_LIFECYCLE' in e for e in validate(self.root)))
                p.write_text(original)

    def test_network_must_revalidate_session(self):
        self.mutate_source('cultivation/fate/FateService.java','SteleAwakeningSessions.valid','Bypass.always')
        self.assertTrue(any('SESSION_AUTHORITY' in e for e in validate(self.root)))

    def test_common_session_cannot_import_client(self):
        p=self.root/(JAVA+'cultivation/fate/SteleAwakeningSessions.java')
        p.write_text('import com.example.myvillage.client.cultivation.AwakeningScreen;\n'+p.read_text())
        self.assertTrue(any('CLIENT_IMPORT' in e for e in validate(self.root)))

    def test_shared_transition_rejects_unauthorized(self):
        self.mutate_source('cultivation/fate/FateService.java','if (!authorized)','if (false)')
        self.assertTrue(any('SESSION_AUTHORITY' in e for e in validate(self.root)))

    def test_diagnostic_bypass_requires_permission(self):
        self.mutate_source('cultivation/fate/FateService.java','source.hasPermission(2)','true')
        self.assertTrue(any('DIAGNOSTIC_PERMISSION' in e for e in validate(self.root)))

    def test_network_cannot_use_diagnostic_bypass(self):
        p=self.root/(JAVA+'cultivation/network/CultivationPayloads.java')
        p.write_text(p.read_text()+'\n FateService.diagnosticIntent(source, player, 0, 0, 0);')
        self.assertTrue(any('DIAGNOSTIC_NETWORK' in e for e in validate(self.root)))

    def test_stele_does_not_awaken_existing_root(self):
        self.mutate_source('block/SpiritTestingSteleBlock.java','spiritualRoot().isEmpty()','spiritualRoot().isPresent()')
        self.assertTrue(any('STELE_OPEN' in e for e in validate(self.root)))

    def test_stele_initializes_missing_draft(self):
        self.mutate_source('block/SpiritTestingSteleBlock.java','FateService.initializeDraft','FateService.unavailable')
        self.assertTrue(any('STELE_OPEN' in e for e in validate(self.root)))

class AwakeningControlReadbackTest(unittest.TestCase):
    def evidence(self, text, size=(960,540)):
        from types import SimpleNamespace
        from tools.innate_fates_evidence import FateEvidence
        tmp=tempfile.TemporaryDirectory();self.addCleanup(tmp.cleanup)
        log=Path(tmp.name)/'client.log';log.write_text(text)
        evidence=object.__new__(FateEvidence)
        evidence.s=SimpleNamespace(game=SimpleNamespace(client_log=log,size=size))
        return evidence

    def test_actual_latest_control_rects(self):
        e=self.evidence('AWAKENING_UI gui=480x270 step=root\nAWAKENING_UI control=continue x=28 y=230 w=424 h=19\n'
                        'AWAKENING_UI gui=480x270 step=fates\nAWAKENING_UI control=reroll x=28 y=230 w=138 h=19\n')
        self.assertEqual({'reroll':(28,230,138,19)},e.geometry())

    def test_stale_gui_cannot_supply_click_coordinates(self):
        e=self.evidence('AWAKENING_UI gui=480x270 step=root\nAWAKENING_UI control=continue x=28 y=230 w=424 h=19\n',size=(640,480))
        with self.assertRaisesRegex(RuntimeError,'No current rendered'):e.geometry()

    def test_omitted_fate_fields_decode_to_codec_defaults(self):
        from tools.innate_fates_evidence import FateEvidence
        omitted={'revision':5,'candidates':['myvillage:robust'],'awakening_session':12}
        explicit={**omitted,'rolls':0,'locked':'','selected':[],'confirmed':False,
                  'recovery_ready_at':0,'healing_ticks':0,'healing_per_tick':0.0,'hunter_ready_at':0}
        decoded=FateEvidence.decoded_state(omitted)
        self.assertEqual(explicit,decoded)
        self.assertEqual(FateEvidence.draft(FateEvidence.decoded_state(explicit)),FateEvidence.draft(decoded))
        self.assertEqual('',decoded['locked'])
        self.assertEqual(12,decoded['awakening_session'])
