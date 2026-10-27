#!/usr/bin/env python3
"""筑基三式的 authored 曲面几何；只生成本境 JSON 和设计说明。"""
from __future__ import annotations

import argparse
from collections import Counter
import json
import math
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]

ENGINE_CHECK = r"""
const fs=require('fs'),crypto=require('crypto'),Module=require('module'),path=require('path');
globalThis.THREE=require('./tools/web/three.min.js');
const enginePath=path.resolve('art/immortal-armor-atlas/core/engine.js');
if(!fs.existsSync(enginePath)){console.log('engine_not_available: 网格构建 not_verified');process.exit(0);}
// 从一次读取的源码构建模块，避免并行作者更新期间把不同内核混成同一次证据。
const source=fs.readFileSync(enginePath,'utf8'),m=new Module(enginePath,module);
m.filename=enginePath;m.paths=Module._nodeModulePaths(path.dirname(enginePath));m._compile(source,enginePath);
const E=m.exports,raw=fs.readFileSync('art/immortal-armor-atlas/levels/level2.json','utf8');
const hash=s=>crypto.createHash('sha256').update(s).digest('hex');
console.log(JSON.stringify({engineSHA256:hash(source),level2SHA256:hash(raw)}));
for(const s of JSON.parse(raw)){
 E.validateDesign(s);
 const armor=E.buildDesign(s,{avatar:false}),ingot=E.buildIngot(s);
 const stats={armor:E.meshStats(armor),ingot:E.meshStats(ingot)};
 for(const group of [armor,ingot]) group.traverse(o=>{
  if(!o.isMesh)return;
  for(const key of ['position','normal']){
   const attribute=o.geometry.attributes[key];
   if(!attribute)throw new Error('缺少 '+key+' '+o.userData.componentId);
   for(const n of attribute.array)if(!Number.isFinite(n))throw new Error('非有限网格 '+o.userData.componentId);
  }
  const n=o.geometry.attributes.position.count;
  if(o.geometry.index)for(const i of o.geometry.index.array)if(!Number.isInteger(i)||i<0||i>=n)throw new Error('索引越界');
  for(const k of ['componentId','role','materialKey'])if(!o.userData[k])throw new Error('缺少组件标记 '+k);
 });
 const b=new THREE.Box3().setFromObject(armor),ib=new THREE.Box3().setFromObject(ingot);
 if(b.min.y<-.001||b.max.y>2.4||b.max.x-b.min.x>1.7)throw new Error('人台范围超界 '+s.id);
 const ratio=(ib.max.y-ib.min.y)/(ib.max.x-ib.min.x);
 if(ratio>.25)throw new Error('含装饰的真实锭高宽比超界 '+s.id);
 const exports={};
 for(const [label,group]of [['armor',armor],['ingot',ingot]]){
  const obj=E.exportOBJ(group),vertices=(obj.match(/^v /gm)||[]).length,faces=(obj.match(/^f /gm)||[]).length;
  if(/\bNaN\b|\bInfinity\b/.test(obj)||vertices!==stats[label].vertices||faces!==stats[label].triangles)throw new Error('OBJ 导出异常 '+s.id);
  exports[label]={bytes:Buffer.byteLength(obj),vertices,faces};
 }
 console.log(JSON.stringify({id:s.id,...stats,bounds:{min:b.min.toArray(),max:b.max.toArray()},
  ingotBounds:{min:ib.min.toArray(),max:ib.max.toArray()},actualIngotHeightWidth:ratio,obj:exports}));
 E.dispose(armor);E.dispose(ingot);
}
"""


def material(name, color, metalness, roughness, **kwargs):
    return dict(name=name, color=color, metalness=metalness, roughness=roughness, **kwargs)


def ring(y, rx, rz, cx=0, cz=0, **kw):
    return dict(y=y, rx=rx, rz=rz, cx=cx, cz=cz, **kw)


class Author:
    def __init__(self, spec):
        self.spec = spec
        self.spec['components'] = []
        self.spec['ingot']['components'] = []

    def add(self, key, name, role, mat, shape, **kw):
        c = dict(id=key, name=name, role=role, material=mat, shape=shape, **kw)
        target = self.spec['ingot']['components'] if role == 'ingot' else self.spec['components']
        target.append(c)
        return c

    def loft(self, key, name, role, mat, rings, **kw):
        return self.add(key, name, role, mat, 'loft', rings=rings, segments=48, thickness=.005, **kw)

    def panel(self, key, name, role, mat, polygon, pos=(0, 0, 0), rot=(0, 0, 0), depth=.012, bevel=.003, trim=None):
        kw = dict(polygon=polygon, depth=depth, bevel=bevel, position=pos, rotation=rot)
        if trim:
            kw.update(trimMaterial=trim, trimRadius=.0018)
        return self.add(key, name, role, mat, 'panel', **kw)

    def tube(self, key, name, role, mat, path, radius=.0025, **kw):
        return self.add(key, name, role, mat, 'tube', path=path, radius=radius, radialSegments=8, steps=40, **kw)

    def ribbon(self, key, name, role, mat, path, widths, normal=(0, 0, 1), **kw):
        return self.add(key, name, role, mat, 'ribbon', path=path, widths=widths, normal=normal, thickness=.006, steps=40, **kw)

    def gem(self, key, name, role, mat, radii, pos, rot=(0, 0, 0)):
        return self.add(key, name, role, mat, 'gem', radii=radii, position=pos, rotation=rot)


def mirrored_path(path, sign):
    return [[sign * x, y, z] for x, y, z in path]


def ellipse_path(rx, rz, y, a0=0, a1=360, cx=0, cz=0, n=37):
    return [[cx + rx * math.cos(math.radians(a0 + (a1-a0)*i/(n-1))), y,
             cz + rz * math.sin(math.radians(a0 + (a1-a0)*i/(n-1)))] for i in range(n)]


def trousers(a, mat, top=.97, bottom=.22, fullness=1, prefix='trouser'):
    for sign, label in [(-1, '左'), (1, '右')]:
        a.loft(f'{prefix}_{label}', f'{label}腿完整内裳', 'legs', mat, [
            ring(bottom, .057*fullness, .066, .095*sign),
            ring(.43, .069*fullness, .075, .095*sign),
            ring(.66, .079*fullness, .092, .095*sign),
            ring(.84, .087*fullness, .105, .095*sign),
            ring(top, .086*fullness, .108, .09*sign),
        ], capTop=True, capBottom=True)


def sleeves(a, mat, widths, prefix='sleeve', cuff_mat=None):
    """连通肩、肘与腕的整只袖筒，袖口止于手背之前。"""
    for sign, label in [(-1, '左'), (1, '右')]:
        rings = [ring(y, rx, rz) for y, rx, rz in widths]
        a.loft(f'{prefix}_{label}', f'{label}完整曲面袖', 'chest', mat, rings,
               position=[sign*.236, 1.438, 0], rotation=[0, 0, sign*11.2], capTop=True)
        if cuff_mat:
            y, rx, rz = widths[0]
            a.loft(f'{prefix}_cuff_{label}', f'{label}袖口整圈护缘', 'chest', cuff_mat,
                   [ring(y-.003, rx+.006, rz+.006), ring(y+.032, rx+.006, rz+.006)],
                   position=[sign*.236, 1.438, 0], rotation=[0, 0, sign*11.2])


def boots(a, upper, sole, edge, top=.33, fullness=1, prefix='boot', flared=False):
    for sign, label in [(-1, '左'), (1, '右')]:
        x = sign*.095
        a.loft(f'{prefix}_sole_{label}', f'{label}整底与跟', 'boots', sole, [
            ring(0, .069*fullness, .137, x, .055),
            ring(.035, .074*fullness, .144, x, .057),
            ring(.047, .071*fullness, .139, x, .057),
        ], capTop=True, capBottom=True)
        a.loft(f'{prefix}_upper_{label}', f'{label}包足连筒靴面', 'boots', upper, [
            ring(.043, .070*fullness, .135, x, .055),
            ring(.075, .072*fullness, .140, x, .058),
            ring(.115, .065*fullness, .119, x, .039),
            ring(min(.165,top-.07), .057*fullness, .071, x, .005),
            ring(top-.065, .059*fullness, .067, x),
            ring(top, (.066 if flared else .059)*fullness, .072 if flared else .066, x),
        ], capBottom=True)
        a.tube(f'{prefix}_welt_{label}', f'{label}鞋缘连续嵌线', 'boots', edge,
               ellipse_path(.072*fullness, .141, .048, cx=x, cz=.057), .0018)
        a.tube(f'{prefix}_lip_{label}', f'{label}靴口连续嵌线', 'boots', edge,
               ellipse_path((.067 if flared else .061)*fullness, .073 if flared else .068, top, cx=x), .002)


def head_base(a, shell, lining, height=1.795, prefix='cap'):
    # 脸正向为 +Z；155..385 度留下完整脸部开口，后脑与两颊有实体覆盖。
    a.loft(f'{prefix}_occiput', '后脑与两侧完整护壳', 'head', shell, [
        ring(1.557, .080, .085, cz=-.009), ring(1.61, .106, .106, cz=-.003),
        ring(1.695, .111, .115, cz=-.003), ring(1.755, .100, .105, cz=-.004),
        ring(height, .066, .078, cz=-.006),
    ], startAngle=155, endAngle=385)
    a.loft(f'{prefix}_top', '覆盖头顶的连续冠胎', 'head', shell, [
        ring(1.747, .100, .104, cz=-.004), ring(1.783, .082, .087, cz=-.007),
        ring(height+.014, .027, .033, cz=-.007),
    ], capTop=True)
    a.tube(f'{prefix}_nape', '后枕内衬收边', 'head', lining,
           ellipse_path(.082, .087, 1.557, 155, 385, cz=-.009), .003)


