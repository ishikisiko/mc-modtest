"""男修脸型、发型与衣装的正式组合；所有输入由 male_variants.json 管理。"""
from __future__ import annotations
import copy
from . import male_catalog,male_clothing as clothing
from .defs import cultivator as legacy,cultivator_f_novice as female
from .humanoid import _box,_rgb,_tone,_mix,_xhz
from ..beastgen.builder import Builder
CONFIG=male_catalog.load()
HEAD_SKIN=legacy.SKIN

def compose_head(face_id,hair_id):
    face=copy.deepcopy(CONFIG['faces'][face_id]);hair=CONFIG['hair'][hair_id]
    w,h=face['width'],face['height']
    face['hair']=hair['hair']
    face['fringe']=[round(hair['fringe'][min(hair['width']-1,int((x+.5)*hair['width']/w))]*h/hair['height']) for x in range(w)]
    pixels=[list(row) for row in face['pixels']]
    for x in range(w):
        first=next((y for y in range(h) if pixels[y][x] in 'LWCDGM'),h)
        face['fringe'][x]=min(face['fringe'][x],first)
    for y in range(h):
        for x in range(w):
            if y<face['fringe'][x] or (pixels[y][x]=='.' and x in (0,w-1) and y<h-1):pixels[y][x]='H'
    face['pixels']=[''.join(row) for row in pixels]
    return face

def head_bones(model):
    names={'head','hair_back'}
    for b in model.bones:
        if b.parent in names:names.add(b.name)
    return names


