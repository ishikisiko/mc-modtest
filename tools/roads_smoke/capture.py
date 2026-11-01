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


def rail_fixtures(route):
    columns={(c['x'],c['z']):c for c in route['cells']}
    choices={}
    for c in route['cells']:
        if c['rail']<0:continue
        for tangent,bits in [((0,1),5),((1,0),10)]:
            if c['rail']&bits!=bits:continue
            if not all((n:=columns.get((c['x']+sign*tangent[0],c['z']+sign*tangent[1]))) and n['rail']>=0 and n['feet2']==c['feet2'] for sign in [-1,1]):continue
            for outward in [(tangent[1],-tangent[0]),(-tangent[1],tangent[0])]:
                start=[c['x']+.5-outward[0]*1.6+tangent[0]*.5,c['feet2']/2-.5+.02,c['z']+.5-outward[1]*1.6+tangent[1]*.5]
                body=columns.get((math.floor(start[0]),math.floor(start[2])))
                if not body or body['band']>1 or body['feet2']!=c['feet2']-1:continue
                choices.setdefault(c['feet2']%2,{'cell':c,'outward':outward,'tangent':tangent,'start':start})
    assert set(choices)=={0,1},'No straight full/half-slab guardrail fixtures'
    return [choices[0],choices[1]]


def guardrail_walks(server,game,user,route):
    walks=[];fixtures=rail_fixtures(route)
    for fixture in fixtures:
        c=fixture['cell'];outward=fixture['outward'];start=fixture['start']
        yaw=math.degrees(math.atan2(-outward[0],outward[1]))
        server.command(f'gamemode adventure {user}')
        server.command(f'execute in minecraft:overworld run tp {user} '+' '.join(map(str,[*start,yaw,0])))
        time.sleep(2)
        pose=numbers(server.command(f'data get entity {user} Pos'))
        assert len(pose)==3 and abs(pose[0]-start[0])<.1 and abs(pose[2]-start[2])<.1,pose
        game.require_ingame('walk into guardrail between posts')
        trajectory=[];game.xdo('keydown','w')
        try:
            for _ in range(10):
                time.sleep(.5)
                trajectory.append({'position':numbers(server.command(f'data get entity {user} Pos')),
                    'dimension':server.command(f'data get entity {user} Dimension')})
        finally:game.xdo('keyup','w')
        assert all(len(t['position'])==3 and '"minecraft:overworld"' in t['dimension'] for t in trajectory),trajectory
        projection=lambda p:(p[0]-c['x']-.5)*outward[0]+(p[2]-c['z']-.5)*outward[1]
        final=trajectory[-1]['position'];stop=projection(final)
        record={**fixture,'trajectory':trajectory,'final_projection_from_rail_center':stop,
            'input':'X11 keydown w for 5 seconds; no jump; autoJump=false',
            'collision_point':'shared beam seam between two stone posts','status':'running'}
        walks.append(record);write(server.out/'guardrail-walks.json',walks)
        assert projection(final)-projection(start)>.25,record
        assert abs(stop+.425)<.10,record
        assert all(t['position'][1]>=start[1]-.1 for t in trajectory),record
        assert max(abs(projection(t['position'])-stop) for t in trajectory[-3:])<.03,record
        record['status']='passed';write(server.out/'guardrail-walks.json',walks)
    return walks,fixtures


def capture(server, route):
    root=Path(__file__).resolve().parents[2]
    display=next(':'+str(n) for n in range(91,120) if not Path(f'/tmp/.X11-unix/X{n}').exists())
    directory=server.out/'client';directory.mkdir(exist_ok=True)
    merge_options(directory/'options.txt',{**CLIENT_OPTIONS,'lang':'zh_cn','renderDistance':'6',
        'simulationDistance':'5','maxFps':'20','autoJump':'false'})
    result={'status':'running','mapped_gate_walks':'unrun','guardrail_side_stop':'unrun','walks':[],'screenshots':[],
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
        result['guardrail_walks'],fixtures=guardrail_walks(server,game,user,route)
        result['guardrail_side_stop']='passed'
        server.command(f'gamemode spectator {user}');game.key('F1')
        columns={(c['x'],c['z']):c for c in route['cells']}
        midpoint=route['centerline'][len(route['centerline'])//2];middle=columns[(midpoint['x'],midpoint['z'])]
        targets=[('from_gate',[groups[0][0],groups[0][1]['feet2']/2,groups[0][2]]),
                 ('road_middle',[middle['x'],middle['feet2']/2,middle['z']]),
                 ('to_gate',[groups[1][0],groups[1][1]['feet2']/2,groups[1][2]])]
        fixture=fixtures[1];c=fixture['cell'];normal=fixture['outward'];tangent=fixture['tangent']
        target=[c['x']+.5,c['feet2']/2+.6,c['z']+.5]
        close_eye=[target[0]-normal[0]*5-tangent[0]*7,target[1]+4,target[2]-normal[1]*5-tangent[1]*7]
        targets.append(('guardrail_close',target))
        for name,target in targets:
            eye=close_eye if name=='guardrail_close' else [target[0]+24,target[1]+18,target[2]+32]
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
