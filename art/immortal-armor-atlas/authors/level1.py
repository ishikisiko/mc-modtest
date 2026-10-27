#!/usr/bin/env python3
"""炼气三套 authored 几何；仅生成本代理拥有的两个 level1 文件。"""
from __future__ import annotations

import argparse
from collections import Counter
import json
import math
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SHAPES = {"loft", "tube", "ribbon", "panel", "torus", "ellipsoid", "gem", "box", "ingot"}
ROLES = {"head", "chest", "legs", "boots"}


def rounded(value):
    if isinstance(value, float):
        return round(value, 6)
    if isinstance(value, list):
        return [rounded(v) for v in value]
    if isinstance(value, dict):
        return {k: rounded(v) for k, v in value.items()}
    return value


class Geometry:
    def __init__(self):
        self.components = []

    def add(self, ident, name, role, material, shape, **params):
        self.components.append(rounded(dict(id=ident, name=name, role=role,
                                            material=material, shape=shape, **params)))

    def loft(self, ident, name, role, material, rings, **params):
        # tuples: (y, rx, rz, cx, cz[, wave]), in global neutral-pose coordinates.
        self.add(ident, name, role, material, "loft", rings=[dict(zip(
            ("y", "rx", "rz", "cx", "cz", "wave"), r)) for r in rings],
            segments=params.pop("segments", 48), thickness=params.pop("thickness", .006), **params)

    def tube(self, ident, name, role, material, path, radius=.002, **params):
        self.add(ident, name, role, material, "tube", path=path, radius=radius,
                 radialSegments=8, steps=params.pop("steps", 36), **params)

    def ribbon(self, ident, name, role, material, path, widths, **params):
        self.add(ident, name, role, material, "ribbon", path=path, widths=widths,
                 thickness=params.pop("thickness", .004), normal=params.pop("normal", [0, 0, 1]),
                 steps=params.pop("steps", 40), **params)

    def panel(self, ident, name, role, material, polygon, **params):
        self.add(ident, name, role, material, "panel", polygon=polygon,
                 depth=params.pop("depth", .012), bevel=params.pop("bevel", .002), **params)

    def oval(self, ident, name, role, material, cx, cy, cz, rx, rz, radius=.002,
             start=0, end=360):
        path = [[cx + rx * math.cos(math.radians(a)), cy,
                 cz + rz * math.sin(math.radians(a))]
                for a in [start + (end - start) * i / 32 for i in range(33)]]
        self.tube(ident, name, role, material, path, radius, steps=64)


def mat(name, color, metalness, roughness, **params):
    return dict(name=name, color=color, metalness=metalness, roughness=roughness, **params)


def pants(g, material, prefix, *, thigh=.088, calf=.061, hem=.18):
    g.loft(f"{prefix}_hip", "完整髋腰裤围", "legs", material,
           [(.888,.169,.099,0,0),(.954,.173,.109,0,0),
            (.99,.166,.115,0,0),(1.026,.151,.11,0,0)],thickness=.005)
    for side, x in (("l", -.095), ("r", .095)):
        g.loft(f"{prefix}_trouser_{side}", f"{'左' if x < 0 else '右'}腿完整内裳", "legs", material,
               [(hem, calf, .061, x, 0), (.39, calf + .006, .065, x, 0),
                (.55, .073, .074, x, 0), (.77, thigh, .091, x, 0),
                (.955, thigh + .003, .095, x, -.002)],
               thickness=.005, capTop=True)