def make_cloud():
    spec = dict(
        id='zhuji_baiqing_lingyun', name='白青凌云', level=2, realm='筑基', style='轻锐 · 凌云灵甲',
        tagline='云肩向上、衣尾向后，以白青灵金的完整薄壳承托初成仙骨。',
        palette=['#abc9d0', '#649da6', '#284e62', '#d6c8a5'],
        description='白青凌云是一套以轻锐、洁净和向上生长为主旨的筑基行游甲。上身由贴体灵绫与连续成形的白青灵金护壳构成，胸腹没有补丁式小板，腰线收得清楚，肩部两片完整云翼从贴肩根部向外上提，形成短而锐的水平节奏。立体交领在锁骨处翻起，左衽向右下方落入腰带，另一条青色内领在交点下方留出窄缝，交叠厚度在侧面仍然可见。下装明确分腿，前侧两条窄幅青绫与后侧两条长些的白绫构成疏密不同的四条流向，步行时仍能读到膝与靴的独立轮廓。整套不依赖光圈；仙气来自上扬云肩、清贵发冠、背部云脊和后掠衣尾之间的呼应。',
        silhouette='肩宽约八十厘米，腰部仅三十四厘米；头顶一枚横展但不夸张的云冠把视线上提。短胸甲止于胯上，外侧衣尾止于膝上，内裤与高靴留下修长而真实的腿部体积。正面胸前为大片洁白留白，重心落在交领与腰扣，不用胸口巨宝石夺目。背面有完整护壳、窄青色竖脊以及从腰后分开的两条长云尾，侧面可见肩翼的上弧、金属厚边与衣尾离开身体的弯曲，不是贴在人台上的前脸装饰。',
        materialStory='凌云灵金锭为低扁削角锭，底部是深青致密金属，顶面具有乳白凝云状的微光泽，浅银云纹沿顶面舒卷而不切断主体。两处低凸青玉质内含物像云气在金属里凝住，薄金只点缀云纹起笔。衣甲保留同一材料逻辑：白青灵金承担胸背与肩翼，月白灵绫覆盖关节和双腿，青岚绫做衣尾与领内，冷银收边表明薄壳真实厚度，青玉仅落在额心、腰扣和小坠处。深青衬里位于开口与内层，用明暗托出完整轮廓；暖色淡金只在云纹卷尖出现，避免通身银蓝而发冷发空。',
        progression='相较炼气，贵重感来自成片轻薄灵金、均匀的抛光曲率和经过立体翻折的交领，而不是增加粗布带与铆钉。冠饰由普通束发件提升为带护枕的云冠；肩部可以保持薄而上翘的连续壳面，衣尾获得受控的轻悬形态。所有超凡效果只局限于青玉内含物的柔亮，仍能被理解为真实可穿戴器物。没有背轮、巨型浮冠、满身晶簇，为金丹之后更成熟的灵器组织保留空间。',
        segmentation='主体、袖筒、裤腿、靴面和头部护壳均是连续曲面。局部分片只出现在左右各一片上扬云肩与左右各一片膝前护面，四处局部辅层合计远小于整套面积；膝面不是逐节排片，云肩也没有多层堆叠。领缘、靴缘和云纹是线性收边与刻嵌，不算另造金属鳞片。',
        parts={
            'head': dict(name='凌云束霄冠', description='低冠胎包覆头顶、后脑与两侧，脸前开口保持清晰；前额为一片带浅凹的云弧冠额，中央青玉压住两条银色卷云。左右细弧向上收成云尖，后枕一条回云弧闭合造型，背后能见结构而非空壳。'),
            'chest': dict(name='白青凌云交领甲', description='白绫内衣完整覆盖躯干与双臂，白青灵金连续胸背壳收至腰部。两道有真实厚度的交领、成对上扬云肩、窄腰带和低凸云扣组成主次；背心一条青绫脊带承接云肩，后颈有完整金属护缘。'),
            'legs': dict(name='分云束胫裳', description='两条完整青白裤腿覆盖髋至靴口，胯部有收窄的连续内裳。前侧短青尾与后侧长白尾离身弯曲，前后长度错开；每侧膝前仅一片完整流云护面，弧形金属收边延续腰部语言。'),
            'boots': dict(name='踏岚轻履', description='靴面从偏长的微翘鞋头连续过渡至小腿中段，脚跟与鞋底闭合，深青底材与银色鞋缘区分厚度。正面青玉色长弧护面上窄下展，足背只留一条卷云嵌线，背跟也有独立银脊。'),
        },
        ingot=dict(name='凝云白青灵金锭', description='宽三十六、高六点七、深二十点五厘米的低扁削角锭；乳白青金顶面、深青下缘和冷银倒角构成三层材质。三组舒卷银云刻线与两处青玉内含物各有独立几何，暖金起笔很小，既像凝云又保持整块金属的重量。'),
        materials={
            'cloud_metal': material('白青凌云灵金', '#abc9d0', .72, .27),
            'silk_white': material('月白灵绫', '#e7eee9', .03, .61),
            'silk_teal': material('青岚绫', '#649da6', .08, .48),
            'silver': material('冷银嵌缘', '#a2becb', .9, .22),
            'jade': material('凝云青玉', '#73c9bd', .23, .23, emissive='#1d5147', emissiveIntensity=.16),
            'lining': material('深青内衬与底胎', '#284e62', .18, .52),
            'gold': material('淡金云纹起笔', '#d6c8a5', .78, .3),
            'pearl': material('云纹瓷白嵌料', '#eff9eb', .18, .31),
        },
    )
    a = Author(spec)
    head_base(a, 'cloud_metal', 'lining', 1.79, 'cloud_cap')
    a.panel('cloud_brow', '完整卷云冠额', 'head', 'cloud_metal',
            [(-.108,-.024),(-.117,.008),(-.082,.025),(-.031,.018),(0,.038),(.031,.018),(.082,.025),(.117,.008),(.108,-.024),(.04,-.011),(0,-.019),(-.04,-.011)],
            [0,1.754,.099], depth=.013, trim='silver')
    a.gem('cloud_forehead', '额心凝云玉', 'head', 'jade', [.015,.023,.009], [0,1.77,.116])
    for s, label in [(-1,'左'),(1,'右')]:
        a.ribbon(f'cloud_crest_{label}', f'{label}整弧云冠翼', 'head', 'silver',
                 mirrored_path([[.025,1.79,.025],[.066,1.831,.014],[.121,1.822,-.005],[.174,1.849,-.023]],s), [.027,.042,.034,.002])
        a.tube(f'cloud_crest_inlay_{label}', f'{label}冠翼青玉细脉', 'head', 'jade',
               mirrored_path([[.041,1.81,.032],[.077,1.831,.022],[.124,1.827,.004],[.157,1.839,-.011]],s), .0025)
        a.panel(f'cloud_temple_{label}',f'{label}完整颞侧护面','head','cloud_metal',
                [(-.013,.042),(.014,.028),(.02,-.026),(0,-.065),(-.017,-.035)],
                [s*.102,1.663,.013],[0,s*71,s*8],depth=.010,trim='silver')
    a.tube('cloud_rear_scroll','后枕回云弧','head','silver',
           [[-.083,1.69,-.078],[-.055,1.725,-.104],[0,1.714,-.121],[.055,1.725,-.104],[.083,1.69,-.078]],.0035)
    a.loft('cloud_underbody','完整月白内衣','chest','silk_white',[
        ring(.945,.175,.123),ring(1.01,.155,.117),ring(1.18,.187,.133),ring(1.36,.214,.137),ring(1.439,.197,.12)],capTop=True)
    sleeves(a,'silk_white',[(-.600,.056,.062),(-.49,.060,.067),(-.31,.068,.072),(-.15,.080,.079),(0,.086,.080)],cuff_mat='silk_teal')
    a.loft('cloud_cuirass','一体白青胸背护壳','chest','cloud_metal',[
        ring(1.055,.161,.125),ring(1.12,.173,.14),ring(1.28,.209,.147),ring(1.385,.211,.141)],capTop=False)
    a.loft('cloud_waist','窄幅环腰青岚束带','chest','silk_teal',[
        ring(.996,.163,.124),ring(1.052,.164,.13)],capTop=False)
    a.tube('cloud_waist_silver','腰封银缘','chest','silver',ellipse_path(.166,.133,1.052),.0025)
    a.ribbon('cloud_inner_lapel','青玉色立体内襟','chest','silk_teal',
             [[.08,1.492,.044],[.13,1.435,.115],[.052,1.344,.153],[-.07,1.229,.145]], [.047,.052,.049,.04])
    a.ribbon('cloud_outer_lapel','白青立体交领','chest','cloud_metal',
             [[-.079,1.492,.045],[-.127,1.436,.118],[-.047,1.344,.164],[.084,1.202,.147],[.11,1.095,.13]], [.05,.058,.055,.052,.041])
    a.tube('cloud_outer_lapel_edge','交领完整银脊','chest','silver',
           [[-.102,1.49,.049],[-.153,1.435,.12],[-.073,1.34,.17],[.061,1.202,.154],[.09,1.096,.139]],.0025)
    a.ribbon('cloud_back_collar','后颈连续翻领','chest','silk_teal',
             [[-.08,1.49,.044],[-.105,1.493,-.025],[0,1.502,-.086],[.105,1.493,-.025],[.08,1.49,.044]], [.047,.048,.047,.048,.047],normal=[0,1,0])
    a.ribbon('cloud_back_spine','青岚背脊衣带','chest','silk_teal',
             [[0,1.43,-.137],[0,1.3,-.157],[0,1.16,-.148],[0,1.05,-.137]], [.065,.052,.04,.048],normal=[0,0,-1])
    a.panel('cloud_buckle','低凸回云腰扣','chest','silver',
            [(-.041,.01),(-.026,.028),(.019,.025),(.045,.005),(.027,-.023),(-.014,-.027)], [0,1.024,.139],depth=.012,trim='gold')
    a.gem('cloud_buckle_jade','腰扣玉髓','chest','jade',[.025,.012,.007],[.004,1.024,.154])
    for s,label in [(-1,'左'),(1,'右')]:
        a.ribbon(f'cloud_shoulder_{label}',f'{label}整片上扬云肩','chest','cloud_metal',
                 mirrored_path([[.153,1.438,-.003],[.234,1.478,-.003],[.325,1.472,-.006],[.397,1.505,-.015]],s),[.174,.182,.135,.004],normal=[0,1,0])
        a.tube(f'cloud_shoulder_edge_{label}',f'{label}云肩前弧银线','chest','silver',
               mirrored_path([[.16,1.44,.080],[.234,1.479,.086],[.326,1.473,.056],[.397,1.505,-.014]],s),.0027)
        a.loft(f'cloud_bracer_{label}',f'{label}连续前臂护筒','chest','cloud_metal',[
            ring(-.558,.061,.068),ring(-.45,.067,.074),ring(-.371,.071,.076)],
            position=[s*.236,1.438,0],rotation=[0,0,s*11.2],startAngle=-30,endAngle=225)
        a.tube(f'cloud_chest_scroll_{label}',f'{label}胸侧克制瓷白云纹','chest','pearl',
               mirrored_path([[.141,1.31,.117],[.169,1.281,.104],[.158,1.248,.118],[.135,1.251,.131],[.14,1.27,.132]],s),.002)
        a.tube(f'cloud_back_scroll_{label}',f'{label}背肩云纹','chest','silver',
               mirrored_path([[.041,1.385,-.142],[.092,1.373,-.143],[.133,1.345,-.129],[.122,1.319,-.137]],s),.002)
    trousers(a,'silk_white',bottom=.22,fullness=1.0)
    a.loft('cloud_hip_shell','连续贴髋内裳','legs','silk_teal',[
        ring(.804,.182,.119),ring(.94,.18,.13),ring(.997,.16,.119)])
    for s,label in [(-1,'左'),(1,'右')]:
        a.ribbon(f'cloud_front_tail_{label}',f'{label}短青分云衣尾','legs','silk_teal',
                 mirrored_path([[.135,.994,.094],[.174,.844,.114],[.204,.689,.12],[.246,.613,.09]],s),[.118,.13,.103,.015])
        a.ribbon(f'cloud_back_tail_{label}',f'{label}长白后掠云尾','legs','silk_white',
                 mirrored_path([[.093,.982,-.102],[.13,.785,-.169],[.179,.57,-.219],[.245,.451,-.209]],s),[.139,.147,.11,.012],normal=[0,0,-1])
        a.tube(f'cloud_tail_edge_{label}',f'{label}后云尾银白收缘','legs','silver',
               mirrored_path([[.15,.967,-.112],[.197,.777,-.174],[.226,.566,-.221],[.245,.451,-.209]],s),.0018)
        a.panel(f'cloud_knee_{label}',f'{label}单片流云膝护','legs','cloud_metal',
                [(-.043,.075),(0,.101),(.043,.075),(.046,.012),(.016,-.065),(0,-.078),(-.026,-.05),(-.048,.01)],
                [s*.095,.531,.084],depth=.013,bevel=.004,trim='silver')
        a.tube(f'cloud_leg_seam_{label}',f'{label}裤外侧青纹','legs','silk_teal',
               [[s*.161,.839,.014],[s*.166,.69,.018],[s*.162,.52,.008],[s*.153,.37,.01]],.003)
    boots(a,'cloud_metal','lining','silver',top=.335,prefix='cloud_boot')
    for s,label in [(-1,'左'),(1,'右')]:
        a.panel(f'cloud_boot_front_{label}',f'{label}靴前青玉长弧','boots','silk_teal',
                [(-.035,-.095),(-.036,.075),(0,.103),(.036,.075),(.035,-.095),(0,-.12)],
                [s*.095,.234,.073],depth=.008,trim='silver')
        a.tube(f'cloud_toe_scroll_{label}',f'{label}足背卷云线','boots','silver',
               [[s*.095-.04,.104,.155],[s*.095-.014,.126,.139],[s*.095+.021,.123,.146],[s*.095+.029,.105,.165]],.0022)
        a.tube(f'cloud_heel_{label}',f'{label}后跟银脊','boots','silver',
               [[s*.095,.055,-.084],[s*.095,.15,-.070],[s*.095,.273,-.07]],.0023)
    a.add('cloud_ingot_body','凝云低扁锭主体','ingot','cloud_metal','ingot',size=[.36,.067,.205],topMaterial='cloud_metal',edgeMaterial='lining')
    for i,(cx,cz,f) in enumerate([(-.085,-.015,1),(0,.018,.82),(.08,-.025,.72)]):
        cloud=[[-.046,.035,.002],[-.022,.036,.021],[.005,.036,.018],[.027,.036,.001],[.017,.036,-.014],[-.004,.036,-.011],[-.003,.036,.001]]
        path=[[cx+x*f,y,cz+z*f] for x,y,z in cloud]
        a.tube(f'cloud_ingot_scroll_{i}','锭面独立凝云银纹','ingot','silver',path,.00165)
        a.tube(f'cloud_ingot_gold_{i}','云纹淡金起笔','ingot','gold',path[:2],.0012)
    for i,(x,z) in enumerate([(-.11,.05),(.10,.038)]):
        a.gem(f'cloud_ingot_inclusion_{i}','低凸凝云玉内含物','ingot','jade',[.026,.006,.014],[x,.035,z],[0,17+i*35,0])
    a.tube('cloud_ingot_front_edge','锭前沿冷银细脊','ingot','silver',[[-.156,.022,.084],[0,.026,.095],[.156,.022,.084]],.0018)
    a.tube('cloud_ingot_back_edge','锭后沿冷银细脊','ingot','silver',[[-.156,.022,-.084],[0,.026,-.095],[.156,.022,-.084]],.0018)
    return spec