def definition(name, *, motion_cover=True, reference_trousers=False):
    spec=next(v for v in CONFIG['variants'] if 'cultivator_'+v['look']==name)
    head=compose_head(spec['face'],spec['hair'])
    outfit=spec['outfit']
    d=clothing.definition(outfit, motion_cover=motion_cover, reference_trousers=reference_trousers)
    original_model,original_painter=d.build_model,d.painter
    d.HAIR=tuple(_rgb(c) for c in CONFIG['palettes']['HAIR'])
    d.IRIS_TOP,d.IRIS,d.IRIS_LOW='#36526B','#789CAE','#BED2DB'
    pieces={}
    reference={}
    w,h,depth=head['width'],head['height'],head['depth']
    top=48+h

    def model():
        result=original_model()
        reference['model']=copy.deepcopy(result)
        for bone in result.bones:
            if bone.name in head_bones(result):bone.cubes.clear()
        b=Builder(d.ID);b.model=result
        def absolute(name,x,y,z,sx,sy,sz,material='hair'):
            _box(b,'head','headstudy_'+name,x,y,z,sx,sy,sz);pieces['headstudy_'+name]=material
        def local(bone,name,origin,size,material='lock'):
            b.box_local(bone,'headstudy_'+name,origin,size);pieces['headstudy_'+name]=material
        absolute('skull',-w/2,48,-depth/2,w,h,depth,'face')
        absolute('cap',-(w+1)/2,top,-(depth+1)/2,w+1,1,depth+1)
        absolute('back',-w/2,48,depth/2-.5,w,h,1)
        absolute('side_r',-w/2-.5,48,-depth/2,1,h,depth,'side')
        absolute('side_l',w/2-.5,48,-depth/2,1,h,depth,'side')
        start=0
        while start<w:
            length=head['fringe'][start]
            end=start+1
            while end<w and head['fringe'][end]==length:end+=1
            absolute('fringe_'+str(start),start-w/2,top-length,-depth/2-.6,end-start,length,1,'fringe')
            start=end
        style=head['hair']
        if style=='crown':
            absolute('bun',-2,top+1,-1,4,3,4,'lock')
            absolute('crown',-3,top+1,-1.5,6,1,5,'silver')
            absolute('crown_jade',-.5,top+1.2,-2,1,1,1,'jade')
            absolute('pin',-4.5,top+2,1,9,1,1,'gold')
            b.bone('headstudy_backfall','head',at=(0,-53,7))
            local('headstudy_backfall','backfall',(-3,0,0),(6,15,2))
            local('headstudy_backfall','backfall_tip',(-2,14,.2),(4,4,2))
        elif style in ('side_tail','high_tail'):
            high=style=='high_tail'
            b.bone('headstudy_tail','head',at=(0,-(top-2 if high else 52),7),rot=(8 if high else 3,0,0))
            local('headstudy_tail','tail_root',(-2,-1,-1),(4,4,4))
            local('headstudy_tail','tail_band',(-2.5,0,-1.5),(5,2,5),'band')
            local('headstudy_tail','tail_main',(-2.5,2,1),(5,13,3))
            local('headstudy_tail','tail_tip',(-1.5,14,1.2),(3,6,2))
            absolute('side_wisp_r',-w/2-.3,45 if high else 46,-depth/2-.5,1,7,2)
            absolute('side_wisp_l',w/2-.7,46,-depth/2-.5,1,6,2)
        else:
            b.bone('headstudy_loose','head',at=(0,-54,7.2),rot=(3,0,0))
            length=20 if style=='half' else 23
            local('headstudy_loose','loose_mid',(-3,0,0),(6,length,2))
            local('headstudy_loose','loose_r',(-5,0,0),(2,length-3,2))
            local('headstudy_loose','loose_l',(3,0,0),(2,length-2,2))
            local('headstudy_loose','loose_band',(-3.5,1,1.5),(7,1,1),'band')
            local('headstudy_loose','loose_clasp',(-1,1,2.2),(2,1,1),'silver')
            absolute('side_wisp_r',-w/2-.4,44 if style=='half' else 42,-depth/2-.5,1,10,2)
            absolute('side_wisp_l',w/2-.6,45 if style=='half' else 43,-depth/2-.5,1,9,2)
        return result

    class Paint:
        def __init__(self,model):
            # 衣装的旧明暗也保留，用原模型计算衣装阴影，不让新发束改写已接受的衣服。
            self.body_painter=original_painter(reference['model'])
        def head_tone(self,x,y,z,face):
            return female._hair_head_tone(x*14/w,60-(top-y)*14/h,z*14/depth,face)
        def new_face(self,t):
            x,y,z=_xhz(t)
            if t.face!='NORTH':return _tone(HEAD_SKIN,5 if t.face!='UP' else 4)
            px,py=int(x+w/2),int(top-y)
            symbol=head['pixels'][py][px]
            if symbol=='H':return _tone(d.HAIR,self.head_tone(x,y,z,'NORTH'))
            if symbol=='B':return _tone(d.HAIR,3.5)
            if symbol=='L':return _rgb('#28303D')
            if symbol=='W':return _rgb(d.EYE_WHITE)
            if symbol=='D':return _rgb(d.IRIS_TOP)
            if symbol=='C':return _rgb(d.IRIS)
            if symbol=='G':return _mix(_rgb(d.IRIS_LOW),_rgb(d.EYE_WHITE),.5)
            if symbol=='M':return _rgb('#E8CCBB')
            return _tone(HEAD_SKIN,5.5)
        def __call__(self,t):
            if t.cube not in pieces:return self.body_painter(t)
            material=pieces[t.cube];x,y,z=_xhz(t)
            if material=='face':return self.new_face(t)
            if material=='side' and t.face in ('EAST','WEST') and -2.5<z<.5 and 49<y<52:return None
            if material in ('hair','side'):return _tone(d.HAIR,self.head_tone(x,y,z,t.face))
            if material=='fringe':
                col=max(0,min(w-1,int(x+w/2)))
                tones=(2.5,2,3,3.5,2.5,3,2,3,3.5,2.5,3,3.5,2.5,2)
                tone=tones[col]+(.5 if y>top-3 else -.5 if y<top-7 else 0)
                return _tone(d.HAIR,tone)
            if material=='lock':
                across=t.local[0] if t.face in ('NORTH','SOUTH') else t.local[2]
                return _tone(d.HAIR,3 if abs(across)<1 else 2.5)
            if material=='silver':return _rgb('#CDD6DE')
            if material=='gold':return _rgb('#D5BC83')
            if material=='jade':return _rgb('#8EBCAE')
            if material=='band':return _rgb('#47586D')
            raise ValueError(material)
    d.build_model,d.painter=model,Paint
    d.NAME,d.LOOK=name,spec['look']
    return d