def moqing():
    materials = {
        "steel": mat("墨青淬纹灵钢 · 连续护壳与锭体", "#284D59", .78, .31),
        "silver": mat("雪银嵌丝 · 轮廓与刻线", "#B7C5BF", .78, .27),
        "ink": mat("深墨青密织绫 · 袖与长背", "#152F38", .03, .72),
        "silk": mat("素月灰绿缎 · 内领与裤衬", "#C8D6CB", .04, .59),
        "jade": mat("雨后青玉 · 小型冠钉与扣心", "#70A997", .16, .23),
        "leather": mat("黑茶鞣革 · 腰封与靴底", "#252C28", .04, .87),
        "vein": mat("灰青矿脉 · 锭内含物与浅刻", "#5C8283", .45, .48),
    }
    g = Geometry()
    # A sculpted whole garment; the steel front is an elliptical curved breastplate.
    g.loft("mq_body", "收腰全围绫衣底身", "chest", "ink",
           [(1.005,.153,.112,0,0),(1.13,.164,.12,0,0),(1.31,.197,.126,0,0),
            (1.41,.207,.12,0,0),(1.47,.085,.082,0,0)])
    g.loft("mq_cuirass", "一体弧面巡霄胸壳", "chest", "steel",
           [(1.065,.151,.119,0,.002),(1.16,.167,.13,0,.001),
            (1.31,.2,.137,0,0),(1.405,.201,.13,0,0)],
           startAngle=4,endAngle=176,thickness=.011)
    g.loft("mq_dorsal", "完整燕脊后背护壳", "chest", "steel",
           [(1.08,.153,.12,0,-.002),(1.22,.18,.135,0,0),
            (1.375,.207,.13,0,0),(1.43,.145,.095,0,0)],
           startAngle=186,endAngle=354,thickness=.009)
    g.loft("mq_inner_collar", "月白贴颈立领", "chest", "silk",
           [(1.425,.083,.08,0,0),(1.5,.076,.074,0,0)],thickness=.006)
    g.loft("mq_collar", "墨钢低折护领", "chest", "steel",
           [(1.409,.113,.09,0,0),(1.461,.096,.086,0,0)],
           startAngle=137,endAngle=403,thickness=.009)
    g.ribbon("mq_cross_lapel", "斜向素绫交领", "chest", "silk",
             [[-.084,1.475,.081],[-.049,1.396,.133],[.013,1.31,.147],[.09,1.244,.127]],
             [.037,.041,.035,.024])
    g.ribbon("mq_lapel_edge", "交领雪银细压缘", "chest", "silver",
             [[-.102,1.468,.084],[-.067,1.386,.137],[-.007,1.302,.15],[.073,1.236,.131]],
             [.004,.005,.005,.004],thickness=.003)
    for side,s in (("l",-1),("r",1)):
        word = "左" if s < 0 else "右"
        g.loft(f"mq_sleeve_{side}",f"{word}完整窄袖", "chest", "ink",
               [(.859,.049,.054,s*.348,.02),(1.01,.059,.064,s*.327,.007),
                (1.165,.068,.073,s*.3,0),(1.326,.083,.086,s*.263,0),
                (1.43,.076,.078,s*.23,0)],thickness=.006)
        g.loft(f"mq_shoulder_{side}",f"{word}整铸圆肩", "chest", "steel",
               [(1.303,.079,.088,s*.27,0),(1.4,.095,.099,s*.239,0),
                (1.473,.056,.073,s*.225,-.004)],thickness=.009)
        g.loft(f"mq_vambrace_{side}",f"{word}环抱护腕", "chest", "steel",
               [(.873,.054,.06,s*.349,.021),(.965,.058,.065,s*.337,.012),
                (1.077,.062,.069,s*.318,.002)],thickness=.009)
        g.oval(f"mq_cuff_edge_{side}",f"{word}护腕口银缘", "chest", "silver",
               s*.349,.877,.021,.055,.061,.0028)
        g.tube(f"mq_arm_inlay_{side}",f"{word}袖甲单道嵌线", "chest", "silver",
               [[s*.321,1.072,.071],[s*.344,.973,.077],[s*.355,.89,.079]],.0023)
        g.ribbon(f"mq_scapula_line_{side}",f"{word}后肩向心云线", "chest", "silver",
                 [[s*.215,1.405,-.071],[s*.146,1.355,-.106],[s*.064,1.285,-.132],
                  [s*.025,1.2,-.14]], [.004,.005,.004,.003],normal=[0,0,-1])
    # One small, deliberately local layered shoulder leaf; not a lamellar suit.
    g.ribbon("mq_left_wing", "左肩单叶折霄辅层", "chest", "silver",
             [[-.18,1.455,.014],[-.25,1.471,-.012],[-.32,1.416,-.045],[-.314,1.334,-.052]],
             [.024,.04,.035,.006],normal=[0,.6,1],thickness=.007)
    g.loft("mq_sash", "全围黑茶腰封", "chest", "leather",
           [(.979,.157,.119,0,0),(1.053,.156,.119,0,0)],thickness=.01)
    g.oval("mq_sash_rim", "腰封上缘银压线", "chest", "silver",0,1.048,0,.16,.122,.0026)
    g.panel("mq_buckle", "方圆收角巡霄扣", "chest", "steel",
            [[-.035,-.021],[.018,-.027],[.041,0],[.018,.026],[-.035,.021]],
            position=[.021,1.012,.125],trimMaterial="silver",trimRadius=.0017,depth=.011)
    g.add("mq_buckle_jade","腰扣雨青嵌心","chest","jade","gem",
          radii=[.011,.016,.006],position=[.022,1.012,.138])
    g.tube("mq_breast_ridge", "胸壳向上中脊", "chest", "vein",
           [[-.01,1.085,.127],[-.027,1.15,.137],[-.032,1.208,.145],[-.057,1.245,.14]],.003)
    g.tube("mq_cloud_seal", "右胸一笔回风云篆", "chest", "silver",
           [[.058,1.352,.132],[.093,1.353,.124],[.117,1.331,.117],
            [.1,1.313,.124],[.067,1.316,.136],[.073,1.287,.137],[.102,1.278,.126]],.0017)
    pants(g,"silk","mq",thigh=.083,calf=.057,hem=.18)
    g.loft("mq_longback", "后半围完整燕尾长裳", "legs", "ink",
           [(.365,.222,.151,0,-.017,.004),(.56,.212,.15,0,-.009,.003),
            (.78,.184,.137,0,0,.001),(1.018,.157,.116,0,0,0)],
           startAngle=177,endAngle=363,thickness=.007)
    g.loft("mq_back_lining", "燕尾内侧月白衬", "legs", "silk",
           [(.378,.214,.145,0,-.017),(.63,.2,.141,0,-.009),(.98,.151,.109,0,0)],
           startAngle=182,endAngle=358,thickness=.003)
    for side,s in (("l",-1),("r",1)):
        g.ribbon(f"mq_front_tail_{side}", "左右独幅尖形前裾" + side, "legs", "ink",
                 [[s*.116,1.0,.103],[s*.151,.83,.144],[s*.187,.627,.149],[s*.208,.477,.119]],
                 [.092,.106,.08,.008],normal=[s*.25,0,1],thickness=.007)
        g.tube(f"mq_tail_inlay_{side}", "前裾窄银边" + side, "legs", "silver",
               [[s*.15,.984,.115],[s*.19,.819,.14],[s*.218,.623,.136],[s*.208,.48,.125]],.0024)
        g.ribbon(f"mq_trouser_seam_{side}", "裤侧墨青连续收线" + side, "legs", "steel",
                 [[s*.16,.93,.057],[s*.171,.71,.044],[s*.152,.53,.053],[s*.148,.345,.034]],
                 [.019,.021,.017,.012],normal=[s,0,.4])
    g.tube("mq_back_hem", "完整弧形后裾雪银下缘", "legs", "silver",
           [[(.222+.004*math.cos(math.radians(6*a)))*math.cos(math.radians(a)),
             .37,-.017+(.153+.004*math.cos(math.radians(6*a)))*math.sin(math.radians(a))]
            for a in range(180,361,12)],.0026,steps=64)
    g.ribbon("mq_back_spine", "燕尾后中素色窄嵌幅", "legs", "vein",
             [[0,.97,-.125],[0,.76,-.145],[0,.54,-.163],[0,.393,-.174]],
             [.015,.022,.018,.007],normal=[0,0,-1])
    for side,s in (("l",-1),("r",1)):
        g.tube(f"mq_hem_cloud_{side}", "后裾低位折云浅纹"+side, "legs", "vein",
               [[s*.058,.455,-.171],[s*.096,.437,-.158],[s*.141,.452,-.14],
                [s*.164,.489,-.121],[s*.152,.515,-.132]],.0018)
    for side,x in (("l",-.095),("r",.095)):
        g.loft(f"mq_boot_{side}", "完整束口巡云靴"+side, "boots", "steel",
               [(.037,.065,.139,x,.048),(.082,.067,.14,x,.05),(.133,.062,.094,x,.015),
                (.24,.061,.064,x,0),(.354,.069,.07,x,-.001)],
               thickness=.009,capBottom=True)
        g.loft(f"mq_sole_{side}", "黑茶完整靴底"+side, "boots", "leather",
               [(.006,.067,.141,x,.05),(.04,.068,.144,x,.05)],capTop=True,capBottom=True)
        g.loft(f"mq_boot_turn_{side}", "墨绫内翻靴口"+side, "boots", "ink",
               [(.325,.07,.071,x,0),(.363,.071,.071,x,0)],thickness=.005)
        g.oval(f"mq_boot_top_{side}", "靴口整圈银缘"+side, "boots", "silver",
               x,.36,0,.073,.073,.0024)
        g.tube(f"mq_vamp_line_{side}", "从靴脊收至鞋尖的银线"+side, "boots", "silver",
               [[x,.329,.074],[x,.217,.069],[x,.135,.11],[x,.089,.178]],.0026)
    # Rear shell and cap cover the head; the face remains open.
    g.loft("mq_head_shell", "开面完整后盔壳", "head", "steel",
           [(1.596,.086,.087,0,-.014),(1.665,.105,.113,0,0),(1.758,.111,.114,0,0)],
           startAngle=155,endAngle=385,thickness=.008)
    g.loft("mq_crown_cap", "连体覆顶小冠盔", "head", "steel",
           [(1.751,.111,.113,0,0),(1.81,.089,.091,0,-.009),
            (1.842,.048,.061,0,-.016),(1.849,.014,.018,0,-.02)],capTop=True,thickness=.008)
    g.panel("mq_brow", "折霄低额护弧", "head", "silver",
            [[-.106,0],[-.083,.032],[-.025,.039],[0,.049],[.025,.039],[.083,.032],
             [.106,0],[.061,.008],[0,.017],[-.061,.008]],
            position=[0,1.752,.104],depth=.008,bevel=.0015)
    g.ribbon("mq_crest", "单体后掠折霄冠脊", "head", "steel",
             [[0,1.807,.045],[0,1.903,.005],[0,1.945,-.071],[0,1.891,-.153]],
             [.039,.051,.038,.004],normal=[1,0,0],thickness=.009)
    g.tube("mq_crest_edge", "冠脊上缘雪银嵌线", "head", "silver",
           [[0,1.815,.061],[0,1.918,.019],[0,1.96,-.065],[0,1.892,-.155]],.0024)
    g.tube("mq_hairpin", "横穿冠后的青玉笄", "head", "jade",
           [[-.145,1.804,-.071],[-.07,1.81,-.073],[.07,1.81,-.073],[.145,1.804,-.071]],.0055)
    for side,s in (("l",-1),("r",1)):
        g.ribbon(f"mq_temple_{side}", "一体短护颊"+side, "head", "steel",
                 [[s*.094,1.759,.063],[s*.107,1.705,.041],[s*.096,1.64,.025]],
                 [.031,.033,.013],normal=[s*.8,0,1],thickness=.007)
    g.add("mq_brow_jade","额心雨青小玉钉","head","jade","gem",
          radii=[.012,.018,.006],position=[0,1.782,.119])
    g.ribbon("mq_crown_tie", "后冠墨绫短垂带", "head", "ink",
             [[0,1.665,-.118],[.012,1.578,-.133],[.028,1.488,-.145]],
             [.049,.041,.011],normal=[0,0,-1])

    ig = Geometry()
    ig.add("mq_ingot_body","墨青低扁六角锭","ingot","steel","ingot",
           size=[.37,.072,.203],topMaterial="steel",edgeMaterial="silver")
    for i,path in enumerate([
        [[-.135,.037,-.052],[-.077,.038,-.03],[-.02,.038,-.049],[.047,.038,-.026],[.113,.037,-.043]],
        [[-.12,.037,.043],[-.062,.038,.022],[-.003,.038,.042],[.052,.038,.018],[.12,.037,.036]],
    ]):
        ig.tube(f"mq_ingot_fold_{i}","锭面折云淬纹"+str(i),"ingot","vein",path,.002)
    ig.panel("mq_ingot_seal","巡霄菱形浅印","ingot","silver",
             [[-.024,0],[0,-.014],[.024,0],[0,.014]],
             rotation=[-90,0,0],position=[0,.038,0],depth=.0015,bevel=.0005)
    ig.add("mq_ingot_inclusion","侧缘天然青玉包体","ingot","jade","gem",
           radii=[.018,.009,.008],position=[-.064,.004,.097],rotation=[0,18,5])
    ig.tube("mq_ingot_side_vein","锭侧延续矿脉","ingot","vein",
            [[-.111,.013,.096],[-.079,.006,.099],[-.049,-.005,.101],[-.017,-.012,.103]],.0022)
    return dict(id="qi_moqing",name="墨青巡霄",level=1,realm="炼气",style="清峻巡天",
        tagline="一脊折云，窄袖长背；初入仙门也有端整的器物风骨。",
        palette=["#284D59","#C8D6CB","#B7C5BF","#152F38","#70A997"],
        description="墨青巡霄不是将江湖短打涂成青色，而以小冠、连续弧面胸壳、收束护腕和燕尾长背建立仙门仪容。上身的墨青钢占最大视觉面积，雪银只承担外轮廓和一两道导向嵌线；月白灰绿从交领与双腿露出，形成清晰、干净的明暗支点。正面一条斜领打破完全轴对称，背面双肩云线汇向纵向燕脊，转身之后依然有完整构图。它像清晨在山门云阶巡行的年轻修士，精细、自持、有礼，而不是布片与皮带拼出的旅装。",
        silhouette="肩部只有温和外扩，腰在一米附近明确收紧。前裾左右各一幅、收成细尖，中间留出完整长裤与靴脊；后半围长裳降至小腿上方，薄银弧形下缘形成一个完整背部轮廓。窄袖沿肩、肘、腕依次贴合，手部周围留有活动负空间。冠脊在头顶向后折出一个清瘦的弧，最高约一米九六；它有真实厚度并与覆顶小盔连接，没有悬空的大光轮。侧面能看到胸、背、护腕及双层裳衬之间的体积关系。",
        materialStory="墨青灵钢以低扁削角锭交付，冷色锭体的两道折云淬纹由灰青矿脉组成，侧缘保留一小处雨青玉包体；这是材质故事的可见起点。成衣的胸壳与冠盔重复同样偏深的淬钢色，玉只用于额心、笄和腰扣小嵌心，雪银则是锭侧亮边在衣甲上的精工延续。内衬是柔哑的素月缎，袖与燕尾为深墨青密织绫，腰封和靴底使用黑茶鞣革，七种材质分别负责承力、收边、织物体积、贴身衬层、点睛、耐磨和矿纹。没有将矿材设定写成已实现的属性或技能。",
        progression="炼气是本系列起点，不假定它已经拥有高境界的空间法器。贵重感首先来自完整铸壳、准确收腰、真实银嵌和冠笄礼制，足以区别凡俗农装。后续境界仍可在更轻的悬离结构、半透明灵材和冠背空间上扩展；本套主动保留哑面暗区、实体连接和有限尺度，不提前使用背轮、发光拖尾或满身晶体。",
        segmentation="主体衣身、前胸、后背、两袖及后半围长裳均是连续曲面。左肩只加一片窄小的银色后折辅叶，作用是给清瘦肩线一个轻微不对称节奏；它没有向全身扩散成鳞片或重叠金属带。前裾是服装本身的左右开裁，不以一排分片遮住人体。所有横线与靴口银线属于真实收边。",
        parts={
            "head":dict(name="折霄青笄冠",description="完整后盔壳和连体小冠覆盖后脑与头顶，正脸敞开；两侧短护颊收住太阳穴。冠脊只沿头部中线向后折出，青玉笄横穿后冠，额前一枚小玉钉与银色护弧构成远看也能识别的低额焦点。后方墨绫短带止于颈背，不装成高阶羽冠。"),
            "chest":dict(name="巡霄合脊衣甲",description="墨绫衣底先完整包覆胸背，前后再各有一整片椭圆曲面钢壳；交领从左颈斜落至右胸，圆肩、窄袖和环抱护腕跟随中性人台的外倾手臂。背甲上两条银线向内汇聚，腰部用皮封和收角扣连接，绝非只装饰前脸。"),
            "legs":dict(name="燕尾分风长裳",description="月白双腿裤衬从髋至靴口连续覆盖，侧面窄墨线顺腿收束。左右两幅尖前裾留出中间行走的空隙，后半围是一张展开的深墨曲面，内侧有浅色衬层，背中一条低对比矿纹嵌幅把肩背向下延伸。"),
            "boots":dict(name="银脊束云履",description="靴面由足尖、脚背、踝部一直连至小腿下段，完整鞋底封住足底。墨绫内翻靴口与细银整圈收边区分软硬关系，一道银脊从靴口降至前掌；足尖保持瘦而圆的端整轮廓，不以夸张尖刺抢走冠与腰的主次。")},
        ingot=dict(name="折云墨青灵钢锭",description="宽三十七、高七点二、深二十点三厘米的低扁削角锭；两道灰青折云淬纹横过顶面，中央只落一个浅菱印，银亮侧缘与天然玉包体的软硬反光有明确差异。矿纹延续到前侧，翻转观察也有信息，保持同族锭轮廓。",components=ig.components),
        materials=materials,components=g.components)