def make_jade():
    spec = dict(
        id='zhuji_yuheng_yizhuang',name='玉衡仪装',level=2,realm='筑基',style='礼仪 · 承衡玉衣',
        tagline='以连肩玉披、深青宽袖和垂直礼绶建立秩序，贵而不迫。',
        palette=['#a5c4aa','#ebe3cb','#305f54','#bb9d5b'],
        description='玉衡仪装面向筑基修士的宗门礼仪与静立场景，造型目标是端正而有呼吸感的玉质法衣。它没有把白青凌云加长换色，而是彻底改为宽袖、长裳、连肩玉披和高桥礼冠：肩部不向外刺出，身形由圆润肩线缓缓向下展开，侧面衣料厚度与背面长垂绶共同把身体包在一个清晰的礼仪秩序内。胸前大片深青灵绫保持安静，两道奶白玉质交领立起后相交，玉衡胸饰以弯曲双臂与中间留空形成桥形负空间，让佩饰看起来是一件精工玉器，而不是一块贴胸方板。下摆没有密集切片，整体青绫裳用少量纵向起伏呈现重量；中央浅色礼蔽与两侧细绶有意保持不同宽度与长度。',
        silhouette='头顶的桥冠升至一米九十八左右，宽袖在前臂处扩展，再在手腕之前被玉护缘收住，使脸与手仍保持人的尺度。连肩玉披从后颈一体展开至双肩，正面中央开口让立体交领清晰可读，后背则是完整的浅玉弧面。腰封高而收束，下方整幅深青长裳扩展到脚踝上方，前礼蔽与后垂绶做一长一短的垂直节奏。前面是礼制中轴，侧面是宽袖空腔与玉披厚边，后面是背肩完整玉面及窄幅金线；三面各有设计重点，不能仅在正面成立。',
        materialStory='承衡青玉金锭保持低扁锭形，青玉质主体带温润乳浊感，顶面的抛光玉面不是透明玻璃。锭中两条深绿玉髓脉和几根米金丝构成自然内含物，中央为方转而不封死的衡仪刻纹，外缘呈金属凝固后的深青壳。衣甲以同样的青玉金承担冠、披肩、护腕与礼履上缘；奶白玉用于交领与胸桥，深青重绫负责长裳与宽袖，浅米绢负责礼蔽和后垂绶，米金只负责勾边与细绳，墨绿衬里填在袖内和鞋底。材料有明确工作分工：玉是承形的器物，绫是完整覆盖身体的衣料，金是细尺度工艺，不能把所有表面都抛成同一种塑料。',
        progression='筑基阶段的升级表现为可以把具有厚度的玉质灵材制成连续弯曲的披肩与桥冠，并让丝绶在静止时保持舒缓弧度。相对炼气常见的朴素束装，本套的宽袖、内外领层次、礼蔽、冠后闭合构件与靴底独立结构都更精密。它表达宗门认可与修养，不表达帝王权力；金色面积被限制，胸前无大宝石，头后无天体光环。后续境界仍可在空间悬浮、材质异变及更大尺度负空间上发展，本境保持可以穿戴、可以站立行礼的克制。',
        segmentation='整幅裳、两只宽袖、连续玉披、贴身内袍和靴面都是完整体面，没有鳞片或逐层排甲。仅在左右腕端各设置一圈独立玉护缘，腰侧各一枚独立小垂佩，属于礼器与衣装的局部辅层；披肩本身是一整道曲面，不用分片拼出。细绳、刻纹和衣边是线性细节，不能误认为分片堆砌。',
        parts={
            'head':dict(name='承衡桥玉冠',description='完整低冠胎护住头顶与后枕，额前一枚温玉弧额承托中央高桥冠。桥冠由左右连贯弧臂与高处小玉梁形成可见负空间；两侧短云耳横出后收，冠后另有闭合玉梁与细金回纹，转至背面依旧完整。'),
            'chest':dict(name='玉衡连肩仪衣',description='深青内袍包覆躯干和双臂，宽袖从肩到腕连续塑形。奶白玉交领在颈侧立起并交叉，青玉连肩披在中央留开口，后背则完整覆盖。胸前桥形玉衡饰中空，腰封以一枚窄玉圭收束，两侧米金礼绳悬垂。'),
            'legs':dict(name='青璧垂仪长裳',description='内层有完整双裤腿，外层为从腰至踝上的整幅重绫长裳，连续轮廓只通过轻微起伏表达垂坠。浅米礼蔽置于正中，后背另有更窄的浅绢垂绶，两侧小玉佩与金绳呼应，裳底与脚面之间留出实际空间。'),
            'boots':dict(name='含璧承仪履',description='较低的圆润玉边履与整幅长裳相配，脚背向鞋头舒展，完整鞋底闭合，后跟有温玉压条。墨绿底胎、青玉上缘和浅米绫面形成三个层次；足背仅有一枚细长如意玉饰，减少礼服下部的视觉噪声。'),
        },
        ingot=dict(name='承衡青玉金锭',description='低扁削角锭宽三十四、高七点二、深二十二厘米。乳青温玉主体承托独立抛光顶面，深青边壳维持锭的重量感。两道深绿玉髓、三根米金细脉和中部桥衡刻纹全部有独立三维线条，强调温润内含与礼器工艺，不采用凌云锭的卷云纹。'),
        materials={
            'jade':material('承衡青玉金','#a5c4aa',.3,.3),
            'white_jade':material('羊脂玉领料','#ebe3cb',.16,.24),
            'silk':material('深青礼仪重绫','#305f54',.03,.72),
            'ivory_silk':material('浅米礼绢','#ded7bd',.02,.64),
            'gold':material('米金刻嵌','#bb9d5b',.84,.29),
            'lining':material('墨绿内衬与底胎','#183d38',.08,.67),
            'vein':material('深绿玉髓内含物','#5a8369',.2,.31),
            'jade_polish':material('顶面精抛青玉','#bcd4b4',.36,.19),
        },
    )
    a=Author(spec)
    head_base(a,'jade','lining',1.805,'jade_cap')
    a.panel('jade_brow','一体温玉冠额','head','white_jade',
            [(-.11,-.017),(-.097,.016),(-.05,.026),(0,.018),(.05,.026),(.097,.016),(.11,-.017),(.045,-.025),(0,-.013),(-.045,-.025)],
            [0,1.753,.106],depth=.016,bevel=.004,trim='gold')
    for s,label in [(-1,'左'),(1,'右')]:
        a.ribbon(f'jade_crown_arch_{label}',f'{label}礼冠完整桥臂','head','jade',
                 mirrored_path([[.072,1.778,.028],[.081,1.853,.028],[.063,1.927,.024],[.018,1.967,.014]],s),[.028,.034,.026,.022])
        a.tube(f'jade_crown_gold_{label}',f'{label}桥臂米金细嵌','head','gold',
               mirrored_path([[.072,1.782,.035],[.082,1.855,.036],[.065,1.93,.031],[.019,1.97,.021]],s),.0018)
        a.panel(f'jade_crown_ear_{label}',f'{label}短云耳','head','jade',
                [(-.014,.012),(.039,.026),(.064,.006),(.039,-.016),(0,-.013)],
                [s*.104,1.741,.005],[0,s*20,0] if s==1 else [0,160,0],depth=.013,trim='gold')
        a.tube(f'jade_ear_cord_{label}',f'{label}短垂玉绳','head','gold',
               [[s*.109,1.712,.005],[s*.122,1.675,.013],[s*.12,1.633,.017]],.002)
        a.gem(f'jade_ear_drop_{label}',f'{label}水滴玉耳坠','head','white_jade',[.009,.021,.009],[s*.12,1.617,.017])
    a.panel('jade_crown_lintel','冠顶小玉梁','head','white_jade',
            [(-.028,-.011),(-.025,.011),(0,.019),(.025,.011),(.028,-.011),(0,-.017)],
            [0,1.964,.015],depth=.023,trim='gold')
    a.tube('jade_crown_back','冠后闭合温玉梁','head','jade',
           [[-.073,1.784,-.038],[-.069,1.843,-.057],[0,1.888,-.071],[.069,1.843,-.057],[.073,1.784,-.038]],.008)
    a.tube('jade_crown_nape_glyph','冠后回纹金线','head','gold',
           [[-.052,1.676,-.103],[-.052,1.717,-.106],[.052,1.717,-.106],[.052,1.682,-.108],[0,1.682,-.118]],.002)
    a.loft('jade_underbody','完整深青贴体内袍','chest','silk',[
        ring(.933,.18,.129),ring(1.02,.161,.119),ring(1.21,.195,.137),ring(1.41,.214,.138),ring(1.446,.194,.115)],capTop=True)
    sleeves(a,'silk',[(-.600,.091,.112),(-.49,.139,.138),(-.32,.154,.145),(-.13,.121,.114),(0,.092,.089)],prefix='jade_sleeve',cuff_mat='jade')
    a.loft('jade_mantle','完整连肩玉披','chest','jade',[
        ring(1.338,.295,.157),ring(1.40,.286,.152),ring(1.459,.216,.125),ring(1.494,.103,.089)],
        startAngle=119,endAngle=421,capTop=False)
    a.tube('jade_mantle_rim','玉披下沿整道米金嵌缘','chest','gold',ellipse_path(.296,.158,1.338,119,421),.0027)
    a.ribbon('jade_inner_lapel','奶玉内交领','chest','white_jade',
             [[.082,1.505,.034],[.139,1.439,.115],[.072,1.337,.155],[-.068,1.199,.151]], [.058,.063,.06,.047])
    a.ribbon('jade_outer_lapel','奶玉外立体交领','chest','white_jade',
             [[-.082,1.505,.034],[-.14,1.441,.115],[-.072,1.333,.17],[.075,1.189,.149],[.094,1.105,.131]], [.064,.071,.067,.05,.043])
    a.tube('jade_lapel_inlay','外领完整米金细嵌','chest','gold',
           [[-.11,1.503,.041],[-.171,1.438,.12],[-.102,1.33,.175],[.052,1.19,.155],[.073,1.106,.139]],.002)
    a.ribbon('jade_rear_collar','后颈完整奶玉领','chest','white_jade',
             [[-.082,1.505,.034],[-.102,1.512,-.031],[0,1.519,-.088],[.102,1.512,-.031],[.082,1.505,.034]], [.058,.058,.058,.058,.058],normal=[0,1,0])
    # 双臂是弯曲玉器，不用一块矩形胸牌填满胸前。
    for s,label in [(-1,'左'),(1,'右')]:
        a.ribbon(f'jade_pectoral_{label}',f'{label}胸前玉衡桥臂','chest','jade',
                 mirrored_path([[.018,1.192,.164],[.061,1.227,.166],[.102,1.218,.153],[.132,1.256,.135]],s),[.025,.034,.031,.013])
        a.tube(f'jade_pectoral_line_{label}',f'{label}玉衡桥金缘','chest','gold',
               mirrored_path([[.021,1.183,.169],[.062,1.213,.17],[.105,1.204,.157],[.137,1.249,.14]],s),.0017)
        a.tube(f'jade_sleeve_line_{label}',f'{label}袖背单道金绣','chest','gold',
               mirrored_path([[.27,1.354,-.102],[.325,1.21,-.145],[.355,1.065,-.141],[.372,.932,-.109]],s),.002)
        a.tube(f'jade_mantle_back_glyph_{label}',f'{label}背披玉髓回纹','chest','vein',
               mirrored_path([[.04,1.401,-.151],[.103,1.397,-.149],[.128,1.37,-.14],[.082,1.36,-.155]],s),.0025)
    a.gem('jade_pectoral_drop','玉衡中央小垂玉','chest','white_jade',[.013,.025,.007],[0,1.165,.166])
    a.loft('jade_belt','高位完整玉绫腰封','chest','ivory_silk',[
        ring(.982,.167,.127),ring(1.065,.169,.132)])
    a.tube('jade_belt_gold','礼封上缘米金线','chest','gold',ellipse_path(.172,.135,1.065),.0024)
    a.panel('jade_belt_plaque','腰封窄玉圭','chest','jade',
            [(-.022,-.034),(-.024,.025),(0,.043),(.024,.025),(.022,-.034),(0,-.042)],
            [0,1.024,.147],depth=.013,bevel=.003,trim='gold')
    trousers(a,'ivory_silk',bottom=.10,fullness=1.10,prefix='jade_trouser')
    a.loft('jade_long_robe','整幅青璧垂仪长裳','legs','silk',[
        ring(.175,.278,.195,wave=.009),ring(.26,.277,.192,wave=.008),ring(.48,.251,.18,wave=.006),
        ring(.73,.218,.154,wave=.004),ring(.94,.181,.132),ring(1.014,.166,.124)],capTop=False)
    a.tube('jade_hem','长裳底缘完整奶玉绣边','legs','white_jade',ellipse_path(.279,.196,.178),.003)
    a.ribbon('jade_front_apron','前中浅绢礼蔽','legs','ivory_silk',
             [[0,.992,.139],[0,.805,.167],[0,.581,.2],[0,.346,.219],[0,.251,.217]], [.148,.162,.167,.166,.13])
    a.ribbon('jade_back_train','后中浅绢垂绶','legs','ivory_silk',
             [[0,.992,-.14],[0,.814,-.17],[0,.604,-.204],[0,.39,-.224],[0,.306,-.223]], [.097,.112,.115,.108,.08],normal=[0,0,-1])
    for s,label in [(-1,'左'),(1,'右')]:
        a.tube(f'jade_apron_line_{label}',f'{label}礼蔽边缘米金线','legs','gold',
               [[s*.069,.976,.147],[s*.076,.807,.176],[s*.078,.584,.208],[s*.077,.35,.227],[s*.06,.255,.226]],.0018)
        a.ribbon(f'jade_side_sash_{label}',f'{label}礼仪窄绶','legs','white_jade',
                 mirrored_path([[.165,.969,.023],[.211,.799,.05],[.241,.623,.048],[.235,.463,.078]],s),[.028,.029,.023,.017])
        a.tube(f'jade_cord_{label}',f'{label}弧垂米金礼绳','legs','gold',
               mirrored_path([[.15,.988,.096],[.206,.878,.118],[.242,.734,.105],[.238,.654,.085]],s),.0025)
        a.panel(f'jade_pendant_{label}',f'{label}腰侧完整小玉佩','legs','jade',
                [(-.014,.027),(0,.041),(.016,.027),(.019,-.018),(0,-.042),(-.019,-.018)],
                [s*.238,.616,.085],depth=.009,trim='gold')
        a.tube(f'jade_back_hem_line_{label}',f'{label}背绶细边','legs','gold',
               [[s*.045,.976,-.149],[s*.051,.815,-.179],[s*.052,.605,-.213],[s*.047,.393,-.232],[s*.035,.309,-.231]],.0018)
        a.tube(f'jade_robe_side_{label}',f'{label}侧裳玉髓纵纹','legs','vein',
               mirrored_path([[.184,.88,.038],[.227,.647,.037],[.258,.413,.038],[.282,.20,.037]],s),.0025)
    a.panel('jade_apron_terminal','礼蔽末端温玉压饰','legs','jade',
            [(-.047,.015),(0,.027),(.047,.015),(.037,-.014),(0,-.026),(-.037,-.014)],
            [0,.306,.228],depth=.008,trim='gold')
    a.tube('jade_back_balance','背绶中央衡仪金纹','legs','gold',
           [[-.024,.655,-.211],[-.024,.702,-.207],[.024,.702,-.207],[.024,.674,-.211],[0,.674,-.214],[0,.63,-.218]],.0018)
    boots(a,'ivory_silk','lining','gold',top=.205,fullness=1.04,prefix='jade_boot',flared=True)
    for s,label in [(-1,'左'),(1,'右')]:
        a.loft(f'jade_boot_collar_{label}',f'{label}低靴整圈玉边','boots','jade',[
            ring(.172,.069,.075,s*.095),ring(.205,.071,.077,s*.095)])
        a.ribbon(f'jade_vamp_{label}',f'{label}足背如意玉饰','boots','jade',
                 [[s*.095,.141,.103],[s*.095,.127,.139],[s*.095,.087,.186]], [.026,.042,.013])
        a.tube(f'jade_heel_{label}',f'{label}后跟温玉压条','boots','white_jade',
               [[s*.095,.049,-.084],[s*.095,.113,-.076],[s*.095,.186,-.076]],.006)
    a.add('jade_ingot_body','承衡低扁锭主体','ingot','jade','ingot',size=[.34,.072,.22],topMaterial='jade_polish',edgeMaterial='lining')
    a.panel('jade_ingot_polished','整幅抛光玉面','ingot','jade_polish',
            [(-.112,-.067),(.112,-.067),(.134,-.043),(.134,.043),(.112,.067),(-.112,.067),(-.134,.043),(-.134,-.043)],
            [0,.037,0],[-90,0,0],depth=.0015,bevel=.001)
    for i,path in enumerate([
        [[-.133,.039,-.038],[-.075,.04,-.018],[-.037,.039,-.041],[.014,.04,-.026]],
        [[.027,.04,.043],[.065,.039,.012],[.115,.04,.035],[.133,.04,.018]],
    ]):
        a.tube(f'jade_ingot_vein_{i}','独立深绿玉髓内脉','ingot','vein',path,.003)
    for i,path in enumerate([
        [[-.12,.041,.055],[-.091,.041,.032],[-.067,.041,.04]],
        [[.074,.041,-.056],[.092,.041,-.034],[.127,.041,-.023]],
        [[-.009,.041,-.061],[.011,.041,-.044],[.038,.041,-.049]],
    ]):
        a.tube(f'jade_ingot_gold_vein_{i}','玉中米金丝','ingot','gold',path,.0013)
    a.tube('jade_ingot_balance_outer','桥衡外刻纹','ingot','gold',
           [[-.041,.041,-.018],[-.041,.041,.026],[.041,.041,.026],[.041,.041,-.026],[.012,.041,-.026]],.0018)
    a.tube('jade_ingot_balance_inner','桥衡内刻纹','ingot','white_jade',
           [[-.021,.041,-.014],[-.021,.041,.008],[.022,.041,.008],[.022,.041,-.008],[.003,.041,-.008]],.0015)
    a.tube('jade_ingot_front_edge','锭前缘米金封线','ingot','gold',[[-.137,.022,.097],[0,.025,.104],[.137,.022,.097]],.0015)
    a.tube('jade_ingot_back_edge','锭后缘米金封线','ingot','gold',[[-.137,.022,-.097],[0,.025,-.104],[.137,.022,-.097]],.0015)
    return spec


