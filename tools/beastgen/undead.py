"""行尸与骸弓的几何、材质和动作；地域构件由 appearance.json 单独拥有。"""
from __future__ import annotations
import math
import json
from types import SimpleNamespace
from functools import lru_cache
from pathlib import Path
from tools.gen_undead_roster import entry,names
from .builder import Builder
from . import anim, paint

ART = Path(__file__).resolve().parents[2] / 'art/entities/undead_roster'
APPEARANCE = Path(__file__).resolve().parents[1] / 'undead_roster/appearance.json'
MATERIALS = {'skin', 'bone', 'cloth', 'hair', 'eye', 'mouth', 'tooth', 'wood', 'string',
             'metal', 'deposit', 'vine', 'moss', 'fungus', 'blood', 'carving'}


@lru_cache(maxsize=1)
def appearance():
    data = json.loads(APPEARANCE.read_text())
    if data['schema'] != 1 or set(data['variants']) != set(names()):
        raise ValueError('外观配置必须逐个声明全部 18 款亡灵')
    if data['model_scale'] != .5:
        raise ValueError('半格细节模型必须使用 0.5 渲染比例')
    for name, spec in data['variants'].items():
        if not spec['description'] or not spec['details']:
            raise ValueError('缺少独立外观说明或实体细节: ' + name)
        for bone, part, origin, size, material in spec['details']:
            if material not in MATERIALS or len(origin) != 3 or len(size) != 3:
                raise ValueError('未知材质或错误构件: ' + name + '/' + part)
            if any(s <= 0 or s * 2 != round(s * 2) for s in size):
                raise ValueError('构件必须使用正的半格尺寸: ' + name + '/' + part)
    return data


class Anatomy:
    """以原版模型单位写造型；导出时加倍，渲染缩回，允许半单位厚度。"""
    def __init__(self, name, shadow):
        self.b = Builder('myvillage:' + name, look={'bone': 'head', 'max_yaw': 35, 'max_pitch': 25},
                         shadow_radius=shadow)
        self.b.model.scale = appearance()['model_scale']
        self.materials = {}

    def bone(self, name, parent, at, rot=(0, 0, 0)):
        self.b.bone(name, parent, at_local=tuple(v * 2 for v in at), rot=rot)

    def box(self, bone, name, origin, size, material):
        self.b.box_local(bone, name, tuple(v * 2 for v in origin), tuple(v * 2 for v in size))
        self.materials[name] = material


def _skull(a, spec):
    box = lambda n, p, s: a.box('head', n, p, s, 'bone')
    # The sockets are actual openings into a hollow cranium, including their side walls.
    chip = spec.get('crown_chip')
    box('skull_crown', (-3 if chip == 'right' else -4, -8, -3.5), (7 if chip else 8, 1, 7))
    box('skull_back', (-3.5, -7, 2.5), (7, 5, 1))
    box('skull_side_right', (-4, -7, -2.5), (1, 4.5, 6))
    box('skull_side_left', (3, -7, -2.5), (1, 4.5, 6))
    box('skull_brow', (-4, -7, -4), (8, 1, 1.5))
    box('socket_right_outer', (-4, -6, -4), (1, 2.5, 1.5))
    box('socket_left_outer', (3, -6, -4), (1, 2.5, 1.5))
    box('nose_bridge', (-.5, -6, -4), (1, 2, 1))
    box('cheek_right', (-4, -3.5, -4), (2.5, 1, 2))
    box('cheek_left', (1.5, -3.5, -4), (2.5, 1, 2))
    box('maxilla', (-2.5, -2.5, -3.5), (5, .5, 2))
    for i, x in enumerate((-2, -1, 0, 1, 2)):
        a.box('head', 'upper_tooth_' + str(i), (x - .25, -2, -3.5), (.5, .5, 1), 'tooth')
    a.bone('jaw', 'head', (0, -1, 1.5))
    a.box('jaw', 'jaw_right', (-3, -1.5, -3.5), (1, 2, 4), 'bone')
    a.box('jaw', 'jaw_left', (2, -1.5, -3.5), (1, 2, 4), 'bone')
    a.box('jaw', 'jaw_chin', (-3, .5, -5), (6, .5, 3), 'bone')
    for i, x in enumerate((-1.5, -.5, .5, 1.5)):
        a.box('jaw', 'lower_tooth_' + str(i), (x - .25, 0, -5), (.5, .5, 1), 'tooth')