def tingyu():
    materials = {
        "bronze":mat("温润雨铜 · 连体护面与锭体", "#94764B", .77, .4),
        "edge":mat("浅金铜压缘 · 细线与冠边", "#D0B783", .7, .31),
        "celadon":mat("青瓷釉甲 · 肩与腕的完整釉面", "#6C9F94", .31, .26),
        "robe":mat("雨洗浅青绫 · 宽袖与钟形外裳", "#649D88", .02, .66),
        "lining":mat("烟梅暗衬 · 开袖内层与袍里", "#5B5360", .03, .79),
        "ivory":mat("米白细绢 · 内领与裤身", "#DED9BD", .02, .68),
        "patina":mat("细绿孔雀石 · 受控包体与雨痕", "#3F786C", .25, .54),
        "leather":mat("栗褐软革 · 束带与雨履底", "#4A3C32", .04, .86),
    }
    g=Geometry()
    g.loft("ty_body", "米白完整内衣", "chest", "ivory",
           [(1.004,.154,.115,0,0),(1.15,.168,.124,0,0),(1.36,.201,.13,0,0),
            (1.442,.163,.107,0,0),(1.484,.078,.077,0,0)])
    g.loft("ty_wrap", "雨青短外衣全围", "chest", "robe",
           [(1.018,.163,.123,0,0),(1.19,.187,.137,0,0),(1.365,.215,.143,0,0),
            (1.445,.155,.112,0,0)],thickness=.007)
    g.loft("ty_breast", "听雨整铸弧形胸护", "chest", "bronze",
           [(1.09,.159,.137,0,.003),(1.17,.174,.149,0,0),(1.30,.203,.151,0,0),
            (1.398,.19,.14,0,0)],startAngle=25,endAngle=155,thickness=.012)
    g.loft("ty_dorsal", "肩背完整青瓷护面", "chest", "celadon",
           [(1.08,.165,.131,0,-.004),(1.25,.202,.146,0,0),(1.399,.214,.139,0,0),
            (1.447,.125,.102,0,-.004)],startAngle=180,endAngle=360,thickness=.011)
    g.loft("ty_collar", "米白立口内领", "chest", "ivory",
           [(1.435,.081,.081,0,0),(1.511,.075,.074,0,0)],thickness=.005)
    g.ribbon("ty_shawl_front", "弧抱胸口的浅青披领", "chest", "robe",
             [[-.159,1.422,.067],[-.106,1.383,.119],[0,1.352,.158],
              [.106,1.383,.119],[.159,1.422,.067]],
             [.037,.042,.044,.042,.037],normal=[0,.3,1],thickness=.007)
    g.tube("ty_shawl_edge", "披领温铜细压边", "chest", "edge",
           [[-.174,1.409,.074],[-.112,1.368,.132],[0,1.332,.163],
            [.112,1.368,.132],[.174,1.409,.074]],.003)
    for side,s in (("l",-1),("r",1)):
        word="左" if s<0 else "右"
        g.loft(f"ty_sleeve_{side}",f"{word}完整钟口宽袖", "chest", "robe",
               [(.923,.116,.116,s*.344,.016),(1.01,.125,.123,s*.332,.01),
                (1.17,.108,.112,s*.296,0),(1.35,.09,.095,s*.254,0),
                (1.438,.071,.079,s*.228,0)],thickness=.008)
        g.loft(f"ty_sleeve_inside_{side}",f"{word}钟袖烟梅内衬", "chest", "lining",
               [(.931,.107,.108,s*.344,.016),(1.025,.116,.114,s*.329,.009),
                (1.21,.095,.101,s*.285,0)],thickness=.004)
        g.loft(f"ty_forearm_{side}",f"{word}袖内贴腕绢筒", "chest", "ivory",
               [(.849,.049,.055,s*.35,.02),(.986,.056,.061,s*.334,.008),
                (1.087,.061,.067,s*.314,0)],thickness=.005)
        g.loft(f"ty_wrist_{side}",f"{word}小型青瓷腕护", "chest", "celadon",
               [(.855,.052,.058,s*.351,.022),(.93,.057,.064,s*.342,.015)],
               thickness=.008)
        g.loft(f"ty_shoulder_{side}",f"{word}圆润整壳青瓷肩", "chest", "celadon",
               [(1.312,.078,.09,s*.263,0),(1.403,.096,.106,s*.24,0),
                (1.482,.048,.067,s*.216,-.006)],thickness=.01)
        g.oval(f"ty_sleeve_rim_{side}",f"{word}宽袖整圈铜缘", "chest", "edge",
               s*.344,.926,.016,.118,.118,.003)
        g.tube(f"ty_shoulder_vein_{side}",f"{word}釉面单道雨线", "chest", "edge",
               [[s*.212,1.47,.043],[s*.258,1.424,.096],[s*.279,1.345,.085]],.0022)
    # A small ornamental drop, not a large chest gem.
    g.panel("ty_raindrop_seal", "雨滴形铜面小印", "chest", "edge",
            [[0,.029],[.016,.006],[.012,-.012],[0,-.022],[-.012,-.012],[-.016,.006]],
            position=[0,1.285,.159],depth=.007)
    g.tube("ty_front_rain_1", "胸护左弧雨痕", "chest", "patina",
           [[-.111,1.304,.129],[-.118,1.249,.132],[-.091,1.198,.142],[-.053,1.176,.149]],.002)
    g.tube("ty_front_rain_2", "胸护右弧雨痕", "chest", "patina",
           [[.111,1.304,.129],[.118,1.249,.132],[.091,1.198,.142],[.053,1.176,.149]],.002)
    g.loft("ty_belt", "栗褐细腰封", "chest", "leather",
           [(.986,.168,.128,0,0),(1.054,.167,.128,0,0)],thickness=.009)
    g.panel("ty_buckle", "听雨椭菱腰扣", "chest", "bronze",
            [[-.041,0],[-.021,-.023],[.021,-.023],[.041,0],[.021,.023],[-.021,.023]],
            position=[-.018,1.021,.136],depth=.011,trimMaterial="edge",trimRadius=.002)
    g.add("ty_buckle_inset", "腰扣细绿石嵌心", "chest", "patina", "gem",
          radii=[.014,.012,.006],position=[-.018,1.021,.148])
    g.ribbon("ty_rear_collar", "后颈雨铜披领", "chest", "bronze",
             [[-.162,1.414,-.08],[-.09,1.445,-.104],[0,1.454,-.109],
              [.09,1.445,-.104],[.162,1.414,-.08]],
             [.034,.04,.044,.04,.034],normal=[0,0,-1],thickness=.008)
    g.tube("ty_dorsal_motif", "后背听雨一笔云涡", "chest", "edge",
           [[-.058,1.319,-.142],[-.085,1.29,-.137],[-.066,1.252,-.145],
            [0,1.241,-.151],[.065,1.266,-.144],[.071,1.305,-.14],[.028,1.328,-.148]],.0026)
    pants(g,"ivory","ty",thigh=.088,calf=.063,hem=.13)
    g.loft("ty_bell_robe", "完整开口钟形雨裳", "legs", "robe",
           [(.214,.298,.19,0,-.004,.007),(.31,.297,.19,0,-.004,.006),
            (.48,.278,.184,0,-.002,.005),(.65,.242,.169,0,0,.004),
            (.81,.211,.15,0,0,.002),(1.022,.168,.127,0,0,0)],
           startAngle=112,endAngle=428,thickness=.009)
    g.loft("ty_robe_inside", "钟裳烟梅完整内层", "legs", "lining",
           [(.224,.288,.182,0,-.004),(.55,.253,.169,0,0),(.86,.198,.138,0,0),
            (1.01,.161,.12,0,0)],startAngle=114,endAngle=426,thickness=.003)
    g.ribbon("ty_front_petal", "中前连幅雨滴蔽膝", "legs", "celadon",
             [[0,1.004,.139],[0,.854,.174],[0,.68,.202],[0,.517,.208]],
             [.126,.176,.143,.012],thickness=.009)
    for side,s in (("l",-1),("r",1)):
        g.tube(f"ty_petal_rim_{side}", "蔽膝雨滴细铜边"+side, "legs", "edge",
               [[s*.062,.994,.146],[s*.085,.855,.18],[s*.07,.685,.206],[0,.519,.214]],.0025)
        a=112 if s<0 else 428
        ca,sa=math.cos(math.radians(a)),math.sin(math.radians(a))
        g.tube(f"ty_robe_open_{side}", "前开襟连续铜缘"+side, "legs", "edge",
               [[.168*ca,1.013,.127*sa],[.211*ca,.81,.15*sa],
                [.257*ca,.59,.176*sa],[.298*ca,.218,.19*sa-.004]],.0027)
    g.tube("ty_hem", "钟裳完整温铜下缘", "legs", "edge",
           [[(.298+.007*math.cos(math.radians(6*a)))*math.cos(math.radians(a)),
             .218,(.19+.007*math.cos(math.radians(6*a)))*math.sin(math.radians(a))-.004]
            for a in range(112,429,8)],.0028,steps=80)
    g.ribbon("ty_rear_pleat", "后中青瓷色长褶脊", "legs", "celadon",
             [[0,.995,-.134],[0,.78,-.165],[0,.51,-.191],[0,.242,-.199]],
             [.026,.045,.061,.034],normal=[0,0,-1],thickness=.005)
    for side,s in (("l",-1),("r",1)):
        g.tube(f"ty_side_drop_{side}", "侧前裳单枚雨滴织纹"+side, "legs", "patina",
               [[s*.162,.714,.126],[s*.174,.677,.134],[s*.185,.647,.138],
                [s*.171,.629,.15],[s*.152,.644,.157],[s*.151,.673,.146],
                [s*.162,.714,.126]],.0019)
    # Rain arcs are sparse; broad robe surfaces remain quiet.
    for side,s in (("l",-1),("r",1)):
        g.tube(f"ty_hem_rain_{side}", "后裳单道浮雨弧"+side, "legs", "patina",
               [[s*.213,.36,-.13],[s*.178,.33,-.157],[s*.116,.331,-.18],
                [s*.074,.358,-.187]],.0023)
        x=s*.095
        g.loft(f"ty_boot_{side}", "完整圆头踏雨履"+side, "boots", "celadon",
               [(.033,.07,.14,x,.045),(.075,.073,.142,x,.047),(.13,.068,.095,x,.012),
                (.216,.066,.069,x,0),(.284,.071,.075,x,0)],thickness=.009,capBottom=True)
        g.loft(f"ty_sole_{side}", "栗褐防潮整底"+side, "boots", "leather",
               [(.005,.072,.142,x,.047),(.035,.074,.145,x,.047)],capTop=True,capBottom=True)
        g.loft(f"ty_boot_sock_{side}", "雨履米白内口"+side, "boots", "ivory",
               [(.263,.066,.068,x,0),(.303,.065,.068,x,0)],thickness=.005)
        g.oval(f"ty_boot_top_{side}", "圆履温铜口沿"+side, "boots", "edge",
               x,.281,0,.073,.077,.0026)
        g.tube(f"ty_boot_motif_{side}", "履面环抱水滴线"+side, "boots", "edge",
               [[x-.041,.102,.111],[x-.025,.127,.096],[x,.153,.087],
                [x+.025,.127,.096],[x+.041,.102,.111]],.0025)
    g.loft("ty_head_rear", "雨铜开面圆后盔", "head", "bronze",
           [(1.578,.083,.086,0,-.015),(1.657,.106,.114,0,0),(1.756,.111,.116,0,0)],
           startAngle=151,endAngle=389,thickness=.008)
    g.loft("ty_head_cap", "低圆青瓷冠顶", "head", "celadon",
           [(1.752,.113,.115,0,0),(1.805,.102,.104,0,-.003),
            (1.851,.065,.075,0,-.007),(1.868,.014,.018,0,-.01)],
           thickness=.009,capTop=True)
    g.loft("ty_brow_band", "低额全围浅铜束圈", "head", "edge",
           [(1.747,.114,.117,0,0),(1.777,.113,.116,0,0)],thickness=.006)
    g.panel("ty_crown_crescent", "前冠舒展单体听雨璧", "head", "bronze",
            [[-.1,0],[-.088,.055],[-.048,.077],[0,.042],[.048,.077],[.088,.055],
             [.1,0],[.061,.012],[0,-.004],[-.061,.012]],
            position=[0,1.771,.084],rotation=[-9,0,0],depth=.01,
            trimMaterial="edge",trimRadius=.002)
    g.add("ty_crown_seed", "额心米白雨滴珠", "head", "ivory", "gem",
          radii=[.014,.023,.008],position=[0,1.798,.112])
    for side,s in (("l",-1),("r",1)):
        g.ribbon(f"ty_temple_{side}", "青瓷圆收护耳"+side, "head", "celadon",
                 [[s*.104,1.746,.038],[s*.118,1.68,.018],[s*.102,1.621,-.012]],
                 [.035,.044,.013],normal=[s,0,.4],thickness=.008)
        g.tube(f"ty_pendant_chain_{side}", "短垂铜雨线"+side, "head", "edge",
               [[s*.112,1.683,.025],[s*.12,1.637,.032],[s*.118,1.597,.032]],.0019)
        g.add(f"ty_drop_{side}","小型细绿垂雨"+side,"head","patina","gem",
              radii=[.007,.015,.006],position=[s*.118,1.588,.032])
    g.ribbon("ty_nape_fold", "烟梅后颈整幅护衬", "head", "lining",
             [[0,1.662,-.121],[0,1.576,-.126],[0,1.529,-.105]],
             [.117,.139,.1],normal=[0,0,-1],thickness=.005)
    g.tube("ty_head_back_motif", "后冠一笔温铜弧", "head", "edge",
           [[-.069,1.78,-.09],[-.048,1.809,-.092],[0,1.822,-.099],
            [.048,1.809,-.092],[.069,1.78,-.09]],.0024)
    ig=Geometry()
    ig.add("ty_ingot_body","温雨铜低扁削角锭","ingot","bronze","ingot",
           size=[.365,.074,.211],topMaterial="bronze",edgeMaterial="edge")
    for i,(cx,cz,rx,rz) in enumerate([(-.049,-.003,.042,.026),(.057,.015,.027,.016)]):
        path=[[cx+rx*math.cos(math.radians(a)),.039,cz+rz*math.sin(math.radians(a))]
              for a in range(20,321,15)]
        ig.tube(f"ty_ingot_ripple_{i}","锭顶非闭合听雨涟纹"+str(i),"ingot","patina",path,.0018)
    ig.panel("ty_ingot_drop","锭顶嵌铜雨滴印","ingot","edge",
             [[0,.021],[.013,.002],[.009,-.012],[0,-.017],[-.009,-.012],[-.013,.002]],
             position=[.018,.039,-.039],rotation=[-90,0,-12],depth=.0016,bevel=.0004)
    ig.add("ty_ingot_stone","前侧扁长孔雀石包体","ingot","patina","gem",
           radii=[.024,.012,.007],position=[.072,-.002,.104],rotation=[0,9,-17])
    ig.tube("ty_ingot_side","顺侧绿矿脉","ingot","patina",
            [[-.112,.016,.102],[-.077,.005,.105],[-.031,.009,.105],[.01,-.007,.107]],.002)
    ig.panel("ty_ingot_glaze", "锭侧青瓷色小釉窗", "ingot", "celadon",
             [[-.02,-.007],[.024,-.006],[.016,.009],[-.012,.012]],
             position=[-.071,.008,-.106],rotation=[0,180,0],depth=.002,bevel=.0005)
    return dict(id="qi_tingyu",name="青铜听雨",level=1,realm="炼气",style="清润听山",
        tagline="铜不耀目，青瓷带雨；圆肩与钟袖收成一身温润的山门仪装。",
        palette=["#94764B","#649D88","#6C9F94","#5B5360","#DED9BD"],
        description="青铜听雨以润铜、青瓷釉和浅青宽袖组成一套文雅但有防护体积的仙门衣甲。它的美感来自圆润的肩颈、胸前小幅水滴构图和钟形下裳，避免把铜甲做成厚重古代军阵，也避免把衣服退回无形的农家长衫。头冠很低，前额单体听雨璧向两侧舒展，耳侧两粒短垂雨只为脸部提供精致尺度。正面温铜胸护是安静的主面，背面青瓷护壳与一笔云涡相接，烟梅内衬在袖口、开襟和背颈处提供柔暗反衬。",
        silhouette="整体是上圆下舒的钟形：宽袖从肩部逐渐扩张，在手腕上方显出一圈完整开口，内部有烟梅衬筒与米白贴腕层，不能从侧面看成空心披布。腰封仍明确束腰，下裳由一张接近全周的开口曲面扩至小腿，前中留出窄而连续的负空间。青瓷雨滴蔽膝覆盖正中上腿，双脚从米白裤衬与长袍间完整伸出。侧背的长褶只有一条，帮观者理解衣料厚度，轮廓不使用尖锐战翼或高耸冠脊。",
        materialStory="听雨铜锭保持低扁削角家族形状，温润铜面上有两道不闭合的细绿涟纹、一枚浅铜雨滴印，前侧孔雀石包体与后侧小釉窗让转动观察仍有独立信息。铜的低亮度大面成为胸护，浅金铜负责冠、袖口和下裳细压缘，青瓷釉则落在圆肩、后背、护腕、蔽膝与雨履；这三种硬材的反光尺度不同。浅青绫构成宽袖与长袍，烟梅暗衬仅在开口处显现，米白细绢贴颈并覆盖双腿，栗褐软革承担束带与靴底。细绿矿纹的面积很小，不将整套做成脏旧铜锈。",
        progression="作为炼气的清润方向，青铜听雨展示的是可触摸的人间精工走向灵材的第一步，靠釉面的洁净、曲线的圆收和袍袖的完整裁形体现修仙气质。它与墨青巡霄处于同境，不把宽袖解释成更高战力；它也没有悬浮背环、半透明能量袍或天象特效。后续境界可把釉的内光与环绕空间发展成真正超凡的视觉，本套保留实体扣合、有限冠高和清楚的织物重量。",
        segmentation="全套没有堆叠鳞甲。铜胸护、釉背、圆肩、袖筒和钟形外裳都是连续曲面，前中只有一幅完整雨滴蔽膝作为局部硬层，肩上没有多层护片。冠前听雨璧为一整件薄铜饰，后颈衬也只是一幅织物。细铜弧线与袖口横向收边负责节奏，不把纹理接缝误当作分片。",
        parts={
            "head":dict(name="雨霁听山冠",description="温铜后壳保护后脑，低圆青瓷冠顶完整覆住头顶，全围浅铜束圈在额头上方收边。前冠一件舒展的听雨璧构成圆润负空间，两枚短垂绿石贴近耳侧，不垂成戏曲流苏墙。背冠一笔铜弧及烟梅护衬让后视同样完整。"),
            "chest":dict(name="听雨青瓷仪甲",description="米白内衣、浅青短衣与弧形温铜胸护组成软硬三层，圆肩及青瓷后背连续转接；宽袖有明确的钟口厚度、暗衬及贴腕内筒，双手自由显露。披领前后均有真实体积，腰用栗革细封束住，少量雨痕停留在胸部下侧。"),
            "legs":dict(name="涵露钟形长裳",description="接近全周的浅青曲面长裳下放至脚踝上方，烟梅内层在前开口和低边显露。正中青瓷雨滴蔽膝逐步收尖，后中单一长褶把体量导回中心，靠近下摆的两道矿绿弧线很淡。米白完整裤衬保证开口与侧面没有缺失的腿部覆盖。"),
            "boots":dict(name="青釉踏雨履",description="圆头雨履有完整足底、饱满但不过大的前掌、贴合踝部与小腿的青瓷护壳。栗褐靴底只占低部一条厚边，米白内口从釉面靴筒稍稍露出，一条水滴铜线环抱脚背；长袍遮住一部分靴筒时，足尖仍能清楚辨认。")},
        ingot=dict(name="温雨细绿青铜锭",description="宽三十六点五、高七点四、深二十一点一厘米，保留低扁削角轮廓；铜顶不做平铺噪点，只用疏密不同的双涟纹、单滴浅印、孔雀石包体和背侧釉窗构成独立材质辨识。铜、浅金边、釉与矿石分别有温哑、细亮、润滑和颗粒般的反光。",components=ig.components),
        materials=materials,components=g.components)


