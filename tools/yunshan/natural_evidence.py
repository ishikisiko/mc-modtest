#!/usr/bin/env python3
"""Actual NORMAL-world site survey, sparse city fitting and mapped cloud evidence.

Only the disposable Modal world is touched. Original flushed Anvil snapshots
are retained separately from fitted-world data; RCON prepares and reads state,
while actual mapped W input proves the selected paths.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import copy
import hashlib
import json
import math
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from tools.combat_capture import session as cs, scene, procs
from tools.realm_stats_evidence import Evidence
from tools.yunshan.district_game_evidence import DistrictEvidence, chunk_rectangle, chunk_readiness_commands, package_world
from tools.yunshan.game_evidence import aim


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1024*1024), b''):
            value.update(part)
    return value.hexdigest()


def generation_batches(window):
    """Chunk-aligned, <=256-chunk rectangles covering an exclusive XY window."""
    if len(window) != 4 or any(isinstance(v, bool) or not isinstance(v, int) for v in window):
        raise ValueError('Survey window must contain four integers')
    x0, z0, x1, z1 = window
    if x0 >= x1 or z0 >= z1 or x1-x0 > 1024 or z1-z0 > 1024:
        raise ValueError('Survey window must be nonempty and at most 1024 square')
    for cx in range(x0//16, (x1-1)//16+1, 16):
        for cz in range(z0//16, (z1-1)//16+1, 16):
            ex = min(cx+15, (x1-1)//16); ez = min(cz+15, (z1-1)//16)
            yield [cx*16, cz*16, ex*16+15, ez*16+15]


def operation_bounds(command):
    """Whitelist bounded terrain fills/setblocks, preserving the fitter's order."""
    if len(command.encode('utf-8')) > 1200:
        raise ValueError('Terrain RCON command exceeds 1200 bytes')
    words = command.split()
    try:
        if words[0] == 'fill' and len(words) >= 8:
            a = list(map(int, words[1:4])); b = list(map(int, words[4:7]))
            low = [min(a[i], b[i]) for i in range(3)]
            high = [max(a[i], b[i]) for i in range(3)]
            if math.prod(high[i]-low[i]+1 for i in range(3)) > 32768:
                raise ValueError('Terrain fill exceeds vanilla 32768-block limit')
        elif words[0] == 'setblock' and len(words) >= 5:
            low = high = list(map(int, words[1:4]))
        else:
            raise ValueError('Only bounded fill/setblock terrain commands are accepted')
    except (IndexError, TypeError) as error:
        raise ValueError('Malformed terrain command') from error
    if low[1] < -64 or high[1] > 319:
        raise ValueError('Terrain command leaves overworld build height')
    return low, high


