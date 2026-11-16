"""隔离平坦世界内检查真实死亡掉落、回收事务及原生容器重启；不代替玩家操作验收。"""
from __future__ import annotations

import argparse
import copy
import json
import shutil
import traceback

from tools.drops.native_evidence import Evidence, ROOT

CASES = ['candidate_identity', 'registered_items_and_recycling', 'ordinary_loot',
         'all_realms_and_rarities', 'non_player_sources', 'trial_shields',
         'invalid_config_keeps_snapshot', 'restart_persistence', 'restored_defaults']


class MaterialEvidence(Evidence):
    def __init__(self, out, jar, probe):
        super().__init__(out, jar, probe, 202611160135, only=[])
        self.server.level_type = 'minecraft:flat'
        self.server.generate_structures = 'false'
        self.server.view_distance = 3
        self.selected = CASES.copy()
        self.report.update(kind='beast_materials_native', selected=self.selected, completed=[], unrun=self.selected.copy())
        self.save()

    def material_check(self, restore=False):
        action = 'restore' if restore else 'save'
        summary = self.value('material_probe ' + action, 'MATERIAL_PROBE ')
        path = self.game / f'material-probe-{action}.json'
        data = json.loads(path.read_text())
        shutil.copy2(path, self.out / path.name)
        self.check('native_material_' + action, summary['status'] == data['status'] == 'passed'
                   and all(v['passed'] for v in data['checks']), data)

    def target(self, entity, realm='qi_refining', source='player', scene='normal'):
        self.spawn('yuanxian:' + entity, scene, 0)
        self.value(f'drop_probe realm yuanxian:{realm} 0', 'DROP_PROBE ')
        if source == 'player':
            return self.finish()
        self.value('drop_probe ' + ('npc_finish' if source == 'npc' else 'environment'), 'DROP_PROBE ')
        value = self.value('drop_probe read', 'DROP_PROBE ')
        self.check('non_player_native_event_' + source,
                   value['event_items'] == value['world_items'] and not value['death_context']['playerKill'], value)
        return value

    @staticmethod
    def family_count(items, family):
        return sum(count for id, count in items.items() if id.startswith('yuanxian:' + family + '_'))

    def ordinary(self):
        self.bundled()
        habitat = self.value('yuanxian drops show yuanxian:demon_wolf habitat donghuang yuanxian:foundation_establishment 0', 'DROPS ')
        self.check('habitat_beast_uses_material_inheritance',
                   habitat['effective']['origin'] == 'overrides.beast_habitat_materials'
                   and habitat['effective']['policy']['mode'] == 'INHERIT', habitat)
        for entity in ['demon_wolf', 'thornback_badger', 'redplate_lizard', 'frostplume_pheasant', 'ember_toad']:
            value = self.target(entity)
            items = value['world_items']
            self.check('ordinary_counts_and_no_qi_core_' + entity,
                       self.family_count(items, 'beast_hide') in (1, 2)
                       and self.family_count(items, 'beast_bone') in (0, 1)
                       and self.family_count(items, 'beast_core') == 0
                       and 'yuanxian:low_grade_spirit_stone' not in items, value)
            if entity == 'redplate_lizard':
                self.check('original_scale_is_preserved', items.get('yuanxian:redplate_scale') in (1, 2), value)

    def forced(self, rarity):
        data = copy.deepcopy(self.defaults)
        for addition in data['additions']:
            for drop in addition['drops']:
                if drop.get('factory') == 'beast_material':
                    drop['rarity_weights'] = [int(i == rarity) for i in range(4)]
                    drop['chance'] = 1
        return data

    def matrix(self):
        rarities = ['common', 'uncommon', 'rare', 'legendary']
        realms = ['qi_refining', 'foundation_establishment', 'golden_core', 'nascent_soul', 'spirit_transformation']
        seen = set()
        for r, rarity in enumerate(rarities):
            self.config(self.forced(r))
            for realm in realms:
                value = self.target('thunder_tiger', realm)
                expected = {f'yuanxian:beast_hide_{realm}_{rarity}': 3, f'yuanxian:beast_bone_{realm}_{rarity}': 3}
                if realm != 'qi_refining':
                    expected[f'yuanxian:beast_core_{realm}_{rarity}'] = 1
                # Any separate original boss trophies are retained and recorded.
                actual = {id: count for id, count in value['world_items'].items()
                          if id.startswith(('yuanxian:beast_hide_', 'yuanxian:beast_bone_', 'yuanxian:beast_core_'))}
                self.check('exact_native_materials_' + realm + '_' + rarity, actual == expected, value)
                seen.update(actual)
        self.check('all_56_material_identities_observed_as_item_entities', len(seen) == 56, sorted(seen))

    def sources(self):
        self.config(self.forced(2))
        expected = {'yuanxian:beast_hide_foundation_establishment_rare': 2,
                    'yuanxian:beast_bone_foundation_establishment_rare': 1,
                    'yuanxian:beast_core_foundation_establishment_rare': 1}
        for source in ['environment', 'npc']:
            value = self.target('demon_wolf', 'foundation_establishment', source)
            self.check('same_drops_for_' + source, value['world_items'] == expected, value)
        lamp = self.target('lantern_sporeling', 'foundation_establishment')['world_items']
        self.check('lantern_only_new_core', self.family_count(lamp, 'beast_hide') == self.family_count(lamp, 'beast_bone') == 0
                   and lamp.get('yuanxian:beast_core_foundation_establishment_rare') == 1, lamp)

    def shields(self):
        for scene in ['quest_realm', 'sect_trial', 'aptitude_trial']:
            value = self.target('demon_wolf', 'foundation_establishment', scene=scene)
            self.check('trial_no_items_' + scene, value['world_items'] == {}, value)

    def invalid_weights(self):
        before = self.status()
        bad = self.forced(0)
        bad['additions'][1]['drops'][0]['rarity_weights'] = [0, 0, 0, 0]
        self.path.write_text(json.dumps(bad))
        response = self.rc('yuanxian drops reload')
        after = self.status()
        self.check('invalid_weights_retain_exact_snapshot',
                   all(before[key] == after[key] for key in ['revision', 'sha256', 'source']), response)
        self.bundled()

    def restart(self):
        self.server.stop()
        self.server.start()
        self.material_check(True)

    def run(self):
        try:
            self.case('candidate_identity', self.identity)
            self.server.start()
            self.case('registered_items_and_recycling', self.material_check)
            self.case('ordinary_loot', self.ordinary)
            self.case('all_realms_and_rarities', self.matrix)
            self.case('non_player_sources', self.sources)
            self.case('trial_shields', self.shields)
            self.case('invalid_config_keeps_snapshot', self.invalid_weights)
            self.case('restart_persistence', self.restart)
            self.case('restored_defaults', self.bundled)
            self.report['status'] = 'passed'
        except BaseException as error:
            self.report.update(status='failed', error=repr(error), traceback=traceback.format_exc())
            self.report['failed'].append(self.current)
            raise
        finally:
            try:
                self.server.stop()
            finally:
                self.save()


def main():
    from pathlib import Path
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--jar', type=Path, required=True)
    p.add_argument('--probe', type=Path, required=True)
    a = p.parse_args()
    MaterialEvidence(a.out.resolve(), a.jar.resolve(), a.probe.resolve()).run()


if __name__ == '__main__':
    main()