def chixia():
    materials = {
        "copper":mat("赤霞锻铜 · 甲壳与锭体", "#9B473D", .79, .43),
        "gold":mat("浅赤金嵌边 · 轮廓与主脊", "#D9AD71", .8, .37),
        "redsilk":mat("深绛霞纹绫 · 披肩与前后裾", "#612238", .0, .81),
        "ivory":mat("暖象牙绢 · 立领与完整裤身", "#E2D4B8", .03, .68),
        "dark":mat("乌赤硬革 · 腰封与靴底", "#392A2B", .06, .83),
        "jade":mat("橙红霞髓 · 冠心与锭内含物", "#DE8053", .22, .24),
        "grain":mat("暗玫铜晶脉 · 低对比刻纹", "#B4655A", .62, .43),
    }
    g=Geometry()
    g.loft("cx_body", "暖绢全围立领衣底", "chest", "ivory",
           [(1.008,.15,.114,0,0),(1.17,.17,.121,0,0),(1.36,.202,.124,0,0),
            (1.425,.184,.111,0,0),(1.485,.076,.075,0,0)],thickness=.006)
    g.loft("cx_cuirass", "完整赤铜收腰甲壳", "chest", "copper",
           [(1.039,.16,.124,0,0),(1.142,.176,.132,0,0),(1.28,.204,.144,0,.002),
            (1.378,.214,.134,0,0),(1.428,.194,.119,0,0)],thickness=.012)
    g.loft("cx_collar", "绛绫贴颈内领", "chest", "redsilk",
           [(1.445,.08,.078,0,0),(1.513,.074,.074,0,0)],thickness=.005)
    g.ribbon("cx_collar_v", "赤金收颈双曲披领", "chest", "gold",
             [[-.165,1.426,.075],[-.103,1.402,.115],[0,1.315,.15],
              [.103,1.402,.115],[.165,1.426,.075]],
             [.025,.03,.019,.03,.025],normal=[0,.2,1],thickness=.008)
    g.ribbon("cx_center_ridge", "从领至腰的连续霞脊", "chest", "grain",
             [[0,1.325,.154],[0,1.25,.148],[0,1.146,.139],[0,1.073,.129]],
             [.013,.019,.016,.008],thickness=.005)
    for side,s in (("l",-1),("r",1)):
        word="左" if s<0 else "右"
        g.loft(f"cx_sleeve_{side}",f"{word}完整暖绢窄袖", "chest", "ivory",
               [(.831,.044,.048,s*.35,.02),(.988,.052,.058,s*.332,.009),
                (1.10,.063,.068,s*.314,.002),(1.166,.071,.075,s*.3,0),(1.338,.081,.085,s*.258,0),
                (1.438,.073,.078,s*.23,0)],thickness=.006)
        g.loft(f"cx_upperarm_{side}",f"{word}沿肘上收的连续臂壳", "chest", "copper",
               [(1.176,.078,.085,s*.298,.003),(1.241,.082,.09,s*.283,.003),
                (1.341,.089,.095,s*.257,.003),(1.43,.086,.095,s*.231,.002)],thickness=.01)
        g.loft(f"cx_shoulder_{side}",f"{word}低伏连锁骨肩壳", "chest", "copper",
               [(1.332,.079,.09,s*.27,0),(1.402,.096,.103,s*.241,-.002),
                (1.448,.089,.097,s*.225,-.005),(1.476,.052,.069,s*.204,-.009),
                (1.491,.023,.042,s*.184,-.009)],thickness=.009,capTop=True)
        g.ribbon(f"cx_shoulder_rise_{side}",f"{word}贴肩向锁骨接续的霞翎", "chest", "copper",
                 [[s*.115,1.413,.116],[s*.195,1.451,.088],[s*.279,1.456,.009],
                  [s*.327,1.416,-.076]],
                 [.032,.048,.052,.009],normal=[0,.65,1],thickness=.007)
        g.loft(f"cx_forearm_{side}",f"{word}深绛护臂底套", "chest", "redsilk",
               [(.834,.048,.053,s*.351,.021),(.984,.054,.06,s*.333,.01),
                (1.12,.061,.067,s*.31,0)],thickness=.006)
        g.loft(f"cx_bracer_{side}",f"{word}肘腹外放腕口内收铜臂甲", "chest", "copper",
               [(.873,.059,.066,s*.35,.024),(.935,.062,.069,s*.344,.018),
                (1.013,.07,.077,s*.329,.01),(1.069,.077,.084,s*.319,.004),
                (1.112,.075,.081,s*.313,.002)],thickness=.009)
        g.oval(f"cx_wrist_rim_{side}",f"{word}腕口金线", "chest", "gold",
               s*.35,.88,.024,.06,.067,.0023)
        g.ribbon(f"cx_bracer_inlay_{side}",f"{word}护腕细霞脊", "chest", "gold",
                 [[s*.316,1.099,.087],[s*.325,1.042,.093],[s*.343,.946,.089],[s*.351,.888,.092]],
                 [.003,.007,.006,.003],thickness=.003)
        g.tube(f"cx_chest_ray_{side}",f"{word}胸侧内收云弧", "chest", "gold",
               [[s*.173,1.346,.082],[s*.124,1.281,.117],[s*.067,1.23,.141],
                [s*.042,1.166,.139]],.0024)
    g.loft("cx_belt", "乌赤革全围腰封", "chest", "dark",
           [(.982,.159,.124,0,0),(1.057,.158,.122,0,0)],thickness=.01)
    g.oval("cx_belt_top", "腰封全围浅金上缘", "chest", "gold",0,1.053,0,.162,.126,.0024)
    g.panel("cx_buckle", "朝霞折角腰扣", "chest", "gold",
            [[-.042,.01],[-.022,.026],[0,.012],[.022,.026],[.042,.01],
             [.025,-.021],[0,-.03],[-.025,-.021]],
            position=[0,1.016,.13],depth=.01,bevel=.0017)
    g.panel("cx_buckle_inset", "腰扣红铜内嵌", "chest", "copper",
            [[-.023,.006],[0,.015],[.023,.006],[.014,-.013],[0,-.019],[-.014,-.013]],
            position=[0,1.017,.142],depth=.004,bevel=.001)
    # A transversely draped collar leaves the continuous metal back visible.
    g.ribbon("cx_mantle", "完整深绛燕形后披领", "chest", "redsilk",
             [[-.217,1.421,-.082],[-.133,1.398,-.129],[0,1.353,-.154],
              [.133,1.398,-.129],[.217,1.421,-.082]],
             [.048,.117,.205,.117,.048],normal=[0,0,-1],thickness=.008)
    g.ribbon("cx_dorsal_ridge", "后披领连续赤铜燕脊", "chest", "copper",
             [[0,1.439,-.112],[0,1.302,-.16],[0,1.176,-.143],[0,1.082,-.127]],
             [.024,.045,.03,.006],normal=[0,0,-1],thickness=.006)
    for side,s in (("l",-1),("r",1)):
        g.tube(f"cx_dorsal_ray_{side}", "后肩单道浅金霞线"+side, "chest", "gold",
               [[s*.2,1.412,-.078],[s*.146,1.336,-.128],
                [s*.078,1.223,-.15],[s*.026,1.122,-.166]],.0026)
    pants(g,"ivory","cx",thigh=.086,calf=.061,hem=.19)
    next(c for c in g.components if c["id"]=="cx_hip")["rings"][0].update(y=.86,rx=.17,rz=.11)
    g.ribbon("cx_front_tabard", "完整上窄下尖赤霞前裾", "legs", "redsilk",
             [[0,.999,.136],[0,.849,.171],[0,.657,.196],[0,.451,.192]],
             [.232,.27,.228,.008],thickness=.009)
    for side,s in (("l",-1),("r",1)):
        g.tube(f"cx_front_edge_{side}", "前裾连续金压边"+side, "legs", "gold",
               [[s*.115,.992,.142],[s*.134,.849,.178],
                [s*.112,.656,.202],[0,.454,.199]],.0028)
        g.ribbon(f"cx_back_tail_{side}", "左右完整外翻长后裾"+side, "legs", "redsilk",
                 [[s*.094,.995,-.112],[s*.145,.813,-.149],
                  [s*.208,.605,-.181],[s*.239,.387,-.194]],
                 [.149,.176,.169,.025],normal=[0,0,-1],thickness=.008)
        g.ribbon(f"cx_back_tail_lining_{side}", "长后裾暖绢内衬"+side, "legs", "ivory",
                 [[s*.094,.983,-.105],[s*.145,.813,-.142],
                  [s*.208,.605,-.174],[s*.239,.399,-.187]],
                 [.138,.164,.157,.018],normal=[0,0,-1],thickness=.003)
        g.tube(f"cx_back_tail_edge_{side}", "外翻后裾金缘"+side, "legs", "gold",
               [[s*.163,.983,-.12],[s*.218,.794,-.157],
                [s*.275,.587,-.189],[s*.239,.39,-.2]],.0027)
        # Only a pair of complete hip leaves is layered over the garment.
        g.panel(f"cx_hip_leaf_{side}", "侧髋单体铜护叶"+side, "legs", "copper",
                [[-.038,.079],[.038,.067],[.046,-.029],[.007,-.107],[-.035,-.041]],
                position=[s*.178,.89,.025],rotation=[0,s*68,s*7],depth=.011,
                trimMaterial="gold",trimRadius=.0018)
        g.ribbon(f"cx_trouser_side_{side}", "裤侧绛色收线"+side, "legs", "redsilk",
                 [[s*.176,.919,.017],[s*.173,.741,.011],[s*.161,.55,.026],
                  [s*.155,.374,.014]],
                 [.027,.03,.023,.017],normal=[s,0,.3],thickness=.004)
    g.panel("cx_front_cloud", "前裾一枚上升云火纹", "legs", "grain",
            [[-.022,.037],[-.015,.008],[-.038,.014],[-.018,-.016],
             [0,-.043],[.018,-.016],[.038,.014],[.015,.008],[.022,.037],[0,.02]],
            position=[0,.772,.189],depth=.002,bevel=.0006)
    for side,x in (("l",-.095),("r",.095)):
        g.loft(f"cx_boot_{side}", "完整高束赤铜护履"+side, "boots", "copper",
               [(.034,.067,.139,x,.052),(.077,.069,.143,x,.054),(.137,.06,.096,x,.016),
                (.19,.066,.079,x,.004),(.27,.072,.077,x,0),(.402,.075,.082,x,-.003)],
               thickness=.009,capBottom=True)
        g.loft(f"cx_sole_{side}", "乌赤整底"+side, "boots", "dark",
               [(.001,.069,.143,x,.052),(.037,.071,.146,x,.052)],capTop=True,capBottom=True)
        g.loft(f"cx_boot_cuff_{side}", "深绛靴口软领"+side, "boots", "redsilk",
               [(.376,.077,.084,x,-.003),(.423,.075,.08,x,-.003)],thickness=.005)
        g.oval(f"cx_boot_rim_{side}", "靴口细金整缘"+side, "boots", "gold",
               x,.405,-.003,.077,.084,.0025)
        g.ribbon(f"cx_toe_{side}", "轻挑完整云头履尖"+side, "boots", "copper",
                 [[x,.08,.158],[x,.097,.204],[x,.135,.221],[x,.157,.223]],
                 [.071,.062,.039,.009],normal=[0,1,0],thickness=.008)
        g.tube(f"cx_shin_ridge_{side}", "护履主脊直达云头"+side, "boots", "gold",
               [[x,.379,.078],[x,.256,.072],[x,.14,.105],
                [x,.105,.196],[x,.16,.224]],.0028)
    g.loft("cx_head_rear", "赤铜完整后盔壳", "head", "copper",
           [(1.592,.085,.085,0,-.015),(1.66,.105,.112,0,0),(1.754,.112,.115,0,0)],
           startAngle=153,endAngle=387,thickness=.009)
    g.loft("cx_head_cap", "赤铜束发冠覆顶", "head", "copper",
           [(1.749,.112,.114,0,0),(1.806,.09,.095,0,-.004),
            (1.846,.048,.059,0,-.008),(1.855,.014,.019,0,-.009)],
           thickness=.009,capTop=True)
    g.panel("cx_brow", "朝霞向上额弧", "head", "gold",
            [[-.106,0],[-.092,.029],[-.049,.034],[0,.061],[.049,.034],
             [.092,.029],[.106,0],[.057,.008],[0,.027],[-.057,.008]],
            position=[0,1.753,.107],depth=.008,bevel=.0015)
    # 扁长的矢状霞脊由覆顶向后连续起势，既非孤立竖刃也非头顶插片。
    g.loft("cx_crown_blade", "贴冠向后起势的连续霞脊", "head", "copper",
           [(1.792,.025,.084,0,-.022),(1.828,.026,.11,0,-.056),
            (1.858,.02,.109,0,-.089),(1.89,.011,.07,0,-.119),
            (1.903,.0045,.01,0,-.148)],thickness=.003,capTop=True)
    g.ribbon("cx_crown_groove", "沿霞脊后收的窄霞髓嵌槽", "head", "jade",
             [[0,1.803,.067],[0,1.844,.038],[0,1.889,-.06],[0,1.905,-.148]],
             [.004,.007,.005,.002],thickness=.002)
    for side,s in (("l",-1),("r",1)):
        g.ribbon(f"cx_temple_{side}", "整铸短颊护"+side, "head", "copper",
                 [[s*.1,1.753,.057],[s*.11,1.696,.036],[s*.097,1.634,.028]],
                 [.031,.039,.01],normal=[s*.7,0,1],thickness=.008)
        g.tube(f"cx_head_rim_{side}", "后冠侧缘金线"+side, "head", "gold",
               [[s*.099,1.755,-.049],[s*.102,1.677,-.054],[s*.077,1.603,-.065]],.0024)
    g.ribbon("cx_nape_silk", "绛绫整幅护颈", "head", "redsilk",
             [[0,1.658,-.119],[0,1.574,-.131],[0,1.504,-.112]],
             [.102,.113,.051],normal=[0,0,-1],thickness=.006)
    ig=Geometry()
    ig.add("cx_ingot_body","赤霞低扁锻铜锭","ingot","copper","ingot",
           size=[.378,.074,.202],topMaterial="copper",edgeMaterial="gold")
    for i,(offset,length) in enumerate([(-.052,.112),(0,.133),(.052,.105)]):
        ig.tube(f"cx_ingot_ray_{i}","锭顶上升锻纹"+str(i),"ingot","grain",
                [[-length,.039,offset*.6-.012],[-.045,.039,offset-.01],
                 [.014,.039,offset+.008],[length*.7,.039,offset*.65+.025]],.0022)
    ig.panel("cx_ingot_seal", "朝霞浅金弧印", "ingot", "gold",
             [[-.024,-.009],[-.013,.01],[0,.019],[.013,.01],[.024,-.009],
              [.01,-.002],[0,.007],[-.01,-.002]],
             position=[-.016,.039,-.007],rotation=[-90,0,0],depth=.0017,bevel=.0005)
    ig.add("cx_ingot_heart","前侧橙红霞髓内含物","ingot","jade","gem",
           radii=[.025,.013,.008],position=[.063,.001,.1],rotation=[0,18,11])
    ig.tube("cx_ingot_split","霞髓延伸锻缝","ingot","grain",
            [[.113,.013,.096],[.089,.004,.1],[.055,-.006,.102],[.022,-.009,.103]],.0022)
    return dict(id="qi_chixia",name="赤霞护道",level=1,realm="炼气",style="昂扬守正",
        tagline="朝霞上升而不燃遍全身：赤铜整甲、贴伏肩线与深绛长裾。",
        palette=["#9B473D","#D9AD71","#612238","#E2D4B8","#DE8053"],
        description="赤霞护道为炼气三款中最昂扬的一套。赤铜胸甲采用完整围身的收腰护壳，暖象牙窄袖和裤身从硬甲之间显露，深绛前裾与外翻长后裾构成礼仪感。双肩只有各一片顺锁骨贴伏的短霞翎，浅金线顺着肩头、胸侧与护履主脊聚拢，视觉上向上生长，却没有全身燃烧或大面积发光。贴冠后起的霞脊把额顶与后脑连成一道低而有速度的曲线，后披领与赤铜燕脊给背影一个稳定的中心，穿戴者像初授护道职责的年轻仙门弟子，而非红色将军或乡野武人。",
        silhouette="上身比墨青更挺拔，肩壳压低并向锁骨内接，两片短霞翎从前胸转过肩峰后收向背侧，外缘不再成为两只独立高鼓包；护臂在肘下外放，沿前臂向腕口逐步收紧，硬甲与软肘带形成可读的关节余量。腰封以下，完整前裾从宽肩式上缘收为膝旁一尖；后方两幅长裾从髋部向两侧展开，暖绢内衬在侧背翻出，使前短后长的深绛轮廓有真实翻面。双腿是连续暖白裤身，高靴升至小腿中部，轻挑的云头鞋尖回应上扬冠饰。最高点是约一米九的连续霞脊，前根贴住覆顶，宽厚中段向后展开后收细；侧面有真实深度，正面不再是一把直插头顶的竖刃。",
        materialStory="赤霞锻铜锭保留低扁削角轮廓，铜顶三道方向一致、长度不同的锻纹像朝霞向外舒展；中央一枚浅金弧印，前侧嵌一小处橙红霞髓，矿脉顺着侧缘延伸。成衣将赤铜用于完整胸壳、收放臂壳、冠盔与高履，浅赤金只承担窄边线和扣具，赤铜使用较宽的磨锻反光，深绛织物保持低金属度与高粗糙度，暗玫铜晶脉成为胸脊与前裾低对比云纹。深绛绫负责柔性披领与衣摆，暖象牙细绢负责贴身衣袖和裤身，乌赤硬革负责腰封、足底及视觉收束。霞髓只有后收冠槽和锭包体，没有把每处关节都换成大宝石，也没有声称其具有已实现的战斗能力。",
        progression="这套表达炼气修士开始承担宗门礼仪与守护职责的仪容变化，同境中选择更明确的硬甲比例，而非跳到金丹太阳神的华丽程度。升华来自完整曲面护壳、方向统一的浅金嵌线、精巧云头履及前后衣摆的翻衬结构，材料仍可触摸、重量仍可理解。后续境界有足够空间将朝霞发展为日曜或神性天象，本套不占用背轮、悬浮圣冠、透明法衣和满身粒子的视觉额度。",
        segmentation="整套主体由连围胸壳、整袖、整护臂和几幅连续裁出的绛绫构成。局部层次只出现在双肩各一片霞翎、双髋各一片独立铜护叶；两对小辅层分别帮助锁骨转入肩背与髋部转折，不延伸成排甲。前后裾是宽幅衣料及其内衬，金色线是压边或嵌线，均不靠大量分片填满身体。",
        parts={
            "head":dict(name="朝霞束云冠",description="赤铜后盔、连体覆顶与短颊护包覆后脑及太阳穴，前脸保留清晰开口。单体霞脊贴住头顶后连续向后起势，低厚前根、扁长中段与回收尖端形成一体曲面；中央只有一条窄霞髓槽，低额弧迎接前根，后颈绛绫护衬和左右金线补全背面，避免只画正面冠额。"),
            "chest":dict(name="护道赤霞礼甲",description="完整收腰铜壳环绕胸背，肩壳与上臂护筒沿中性人台自然外倾，暖绢内袖连续接至腕口。浅金披领在胸前收成低尖，双肩各一片贴伏霞翎从锁骨过渡至后肩；肘下饱满、腕口收紧的连续护臂取代直筒套管；背后燕形绛领横向舒展，中央赤铜燕脊下接完整金属后背，与肩背金线构成清楚后视。"),
            "legs":dict(name="绛云开襟护裳",description="暖象牙裤身包覆髋、大腿与小腿，一幅连贯前裾在膝上收尖，两幅带内衬的长后裾向侧背翻展。双髋仅各加一个完整小护叶，前裾一枚暗玫铜云火纹与大块安静绫面保持主次；腿部没有用叠片重新拼成人形。"),
            "boots":dict(name="金脊挑云高履",description="赤铜足壳从完整前掌连到小腿中段，乌赤靴底封实足底，绛色软靴领略高于铜口。足尖的云头是一条真实向上弯起的有厚度曲面，金脊从靴筒贯至云头收尖；鞋的背面仍是闭合的赤铜轮廓，而非仅一块前护胫。")},
        ingot=dict(name="霞髓赤铜锻纹锭",description="宽三十七点八、高七点四、深二十点二厘米的低扁削角锭。三道不同长短的上升锻纹、单枚浅金弧印和橙红霞髓包体共同建立独立识别；前侧暗玫铜脉与外缘浅金倒角延续衣甲的赤、金、绛层次，保持同族锭的实体重量和低平比例。",components=ig.components),
        materials=materials,components=g.components)


