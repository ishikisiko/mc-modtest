"""实验报告必须保留路边阻塞、端点缺口和不完整采集。"""
import unittest

from .audit import summarize


def cell(x, z=0, *, clear=True, pavement=True, reserved=False, y=70):
    return {'x': x, 'z': z, 'target_y': y, 'segment': 0, 'reserved': reserved,
            'actual_surface': y+1, 'blocks': [
                {'y': y, 'pavement': pavement, 'solid': True, 'fluid': False, 'clear': clear}]}


class AuditTests(unittest.TestCase):
    def probe(self, count):
        return {'declared_columns': count, 'connections': [{'status': 'COMPLETED'}],
                'from': {'pos': [0, 71, 0]}, 'to': {'pos': [2, 71, 0]}}

    def test_clear_centre_does_not_hide_blocked_side_column(self):
        columns = [cell(0), cell(1), cell(2), cell(1, 1, clear=False)]
        result = summarize(self.probe(4), [{'spans': []}], columns)
        self.assertFalse(result['developer_geometry_pass'])
        self.assertEqual([{'position': [1, 70, 1], 'reason': 'blocked_or_liquid',
                           'observed': columns[-1]['blocks'][0]}], result['failed_columns'])

    def test_reserved_missing_pavement_is_retained(self):
        result = summarize(self.probe(1), [{}], [cell(0, pavement=False, reserved=True)])
        self.assertEqual('missing_pavement', result['failed_columns'][0]['reason'])
        self.assertTrue(result['failed_columns'][0]['reserved'])
        self.assertFalse(result['developer_geometry_pass'])

    def test_partial_readback_cannot_pass(self):
        result = summarize(self.probe(3), [{}], [cell(0), cell(1)])
        self.assertFalse(result['full_width_readback_complete'])
        self.assertFalse(result['developer_geometry_pass'])

    def test_height_cliff_breaks_connectivity(self):
        result = summarize(self.probe(2), [{}], [cell(0), cell(1, y=74)])
        self.assertEqual([1, 1], result['clear_pavement_components'])

    def test_endpoint_gap_and_failure_state_remain_visible(self):
        probe = self.probe(2)
        probe['connections'] = [{'status': 'FAILED'}]
        result = summarize(probe, [{}], [cell(1), cell(2)])
        self.assertEqual(1, result['from_gate_pavement_distance'])
        self.assertFalse(result['developer_geometry_pass'])

    def test_empty_experiment_is_not_a_pass(self):
        self.assertFalse(summarize({}, [], [])['developer_geometry_pass'])


if __name__ == '__main__':
    unittest.main()
