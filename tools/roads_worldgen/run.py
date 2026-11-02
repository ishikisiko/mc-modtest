"""在 Colab 隔离新世界检查生成期官道、完整路幅、掉落物和重启缓存。"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import secrets
import shutil
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from roads_smoke.run import Server, verify_candidate
from roadweaver_probe.run import write
from buildgen.nbtread import read_gzipped_nbt
from yunshan.natural_terrain import read_region


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--seed', type=int, required=True)
    parser.add_argument('--seconds', type=int, default=2400)
    args = parser.parse_args()
    args.out = args.out.resolve()
    args.out.mkdir(parents=True, exist_ok=False)
    selected = ['candidate_runtime_identity', 'fresh_world_generation', 'full_road_pregeneration',
                'full_width_and_gate_readback', 'no_generation_drops', 'cached_restart',
                'existing_chunk_edit_preserved', 'ordinary_drops_still_enabled']
    result = dict(source=args.source, seed=args.seed, selected=selected, completed=[], failed=[],
                  unrun=selected.copy(), owner_acceptance='not_verified',
                  full_route_player_walk='not_verified', visual_acceptance='not_verified',
                  multiplayer='not_verified', owner_modpack='not_verified')

    def complete(name):
        result['completed'].append(name)
        result['unrun'].remove(name)
        write(args.out / 'result.json', result)

    def no_items():
        answer = server.command('execute if entity @e[type=minecraft:item]')
        assert 'Test failed' in answer, answer
        return answer

    def load_column(x, z):
        server.command(f'forceload add {x} {z}')
        deadline = time.monotonic() + 60
        while not server.test(f'if loaded {x} 300 {z}'):
            if time.monotonic() > deadline:
                raise TimeoutError('Fixture column did not load')
            time.sleep(1)

    game = ROOT / 'run-road-worldgen' / ('native-' + secrets.token_hex(4))
    write(args.out / 'world.json', dict(directory=str(game), seed=args.seed,
          kind='fresh isolated development server world', doTileDrops='true'))
    server = Server(args.out, game, seed=args.seed)
    stage = selected[0]
    write(args.out / 'result.json', result)
    try:
        identity = verify_candidate(args.jar.resolve())
        identity['source'] = args.source
        write(args.out / 'identity.json', identity)
        complete(stage)

        stage = 'fresh_world_generation'
        started = time.monotonic()
        server.start()
        status = server.roads('status')
        assert status['mode'] == 'chunk_generation' and status['phase'] == 'ready', status
        assert status['cities'] == 8 and len(status['routes']) == 7, status
        assert status['road_columns'] > 0 and status['road_chunks'] > 0, status
        assert status['planning_progress']['phase'] != 'cached', status
        assert 'true' in server.command('gamerule doTileDrops')
        write(args.out / 'startup.json', dict(seconds=time.monotonic() - started, status=status))
        no_items()
        server.roads('export')
        shutil.copy2(server.data / 'roads.json', args.out / 'roads.json')
        layout = json.loads((args.out / 'roads.json').read_text())
        assert layout['layout_hash'] == status['layout_hash']
        complete(stage)

        stage = 'full_road_pregeneration'
        server.roads('pregenerate')
        deadline = time.monotonic() + args.seconds
        progress = []
        while time.monotonic() < deadline:
            if server.process.poll() is not None:
                raise RuntimeError('Server exited during pregeneration')
            status = server.roads('status')
            write(args.out / 'progress.json', status)
            progress.append(dict(elapsed=time.monotonic() - started, status=status))
            write(args.out / 'progress-history.json', progress)
            if status['phase'] in ['complete', 'failed', 'partial']:
                break
            time.sleep(10)
        else:
            raise TimeoutError(str(status))
        shutil.copy2(server.data / 'roads-audit.json', args.out / 'roads-audit.json')
        audit = json.loads((args.out / 'roads-audit.json').read_text())
        check = audit['status']['inspection']
        assert status['phase'] == 'complete', dict(status=status, first_failures=audit['failures'][:20])
        assert check['processed_chunks'] == check['total_chunks'] and check['unloaded_chunks'] == 0, check
        complete(stage)

        stage = 'full_width_and_gate_readback'
        assert check['failure_count'] == 0 and not audit['failures'], check
        assert check['checked_columns'] >= sum(len(c['columns']) for c in layout['chunk_columns']), check
        complete(stage)

        stage = 'no_generation_drops'
        assert check['item_entities_seen'] == 0, check
        time.sleep(15)
        no_items()
        assert 'true' in server.command('gamerule doTileDrops')
        server.command('save-all flush')
        saved_items = []
        entity_files = []
        for path in sorted((game / 'world/entities').glob('*.mca')):
            chunks, digest = read_region(path)
            entity_files.append(dict(name=path.name, sha256=digest, chunks=len(chunks)))
            for chunk in chunks.values():
                saved_items.extend(e for e in chunk.get('Entities', []) if e.get('id') == 'minecraft:item')
        write(args.out / 'saved-entities.json', dict(regions=entity_files, items=saved_items))
        assert not saved_items, saved_items[:20]
        write(args.out / 'drops.json', dict(generated_road_items=check['item_entities_seen'],
              loaded_item_entities=0, saved_item_entities=0, doTileDrops=True, entities_removed=False))
        complete(stage)

        # An operator fixture proves that finished chunks are not repaved on load.
        cell = next(c for chunk in layout['chunk_columns'] for c in chunk['columns'] if c['band'] == 0)
        position = f"{cell['x']} {(cell['feet2'] - 1) // 2} {cell['z']}"
        load_column(cell['x'], cell['z'])
        server.command(f'setblock {position} minecraft:diamond_block')
        assert server.test(f'if block {position} minecraft:diamond_block')
        before_hash = status['layout_hash']

        stage = 'cached_restart'
        server.stop()
        level = game / 'world/level.dat'
        _, saved = read_gzipped_nbt(str(level))
        cache = saved['Data']['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']['roads']
        assert cache['layout_hash'] == before_hash and len(cache['routes']) == 7, cache.keys()
        assert len(cache['chunks']) == status['road_chunks']
        write(args.out / 'saved-world.json', dict(level_dat_bytes=level.stat().st_size,
              level_dat_sha256=hashlib.sha256(level.read_bytes()).hexdigest(),
              cached_chunks=len(cache['chunks']), cached_routes=len(cache['routes'])))
        started = time.monotonic()
        server.start()
        restarted = server.roads('status')
        assert restarted['layout_hash'] == before_hash and restarted['phase'] == 'ready', restarted
        assert restarted['planning_progress']['phase'] == 'cached', restarted
        write(args.out / 'restart.json', dict(seconds=time.monotonic() - started, status=restarted))
        complete(stage)

        stage = 'existing_chunk_edit_preserved'
        assert server.test(f'if block {position} minecraft:diamond_block')
        write(args.out / 'player-edit.json', dict(position=position, preserved_after_restart=True,
              setup='isolated operator fixture; no mapped player input claim'))
        complete(stage)

        stage = 'ordinary_drops_still_enabled'
        # Positive control occurs after the zero-drop gate and is retained in the world.
        load_column(0, 0)
        server.command('setblock 0 300 0 minecraft:oak_planks')
        server.command('setblock 0 300 0 minecraft:air destroy')
        assert server.test('if entity @e[type=minecraft:item,x=0,y=300,z=0,distance=..8]')
        assert 'true' in server.command('gamerule doTileDrops')
        write(args.out / 'ordinary-drops.json', dict(destroyed_plank_item_present=True, doTileDrops=True))
        complete(stage)
    except BaseException as error:
        result['failed'].append(dict(case=stage, error=repr(error)))
        result['unrun'] = [case for case in selected if case not in result['completed'] and case != stage]
        write(args.out / 'result.json', result)
        raise
    finally:
        server.stop()
        write(args.out / 'result.json', result)


if __name__ == '__main__':
    main()