DESIGN_NOTES = {
    "qi_moqing": """### 构图取舍与前后侧阅读

最容易失败的方向是把“巡霄”做成深色窄袖武侠装，所以这里把主体改为能看见真实侧厚的胸背钢壳，而不是在布衣上贴一块小补丁。额弧、斜交领、腰扣、靴脊构成正面四个相距足够远的阅读节点，节点之间留完整的墨青面；背面的双肩银线和后中窄幅则是一条不同的视线组织。右肩保持安静，左肩只有一片薄银辅叶，侧面转动时会显现单点层次，而非整套叠片。前裾的尖端与后裳的低弧错开高度，观者不需要依赖颜色也能分清前后。

### 实体覆盖与材料用途

胸背壳内先放全围绫衣，领口是独立贴颈曲面。两臂沿肩、肘、腕位置改变截面中心，护腕有完整环围；裤衬从髋到靴口连续，靴面封底并向踝部收窄，后视没有裸露的小腿。头部后壳覆盖后脑，覆顶以连续截面向上收拢，额部和两侧短护颊让它成为可穿戴的小盔冠。主材是冷深墨钢，辅助材料按软硬分工，不让一块浅玉或银边变成满身的高亮斑。

### 与另外两款及后境的关系

墨青强调竖向与窄长，侧背长裳像一条安静云迹。青铜的圆润钟袖与赤霞的抬肩长尾都不应直接复制到本款。炼气的仙气来自衣冠端整与精工尺度；后续尚可发展空间负形和灵材透光，这里没有预支背轮与悬浮结构。设计审美仍需最终统一摄影棚预览与用户观看确认，几何自检不替代接受。""",
    "qi_tingyu": """### 构图取舍与前后侧阅读

青铜并不天然显得修仙：大块发黑铜板会像墓俑，通身绿锈又会显脏。因此铜被保留为温润而有限的主胸面，青瓷肩背负责更干净的曲面反光，浅青绫与烟梅衬共同表达雨后山门的清润。圆肩、低冠、弧抱披领、雨滴蔽膝和圆头履保持同一语汇；不再加尖冠或战翼干扰。胸前只有两条低对比雨痕和一枚小印，背面一笔云涡与后中长褶足以建立识别，衣摆的大面维持疏朗。

### 宽袖、内层与真实体积

钟袖不是一张面片：外袖、内衬和贴腕筒是分别闭围的曲面，开口位置有铜细缘，手露在袖口下方。外裳接近全周，正前只有约四十四度开口，烟梅里层略收在外层之内，米白完整裤身与雨滴蔽膝处理开襟后的实际包覆。釉肩、腕护和靴子都是可从侧背观察的完整壳体，不能把人体人台当成甲壳替身。鞋头较圆，靴筒较低，为长袍下部保留自然重量。

### 精致程度与境界控制

这套用八种材料承担不同任务，颜色大面并不平均分配。浅青、温铜、青瓷先建立三块体量，烟梅只在开口出现，浅金细缘与细绿矿脉更小。灵材故事局限在锭体包体、釉面洁净与铜面雨纹，没有把“听雨”落实成音效、法术或属性。它是同境另一种仪容，并不比其他炼气款更强。最终仍须由核心渲染器统一预览，不可由结构通过推断审美通过。""",
    "qi_chixia": """### 构图取舍与前后侧阅读

赤霞最容易退化为泛用红金将军甲，所以本款压低正面大金属饰物的数量。浅金披领与两条胸侧云弧向腰部内收，肩头的两个短霞翎沿锁骨贴伏并转向后肩；深绛前裾则向下收尖，二者构成上下相向的节奏。背后不照抄胸口图案，而是短披领、单条赤铜燕脊和外翻双长裾；外裾深绛，翻面暖绢，侧视可以读出织物转折与轻重。

### 主体连续与局部辅层

胸甲是完整全围壳，肩、上臂与腕部都有闭合曲壳，肘下略饱满、腕口明确收紧。全身的局部辅叶只有双肩与双髋各一，属于明确的少数装饰或防护层；大片软料由连续宽幅曲面构成，不把前裾分成十几块再借“层次”掩饰。霞脊是一个由覆顶起势的完整铜体，低根宽厚、中段后展、末端收细，镶槽小而连贯。高履从足底到小腿均连续包覆，微挑云头的曲率重复冠、肩向上的姿态，提供仙门礼器的精细感。

### 色彩与升级余地

红铜偏沉，绛绫更深，暖象牙为腿和袖提供大块明面，金线只控制走向；因此不靠大光泽把整个人照成金色。锭包体的橙红霞髓只在冠心复现，胸口没有大宝石。赤霞强调初入仙门的昂扬职责，尚未达到金乌日曜或化神圣辉。神性空间、悬浮冠饰与透光法衣都留给后境，当前三款的差异由轮廓和裁切先成立。结构、渲染与最终审美的结论应分别报告。""",
}


