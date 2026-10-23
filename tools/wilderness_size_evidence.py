#!/usr/bin/env python3
"""Scoped size follow-up on the retained Colab acceptance world."""
import argparse,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from tools.wilderness_roster_evidence import RosterEvidence
from tools.alchemy_quality_evidence import read_data

class SizeEvidence(RosterEvidence):
    def run(self):
        self.start(reuse=True);self.rc('tick freeze')
        try:
            for m in self.roster['mobs']:
                if m['kind']=='人形':continue
                target='@e[type=myvillage:'+m['id']+',tag=roster_gallery,limit=1]'
                pos=read_data(self.rc('data get entity '+target+' Pos'));x,y,z=pos;s=m['body_scale'];w=m['dimensions']['width']*s;h=m['dimensions']['height']*s
                for axis,base in [('width',x+w/2),('height',y+h)]:
                    for delta,expected in [(-.015,True),(.015,False)]:
                        px,py=(base+delta,y+.1) if axis=='width' else (x,base+delta)
                        reply=self.rc(f'execute positioned {px} {py} {z} if entity @e[type=myvillage:'+m['id']+',tag=roster_gallery,dx=0,dy=0,dz=0]')
                        self.check(f'saved_{m["id"]}_{axis}_{delta}',('Test passed' in reply)==expected,{'reply':reply,'expected_width':w,'expected_height':h})
        finally:self.rc('tick unfreeze')
        self.done('saved_dimensions');self.setup()
        self.roster['mobs']=[m for m in self.roster['mobs'] if m['kind']!='人形']
        self.rc(f'myvillage cultivation qi fill {self.s.username}')
        self.combat();self.done('combat')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    if (a.out/'evidence.json').exists():raise RuntimeError('Keep prior attempts')
    e=SizeEvidence(a.out,['saved_dimensions','combat'])
    try:e.run();e.report['status']='passed'
    except BaseException as error:e.report['status']='failed';e.report['failures'].append(repr(error));raise
    finally:
        if e.s:e.rc('tick unfreeze');e.rc('tick rate 20');e.rc('save-all flush')
        e.save()