def terrain_batches(operations):
    batch = []; low = high = None
    for operation in operations:
        a, b = operation_bounds(operation['command'])
        new_low = a if low is None else [min(a[i], low[i]) for i in range(3)]
        new_high = b if high is None else [max(b[i], high[i]) for i in range(3)]
        chunks = (new_high[0]//16-new_low[0]//16+1)*(new_high[2]//16-new_low[2]//16+1)
        if batch and (len(batch) >= 512 or chunks > 256):
            yield low, high, batch
            batch = []; new_low, new_high = a, b
        if (new_high[0]//16-new_low[0]//16+1)*(new_high[2]//16-new_low[2]//16+1) > 256:
            raise ValueError('One terrain operation needs more than 256 chunks')
        batch.append(operation); low, high = new_low, new_high
    if batch:
        yield low, high, batch


def snapshot_regions(world, window, destination):
    """Copy only the survey's real region files; caller must save/flush first."""
    destination.mkdir(parents=True, exist_ok=True)
    (destination/'region').mkdir(exist_ok=True)
    x0, z0, x1, z1 = window
    paths = [Path('level.dat')]
    paths += [Path(f'region/r.{rx}.{rz}.mca') for rx in range(x0//512, (x1-1)//512+1)
              for rz in range(z0//512, (z1-1)//512+1)]
    hashes = {}
    for relative in paths:
        source = world/relative
        if not source.is_file() or source.is_symlink():
            raise FileNotFoundError(f'Actual saved survey file missing: {source}')
        target = destination/relative
        shutil.copyfile(source, target)
        hashes[relative.as_posix()] = {'sha256': digest(target), 'bytes': target.stat().st_size}
    return hashes


def probe_groups(probes):
    if isinstance(probes, dict):
        return [(category, probe) for category, entries in probes.items() for probe in entries]
    return [(probe.get('category', 'core'), probe) for probe in probes]


def raw_site_cameras(candidate):
    """Actual-terrain fallback frames remain available for rejected fit plans."""
    width, depth = candidate.get('size', [265, 210] if candidate['rotation_quarters'] % 2 else [210, 265])
    x = candidate['origin'][0]+width/2; z = candidate['origin'][1]+depth/2
    water = candidate['water_surface']; target = [x, water+8, z]
    cameras = []
    for name, pos in [('natural_overview', [x+90, water+70, z+105]),
                      ('shore_and_slope', [x+115, water+50, z-45])]:
        yaw, pitch = aim(pos, target)
        cameras.append({'name': name, 'pos': pos, 'yaw': yaw, 'pitch': pitch})
    return cameras


def refine_candidate(survey, candidate, settings):
    """Re-evaluate an authored site adjustment against its existing MCA survey."""
    refinement = settings.get('candidate_refinements', {}).get(candidate['id'])
    if refinement is None:
        return candidate
    from tools.yunshan.natural_terrain import evaluate_site
    reason = refinement.get('reason', refinement.get('why'))
    if not isinstance(reason, str) or not reason.strip():
        raise ValueError('Candidate refinement requires an explicit reason')
    evaluated = evaluate_site(survey, refinement['origin'], refinement['rotation_quarters'], settings=settings)
    result = copy.deepcopy(candidate)
    result.update(evaluated)
    # Keep the original comparison-row identity and rank. Coordinates, score,
    # eligibility and metrics are exclusively the fresh point evaluation.
    result['id'] = candidate['id']
    if 'rank' in candidate:
        result['rank'] = candidate['rank']
    result['refined_from'] = {'original_id': candidate['id'], **{
        key: copy.deepcopy(candidate[key]) for key in ('origin', 'rotation_quarters', 'score', 'metrics', 'eligible', 'limitations') if key in candidate}}
    result['refinement_reason'] = reason
    result['refinement_evaluation_id'] = evaluated['id']
    return result


class NaturalEvidence(DistrictEvidence):
    def __init__(self, out, site_id=None, seed=20261002):
        Evidence.__init__(self, out)
        if type(seed) is not int or not -(1 << 63) <= seed < (1 << 63):
            raise ValueError("seed must be a signed 64-bit integer")
        self.seed = seed
        self.site_id = site_id
        self.hud_hidden = False
        self.buildings = []
        self.report.update(kind='yunshan_normal_world_fusion', status='running',
            selected=['actual_normal_terrain_survey', 'three_distinct_sites', 'before_frames',
                      'sparse_natural_fit', 'mapped_outgoing_paths', 'mapped_rotated_core_entry',
                      'support_shore_boundary_readback', 'matched_after_frames', 'playable_world_export'],
            completed=[], screenshots=[], snapshots=[], buildings={}, command_log='commands.jsonl',
            command_count=0, command_count_semantics='attempts including failed calls',
            command_attempt_count=0, command_success_count=0, command_failure_count=0,
            owner_appearance='not_verified', owner_feel='not_verified',
            owner_sound='not_verified', owner_fps='not_verified', multiplayer='not_verified',
            software_rendering=True, world_seed=seed,
            method='real normal terrain; immutable flushed MCA survey; sparse fitting; actual mapped W; before/after real-client frames')
        self.report['scope'] = 'single_authored_site' if site_id is not None else 'three_candidate_comparison'
        self.report['candidate_manifest'] = 'candidates.json' if site_id is not None else 'top3.json'
        if site_id is not None:
            self.report['site_id'] = site_id
            self.report['selected'] = ['single_site_actual_survey', *self.report['selected'][2:]]
            self.report['not_selected'] = ['actual_normal_terrain_survey', 'three_distinct_sites']
        self.save()

    def rc(self, command):
        if len(command.encode('utf-8')) > 1200:
            raise ValueError('RCON command exceeds the 1200-byte safe packet budget')
        self.report['command_count'] += 1
        self.report['command_attempt_count'] += 1
        record = {'attempt': self.report['command_attempt_count'], 'command': command, 'timeout_s': 45}
        began = time.monotonic()
        try:
            with self.s.rcon(retries=1) as client:
                client.timeout = 45
                client.sock.settimeout(45)
                reply = client.cmd(command)
                record['reply'] = reply
            self.report['command_success_count'] += 1
            return reply
        except BaseException as error:
            self.report['command_failure_count'] += 1
            record.update(error=repr(error), outcome_unknown=True)
            # The server may already have applied the command. Preserve the
            # failed attempt and propagate it; never replay a timed-out edit.
            raise
        finally:
            record['wall_s'] = round(time.monotonic()-began, 3)
            with (self.out/'commands.jsonl').open('a') as stream:
                stream.write(json.dumps(record, ensure_ascii=False)+'\n')

    @contextmanager
    def phase(self, name):
        began = time.monotonic()
        try:
            yield
        finally:
            self.report['phases'].append({'phase': name, 'wall_s': round(time.monotonic()-began, 3)})
            self.save()

    @contextmanager
    def loaded(self, low, high, timeout=180):
        rectangle = ' '.join(map(str, chunk_rectangle(low, [high[i]-low[i]+1 for i in range(3)])))
        self.rc('forceload add '+rectangle)
        began = time.monotonic()
        try:
            for command in chunk_readiness_commands(low, high):
                self.poll(lambda: self.rc(command), lambda reply: 'passed' in reply,
                          max(.2, timeout-(time.monotonic()-began)), 'normal-world chunk batch loaded')
            yield
        finally:
            self.rc('forceload remove '+rectangle)

    def start(self):
        if ROOT != Path('/workspace/repo'):
            raise RuntimeError('Actual natural-world job is restricted to the Modal disposable /workspace/repo checkout')
        with self.phase('normal_client_server_start'):
            cs.merge_options(cs.CLIENT_DIR/'options.txt', {'key_key.forward': 'key.keyboard.w', 'key_key.use': 'key.mouse.right'})
            state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), language='zh_cn',
                lock=str(procs.default_lock_path(ROOT)), natural_terrain=True, view_distance=16, world_seed=self.seed))
            self.s = cs.Session.attach(ui_check=True); self.sessions += 1
            properties = dict(line.split('=', 1) for line in (cs.SERVER_DIR/'server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
            self.check('actual_normal_generator', properties.get('level-type') == 'minecraft\\:normal'
                       and properties.get('level-seed') == str(self.seed),
                       {key: properties.get(key) for key in ('level-type', 'level-seed', 'generate-structures', 'view-distance')})
            self.report['capture_start_timings'] = state.get('timings', {})
            self.report['graphics'] = subprocess.check_output(['glxinfo', '-B'], env=self.s.game.env, text=True, timeout=20)
            request = Path('/workspace/request.json')
            if request.is_file(): self.report['source_commit'] = json.loads(request.read_text())['commit']
            self.report['mod_version'] = next(line.split('=', 1)[1] for line in (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
            self.report['built_jars'] = {path.name: digest(path) for path in (ROOT/'build/libs').glob('*.jar')}
            bindings = dict(line.split(':', 1) for line in (cs.CLIENT_DIR/'options.txt').read_text().splitlines() if ':' in line)
            self.check('mapped_forward_binding', bindings.get('key_key.forward') == 'key.keyboard.w', {'forward': bindings.get('key_key.forward')})
            for command in (*scene.world_rules(), f'op {self.s.username}', f'clear {self.s.username}',
                            f'gamemode spectator {self.s.username}', 'myvillage world pause',
                            'gamerule doFireTick false', 'gamerule randomTickSpeed 0'):
                self.rc(command)

    def survey(self, window, label, settings):
        from tools.yunshan.natural_terrain import sample_window
        with self.phase('survey_'+label):
            batches = list(generation_batches(window))
            for x0, z0, x1, z1 in batches:
                with self.loaded([x0, 64, z0], [x1, 64, z1]):
                    pass
            self.rc('save-off')
            try:
                self.rc('save-all flush')
                original = self.out/'original_regions'/label
                hashes = snapshot_regions(cs.SERVER_DIR/cs.LEVEL_NAME, window, original)
            finally:
                self.rc('save-on')
            result = sample_window(original, window, self.seed, settings=settings)
            survey_path = self.out/'surveys'/(label+'.json'); survey_path.parent.mkdir(exist_ok=True)
            survey_path.write_text(json.dumps(result, ensure_ascii=False)+'\n')
            self.report['snapshots'].append({'label': label, 'window': window, 'chunk_batches': len(batches),
                'files': hashes, 'directory': str(original.relative_to(self.out)),
                'survey': str(survey_path.relative_to(self.out)), 'survey_sha256': digest(survey_path)})
            return result

    def capture(self, candidate_id, cameras, stage):
        self.rc(f'gamemode spectator {self.s.username}'); self.set_hud(True)
        for camera in cameras:
            self.teleport(camera['pos'], camera['yaw'], camera['pitch'])
            low = [math.floor(camera['pos'][0])-80, 64, math.floor(camera['pos'][2])-80]
            high = [low[0]+160, 64, low[2]+160]
            with self.loaded(low, high, timeout=90):
                for _ in range(2): self.s.game.stable_grab(max_wait=5, settle=2)
                relative = Path('frames')/candidate_id/(stage+'-'+camera['name']+'.png')
                capture = self.s.game.shot(self.out/relative, max_wait=12, settle=2)
            self.report['screenshots'].append({'candidate': candidate_id, 'stage': stage,
                'camera': camera, 'file': relative.as_posix(), 'capture': capture,
                'client_mesh_completion': 'not instrumented; actual frame review required'})
            self.save()
        self.set_hud(False)

    def apply_fit(self, manifest, directory):
        # Intermediate fill order must not let fluid escape through banks
        # that have not yet been installed. All gameplay runs unfrozen.
        self.rc('tick freeze')
        try:
            self._apply_fit(manifest, directory)
        finally:
            self.rc('tick unfreeze')

    def _apply_fit(self, manifest, directory):
        self.spec = manifest; self.manifest = directory/'fit.json'
        operations = (json.loads(line) for line in (directory/'terrain_commands.jsonl').read_text().splitlines() if line.strip())
        count = 0
        with self.phase('terrain_transition_commands'):
            for low, high, batch in terrain_batches(operations):
                with self.loaded(low, high):
                    for operation in batch:
                        reply = self.rc(operation['command'])
                        if any(word in reply.lower() for word in ('unknown', 'incorrect argument', 'outside of the world', 'too many blocks')):
                            raise RuntimeError('Terrain command failed: '+reply)
                        count += 1
                self.report['terrain_commands_applied'] = count; self.save()
        pack = cs.SERVER_DIR/cs.LEVEL_NAME/'datapacks/yunshan_natural'
        pack.mkdir(parents=True, exist_ok=True)
        (pack/'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'Sparse Yunshan natural-world fusion'}}))
        for tile in manifest['tiles']:
            namespace, name = tile['id'].split(':', 1)
            relative = Path(name+'.nbt')
            if relative.is_absolute() or '..' in relative.parts: raise ValueError('Unsafe natural tile resource')
            target = pack/'data'/namespace/'structure'/relative; target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(directory/tile['nbt'], target)
        self.rc('reload')
        self.poll(lambda: self.rc('datapack list enabled'), lambda reply: 'yunshan_natural' in reply, 20, 'natural fit pack enabled')
        with self.phase('sparse_city_placement'):
            placed = []
            for tile in manifest['tiles']:
                low = tile['world_min']; high = [low[i]+tile['size'][i]-1 for i in range(3)]
                with self.loaded(low, high):
                    probe = tile['probe']; xyz = ' '.join(map(str, probe['pos']))
                    marker = 'minecraft:bedrock' if probe['state'].split('[', 1)[0] == 'minecraft:barrier' else 'minecraft:barrier'
                    self.rc('setblock '+xyz+' '+marker)
                    self.poll(lambda: self.rc('execute if block '+xyz+' '+marker), lambda reply: 'passed' in reply, 5, 'sparse tile marker')
                    self.rc('place template '+tile['id']+' '+' '.join(map(str, low)))
                    reply = self.poll(lambda: self.rc('execute if block '+xyz+' '+probe['state']), lambda value: 'passed' in value, 15, 'sparse tile readback')
                    placed.append({'id': tile['id'], 'probe': probe, 'marker': marker, 'readback': reply})
                self.report['placed_tiles'] = placed; self.save()
        self.report['completed'].append('sparse_natural_fit'); self.save()

    def verify_fit(self):
        groups = probe_groups(self.spec['probes'])
        categories = {category for category, _ in groups}
        self.check('fit_probe_categories', {'support', 'boundary', 'shore', 'path'} <= categories, sorted(categories))
        for category, probe in groups:
            pos = probe['pos']
            with self.loaded(pos, pos):
                reply = self.rc('execute if block '+' '.join(map(str, pos))+' '+probe['state'])
                self.check(category+'_block_'+','.join(map(str, pos)), 'passed' in reply, {'expected': probe, 'readback': reply})
        self.report['completed'].append('support_shore_boundary_readback')
        trials = self.spec['path_trials']
        if not trials: raise ValueError('Natural fitting emitted no mapped outgoing trials')
        for trial in trials:
            self.walk_world(trial['name'], trial['start'], trial['end'],
                min_y=max(trial.get('min_y', trial['end'][1]), trial['end'][1]-.1), timeout_s=trial.get('timeout_s', 15))
        self.report['completed'].append('mapped_outgoing_paths')
        installation = self.spec['buildings'][0]
        data = copy.deepcopy(json.loads((ROOT/'blueprints/yunshan'/installation['source']).read_text()))
        data['_installation'] = installation
        self.building_walk(data, next(trial for trial in data['walk_trials'] if trial['name'] == 'main_entry'))
        self.report['completed'].append('mapped_rotated_core_entry'); self.save()

    def finish_world(self):
        spawn = self.spec['recommended_spawn']; pos = spawn['pos']; block = list(map(math.floor, pos))
        for command in ('setworldspawn '+' '.join(map(str, block))+' '+str(spawn.get('yaw', 0)),
                        f'spawnpoint {self.s.username} '+' '.join(map(str, block)),
                        'defaultgamemode creative', f'gamemode creative {self.s.username}',
                        f'effect clear {self.s.username}', 'myvillage world resume',
                        'gamerule randomTickSpeed 3', 'forceload remove all'):
            self.rc(command)
        self.teleport(pos, spawn.get('yaw', 0), spawn.get('pitch', 0))
        self.check('natural_delivery_spawn', all(abs(a-b) < .2 for a,b in zip(self.player_pos(), pos)), spawn)
        self.rc('save-all flush'); self.stop()
        self.report['world_export'] = package_world(cs.SERVER_DIR/cs.LEVEL_NAME,
            self.out/'yunshan_natural_world.zip', level_name='云山 · 自然山水融合预览', folder_name='Yunshan Natural Preview')
        self.report['completed'].append('playable_world_export'); self.save()

    def run(self):
        from tools.yunshan.natural_terrain import read_settings, coarse_windows, rank_sites, expanded_window, evaluate_site
        from tools.yunshan.fit_natural import preview_fit, compile_fit
        settings = read_settings()
        if settings['seed'] != 20261002 or self.seed != 20261002:
            raise ValueError('Natural evidence requires the fixed authored seed 20261002')
        refinement = None
        if self.site_id is not None:
            if self.site_id not in settings.get('candidate_refinements', {}):
                raise ValueError('--site-id must name an authored candidate_refinements entry')
            refinement = settings['candidate_refinements'][self.site_id]
        self.start()
        expanded = {}
        if self.site_id is not None:
            descriptor = {'id': self.site_id, 'origin': refinement['origin'],
                          'rotation_quarters': refinement['rotation_quarters']}
            survey = self.survey(expanded_window(descriptor, settings=settings), 'expanded-'+self.site_id, settings)
            candidate = evaluate_site(survey, refinement['origin'], refinement['rotation_quarters'], settings=settings)
            candidate['evaluation_id'] = candidate['id']; candidate['id'] = self.site_id
            candidate['authored_site'] = {'config_key': self.site_id, **copy.deepcopy(refinement)}
            candidates = [candidate]; expanded[self.site_id] = survey
            self.report['completed'].append('single_site_actual_survey')
        else:
            surveys = [self.survey(window['window'], 'coarse-'+window['id'], settings) for window in coarse_windows(settings)]
            self.report['completed'].append('actual_normal_terrain_survey')
            candidates = rank_sites(surveys, settings=settings)[:3]
            self.check('three_independent_actual_sites', len(candidates) == 3, candidates)
            self.report['completed'].append('three_distinct_sites')
        self.report['candidates'] = candidates
        for index, candidate in enumerate(candidates):
            if candidate['id'] in expanded:
                survey = expanded[candidate['id']]
            else:
                survey = self.survey(expanded_window(candidate, settings=settings), 'expanded-'+candidate['id'], settings)
                candidate = refine_candidate(survey, candidate, settings)
            candidates[index] = candidate
            expanded[candidate['id']] = survey
            preview_directory = self.out/'sites'/candidate['id']
            try:
                preview = preview_fit(survey, candidate, out=preview_directory)
                candidate['fit_validation'] = preview['validation']
            except ValueError as error:
                candidate['fit_validation'] = {'passed': False, 'reason': str(error)}
                preview = {'candidate': candidate, 'validation': candidate['fit_validation'],
                           'fixture_cameras': raw_site_cameras(candidate)}
                preview_directory.mkdir(parents=True, exist_ok=True)
                (preview_directory/'fit-preview.json').write_text(json.dumps(preview, ensure_ascii=False, indent=2)+'\n')
            self.capture(candidate['id'], preview.get('matched_cameras', preview['fixture_cameras']), 'before')
            candidate['preview_paths'] = {name: f'sites/{candidate["id"]}/{name}'
                for name in ('fit-preview.json', 'heightmaps.json', 'preview.svg') if (preview_directory/name).is_file()}
            candidate['before_screenshots'] = [shot['file'] for shot in self.report['screenshots']
                if shot['candidate'] == candidate['id'] and shot['stage'] == 'before']
            (self.out/self.report['candidate_manifest']).write_text(json.dumps(candidates, ensure_ascii=False, indent=2)+'\n')
        self.report['completed'].append('before_frames')
        selected = manifest = directory = None
        selection_order = sorted(candidates, key=lambda candidate: candidate.get('score', math.inf))
        self.report['selection_order'] = [candidate['id'] for candidate in selection_order]
        with self.phase('compile_natural_fit'):
            for candidate in selection_order:
                if candidate.get('eligible') is not True or candidate['fit_validation'].get('passed') is not True:
                    continue
                attempt = self.out/'fit_attempts'/candidate['id']
                try:
                    compiled = compile_fit(expanded[candidate['id']], candidate, attempt)
                    if compiled['validation']['passed'] is not True:
                        raise ValueError('Compiled validation failed: '+json.dumps(compiled['validation']))
                except ValueError as error:
                    candidate['compile_failure'] = str(error)
                    attempt.mkdir(parents=True, exist_ok=True)
                    (attempt/'failure.json').write_text(json.dumps({'candidate': candidate, 'error': str(error)}, ensure_ascii=False, indent=2)+'\n')
                    self.save()
                    continue
                selected, manifest, directory = candidate, compiled, attempt
                break
        self.check('selected_site_passes_authored_and_fit_gates', selected is not None, candidates)
        stable_directory = self.out/'fit'
        directory.rename(stable_directory); directory = stable_directory
        self.report['selected_id'] = selected['id']; self.report['recommended_site'] = selected['id']
        (self.out/self.report['candidate_manifest']).write_text(json.dumps(candidates, ensure_ascii=False, indent=2)+'\n')
        self.check('compiled_fit_validation', manifest['validation']['passed'] is True, manifest['validation'])
        before_cameras = [shot['camera'] for shot in self.report['screenshots']
            if shot['candidate'] == selected['id'] and shot['stage'] == 'before']
        after_cameras = manifest.get('matched_cameras', manifest['fixture_cameras'])
        self.check('before_after_camera_geometry_matches', before_cameras == after_cameras,
                   {'before': before_cameras, 'after': after_cameras})
        self.report['fit_manifest'] = {'path': str((directory/'fit.json').relative_to(self.out)), 'sha256': digest(directory/'fit.json')}
        self.apply_fit(manifest, directory); self.verify_fit()
        self.capture(selected['id'], manifest.get('matched_cameras', manifest['fixture_cameras']), 'after')
        self.report['after_screenshots'] = [shot['file'] for shot in self.report['screenshots'] if shot['stage'] == 'after']
        self.report['completed'].append('matched_after_frames')
        self.finish_world(); self.report['status'] = 'pass'; self.save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT/'out/preview/yunshan-natural/runtime')
    parser.add_argument('--site-id', help='Revalidate one authored candidate_refinements key using its own actual survey')
    args = parser.parse_args(); evidence = NaturalEvidence(args.out, site_id=args.site_id)
    try:
        evidence.run()
    except BaseException as error:
        evidence.report['status'] = 'fail'; evidence.report['failures'].append(repr(error)); evidence.save()
        raise
    finally:
        if evidence.s:
            try: evidence.s.game.xdo('keyup', 'w')
            finally: evidence.stop()
        evidence.save()


if __name__ == '__main__':
    main()
