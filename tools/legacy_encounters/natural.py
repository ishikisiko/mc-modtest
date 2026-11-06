"""在独立普通新世界生成两座遗迹，保存后读取真实 StructureStart 和石碑 NBT。"""
from __future__ import annotations
import argparse,json,os,re,secrets,subprocess,sys,time,traceback
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from tools.waystones_smoke.run import Server,write,stop
from tools.combat_capture.rcon import RconError
from yunshan.natural_terrain import read_region,decode_section,section_state

class NaturalServer(Server):
    def __init__(self,out,seed):
        super().__init__(out,False);self.seed=seed
    def __enter__(self):
        self.game.mkdir(parents=True,exist_ok=False)
        props={'server-ip':'127.0.0.1','server-port':self.sport,'online-mode':'false','enable-rcon':'true',
               'rcon.port':self.rport,'rcon.password':self.password,'level-name':'world','level-seed':self.seed,
               'level-type':'minecraft\\:normal','generate-structures':'true','difficulty':'normal',
               'spawn-protection':0,'view-distance':2,'simulation-distance':2,'max-tick-time':180000,
               'spawn-monsters':'false','spawn-animals':'false','spawn-npcs':'false','sync-chunk-writes':'true'}
        (self.game/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in props.items())+'\n')
        (self.game/'eula.txt').write_text('eula=true\n')
        self.log=(self.out/'server.log').open('w')
        args=['./gradlew','-I','tools/legacy_encounters/natural.init.gradle','runAcceptanceServer','-x','processResources',
              '--no-daemon','--console=plain','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx2g']
        self.process=subprocess.Popen(args,cwd=ROOT,env=dict(os.environ,MYVILLAGE_LEGACY_WORLD=str(self.game)),
            stdout=self.log,stderr=subprocess.STDOUT,start_new_session=True)
        try:
            deadline=time.monotonic()+600
            while time.monotonic()<deadline:
                if self.process.poll() is not None:raise RuntimeError('Natural server exited')
                try:
                    if 'players online' in self.command('list'):return self
                except (OSError,RconError):pass
                time.sleep(2)
            raise TimeoutError('Natural server startup')
        except BaseException:
            self.__exit__(None,None,None);raise

def saved_chunks(world,x,z):
    result=[];regions={}
    for cx in range(x//16-3,x//16+4):
        for cz in range(z//16-3,z//16+4):
            key=(cx//32,cz//32)
            if key not in regions:
                p=world/'region'/f'r.{key[0]}.{key[1]}.mca'
                regions[key]=read_region(p)[0] if p.exists() else {}
            chunk=regions[key].get((cx%32,cz%32))
            if chunk:result.append(chunk)
    return result

def audit(world,id,x,z):
    chunks=saved_chunks(world,x,z)
    starts=[c.get('structures',{}).get('starts',{}).get(id) for c in chunks]
    start=next((s for s in starts if s and s.get('id')==id),None)
    if not start or not start.get('Children'):raise AssertionError('Missing naturally generated StructureStart: '+id)
    boxes=[p['BB'] for p in start['Children']]
    steles=[]
    for c in chunks:
        if str(c.get('Status','')).split(':')[-1]!='full':continue
        for b in c.get('block_entities',[]):
            if b.get('id')!='myvillage:legacy_stele':continue
            if not any(bb[0]<=b['x']<=bb[3] and bb[1]<=b['y']<=bb[4] and bb[2]<=b['z']<=bb[5] for bb in boxes):continue
            if not b.get('Ready') or b.get('Cleared') or b.get('Claimed'):raise AssertionError(('Fresh stele state',b))
            section=next((s for s in c.get('sections',[]) if s['Y']==b['y']//16),{})
            block=section_state(decode_section(section),b['x']%16,b['y']%16,b['z']%16)
            expected='myvillage:sword_inscription' if 'sword' in id else 'myvillage:mountain_stele'
            if block!=expected:raise AssertionError((block,expected))
            steles.append({'position':[b['x'],b['y'],b['z']],'block':block,'nbt':b})
    if len(steles)!=1:raise AssertionError(('Expected one usable natural stele',steles))
    return {'structure':id,'start':start,'steles':steles,'full_chunks':sum(str(c.get('Status','')).endswith('full') for c in chunks)}

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--seed',type=int,default=20261106);a=p.parse_args()
    if os.environ.get('MYVILLAGE_CLOUD_PROVIDER')!='Colab' or not str(ROOT).startswith('/content/'):raise RuntimeError('Colab only')
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
    selected=['legacy_sword_ruin','legacy_mountain_ruin']
    report={'kind':'legacy_natural_world','seed':a.seed,'selected':selected,'completed':[],'unrun':selected[:],'failures':[],
            'method':__doc__,'source_commit':json.loads((ROOT/'source-manifest.json').read_text())['commit'],'structures':[]}
    try:
        with NaturalServer(out,a.seed) as server:
            for name in selected:
                id='myvillage:'+name
                result=server.command('execute positioned 14000 90 14000 run locate structure '+id)
                coords=re.search(r'\[(-?\d+),\s*(?:~|-?\d+),\s*(-?\d+)\]',result)
                if not coords:raise AssertionError(result)
                x,z=map(int,coords.groups());server.command(f'forceload add {x-32} {z-32} {x+32} {z+32}')
                deadline=time.monotonic()+180
                while time.monotonic()<deadline:
                    if 'The time is' in server.command(f'execute if loaded {x} 80 {z} run time query gametime'):break
                    time.sleep(2)
                else:raise TimeoutError('Natural structure chunks')
                server.command('save-all flush')
                result=audit(server.game/'world',id,x,z)
                report['structures'].append(result);report['completed'].append(name);report['unrun'].remove(name)
                write(out/'evidence.json',report);print('PASS',name,result['steles'][0]['position'],flush=True)
                server.command('forceload remove all')
    except BaseException as error:report['failures'].append(repr(error));traceback.print_exc()
    finally:
        report['passed']=not report['failures'] and not report['unrun'];write(out/'evidence.json',report)
    return 0 if report['passed'] else 1

if __name__=='__main__':raise SystemExit(main())
