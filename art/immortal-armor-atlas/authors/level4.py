#!/usr/bin/env python3
"""元婴三式的独立 authored 输入；只生成本境 JSON 与中文设计说明。"""
from __future__ import annotations

import argparse
from collections import Counter
import json
import math
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ROLES = ("head", "chest", "legs", "boots")


def rounded(value):
    if isinstance(value, float):
        return round(value, 6)
    if isinstance(value, list):
        return [rounded(v) for v in value]
    if isinstance(value, dict):
        return {k: rounded(v) for k, v in value.items()}
    return value


def mat(name, color, metalness, roughness, opacity=1, emissive=None, intensity=0):
    result = dict(name=name, color=color, metalness=metalness, roughness=roughness)
    if opacity != 1:
        result["opacity"] = opacity
    if emissive:
        result.update(emissive=emissive, emissiveIntensity=intensity)
    return result


class Author:
    def __init__(self, spec):
        self.spec = spec
        self.components = spec["components"] = []
        self.ingot = spec["ingot"]["components"] = []

    def add(self, id, name, role, material, shape, **kw):
        item = dict(id=id, name=name, role=role, material=material, shape=shape, **kw)
        (self.ingot if role == "ingot" else self.components).append(item)
        return item

    def loft(self, id, name, role, material, rings, **kw):
        fields = ("y", "rx", "rz", "cx", "cz", "twist", "wave")
        return self.add(id, name, role, material, "loft",
                        rings=[dict(zip(fields, r)) for r in rings], **kw)

    def tube(self, id, name, role, material, path, radius=.003, **kw):
        if "radii" not in kw:
            kw["radius"] = radius
        return self.add(id, name, role, material, "tube", path=path,
                        radialSegments=8, steps=40, **kw)

    def ribbon(self, id, name, role, material, path, widths, **kw):
        return self.add(id, name, role, material, "ribbon", path=path,
                        widths=widths, steps=48, thickness=kw.pop("thickness", .004), **kw)

    def panel(self, id, name, role, material, polygon, position, **kw):
        return self.add(id, name, role, material, "panel", polygon=polygon,
                        position=position, depth=kw.pop("depth", .012),
                        bevel=kw.pop("bevel", .002), **kw)

    def gem(self, id, name, role, material, position, radii, **kw):
        return self.add(id, name, role, material, "gem", position=position, radii=radii, **kw)


def mirror(points, side):
    return [[side * p[0], *p[1:]] for p in points]


def ellipse(rx, rz, y, start=0, end=360, cx=0, cz=0, n=32):
    return [[cx + rx * math.cos(math.radians(start + (end-start)*i/n)), y,
             cz + rz * math.sin(math.radians(start + (end-start)*i/n))]
            for i in range(n+1)]


def text_spec(id, name, style, tagline, palette, materials, description, silhouette,
              story, progression, segmentation, parts, ingot):
    return dict(id=id, name=name, level=4, realm="元婴", style=style,
                tagline=tagline, palette=palette, description=description,
                silhouette=silhouette, materialStory=story, progression=progression,
                segmentation=segmentation, parts=parts, materials=materials, ingot=ingot)


