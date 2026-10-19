"""Thunderhorn Tiger: owned cuboid geometry, UV truth, native details and original clips.

Design space: ground y=0, up -Y, front -Z. Model scale 2 makes one unit 1/8 block.
Grey-white coat and black stripes are the owner-confirmed palette. The editable source is this
module plus art/entities/thunder_tiger/source_texture.png, never the runtime exports.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

from .. import anim, cuboid, quadruped as quad
from ..builder import Builder
from ..paint import hex_rgb, pick, ramp, value_noise

ID = "myvillage:thunder_tiger"
NAME = "thunder_tiger"
HITBOX = (2.8, 3.0)
SCALE = 2.0
ART = Path(__file__).resolve().parents[3] / "art/entities/thunder_tiger"
COAT = ramp("#747F89", "#929DA7", "#B4BEC6", "#CFD5DA", "#E4E7E7", "#F1F1EC")
WHITE = ramp("#9CA7AF", "#C1CBD0", "#DCE1DF", "#F1F0E8")
BLACK = ramp("#171C22", "#252B32", "#333C45")
HORN = ramp("#647C8A", "#91A5AF", "#BACBD0", "#DDE5DF")
BLUE = hex_rgb("#81D5EE")
EYE = hex_rgb("#B5E7ED")
NOSE = hex_rgb("#30323C")
MOUTH = hex_rgb("#492B37")
LEGS = tuple((f"{p}_{s}", f"{p}_{s}_upper", f"{p}_{s}_lower", f"{p}_{s}_paw", "back" if p == "front" else "forward")
             for p in ("front", "hind") for s in ("right", "left"))


def _leg(b, side, front):
    s = -1 if side == "right" else 1
    p = "front" if front else "hind"
    up = b.bone(f"{p}_{side}_upper", "chest" if front else "hip", at=(s * 8, -20 if front else -19, -11 if front else 11),
                rot=(15 if front else -24, 0, 0))
    b.box_local(up, f"{p}_{side}_shoulder", (-4.5, -4, -5), (9, 15, 10) if front else (9, 13, 10))
    lo = b.bone(f"{p}_{side}_lower", up, at_local=(0, 8.5, 0), rot=(-35 if front else 56, 0, 0))
    b.box_local(lo, f"{p}_{side}_shin", (-3.5, -1, -3.5), (7, 10, 7))
    paw = b.bone(f"{p}_{side}_paw", lo, at_local=(0, 8.0, 0), rot=(20 if front else -32, 0, 0))
    ground = -b.design_point(paw, (0, 0, 0))[1]
    b.box_local(paw, f"{p}_{side}_paw_cube", (-4.5, ground - 5, -5), (9, 5, 10))
    # Broad paws and a small wrist block, rather than canine thin ankles.
    if front:
        b.box_local(paw, f"{p}_{side}_wrist", (-4, ground - 8, -3), (8, 5, 7))


def build_model():
    b = Builder(ID, look={"bone": "head", "max_yaw": 32.0, "max_pitch": 22.0}, shadow_radius=1.8)
    b.model.scale = SCALE
    b.bone("body", "root", at=(0, -18, 0))
    b.bone("chest", "body", at=(0, -19, -8))
    b.bone("ribcage", "chest", at=(0, -19, -8))
    b.box("ribcage", "barrel", (-11, -24, -14), (22, 16, 20))
    b.box("ribcage", "brisket", (-9, -20, -17), (18, 13, 9))
    b.bone("hip", "body", at=(0, -18, 8))
    b.box("hip", "waist", (-7.5, -23, 3), (15, 10, 10))
    b.box("hip", "haunch", (-9, -23, 9), (18, 12, 7))
    b.bone("neck", "chest", at=(0, -21, -13))
    b.box_local("neck", "neck_cube", (-9, -5, -9), (18, 15, 12))
    b.bone("head", "neck", at=(0, -23.5, -23))
    # Wide feline skull, short muzzle and split cheek pads. No tapered canine snout.
    b.box_local("head", "skull", (-8, -7, -6), (16, 12, 11))
    b.box_local("head", "cheek_right", (-10, -2, -6), (6, 8, 8))
    b.box_local("head", "cheek_left", (4, -2, -6), (6, 8, 8))
    b.box_local("head", "brow_right", (-8, -4, -7), (7, 2, 2))
    b.box_local("head", "brow_left", (1, -4, -7), (7, 2, 2))
    b.box_local("head", "muzzle", (-6, 0, -9), (12, 4, 4))
    b.box_local("head", "nose", (-2.5, -0.5, -9.6), (5, 2, 1))
    b.bone("jaw", "head", at_local=(0, 3, -3))
    b.box_local("jaw", "jaw_cube", (-5, 0, -5.8), (10, 3, 7))
    for side, s in (("right", -1), ("left", 1)):
        b.bone(f"ear_{side}", "head", at_local=(s * 6, -5.5, 1), rot=(0, 0, s * 6))
        b.box_local(f"ear_{side}", f"ear_{side}_cube", (-2, -3, -1.5), (4, 4, 3))
        b.box_local("jaw", f"fang_{side}", (s * 3 - 0.5, -1.4, -5.6), (1, 2, 1))
    # Stable structural horn root; only horn_tip disappears, leaving a real blunt stump.
    b.bone("horn", "head", at_local=(0, -6.5, -3.5), rot=(14, 0, 0))
    b.bone("horn_base", "horn", at_local=(0, 0, 0))
    b.box_local("horn_base", "horn_stump", (-2, -3, -2), (4, 4, 4))
    b.bone("horn_tip", "horn", at_local=(0, -3, 0))
    b.box_local("horn_tip", "horn_lower", (-1.5, -5, -1.5), (3, 6, 3))
    b.box_local("horn_tip", "horn_upper", (-1, -10, -1), (2, 6, 2))
    b.box_local("horn_tip", "horn_point", (-0.5, -12, -0.5), (1, 3, 1))
    for side in ("right", "left"):
        _leg(b, side, True)
        _leg(b, side, False)
    b.bone("tail_0", "hip", at=(0, -20, 15), rot=(-12, 0, 0))
    b.box_local("tail_0", "tail_0_cube", (-2, -2, -1), (4, 4, 10))
    b.bone("tail_1", "tail_0", at_local=(0, 0, 8), rot=(-12, 0, 0))
    b.box_local("tail_1", "tail_1_cube", (-1.5, -1.5, -1), (3, 3, 10))
    b.bone("tail_2", "tail_1", at_local=(0, 0, 8), rot=(-20, 0, 0))
    b.box_local("tail_2", "tail_2_cube", (-1.5, -1.5, -1), (3, 3, 9))
    b.bone("tail_3", "tail_2", at_local=(0, 0, 7), rot=(-20, 0, 0))
    b.box_local("tail_3", "tail_3_cube", (-1.5, -1.5, -1), (3, 3, 7))
    return b.model


def legs(model):
    out = []
    for n, up, lo, paw, knee in LEGS:
        c = next(c for c in model.bone(paw).cubes if c.name.endswith("_paw_cube"))
        out.append(quad.Leg(n, up, lo, paw, (0, c.origin[1] + c.size[1], c.origin[2] + c.size[2] / 2), knee))
    return out


def _scar(t):
    if t.cube != "barrel" or t.face not in ("WEST", "EAST"):
        return False
    x, y, z = t.p
    return -13 < z < -5 and abs(y - (3 + 0.9 * (z + 10))) < 0.65


def _detail(t):
    x, y, z = t.local
    if t.cube == "nose":
        return NOSE
    if t.cube.startswith("fang"):
        return WHITE[-1]
    if t.cube == "skull" and t.face == "NORTH":
        if -2.5 < y < -0.5 and 3 < abs(x) < 7:
            return EYE if abs(x) > 4 else BLACK[0]
        if -3.5 < y < -2.5 and abs(x) > 3:
            return BLACK[0]
    if t.cube == "muzzle":
        if t.face == "NORTH":
            if abs(x) < 0.8 or y > 2.5:
                return BLACK[1]
            if 1.5 < y < 2.5 and int(abs(x)) in (2, 4):
                return BLACK[1]
        return WHITE[-1 if t.face != "UP" else 1]
    if t.cube == "jaw_cube":
        return MOUTH if t.face == "DOWN" else WHITE[2]
    if "brow" in t.cube:
        return WHITE[3] if t.face in ("NORTH", "DOWN") else COAT[1]
    if t.bone.startswith("ear"):
        return WHITE[2] if t.face == "NORTH" and abs(x) < 1.5 and y > -3 else BLACK[1]
    if t.cube.endswith("_paw_cube") and t.face == "NORTH" and y > t.cube_bottom - 2:
        return WHITE[3] if int(x + 4) % 2 else BLACK[1]
    return None


def paint(t):
    d = _detail(t)
    if d is not None:
        return d
    x, y, z = t.p
    lx, ly, lz = t.local
    if t.bone in ("horn_base", "horn_tip"):
        if (int(lx - ly + lz) % 6) == 0:
            return BLUE
        return pick(HORN, 2.6 + (-0.8 if t.n[1] > 0.5 else 0))
    if "_paw" in t.bone:
        return pick(WHITE,3 if t.n[1]<-0.5 else 2)
    if _scar(t):
        return BLUE
    shade = (0.5 if t.n[1] < -0.5 else -0.6 if t.n[1] > 0.5 else 0)
    cluster = (value_noise((x, y, z), 2.0, 31) - 0.5) * 1.1
    pale = t.n[1] > 0.5 or (t.bone in ("ribcage", "hip", "neck") and t.height < 14)
    if "cheek" in t.cube:
        pale = ly > 0 or t.face == "NORTH"
    if pale:
        return pick(WHITE, 2.2 + shade + cluster)
    # Model-space stripes run across seams. Large angular bands keep a tiger reading from distance.
    if t.bone.startswith("tail"):
        stripe = int((lz + 1) / 2) % 3 == 0
    elif "_upper" in t.bone or "_lower" in t.bone:
        stripe = math.sin(ly * 1.15 + lz * 0.28) > 0.5
    elif t.bone == "head":
        stripe = (abs(lx) < 1.2 and ly < -3) or (math.sin(ly * 1.3 + abs(lx) * 0.42) > 0.48 and ly < 1)
    else:
        stripe = math.sin(z * 0.78 + y * 0.18 + abs(x) * 0.10) > 0.47 + 0.13 * math.sin(z * 0.24)
    return pick(BLACK, 1 + shade * 0.4) if stripe else pick(COAT, 3.4 + shade + cluster)


def glow(t):
    if _scar(t):
        return BLUE + (255,)
    if t.bone in ("horn_base", "horn_tip") and int(sum(t.local[:1]) - t.local[1] + t.local[2]) % 6 == 0:
        return BLUE + (255,)
    if t.cube == "skull" and t.face == "NORTH":
        x, y, _ = t.local
        if -2.5 < y < -0.5 and 4 < abs(x) < 7:
            return EYE + (255,)
    return None


def phase2_glow(t):
    """Additional thin lightning fractures, visible only after the synced phase transition."""
    x,y,z=t.p
    if t.cube in ("barrel","haunch") and t.face in ("WEST","EAST"):
        if -7<z<13 and abs(y-(4+0.45*z+1.3*math.sin(z*.7)))<0.55:
            return (72,149,179,255)
    return None


def extra_outputs(built):
    from .. import paint as painter
    from ...gen_qingfeng_sword_model import encode_png
    from ..build import RESOURCES
    rows=painter.image(built.model,phase2_glow,built.texels)
    path=RESOURCES/"assets/myvillage/textures/entity/thunder_tiger/thunder_tiger_phase2.png"
    return {path:encode_png(rows), ART/"source_texture.png":encode_png(built.texture)}


SKIP_CREASE = tuple(c.name for b, c in build_model().cubes())  # pixel clusters carry form, no noisy AO specks


def authored_texture(model, fallback):
    path = ART / "material_tones.png"
    if not path.exists():
        return fallback
    from ...gen_qingfeng_sword_model import decode_png_rgba
    w, h, raw = decode_png_rgba(path.read_bytes())
    faces = json.loads((ART/"material_faces.json").read_text())["faces"]
    face_by_id = {f["id"]:f["rect"] for f in faces}
    from ..paint import texels
    rows = [list(row) for row in fallback]
    for t in texels(model,SKIP_CREASE):
        color=rows[t.v][t.u][:3]
        palette=COAT if color in COAT else WHITE if color in WHITE else None
        if palette is None or t.bone in ("head","jaw") or t.bone.startswith("ear") or "_paw" in t.bone:
            continue
        rect=face_by_id[t.cube+"."+t.face.lower()]
        x=rect[0]+min(rect[2]-1,int(t.fu*rect[2])); y=rect[1]+min(rect[3]-1,int(t.fv*rect[3]))
        material=tuple(raw[(y*w+x)*4:(y*w+x)*4+3])
        if max(material)-min(material)>32 or sum(material)/3<110:
            continue
        difference=sum(material)/3-sum(color)/3
        step=1 if difference>24 else -1 if difference<-24 else 0
        index=max(0,min(len(palette)-1,palette.index(color)+step))
        rows[t.v][t.u]=palette[index]+(255,)
    return rows


def _merge(*parts):
    out = {}
    for p in parts:
        for bone, targets in p.items():
            for target, v in targets.items():
                dst = out.setdefault(bone, {})
                old = dst.get(target, (1, 1, 1) if target == "scale" else (0, 0, 0))
                dst[target] = tuple(a * b if target == "scale" else a + b for a, b in zip(old, v))
    return out


def rot(b, x=0, y=0, z=0):
    return {b: {"rotation": (x, y, z)}}


def pos(b, x=0, y=0, z=0):
    return {b: {"position": (x, y, z)}}


def tail(yaw=0, lift=0):
    return _merge(*(rot(f"tail_{i}", lift * (1 - i * 0.15), yaw * (1 + i * 0.3), 0) for i in range(4)))


def stance(model, pose, shift=None, lift=None, pitch=None):
    def expand(values):
        return {n: values.get(n, values.get(n.split("_")[0], 0)) for n, *_ in LEGS} if values else None
    return _planted(model, pose, expand(shift), expand(lift), expand(pitch))


def _planted(model, pose, shift=None, lift=None, pitch=None):
    targets = quad.planted(model, legs(model), shift=shift, lift=lift, pitch=pitch)
    quad.solve_legs(model, pose, legs(model), targets)
    return pose


def _idle():
    keys=[]
    for i in range(17):
        p=i/16; s=math.sin(2*math.pi*p)
        keys.append(anim.Key(p*3, _merge({"ribcage":{"scale":(1+0.012*s,1+0.018*s,1)}},rot("head",s,2*s),tail(5*s),rot("ear_right",0,0,3*math.sin(4*math.pi*p)))))
    return anim.Clip("idle",3,True,keys)


WALK={"length":1.25,"samples":24,"duty":0.68,"stride":10.0,"lift":3.0,"phase":{"front_right":0,"hind_left":0.25,"front_left":0.5,"hind_right":0.75}}
RUN={"length":0.7,"samples":24,"duty":0.4,"stride":14.0,"lift":5.5,"phase":{"front_right":0.48,"front_left":0.54,"hind_left":0,"hind_right":0.06}}


def _gait(model, name, g):
    def body(ph):
        wave=math.sin(2*math.pi*ph)
        return _merge(pos("body",0,-1.6+0.4*math.cos(4*math.pi*ph) if name=="walk" else -2.2+0.8*math.cos(4*math.pi*ph),0),rot("chest",2*wave),rot("hip",-3*wave),rot("head",-wave),tail(4*wave,5 if name=="run" else 0))
    def feet(leg, ph):
        return quad.foot_track((ph-g["phase"][leg])%1,g["duty"],g["stride"],g["lift"],20 if name=="walk" else 35)
    keys, err=quad.gait_cycle(model,legs(model),g["length"],g["samples"],body,feet)
    return anim.Clip(name,g["length"],True,keys,{"ik_residual":err})


def _move(model,m):
    name=m["animation"]; total=m["total_ticks"]; a0,a1=m["active_ticks"]; lock=m["turn_lock_tick"]
    def keyed(t,p,label="",linear=False):
        return anim.Key(t/20,p,"linear" if linear else "catmullrom",label)
    if name=="claw":
        ready=_merge(rot("chest",-4,-10,-6),rot("head",-6,12,4),rot("front_right_upper",-72,-15,-25),rot("front_right_lower",-45),rot("front_right_paw",35),tail(-12,8))
        hit=_merge(rot("chest",8,22,10),rot("head",5,-14,-4),rot("front_right_upper",-12,35,28),rot("front_right_lower",12),rot("front_right_paw",-12),tail(16,5))
        rec=_merge(rot("chest",4,12,5),rot("front_right_upper",-10,10,12),rot("front_right_lower",-18),rot("head",10),tail(8))
    elif name=="pounce":
        ready=stance(model,_merge(pos("body",0,-6,1.5),rot("body",4),rot("neck",-5),rot("head",-9),tail(-7,12)),shift={"front":-2,"hind":-3})
        hit=_merge(rot("body",-10),rot("chest",-8),rot("head",8),rot("jaw",30),tail(3,16),*(rot(f"front_{s}_upper",-55) for s in ("left","right")),*(rot(f"front_{s}_lower",-25) for s in ("left","right")),*(rot(f"hind_{s}_upper",35) for s in ("left","right")))
        rec=stance(model,_merge(pos("body",0,-4,0),rot("body",6),rot("head",9),tail(-8,8)),shift={"front":-3,"hind":1})
    elif name=="thunder_stomp":
        ready=_merge(pos("body",0,1,1),rot("body",-14),rot("chest",-10),rot("head",-12),rot("jaw",14),tail(0,14),*(rot(f"front_{s}_upper",-60) for s in ("left","right")),*(rot(f"front_{s}_lower",-30) for s in ("left","right")))
        hit=stance(model,_merge(pos("body",0,-5,0),rot("body",8),rot("chest",8),rot("head",12),rot("jaw",4),tail(0,-6)))
        rec=stance(model,_merge(pos("body",0,-3,0),rot("head",8),tail(0,5)))
    elif name=="roar":
        ready=stance(model,_merge(pos("body",0,-2,0),rot("neck",-17),rot("head",-18),rot("jaw",24),tail(0,10)),shift={"front":-1})
        hit=stance(model,_merge(rot("chest",-3),rot("neck",-25),rot("head",-22),rot("jaw",42),tail(4,16)))
        rec=stance(model,_merge(pos("body",0,-1,0),rot("neck",-8),rot("jaw",15),tail(-4,8)))
    else:
        raise ValueError(name)
    # The held telegraph and recovery are deliberate; damaging interval is sampled from server data.
    keys=[keyed(0,{},"rest"),keyed(lock,ready,"turn_lock"),keyed(a0-2,ready,"held"),keyed(a0,hit,"active_first",True),keyed(a1,hit,"active_last",True),keyed(a1+max(1,(total-a1)*0.35),rec,"recovery"),keyed(total,{},"end")]
    if name == "pounce":
        land = quad.move_flight(m).landing
        # The entity supplies the entire arc. Reach down before the actual server touch-down,
        # then absorb it immediately; never hold the flying pose through the recovery.
        keys = [k for k in keys if k.time < (land-1)/20 or k.time > (land+2)/20]
        keys += [keyed(land-1,hit,"reach_down",True),keyed(land,rec,"landing",True),
                 keyed(land+1,rec,"absorb",True),keyed(a1,rec,"active_last",True)]
        keys = sorted({k.time:k for k in keys}.values(), key=lambda k:k.time)
    clip = anim.Clip(name,total/20,False,keys,{"phase_ticks":{"lock":lock,"active_first":a0,"active_last":a1,"end":total}})
    return _grounded_clip(model, clip, m)


def _floor_correct(model, pose, below=0):
    """Keep all cuboid corners above the floor, including the rolled corpse's outer paw."""
    mats = anim.posed_matrices(model, anim.pose_offsets(pose))
    lowest = max(cuboid.mat_apply(mats[b.name],tuple(c.origin[k]+c.size[k]*((i>>k)&1) for k in range(3)))[1]
                 for b,c in model.cubes() for i in range(8))
    if lowest > 24+below:
        pose = _merge(pose,pos("body",0,lowest-24-below,0))
    return pose


