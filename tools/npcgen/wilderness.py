"""Human wilderness silhouettes, owned by npcgen; the caster adapts to the beast spell timeline."""
from ..beastgen.builder import Builder
from ..beastgen import anim
from ..beastgen.roster import entry, clips as creature_clips
import math

def build_model(name):
    caster=name=='ash_talismanist';m=entry(name)
    b=Builder('myvillage:'+name,look={'bone':'head','max_yaw':45.,'max_pitch':30.},shadow_radius=.38)
    b.bone('body','root',at=(0,-12,0));b.box_local('body','robe_torso',(-4,-11,-2.2),(8,11,5))
    b.box_local('body','belt',(-4.5,-1,-2.7),(9,2,6));b.box_local('body','buckle',(-1,0,-3.3),(2,2,1))
    for side,s in [('right',-1),('left',1)]:
        b.bone('leg_'+side,'body',at_local=(s*2,0,0));b.box_local('leg_'+side,'trouser_'+side,(-1.8,0,-1.8),(4,10,4))
        b.box_local('leg_'+side,'boot_'+side,(-2,9,-2.5),(4,3,5))
        b.box_local('leg_'+side,'wrap_'+side,(-2.1,6,-2),(4,3,4))
        b.box_local('leg_'+side,'skirt_'+side,(-1.95,0,-2.6),(4,10 if caster else 5,6))
        b.bone('arm_'+side,'body',at_local=(s*5.3,-10,0),rot=(0,0,s*-4))
        b.box_local('arm_'+side,'sleeve_'+side,(-2,-1,-2),(4,6,4))
        b.bone('forearm_'+side,'arm_'+side,at_local=(0,5,0),rot=(-8,0,0))
        b.box_local('forearm_'+side,'cuff_'+side,(-2,-.5,-2),(4,5,5 if caster else 4))
        b.box_local('forearm_'+side,'hand_'+side,(-1.4,4,-1.6),(3,3,3))
        if caster:b.box_local('forearm_'+side,'sleeve_drape_'+side,(-1.6,3,1.1),(3,5,2))
        b.bone('lapel_'+side,'body',at_local=(s*2.4,-10.5,-2.8),rot=(0,0,s*24))
        b.box_local('lapel_'+side,'trim_'+side,(-.7,0,-.2),(2,10,1))
    b.bone('head','body',at_local=(0,-11,0));b.box_local('head','skin_head',(-3.5,-8,-3.5),(7,8,7))
    b.box_local('head','hair_cap',(-4,-9,-4),(8,2,8));b.box_local('head','hair_back_head',(-4,-7,2.7),(8,7,2))
    for s in [-1,1]:
        b.box_local('head','hair_side_'+str(s).replace('-','n'),(s*3.4-.6,-7,-3.8),(2,6,2))
        b.box_local('head','hair_fringe_'+str(s).replace('-','n'),(s*1.8-1,-7.7,-3.8),(2,3,1))
    b.bone('hair_back','body',at_local=(0,-10.5,3));b.box_local('hair_back','hair_lock',(-1,0,0),(2,7,1))
    b.bone('sash','body',at_local=(0,0,-3.3));b.box_local('sash','sash_long',(-1.8,0,0),(2,7,1));b.box_local('sash','sash_short',(1,0,0),(1,5,1))
    if caster:
        for n,lo,size in [('top',(-4.5,-10,-4.5),(9,2,9)),('back',(-4.5,-8,3),(9,9,2)),('left',(3.5,-8,-4),(2,9,8)),('right',(-5.5,-8,-4),(2,9,8))]:b.box_local('head','hood_'+n,lo,size)
        b.bone('talisman','forearm_right',at_local=(0,5,-2),rot=(0,0,0));b.box_local('talisman','talisman_paper',(-1.4,-7,-.2),(3,8,1))
        for i in range(2):b.box_local('body','talisman_belt_'+str(i),(-3+i*5,1,-3.2),(2,5,1))
        b.box_local('body','jade', (2.4,-1,-3.6),(2,3,1))
    else:
        b.box_local('head','mask',(-3.6,-3.7,-3.8),(7,4,1));b.box_local('head','bun',(-1.5,-12,0),(3,3,3))
        b.bone('cape','arm_left',at_local=(0,-1,0),rot=(0,0,-12))
        for i in range(4):b.box_local('cape','straw_'+str(i),(-2+i*.4,-1+i*.9,-3),(5,2,7-i))
    return b.model

def painter(name):
    caster=name=='ash_talismanist';pal=[tuple(bytes.fromhex(c)) for c in entry(name)['palette']]
    def draw(t):
        n=t.cube;c=pal[0]
        if n.startswith(('trim','wrap','hood','cuff')):c=(165,160,141) if not caster else pal[0]
        if caster and n.startswith(('robe','skirt')):c=(70,69,66)
        if n.startswith(('belt','sash','talisman_belt')):c=pal[2] if caster else pal[1]
        if n.startswith('straw'):c=pal[2]
        if n.startswith(('hair','boot','trouser')):c=(30,34,40)
        if n.startswith(('skin','hand')):c=(231,193,158)
        if n=='skin_head' and t.face=='NORTH':
            x,y=t.local[0],t.local[1]
            if -4.7<y<-3.7 and 1<abs(x)<3:return (49,36,28,255)
            if -4.7<y<-3.7 and 2.6<abs(x)<3.5:return (240,226,207,255)
            if -2<y<-1.3 and abs(x)<.6:return (150,90,75,255)
        if n=='talisman_paper':
            c=(225,190,110)
            if t.face in ('NORTH','SOUTH') and ((int(t.fv*8)%3==0 and .2<t.fu<.8) or .4<t.fu<.6):c=(130,58,33)
        if n=='jade':c=(67,137,113)
        if n=='buckle':c=(168,142,77)
        if n=='mask':c=(40,52,67)
        u,v=int(t.fu*16),int(t.fv*16)
        shade=.88+.12*max(0,-t.n[1])+.04*((u//3+v//5)%3-1)
        if n.startswith(('skirt','robe','sleeve')):
            if u%5==0:shade-=.12
            if v>=14:shade+=.1
        return tuple(max(0,min(255,round(x*shade))) for x in c)+(255,)
    return draw

def clips(model):
    keys=[]
    for k in range(9):
        angle=math.sin(2*math.pi*k/8)*20
        keys.append(anim.Key(k/8,{'leg_right':{'rotation':(angle,0,0)},'leg_left':{'rotation':(-angle,0,0)},'arm_right':{'rotation':(-angle*.7,0,0)},'arm_left':{'rotation':(angle*.7,0,0)},'sash':{'rotation':(angle*.15,0,0)}},'linear'))
    return [anim.Clip('idle',3,True,[anim.Key(0,{}),anim.Key(1.5,{'head':{'rotation':(0,2,0)}}),anim.Key(3,{})]),anim.Clip('walk',1,True,keys)]