def make_night():
    spec=dict(
        id='zhuji_wuyao_yeque',name='乌曜夜阙',level=2,realm='筑基',style='幽玄 · 守夜玄甲',
        tagline='乌曜整甲与斜襟长裾压住暗色体积，紫辉只在转折处醒来。',
        palette=['#303744','#211e36','#8c83a9','#9dabb8'],
        description='乌曜夜阙的幽玄来自不对称的秩序与大块深色曲面，而不是骷髅、密刺和紫色灯带。躯干是一整件收腰乌曜金属甲，正面叠入宽而弯曲的斜襟护面；左肩为完整的低阙檐护肩，右肩保持窄而贴体的玄绫覆面，前后都能读到明确的主次。后腰一条偏向左侧的长裾向外弯出，前方则用两片连续玄绫前衽打开腿间负空间，内裤和高靴清晰可见。头部为护住后脑的低夜冠，额前精炼护面从眉上向两颊收束，面部开口仍保留人的目光。上方冠弧不是兽角，而像夜阙屋脊在额顶留出一道安静的门洞。整套保持沉静与克制，适合守夜、观星和幽境行走的筑基修士形象。',
        silhouette='全套重心偏高但不过宽，左肩檐弧在上缘建立一条低而平的折线，右肩和腰侧轮廓则更贴身。正面斜襟把左上与右下连接，垂直的双腿抵消这条斜势；后面以一片窄背甲和偏左长裾形成第二条反向曲线，避免前后对称得像同一个筒。头冠最高不到一米九十，不追求金丹级的高冠与环轮。衣裾最下端离地二十厘米左右，靴筒高而瘦，鞋头有足够三维长度。即使材质全部失去发光，冠门、单侧肩檐、斜交领与后掠长裾仍能识别为独立样式。',
        materialStory='乌曜玄金锭以暗银乌曜合金为完整低扁主体，上表面有带紫灰反射的精抛面，下沿为粗哑的深墨底壳。两条紫辉云母内脉与一条浅银封阙刻纹互不重复：云母是不规则弯曲的内含物，银纹则是刻意组织的门阙图式。衣甲把乌曜合金用于胸背、夜冠与靴前，紫灰玄绫用于袖、内裤和前后长衽，冷银用于窄护缘，乌玉用于低凸腰封与冠额，暗紫铜只藏在领内和少量刻纹端部。紫辉只出现在狭窄嵌脉和小玉髓上，亮度低于白青凌云的银边；它是一种材质转折，不是通体自发光的赛博装束。',
        progression='它比炼气的深色行装贵重一档：整件乌曜金属护壳能形成收腰复曲面，斜襟具有真实翻折厚度，头部护面与后枕一体配合，衣尾也由完整重绫变成可保持弧度的玄绫。筑基的少量超凡感体现在内含紫辉与离身衣裾，仍让人相信这是一套可穿戴的衣甲。没有扩大胸石、复制满身装甲尖片，也没有把暗色误作越黑越高级。后续境界可以进一步引入星空、空间与仪式性，本套只在门阙轮廓和材料内反射中埋下伏笔。',
        segmentation='连续胸背壳、完整头壳、整只袖筒、完整裤腿和连筒靴面占绝大多数可见面积。局部分片仅为左肩一枚阙檐护肩、左右前臂各一枚外侧护面；这些都是完整小件，数量有限，不沿躯干或腿部排成瓦片。前后衣衽是连续大幅衣料的裁切，领缘与刻线不是分片金属板。',
        parts={
            'head':dict(name='封阙乌曜夜冠',description='完整乌曜冠胎覆盖顶部、后脑与脸侧，额上精炼护面用短中央鼻梁与两条颊弧建立收束。高处两条完整冠弧围成门阙形负空间，后冠有更低的闭合梁，侧面紫灰玉线向后枕回收；不使用恶魔角和巨大晶冠。'),
            'chest':dict(name='乌曜斜襟守夜甲',description='暗紫内衣覆盖躯干和双臂，连续乌曜胸背壳外覆一条从左上跨向右腰的宽曲面护襟。左侧低阙檐护肩有前后厚边，右肩则为柔性玄绫弧面；背部窄甲与暗银刻纹形成清楚中轴，腰封以乌玉窄扣收住斜势。'),
            'legs':dict(name='玄裾分夜下裳',description='完整双裤腿提供实体覆盖，腰侧后环裳连续包髋，正面两条深玄长衽留出腿间负空间。左后长裾比右侧明显更长，带克制紫灰窄缘；左右髋外侧仅有一条纵向银纹，不排膝片，不做层层碎甲。'),
            'boots':dict(name='敛曜长筒履',description='高而窄的乌曜靴从闭合鞋底一直包覆至小腿上部，足背和靴筒为连续体面。前侧长弧紫灰护面把视线提到膝下，后跟有暗银竖脊，两侧少量弯折刻纹提供近看细节；鞋头深度与后跟位置都符合人台脚部。'),
        },
        ingot=dict(name='乌曜紫辉玄金锭',description='宽三十七、高七、深二十一厘米的低扁锭，暗银主体、墨色底壳和紫灰精抛顶面均为独立材质分工。两道不规则紫辉内脉、一枚很低的乌玉内含物与中央封阙银刻纹具有独立几何；前后沿仅刻短银线，保持整块黑金的安静重量。'),
        materials={
            'obsidian_metal':material('乌曜玄金','#303744',.82,.3),
            'silk':material('夜紫玄绫','#211e36',.09,.56),
            'pale_silk':material('紫灰裾缘绫','#69617f',.06,.65),
            'silver':material('暗银阙纹','#9dabb8',.86,.27),
            'black_jade':material('沉曜乌玉','#181f2a',.32,.19),
            'violet':material('紫辉云母嵌脉','#8c83a9',.44,.26,emissive='#29213f',emissiveIntensity=.14),
            'lining':material('深墨哑底','#121923',.15,.73),
            'copper':material('暗紫铜领里','#786573',.73,.34),
            'polished':material('乌曜精抛面','#424956',.9,.19),
        },
    )
    a=Author(spec)
    head_base(a,'obsidian_metal','lining',1.797,'night_cap')
    a.panel('night_brow','精炼封阙护面','head','polished',
            [(-.108,.019),(-.073,.033),(-.023,.017),(0,.036),(.023,.017),(.073,.033),(.108,.019),(.098,-.025),(.043,-.014),(.015,-.025),(0,-.049),(-.015,-.025),(-.043,-.014),(-.098,-.025)],
            [0,1.736,.107],depth=.014,bevel=.003,trim='silver')
    a.panel('night_brow_jade','额顶低凸乌玉','head','black_jade',
            [(-.019,-.009),(0,.017),(.019,-.009),(0,-.02)], [0,1.768,.122],depth=.006,trim='copper')
    for s,label in [(-1,'左'),(1,'右')]:
        a.ribbon(f'night_gate_arch_{label}',f'{label}完整阙形冠弧','head','obsidian_metal',
                 mirrored_path([[.077,1.773,.012],[.082,1.814,.002],[.115,1.839,-.008],[.089,1.861,-.012],[.025,1.861,-.015]],s),[.031,.045,.035,.035,.022])
        a.tube(f'night_gate_silver_{label}',f'{label}冠弧暗银短脊','head','silver',
               mirrored_path([[.086,1.78,.019],[.095,1.814,.009],[.126,1.839,-.001],[.092,1.873,-.005],[.025,1.873,-.008]],s),.0019)
        a.ribbon(f'night_cheek_{label}',f'{label}连续颊弧护面','head','obsidian_metal',
                 mirrored_path([[.103,1.739,.072],[.113,1.682,.064],[.104,1.625,.046],[.082,1.591,.035]],s),[.022,.028,.02,.004])
        a.tube(f'night_temple_vein_{label}',f'{label}颞侧紫辉细脉','head','violet',
               mirrored_path([[.102,1.729,.077],[.117,1.695,.023],[.111,1.666,-.041],[.087,1.647,-.087]],s),.0018)
    a.panel('night_gate_lintel','前冠低阙玉梁','head','black_jade',
            [(-.042,-.013),(-.065,.004),(-.025,.014),(0,.027),(.025,.014),(.065,.004),(.042,-.013)],
            [0,1.861,-.014],depth=.012,trim='silver')
    a.tube('night_rear_crown','后冠完整低阙梁','head','polished',
           [[-.076,1.779,-.046],[-.07,1.816,-.079],[0,1.832,-.097],[.07,1.816,-.079],[.076,1.779,-.046]],.007)
    a.tube('night_rear_glyph','后枕封阙细银纹','head','silver',
           [[-.045,1.654,-.11],[-.045,1.706,-.117],[0,1.726,-.123],[.045,1.706,-.117],[.045,1.654,-.11]],.002)
    a.loft('night_underbody','完整夜紫内衣','chest','silk',[
        ring(.937,.178,.129),ring(1.01,.154,.117),ring(1.22,.19,.135),ring(1.39,.211,.136),ring(1.444,.193,.116)],capTop=True)
    sleeves(a,'silk',[(-.600,.055,.061),(-.48,.062,.068),(-.31,.065,.071),(-.12,.079,.081),(0,.085,.083)],prefix='night_sleeve',cuff_mat='copper')
    a.loft('night_cuirass','连续复曲乌曜胸背甲','chest','obsidian_metal',[
        ring(1.029,.163,.127,twist=-4),ring(1.12,.169,.138,twist=0),ring(1.285,.205,.149,twist=5),ring(1.398,.211,.14,twist=7)])
    a.ribbon('night_inner_lapel','暗紫铜立体内领','chest','copper',
             [[.073,1.522,.02],[.118,1.451,.109],[.048,1.342,.16],[-.081,1.205,.147]], [.045,.05,.048,.037])
    a.ribbon('night_outer_lapel','乌曜整幅斜襟护面','chest','polished',
             [[-.071,1.533,.006],[-.14,1.441,.113],[-.089,1.344,.169],[.034,1.219,.165],[.101,1.096,.137]], [.044,.115,.15,.138,.074])
    a.tube('night_lapel_edge','斜襟连续暗银脊','chest','silver',
           [[-.091,1.533,.012],[-.192,1.438,.118],[-.153,1.333,.17],[-.019,1.207,.172],[.071,1.086,.145]],.0025)
    a.ribbon('night_back_collar','完整后竖领','chest','obsidian_metal',
             [[-.071,1.533,.006],[-.104,1.54,-.046],[0,1.551,-.093],[.104,1.54,-.046],[.073,1.522,.02]], [.047,.058,.065,.058,.045],normal=[0,1,0])
    a.loft('night_belt','完整沉曜腰封','chest','black_jade',[ring(.986,.165,.129),ring(1.042,.166,.132)])
    a.panel('night_buckle','乌玉斜收腰扣','chest','black_jade',
            [(-.037,.024),(.026,.031),(.045,.003),(.016,-.031),(-.039,-.018)], [0,1.012,.143],depth=.014,trim='copper')
    a.tube('night_buckle_glyph','腰扣封阙刻线','chest','silver',
           [[-.019,.998,.162],[-.019,1.024,.162],[.014,1.028,.162],[.021,1.009,.162]],.0016)
    a.ribbon('night_left_mantle','左肩完整阙檐曲壳','chest','obsidian_metal',
             [[-.16,1.445,0],[-.231,1.476,0],[-.304,1.481,-.004],[-.368,1.454,-.008],[-.397,1.411,-.01]],
             [.157,.205,.222,.174,.046],normal=[0,1,0])
    a.tube('night_left_eave','左肩前后阙檐银脊','chest','silver',
           [[-.16,1.447,.079],[-.232,1.479,.103],[-.305,1.484,.108],[-.37,1.456,.077],[-.397,1.412,.013]],.0027)
    a.tube('night_left_eave_back','左肩檐后侧完整银边','chest','silver',
           [[-.16,1.447,-.079],[-.232,1.479,-.103],[-.305,1.484,-.116],[-.37,1.456,-.093],[-.397,1.412,-.033]],.0024)
    a.ribbon('night_right_mantle','右肩玄绫柔弧','chest','pale_silk',
             [[.143,1.436,.03],[.232,1.458,.005],[.312,1.389,-.025],[.344,1.292,-.065]], [.116,.154,.12,.031],normal=[0,1,0])
    a.panel('night_backplate','完整窄脊背护','chest','polished',
            [(-.126,.185),(-.087,.227),(0,.21),(.087,.227),(.126,.185),(.083,.05),(.045,-.139),(0,-.18),(-.045,-.139),(-.083,.05)],
            [0,1.203,-.149],[0,180,0],depth=.013,bevel=.005,trim='silver')
    a.tube('night_back_glyph','背甲封阙主纹','chest','copper',
           [[-.043,1.267,-.166],[-.043,1.352,-.166],[0,1.373,-.167],[.043,1.352,-.166],[.043,1.267,-.166],[0,1.23,-.167],[0,1.113,-.166]],.0022)
    for s,label in [(-1,'左'),(1,'右')]:
        a.loft(f'night_bracer_{label}',f'{label}单枚连续臂护','chest','obsidian_metal',[
            ring(-.558,.061,.068),ring(-.47,.068,.074),ring(-.367,.072,.078)],
            position=[s*.236,1.438,0],rotation=[0,0,s*11.2],startAngle=-22,endAngle=218)
        a.tube(f'night_arm_vein_{label}',f'{label}臂护窄紫辉线','chest','violet',
               mirrored_path([[.331,.915,.07],[.327,.985,.079],[.309,1.047,.078]],s),.002)
        a.tube(f'night_chest_line_{label}',f'{label}胸腹弧形暗银刻线','chest','silver',
               mirrored_path([[.139,1.274,.119],[.156,1.211,.115],[.133,1.132,.119]],s),.0019)
    trousers(a,'silk',bottom=.24,fullness=.97,prefix='night_trouser')
    a.loft('night_hip_core','连续包髋玄绫内裳','legs','silk',[
        ring(.837,.174,.118),ring(.934,.179,.13),ring(.993,.161,.123)])
    a.loft('night_back_hip','后环连续玄裳','legs','silk',[
        ring(.552,.218,.141,cz=-.012,twist=8),ring(.731,.214,.144,cz=-.01,twist=4),
        ring(.921,.182,.133),ring(.996,.163,.126)],startAngle=155,endAngle=385)
    a.ribbon('night_front_left','左侧完整前衽','legs','silk',
             [[-.105,.99,.109],[-.135,.818,.139],[-.169,.616,.147],[-.188,.452,.12]], [.136,.159,.142,.025])
    a.ribbon('night_front_right','右侧斜短前衽','legs','pale_silk',
             [[.111,.986,.111],[.149,.822,.143],[.198,.671,.134],[.219,.603,.104]], [.121,.132,.103,.016])
    a.ribbon('night_back_train','偏左整幅后掠玄裾','legs','silk',
             [[-.018,.989,-.139],[-.057,.831,-.19],[-.115,.609,-.259],[-.18,.378,-.308],[-.225,.227,-.297]], [.272,.294,.279,.245,.13],normal=[0,0,-1])
    a.ribbon('night_train_edge','玄裾窄紫灰整幅缘','legs','pale_silk',
             [[.105,.974,-.147],[.078,.824,-.2],[.012,.606,-.27],[-.067,.373,-.319],[-.166,.228,-.309]], [.016,.019,.02,.019,.01],normal=[0,0,-1])
    a.tube('night_train_vein','后裾一条封夜银纹','legs','silver',
           [[-.082,.861,-.193],[-.109,.71,-.236],[-.154,.58,-.279],[-.177,.453,-.304]],.0017)
    for s,label in [(-1,'左'),(1,'右')]:
        a.tube(f'night_leg_line_{label}',f'{label}裤外侧暗银纵纹','legs','silver',
               [[s*.156,.861,.019],[s*.167,.723,.023],[s*.163,.581,.024],[s*.151,.444,.022]],.002)
        a.ribbon(f'night_side_keeper_{label}',f'{label}窄幅髋侧缚绫','legs','lining',
                 mirrored_path([[.17,.975,.008],[.193,.873,.017],[.21,.756,.024]],s),[.035,.041,.018],normal=[1,0,0])
    a.tube('night_front_left_edge','左前衽紫辉细缘','legs','violet',
           [[-.165,.976,.117],[-.208,.812,.145],[-.23,.607,.152],[-.188,.452,.12]],.0017)
    a.tube('night_front_right_edge','右前衽暗银细缘','legs','silver',
           [[.164,.973,.118],[.209,.819,.152],[.242,.668,.143],[.219,.603,.104]],.0017)
    boots(a,'obsidian_metal','lining','silver',top=.411,fullness=.96,prefix='night_boot')
    for s,label in [(-1,'左'),(1,'右')]:
        a.ribbon(f'night_boot_front_{label}',f'{label}靴前完整紫灰护面','boots','polished',
                 [[s*.095,.393,.07],[s*.095,.278,.081],[s*.095,.161,.088],[s*.095,.107,.145]], [.056,.069,.065,.038])
        a.tube(f'night_boot_glyph_{label}',f'{label}靴前封阙银纹','boots','silver',
               [[s*.095-.017,.213,.092],[s*.095-.017,.327,.09],[s*.095+.017,.345,.084],[s*.095+.017,.269,.092]],.0018)
        a.tube(f'night_toe_vein_{label}',f'{label}足背紫辉短脉','boots','violet',
               [[s*.095-.031,.107,.149],[s*.095,.126,.142],[s*.095+.022,.106,.167]],.0016)
        a.tube(f'night_heel_{label}',f'{label}后靴完整银脊','boots','silver',
               [[s*.095,.055,-.083],[s*.095,.173,-.073],[s*.095,.291,-.071],[s*.095,.401,-.069]],.0022)
    a.add('night_ingot_body','乌曜低扁玄金主体','ingot','obsidian_metal','ingot',size=[.37,.07,.21],topMaterial='polished',edgeMaterial='lining')
    for i,path in enumerate([
        [[-.15,.037,.034],[-.106,.038,.017],[-.08,.038,.035],[-.033,.038,.013],[.003,.038,.028]],
        [[.025,.038,-.055],[.058,.038,-.03],[.095,.038,-.044],[.125,.038,-.013],[.153,.037,-.022]],
    ]):
        a.tube(f'night_ingot_mica_{i}','锭内紫辉云母曲脉','ingot','violet',path,.0028)
        # 分叉是内脉的微小支流，不把锭主体拆成碎片。
        p=path[2]
        a.tube(f'night_ingot_branch_{i}','紫辉微小支流','ingot','copper',
               [p,[p[0]+.005,.039,p[2]+.017],[p[0]+.027,.039,p[2]+.026]],.0012)
    a.tube('night_ingot_gate_outer','封阙外银刻纹','ingot','silver',
           [[-.034,.039,-.029],[-.034,.039,.025],[0,.039,.043],[.034,.039,.025],[.034,.039,-.029]],.0016)
    a.tube('night_ingot_gate_inner','封阙内铜刻纹','ingot','copper',
           [[-.015,.039,-.025],[-.015,.039,.015],[0,.039,.025],[.015,.039,.015],[.015,.039,-.025]],.0012)
    a.gem('night_ingot_inclusion','低凸沉曜乌玉内含物','ingot','black_jade',[.026,.0045,.015],[-.118,.037,-.043],[0,31,0])
    a.tube('night_ingot_left_edge','锭前左沿银线','ingot','silver',[[-.161,.02,.077],[-.124,.024,.094],[-.07,.025,.097]],.0015)
    a.tube('night_ingot_right_edge','锭后右沿银线','ingot','silver',[[.161,.02,-.077],[.124,.024,-.094],[.07,.025,-.097]],.0015)
    return spec