def _grounded_clip(model, clip, move=None):
    """Sample authored anticipation/strike/recovery, solve supporting feet at native half-ticks.

    A planted leg is solved through its parent pose; raised attacking paws retain their animation.
    Dense linear samples avoid Catmull-Rom floor overshoot between a landing and compression.
    """
    channels=clip.channels(); keys=[]
    n=round(clip.length*40)
    for i in range(n+1):
        sec=clip.length*i/n; tick=sec*20
        pose={}
        for bone,target,frames in channels:
            pose.setdefault(bone,{})[target]=anim.sample_channel(frames,sec,clip.length,False)
        airborne=move and move["animation"]=="pounce" and move["lunge"]["tick"]<=tick<quad.move_flight(move).landing
        if not airborne and clip.name!="death":
            targets=quad.planted(model,legs(model))
            raised=[]
            if move and move["animation"]=="claw" and 0<tick<move["total_ticks"]:
                raised=["front_right"]
            if move and move["animation"]=="thunder_stomp" and tick<move["active_ticks"][0]:
                raised=["front_left","front_right"]
            for name in raised: targets.pop(name,None)
            quad.solve_legs(model,pose,legs(model),targets)
        below=quad.move_flight(move).at(tick)[1]*16/model.scale if airborne else 0
        pose=_floor_correct(model,pose,below)
        keys.append(anim.Key(sec,pose,"linear"))
    clip.keys=keys
    return clip


