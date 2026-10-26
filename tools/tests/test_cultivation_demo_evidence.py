import unittest

from tools.cultivation_demo_evidence import allowed_rcon, menu_point, select_cases, status_json


class DemoEvidenceBoundaryTest(unittest.TestCase):
    def test_player_actions_cannot_be_sent_as_rcon(self):
        for command in ['myvillage tribulation start DemoTester', 'myvillage cultivation advance DemoTester',
                        'myvillage combat cast 1', 'execute as DemoTester run myvillage combat cast 1',
                        'give DemoTester minecraft:stone\nmyvillage tribulation start DemoTester']:
            self.assertFalse(allowed_rcon(command), command)

    def test_fixture_and_readback_remain_available(self):
        for command in ['myvillage tribulation status DemoTester', 'myvillage talisman status DemoTester 0 -60 3',
                        'myvillage flying_sword status DemoTester', 'myvillage cultivation qi set DemoTester 0',
                        'data get entity DemoTester Inventory', 'save-all flush']:
            self.assertTrue(allowed_rcon(command), command)

    def test_status_must_have_one_complete_object(self):
        self.assertEqual(status_json('TRIBULATION_STATUS {"phase":"ACTIVE"}', 'TRIBULATION_STATUS '), {'phase': 'ACTIVE'})
        for response in ['', 'TRIBULATION_STATUS []', 'TRIBULATION_STATUS {',
                         'TRIBULATION_STATUS {}\nTRIBULATION_STATUS {}']:
            with self.assertRaises(ValueError):
                status_json(response, 'TRIBULATION_STATUS ')

    def test_selected_cases_do_not_silently_drop_or_repeat(self):
        self.assertEqual(select_cases('flight,drawing'), ['flight', 'drawing'])
        for value in ['', 'flight,flight', 'missing', 'flight,']:
            with self.assertRaises(ValueError):
                select_cases(value)

    def test_gui_point_tracks_actual_scaled_menu(self):
        self.assertEqual(menu_point((960, 540), 199, 122), (578, 276))
        self.assertEqual(menu_point((1280, 720), 199, 122), (786, 369))


if __name__ == '__main__':
    unittest.main()