def chinese_count(spec):
    fields = [spec[k] for k in ("description", "silhouette", "materialStory", "progression", "segmentation")]
    fields.extend(p["name"] + p["description"] for p in spec["parts"].values())
    fields.extend([spec["ingot"]["name"], spec["ingot"]["description"]])
    return len(re.findall(r"[\u3400-\u9fff]", "".join(fields)))


def polygon_is_simple(points):
    def orientation(a,b,c):
        return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
    n=len(points)
    for i in range(n):
        a,b=points[i],points[(i+1)%n]
        assert math.dist(a,b)>1e-8,"zero polygon edge"
        for j in range(i+1,n):
            if j in {i,(i+1)%n} or (j+1)%n in {i,(i+1)%n}:
                continue
            c,d=points[j],points[(j+1)%n]
            if orientation(a,b,c)*orientation(a,b,d)<-1e-16 and orientation(c,d,a)*orientation(c,d,b)<-1e-16:
                return False
    area=sum(points[i][0]*points[(i+1)%n][1]-points[(i+1)%n][0]*points[i][1] for i in range(n))/2
    return abs(area)>1e-7


def validate(designs):
    assert len(designs)==3
    assert len({s["id"] for s in designs})==3
    all_stats=[]
    for spec in designs:
        assert spec["realm"]=="炼气" and spec["level"]==1
        assert set(spec["parts"])==ROLES
        assert len(spec["materials"])>=5
        assert chinese_count(spec)>=500
        full=spec["components"]+spec["ingot"]["components"]
        ids=[c["id"] for c in full]
        assert len(ids)==len(set(ids)),spec["id"]+": duplicate component id"
        assert 35<=len(spec["components"])<=120
        assert {c["role"] for c in spec["components"]}==ROLES
        assert all(c["role"]=="ingot" for c in spec["ingot"]["components"])

        def numeric(value):
            if isinstance(value,(int,float)) and not isinstance(value,bool):
                assert math.isfinite(value)
            elif isinstance(value,(list,tuple)):
                for v in value: numeric(v)
            elif isinstance(value,dict):
                for v in value.values(): numeric(v)
        numeric(spec)
        for key,m in spec["materials"].items():
            assert re.fullmatch(r"#[0-9a-fA-F]{6}",m["color"]),key
            for field in ("metalness","roughness","opacity"):
                if field in m: assert 0<=m[field]<=1
        for c in full:
            ident=c["id"]
            assert c["shape"] in SHAPES,ident
            assert c["material"] in spec["materials"],ident
            for key in ("trimMaterial","topMaterial","edgeMaterial"):
                if key in c: assert c[key] in spec["materials"],ident
            for key in ("position","rotation","scale"):
                if key in c: assert len(c[key])==3,ident
            if c["shape"]=="loft":
                assert len(c["rings"])>=2,ident
                assert all(r["rx"]>0 and r["rz"]>0 for r in c["rings"]),ident
                assert all(a["y"]<b["y"] for a,b in zip(c["rings"],c["rings"][1:])),ident
                assert 0<c.get("endAngle",360)-c.get("startAngle",0)<=360,ident
                assert c["thickness"]<min(min(r["rx"],r["rz"]) for r in c["rings"]),ident
            if c["shape"] in {"tube","ribbon"}:
                assert len(c["path"])>=2,ident
                assert all(len(p)==3 for p in c["path"]),ident
                assert all(math.dist(a,b)>1e-7 for a,b in zip(c["path"],c["path"][1:])),ident
                for key in ("widths","radii"):
                    if key in c:
                        assert len(c[key])==len(c["path"]) and min(c[key])>0,ident
                if c["shape"]=="tube": assert c["radius"]>0,ident
            if c["shape"]=="panel":
                assert polygon_is_simple(c["polygon"]),ident
                assert c["depth"]>0 and c["bevel"]>=0,ident
                # Extrusion bevel stays subordinate to the narrowest edge.
                assert c["bevel"]<min(math.dist(a,b) for a,b in
                    zip(c["polygon"],c["polygon"][1:]+c["polygon"][:1]))/2,ident
            if c["shape"]=="ingot":
                assert c["size"][1]/c["size"][0]<=.25,ident
                assert .3<=c["size"][0]<=.4 and .18<=c["size"][2]<=.23,ident
        assert sum(c["shape"]=="ingot" for c in spec["ingot"]["components"])==1
        # Neutral pose coverage is checked against closed, continuous lofts, not colors.
        prefix=spec["components"][0]["id"].split("_")[0]
        byid={c["id"]:c for c in spec["components"]}
        for side in ("l","r"):
            sleeve=byid[f"{prefix}_sleeve_{side}"]
            assert sleeve["rings"][0]["y"]<=.93 and sleeve["rings"][-1]["y"]>=1.43
            trouser=byid[f"{prefix}_trouser_{side}"]
            assert trouser["rings"][0]["y"]<=.19 and trouser["rings"][-1]["y"]>=.95
            boot=byid[f"{prefix}_boot_{side}"]
            assert boot["capBottom"] and boot["rings"][0]["y"]<=.04
            sole=byid[f"{prefix}_sole_{side}"]
            assert sole["capBottom"] and sole["capTop"]
            assert boot["rings"][-1]["y"]>trouser["rings"][0]["y"]
        hip=byid[f"{prefix}_hip"]
        assert hip["rings"][0]["y"]<.94 and hip["rings"][-1]["y"]>1.02
        all_stats.append(dict(id=spec["id"],name=spec["name"],
            armor=len(spec["components"]),ingot=len(spec["ingot"]["components"]),
            roles=dict(Counter(c["role"] for c in spec["components"])),
            shapes=dict(Counter(c["shape"] for c in full)),
            materials=len(spec["materials"]),chineseCharacters=chinese_count(spec)))
    return all_stats


