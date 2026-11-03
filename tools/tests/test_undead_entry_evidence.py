import unittest
from tools.qianzhen.entry_evidence import record_failure


class EntryFailureEvidenceTest(unittest.TestCase):
    def test_failed_and_unrun_cases_are_disjoint_without_losing_completed_evidence(self):
        report=dict(selected=['login','render','restart'],completed=['login'],failed=[],unrun=['render','restart'])
        error=AssertionError('entity was not active')
        record_failure(report,error)
        self.assertEqual(['login'],report['completed'])
        self.assertEqual(['render'],report['failed'])
        self.assertEqual(['restart'],report['unrun'])
        self.assertEqual(repr(error),report['error'])
        groups=[set(report[k]) for k in ('completed','failed','unrun')]
        self.assertEqual(set(report['selected']),set.union(*groups))
        self.assertEqual(sum(map(len,groups)),len(set.union(*groups)))

    def test_cleanup_failure_does_not_reclassify_completed_cases(self):
        report=dict(selected=['render'],completed=['render'],failed=[],unrun=[])
        record_failure(report,RuntimeError('server cleanup failed'))
        self.assertEqual('failed',report['status'])
        self.assertEqual(['render'],report['completed'])
        self.assertEqual([],report['failed'])
        self.assertEqual([],report['unrun'])
