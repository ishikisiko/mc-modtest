"""Mutation coverage for the complete playable ladder and its cross-file contracts."""
import copy
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from tools import cultivation_progression_contract as contract
from tools.validate_cultivation_advancement import CultivationAdvancementValidator
from tools.validate_cultivation_gain import CultivationGainValidator

ROOT = Path(__file__).resolve().parents[2]


class CultivationProgressionContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for relative in (contract.REALMS, contract.STATS, contract.SIM_REALMS):
            source, target = ROOT / relative, self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(source, target)
            else:
                shutil.copy2(source, target)

    def errors(self, cross_files=True):
        return [message for _path, message in contract.validate_progression(self.root, cross_files=cross_files)[0]]

    def assert_code(self, code, errors):
        self.assertTrue(any(e.startswith(code + ':') for e in errors), errors)

    def mutate_json(self, relative, mutation):
        path = self.root / relative
        data = json.loads(path.read_text())
        mutation(data)
        path.write_text(json.dumps(data))

    def mutate_stage(self, sid, mutation):
        rid = contract.STAGE_OWNERS[sid]
        relative = contract.REALMS / (rid.split(':')[1] + '.json')
        self.mutate_json(relative, lambda realm: mutation(next(s for s in realm['stages'] if s['id'] == sid)))

    def test_complete_ladder_is_reachable_and_has_exactly_one_playable_terminal(self):
        self.assertEqual([], self.errors())
        stages = {s['id']: s for path in (self.root / contract.REALMS).glob('*.json')
                  for s in json.loads(path.read_text())['stages']}
        route = []
        sid = 'myvillage:mortal_qi_sensed'
        while True:
            self.assertNotIn(sid, route)
            route.append(sid)
            if 'advancement' not in stages[sid]:
                break
            sid = stages[sid]['advancement']['target_stage']
        self.assertEqual(16, len(route))
        self.assertEqual('myvillage:golden_late', route[-1])
        self.assertEqual(set(stages) - {'myvillage:mortal_unawakened'}, set(route))
        self.assertEqual(15, sum('advancement' in s for s in stages.values()))

    def test_every_missing_edge_is_a_premature_terminal(self):
        for sid in contract.PLAYABLE_CHAIN[:-1]:
            with self.subTest(sid=sid):
                path = self.root / contract.REALMS / (contract.STAGE_OWNERS[sid].split(':')[1] + '.json')
                original = path.read_text()
                self.mutate_stage(sid, lambda s: s.pop('advancement'))
                errors = self.errors()
                self.assert_code('PROGRESSION_TERMINAL', errors)
                self.assert_code('PROGRESSION_REACHABILITY', errors)
                path.write_text(original)

    def test_skipping_an_existing_stage_is_rejected(self):
        self.mutate_stage('myvillage:qi_refining_4', lambda s: s['advancement'].update(target_stage='myvillage:qi_refining_6'))
        self.assert_code('PROGRESSION_ADJACENCY', self.errors())
        self.assert_code('PROGRESSION_REACHABILITY', self.errors())

    def test_self_loop_and_multi_stage_cycle_are_rejected(self):
        for target in ('myvillage:qi_refining_5', 'myvillage:qi_refining_4'):
            self.mutate_stage('myvillage:qi_refining_5', lambda s: s['advancement'].update(target_stage=target))
            self.assert_code('PROGRESSION_CYCLE', self.errors())

    def test_major_boundaries_require_the_exact_next_owner_and_stage(self):
        for sid, wrong_realm, wrong_stage in (
            ('myvillage:qi_refining_9', 'myvillage:golden_core', 'myvillage:golden_early'),
            ('myvillage:foundation_late', 'myvillage:golden_core', 'myvillage:golden_middle'),
            ('myvillage:qi_refining_9', 'myvillage:qi_refining', 'myvillage:foundation_early'),
        ):
            with self.subTest(sid=sid):
                path = self.root / contract.REALMS / (contract.STAGE_OWNERS[sid].split(':')[1] + '.json')
                original = path.read_text()
                self.mutate_stage(sid, lambda s: s['advancement'].update(target_realm=wrong_realm, target_stage=wrong_stage))
                self.assert_code('PROGRESSION_ADJACENCY', self.errors())
                path.write_text(original)

    def test_terminal_cannot_gain_or_advance_to_a_supported_stats_only_realm(self):
        self.mutate_stage('myvillage:golden_late', lambda s: s.update(cultivation_cap=8400, advancement={
            'target_realm': 'myvillage:nascent_soul', 'target_stage': 'myvillage:nascent_early'}))
        self.assert_code('PROGRESSION_TERMINAL', self.errors())

    def test_deleted_duplicated_reordered_or_wrong_owner_stage_is_rejected(self):
        relative = contract.REALMS / 'foundation_establishment.json'
        path = self.root / relative
        original = path.read_text()
        for change in (lambda d: d['stages'].pop(),
                       lambda d: d['stages'].append(copy.deepcopy(d['stages'][0])),
                       lambda d: d['stages'].reverse(),
                       lambda d: d['stages'][1].update(id='myvillage:golden_middle')):
            path.write_text(original)
            self.mutate_json(relative, change)
            self.assert_code('PROGRESSION_STAGE', self.errors())
        path.write_text(original)
        (self.root / contract.REALMS / 'golden_core.json').unlink()
        self.assert_code('PROGRESSION_REALM', self.errors())

    def test_registered_but_unreachable_extra_stage_is_rejected(self):
        self.mutate_json(contract.REALMS / 'golden_core.json', lambda d: d['stages'].append({
            'id': 'myvillage:golden_secret', 'sort_order': 3, 'spiritual_power_cap': 650}))
        self.assert_code('PROGRESSION_STAGE', self.errors())

    def test_next_realm_must_agree_with_the_actual_major_transition(self):
        self.mutate_json(contract.REALMS / 'foundation_establishment.json', lambda d: d.update(next_realm='myvillage:qi_refining'))
        self.assert_code('PROGRESSION_NEXT_REALM', self.errors())

    def test_cap_and_stone_cost_match_java_numeric_limits(self):
        sid = 'myvillage:foundation_early'
        path = self.root / contract.REALMS / 'foundation_establishment.json'
        original = path.read_text()
        for key, value, code in (('cultivation_cap', 1, 'PROGRESSION_CAP'),
                                 ('cultivation_cap', 2 * contract.MAX_INT + 2, 'PROGRESSION_CAP'),
                                 ('cultivation_cap', True, 'PROGRESSION_CAP'),
                                 ('cultivation_cap', float('inf'), 'PROGRESSION_CAP'),
                                 ('spirit_stone_cost', 0, 'PROGRESSION_COST'),
                                 ('spirit_stone_cost', contract.MAX_INT + 1, 'PROGRESSION_COST'),
                                 ('spirit_stone_cost', True, 'PROGRESSION_COST')):
            path.write_text(original)
            self.mutate_stage(sid, lambda s: s.update({key: value}))
            self.assert_code(code, self.errors())

    def test_missing_cost_cannot_leave_a_nominally_reachable_but_unusable_source(self):
        self.mutate_stage('myvillage:golden_middle', lambda s: s.pop('spirit_stone_cost'))
        self.assert_code('PROGRESSION_COST', self.errors())

    def test_required_stability_and_compatibility_cost_remain_cap_derived(self):
        for key, code in (('required_stability', 'PROGRESSION_STABILITY'), ('stability_cost', 'PROGRESSION_RETENTION')):
            self.mutate_stage('myvillage:golden_early', lambda s: s['advancement'].update({key: 1}))
            self.assert_code(code, self.errors())

    def test_a_coherent_new_stage_retune_is_valid_and_odd_compatibility_cost_rounds_up(self):
        # No duplicated new-stage balance table: valid authored tuning is accepted.
        self.mutate_stage('myvillage:foundation_early', lambda s: s.update(cultivation_cap=3002))
        self.mutate_stage('myvillage:foundation_early', lambda s: s['advancement'].update(required_stability=1501, stability_cost=751))
        self.assertEqual([], self.errors())
        self.mutate_stage('myvillage:foundation_early', lambda s: s['advancement'].update(stability_cost=750))
        self.assert_code('PROGRESSION_RETENTION', self.errors())

    def test_duration_kind_loss_and_rule_field_boundaries(self):
        path = self.root / contract.REALMS / 'foundation_establishment.json'
        original = path.read_text()
        for key, value, code in (('duration_ticks', 0, 'PROGRESSION_RULE'),
                                 ('duration_ticks', True, 'PROGRESSION_RULE'),
                                 ('duration_ticks', contract.MAX_INT + 1, 'PROGRESSION_RULE'),
                                 ('kind', 'random', 'PROGRESSION_KIND'),
                                 ('interruption_stability_loss', 1, 'PROGRESSION_LOSS'),
                                 ('authority', 1, 'PROGRESSION_RULE')):
            path.write_text(original)
            self.mutate_stage('myvillage:foundation_early', lambda s: s['advancement'].update({key: value}))
            self.assert_code(code, self.errors())
        path.write_text(original)
        self.mutate_stage('myvillage:foundation_late', lambda s: s['advancement'].update(interruption_stability_loss=0, kind='ordinary'))
        self.assert_code('PROGRESSION_LOSS', self.errors())
        self.assert_code('PROGRESSION_KIND', self.errors())

    def test_first_four_transitions_remain_pinned(self):
        self.mutate_stage('myvillage:qi_refining_2', lambda s: s['advancement'].update(duration_ticks=121))
        self.assert_code('PROGRESSION_BASELINE', self.errors())

    def test_power_is_explicit_nonnegative_bounded_and_preserves_old_capacities(self):
        path = self.root / contract.REALMS / 'golden_core.json'
        original = path.read_text()
        for value in (None, -1, 0, True, 600.5, float('inf'), contract.MAX_LONG + 1):
            path.write_text(original)
            self.mutate_stage('myvillage:golden_middle', lambda s: s.update(spiritual_power_cap=value))
            self.assert_code('PROGRESSION_POWER', self.errors())
        path.write_text(original)
        self.mutate_stage('myvillage:golden_middle', lambda s: s.update(spiritual_power_cap=399))
        self.assert_code('PROGRESSION_POWER', self.errors())
        self.mutate_stage('myvillage:qi_refining_7', lambda s: s.update(spiritual_power_cap=91))
        self.assert_code('PROGRESSION_POWER', self.errors())

    def test_alias_coverage_duplicates_and_owner_or_ordinal_mismatch(self):
        path = self.root / contract.STATS
        original = path.read_text()
        for mutation in (lambda d: d['player_stage_aliases'].pop(),
                         lambda d: d['player_stage_aliases'].append(d['player_stage_aliases'][0]),
                         lambda d: d['player_stage_aliases'][-1].update(realm='myvillage:foundation_establishment'),
                         lambda d: d['player_stage_aliases'][-1].update(stage=1),
                         lambda d: d['player_stage_aliases'][-1].update(stage=True),
                         lambda d: d['player_stage_aliases'][-1].update(id='myvillage:unknown')):
            path.write_text(original)
            self.mutate_json(contract.STATS, mutation)
            self.assert_code('PROGRESSION_ALIAS', self.errors())

    def test_lifespans_and_stage_counts_agree_with_the_ledger(self):
        self.mutate_json(contract.SIM_REALMS, lambda d: next(r for r in d['realms'] if r['id'] == 'golden_core').update(lifespan_years=501))
        self.assert_code('PROGRESSION_LIFESPAN', self.errors())
        self.mutate_json(contract.SIM_REALMS, lambda d: next(r for r in d['realms'] if r['id'] == 'golden_core')['stages'].pop())
        self.assert_code('PROGRESSION_STAGE_MAPPING', self.errors())
        self.mutate_json(contract.REALMS / 'golden_core.json', lambda d: d.update(maximum_lifespan_years=499))
        self.assert_code('PROGRESSION_LIFESPAN', self.errors())

    def test_both_focused_data_gates_use_the_shared_graph_contract(self):
        self.mutate_stage('myvillage:qi_refining_9', lambda s: s.pop('advancement'))
        gain = CultivationGainValidator(self.root)
        gain.validate_caps()
        advancement = CultivationAdvancementValidator(self.root)
        advancement.validate_rules()
        for errors in (gain.errors, advancement.errors):
            self.assertTrue(any('PROGRESSION_REACHABILITY' in error for error in errors), errors)

    def test_duplicate_json_keys_are_not_silently_discarded(self):
        path = self.root / contract.REALMS / 'golden_core.json'
        path.write_text('{"stages":[],"stages":[]}')
        self.assert_code('PROGRESSION_FILE', self.errors())


if __name__ == '__main__':
    unittest.main()