def documentation(designs,stats):
    out=["# 炼气三器 · 连续曲面的仙门仪容\n",
         "本稿只负责第1境三套真实三维锭与防具概念，不进入游戏，不设定玩法数值。坐标、role、材质和形状遵循冻结的 [CONTRACT.md](../CONTRACT.md)；几何由[作者脚本](../authors/level1.py)唯一编写并输出。\n",
         "## 设计判断\n",
         "炼气不应该用破布、补丁或贫乏方板表达“初级”。初入仙门仍应有完整冠服、精致器物和能被前后侧识别的剪影。三个方向分别从窄长折线、圆润钟形、上升礼甲成立；深色、浅青与赤绛只加强既有几何差异。主体由完整曲面支撑，小范围辅叶只放在确有轮廓作用的位置。全系列不采用背轮、aura角色、发光材料或粒子。\n",
         "| 套装 | 轮廓语汇 | 核心材质 | 防具组件 | 锭组件 |\n|---|---|---|---:|---:|\n"]
    for d,s in zip(designs,stats):
        out.append(f"| {d['name']} | {d['style']} | {d['materials'][next(iter(d['materials']))]['name']} | {s['armor']} | {s['ingot']} |\n")
    for d,s in zip(designs,stats):
        out.extend([f"\n## {d['name']}\n",f"{d['tagline']}\n"])
        for title,key in (("整体定位","description"),("比例、轮廓与背面","silhouette"),
                          ("材料与低扁锭的联系","materialStory"),
                          ("炼气的升级边界","progression"),("局部分片说明","segmentation")):
            out.extend([f"\n### {title}\n",d[key]+"\n"])
        out.append("\n### 四件与锭\n")
        for role in ("head","chest","legs","boots"):
            p=d["parts"][role]
            out.append(f"\n**{p['name']}**（{role}）：{p['description']}\n")
        out.append(f"\n**{d['ingot']['name']}**：{d['ingot']['description']}\n")
        out.append("\n### 材质角色\n\n| 材质键 | 名称与用途 | 色值 | 金属度 | 粗糙度 |\n|---|---|---|---:|---:|\n")
        for key,m in d["materials"].items():
            out.append(f"| {key} | {m['name']} | {m['color']} | {m['metalness']} | {m['roughness']} |\n")
        out.extend(["\n"+DESIGN_NOTES[d["id"]]+"\n",f"\n本套防具组件 {s['armor']}，锭组件 {s['ingot']}；四角色分布："+
            "、".join(f"{k} {v}" for k,v in s["roles"].items())+f"。JSON实质设计说明含汉字 {s['chineseCharacters']} 字。\n"])
    out.extend(["\n## 本地作者检查与接受边界\n",
        "`python3 art/immortal-armor-atlas/authors/level1.py --check` 只读回读并比较两个输出，同时执行独立结构检查。检查包括恰好三套、身份唯一、所有数值有限、材质引用、四个防具role、独立ingot角色、形状白名单、loft环序及厚度、路径宽度与控制点对应、panel轮廓非自交及倒角、全围袖裤与封底靴的中性姿态覆盖、每锭高宽比不超过0.25，以及每套不少于500汉字的实质说明。主衣甲全部使用loft/panel/ribbon/tube，未使用box或ellipsoid。\n",
        "这是设计几何与作者自检范围，不能替代统一渲染器的集成检查、人工多角度外观审稿或用户接受。最终统一摄影棚画面、交互试衣间、核心引擎构建及OBJ导出应由整合者依据实际执行结果分别报告；游戏运行、动作穿插、手感、音效、FPS和多人均为 `not_verified`，不在本次设计实现范围。\n"])
    return "\n".join(out)



