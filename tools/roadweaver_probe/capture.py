"""真实客户端只读观察；截图不是玩家行走或外观验收。"""
from __future__ import annotations

import json
import math
import os
import re
from pathlib import Path
import subprocess
import time

from combat_capture.session import CLIENT_OPTIONS, merge_options
from combat_capture.xgame import Game


def capture(server, root, terminate):
    display = next(':'+str(n) for n in range(91, 120) if not Path(f'/tmp/.X11-unix/X{n}').exists())
    directory = server.out/'client'
    directory.mkdir(parents=True, exist_ok=True)
    merge_options(directory/'options.txt', {**CLIENT_OPTIONS, 'lang': 'zh_cn', 'renderDistance': '6',
                                          'simulationDistance': '5', 'maxFps': '20'})
    result = {'status': 'running', 'screenshots': [], 'method': 'native spectator client; software OpenGL',
              'actual_player_traversal': 'not_verified', 'owner_appearance': 'not_verified'}
    client = xvfb = None
    xlog = (server.out/'xvfb.log').open('w')
    clog = (server.out/'client-launch.log').open('w')
    try:
        xvfb = subprocess.Popen(['Xvfb', display, '-screen', '0', '960x540x24', '+extension', 'GLX',
                                 '+render', '-noreset'], stdout=xlog, stderr=subprocess.STDOUT, start_new_session=True)
        deadline = time.monotonic()+20
        while not Path('/tmp/.X11-unix/X'+display[1:]).exists():
            if xvfb.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError('Xvfb did not start')
            time.sleep(.2)
        user = 'RoadProbeView'
        args = ['./gradlew', '-I', 'tools/roadweaver_probe/probe.init.gradle', 'runClient',
                '-x', 'generateAllStructures', '--no-daemon', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx3g',
                f'-Pcombat_smoke_game_dir={directory}', f'-Pcombat_smoke_username={user}',
                f'-Pcombat_smoke_server=127.0.0.1:{server.sport}']
        if server.runtime_jar:
            args += ['-x', 'processResources']
        env = dict(server.env, DISPLAY=display, LIBGL_ALWAYS_SOFTWARE='1', LP_NUM_THREADS='2', MYVILLAGE_ROAD_PROBE='0')
        client = subprocess.Popen(args, cwd=root, env=env, stdout=clog, stderr=subprocess.STDOUT, start_new_session=True)
        deadline = time.monotonic()+600
        while user not in server.command('list'):
            if client.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError('Native client did not join; see client-launch.log')
            time.sleep(3)
        game = Game(display, directory/'logs/latest.log')
        if not (game.wait_ingame(60, poll=1) or game.recover_grab()):
            raise RuntimeError('Client joined without in-game view')
        server.command(f'gamemode spectator {user}')
        game.key('F1')
        probe = json.loads((server.out/'probe.json').read_text())
        columns = json.loads((server.out/'columns.json').read_text())
        middle = columns[len(columns)//2]
        positions = [('from_gate', probe['from']['pos']),
                     ('road_middle', [middle['x'], middle['actual_surface'], middle['z']]),
                     ('to_gate', probe['to']['pos'])]
        for label, target in positions:
            eye = [target[0]+28, target[1]+22, target[2]+35]
            dx, dy, dz = [target[i]-eye[i] for i in range(3)]
            yaw = math.degrees(math.atan2(-dx, dz))
            pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
            # First login may asynchronously enter the player's Qianzhen realm. Bind the
            # observation to the overworld and read back the actual camera before capture.
            for attempt in range(3):
                server.command(f'gamemode spectator {user}')
                server.command(f'execute in minecraft:overworld run tp {user} '+' '.join(f'{v:.3f}' for v in [*eye, yaw, pitch]))
                time.sleep(12)
                dimension = server.command(f'data get entity {user} Dimension')
                position = server.command(f'data get entity {user} Pos')
                numbers = [float(v) for v in re.findall(r'[-+]?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?', position)]
                if '"minecraft:overworld"' in dimension and len(numbers) == 3 and all(abs(a-b) < .05 for a, b in zip(numbers, eye)):
                    break
            else:
                raise RuntimeError('Observation dimension/position readback did not match the requested camera')
            path = server.out/(label+'.png')
            stability = game.shot(path, max_wait=12, settle=1)
            result['screenshots'].append({'file': path.name, 'eye': eye, 'target': target,
                                          'stability': stability, 'status': 'captured_for_review',
                                          'server_dimension': dimension, 'server_position': position})
        result['status'] = 'captured_for_review'
    except Exception as error:
        result['status'] = 'failed'
        result['error'] = repr(error)
    finally:
        if client:
            result['client_cleanup'] = terminate(client)
        if xvfb:
            result['display_cleanup'] = terminate(xvfb)
        xlog.close()
        clog.close()
        (server.out/'capture.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    return result