def _ribcage(a, spec):
    a.box('body', 'torso', (-.75, -12, .5), (1.5, 12, 1.5), 'bone')
    a.box('body', 'neck', (-.75, -13, -.5), (1.5, 2, 1.5), 'bone')
    a.box('body', 'clavicle', (-3.75, -12, -1), (7.5, 1, 2), 'bone')
    a.box('body', 'sternum', (-.5, -11.5, -2), (1, 7, 1), 'bone')
    for i, y in enumerate((-10.5, -8, -5.5)):
        for side, sign in [('right', -1), ('left', 1)]:
            broken = spec['rib_break'] == [i, side]
            length = 1.5 if broken else 3
            x = -length - .5 if sign < 0 else .5
            a.box('body', f'rib_{i}_{side}_front', (x, y, -2), (length, 1, 1), 'bone')
            a.box('body', f'rib_{i}_{side}_side', (-3.5 if sign < 0 else 2.5, y, -1), (1, 1, 3), 'bone')
            a.box('body', f'rib_{i}_{side}_back', (-3.5 if sign < 0 else .5, y, 1), (3, 1, 1), 'bone')
    a.box('body', 'pelvis_right', (-3.5, -3, -1.5), (2, 2, 3), 'bone')
    a.box('body', 'pelvis_left', (1.5, -3, -1.5), (2, 2, 3), 'bone')
    a.box('body', 'pelvis_arch', (-2, -1, -1.5), (4, 1, 1), 'bone')
    for side, sign in [('right', -1), ('left', 1)]:
        a.box('body', 'scapula_' + side, (-3 if sign < 0 else 1.5, -11.5, 1.5), (1.5, 2, .5), 'bone')


def _corpse_head(a, spec):
    a.box('head', 'head', (-4, -8, -2.5), (8, 8, 6.5), 'skin')
    a.box('head', 'scalp', (-4, -8.5, -3.5), (8, .5, 7.5), 'hair')
    a.box('head', 'brow', (-4, -8, -4), (8, 2, 1.5), 'skin')
    for side, x in [('right', -4), ('left', 3)]:
        a.box('head', 'temple_' + side, (x, -6, -4), (1, 3, 1.5), 'skin')
        a.box('head', 'eye_' + side, (-3 if side == 'right' else 1, -5.5, -3), (2, 1, .5), 'eye')
        a.box('head', 'cheek_' + side, (-3 if side == 'right' else 1, -4, -4), (2, 1.5, 1.5), 'skin')
    a.box('head', 'nose', (-.5, -5.5, -4.5), (1, 2, 2), 'skin')
    a.box('head', 'lip', (-2.5, -2.5, -4), (5, .5, 1.5), 'skin')
    a.box('head', 'mouth', (-2.5, -2, -3), (5, 1.5, .5), 'mouth')
    # One cheek is recessed instead of adding a painted black wound to a flat face.
    x = -4 if spec['cheek'] == 'right' else 2.5
    a.box('head', 'cheek_wound', (x, -2.5, -3.5), (1.5, 2, 1), 'mouth')
    a.bone('jaw', 'head', (0, -1, 1.5))
    a.box('jaw', 'chin', (-3.5, .5, -5.5), (7, .5, 2), 'skin')
    for i, x in enumerate((-1.5, -.5, .5, 1.5)):
        a.box('head', 'exposed_tooth_' + str(i), (x - .25, -2, -4), (.5, .5, .5), 'tooth')


