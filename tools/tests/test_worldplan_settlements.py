"""Fail-closed native evidence parsing; no game or world creation."""
import unittest
from tools.worldplan.settlement_native import tickets_released
from tools.worldplan.acceptance import decode_saved_rules
from tools.worldplan.district import encode_rules


class SettlementEvidenceTest(unittest.TestCase):
    def test_native_empty_ticket_reply_is_distinct_from_loaded_or_failed_queries(self):
        self.assertTrue(tickets_released('No force loaded chunks were found in Overworld\n'))
        for reply in ('', 'Removed all force loaded chunks from Overworld',
                      'Found 1 force loaded chunks in Overworld: [0, 0]',
                      'Unknown or incomplete command',
                      'No force loaded chunks were found in The Nether',
                      'No force loaded chunks were found in Overworld\nFound 1 force loaded chunks'):
            self.assertFalse(tickets_released(reply),reply)

    def test_saved_algorithm_must_match_the_requested_version(self):
        source={'algorithm':'myvillage:worldplan_v5'};encoded=encode_rules(source)
        self.assertEqual(source,decode_saved_rules(encoded,'myvillage:worldplan_v5'))
        with self.assertRaises(ValueError):decode_saved_rules(encoded)
        with self.assertRaises(ValueError):
            decode_saved_rules(encode_rules({'algorithm':'myvillage:worldplan_v4'}),'myvillage:worldplan_v5')


if __name__=='__main__':unittest.main()
