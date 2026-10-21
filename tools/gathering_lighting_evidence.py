#!/usr/bin/env python3
"""Single-client, same-camera day/night material evidence in the cloud preview world."""
from pathlib import Path
import argparse, sys, time
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from tools.gathering_architecture_evidence import ArchitectureEvidence


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    e=ArchitectureEvidence(a.out)
    e.report.update(selected=['material_day_night'],completed=[],unrun=['material_day_night'],
                    method='Single real client; RCON fixed camera/time in independent preview world',shader_pack='none')
    try:
        e.start(reuse=True);e.close_menu();e.key('x')
        for c in ('gamerule doDaylightCycle false','weather clear',f'effect clear {e.s.username}',f'gamemode spectator {e.s.username}'):e.rc(c)
        # Re-place the four fixture lamps so both builds recompute saved block lighting.
        for part in e.parts['high']:
            if part['block']!='myvillage:purple_gathering_lamp':continue
            dx,dy,dz=part['offset'];where=f'{18+dx} {-61+dy} {3+dz}'
            e.rc(f'setblock {where} minecraft:air')
            e.rc(f'setblock {where} '+part['block']+'[facing='+part['facing']+']')
        e.report['fixture_lamps_replaced_for_light_recalculation']=True
        e.key('F1')
        views=[('medium',(6.4,-59.3,0),(4.5,-59.45,3.5)),('crystal',(25.4,-59.3,0),(23.5,-59.45,3.5)),
               ('lamp',(25,-59.5,-5),(22,-58.7,-1)),('high',(28.5,-52,-9),(18.5,-59.9,3.5))]
        for period,tick in [('night',18000),('day',6000)]:
            e.rc(f'time set {tick}');time.sleep(2)
            for name,position,target in views:
                e.camera(e.s,position,target);e.capture_game(e.s.game,name+'-'+period+'.png')
        e.key('F1');e.rc(f'gamemode survival {e.s.username}');e.rc(f'tp {e.s.username} -7 -60 -3 0 15')
        e.check('both_arrays_still_formed',all(e.inspect(core)['formed'] for core in [(0,-61,3),(18,-61,3)]),'unchanged structures')
        e.complete('material_day_night');e.rc('save-all flush');e.stop();e.report['status']='pass'
    except BaseException as err:e.report['status']='fail';e.report['failures'].append(repr(err));raise
    finally:
        if e.s:e.stop()
        e.save()
if __name__=='__main__':main()