def cross(a,b,c):
    return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])


def proper_intersection(a,b,c,d):
    return cross(a,b,c)*cross(a,b,d)<-1e-16 and cross(c,d,a)*cross(c,d,b)<-1e-16


def check(designs):
    assert len(designs)==3
    assert len({d['id'] for d in designs})==3
    allowed={'loft','tube','ribbon','panel','torus','ellipsoid','gem','box','ingot'}
    for d in designs:
        assert d['level']==2 and d['realm']=='筑基'
        assert len(d['materials'])>=5
        assert set(d['parts'])=={'head','chest','legs','boots'}
        comps=d['components']+d['ingot']['components']
        assert 35<=len(d['components'])<=120
        assert len({c['id'] for c in comps})==len(comps)
        assert {c['role'] for c in d['components']}=={'head','chest','legs','boots'}
        assert all(c['role']=='ingot' for c in d['ingot']['components'])
        body=[c for c in d['ingot']['components'] if c['shape']=='ingot']
        assert len(body)==1 and body[0]['size'][1]/body[0]['size'][0]<=.25
        for key,m in d['materials'].items():
            assert re.fullmatch('#[0-9a-fA-F]{6}',m['color'])
            assert 0<=m['metalness']<=1 and 0<=m['roughness']<=1
            assert 0<m.get('opacity',1)<=1
        for c in comps:
            assert c['shape'] in allowed
            assert c['material'] in d['materials']
            for k in ['trimMaterial','topMaterial','edgeMaterial']:
                if k in c:
                    assert c[k] in d['materials']
            def finite(v):
                if isinstance(v,(float,int)):
                    assert math.isfinite(v),(c['id'],v)
                elif isinstance(v,list):
                    for i in v: finite(i)
                elif isinstance(v,dict):
                    for i in v.values(): finite(i)
            finite(c)
            for k in ['position','rotation','scale']:
                if k in c: assert len(c[k])==3
            if c['shape']=='loft':
                assert len(c['rings'])>=2
                assert all(r['rx']>0 and r['rz']>0 for r in c['rings'])
                assert all(b['y']>a['y'] for a,b in zip(c['rings'],c['rings'][1:]))
                assert c['thickness']>0
                assert c.get('endAngle',360)>c.get('startAngle',0)
            if c['shape'] in {'tube','ribbon'}:
                assert len(c['path'])>=2 and all(len(p)==3 for p in c['path'])
                assert all(sum((x-y)**2 for x,y in zip(p,q))>1e-10 for p,q in zip(c['path'],c['path'][1:]))
                for k in ['radii','widths']:
                    if k in c: assert len(c[k])==len(c['path']) and min(c[k])>0
            if c['shape']=='panel':
                p=c['polygon']; n=len(p)
                assert n>=3 and all(len(x)==2 for x in p)
                assert abs(sum(p[i][0]*p[(i+1)%n][1]-p[(i+1)%n][0]*p[i][1] for i in range(n)))>1e-7
                for i in range(n):
                    for j in range(i+1,n):
                        if j==i+1 or (i==0 and j==n-1): continue
                        assert not proper_intersection(p[i],p[(i+1)%n],p[j],p[(j+1)%n]),(c['id'],'自交')
            if c['shape'] in {'gem','ellipsoid'}:
                assert len(c['radii'])==3 and min(c['radii'])>0
            if c['shape']=='box':
                assert max(c['size'])<.08,'box只能做小连接'
        all_text=''.join(str(d[k]) for k in ['description','silhouette','materialStory','progression','segmentation','parts','ingot'])
        han=len(re.findall(r'[\u4e00-\u9fff]',all_text))
        assert han>=500,(d['id'],han)
        used={c['material'] for c in comps}
        assert len(used)>=5
        print(json.dumps(dict(id=d['id'],name=d['name'],armorComponents=len(d['components']),
                              ingotComponents=len(d['ingot']['components']),roles=Counter(c['role'] for c in d['components']),
                              shapes=Counter(c['shape'] for c in d['components']),materials=len(d['materials']),
                              ingotHeightWidth=round(body[0]['size'][1]/body[0]['size'][0],4),chineseCharacters=han),ensure_ascii=False))