def star():
    spec = text_spec(
        "yuanying_xinghe", "星河天衣", "负空间中的星河法衣",
        "深蓝如夜穹，银线绕体而行；衣在身上，星河在衣外。",
        ["#102445", "#244d78", "#c7dae8", "#6c9cbb", "#dce8ef"],
        {
            "night": mat("天河玄绡·连续承衣", "#102445", .12, .58),
            "blue": mat("沉星蓝金·整铸护面", "#244d78", .72, .24),
            "silver": mat("太微霜银·冠骨与星轨", "#c7dae8", .88, .23),
            "lining": mat("雾蓝里绫·开襟与靴口", "#6c9cbb", .02, .73),
            "veil": mat("离身星纱·后袖薄幕", "#759bbb", .04, .44, .32),
            "star": mat("凝星髓·少量星核", "#dce8ef", .28, .18, 1, "#8bbddd", .28),
            "ink": mat("陨铁墨芯·锭内暗层", "#142139", .67, .34),
        },
        "星河天衣的主景是完整的修长蓝衣与从身体表面逐渐离开的银色曲线。胸腹以一块圆润的蓝金护面承住衣身，肩部收而不耸，腰线高提，后襟向下延伸成有真实弧深的夜穹；没有用方板拼出胸和腹。正面在两条长襟之间留出窄而深的缝，能看见独立裤腿和完整靴面；背面则收成一条从后领到尾襟的星川。两袖的薄幕只在外后侧展开，前臂仍然由闭合袖筒保护，因此轻盈来自内实外虚的空间差，而不是没有主体。银线不绕成背后的统一光环：冠上是偏向后方的双弧，胸前是高低错落的天文弦，腰后才有一段离身轨道；这三种尺度之间有大片无纹的蓝色留白。",
        "人台头顶以上的双冠弧升到约二点一八米，宽度仍压在肩宽以内。肩、腰、长摆形成上窄、中收、下部轻微外展的竖向节奏；前襟开口只占前圆周约六十度，侧腰与整个后背都有连续表面。后摆比正面低，半透明外袖向后退，旋转到侧面时可见布、薄幕、银轨三个不同深度。前胸的细长护面沿胸骨走，不做西式大圆宝石，远看首先读到星川般的流线。",
        "凝星髓在陨铁中沉积成针点，再被沉星蓝金包住，低扁锭的上表面保留一条偏心星河和两条短轨刻线。银白不铺满所有边缘，只进入承力冠骨、衣襟边和星座连线；玄绡粗糙、蓝金光滑、星纱半透、星核微亮，四种不同响应让同样的深蓝产生厚薄差。锭上的五个细小星核在衣甲上分别转译为额心、胸侧、腰尾和后背的几处凝点，不是把同一图案反复贴到每一块甲上。这些是设计中的材质叙事，不代表已经实现了星空模拟或任何游戏能力。",
        "相较金丹境清晰的日月雷象，这里从单个天象转向身体周围的一小片空间：外袖与后轨有真实离身距离，冠弧和衣摆分别控制上下重心，正背的星图并不镜像复制。贵重感来自精确的曲率、暗亮面积比和连续衣甲的收放。它仍保留可辨的穿衣关系、腰结和实体靴底，尚未进入化神境的无相或整体消隐；未来提升应改变存在方式，而不是继续堆星点与大光圈。",
        "主体无鳞甲、无横排碎板。只把额心的整块冠额、胸骨上的狭长护面和两只袖口认作局部独立护件；它们都附着于连续冠壳或闭合衣袖，不靠叠片制造全身层次。星座点、细嵌线和外侧薄幕不是替代主体的拼片。",
        {
            "head": {"name": "太微游星冠", "description": "后脑由蓝金弧壳包住，头顶封成平缓穹面，额前留出面孔；左右银骨在脑后上升，顶端前后错开，形成可从侧面穿看的双弧冠。额心细长星髓藏在整块冠额内，耳后有连续护颊弦，冠背另有短星路，后视不是一条平带。"},
            "chest": {"name": "垂象天河衣", "description": "玄绡闭合包覆胸、背和双臂，蓝金长护面贴着胸骨下行，银弦沿肩窝绕向背部。外后袖是开口薄幕，与实心内袖之间留出可见空气；两条有宽度的披帛从肩后翻到外侧又落回衣身。背部连续穹襟中央有独立星座，细银长轴一直指向腰后的非闭合星轨。"},
            "legs": {"name": "分河垂星裳", "description": "双腿各有收束裤管，外罩一体开襟长裳和更低的后弧尾襟。前面两条收尖长襟覆盖大腿内侧，开口边缘有真实银色窄嵌线；侧面保留圆润体积，后摆是一整块弯曲星幕而非一排挂片。腰封呈低矮曲面，腰后偏心小星核与尾襟连成一条竖向叙事。"},
            "boots": {"name": "履斗潜星履", "description": "低靴以蓝金完整包覆脚背、后跟和踝部，长椭圆趾面向前伸出，内侧雾蓝靴口承接裤腿。深色薄底有独立体积，银色中脊从脚背回收到踝口，两侧只留一条弧形星路；不采用翘起刀尖或巨大的镶石脚头，保持长衣下方的轻而稳。"},
        },
        {"name": "沉星天河锭", "description": "削角低扁蓝金锭，宽三十六厘米、高六点四厘米，侧壁带陨铁暗层、倒角为冷银。上表面的偏心星河由真实细线与五枚微小凝星髓组成，双轨刻纹错向穿行，亮点间有足够暗面。细节贴着锭面并略微嵌入，不把锭做成带悬浮大环的祭坛。"},
    )
    a = Author(spec)
    a.loft("s_body", "玄绡闭合胸腰", "chest", "night", [(.96,.157,.117),(1.05,.153,.115),(1.24,.188,.131),(1.39,.217,.134),(1.46,.193,.116)], thickness=.007, segments=48)
    a.loft("s_shoulder_yoke", "曲面闭合肩轭与前领", "chest", "night", [(1.375,.247,.146),(1.43,.281,.143),(1.47,.263,.128),(1.49,.217,.10),(1.515,.078,.075)], thickness=.006, segments=48)
    a.loft("s_collar", "回折后领", "chest", "blue", [(1.43,.118,.096),(1.49,.091,.084),(1.565,.084,.080)], startAngle=155, endAngle=385, thickness=.008)
    a.loft("s_back", "完整夜穹背襟", "chest", "blue", [(1.005,.160,.125,0,-.006),(1.20,.198,.145,0,-.012),(1.39,.226,.147,0,-.006),(1.48,.155,.125,0,-.010)], startAngle=184, endAngle=356, thickness=.006, segments=36)
    a.panel("s_sternum", "天枢整铸长护面", "chest", "blue", [(-.035,-.18),(-.086,-.025),(-.104,.095),(-.052,.152),(0,.184),(.052,.152),(.104,.095),(.086,-.025),(.035,-.18),(0,-.222)], [0,1.262,.141], depth=.018, bevel=.009, trimMaterial="silver", trimRadius=.0024)
    a.ribbon("s_sternum_inlay", "胸心细长星川", "chest", "silver", [[0,1.435,.158],[-.016,1.37,.171],[.008,1.29,.171],[0,1.16,.158]], [.006,.012,.008,.003])
    a.loft("s_belt", "无碎片的曲面星带", "legs", "blue", [(.973,.164,.124),(1.003,.161,.122),(1.025,.158,.121)], thickness=.007, segments=48)
    a.loft("s_hip_lining", "开襟内连续包髋承袴", "legs", "lining", [(.85,.146,.108),(.90,.172,.113),(.947,.183,.118),(1.006,.161,.123)], thickness=.006, segments=40)
    a.loft("s_robe", "前开六十度完整外裳", "legs", "night", [(.16,.316,.226,0,-.015,0,.006),(.42,.285,.207,0,-.008,0,.004),(.70,.242,.175),(1.005,.165,.125)], startAngle=120, endAngle=420, thickness=.005, segments=60)
    a.loft("s_train", "弧面夜穹后摆", "legs", "blue", [(.095,.395,.299,0,-.024,0,.009),(.33,.337,.247,0,-.019,0,.007),(.64,.257,.187,0,-.012),(.99,.166,.129)], startAngle=202, endAngle=338, thickness=.005, segments=36)
    a.tube("s_back_axis", "后背天河中轴", "chest", "silver", [[0,1.48,-.135],[-.018,1.36,-.16],[.021,1.21,-.162],[0,1.04,-.137]], .0032)
    a.tube("s_orbit", "腰后离身偏轨", "chest", "silver", [[-.19,1.12,.02],[-.28,1.08,-.12],[-.20,1.05,-.255],[.02,1.11,-.285],[.235,1.20,-.17],[.26,1.27,.00]], .004)
    a.gem("s_orbit_star", "偏轨凝星", "chest", "star", [.26,1.27,0], [.011,.016,.011])
    for side, tag in [(-1,"l"),(1,"r")]:
        label = "左" if side < 0 else "右"
        a.loft(f"s_sleeve_{tag}", label+"闭合玄绡袖", "chest", "night", [(.855,.053,.061,side*.35,.016),(.98,.062,.068,side*.332,.006),(1.15,.074,.078,side*.303,0),(1.36,.081,.083,side*.254,0),(1.43,.075,.076,side*.232,0)], segments=36, thickness=.006)
        a.loft(f"s_mantle_{tag}", label+"离身薄星袖幕", "chest", "veil", [(.75,.117,.129,side*.354,-.054,0,.006),(.97,.137,.149,side*.33,-.033,0,.005),(1.19,.113,.117,side*.299,-.018),(1.43,.086,.09,side*.24,-.007)], startAngle=151, endAngle=389, thickness=.003, segments=36)
        a.loft(f"s_cuff_{tag}", label+"一体星银袖口", "chest", "blue", [(.85,.059,.067,side*.351,.017),(.882,.06,.068,side*.347,.015),(.912,.063,.069,side*.343,.012)], segments=32, thickness=.006)
        a.tube(f"s_shoulder_{tag}", label+"绕肩天文弦", "chest", "silver", mirror([[.055,1.42,.149],[.17,1.445,.114],[.239,1.45,.015],[.20,1.46,-.10],[.05,1.40,-.158]],side), .0033)
        scarf=([[.19,1.46,-.06],[.34,1.54,-.16],[.52,1.36,-.19],[.53,1.12,.03],[.43,.83,.14],[.43,.50,-.17]] if side<0 else [[.19,1.46,-.06],[.38,1.41,-.18],[.49,1.15,-.12],[.42,.92,.12],[.51,.66,.02],[.39,.44,-.17]])
        a.ribbon(f"s_scarf_{tag}", label+"错位回旋厚度披帛", "chest", "veil", mirror(scarf,side), [.038,.083,.075,.06,.042,.005], normal=[side*.3,0,1], thickness=.003)
        a.tube(f"s_scarf_edge_{tag}", label+"披帛中心银丝", "chest", "silver", mirror(scarf,side), .0016)
        a.loft(f"s_trouser_{tag}", label+"完整星绡裤管", "legs", "lining", [(.21,.056,.065,side*.095,0),(.45,.065,.075,side*.095,0),(.73,.077,.087,side*.095,0),(.96,.087,.098,side*.094,0)], thickness=.005, segments=32)
        a.ribbon(f"s_lapel_{tag}", label+"前襟完整垂星绶", "legs", "blue", mirror([[.068,1.0,.138],[.083,.82,.153],[.117,.54,.188],[.15,.245,.222]],side), [.12,.135,.12,.018], thickness=.005)
        a.tube(f"s_opening_{tag}", label+"开襟细银嵌线", "legs", "silver", mirror([[.0825,1.005,.1083],[.121,.70,.1515],[.1425,.42,.1713],[.158,.16,.1807]],side), .003)
        a.loft(f"s_sole_{tag}", label+"陨铁薄靴底", "boots", "ink", [(0,.060,.135,side*.095,.051),(.05,.064,.14,side*.095,.051)], thickness=.008, capBottom=True, capTop=True, segments=36)
        a.loft(f"s_boot_{tag}", label+"连续蓝金全包覆靴", "boots", "blue", [(.045,.060,.132,side*.095,.05),(.082,.061,.137,side*.095,.053),(.13,.056,.088,side*.095,.02),(.20,.052,.065,side*.095,0),(.285,.058,.071,side*.095,0)], thickness=.007, capBottom=True, segments=40)
        a.loft(f"s_boot_lip_{tag}", label+"雾蓝靴口内缘", "boots", "lining", [(.268,.060,.073,side*.095,0),(.289,.061,.074,side*.095,0)], thickness=.004)
        a.tube(f"s_boot_spine_{tag}", label+"足面银脊", "boots", "silver", mirror([[.095,.287,.073],[.095,.20,.071],[.095,.133,.113],[.095,.092,.187]],side), .0036)
        a.tube(f"s_boot_side_{tag}", label+"后跟回星线", "boots", "silver", mirror([[.15,.062,.13],[.157,.09,.035],[.146,.15,-.045],[.095,.22,-.067]],side), .0025)
    a.loft("s_skull", "蓝金后脑全弧壳", "head", "blue", [(1.565,.105,.111,0,-.008),(1.68,.119,.121,0,-.006),(1.78,.113,.118,0,-.006)], startAngle=158, endAngle=382, thickness=.007, segments=36)
    a.loft("s_head_cap", "整面弧顶冠壳", "head", "blue", [(1.767,.117,.12,0,-.007),(1.81,.093,.097,0,-.008),(1.842,.012,.018,0,-.01)], thickness=.006, capTop=True, segments=40)
    a.panel("s_forehead", "太微整体冠额", "head", "silver", [(-.104,.005),(-.089,.035),(-.035,.03),(0,.066),(.035,.03),(.089,.035),(.104,.005),(.054,-.012),(0,-.037),(-.054,-.012)], [0,1.767,.114], depth=.012, bevel=.003)
    a.gem("s_forehead_seed", "额心凝星髓", "head", "star", [0,1.79,.128], [.011,.027,.008])
    for side,tag in [(-1,"l"),(1,"r")]:
        a.tube(f"s_crown_{tag}", "太微错位主冠弧"+tag, "head", "silver", mirror([[.10,1.77,-.005],[.16,1.91,-.025],[.125,2.067,-.05],[.035,2.17,-.04]],side), radii=[.010,.009,.006,.0025])
        a.tube(f"s_crown_inner_{tag}", "后置短冠弦"+tag, "head", "blue", mirror([[.075,1.79,-.088],[.113,1.94,-.12],[.063,2.059,-.125]],side), radii=[.007,.006,.002])
        a.tube(f"s_cheek_{tag}", "连续耳侧护弦"+tag, "head", "silver", mirror([[.093,1.77,.062],[.121,1.70,.035],[.115,1.602,-.018],[.09,1.584,-.06]],side), .0045)
    a.gem("s_crown_tip", "双弧间悬星", "head", "star", [0,2.151,-.035], [.015,.026,.011])
    a.tube("s_head_back_map", "冠背小星路", "head", "silver", [[-.08,1.69,-.107],[0,1.76,-.13],[.075,1.71,-.11]], .002)
    a.tube("s_chest_constellation", "前胸五点天纪", "chest", "silver", [[-.08,1.38,.155],[-.044,1.322,.167],[.009,1.34,.176],[.047,1.29,.17],[.026,1.235,.165]], .0018)
    for i,p in enumerate([[-.08,1.38,.155],[-.044,1.322,.167],[.009,1.34,.176],[.047,1.29,.17],[.026,1.235,.165]]):
        a.gem(f"s_chest_star_{i}", "前胸天纪凝点"+str(i+1), "chest", "star", p, [.0045,.006,.0035])
    back_map=[[-.14,1.335,-.127],[-.063,1.38,-.151],[.032,1.337,-.165],[.096,1.24,-.145],[.025,1.19,-.167]]
    a.tube("s_back_constellation", "背部独立星图", "chest", "silver", back_map, .0019)
    for i,p in enumerate(back_map):
        a.gem(f"s_back_star_{i}", "后背天纪凝点"+str(i+1), "chest", "star", p, [.005,.007,.0038])
    a.tube("s_train_hem", "后摆一笔银弧", "legs", "silver", ellipse(.395,.299,.102,202,338,cz=-.024), .0025)
    a.gem("s_waist_seed", "腰后孤星髓", "legs", "star", [0,.985,-.145], [.008,.019,.006])
    a.add("s_ingot", "沉星低扁削角锭", "ingot", "blue", "ingot", size=[.36,.064,.20], topMaterial="blue", edgeMaterial="silver")
    a.tube("s_ingot_river", "偏心天河刻纹", "ingot", "silver", [[-.126,.033,-.046],[-.077,.034,-.024],[-.021,.035,-.03],[.042,.034,.011],[.112,.033,.032]], .0018)
    for i,p in enumerate([[-.126,.034,-.046],[-.077,.035,-.024],[-.021,.036,-.03],[.042,.035,.011],[.112,.034,.032]]):
        a.gem(f"s_ingot_seed_{i}", "锭中五凝星"+str(i+1), "ingot", "star", p, [.005,.004,.005])
    a.tube("s_ingot_orbit_a", "锭面短天轨一", "ingot", "ink", ellipse(.123,.060,.0328,18,157,cx=.003,cz=.004,n=22), .0016)
    a.tube("s_ingot_orbit_b", "锭面错向天轨二", "ingot", "silver", ellipse(.095,.056,.033,192,330,cx=-.025,cz=.004,n=22), .0014)
    a.tube("s_ingot_side", "侧壁陨铁暗层", "ingot", "ink", [[-.167,-.005,-.058],[-.177,-.005,0],[-.166,-.005,.060]], .002)
    return spec


