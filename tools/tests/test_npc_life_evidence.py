"""Reject false runtime evidence; these are parser checks, not game passes."""
import json
from pathlib import Path
import tempfile
import unittest
from tools.npc_life_evidence import resident_rows, read_ledger, assets
from tools.modal_runner.remote import commands_for, select_npc_life_cases
from tools.yunshan.game_export import nbt, write_typed


class NpcEvidenceTests(unittest.TestCase):
    def test_only_diagnostic_lines_and_duplicate_rejection(self):
        row = {'personId': 42, 'phase': 'moving'}
        line = 'NPC_LIFE '+json.dumps(row)
        self.assertEqual(resident_rows('localized status\n'+line), [row])
        self.assertEqual(resident_rows('Command failed'), [])
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            resident_rows(line+'\n'+line)
        with self.assertRaises(ValueError):
            resident_rows('NPC_LIFE {broken')

    def test_saved_utf8_payload_and_assets(self):
        state = {'name': '云山', 'life': {'residents': [
            {'person_id': 4, 'herbs': 2, 'coins': 6, 'completed_activities': 1}]}}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'ledger.dat'
            root = nbt.Compound({'data': nbt.Compound({'payload': nbt.Tag(7, json.dumps(state, ensure_ascii=False).encode())})})
            write_typed(path, '', root)
            self.assertEqual(read_ledger(path), state)
        self.assertEqual(assets(state), {4: (2, 6, 1)})

    def test_runtime_builds_before_game_without_full_release(self):
        runtime = commands_for('npc-life-runtime')
        self.assertEqual(runtime[0], commands_for('npc-life-build')[0])
        self.assertEqual(runtime[1][:2], ['./gradlew', 'jar'])
        self.assertNotIn('test', runtime[1])
        self.assertEqual(runtime[-1][:2], ['python3', 'tools/npc_life_evidence.py'])
        self.assertFalse(any('release_gate.py' in part or 'run_chunky_acceptance.py' in part
                             for command in runtime for part in command))

    def test_targeted_cases_keep_registration_and_reject_typos(self):
        cases = select_npc_life_cases('avatar_cap,conversation')
        self.assertEqual(cases, ['bind_idempotence', 'demo_identity', 'conversation', 'avatar_cap'])
        self.assertEqual(commands_for('npc-life-runtime', 'avatar_cap,conversation')[-1][-1], ','.join(cases))
        for invalid in ('', 'typo', 'avatar_cap,avatar_cap'):
            with self.assertRaises(ValueError):
                select_npc_life_cases(invalid)


if __name__ == '__main__':
    unittest.main()