def _limbs(a, skeleton, spec):
    for side, sign in [('right', -1), ('left', 1)]:
        arm, leg = 'arm_' + side, 'leg_' + side
        a.bone(arm, 'body', (sign * 5, -10, 0))
        a.bone(leg, 'root', (sign * 2, -12, 0))
        if skeleton:
            a.box(arm, arm + '_humerus', (-.75, -2, -.75), (1.5, 5, 1.5), 'bone')
            a.box(arm, arm + '_elbow', (-1, 3, -1), (2, 1, 2), 'bone')
            for i, x in enumerate((-.75, .25)):
                a.box(arm, arm + '_forearm_' + str(i), (x, 4, -.5), (.5, 4.5, 1), 'bone')
            a.box(arm, arm + '_palm', (-1, 8, -1), (2, 1.5, 2), 'bone')
            for i, x in enumerate((-.75, .25)):
                a.box(arm, arm + '_finger_' + str(i), (x, 9.5, -1), (.5, 1, 1.5), 'bone')
            a.box(leg, leg + '_femur', (-.75, 0, -.75), (1.5, 5, 1.5), 'bone')
            a.box(leg, leg + '_knee', (-1, 5, -1), (2, 1, 2), 'bone')
            a.box(leg, leg + '_tibia', (-.75, 6, -.5), (1, 5, 1), 'bone')
            a.box(leg, leg + '_fibula', (.5, 6, -.5), (.5, 5, 1), 'bone')
            a.box(leg, leg + '_foot', (-1, 11, -2), (2, 1, 3), 'bone')
            for i, x in enumerate((-.75, .25)):
                a.box(leg, leg + '_toe_' + str(i), (x, 11.5, -2.5), (.5, .5, 1), 'bone')
        else:
            x = -3 if sign < 0 else -1
            sleeve = spec['sleeve']
            a.box(arm, arm + '_sleeve', (x, -2, -2), (4, sleeve, 4), 'cloth')
            a.box(arm, arm + '_skin', (x + .25, sleeve - 2, -1.75), (3.5, 11 - sleeve, 3.5), 'skin')
            a.box(arm, arm + '_hand', (x, 8.5, -2), (4, 1.5, 3.5), 'skin')
            for i, xx in enumerate((x + .5, x + 2.5)):
                a.box(arm, arm + '_finger_' + str(i), (xx, 10, -2), (1, 1, 2.5), 'skin')
            a.box(leg, leg, (-2, 0, -2), (4, 10, 4), 'cloth')
            a.box(leg, leg + '_shoe', (-2, 10, -3), (4, 2, 5), 'hair')


def _bow(a, angle):
    a.bone('bow', 'arm_left', (0, 9, -1))
    a.box('bow', 'bow_grip', (-.75, -3, -.75), (1.5, 6, 1.5), 'wood')
    for i, sign in enumerate((-1, 1)):
        bn = 'bow_end_' + str(i)
        a.bone(bn, 'bow', (0, sign * 3, 0), (sign * angle, 0, 0))
        a.box(bn, 'bow_limb_' + str(i), (-.5, -7 if sign < 0 else 0, -.5), (1, 7, 1), 'wood')
    length = 3 + 7 * math.cos(math.radians(angle))
    z = 7 * math.sin(math.radians(angle))
    # Rounded to a half-model-unit, the tips meet a real thin bowstring.
    length = round(length * 2) / 2
    a.box('bow', 'bow_string', (-.25, -length, z - .25), (.5, length * 2, .5), 'string')
    a.box('body', 'quiver', (3, -11, 2), (1.5, 6.5, 1.5), 'cloth')
    a.box('body', 'quiver_rim', (2.75, -11.5, 1.75), (2, 1, 2), 'cloth')
    for i, x in enumerate((3.25, 4)):
        a.box('body', 'quiver_arrow_' + str(i), (x, -14, 2.5), (.5, 3, .5), 'wood')
        a.box('body', 'quiver_feather_' + str(i), (x - .25, -14, 2.25), (1, 1, 1), 'deposit')