def crystal():
    spectral = ["#ee9eb0","#eab27a","#e4d695","#a5dca6","#77cfbf","#82cadd","#9faee5","#ba9ed4","#d4b6d8"]
    materials = {
        "porcelain": mat("月瓷纤绫·不透明内衬", "#9eb5b8", .03, .63),
        "glass": mat("无色青琉璃·主体曲壳", "#8ac5c3", .12, .11, .43),
        "frost": mat("雾化乳琉璃·承力折缘", "#dcefe7", .16, .29, .88),
        "silver": mat("水铂·窄结构线", "#9fc3c3", .87, .20),
        "dark": mat("深潭青芯·阴影与靴底", "#234c56", .28, .43),
    }
    for i,color in enumerate(spectral):
        materials[f"spectrum{i+1}"] = mat("琉璃内含微谱·"+str(i+1), color, .14, .17, .74)
    spec = text_spec(
        "yuanying_jiuse", "九色琉光", "一体折射的琉璃仙装",
        "以无色为贵，九色藏在窄边与内含物中；悬挑披领托住一身清光。",
        ["#8ac5c3", "#dcefe7", "#234c56", "#9fc3c3", "#ba9ed4"], materials,
        "九色琉光采用折面而非碎片作为主要语言。胸腰是一整件带螺旋收势的琉璃承壳，内侧有月瓷色连续承衣，因此看得见半透明厚度，却不会把人台裸露当作透明设计。肩后展开的披领不是平面翅膀：它从靠身的低处翻起，在肩外形成悬挑，再沿后颈收回，旋转时会出现清晰的上下两道边。下装放弃星河的整圈长裳，保留修长的独立双腿，左右只有各一条宽幅悬垂曲襟，背中央则落下一片有真实弯曲深度的琉璃水瀑。它的贵重来自大面透明体与少数乳白磨砂承力边的对照；九种异色都缩在几毫米的边线或锭内晶核，不拼成九块彩色胸甲，也不为“九色”把全身涂成彩虹。",
        "这套的主轮廓上宽下收：披领在肩外挑出，头冠向后上方折展，腰下尽量空出双腿两侧。冠不是圆环，而是一块带凹口的连续折光冠扇；正面呈高低不等的两道峰线，侧面可见后倾的实体厚度。两只袖筒有受控的多边截面，外袖在肘上鼓起而腕部骤收。腿甲以长而整块的棱面沿膝胫延伸，脚背与踝面也延续十边折光；后瀑与双腿之间有距离，背部并未省略承壳与脊线。",
        "低扁琉璃锭保留削角金属锭的家族外形，内部九枚细长异色晶核沿一条曲折中线排列；上表面只有一笔折射刻纹与雾化斜脊。主材青琉璃是浅而冷的透明体，月瓷承衣不透明且偏灰，乳琉璃负责磨砂边，水铂负责真正细窄的连接线，深潭青仅用于关节阴影、靴底和少量后衬。九色微谱与这五类材质分开，避免把每个表面都赋予同一高光。当前冻结接口只表达透明度、金属度与粗糙度；所谓折射是造型与材质叙事，不能宣称浏览器已经实现色散、光学焦散或物理折射。",
        "相较金丹的金属灵器，这一方向把护具变为可透见内衬的整块灵性物质，并用离身悬挑、粗细不同的磨砂边和折面转角表现元婴的精微控制。比星河更外展、比长生更收敛，整体节奏是宽领、细腰、长腿，避免三套都成为拖地长袍。仍然保留肩、腕、腰和足底等穿戴落点，留给化神的提升是超越实体依附，而不是增加透明比例或把背后再放一个光轮。",
        "只在左右肩端各用一块小折翼，在胫前各用一块完整护面；这四处允许独立件增强厚薄层次。胸腹、冠扇、披领、袖筒和背瀑都是连续的整面结构，没有密集晶片、鳞片或全身镶嵌拼板。九枚异色内含物只是微小光学点缀，面积远小于主体。",
        {
            "head": {"name": "映界琉光冠", "description": "乳琉璃的后脑壳与头顶弧壳完整包覆，额面是一块前低中高的光滑冠额，面孔保留开口。冠扇后倾，边缘有两道不等高峰和中央透空凹口，单块轮廓以内再走一条极细水铂脊；耳后的护弧顺着头形向下收，不把尖晶片围满整张脸。"},
            "chest": {"name": "九曜承光衣", "description": "先用月瓷绫包住躯干与双臂，再以轻微扭转的琉璃曲壳覆盖胸腰。乳琉璃披领从肩胛翻起，左右两枚小折翼只是端部辅层；内外之间能看到真实负空间。胸骨上有一面窄而整体的水滴棱脊，背面则由完整护壳和弯曲的长瀑构成，背瀑的边线在腰后折出第二次方向变化。"},
            "legs": {"name": "悬镜分光袴", "description": "两腿保有完整不透明内袴与外覆琉璃长筒，膝前护面从大腿下部收尖到胫骨，不拆成一节节膝甲。两侧只有各一条有宽度变化的悬襟，髋部宽、下端细，前面留给双腿与行走的空间。踝上微色窄线延续锭内晶核的方向，而非把九色作成横向彩带。"},
            "boots": {"name": "涵光凝露靴", "description": "深潭青薄底托住月瓷色内靴，外层是一体十边琉璃靴壳；脚趾前伸，脚跟封闭，踝口包住裤管。窄磨砂中脊在脚背上形成高光折点，后跟有水铂闭合回线。透明外壳与不透明内芯相距数毫米，脚的体积、底厚和内外层关系在单件模式中依然成立。"},
        },
        {"name": "九曜涵光锭", "description": "宽三十七厘米、高六点八厘米的青琉璃低扁削角锭，顶部透见九枚不同色相的小晶核；它们沿锭的长轴错落，均藏在锭体内，不竖成水晶簇。磨砂斜脊和水铂短刻线分别呈现柔光与锐光，侧面保留整块琉璃的连续折面。"},
    )
    a=Author(spec)
    a.loft("g_body_inner", "月瓷完整胸腰承衣", "chest", "porcelain", [(.96,.153,.115),(1.08,.15,.116),(1.28,.188,.129),(1.43,.211,.129),(1.47,.181,.110)], thickness=.006, segments=40)
    a.loft("g_shoulder_inner", "完整月瓷斜肩承衣", "chest", "porcelain", [(1.37,.246,.139),(1.43,.277,.136),(1.472,.261,.119),(1.491,.216,.095),(1.52,.077,.074)], thickness=.006, segments=40)
    a.loft("g_body_shell", "一体扭转琉璃胸腰壳", "chest", "glass", [(.975,.166,.127,0,0,-6),(1.08,.160,.129,0,0,-2),(1.28,.201,.144,0,0,7),(1.43,.225,.142,0,0,12),(1.47,.193,.122,0,0,10)], thickness=.009, segments=12)
    a.loft("g_neck", "磨砂连续高领", "chest", "frost", [(1.455,.104,.091),(1.52,.078,.077),(1.565,.083,.079)], startAngle=151, endAngle=389, thickness=.008, segments=24)
    a.loft("g_collar", "后翻悬挑整面披领", "chest", "frost", [(1.385,.230,.152,0,-.01),(1.46,.374,.207,0,-.028),(1.55,.340,.190,0,-.032),(1.59,.249,.148,0,-.029)], startAngle=163, endAngle=377, thickness=.008, segments=40)
    a.panel("g_sternum", "单体滴形承光棱", "chest", "frost", [(0,.17),(-.054,.073),(-.066,-.025),(-.022,-.161),(0,-.20),(.028,-.145),(.067,-.006),(.046,.085)], [0,1.275,.157], depth=.022, bevel=.009, trimMaterial="silver", trimRadius=.0018, rotation=[0,0,-7])
    a.ribbon("g_sternum_fold", "胸棱窄青折光面", "chest", "glass", [[-.018,1.43,.182],[.014,1.31,.188],[-.005,1.20,.182],[0,1.10,.17]], [.012,.022,.018,.003], thickness=.007)
    a.loft("g_back_shell", "完整后背折光壳", "chest", "glass", [(1.02,.172,.143,0,-.01),(1.22,.207,.164,0,-.014),(1.43,.235,.162,0,-.01),(1.50,.155,.135,0,-.009)], startAngle=180, endAngle=360, thickness=.008, segments=14)
    waterfall=[[0,1.51,-.16],[.048,1.30,-.208],[-.046,1.05,-.235],[.055,.78,-.31],[.014,.51,-.40],[-.035,.29,-.37]]
    a.ribbon("g_waterfall", "完整离身琉璃背瀑", "chest", "glass", waterfall, [.13,.23,.21,.20,.16,.036], normal=[0,0,-1], thickness=.010)
    a.tube("g_back_spine", "背瀑单笔磨砂折脊", "chest", "frost", waterfall, radii=[.006,.008,.007,.006,.005,.002])
    a.loft("g_belt", "完整水铂腰缘", "legs", "silver", [(.974,.168,.129,0,0,-6),(1.005,.167,.13,0,0,-4)], segments=12, thickness=.006)
    a.loft("g_hip_inner", "髋部闭合月瓷承袴", "legs", "porcelain", [(.85,.146,.108),(.90,.172,.113),(.947,.183,.118),(1.006,.161,.123)], thickness=.006, segments=40)
    a.loft("g_hip_shell", "髋部完整琉璃短曲壳", "legs", "glass", [(.87,.159,.12,0,0,-4),(.94,.193,.13,0,0,-4),(.986,.174,.135,0,0,-6)], thickness=.008, segments=12)
    for side,tag in [(-1,"l"),(1,"r")]:
        label="左" if side<0 else "右"
        a.loft(f"g_inner_sleeve_{tag}", label+"闭合月瓷内袖", "chest", "porcelain", [(.85,.050,.06,side*.35,.016),(.99,.06,.067,side*.33,.007),(1.17,.071,.076,side*.297,0),(1.39,.076,.078,side*.245,0),(1.435,.071,.073,side*.231,0)], thickness=.005, segments=32)
        a.loft(f"g_sleeve_shell_{tag}", label+"全包折面琉璃外袖", "chest", "glass", [(.858,.059,.069,side*.35,.016,side*12),(1.02,.073,.083,side*.328,.007,side*8),(1.19,.088,.094,side*.294,0,side*2),(1.39,.089,.092,side*.245,0,-side*8),(1.44,.079,.083,side*.229,0,-side*8)], thickness=.007, segments=12)
        a.loft(f"g_over_sleeve_{tag}", label+"上臂悬空折光穹袖", "chest", "glass", [(1.03,.09,.104,side*.327,-.01,side*8),(1.18,.143,.137,side*.299,-.025,side*12),(1.36,.112,.108,side*.254,-.009,side*4),(1.445,.082,.085,side*.232,0)], startAngle=137, endAngle=403, thickness=.006, segments=14)
        a.loft(f"g_cuff_{tag}", label+"磨砂收口", "chest", "frost", [(.85,.062,.073,side*.35,.017),(.901,.065,.075,side*.344,.012)], thickness=.007, segments=12)
        wing=mirror([[-.085,-.04],[-.02,.07],[.102,.125],[.143,.07],[.072,-.01],[-.01,-.075]],side)
        a.panel(f"g_wing_{tag}", label+"肩端单块局部折翼", "chest", "glass", wing, [side*.29,1.52,-.119], rotation=[-8,side*24,0], depth=.018, bevel=.005, trimMaterial="frost", trimRadius=.003)
        a.tube(f"g_forearm_fold_{tag}", label+"前臂连续棱线", "chest", "silver", mirror([[.246,1.39,.092],[.30,1.19,.099],[.337,1.02,.09],[.35,.87,.089]],side), .0023)
        a.ribbon(f"g_side_lapel_{tag}", label+"完整悬镜侧襟", "legs", "glass", mirror([[.154,.991,.01],[.229,.87,.062],[.281,.665,.079],[.294,.43,.105]],side), [.106,.183,.145,.035], normal=[side*.4,0,1], thickness=.009)
        a.tube(f"g_lapel_rib_{tag}", label+"侧襟磨砂主脊", "legs", "frost", mirror([[.154,.991,.015],[.229,.87,.068],[.281,.665,.085],[.294,.43,.111]],side), radii=[.004,.005,.004,.002])
        a.loft(f"g_trouser_{tag}", label+"完整月瓷内袴", "legs", "porcelain", [(.19,.052,.062,side*.095,0),(.48,.063,.073,side*.095,0),(.73,.075,.084,side*.095,0),(.975,.086,.096,side*.094,0)], thickness=.005, segments=32)
        a.loft(f"g_leg_shell_{tag}", label+"完整长腿琉璃曲壳", "legs", "glass", [(.22,.062,.073,side*.095,0,side*5),(.49,.075,.086,side*.095,0,side*10),(.74,.086,.097,side*.095,0,side*4),(.965,.096,.107,side*.094,0,-side*4)], thickness=.007, segments=10)
        a.panel(f"g_greave_{tag}", label+"单体长棱胫甲", "legs", "frost", [(-.046,.23),(-.064,.11),(-.05,-.11),(-.018,-.26),(0,-.295),(.033,-.19),(.060,.09),(.04,.21),(0,.27)], [side*.095,.53,.095], rotation=[0,side*7,-side*3], depth=.014, bevel=.006, trimMaterial="silver", trimRadius=.0017)
        a.tube(f"g_leg_inset_{tag}", label+"胫面藏色细线", "legs", "spectrum7" if side<0 else "spectrum5", mirror([[.095,.75,.109],[.105,.58,.12],[.09,.37,.111],[.095,.27,.109]],side), .0024)
        a.loft(f"g_sole_{tag}", label+"深潭青闭合靴底", "boots", "dark", [(0,.059,.133,side*.095,.052),(.049,.062,.137,side*.095,.052)], thickness=.007, capBottom=True, capTop=True, segments=12)
        a.loft(f"g_boot_inner_{tag}", label+"不透明足部内芯", "boots", "porcelain", [(.048,.052,.126,side*.095,.052),(.083,.055,.127,side*.095,.052),(.135,.048,.083,side*.095,.021),(.26,.05,.063,side*.095,0)], thickness=.006, capBottom=True, segments=24)
        a.loft(f"g_boot_shell_{tag}", label+"完整十边琉璃靴壳", "boots", "glass", [(.047,.061,.137,side*.095,.053,side*5),(.083,.063,.14,side*.095,.054,side*3),(.139,.056,.095,side*.095,.022,side*8),(.23,.059,.074,side*.095,0,side*8),(.282,.06,.076,side*.095,0,side*8)], thickness=.007, capBottom=True, segments=10)
        a.tube(f"g_toe_ridge_{tag}", label+"脚背乳琉璃折脊", "boots", "frost", mirror([[.095,.28,.078],[.092,.19,.083],[.095,.128,.13],[.095,.085,.191]],side), radii=[.004,.005,.006,.002])
        a.tube(f"g_heel_{tag}", label+"后跟完整水铂回线", "boots", "silver", mirror([[.153,.067,.01],[.154,.14,-.038],[.095,.225,-.079],[.036,.14,-.038],[.037,.067,.01]],side), .0024)
    a.loft("g_skull", "乳琉璃后脑包覆壳", "head", "frost", [(1.565,.105,.11,0,-.007),(1.68,.118,.121,0,-.006),(1.782,.111,.117,0,-.006)], startAngle=157, endAngle=383, thickness=.007, segments=24)
    a.loft("g_cap", "冠顶完整琉璃弧壳", "head", "glass", [(1.77,.117,.123,0,-.004),(1.815,.094,.101,0,-.007),(1.847,.014,.018,0,-.01)], thickness=.006, capTop=True, segments=16)
    a.panel("g_brow", "整面映界冠额", "head", "frost", [(-.112,0),(-.075,.044),(-.026,.036),(.015,.072),(.065,.04),(.113,.012),(.074,-.016),(.015,-.035),(-.05,-.021)], [0,1.765,.119], depth=.018, bevel=.006, trimMaterial="silver", trimRadius=.0018)
    fan=[(-.125,-.04),(-.195,.055),(-.185,.16),(-.114,.276),(-.035,.172),(.022,.322),(.133,.22),(.203,.086),(.14,-.041)]
    a.panel("g_crown_fan", "单块后倾折光冠扇", "head", "glass", fan, [0,1.82,-.056], rotation=[-15,0,0], depth=.022, bevel=.006, trimMaterial="frost", trimRadius=.003)
    a.tube("g_crown_axis", "冠扇水铂折轴", "head", "silver", [[0,1.81,-.025],[.03,1.94,-.05],[-.012,2.047,-.082],[.022,2.13,-.115]], .0027)
    for side,tag in [(-1,"l"),(1,"r")]:
        a.tube(f"g_ear_{tag}", "琉璃连续耳后承弧"+tag, "head", "glass", mirror([[.092,1.78,.077],[.125,1.71,.041],[.118,1.615,-.023],[.082,1.59,-.078]],side), radii=[.009,.010,.008,.003])
        a.tube(f"g_crown_side_{tag}", "冠背回折窄脊"+tag, "head", "frost", mirror([[.08,1.79,-.095],[.145,1.89,-.106],[.158,1.99,-.133]],side), .003)
    # 九色仅在右上臂一条短窄带上显现；不占据九块主甲面。
    for i in range(9):
        y=1.346-i*.010
        a.tube(f"g_micro_spectrum_{i}", "袖缘第"+str(i+1)+"微谱", "chest", f"spectrum{i+1}", [[.306+(i*.001),y,.079],[.325+(i*.001),y-.006,.069]], .0016)
    a.add("g_ingot", "涵光低扁削角锭", "ingot", "glass", "ingot", size=[.37,.068,.205], topMaterial="glass", edgeMaterial="frost")
    for i in range(9):
        x=-.12+i*.03
        a.gem(f"g_ingot_inclusion_{i}", "锭内第"+str(i+1)+"异色晶核", "ingot", f"spectrum{i+1}", [x,0,.023*math.sin(i*.84)], [.008,.009,.017], rotation=[0,20+i*13,18])
    a.tube("g_ingot_mark", "锭面一笔折射刻纹", "ingot", "silver", [[-.12,.035,.044],[-.06,.035,.011],[-.015,.035,.034],[.04,.035,-.026],[.12,.035,-.042]], .0018)
    a.ribbon("g_ingot_frost", "锭面整笔磨砂斜脊", "ingot", "frost", [[-.102,.034,-.054],[-.03,.035,-.02],[.034,.035,.017],[.099,.034,.055]], [.004,.011,.009,.003], normal=[0,1,0], thickness=.002)
    a.tube("g_ingot_inner", "锭内无色弯曲细脉", "ingot", "frost", [[-.14,-.012,.028],[-.065,-.009,-.025],[.03,-.012,.027],[.14,-.009,-.02]], .0016)
    return spec