def clips(model,server):
    boss_path=Path(__file__).resolve().parents[3]/"src/main/resources/data/myvillage/beast/thunder_tiger_boss.json"
    boss=json.loads(boss_path.read_text()) if boss_path.exists() else {}
    pr=boss.get("presentation",{})
    out=[_idle(),_gait(model,"walk",WALK),_gait(model,"run",RUN)]
    out += [_move(model,m) for m in server["moves"]]
    duration=0.5  # BeastEntity.STAGGER_SHOW_TICKS; presentation parity, not gameplay tuning.
    for name in ("stagger","hurt"):
        d=duration if name=="stagger" else 0.35
        p=_merge(pos("body",0,-2,1),rot("head",-12,6,-8),rot("jaw",12),tail(-8,-5))
        out.append(_grounded_clip(model,anim.Clip(name,d,False,[anim.Key(0,{}),anim.Key(min(d*0.25,0.15),p,"linear"),anim.Key(d,{})])))
    horn=boss["horn"]
    lowered=(horn["head_center_y"]-horn["exposed_head_center_y"])*16/model.scale
    exposed=_merge(pos("head",0,-lowered,0),rot("head",12),rot("jaw",24),rot("ear_right",0,0,-16),rot("ear_left",0,0,16),tail(2,-4))
    out.append(anim.Clip("horn_exposed",1.2,True,[anim.Key(0,exposed),anim.Key(0.6,_merge(exposed,rot("head",0,2,3))),anim.Key(1.2,exposed)]))
    for name,default in (("horn_break",24),("phase_change",40)):
        d=pr.get(name+"_ticks",default)/20
        p=_merge(pos("body",0,-3,0),rot("head",-24,-10,14),rot("jaw",40),tail(-14,16))
        q=_merge(rot("head",-14,15,-10),rot("jaw",30),tail(10,12))
        out.append(_grounded_clip(model,anim.Clip(name,d,False,[anim.Key(0,{}),anim.Key(d*0.18,p,"linear"),anim.Key(d*0.42,q),anim.Key(d*0.68,p),anim.Key(d,{})])))
    d=pr.get("death_ticks",20)/20
    dead=_merge(pos("body",0,-9,0),rot("body",0,0,72),rot("neck",15),rot("head",18),rot("jaw",16),tail(0,-10),*(rot(f"{p}_{s}_upper",-15 if p=="front" else 28,0,20 if s=="left" else -10) for p in ("front","hind") for s in ("left","right")))
    out.append(_grounded_clip(model,anim.Clip("death",d,False,[anim.Key(0,{}),anim.Key(d*0.3,_merge(pos("body",0,-4,0),rot("body",0,0,18))),anim.Key(d*0.75,dead,"linear"),anim.Key(d,dead)])))
    return out