def build_model(name):
    v=entry(name);sk=v['family']=='archer';spec=appearance()['variants'][name]
    a=Anatomy(name,v['physical']['shadow_radius'])
    a.bone('body','root',(0,-12,0));a.bone('head','body',(0,-12,0))
    if sk:
        _skull(a,spec);_ribcage(a,spec)
    else:
        _corpse_head(a,spec)
        shoulder,waist,depth,hem=spec['body_shape']
        a.box('body','torso',(-waist/2,-12,-depth/2),(waist,12,depth),'cloth')
        # A single connected tunic changes section at the waist; these are solid side volumes.
        # There are no overlapping plates or floating cloth panels.
        for side,sign in [('right',-1),('left',1)]:
            x=-shoulder/2 if sign<0 else waist/2
            a.box('body','shirt_shoulder_'+side,(x,-12,-depth/2),((shoulder-waist)/2,7,depth),'cloth')
            x=-hem/2 if sign<0 else waist/2
            a.box('body','shirt_hem_'+side,(x,-3,-depth/2),((hem-waist)/2,3,depth),'cloth')
        a.box('body','neck',(-2,-13,-1.5),(4,2,3),'skin')
        a.box('body','neckline',(-1.5,-12,-depth/2-.5),(3,1.5,.5),'skin')
        a.box('body','collar_right',(-2.5,-12,-2.5),(1,3,.5),'cloth')
        a.box('body','collar_left',(1.5,-12,-2.5),(1,3,.5),'cloth')
    _limbs(a,sk,spec)
    if sk:_bow(a,spec['bow_angle'])
    if not sk and v['region']=='zhongzhou':
        a.bone('weapon','arm_right',(0,9,0))
        a.box('weapon','sword_grip',(-.5,-1,-.5),(1,4,1),'wood')
        a.box('weapon','sword_blade',(-1,-15,-.5),(2,14,1),'metal')
    for bone,part,origin,size,mat in spec['details']:
        a.box(bone,bone+'__'+part,origin,size,mat)
    a.b.model.materials=a.materials
    return a.b.model

