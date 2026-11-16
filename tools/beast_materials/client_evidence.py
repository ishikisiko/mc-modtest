"""真实客户端的回收菜单画面与鼠标筛选；菜单由测试夹具打开，不声称NPC交易验收。"""
from __future__ import annotations

import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import traceback

from tools.drops.native_evidence import ROOT
from tools.roadweaver_probe.run import terminate, write


def capture(evidence):
    out = evidence.out / 'client-menu'
    out.mkdir()
    game = ROOT / 'run-beast-material-client' / evidence.out.name
    (game / 'mods').mkdir(parents=True)
    shutil.copy2(evidence.probe, game / 'mods' / evidence.probe.name)
    (game / 'options.txt').write_text('lang:zh_cn\nguiScale:2\nmaxFps:30\nrenderDistance:3\nsimulationDistance:3\nenableVsync:false\n')
    (game / 'material-preview.request').write_text('open')
    display = next(':' + str(n) for n in range(100, 200) if not Path('/tmp/.X11-unix/X' + str(n)).exists())
    env = dict(os.environ, DISPLAY=display, LIBGL_ALWAYS_SOFTWARE='1', JAVA_TOOL_OPTIONS='-Xmx3g')
    xlog = (out / 'xvfb.log').open('w')
    xvfb = subprocess.Popen(['Xvfb', display, '-screen', '0', '1100x700x24', '+extension', 'GLX', '-nolisten', 'tcp', '-noreset'], stdout=xlog, stderr=subprocess.STDOUT, start_new_session=True)
    client = None
    log = None
    record = {'status': 'running', 'evidence': 'real_client_render_fixture_and_mapped_filter_mouse_input',
              'npc_opening_and_trade': 'not_verified', 'owner_visual': 'not_verified', 'inputs': []}
    try:
        deadline = time.monotonic() + 20
        while not Path('/tmp/.X11-unix/X' + display[1:]).exists():
            if time.monotonic() > deadline:
                raise TimeoutError('Xvfb启动超时')
            time.sleep(.1)
        command = ['./gradlew', 'runClient', '-Pcombat_smoke_server=127.0.0.1:' + str(evidence.server.sport),
                   '-Pcombat_smoke_game_dir=' + str(game), '-Pcombat_smoke_username=MaterialReview',
                   '-x', 'processResources', '--no-daemon', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx1g']
        log = (out / 'client.log').open('w')
        client = subprocess.Popen(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        state_path = game / 'material-preview-state.json'
        deadline = time.monotonic() + 900
        while not state_path.exists():
            if client.poll() is not None:
                raise RuntimeError('客户端退出：' + str(client.returncode))
            if time.monotonic() > deadline:
                raise TimeoutError('真实客户端菜单未打开')
            time.sleep(1)
        ids = subprocess.check_output(['xdotool', 'search', '--onlyvisible', '--name', 'Minecraft'], env=env, text=True).splitlines()
        window = ids[-1]
        subprocess.run(['xdotool', 'windowfocus', '--sync', window], env=env, check=True)

        def snapshot():
            state_path.unlink(missing_ok=True)
            (game / 'material-preview.request').write_text('snapshot')
            end = time.monotonic() + 15
            while not state_path.exists():
                if time.monotonic() > end:
                    raise TimeoutError('客户端控件状态未返回')
                time.sleep(.1)
            return json.loads(state_path.read_text())

        def click(label=None, width=None):
            state = snapshot()
            widget = next(v for v in state['widgets'] if v['visible'] and v['active']
                          and (v['label'] == label if label is not None else v['width'] == width))
            x, y = round((widget['x'] + widget['width']/2)*state['scale']), round((widget['y'] + widget['height']/2)*state['scale'])
            args = ['xdotool', 'mousemove', '--window', window, str(x), str(y), 'click', '1']
            subprocess.run(args, env=env, check=True)
            record['inputs'].append({'widget': widget, 'physical_x': x, 'physical_y': y, 'argv': args})
            time.sleep(.3)

        def screenshot(name):
            subprocess.run(['xdotool', 'mousemove', '--window', window, '10', '10'], env=env, check=True)
            time.sleep(.5)
            subprocess.run(['import', '-display', display, '-window', window, str(out / name)], env=env, check=True)

        language = json.loads((ROOT / 'src/main/resources/assets/yuanxian/lang/zh_cn.json').read_text())
        click(label=language['screen.yuanxian.commerce.sell'])
        screenshot('all-materials.png')
        click(label='妖兽骨')
        for _ in range(3):
            click(width=102)
        state = snapshot()
        expected_realm = language['world_sim.realm.golden_core']
        assert any(v['visible'] and v['width'] == 102 and v['label'] == expected_realm for v in state['widgets']), state
        screenshot('golden-bones.png')
        click(label='>')
        screenshot('golden-legendary-bone.png')
        record.update(status='passed', state=state, screenshots=['all-materials.png', 'golden-bones.png', 'golden-legendary-bone.png'])
    except BaseException as error:
        record.update(status='failed', error=repr(error), traceback=traceback.format_exc())
        raise
    finally:
        if client is not None:
            record['client_cleanup'] = terminate(client)
        record['xvfb_cleanup'] = terminate(xvfb)
        if log is not None:
            log.close()
        xlog.close()
        write(out / 'report.json', record)
    return record
