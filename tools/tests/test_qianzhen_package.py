"""组合证据不能把中断路线、过期源或输入代码变化算作通过。"""
from pathlib import Path
import json
import tempfile
import unittest
from unittest.mock import patch

from tools.qianzhen.package_game import ROOT, completed_walk, sha, verify_reuse


class QianzhenPackageTests(unittest.TestCase):
    def test_reuse_requires_return_leg_same_source_and_video(self):
        source = ROOT/'blueprints/qianzhen/relay_inn.json'
        authored = json.loads(source.read_text())
        slug, identity = authored['presentation']['role'], authored['id']
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            (folder/slug).mkdir()
            (folder/slug/'walkthrough.mp4').write_bytes(b'recorded-video-fixture')
            row = {'mapped_targets':list(authored['walk_targets']),'source_sha256':sha(source)}
            checks = [{'check':slug+'_walk_'+name,'pass':True}
                      for name in (*authored['walk_targets'],'return_entry')]
            run = {'folder':folder,'evidence':{'buildings':{identity:row},'checks':checks}}
            self.assertTrue(completed_walk(run,identity,authored))
            checks[-1]['pass'] = False
            self.assertFalse(completed_walk(run,identity,authored))
            checks[-1]['pass'] = True
            row['source_sha256'] = 'stale-source'
            self.assertFalse(completed_walk(run,identity,authored))
            row['source_sha256'] = sha(source)
            (folder/slug/'walkthrough.mp4').unlink()
            self.assertFalse(completed_walk(run,identity,authored))

    def test_production_change_invalidates_prior_input_evidence(self):
        a,b = ({'state':{'commit':c}} for c in ('prior','current'))
        with patch('tools.qianzhen.package_game.subprocess.check_output',return_value='src/main/java/Changed.java\n'):
            with self.assertRaisesRegex(ValueError,'生产或输入工具'):
                verify_reuse(a,b)

    def test_movement_change_invalidates_prior_evidence_but_ui_settle_does_not(self):
        a,b = ({'state':{'commit':c}} for c in ('prior','current'))
        before = 'def travel_to():\n    return 1\ndef facilities():\n    return 1\n'
        ui_only = 'def travel_to():\n    return 1\ndef facilities():\n    return 2\n'
        movement = 'def travel_to():\n    return 2\ndef facilities():\n    return 2\n'
        with patch('tools.qianzhen.package_game.subprocess.check_output',return_value=''):
            with patch('tools.qianzhen.package_game.source_text',side_effect=[before,ui_only]):
                verify_reuse(a,b)
            with patch('tools.qianzhen.package_game.source_text',side_effect=[before,movement]):
                with self.assertRaisesRegex(ValueError,'行走输入'):
                    verify_reuse(a,b)
            with patch('tools.qianzhen.package_game.source_text',side_effect=["KEYS={'forward':'w'}\n"+before,"KEYS={'forward':'s'}\n"+before]):
                with self.assertRaisesRegex(ValueError,'行走输入'):
                    verify_reuse(a,b)


if __name__=='__main__':
    unittest.main()