def painter(name, materials):
    v=entry(name);p={k:tuple(bytes.fromhex(x)) for k,x in v['palette'].items()};sk=v['family']=='archer';region=v['region']
    def mix(a,b,weight):return tuple(round(x*(1-weight)+y*weight) for x,y in zip(a,b))
    colours={'skin':p['body'],'bone':p['body'],'cloth':p['cloth'],
             'hair':mix(p['dark'],p['cloth'],.22),'eye':mix(p['dark'],p['accent'],.10),
             'mouth':mix(p['dark'],p['cloth'],.15),'tooth':p['body'] if sk else mix(p['accent'],(202,196,167),.6),
             'wood':mix((105,79,51),p['cloth'],.28),'string':(160,151,125),'metal':(137,142,136),
             'deposit':p['accent'],'vine':mix(p['accent'],(47,66,37),.55),'moss':mix(p['accent'],(57,72,34),.4),
             'fungus':mix(p['accent'],p['cloth'],.55),'blood':p['accent'],'carving':mix(p['accent'],p['body'],.4)}
    tones=material_tones(v['family'])
    def draw(t):
        mat=materials[t.cube];c=colours[mat]
        x,y,z=t.p[0]/2,(t.p[1]-24)/2,t.p[2]/2
        # The same model-space field crosses all cube and UV seams. No atlas-space checkerboard.
        grain=paint.value_noise((x,y,z),2.4,17 if sk else 29)
        shade=(.92 if grain<.34 else 1.04 if grain>.68 else .99)
        shade*=tones[(math.floor(x/2)+math.floor(y/3)*3+math.floor(z/2)*5)%len(tones)]
        if mat in ('skin','bone','cloth'):
            if region=='ximo':
                if y>-5 or grain<.27:c=mix(c,p['accent'],.25)
                if mat=='bone' and abs((y+x*.3)%3-.5)<.22:shade*=.78
            elif region=='donghai':
                if mat=='cloth' and y>-9:shade*=.77
                if (t.n[1]<-.5 or grain>.72):c=mix(c,p['accent'],.35)
            elif region=='nanjiang' and grain<.38:c=mix(c,p['accent'],.48)
            elif region=='beiyuan':
                if t.n[1]<-.5 or grain>.64:c=mix(c,p['accent'],.48)
                if mat in ('bone','skin') and abs((x+math.floor(y/2)*.5)%4-1)<.18:shade*=.76
            elif region=='donghuang' and z>0 and grain>.52:c=mix(c,colours['moss'],.55)
            elif region=='lingyue' and mat=='cloth' and -22<y<-15:
                cloud=(math.floor(x)+math.floor(z)+math.floor(y/2))%9
                if cloud in (0,1):c=mix(c,p['accent'],.45)
            elif region=='moyu':
                if abs((x+math.floor(y/2)*.5)%6-2)<.25:c=mix(c,p['accent'],.56)
                if mat=='bone':shade*=1.15
            elif region=='zhongzhou' and mat=='cloth' and math.floor(y)%3==0 and abs(x+.75)<.35:c=mix(c,p['accent'],.6)
            elif region=='common' and grain<.27:shade*=.86
        if mat=='cloth':
            # Half-texel stitching and broad folds stay subordinate to the physical collar and hem.
            edge=min(t.fu,1-t.fu)
            if edge<.07:shade*=.88
            if t.bone.startswith('leg_'):shade*=.62
            if t.bone=='body' and abs(x)>2.4 and -23<y<-15:shade*=.88
            if t.bone=='body' and -.5<x<0 and -22<y<-14 and z<0:shade*=.82
        if mat=='wood' and t.face in ('NORTH','SOUTH') and .35<t.fu<.7:shade*=1.12
        if mat=='bone':
            inward=(t.cube=='skull_back' and t.face=='NORTH') or (t.cube=='skull_crown' and t.face=='UP')
            inward|=t.cube=='skull_side_right' and t.face=='EAST'
            inward|=t.cube=='skull_side_left' and t.face=='WEST'
            if inward:c=mix(c,p['dark'],.62)
        if mat=='tooth':shade*=1.08
        if mat in ('eye','mouth'):shade=.75
        return tuple(max(0,min(255,round(value*shade))) for value in c)+(255,)
    return draw