def wood():
    spec=text_spec(
        "yuanying_qingdi", "青帝长生", "神木生长的帝君礼甲",
        "神木不以树皮示人：翡翠作整甲，青铜作脉络，宽袖与背冠向外生长。",
        ["#1c6759", "#a6c8af", "#b59d64", "#e1dfbf", "#163f39"],
        {
            "jade": mat("长生翡玉·连续整甲", "#1c6759", .35, .22),
            "silk": mat("春水青绫·完整宽衣", "#a6c8af", .02, .72),
            "bronze": mat("神木青铜·承力生长脉", "#b59d64", .80, .34),
            "ivory": mat("桑月绢·长裳与袖里", "#e1dfbf", .01, .80),
            "dark": mat("古木墨玉·背脊与足底", "#163f39", .26, .44),
            "leaf": mat("初生玉叶·局部舒展叶面", "#64ac87", .18, .32),
            "seed": mat("木心凝露·芽心微光", "#d2dfa8", .12, .21, 1, "#a7c88b", .16),
            "vein": mat("苍青年轮·锭内沉纹", "#315f4d", .58, .41),
        },
        "青帝长生不是一套树皮铠甲，也不是在普通长袍上贴满树叶。它以厚而润的整块翡翠曲甲为胸背主体，暖青铜沿胸肋和脊柱生成连续脉络，淡春水色大袖从肩下舒展，桑月色长裳前短后长。头冠和背冠都向上、向外生长，但两者尺度分明：头冠只有少数分枝和三处芽心，背部则以中轴树脉托起肩后叶形曲面。衣甲的大面保持完整，叶子只出现在分枝的端点；远看先读到帝君式的宽袖、收腰、长尾，再看见神木的细节。与星河的冷静竖向、琉光的硬质悬挑不同，长生的曲线更缓、体量更温润，青铜也刻意保留微哑表面，使其有长久滋养而非战损锈蚀的气质。",
        "轮廓以约一米宽的舒展大袖为主，冠顶向上生长到约二点二三米，外袍前端止于靴踝，后端落得更低。前胸由两道细脉抱住中间大块无纹翡翠，腰下有一条顺势弯曲的整体长叶纹，避免胸、腹、大腿各放一块重复叶片。背甲是完整弧壳，青铜脊干从腰部上行后分成几支；两片离身肩叶位于肩胛后上方，侧视可见叶面鼓起的厚度、背甲与宽袖之间的空气。头部留出面孔，后脑与耳根用连续护壳包覆，冠枝之间的空处没有用发光膜填满。",
        "神木心髓在青铜基质中凝成绿色玉质，锭仍铸为削角的低扁长形。顶面一枚横卧的树心玉窗包住细小芽核，两组不完全同心的年轮与向两端生长的根脉刻纹说明材料的生长方向。相同语言进入衣甲时，不照搬木纹：翡翠用于整胸、后背和靴壳，青铜用于冠骨和生长脉，春水绫与桑月绢负责内外柔软差异，古木墨玉只压住阴影与足底，初生玉叶用在少数卷曲端点。青帝的生命感因此来自结构生成与色温关系，不靠全身荧光绿。所有生长与长生叙事都属于本次设计，不声称已经接入修炼或任何游戏效果。",
        "相较金丹的稳定器形，元婴长生开始呈现主动生长：冠枝逐渐变细，背脉分叉，袖下长绶脱离人体自然弯落，衣甲与神木仿佛来自同一条生命轨迹。它仍有实体衣料的重量、包覆的甲面和扎实的脚底，所以没有提前消耗化神的超越感；后续可以改变形与神的关系，而非简单放大树冠或把每个空处补成叶片。这套在三款中最温暖也最庄重，设计对象是已经能自持一方生机的仙君，不是森林游侠或武侠药农。",
        "整胸、整背、整袖、前后长裳均为连续曲面。只在两侧肩胛后上方各用一片舒展的独立玉叶作辅层，头冠端点少量芽叶不连续铺排；没有鳞甲、树皮碎板或满身叠叶。青铜枝干是结构线，年轮是真实窄嵌线，二者均不能被误判为大面积分片，也不能拿它们代替胸背主体。",
        {
            "head": {"name": "扶桑承露冠", "description": "翡翠额面与深色后脑壳连成完整冠基，两道温哑青铜主枝沿耳后向上生长，越往上越细，端头微向内收。冠内留下较大的负空间，三处凝露芽心分出主次，后视另有沿脑壳而行的根脉。额心不放巨大宝石，冠的识别依靠真实分枝、包覆基座和向上的生长势。"},
            "chest": {"name": "青帝蕴生甲", "description": "春水青绫包住躯干和两臂，前后各一整面翡翠曲壳贴身收腰，胸前枝脉留出宽阔无纹玉面。大袖在肘下逐渐展开，内缘是桑月绢，袖口又回收以露出手。肩后两片局部玉叶与背部青铜主干联系，枝干由腰背长出而非粘在一件普通衣服上，侧后方仍有完整背甲作承托。"},
            "legs": {"name": "万岁含章裳", "description": "独立双腿完整包覆，外层桑月长裳在正面形成宽而连续的弧面，背面青绿长尾更低、更外展。前面的整体长叶纹只是一道有宽度变化的流线，青铜细脉在其内部收放；侧腰到后摆的年轮式弧纹只用少量线，保持大面积柔软留白。它有真正的裙后体积，不以几块竖板遮住裤腿。"},
            "boots": {"name": "盘根含玉靴", "description": "翡翠靴壳完整包住足部和脚踝，古木墨玉薄底有封闭体积，靴口以春水色内缘承接裤管。青铜根脉从后跟沿两侧绕到脚背，最后收成一处温润小芽，既承接神木主题又不做巨爪或树桩脚。趾面低长、后跟圆收，站姿稳而不沉重。"},
        },
        {"name": "神木长生锭", "description": "宽三十五点五厘米、高六点三厘米的青铜低扁削角锭，顶面一体树心玉窗呈横卧长椭圆，内部只有一颗浅暖芽核。年轮为几道偏心细刻纹，两端各有不同走向的根脉；侧面苍青沉纹与上面的温润玉窗对照。它始终是长形锭，不改成圆木截面、树桩或盆景。"},
    )
    a=Author(spec)
    a.loft("w_body", "春水绫完整承衣", "chest", "silk", [(.96,.155,.116),(1.08,.155,.119),(1.27,.193,.133),(1.41,.219,.132),(1.46,.188,.114)], thickness=.007, segments=48)
    a.loft("w_front", "连续翡翠整胸甲", "chest", "jade", [(1.015,.164,.127),(1.10,.163,.132),(1.29,.203,.148),(1.425,.235,.145),(1.47,.187,.124)], startAngle=5, endAngle=175, thickness=.010, segments=40)
    a.loft("w_back", "连续翡翠整背甲", "chest", "jade", [(1.015,.166,.13,0,-.004),(1.10,.166,.134,0,-.004),(1.29,.208,.15,0,-.004),(1.425,.238,.15,0,-.004),(1.47,.190,.127,0,-.004)], startAngle=175, endAngle=365, thickness=.010, segments=40)
    a.loft("w_collar", "长生后颈整领", "chest", "bronze", [(1.435,.121,.101),(1.50,.087,.086),(1.568,.081,.079)], startAngle=152, endAngle=388, thickness=.007, segments=32)
    a.loft("w_belt", "翡翠整曲腰封", "legs", "jade", [(.969,.169,.128),(.999,.167,.128),(1.032,.165,.129)], thickness=.008, segments=40)
    a.loft("w_front_robe", "完整桑月前裳", "legs", "ivory", [(.205,.319,.236,0,.004,0,.009),(.45,.284,.208,0,.002,0,.007),(.72,.232,.174,0,.001,0,.003),(1.005,.166,.126)], startAngle=0, endAngle=180, thickness=.006, segments=48)
    a.loft("w_back_robe", "完整春水长后裳", "legs", "silk", [(.075,.414,.312,0,-.018,0,.01),(.30,.348,.263,0,-.018,0,.008),(.62,.26,.196,0,-.009,0,.004),(1.006,.167,.129)], startAngle=180, endAngle=360, thickness=.006, segments=48)
    a.ribbon("w_front_leaf", "衣前整体长叶流纹", "legs", "leaf", [[0,1.006,.139],[-.027,.86,.164],[-.048,.67,.197],[.014,.42,.228],[.036,.252,.24]], [.045,.16,.178,.123,.004], thickness=.004)
    a.tube("w_front_vein", "长叶内部单笔主脉", "legs", "bronze", [[0,1.006,.145],[-.027,.86,.17],[-.048,.67,.203],[.014,.42,.234],[.036,.252,.246]], radii=[.003,.0032,.0028,.002,.001])
    a.tube("w_back_trunk", "从腰背生出的青铜主干", "chest", "bronze", [[0,1.021,-.144],[-.018,1.18,-.161],[.009,1.36,-.169],[0,1.50,-.145]], radii=[.012,.015,.012,.006])
    for side,tag in [(-1,"l"),(1,"r")]:
        label="左" if side<0 else "右"
        a.loft(f"w_sleeve_{tag}", label+"向后下方舒展的完整外袖", "chest", "silk", [(.69,.224,.185,side*.371,-.055,0,.007),(.90,.196,.166,side*.34,-.034,0,.006),(1.12,.153,.134,side*.298,-.019,0,.005),(1.31,.110,.105,side*.262,-.008),(1.44,.081,.086,side*.234,0)], startAngle=140, endAngle=400, thickness=.006, segments=48)
        a.loft(f"w_sleeve_inner_{tag}", label+"闭合桑月内袖", "chest", "ivory", [(.812,.052,.061,side*.355,.012),(.96,.061,.069,side*.33,.006),(1.14,.073,.079,side*.305,0),(1.36,.082,.085,side*.249,0),(1.46,.074,.077,side*.225,0)], thickness=.005, segments=40)
        a.loft(f"w_cuff_{tag}", label+"翡翠贴腕内袖口", "chest", "jade", [(.807,.058,.067,side*.354,.013),(.85,.060,.069,side*.349,.012)], thickness=.007, segments=36)
        a.loft(f"w_shoulder_{tag}", label+"扁弧翡翠整肩甲", "chest", "jade", [(1.375,.100,.105,side*.245,0),(1.445,.116,.118,side*.255,-.003),(1.486,.10,.104,side*.249,-.009),(1.512,.023,.03,side*.235,-.012)], startAngle=0, endAngle=360, thickness=.006, capTop=True, segments=36)
        a.ribbon(f"w_shoulder_leaf_{tag}", label+"唯一肩后舒展玉叶", "chest", "leaf", mirror([[.19,1.44,-.122],[.30,1.48,-.155],[.389,1.58,-.13],[.414,1.66,-.081]],side), [.035,.114,.091,.003], normal=[0,0,-1], thickness=.009)
        a.tube(f"w_leaf_mid_{tag}", label+"肩叶青铜主脉", "chest", "bronze", mirror([[.19,1.44,-.132],[.30,1.48,-.164],[.389,1.58,-.139],[.414,1.66,-.089]],side), radii=[.006,.005,.003,.001])
        a.tube(f"w_chest_vein_{tag}", label+"胸肋完整生长脉", "chest", "bronze", mirror([[.021,1.045,.141],[.111,1.18,.124],[.156,1.32,.118],[.205,1.428,.086],[.122,1.48,.099]],side), radii=[.003,.006,.006,.004,.002])
        a.tube(f"w_chest_fork_{tag}", label+"胸脉少量分枝", "chest", "bronze", mirror([[.111,1.18,.124],[.079,1.28,.147],[.052,1.38,.146]],side), radii=[.004,.003,.001])
        a.tube(f"w_back_branch_{tag}", label+"背脊向肩生长的分枝", "chest", "bronze", mirror([[0,1.23,-.165],[.115,1.35,-.159],[.204,1.46,-.146],[.303,1.53,-.16],[.388,1.61,-.125]],side), radii=[.008,.008,.006,.004,.0015])
        a.tube(f"w_back_root_{tag}", label+"下背回根", "chest", "dark", mirror([[.008,1.36,-.171],[.11,1.22,-.151],[.127,1.08,-.109],[.075,1.018,-.142]],side), radii=[.005,.006,.005,.002])
        a.ribbon(f"w_streamer_{tag}", label+"宽袖下生长长绶", "chest", "silk", mirror([[.35,1.01,-.131],[.47,.83,-.13],[.51,.59,-.17],[.49,.37,-.23],[.43,.25,-.265]],side), [.13,.157,.13,.078,.008], normal=[0,0,-1], thickness=.004)
        a.tube(f"w_streamer_vein_{tag}", label+"长绶一笔铜丝", "chest", "bronze", mirror([[.35,1.01,-.139],[.47,.83,-.138],[.51,.59,-.178],[.49,.37,-.238],[.43,.25,-.273]],side), .0018)
        a.loft(f"w_trouser_{tag}", label+"完整内层长裤", "legs", "silk", [(.18,.053,.063,side*.095,0),(.45,.064,.075,side*.095,0),(.73,.077,.087,side*.095,0),(.968,.087,.099,side*.094,0)], thickness=.006, segments=36)
        a.tube(f"w_robe_flow_{tag}", label+"衣摆单笔年轮弧", "legs", "bronze", mirror([[.151,.98,.069],[.22,.72,.065],[.268,.47,.088],[.269,.26,.145],[.16,.215,.211]],side), .0022)
        a.loft(f"w_sole_{tag}", label+"古木墨玉封闭薄底", "boots", "dark", [(0,.06,.137,side*.095,.054),(.05,.065,.142,side*.095,.054)], thickness=.007, capTop=True, capBottom=True, segments=36)
        a.loft(f"w_boot_{tag}", label+"完整盘根翡翠靴", "boots", "jade", [(.047,.061,.138,side*.095,.053),(.084,.062,.14,side*.095,.054),(.138,.055,.091,side*.095,.021),(.21,.054,.066,side*.095,0),(.282,.061,.073,side*.095,0)], thickness=.008, capBottom=True, segments=40)
        a.loft(f"w_boot_lip_{tag}", label+"春水内靴口", "boots", "silk", [(.267,.062,.075,side*.095,0),(.287,.063,.077,side*.095,0)], thickness=.004, segments=32)
        a.tube(f"w_boot_root_{tag}", label+"后跟盘至趾面根脉", "boots", "bronze", mirror([[.095,.22,-.067],[.149,.14,-.036],[.151,.088,.091],[.127,.089,.169],[.095,.108,.181]],side), radii=[.004,.0048,.005,.004,.0018])
        a.tube(f"w_boot_fork_{tag}", label+"靴面内侧回根", "boots", "bronze", mirror([[.095,.22,-.067],[.044,.15,-.033],[.04,.092,.089],[.078,.11,.156],[.095,.108,.181]],side), radii=[.0035,.004,.0035,.0025,.0018])
        a.gem(f"w_boot_bud_{tag}", label+"足背小芽心", "boots", "seed", [side*.095,.112,.18], [.006,.010,.005])
    a.tube("w_robe_back_hem", "后裳低位苍青年轮", "legs", "vein", ellipse(.414,.312,.085,193,347,cz=-.018,n=40), .003)
    a.loft("w_skull", "古木墨玉后脑完整壳", "head", "dark", [(1.565,.105,.11,0,-.008),(1.68,.119,.123,0,-.006),(1.782,.112,.119,0,-.006)], startAngle=158, endAngle=382, thickness=.007, segments=36)
    a.loft("w_cap", "翡翠连续冠基穹顶", "head", "jade", [(1.767,.117,.123,0,-.004),(1.814,.094,.101,0,-.007),(1.844,.013,.019,0,-.01)], thickness=.006, capTop=True, segments=40)
    a.panel("w_brow", "扶桑完整翡翠额面", "head", "jade", [(-.106,-.004),(-.095,.026),(-.047,.036),(0,.064),(.047,.036),(.095,.026),(.106,-.004),(.047,-.017),(0,-.035),(-.047,-.017)], [0,1.765,.12], depth=.014, bevel=.004, trimMaterial="bronze", trimRadius=.002)
    for side,tag in [(-1,"l"),(1,"r")]:
        a.tube(f"w_crown_trunk_{tag}", "扶桑渐细主冠枝"+tag, "head", "bronze", mirror([[.091,1.77,-.007],[.143,1.91,-.052],[.193,2.055,-.085],[.181,2.159,-.086],[.119,2.226,-.06]],side), radii=[.013,.014,.011,.007,.0025])
        a.tube(f"w_crown_fork_{tag}", "主枝一次分生"+tag, "head", "bronze", mirror([[.144,1.92,-.056],[.101,2.024,-.032],[.075,2.113,-.007]],side), radii=[.009,.006,.0018])
        a.ribbon(f"w_crown_leaf_{tag}", "冠端局部舒展玉芽"+tag, "head", "leaf", mirror([[.181,2.152,-.085],[.223,2.184,-.049],[.213,2.225,-.017]],side), [.006,.045,.002], normal=[0,0,1], thickness=.005)
        a.gem(f"w_crown_seed_{tag}", "冠端小凝露"+tag, "head", "seed", [side*.075,2.113,-.007], [.008,.015,.007])
        a.tube(f"w_ear_{tag}", "完整耳后根护弦"+tag, "head", "bronze", mirror([[.094,1.77,.072],[.122,1.69,.031],[.114,1.612,-.024],[.087,1.588,-.077]],side), radii=[.006,.008,.006,.003])
        a.tube(f"w_head_back_{tag}", "后脑细根脉"+tag, "head", "bronze", mirror([[.084,1.61,-.088],[.062,1.70,-.124],[.086,1.79,-.101],[.105,1.87,-.075]],side), .0027)
    a.gem("w_crown_seed_center", "扶桑主凝露", "head", "seed", [0,2.05,-.026], [.013,.023,.011])
    a.tube("w_crown_seed_stem", "主芽有根承托", "head", "bronze", [[0,1.82,-.093],[-.025,1.91,-.092],[0,2.026,-.031]], radii=[.006,.005,.002])
    a.add("w_ingot", "神木低扁削角锭", "ingot", "bronze", "ingot", size=[.355,.063,.20], topMaterial="bronze", edgeMaterial="vein")
    window=[(-.117,0),(-.093,.042),(-.04,.061),(.047,.054),(.113,.016),(.121,-.006),(.073,-.049),(-.024,-.060),(-.092,-.037)]
    a.panel("w_ingot_window", "整块横卧树心玉窗", "ingot", "jade", window, [0,.0328,0], rotation=[-90,0,0], depth=.0028, bevel=.0014, trimMaterial="vein", trimRadius=.0013)
    for i,(rx,rz,cx,cz,start,end) in enumerate([(.092,.042,-.007,.003,18,174),(.076,.033,-.011,.005,197,352),(.056,.026,-.017,.002,14,167),(.039,.018,-.013,.002,196,348)]):
        a.tube(f"w_ingot_ring_{i}", "偏心树心年轮"+str(i+1), "ingot", "bronze", ellipse(rx,rz,.036,start,end,cx,cz,n=24), .00115)
    a.gem("w_ingot_seed", "树心微小凝露核", "ingot", "seed", [-.012,.036,.002], [.008,.004,.009])
    for side,tag in [(-1,"l"),(1,"r")]:
        a.tube(f"w_ingot_root_{tag}", "向端部生长的根刻纹"+tag, "ingot", "dark", mirror([[.056,.0335,-.019],[.10,.0335,-.033],[.139,.030,-.044],[.159,.022,-.039]],side), .0017)
        a.tube(f"w_ingot_root_fork_{tag}", "锭面端根分生"+tag, "ingot", "jade", mirror([[.10,.0335,-.033],[.13,.031,.004],[.159,.021,.029]],side), .0015)
    a.tube("w_ingot_side_vein", "侧壁苍青沉纹", "ingot", "jade", [[-.165,-.009,.063],[-.119,-.009,.091],[.06,-.009,.091],[.156,-.009,.067]], .0022)
    return spec


