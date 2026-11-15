from pathlib import Path
import json
import shutil
import tempfile
import unittest

from tools.golden_core_evidence import ROOT, parse_profile, shipped_chain


class GoldenCoreEvidenceTest(unittest.TestCase):
    def test_profile_keeps_namespaced_ids_and_counters(self):
        profile = parse_profile('''Cultivation profile for Test
realm: yuanxian:golden_core
stage: yuanxian:golden_late
cultivation progress: 0
stability: 1800
current spiritual power: 123/600
spiritual affinity: 10
lifespan consumed ticks: 4567
spiritual root: yuanxian:metal=10000
learned techniques: yuanxian:basic_breathing=0
running core technique: yuanxian:basic_breathing
''')
        self.assertEqual(profile['stage'], 'yuanxian:golden_late')
        self.assertEqual((profile['power'], profile['power_cap'], profile['age_ticks']), (123, 600, 4567))

    def test_partial_readback_cannot_count_as_evidence(self):
        with self.assertRaisesRegex(ValueError, 'Incomplete'):
            parse_profile('stage: yuanxian:golden_late\ncurrent spiritual power: 600/600')

    def test_shipped_chain_reaches_terminal_without_admin_stage_jumps(self):
        chain = shipped_chain()
        self.assertEqual(len(chain), 16)
        self.assertEqual(chain[-1]['id'], 'yuanxian:golden_late')
        self.assertEqual(sum(a['realm'] != b['realm'] for a,b in zip(chain,chain[1:])), 3)

    def test_bad_chain_is_rejected_before_starting_minecraft(self):
        for mutation in ('cycle', 'premature', 'wrong_realm', 'skip'):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as directory:
                root=Path(directory)
                relative=Path('src/main/resources/data/yuanxian/yuanxian/realm')
                shutil.copytree(ROOT/relative, root/relative)
                path=root/relative/'golden_core.json'
                data=json.loads(path.read_text())
                rule=data['stages'][0]['advancement']
                if mutation=='cycle': rule['target_stage']='yuanxian:golden_early'
                elif mutation=='premature': data['stages'][0].pop('advancement')
                elif mutation=='wrong_realm': rule['target_realm']='yuanxian:mortal'
                else: rule['target_stage']='yuanxian:golden_late'
                path.write_text(json.dumps(data))
                with self.assertRaises(ValueError): shipped_chain(root)


if __name__=='__main__': unittest.main()