def clips(model,server):
    def pose(**angles):return {n:{'rotation':v} for n,v in angles.items()}
    corpse=entry(model.id.split(':')[1])['family']=='corpse'
    # Idle is also sampled during attacks by BeastModel, so it must not add an arm offset.
    rest={}
    breathe={**rest,**pose(head=(2,0,0),jaw=(3,0,0))}
    out=[anim.Clip('idle',2,True,[anim.Key(0,rest),anim.Key(1,breathe),anim.Key(2,rest)])]
    for label,amp in [('walk',25),('run',34)]:
        keys=[]
        for i in range(9):
            a=amp*math.sin(math.pi*i/4)
            raised=-35 if corpse else 0
            walk=pose(leg_left=(a,0,0),leg_right=(-a,0,0),arm_left=(raised-a*.6,0,0),arm_right=(raised+a*.6,0,0))
            # Only the returning foot lifts; the planted foot moves backwards and defines gait rate.
            velocity=math.cos(math.pi*i/4)
            walk['leg_left']['position']=(0,1.5/model.scale*max(0,-velocity),0)
            walk['leg_right']['position']=(0,1.5/model.scale*max(0,velocity),0)
            keys.append(anim.Key(i/8,walk,'linear'))
        out.append(anim.Clip(label,1,True,keys))
    out.append(anim.Clip('stagger',.5,False,[anim.Key(0,{}),anim.Key(.1,pose(body=(-14,0,0),head=(12,0,0))),anim.Key(.5,{})]))
    v=entry(model.id.split(':')[1])
    for a in v['moves']:
        n=a['animation'];end=a['total_ticks'];w=a['windup_ticks'];ticks=a['strike_ticks']
        wind=pose(body=(-8,-12,0),arm_right=(-45,-15,20),arm_left=(-35,15,-20))
        hit=pose(body=(12,15,0),arm_right=(-95,10,-12),arm_left=(-90,-10,12))
        if n=='shot':
            wind=pose(body=(0,-12,0),arm_left=(-90,18,0),arm_right=(-90,-17,0),bow=(90,0,0),head=(0,12,0))
            hit=pose(body=(0,-7,0),arm_left=(-85,13,0),arm_right=(-85,-22,0),bow=(85,0,0),head=(0,7,0))
        elif n=='bow_sweep':wind=pose(body=(0,-35,0),arm_left=(-65,40,0));hit=pose(body=(0,35,0),arm_left=(-75,-40,0))
        elif n in ('pounce','ice_pounce'):wind=pose(body=(22,0,0),arm_right=(-35,0,25),arm_left=(-35,0,-25),leg_left=(-15,0,0),leg_right=(15,0,0));hit=pose(body=(40,0,0),arm_right=(-95,0,0),arm_left=(-95,0,0))
        elif n=='ankle_sweep':wind=pose(body=(65,0,0),head=(-50,0,0),arm_right=(-30,0,0));hit=pose(body=(75,15,0),head=(-55,0,0),arm_right=(-105,20,0))
        elif n=='gu_breath':wind=pose(body=(-15,0,0),head=(-30,0,0));hit=pose(body=(20,0,0),head=(15,0,0))
        keys=[anim.Key(0,{}),anim.Key(max(1,w-7)/20,wind),anim.Key((w-1)/20,wind)]
        for j,t in enumerate(ticks):
            if j:keys.append(anim.Key((t-3)/20,wind))
            k=hit if j%2==0 or n=='shot' else pose(body=(12,-15,0),arm_right=(-35,0,0),arm_left=(-105,-15,0))
            keys.append(anim.Key(t/20,k,'linear'))
        recover=pose(body=(42,0,0),arm_left=(-60,0,0),arm_right=(-60,0,0)) if n in ('pounce','ice_pounce') else {}
        keys.extend([anim.Key((ticks[-1]+5)/20,recover),anim.Key(end/20,{})])
        out.append(anim.Clip(n,end/20,False,keys))
    return out

def definition(name):
    model=build_model(name)
    return SimpleNamespace(build_model=lambda:model,paint=painter(name,model.materials),glow=lambda t:None,
                          clips=clips,extra_outputs=extra_outputs,
                          SKIP_CREASE=tuple(c.name for _,c in model.cubes()))


@lru_cache(maxsize=2)
def material_tones(family):
    """复用已采用图稿的有限明暗，不把旧 atlas 的坐标套在新几何上。"""
    from tools.gen_qingfeng_sword_model import decode_png_rgba
    path=ART/family/'material_tones.png'
    if not path.exists():raise ValueError('missing authored undead material: '+str(path))
    _,_,raw=decode_png_rgba(path.read_bytes())
    values=sorted(sum(raw[i:i+3])/765 for i in range(0,len(raw),4) if raw[i+3])
    return tuple(.95+.08*values[round((len(values)-1)*q)] for q in (.15,.35,.55,.75))

def extra_outputs(built):
    if built.name not in ('wandering_corpse','bone_archer'):return {}
    import json
    from tools.gen_qingfeng_sword_model import encode_png
    family=entry(built.name)['family'];path=ART/family
    w,h=built.model.texture_size;rows=[[(0,0,0,255)]*w for _ in range(h)]
    for t in built.texels:rows[t.v][t.u]=(255,255,255,255)
    return {path/'uv_truth.json':(json.dumps(built.model_doc,ensure_ascii=False,indent=2)+'\n').encode(),
            path/'model_uv_islands_mask.png':encode_png(rows)}