def export_uv():
    """Write exact filled UV truth for skill mask/composite/validator consumers (Pillow required)."""
    from PIL import Image, ImageDraw
    m=build_model(); cuboid.pack(m); w,h=m.texture_size
    parts=[]; faces=[]; regions=[]; mask=Image.new("L",(w,h),0); draw=ImageDraw.Draw(mask)
    for bone,c in m.cubes():
        if c.uv_from: continue
        semantic=c.name
        parts.append({"id":c.name,"bone":bone.name,"semantic_region":semantic,"tex_offset":list(c.uv),"box":[int(v) for v in c.size]})
        rs=cuboid.uv_regions(c)
        for f,r in rs.items():
            x,y,rw,rh=r; draw.rectangle((x,y,x+rw-1,y+rh-1),fill=255)
            faces.append({"id":c.name+"."+f.lower(),"part":c.name,"face":f.lower(),"semantic_region":semantic,"rect":list(r)})
        iw,ih=cuboid.island_size(c)
        regions.append({"id":semantic,"label":c.name,"rect":[*c.uv,iw,ih],"prompt_role":bone.name+" pixel tiger fur; preserve face direction"})
    ART.mkdir(parents=True,exist_ok=True)
    docs={"uv_truth.json":{"schema_version":1,"entity_id":NAME,"canvas":{"width":w,"height":h},"parts":parts},"texture_faces.json":{"schema_version":1,"entity_id":NAME,"canvas":{"width":w,"height":h},"faces":faces},"texture_regions.json":{"schema_version":1,"entity_id":NAME,"canvas":{"width":w,"height":h},"regions":regions}}
    for n,doc in docs.items(): (ART/n).write_text(json.dumps(doc,indent=2)+"\n")
    mask.save(ART/"uv_islands_mask.png")
    from .. import paint as painter
    from ...gen_qingfeng_sword_model import encode_png
    rows=painter.image(m,paint,painter.texels(m,SKIP_CREASE))
    (ART/"source_texture.png").write_bytes(encode_png(authored_texture(m,rows)))
    print(f"UV truth: {w}x{h}, {len(parts)} cubes, {len(faces)} faces")


if __name__=="__main__":
    export_uv()