def validate(designs):
    """独立检查冻结接口的数值、引用、轮廓、覆盖语义及低扁锭约束。"""
    assert isinstance(designs, list) and len(designs)==3
    design_ids=set()
    statistics=[]
    common={"id","name","role","material","shape","position","rotation","scale"}
    fields={
        "loft":{"rings","segments","startAngle","endAngle","thickness","capTop","capBottom"},
        "tube":{"path","radius","radii","steps","radialSegments"},
        "ribbon":{"path","width","widths","normal","thickness","steps"},
        "panel":{"polygon","depth","bevel","trimMaterial","trimRadius"},
        "torus":{"radius","tube","arc"},"gem":{"radii"},"ellipsoid":{"radii"},
        "box":{"size"},"ingot":{"size","topMaterial","edgeMaterial"},
    }
    def finite(v):
        if isinstance(v,(int,float)):
            assert math.isfinite(v), f"非有限数值 {v}"
        elif isinstance(v,dict):
            for value in v.values(): finite(value)
        elif isinstance(v,list):
            for value in v: finite(value)
    def orient(a,b,c):
        return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
    def touch(a,b,p):
        return abs(orient(a,b,p))<1e-12 and min(a[0],b[0])-1e-12<=p[0]<=max(a[0],b[0])+1e-12 and min(a[1],b[1])-1e-12<=p[1]<=max(a[1],b[1])+1e-12
    def cross(a,b,c,d):
        return (orient(a,b,c)*orient(a,b,d)<0 and orient(c,d,a)*orient(c,d,b)<0) or touch(a,b,c) or touch(a,b,d) or touch(c,d,a) or touch(c,d,b)
    for spec in designs:
        assert spec["level"]==4 and spec["realm"]=="元婴"
        assert spec["id"] not in design_ids
        design_ids.add(spec["id"])
        mats=spec["materials"]
        assert len(mats)>=5
        for m in mats.values():
            assert re.fullmatch(r"#[0-9a-fA-F]{6}", m["color"])
            for key in ("metalness","roughness","opacity"):
                assert 0<=m.get(key,1)<=1
        ids=set()
        components=spec["components"]
        assert 35<=len(components)<=120
        assert set(ROLES).issubset({c["role"] for c in components})
        assert not any(c["shape"]=="box" for c in components), "主体不使用方盒"
        assert not any(c["role"]=="aura" for c in components), "本境不靠光环补主体"
        for role in ROLES:
            assert any(c["role"]==role and c["shape"]=="loft" for c in components), role+"缺少包覆曲面"
        for c in components+spec["ingot"]["components"]:
            assert c["id"] not in ids, c["id"]
            ids.add(c["id"])
            shape=c["shape"]
            assert shape in fields and set(c)<=common|fields[shape], c["id"]
            for key in ("material","trimMaterial","topMaterial","edgeMaterial"):
                if key in c: assert c[key] in mats, c["id"]+"未知材质"
            if c in spec["ingot"]["components"]:
                assert c["role"]=="ingot"
            else:
                assert c["role"] in ROLES
            if shape=="loft":
                rings=c["rings"]
                assert len(rings)>=2
                assert all(r["y"]<s["y"] for r,s in zip(rings,rings[1:])), c["id"]+"截面倒序"
                assert 0<c.get("endAngle",360)-c.get("startAngle",0)<=360
                for r in rings:
                    assert r["rx"]>0 and r["rz"]>0
                    assert c.get("thickness",.004)<min(r["rx"],r["rz"])-abs(r.get("wave",0))
            if shape in ("tube","ribbon"):
                path=c["path"]
                assert len(path)>=2 and all(len(p)==3 for p in path)
                assert all(p!=q for p,q in zip(path,path[1:])), c["id"]
                single,plural=("radius","radii") if shape=="tube" else ("width","widths")
                assert (single in c)!=(plural in c), c["id"]
                if single in c: assert c[single]>0
                if plural in c:
                    assert len(c[plural])==len(path) and min(c[plural])>0
            if shape=="panel":
                p=c["polygon"]; n=len(p)
                assert n>=3
                area=sum(p[i][0]*p[(i+1)%n][1]-p[(i+1)%n][0]*p[i][1] for i in range(n))
                assert abs(area)>1e-12
                for i in range(n):
                    assert p[i]!=p[(i+1)%n]
                    for j in range(i+1,n):
                        if j==i+1 or (i==0 and j==n-1): continue
                        assert not cross(p[i],p[(i+1)%n],p[j],p[(j+1)%n]), c["id"]+"轮廓自交"
            if shape=="ingot":
                assert min(c["size"])>0 and c["size"][1]/c["size"][0]<=.25
        ingots=[c for c in spec["ingot"]["components"] if c["shape"]=="ingot"]
        assert len(ingots)==1
        narrative="".join(spec[key] for key in ("description","silhouette","materialStory","progression","segmentation"))+"".join(p["description"] for p in spec["parts"].values())+spec["ingot"]["description"]
        han=len(re.findall(r"[\u4e00-\u9fff]",narrative))
        assert han>=500, f"{spec['name']}说明不足 {han}"
        finite(spec)
        statistics.append({"name":spec["name"],"components":len(components),"ingotComponents":len(spec["ingot"]["components"]),"roles":dict(Counter(c["role"] for c in components)),"shapes":dict(Counter(c["shape"] for c in components)),"materials":len(mats),"hanCharacters":han,"ingotHeightWidth":round(ingots[0]["size"][1]/ingots[0]["size"][0],6)})
    return statistics