def design_document(designs):
    lines=['# 筑基三式：白青凌云／玉衡仪装／乌曜夜阙','',
           '本境作者范围仅为 level2.json、此说明及 authors/level2.py。三套均为冻结接口 v1 的实际几何，不涉及游戏实装、玩法、数值或武器工具。', '',
           '## 本境判断与共通尺度','',
           '筑基不能只把炼气布装改为白色或再贴几枚方板。三款分别用短甲与分腿、宽袖与长裳、不对称斜襟与后裾建立不同的主轮廓。灵材用来承托可见厚度、完整曲率与冠饰负空间；衣料负责关节覆盖和垂坠，刻线服从大面。前、侧、背均有完整设计；无任何 aura 组件。', '',
           '全部使用约米制的人台坐标，Y 向上、+Z 向前；脚底落在 y=0，肩峰在 ±0.235／1.45，双腿中心 ±0.095。头壳覆盖后枕与颞侧，前方留脸部开口；上臂袖筒沿肩至腕倾斜约 11.2°；每只靴从闭合鞋底连通足背及筒口。低扁锭独立在原点建模，三款的高宽比分别小于 0.22。', '',
           '## 三款的构图差异','',
           '|方向|远看轮廓|中景主体|近看工艺|后视重点|',
           '|---|---|---|---|---|',
           '|白青凌云|短甲、上扬云肩、修长分腿|乳白整甲与青色立体交领|冷银云纹、两处青玉内含|青岚背脊、两条后掠长云尾|',
           '|玉衡仪装|高桥冠、圆肩宽袖、完整长裳|连肩玉披与奶玉交领|衡仪刻纹、米金细绳、温玉器物|一体背披、窄幅浅绢垂绶|',
           '|乌曜夜阙|低阙冠、单侧肩檐、斜襟与偏左后裾|乌曜复曲面与暗紫玄绫|门阙银纹、紫辉内脉、暗铜领里|窄背甲、偏左整幅长裾|','']
    for d in designs:
        lines += [f"## {d['name']} · {d['style']}",'',f"> {d['tagline']}",'']
        for title,key in [('整体设计','description'),('轮廓、负空间与前后侧','silhouette'),('锭与材质故事','materialStory'),('相对炼气的升级依据','progression'),('局部分片的准确范围','segmentation')]:
            lines += [f'### {title}','',d[key],'']
        lines += ['### 四件防具','']
        for role,label in [('head','头'),('chest','胸'),('legs','腿'),('boots','靴')]:
            p=d['parts'][role]
            lines += [f"- **{label} · {p['name']}**：{p['description']}",'']
        lines += [f"### 独立锭：{d['ingot']['name']}",'',d['ingot']['description'],'',
                  '### 材质职责','', '|材质|颜色|金属度|粗糙度|','|---|---|---|---|']
        for m in d['materials'].values():
            lines += [f"|{m['name']}|{m['color']}|{m['metalness']}|{m['roughness']}|"]
        counts=Counter(c['shape'] for c in d['components'])
        roles=Counter(c['role'] for c in d['components'])
        lines += ['',f"实际几何：防具 {len(d['components'])} 组件，独立锭 {len(d['ingot']['components'])} 组件；防具 shape 分布 {dict(counts)}；槽位分布 {dict(roles)}。",'']
    lines += ['## 作者检查与证据边界','',
              '运行 `python3 art/immortal-armor-atlas/authors/level2.py --check`，从交付 JSON 回读检查：恰好三套、ID 唯一、所有数值有限、材质与 trim/top/edge 引用有效、四个防具 role 完备、锭组件独立、曲面半径与厚度为正、loft 圈序递增、路径非退化、panel 非自交、每套中文字数、组件数量和低扁锭高宽比。`--check` 只读；无参数重新生成本作者拥有的两个文件。', '',
              '内核接入后已运行 `python3 art/immortal-armor-atlas/authors/level2.py --check --engine-check`。该命令使用仓库 Three.js 与现有 core/engine.js，一次读取固定内核源码并输出 SHA-256；逐套调用 validateDesign、buildDesign、buildIngot、meshStats、exportOBJ 和 dispose。检查真实顶点、法线均有限，索引有效，Mesh 保留组件／role／材质标记，人台宽高在约定范围，包含刻线和内含物的实际锭包围盒高宽比也小于 0.25；防具与锭的内存 OBJ 顶点／面数与网格统计一致。该命令不输出 OBJ 文件，不写共享内核。', '',
              '已用相同内核与相同摄影棚布光，经 html_preview 查看三套前侧、后侧和独立锭的真实 WebGL 画面，并以 html_render 交付可点击旋转的代表预览。首轮发现夜阙冠弧与玉衡过近、肩护偏圆，已将夜阙冠压低为阙形，单肩改为檐式完整曲壳；凌云冠改为横向云弧，同时拉开金属与白绫明度。第二轮代表画面已观察到三款不同头肩和下摆轮廓。此为开发者视觉证据，预览环境使用软件 WebGL，控制台有软件回退及 ReadPixels 性能提示，没有脚本错误。', '',
              '本境代表画面与静态网格检查不能覆盖十五套总览的最终整合页面、所有旋转角度下的穿插、交互缩放或单件筛选；这些保持 not_verified。游戏、手感、音效、FPS、多人以及玩家审美认可均为 not_verified。未运行 Gradle／Minecraft，未创建或切换 checkout，未提交、推送或合并。','']
    return '\n'.join(lines)



