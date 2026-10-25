"""已保留的三套男修衣装及旧版衣装；头部由 male.py 组合。"""
from __future__ import annotations
import importlib.util
from .defs import cultivator as legacy
from .humanoid import _box,_rgb,_tone,_xhz
from ..beastgen.builder import Builder
from ..beastgen.paint import ramp
from . import male_catalog
CONFIG=male_catalog.load()

def definition(outfit, *, motion_cover=True, reference_trousers=False):
    spec=CONFIG['outfits'][outfit]
    loader=importlib.util.spec_from_file_location('tools.npcgen.defs._male_study',legacy.__file__)
    d=importlib.util.module_from_spec(loader);loader.loader.exec_module(d)
    if outfit=='legacy':return d
    original_model=d.build_model
    for name,values in CONFIG['palettes'].items():setattr(d,name,ramp(*values))
    d.CLOTH=ramp(*spec['cloth']);d.SASH_CLOTH=ramp(*spec['sash'])
    materials={}

    def model():
        result=original_model()
        # 旧骨架和鞋底原样保留；所有新衣片、头发只加 cuboid 或新骨。
        for bone in result.bones:
            bone.cubes[:]=[c for c in bone.cubes if c.name in ('boot_right','boot_left')]
        b=Builder(d.ID);b.model=result
        def box(bone,name,x,h,z,w,height,depth,mat):
            _box(b,bone,name,x,h,z,w,height,depth);materials[name]=mat
        def local(bone,name,origin,size,mat):
            b.box_local(bone,name,origin,size);materials[name]=mat
        box('body','male_torso',-8,29,-4,16,18,8,'cloth')
        box('body','male_jacket',-8.5,29,-4.5,17,18,9,'jacket')
        box('body','male_neck',-3,46,-2.5,6,3,5,'skin')
        box('body','male_belt',-8.5,28,-4.8,17,3,10,'sash')
        box('body','male_buckle',-1.5,28.5,-5.8,3,2,1,'gold')
        # 平贴交领；上领落至腰封，下领止于交叉处。
        b.bone('male_lapel_over','body',at=(3,-47,-4.8),rot=(0,0,25))
        local('male_lapel_over','male_lapel_over_band',(-1,-.5,-.5),(2,17,1),'lining')
        b.bone('male_lapel_under','body',at=(-3,-47,-4.65),rot=(0,0,-25))
        local('male_lapel_under','male_lapel_under_band',(-1,-.5,-.5),(2,8,1),'lining')
        if spec['reference_tail']:
            tail=spec['reference_tail']
            b.bone('male_tail','head',at=tuple(tail['at']),rot=tuple(tail['rot']))
        for side in ('right','left'):
            local('arm_'+side,'male_sleeve_upper_'+side,(-3.5,-3,-3.5),(7,13,7),'cloth')
            local('forearm_'+side,'male_sleeve_lower_'+side,(-3.5,-1,-3.5),(7,11,7),'cloth')
            local('forearm_'+side,'male_cuff_'+side,(-3.5,7,-3.5),(7,3,7),'cuff')
            result.cube('male_cuff_'+side)[1].inflate=.15
            # 手的位置尺寸与旧男修一致，既有握点继续可用。
            local('forearm_'+side,'hand_'+side,(-2,9.5,-2.5),(4,5,4),'skin')
            if motion_cover and spec['outfit']=='robe':
                local('leg_'+side,'male_inner_trouser_'+side,(-3,-1,-3),(6,25,6),'pants')
            if spec['outfit']=='pants':
                fit=spec['trousers']['reference' if reference_trousers else 'current']
                width,depth=fit['width'],fit['depth']
                shaft=fit['boot_shaft_width']
                # 向外加宽，保留内侧腿缝；鞋底不变，避免改变战斗贴地解算。
                x=3-width if side=='right' else -3
                boot_x=3-shaft if side=='right' else -3
                local('leg_'+side,'male_trouser_'+side,(x,-1,-depth/2),(width,fit['height'],depth),'pants')
                local('leg_'+side,'male_boot_top_'+side,(boot_x,17,-3.5),(shaft,7,7),'boot')
        if spec['outfit']=='pants':
            box('body','male_waist',-7,25,-4,14,5,8,'pants')
            box('body','male_short_hem',-8.5,24,-4.6,17,5,9,'cloth')
            box('body','male_side_tie',5,19,-5.7,2,10,1,'sash')
        else:
            if motion_cover:
                box('body','male_inner_waist',-7,25,-4,14,5,8,'pants')
            # 单一连续长衫；四个面共享腰到下摆的坐标，不使用旧的分腿分层裙。
            if motion_cover:
                # 腰下两片分别随左右步幅展开，静止时仍是同一连续衣面。
                # 反向抵消旧挂片的 rest rotation，保留已确认的静止轮廓。
                for side,x in (('right',-9),('left',0)):
                    bone='male_robe_front_'+side
                    b.bone(bone,'vest_front_'+side,at=(0,-29,-4.5),rot=(5,0,0))
                    box(bone,bone+'_panel',x,4,-5,9,25,1,'robe')
                b.bone('male_robe_back','vest_back',at=(0,-29,4.5),rot=(-5,0,0))
                box('male_robe_back','male_robe_back_panel',-9,4,4,18,25,1,'robe')
            else:
                box('body','male_robe_front',-9,4,-5,18,25,1,'robe')
                box('body','male_robe_back',-9,4,4,18,25,1,'robe')
            box('body','male_robe_right',-9,4,-4,1,25,8,'robe')
            box('body','male_robe_left',8,4,-4,1,25,8,'robe')
            box('body','male_side_tie',5.5,18,-5.8,1,11,1,'sash')
            box('body','male_jade',5,16,-5.9,2,3,1,'jade')
        return result

    class Paint:
        def __init__(self,model):
            self.model=model
        def __call__(self,t):
            x,h,z=_xhz(t)
            material=materials.get(t.cube,'boot')
            if material=='skin':return _tone(d.SKIN,4.5 if 'neck' in t.cube else 5)
            if material=='lining':return _tone(d.SASH_CLOTH if spec['id']=='C' else d.LINING,3)
            if material=='sash':return _tone(d.SASH_CLOTH,2.5)
            if material=='gold':return _tone(d.EDGE,2.5)
            if material=='jade':return _rgb('#83AE9C')
            if material=='boot':return _tone(d.SOLE,1.5) if h<1 else _tone(d.BOOT,2)
            if material=='pants':return _tone(d.PANTS,2.5 if t.face in ('NORTH','SOUTH') else 2)
            if material=='cuff':
                if spec['id']=='B':return _tone(d.SASH_CLOTH,2) if t.local[1]>8 else _tone(d.BOOT,2)
                return _tone(d.SASH_CLOTH if spec['id']=='C' else d.LINING,2.5)
            if material=='jacket':
                if t.face=='DOWN' and abs(x)<3 and abs(z)<3:return None
                if t.face=='NORTH' and h>38 and abs(x)<(h-38)*.37:return _tone(d.LINING,3)
            if material=='robe':
                if h<5:return _tone(d.SASH_CLOTH,2)
                across=abs(x) if t.face in ('NORTH','SOUTH') else abs(z)
                tone=3 if across<4 else 2.5 if across<6 else 3
                if spec['id']=='C' and t.face in ('NORTH','SOUTH') and 5<abs(x)<7:
                    return _tone(d.SASH_CLOTH,3)
                return _tone(d.CLOTH,tone)
            if material=='cloth' and t.cube=='male_torso' and t.face=='NORTH' and h>38:
                return _tone(d.LINING,3)
            across=abs(t.local[0]) if t.face in ('NORTH','SOUTH') else abs(t.local[2])
            return _tone(d.CLOTH,3 if across<2.5 else 2.5)
    d.build_model,d.painter=model,Paint
    return d