def design_document(designs, stats):
    lines=["# 元婴三式：星河天衣／九色琉光／青帝长生", "",
           "本境交付真实曲面几何与中文设计，供主线程整合手写 HTML。只使用冻结接口中的 loft、panel、ribbon、tube、gem、ingot；没有 imagegen、外部图片、游戏资源或玩法承诺。",
           "", "## 总体设计判断", "",
           "旧稿的问题是轮廓贫乏、前后不成立和把装饰当主体。因此这三套先解决完整包覆与正侧背的体量，再用不同的材料行为形成神仙气质。星河是长衣外的离身空间；琉光是整块透明体的悬挑与折面；长生是温润整甲中的生长脉络。三套没有共用整身筒形模板，也没有给相同背板更换光环。所有服装都按合同的修长人台定位，双手与面孔可露出，脚趾朝 +Z。",
           "", "| 方向 | 第一识别轮廓 | 后视结构 | 材质行为 | 主体之外的局部辅层 |", "|---|---|---|---|---|",
           "| 星河天衣 | 窄肩、高腰、前开长裳、后伸星幕 | 弧面背襟、独立星图、非闭合腰轨 | 暗绡／蓝金／霜银／半透星纱 | 细长胸护面、整块冠额、袖口 |",
           "| 九色琉光 | 外挑披领、后倾冠扇、细腰长腿 | 完整背壳与离身曲瀑 | 不透明月瓷芯／透明琉璃壳／磨砂承缘 | 两片肩端小折翼、两块长棱胫甲 |",
           "| 青帝长生 | 上生冠枝、宽袖、前短后长大裳 | 完整背甲、青铜分生脊、两片肩后玉叶 | 翡翠整甲／温哑青铜／双层织物 | 只在枝端与肩后用少量玉叶 |", "",
           "## 几何与阅读顺序", "",
           "远景先读轮廓，中景读材质与主要曲线，近景再读星点、微谱和年轮。所有饰线都是有半径的 tube，披帛、曲瀑与叶面都带实际厚度。头部有后脑弧壳和封顶冠基，胸部有闭合承衣和完整背面，腿部有独立裤管，靴有脚背、踝、后跟和封底薄底。锭均是削角低扁长锭；其内含物与刻纹分别编写，不是同一锭换色。",
           "", "透明度只使用当前接口实际支持的 opacity；色散、焦散、时序动画、风摆与星空模拟均未实现，也未写入接口未知字段。主导面材质不发光，星髓与芽心仅少量微亮，九色无自发光。", ""]
    for spec,stat in zip(designs,stats):
        lines.extend(["## "+spec["name"], "", "> "+spec["tagline"], "", "### 完整设计", "", spec["description"], "", "### 轮廓与正侧背", "", spec["silhouette"], "", "### 材质与锭", "", spec["materialStory"], "", "**"+spec["ingot"]["name"]+"**："+spec["ingot"]["description"], "", "| 材质 | 职责与物理参数 |", "|---|---|"])
        for key,m in spec["materials"].items():
            lines.append("| "+m["name"]+" | `"+key+"`，"+m["color"]+"；金属度 "+str(m["metalness"])+"，粗糙度 "+str(m["roughness"])+"，不透明度 "+str(m.get("opacity",1))+" |")
        lines.extend(["", "### 四件的结构与命名", ""])
        for role in ROLES:
            part=spec["parts"][role]
            lines.extend(["**"+part["name"]+"**（"+{"head":"头","chest":"胸","legs":"腿","boots":"靴"}[role]+"）："+part["description"], ""])
        lines.extend(["### 进阶依据", "",spec["progression"],"", "### 局部分片说明", "",spec["segmentation"],"", "### 组件规模", "", f"防具 {stat['components']} 个独立组件，锭 {stat['ingotComponents']} 个；材质 {stat['materials']} 种；JSON 内实质说明 {stat['hanCharacters']} 个汉字。四角色计数为 "+"、".join({"head":"头","chest":"胸","legs":"腿","boots":"靴"}[r]+" "+str(stat['roles'][r]) for r in ROLES)+"。主体 shape 分布："+"、".join(k+" "+str(v) for k,v in stat["shapes"].items())+"。锭高宽比 "+str(stat["ingotHeightWidth"])+"。", ""])
    lines.extend(["## 独立检查与证据边界", "",
                  "`python3 art/immortal-armor-atlas/authors/level4.py --check` 可独立验证：恰好三套、境界与设计 ID、四种防具 role、每 role 的实际包覆 loft、材质引用、所有数值有限、loft 截面顺序与厚度、panel 非自交、路径及宽度／半径长度、唯一组件 ID、低扁锭比例、至少五种材质和每套不少于五百汉字的说明。该命令还比较 authored 输出与磁盘文件，避免说明和 JSON 漂移。", "",
                  "`python3 art/immortal-armor-atlas/authors/level4.py --engine-check` 复用已经存在的冻结引擎，验证三套的裸甲、穿戴人台、独立锭三类 Group：所有顶点与法线有限，Mesh 保留角色和材质元数据，真实包围盒满足合同尺寸，OBJ 确有顶点与三角面。另逐套从正侧背发出二百二十条包覆射线，覆盖躯干、肩、后脑和足部，并区分执行数、实际命中人台的有效采样数和未命中参考面的射线；不能把未命中当成通过采样。它打印 JSON、引擎与 Three.js 的 SHA256，不写额外产物；如果引擎不存在，明确返回 `not_verified`，不轮询或构造替代引擎。", "",
                  "初次几何审阅暴露了肩部承衣缺口与琉光髋衬缺口，已经增补连续包覆曲面；前腰射线又定位了星河两处开襟内侧裸露，已补完整承袴后复验。长生的外袖也由鼓袖改为向后下方舒展的宽袖，足底统一下探至 y=0，以完整覆盖人台足底。保留这些修正背景，避免把初稿数值检查与精修后的网格检查混作同一版。", "",
                  "冻结引擎的 Node 检查、实际网格规模与导出结果以本任务最终交付记录为准。独立检查及开发者三维审阅不等于人工审美认可。最终整册 HTML、不同灯光与连续转动中的透明排序、单件 UI、玩家审美认可、游戏运行、音效、FPS 与多人表现均由主线程继续整合或保持 `not_verified`；本作者不修改 core、不启动游戏。", ""])
    return "\n".join(lines)


