"""Small Colab smoke: automatic entry, one genuine NPC acceptance, marker screenshot and journal travel.

No full teaching/planting/reward route, continuous walk or owner visual verdict.
RCON only prepares one interaction position and reads state. GUI actions use real mapped input.
"""
from pathlib import Path
import argparse,json,os,re,secrets,time,traceback
from .realm_evidence import Client,Evidence,wait,ROOT,support,read_data
from tools.regiongen.bay_native import archive_world

CASES=['automatic_private_entry','opening_and_marker','journal_roundtrip']
class Smoke(Evidence):
    def __init__(self,out,request):
        super().__init__(out,request)
        self.report.update(kind='qianzhen_polish_smoke',selected=CASES,completed=[],unrun=CASES.copy(),method=__doc__,full_quest='not_selected',multiplayer='not_selected')
        self.save()
    def complete(self,name):
        self.report['completed'].append(name);self.report['unrun']=[x for x in CASES if x not in self.report['completed']];self.save()
    def journal(self,client,action):
        client.close_screen();client.guard();offset=client.game.client_log.stat().st_size
        client.game.xdo('key','o');self.action('key',player=client.name,key='o')
        pattern=re.compile(r'QIANZHEN_JOURNAL action='+action+r' x=(\d+) y=(\d+) w=(\d+) h=(\d+)')
        rows=wait(lambda:pattern.findall(client.tail(offset)),bool,15,'journal '+action)
        x,y,w,h=map(int,rows[-1]);client.guard(menu=True)
        client.game.xdo('mousemove','--sync',str(x+w//2),str(y+h//2));client.guard(menu=True);client.game.xdo('click','1')
        self.action('journal_click',player=client.name,action=action,rectangle=[x,y,w,h]);time.sleep(1)

def run(out,request):
    if os.environ.get('MYVILLAGE_REMOTE_PROVIDER')!='colab' or not str(ROOT).startswith('/content/'):
        raise RuntimeError('Colab only; no local game fallback')
    e=Smoke(out,request);support.OUT=out;name='qianzhen_polish_'+secrets.token_hex(5);world=support.GAME/name
    try:
        with support.Server(name,'smoke',planned=False) as server:
            e.server=server
            with Client(e,server,'PolishPlayer','smoke') as client:
                realm=wait(lambda:e.realm(client.name),lambda s:s['phase']=='COMPLETE' and s['current_dimension']==s['dimension'],120,'automatic entry')
                e.check('automatic_entry_without_command',realm['authorized'] and realm['camera_self'],realm)
                state=e.story(client.name)
                e.check('fresh_v2_unaccepted',state['active_definition_version']==2 and not state['accepted'] and not state['completed'],state)
                e.complete('automatic_private_entry');client.capture('automatic-entry-marker')
                # One explicit NPC interaction fixture, never a walking claim.
                role='pharmacist';station=e.scene['positions'][role];at=e.point(station['approach']);initial=e.point(station['pos'])
                e.fixture(client,at,[initial[0]+.5,initial[1]+1.3,initial[2]+.5])
                person=state['npc'][role]['personId'];dim=realm['dimension']
                query=f'execute in {dim} as @e[type=myvillage:cultivator,nbt={{WorldSimPerson:{person}}}] run data get entity @s Pos'
                bodies=wait(lambda:[read_data(line) for line in e.rc(query).splitlines() if 'data: ' in line],lambda v:len(v)==1,20,'pharmacist body')
                e.fixture(client,at,[bodies[0][0],bodies[0][1]+1.3,bodies[0][2]])
                e.rc('gamemode survival '+client.name)
                offset=client.game.client_log.stat().st_size;client.right();offset=client.choose('learn',offset)
                client.capture('apprentice-terms');client.choose('accept',offset);client.close_screen()
                after=wait(lambda:e.story(client.name),lambda s:s['accepted'],15,'genuine apprentice acceptance')
                e.check('opening_accepted_with_real_input',after['definition_version']==2 and not after['completed'] and after['opening_facilities']['ready'],after)
                text=wait(lambda:client.game.client_log.read_text(errors='replace'),lambda s:'title=药庐学徒' in s and 'targetLabel=测灵碑' in s,15,'marker snapshot')
                client.capture('accepted-target-marker');e.complete('opening_and_marker')
                e.journal(client,'leave')
                left=wait(lambda:e.realm(client.name),lambda s:s['current_dimension']!=s['dimension'],30,'journal leave')
                e.check('return_to_saved_outside',left['current_dimension']==realm['outside']['dimension'],left)
                e.journal(client,'enter')
                resumed=wait(lambda:e.realm(client.name),lambda s:s['current_dimension']==s['dimension'],30,'journal resume')
                resumed_story=e.story(client.name)
                e.check('resume_same_progress_and_people',resumed_story['definition_version']==2 and resumed_story['stage']==after['stage'] and resumed_story['receipts']==after['receipts'] and {k:v['personId'] for k,v in resumed_story['npc'].items()}=={k:v['personId'] for k,v in after['npc'].items()},resumed_story)
                client.capture('journal-resumed');e.complete('journal_roundtrip')
        cleanup=json.loads((out/'smoke-cleanup.json').read_text());e.check('normal_server_shutdown',cleanup['terminated'] and cleanup['returncode']==0 and not cleanup['forced_stop'],cleanup)
        e.report['status']='passed'
    except BaseException as error:
        e.report.update(status='failed',error=repr(error));traceback.print_exc()
    finally:
        if world.exists():
            dest=out/('saved-world.zip' if e.report['status']=='passed' else 'failed-world.zip')
            archive_world(world,dest);e.report['saved_world']=str(dest)
        e.save()
    if e.report['status']!='passed':raise RuntimeError(e.report.get('error'))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--request',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    run(a.out,json.loads(a.request.read_text()))