def refine_fit(designs):
    # Fixed review rays showed the pants outside the back cloth at x=.14, y=.94.
    # Preserve the fitted waist, but derive the hip silhouette from both trouser legs.
    d=next(d for d in designs if d['id']=='qi_moqing');c={x['id']:x for x in d['components']}
    c['mq_longback']['rings']=[dict(y=y,rx=rx,rz=rz,cx=0,cz=cz,wave=wave) for y,rx,rz,cz,wave in [
        (.365,.222,.151,-.017,.004),(.56,.212,.15,-.009,.003),(.78,.205,.148,0,.001),
        (.90,.214,.151,0,.0005),(.98,.205,.145,0,0),(1.018,.163,.122,0,0)]]
    c['mq_back_lining']['rings']=[dict(y=y,rx=rx,rz=rz,cx=0,cz=cz) for y,rx,rz,cz in [
        (.378,.214,.145,-.017),(.63,.200,.141,-.009),(.82,.197,.137,0),
        (.95,.198,.136,0),(.985,.191,.132,0),(1.012,.158,.114,0)]]
    c['mq_back_spine']['path'][0][2]=-.155
    c['mq_back_spine']['path'][1][2]=-.155
    return designs


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check",action="store_true",help="验证并回读，不写文件")
    args=parser.parse_args()
    designs=refine_fit([moqing(),tingyu(),chixia()])
    stats=validate(designs)
    outputs={ROOT/"levels/level1.json":json.dumps(designs,ensure_ascii=False,indent=2)+"\n",
             ROOT/"levels/level1-design.md":documentation(designs,stats)}
    for path,content in outputs.items():
        if args.check:
            assert path.read_text(encoding="utf-8")==content,f"authored output drift: {path}"
        else:
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text(content,encoding="utf-8")
    print(json.dumps(dict(status="checked" if args.check else "written",designs=stats),
                     ensure_ascii=False,indent=2))


if __name__=="__main__":
    main()