def engine_check():
    """复用现有引擎；不生成替代引擎、图片、OBJ 文件或额外目录。"""
    engine=ROOT/"core/engine.js"
    if not engine.is_file():
        print(json.dumps({"engine":"not_verified","reason":"core/engine.js 不存在；未轮询、未构造替代实现"},ensure_ascii=False))
        return
    script=r'''
const fs=require('fs'), crypto=require('crypto');
globalThis.THREE=require('./tools/web/three.min.js');
const E=require('./art/immortal-armor-atlas/core/engine.js');
const source='./art/immortal-armor-atlas/levels/level4.json';
const specs=JSON.parse(fs.readFileSync(source,'utf8'));
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const evidence={jsonSHA256:sha(source),engineSHA256:sha('./art/immortal-armor-atlas/core/engine.js'),threeSHA256:sha('./tools/web/three.min.js'),designs:[]};
for(const spec of specs){
 E.validateDesign(spec);
 const body=E.buildDesign(spec,{avatar:false}), dressed=E.buildDesign(spec,{avatar:true}), ingot=E.buildIngot(spec);
 const stats={name:spec.name,groups:[]};
 for(const [kind,g] of [['body',body],['dressed',dressed],['ingot',ingot]]){
  g.updateMatrixWorld(true);
  g.traverse(n=>{if(n.isMesh){
   for(const key of ['position','normal']){const a=n.geometry.attributes[key];if(a)for(const v of a.array)if(!Number.isFinite(v))throw Error(spec.name+' '+kind+' '+key+' 非有限值');}
   if(!n.userData.componentId||!n.userData.role||!n.userData.materialKey)throw Error('mesh metadata missing');
  }});
  const b=new THREE.Box3().setFromObject(g),size=b.getSize(new THREE.Vector3());
  if(kind==='body'&&(b.min.y<-.0001||b.max.y>2.4||size.x>1.7))throw Error(spec.name+'超出合同尺寸');
  if(kind==='ingot'&&size.y/size.x>.25)throw Error(spec.name+'包含饰纹后的锭体也须保持低扁');
  const obj=E.exportOBJ(g);
  if(!obj.includes('\nv ')||!obj.includes('\nf ')||/NaN|Infinity/.test(obj))throw Error('OBJ invalid');
  stats.groups.push({kind,meshStats:E.meshStats(g),bounds:{min:b.min.toArray(),max:b.max.toArray(),size:size.toArray()},objBytes:obj.length,objVertices:(obj.match(/^v /gm)||[]).length,objFaces:(obj.match(/^f /gm)||[]).length});
 }
 // 从外向内的实际网格射线：衣物必须先于应覆盖的人台表面被击中。
 const avatar=E.buildAvatar();avatar.updateMatrixWorld(true);const ray=new THREE.Raycaster();
 const probes=[];
 for(const y of [.89,.94,1.01,1.13,1.31,1.4,1.45])for(let a=0;a<360;a+=30)probes.push({y,a,x:0,skin:'躯体',kind:'torso'});
 for(const side of [-1,1]){
  for(const y of [1.38,1.43,1.46,1.475])for(let a=0;a<360;a+=45)probes.push({y,a,x:side*.211,skin:side<0?'左肩':'右肩',kind:'shoulder'});
  for(const y of [.008,.04,.075])for(let a=0;a<360;a+=45)probes.push({y,a,x:side*.095,skin:side<0?'左足':'右足',kind:'feet'});
 }
 for(const y of [1.60,1.68,1.76,1.78])for(let a=195;a<=345;a+=30)probes.push({y,a,x:0,skin:'头颅',kind:'occiput'});
 const uncovered=[];let actual=0;
 for(const p of probes){
  const rad=p.a*Math.PI/180,dir=new THREE.Vector3(Math.cos(rad),0,Math.sin(rad));
  const origin=dir.clone().multiplyScalar(2);origin.x+=p.x;origin.y=p.y;ray.set(origin,dir.clone().negate());
  const skin=ray.intersectObject(avatar,true).find(h=>h.object.name===p.skin);
  if(!skin)continue;actual++;
  const cloth=ray.intersectObject(body,true)[0];
  if(!cloth||cloth.distance>skin.distance+.001)uncovered.push({...p,skinDistance:skin.distance,clothDistance:cloth&&cloth.distance});
 }
 if(uncovered.length)throw Error(spec.name+' 包覆射线失败 '+JSON.stringify(uncovered));
 stats.coverage={selected:probes.length,completed:probes.length,unrun:0,referenceHits:actual,referenceMisses:probes.length-actual,uncovered:0};
 evidence.designs.push(stats);E.dispose(avatar);E.dispose(body);E.dispose(dressed);E.dispose(ingot);
}
console.log(JSON.stringify(evidence,null,2));
'''
    subprocess.run(["node","-e",script],cwd=ROOT.parents[1],check=True)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--engine-check", action="store_true")
    args=parser.parse_args()
    designs=rounded([star(),crystal(),wood()])
    stats=validate(designs)
    outputs={ROOT/"levels/level4.json":json.dumps(designs,ensure_ascii=False,indent=2)+"\n",
             ROOT/"levels/level4-design.md":design_document(designs,stats)}
    for path,content in outputs.items():
        if args.check or args.engine_check:
            assert path.read_text()==content, str(path)+"与 authored 输入不同"
        else:
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text(content)
    print(json.dumps({"status":"passed","mode":"check" if args.check or args.engine_check else "write","designs":stats},ensure_ascii=False,indent=2),flush=True)
    if args.engine_check:
        engine_check()


if __name__=="__main__":
    main()