def refine_shoulder_fit(designs):
    # Real cloth shoulder bridges cover the neutral shoulder envelopes and join
    # torso lining to the sleeve head; outer cloud/jade/night shoulders stay distinct.
    for d in designs:
        source=next(c for c in d['components'] if c['id'].endswith('_underbody'))
        for sign,label in [(-1,'左'),(1,'右')]:
            d['components'].append(dict(id='continuous_shoulder_lining_'+label,
                name=label+'连续肩袖内衬',role='chest',material=source['material'],shape='loft',
                rings=[dict(y=y,rx=rx,rz=rz,cx=sign*x,cz=0) for y,rx,rz,x in [
                    (1.375,.075,.087,.239),(1.43,.088,.086,.232),(1.47,.070,.069,.231),
                    (1.495,.045,.045,.232),(1.505,.020,.025,.231)]],
                segments=40,thickness=.004,capTop=True))
    return designs


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--check',action='store_true',help='只读校验已交付 JSON')
    parser.add_argument('--engine-check',action='store_true',help='若现有内核可用，构建真实网格并核对 OBJ；不写文件')
    args=parser.parse_args()
    target=ROOT/'levels'/'level2.json'
    if args.check:
        designs=json.loads(target.read_text(encoding='utf-8'))
        check(designs)
        if args.engine_check:
            subprocess.run(['node','-e',ENGINE_CHECK],cwd=ROOT.parents[1],check=True)
        return
    designs=refine_shoulder_fit([make_cloud(),make_jade(),make_night()])
    check(designs)
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(designs,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (ROOT/'levels'/'level2-design.md').write_text(design_document(designs),encoding='utf-8')
    if args.engine_check:
        subprocess.run(['node','-e',ENGINE_CHECK],cwd=ROOT.parents[1],check=True)


if __name__=='__main__':
    main()
