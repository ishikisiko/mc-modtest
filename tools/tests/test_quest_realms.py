"""关卡设计检查必须能发现断路、错误机关依赖和站位占用。"""
from copy import deepcopy
import unittest

from tools.quest_realms.model import read, reachable, standing_issues, validate, build
from tools.quest_realms.audit import route_audit


class QuestRealmDesignTest(unittest.TestCase):
    def test_all_authored_progressions_are_reachable(self):
        for ident in read('catalog')['ids']:
            with self.subTest(map=ident):
                spec=read(ident);validate(spec)
                done={a['id']for a in spec['actions']}
                self.assertEqual({n['id']for n in spec['nodes']},reachable(spec,done))

    def test_unknown_gate_is_rejected(self):
        spec=deepcopy(read('water_shrine'));spec['edges'][0]['requires']=['absent_sluice']
        with self.assertRaisesRegex(ValueError,'未知机关'):validate(spec)

    def test_disconnected_optional_room_is_rejected(self):
        spec=deepcopy(read('cliff_hunt'));spec['edges']=[e for e in spec['edges']if 'cache'not in(e['a'],e['b'])]
        with self.assertRaisesRegex(ValueError,'孤立区域'):validate(spec)

    def test_action_cannot_be_behind_its_own_gate(self):
        spec=deepcopy(read('red_mine'))
        for e in spec['edges']:
            if 'timber'in(e['a'],e['b']):e['requires']=['support']
        with self.assertRaisesRegex(ValueError,'不能从当前阶段到达'):validate(spec)

    def test_goal_gate_changes_reachability(self):
        spec=read('cliff_hunt')
        self.assertNotIn('ambush',reachable(spec,{'tracks'}))
        self.assertIn('ambush',reachable(spec,{'tracks','bell'}))

    def test_avatar_body_volume_detects_a_low_obstacle(self):
        spec={'nodes':[{'id':'stand','pos':[10,12,10]}]}
        box=[9.8,12.1,9.8,.4,.4,.4,0,'prop','']
        self.assertEqual('stand',standing_issues(spec,[box],read('catalog'))[0]['node'])
        box[1]=10
        self.assertEqual([],standing_issues(spec,[box],read('catalog')))

    def test_two_combat_models_have_clear_continuous_routes(self):
        for ident in ('bandit_stronghold','beast_den'):
            with self.subTest(map=ident):
                model=build(ident)
                audit=route_audit(model)
                self.assertGreater(audit['samples'],1000)
                self.assertEqual([],audit['failures'])

    def test_route_audit_detects_a_wall_between_clear_endpoints(self):
        model={'boxes':[[-2,-1,-2,8,1,4,0,'path','']],
               'routes':[{'a':'start','b':'end','points':[[0,0,0],[3,0,0]]}]}
        self.assertEqual(0,route_audit(model)['failed'])
        model['boxes'].append([1,0,-1,1,2,2,0,'wall',''])
        self.assertGreater(route_audit(model)['failed'],0)
        self.assertTrue(any(f['reason']=='站立体积被占用' for f in route_audit(model)['failures']))

    def test_route_audit_detects_missing_ground(self):
        model={'boxes':[[-1,-1,-1,2,1,2,0,'path','']],
               'routes':[{'a':'start','b':'end','points':[[0,0,0],[3,0,0]]}]}
        self.assertTrue(any(f['reason']=='没有足下支撑' for f in route_audit(model)['failures']))


if __name__=='__main__':unittest.main()
