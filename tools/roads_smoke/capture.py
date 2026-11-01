"""原生客户端短距离映射步行与观察；不声称全程步行、玩家外观或性能验收。"""
from __future__ import annotations

import json
import math
import os
from pathlib import Path
import re
import subprocess
import time

from combat_capture.session import CLIENT_OPTIONS, merge_options
from combat_capture.xgame import Game
from roadweaver_probe.run import terminate, write


def numbers(response):
    return [float(n) for n in re.findall(r'[-+]?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?',response)]


def capture(server, route):
    root=Path(__file__).resolve().parents[2]
    display=next(':'+str(n) for n in range(91,120) if not Path(f'/tmp/.X11-unix/X{n}').exists())
    directory=server.out/'client';directory.mkdir(exist_ok=True)
    merge_options(directory/'options.txt',{**CLIENT_OPTIONS,'lang':'zh_cn','renderDistance':'6',
        'simulationDistance':'5','maxFps':'20','autoJump':'false'})
    result={'status':'running','mapped_gate_walks':'unrun','walks':[],'screenshots':[],
        'method':'native client, X11 mapped forward input, software OpenGL',
        'full_route_player_walk':'not_verified','owner_appearance':'not_verified','fps':'not_verified'}
    client=xvfb=None
    xlog=(server.out/'xvfb.log').open('w');clog=(server.out/'client-launch.log').open('w')
    try:
        xvfb=subprocess.Popen(['Xvfb',display,'-screen','0','960x540x24','+extension','GLX','+render','-noreset'],stdout=xlog,stderr=subprocess.STDOUT,start_new_session=True)
        deadline=time.monotonic()+20
        while not Path('/tmp/.X11-unix/X'+display[1:]).exists():
            if xvfb.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Xvfb not ready')
            time.sleep(.2)
        user='RoadAdapterView'
        args=['./gradlew','runClient','-x','processResources','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx4g',
            f'-Pcombat_smoke_game_dir={directory}',f'-Pcombat_smoke_username={user}',f'-Pcombat_smoke_server=127.0.0.1:{server.sport}']
        env=dict(os.environ,DISPLAY=display,LIBGL_ALWAYS_SOFTWARE='1',LP_NUM_THREADS='2')
        client=subprocess.Popen(args,cwd=root,env=env,stdout=clog,stderr=subprocess.STDOUT,start_new_session=True)
        deadline=time.monotonic()+600
        while user not in server.command('list'):
            if client.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Native client did not join')
            time.sleep(3)
        game=Game(display,directory/'logs/latest.log')
        if not(game.wait_ingame(60,poll=1) or game.recover_grab()):raise RuntimeError('Client joined without in-game view')
        # Let first-login transitions settle, then verify the actual overworld pose.
        time.sleep(8)
        existing=[c for c in route['cells'] if c['existing']]
        groups=[]
        for center in [route['centerline'][0]['x'],route['centerline'][-1]['x']]:
            cells=[c for c in existing if c['x']==center];first=min(cells,key=lambda c:c['z'])
            groups.append((center,first,max(c['z'] for c in cells)+1))
        # Source order does not determine which city lies west. Name by real gate x.
        for name,(x,first,edge) in zip(['from_gate','to_gate'],groups):
            start=[x+.5,first['feet2']/2+.02,first['z']+.5]
            for attempt in range(3):
                server.command(f'gamemode adventure {user}')
                server.command(f'execute in minecraft:overworld run tp {user} '+' '.join(map(str,[*start,0,0])))
                time.sleep(2)
                dim=server.command(f'data get entity {user} Dimension');pose=numbers(server.command(f'data get entity {user} Pos'))
                if '"minecraft:overworld"' in dim and len(pose)==3 and abs(pose[0]-start[0])<.1 and abs(pose[2]-start[2])<.1:break
            else:raise RuntimeError('Mapped walk fixture pose not applied')
            game.require_ingame('mapped forward walk')
            trajectory=[];deadline=time.monotonic()+45
            game.xdo('keydown','w')
            try:
                while time.monotonic()<deadline:
                    time.sleep(1)
                    pose=numbers(server.command(f'data get entity {user} Pos'))
                    rotation=server.command(f'data get entity {user} Rotation')
                    trajectory.append({'position':pose,'rotation':rotation,'dimension':server.command(f'data get entity {user} Dimension')})
                    if len(pose)==3 and pose[2]>=edge+8:break
                else:raise RuntimeError('Mapped gate walk did not exit the city: '+str(trajectory[-3:]))
            finally:game.xdo('keyup','w')
            assert all(len(t['position'])==3 and abs(t['position'][0]-start[0])<1 and '"minecraft:overworld"' in t['dimension'] for t in trajectory)
            result['walks'].append({'gate':name,'start':start,'city_edge_z':edge,'trajectory':trajectory,
                'input':'X11 keydown w; keyup w; no jump input; autoJump=false','status':'passed'})
        result['mapped_gate_walks']='passed'
        server.command(f'gamemode spectator {user}');game.key('F1')
        columns={(c['x'],c['z']):c for c in route['cells']}
        midpoint=route['centerline'][len(route['centerline'])//2];middle=columns[(midpoint['x'],midpoint['z'])]
        targets=[('from_gate',[groups[0][0],groups[0][1]['feet2']/2,groups[0][2]]),
                 ('road_middle',[middle['x'],middle['feet2']/2,middle['z']]),
                 ('to_gate',[groups[1][0],groups[1][1]['feet2']/2,groups[1][2]])]
        for name,target in targets:
            eye=[target[0]+24,target[1]+18,target[2]+32]
            dx,dy,dz=[target[i]-eye[i] for i in range(3)]
            yaw=math.degrees(math.atan2(-dx,dz));pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz)))
            server.command(f'execute in minecraft:overworld run tp {user} '+' '.join(f'{v:.3f}' for v in [*eye,yaw,pitch]))
            time.sleep(12)
            pose=numbers(server.command(f'data get entity {user} Pos'));dimension=server.command(f'data get entity {user} Dimension')
            assert len(pose)==3 and all(abs(a-b)<.05 for a,b in zip(pose,eye)) and '"minecraft:overworld"' in dimension
            stability=game.shot(server.out/(name+'.png'),max_wait=12,settle=1)
            result['screenshots'].append({'file':name+'.png','eye':eye,'target':target,'pose':pose,'dimension':dimension,'stability':stability,'status':'captured_for_review'})
        result['status']='captured_for_review'
    except BaseException as e:
        result['status']='failed';result['error']=repr(e)
    finally:
        if client:result['client_cleanup']=terminate(client)
        if xvfb:result['display_cleanup']=terminate(xvfb)
        xlog.close();clog.close();write(server.out/'capture.json',result)
    return result
