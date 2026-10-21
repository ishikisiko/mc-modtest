#!/usr/bin/env python3
"""Deterministic alchemy assets. --check checks drift; --preview renders actual models.

Resource generation uses only the standard library. Preview uses numpy/Pillow
from .venv-preview and the existing Minecraft JSON model software renderer.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import struct
import sys
import zlib

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/myvillage"
DATA = ROOT / "src/main/resources/data"
HERBS = ("spirit_leaf", "dew_blossom", "sunroot", "blood_berry", "cloud_fungus", "calm_lotus")
MEDICINES = ("huiling", "yangyuan", "ningshen")
ITEM_IDS = ("alchemy_furnace", *HERBS, *(f"formula_{x}" for x in MEDICINES), *(f"pill_{x}" for x in MEDICINES))
FOUNDATION_FORMULAS = ('zhuji_low', 'zhuji_middle', 'zhuji_high', 'buji_middle', 'buji_high')
FOUNDATION_ITEMS = ("ningyuan_fruit_low", "ningyuan_fruit_high", "pill_zhuji", "pill_buji", *("formula_"+x for x in FOUNDATION_FORMULAS))
ITEM_IDS += FOUNDATION_ITEMS
NAMES = {
    "alchemy_furnace": ("Bronze Alchemy Furnace", "青铜丹炉"),
    "spirit_leaf": ("Spirit Leaf", "聚灵叶"), "dew_blossom": ("Dew Blossom", "凝露花"),
    "sunroot": ("Sunroot", "赤阳根"), "blood_berry": ("Blood Berry", "血朱果"),
    "cloud_fungus": ("Cloud Fungus", "云芝"), "calm_lotus": ("Calm Lotus", "清心莲"),
    "formula_huiling": ("Spirit Recovery Formula", "回灵丹方"),
    "formula_yangyuan": ("Vitality Nourishing Formula", "养元丹方"),
    "formula_ningshen": ("Mind Calming Formula", "凝神丹方"),
    "pill_huiling": ("Spirit Recovery Pill", "回灵丹"),
    "pill_yangyuan": ("Vitality Nourishing Pill", "养元丹"),
    "pill_ningshen": ("Mind Calming Pill", "凝神丹"),
}
LANG = {
    "alchemy.myvillage.quality.1": ("Lower", "下品"),
    "alchemy.myvillage.quality.2": ("Middle", "中品"),
    "alchemy.myvillage.quality.3": ("Upper", "上品"),
    "alchemy.myvillage.quality.4": ("Perfect", "极品"),
    "item.myvillage.alchemy.quality_name": ("%s %s", "%s%s"),
    "tooltip.myvillage.alchemy.quality": ("Quality: %s", "品质：%s"),
    "screen.myvillage.crafting.foundation": ("Alchemy and forging use your quality ceilings. Learn patterns and use a Forging Ding.", "炼丹与炼器成品受品质上限限制；学会器谱后可在炼器鼎制作。"),
    "container.myvillage.alchemy_furnace": ("Bronze Alchemy Furnace", "青铜丹炉"),
    "alchemy.myvillage.formula_hint": ("Use to learn permanently; brewing needs no formula item", "右键永久学习，炼丹不再需要携带丹方"),
    "alchemy.myvillage.ingredients": ("Ingredients: %s + %s", "药材：%s + %s"),
    "alchemy.myvillage.fuel_hint": ("Fuel: Low-grade Spirit Stone", "炉火：下品灵石"),
    "alchemy.myvillage.herb_hint": ("Plant on Spirit Soil; harvest this same herb", "可直接种在灵土上，成熟收获同种药材"),
    "alchemy.myvillage.huiling_hint": ("Restores spiritual power", "恢复灵力"),
    "alchemy.myvillage.yangyuan_hint": ("Slowly restores health; damage interrupts recovery", "缓慢恢复生命；受到伤害会中断药效"),
    "alchemy.myvillage.ningshen_hint": ("Assists stability meditation after reaching the stage cap", "修为达到当前上限后，辅助打坐巩固稳定度"),
    "alchemy.myvillage.shared_cooldown": ("Medicines share a cooldown", "丹药共用服药间隔"),
    "tooltip.myvillage.alchemy.formula_learn": ("Use to learn permanently; consumes one copy", "右键永久学习，消耗一份丹方；已学不消耗"),
    "tooltip.myvillage.alchemy.ingredient": ("%s ×1", "%s ×1"),
    "tooltip.myvillage.alchemy.pill_huiling": ("Restore %s%% of maximum spiritual power", "恢复最大灵力的 %s%%"),
    "tooltip.myvillage.alchemy.pill_yangyuan": ("Heal %s%% maximum health every %ss for %ss; damage stops recovery", "每次恢复最大生命的 %s%%，间隔 %s 秒，持续 %s 秒；受伤中断"),
    "tooltip.myvillage.alchemy.pill_ningshen": ("%s× stability consolidation for %ss; only after cultivation cap", "稳定度巩固效率 %s 倍，持续 %s 秒；仅限修为达到当前上限后"),
    "tooltip.myvillage.alchemy.shared_interval": ("Shared medicine interval: %s s", "共用服药间隔：%s 秒"),
    "message.myvillage.alchemy.cooldown": ("Wait for the medication interval", "尚未达到下次服药时间"),
    "message.myvillage.alchemy.active": ("Another medicine is still active", "已有丹药正在生效"),
    "message.myvillage.alchemy.full_power": ("Spiritual power is full", "灵力已满"),
    "message.myvillage.alchemy.full_health": ("Health is full", "生命已满"),
    "message.myvillage.alchemy.ineligible": ("Cannot use this medicine in the current cultivation state", "当前修炼状态无法服用此丹药"),
    "message.myvillage.alchemy.start_invalid": ("Open this furnace nearby to start a batch", "请在丹炉旁打开界面后开炉"),
    "message.myvillage.alchemy.start_busy": ("This furnace already has an active batch", "此炉正在炼制，不能重新开炉"),
    "message.myvillage.alchemy.start_unawakened": ("Awaken your spiritual root before brewing", "请先测灵觉醒，再开始炼丹"),
    "message.myvillage.alchemy.start_ingredients": ("Choose a learned recipe and supply its herbs", "请选择已学丹方并补齐药材"),
    "message.myvillage.alchemy.start_fuel": ("Add enough low-grade spirit stones", "请放入足够的下品灵石"),
    "message.myvillage.alchemy.start_output": ("Take the finished pills before starting a new batch", "请先取走丹药，再开下一炉"),
    "message.myvillage.alchemy.formula_learned": ("Learned %s permanently", "已永久习得：%s"),
    "message.myvillage.alchemy.formula_known": ("You already know %s", "已经习得：%s"),
    "message.myvillage.alchemy.formula_unknown": ("Learn this formula before brewing", "需要先学习这张丹方"),
    "message.myvillage.alchemy.inventory_full": ("Make room for the previous ingredients first", "背包放不下旧材料，请先腾出空间"),
    "screen.myvillage.alchemy.learned": ("Learned recipes", "已学丹方"),
    "screen.myvillage.alchemy.learn_hint": ("Use a formula to learn it. Select a recipe here to fill one batch.", "手持丹方右键学习。点击已学丹方，自动补齐一炉材料。"),
    "screen.myvillage.alchemy.autofill": ("Click to fill one batch from your inventory", "点击从背包补齐一炉药材和灵石"),
    "screen.myvillage.alchemy.not_learned": ("Recipe not learned", "尚未学习此丹方"),
    "screen.myvillage.alchemy.legacy": ("Old copy", "旧丹方"),
    "screen.myvillage.alchemy.formula": ("Formula", "丹方"),
    "screen.myvillage.alchemy.herbs": ("Herbs", "药材"),
    "screen.myvillage.alchemy.fuel": ("Fuel", "灵石"),
    "screen.myvillage.alchemy.output": ("Pills", "丹药"),
    "screen.myvillage.alchemy.status.0": ("Choose a learned recipe", "请选择已学丹方"),
    "screen.myvillage.alchemy.status.1": ("Check ingredients", "药材不符"),
    "screen.myvillage.alchemy.status.2": ("Needs spirit stone", "需要下品灵石"),
    "screen.myvillage.alchemy.status.3": ("Output blocked", "丹药格已满或不兼容"),
    "screen.myvillage.alchemy.status.4": ("Brewing", "炼制中"),
    "screen.myvillage.alchemy.status.5": ("Ready to brew", "材料齐备 · 每次一炉"),
    "screen.myvillage.alchemy.start": ("Start brewing", "开始炼制"),
    "screen.myvillage.alchemy.locked": ("Brewing · Locked", "炼制中 · 材料已锁定"),
    "screen.myvillage.alchemy.ceiling": ("Skill %s · %s", "资质 %s · 本炉上限：%s"),
    "screen.myvillage.alchemy.ceiling_pending": ("Aptitude locked on start", "开炉时锁定你的炼丹资质"),
    "screen.myvillage.alchemy.ingredients": ("Herbs: %s + %s", "药材：%s + %s"),
    "screen.myvillage.alchemy.batch": ("%s stone · %s pills · %ss", "每炉 %s 灵石 · %s 丹 · %s 秒"),
}
ZH_FOUNDATION = {
    "ningyuan_fruit_low":"下品凝元果", "ningyuan_fruit_high":"上品凝元果",
    "pill_zhuji":"筑基丹", "pill_buji":"补基丹",
    "formula_zhuji_low":"下品筑基丹方", "formula_zhuji_middle":"中品筑基丹方", "formula_zhuji_high":"上品筑基丹方",
    "formula_buji_middle":"中品补基丹方", "formula_buji_high":"上品补基丹方",
}
ZH_EXTRA = {"message.myvillage.cultivation.session.foundation_pill_required":"筑基需要服用筑基丹；手持丹药右键开始","message.myvillage.cultivation.session.foundation_failed":"筑基失败，需重新巩固稳定度"}
for _id, _names in NAMES.items():
    LANG[f"{'block' if _id == 'alchemy_furnace' else 'item'}.myvillage.{_id}"] = _names


def encode(obj):
    return (json.dumps(obj, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def png(rows):
    h, w = len(rows), len(rows[0])
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    raw = b"".join(b"\0" + bytes(c for px in row for c in px) for row in rows)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


class Canvas:
    def __init__(self, size=32):
        self.rows = [[(0, 0, 0, 0)] * size for _ in range(size)]

    def put(self, x, y, c):
        if 0 <= x < len(self.rows) and 0 <= y < len(self.rows):
            self.rows[y][x] = (*c, 255) if len(c) == 3 else c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def ellipse(self, cx, cy, rx, ry, c):
        for y in range(int(cy - ry), int(cy + ry) + 1):
            for x in range(int(cx - rx), int(cx + rx) + 1):
                if ((x-cx)/rx)**2 + ((y-cy)/ry)**2 <= 1:
                    self.put(x, y, c)

    def line(self, x0, y0, x1, y1, c, width=1):
        steps = max(abs(x1-x0), abs(y1-y0), 1)
        for i in range(steps + 1):
            x, y = round(x0 + (x1-x0)*i/steps), round(y0 + (y1-y0)*i/steps)
            self.rect(x, y, x+width-1, y+width-1, c)

    def poly(self, points, c):
        for y in range(min(p[1] for p in points), max(p[1] for p in points) + 1):
            for x in range(min(p[0] for p in points), max(p[0] for p in points) + 1):
                inside = False
                for a, b in zip(points, points[1:] + points[:1]):
                    if (a[1] > y) != (b[1] > y) and x < (b[0]-a[0])*(y-a[1])/(b[1]-a[1]) + a[0]:
                        inside = not inside
                if inside:
                    self.put(x, y, c)

    def outline(self, color=(31, 39, 35)):
        src = [row[:] for row in self.rows]
        for y, row in enumerate(src):
            for x, p in enumerate(row):
                if not p[3] and any(0 <= x+dx < 32 and 0 <= y+dy < 32 and src[y+dy][x+dx][3]
                                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    self.put(x, y, color)


def herb_icon(name):
    c = Canvas()
    dark, green, light = (35, 83, 61), (71, 136, 81), (143, 193, 105)
    if name == "spirit_leaf":
        c.line(13, 27, 18, 6, dark, 2)
        for pts in ([(15,21),(5,17),(4,10),(12,11),(17,17)], [(17,15),(19,6),(27,3),(26,12),(20,17)], [(14,25),(7,24),(5,20),(12,21)]):
            c.poly(pts, green)
        c.line(6,12,15,19,light); c.line(19,14,25,5,light); c.line(9,22,14,24,light)
        c.put(23,8,(212,227,145)); c.put(8,14,(185,217,137))
    elif name == "dew_blossom":
        c.line(16,26,16,13,dark,2); c.poly([(16,24),(8,20),(7,17),(14,19)],green)
        for x,y,rx,ry in ((11,11,5,4),(20,10,5,4),(15,6,4,4),(12,16,4,5),(20,16,4,5)):
            c.ellipse(x,y,rx,ry,(72,141,180)); c.ellipse(x-1,y-1,max(rx-2,1),max(ry-2,1),(142,211,220))
        c.ellipse(16,12,3,3,(236,224,153)); c.put(14,11,(255,248,207))
        c.rect(23,20,24,22,(170,227,242)); c.put(23,19,(226,251,255))
    elif name == "sunroot":
        c.poly([(12,11),(17,10),(20,15),(18,21),(22,26),(18,27),(15,22),(12,28),(9,27),(12,19),(10,15)],(168,69,40))
        c.poly([(13,12),(17,13),(17,19),(14,23),(12,22),(14,17)],(225,133,53))
        c.line(12,15,17,15,(242,174,73)); c.line(14,19,17,18,(116,58,39))
        c.poly([(13,11),(8,7),(7,3),(13,5),(15,9),(18,3),(23,2),(22,7),(17,12)],green)
        c.line(10,5,14,10,light); c.line(17,10,21,4,light)
    elif name == "blood_berry":
        c.line(9,26,20,7,(87,74,43),2); c.poly([(16,12),(11,8),(12,4),(18,6),(20,10)],green)
        c.poly([(20,10),(23,6),(28,6),(26,11)],light)
        for x,y in ((13,17),(21,15),(18,22),(25,21),(10,23)):
            c.ellipse(x,y,4,4,(111,35,56)); c.ellipse(x-1,y-1,3,3,(181,57,69)); c.rect(x-2,y-2,x-1,y-1,(244,150,125)); c.put(x,y-4,dark)
    elif name == "cloud_fungus":
        c.poly([(13,13),(19,13),(18,22),(23,25),(20,27),(10,27),(8,24),(13,22)],(165,130,109))
        c.rect(14,16,16,24,(230,207,158))
        for x,y,rx,ry in ((9,12,6,5),(16,9,7,5),(23,12,5,5)):
            c.ellipse(x,y,rx,ry,(116,80,108)); c.ellipse(x,y-1,rx-1,ry-1,(177,141,165))
        c.line(6,14,24,14,(232,207,185)); c.line(10,10,14,7,(217,185,204)); c.put(22,10,(232,207,185))
    elif name == "calm_lotus":
        c.ellipse(16,24,12,4,dark); c.ellipse(16,23,11,3,(64,128,105)); c.line(16,23,24,25,light)
        for pts in ([(15,22),(7,18),(5,11),(12,13)], [(17,22),(24,18),(26,11),(20,13)], [(16,21),(10,15),(12,7),(17,12)], [(16,21),(22,15),(20,7),(15,12)], [(16,21),(13,13),(16,4),(19,13)]):
            c.poly(pts,(155,205,184))
        c.poly([(16,19),(14,13),(16,7),(18,13)],(223,237,200)); c.rect(14,20,18,21,(226,197,111))
    c.outline()
    return c.rows


MED_PALETTES = ((45, 126, 153), (171, 70, 55), (99, 104, 163))


def formula_icon(kind):
    c = Canvas(); seal = (161,124,58) if kind.startswith("zhuji") else (65,146,114) if kind.startswith("buji") else MED_PALETTES[MEDICINES.index(kind)]
    c.rect(7,4,24,27,(132,88,51)); c.rect(6,5,23,26,(216,184,124)); c.rect(8,5,22,25,(239,218,168))
    c.rect(6,3,23,5,(174,124,71)); c.rect(8,3,21,3,(243,218,168))
    c.rect(6,26,24,28,(174,124,71)); c.rect(8,26,22,26,(245,219,169))
    c.line(10,8,20,8,(148,95,49)); c.line(10,10,18,10,(178,137,80))
    c.rect(10,13,19,22,seal); c.rect(11,14,18,21,(229,213,170))
    # Hand-pixel seal scripts: 回, 元 and 心. Strokes stay readable at 32px.
    if kind == "huiling":
        c.rect(12,15,17,20,seal); c.rect(13,16,16,19,(229,213,170)); c.rect(14,17,15,18,seal)
    elif kind == "yangyuan":
        c.line(13,15,16,15,seal); c.line(12,17,17,17,seal); c.line(14,18,12,20,seal); c.line(16,18,16,20,seal); c.put(17,20,seal)
    else:
        c.line(12,18,12,19,seal); c.line(14,17,14,20,seal); c.line(14,20,16,20,seal); c.put(17,19,seal); c.put(16,15,seal)
    if kind in FOUNDATION_FORMULAS:
        q={"low":1,"middle":2,"high":3}[kind.rsplit("_",1)[1]]
        color=((85,255,85),(85,85,255),(170,85,255))[q-1]
        for n in range(q): c.rect(10+n*4,23,11+n*4,24,color)
    c.put(22,23,(190,80,55)); c.put(21,24,(190,80,55)); c.outline((64,50,38))
    return c.rows


def pill_icon(kind):
    c=Canvas(); base=(161,124,58) if kind=="zhuji" else (65,146,114) if kind=="buji" else MED_PALETTES[MEDICINES.index(kind)]
    c.ellipse(16,18,10,10,tuple(round(v*.45) for v in base))
    c.ellipse(16,16,10,9,base)
    c.ellipse(13,13,6,5,tuple(min(255,round(v*1.25+25)) for v in base))
    c.ellipse(12,11,3,2,(240,239,202)); c.put(10,12,(252,248,220))
    c.line(12,24,19,24,tuple(round(v*.65) for v in base))
    # Each medicine has a different gold imprint, visible beyond color alone.
    gold=(241,204,121)
    if kind == "huiling":
        c.rect(17,16,21,20,gold); c.rect(18,17,20,19,base)
    elif kind == "yangyuan":
        c.line(16,18,22,18,gold); c.line(19,15,19,21,gold)
    elif kind == "zhuji":
        c.line(16,16,22,16,gold); c.line(19,16,19,22,gold); c.line(16,22,22,22,gold)
    elif kind == "buji":
        c.line(16,17,19,14,gold); c.line(19,14,22,17,gold); c.line(19,17,19,22,gold)
    else:
        c.line(17,17,17,21,gold); c.line(17,21,21,21,gold); c.put(22,19,gold); c.put(20,15,gold)
    c.outline((36,40,44)); return c.rows


def fruit_icon(high=False):
    c=Canvas(); base=(126,82,160) if high else (88,143,63)
    c.ellipse(16,19,9,9,tuple(int(v*.45) for v in base));c.ellipse(16,17,9,8,base)
    c.ellipse(13,14,5,4,tuple(min(255,v+45) for v in base));c.ellipse(12,12,2,1,(238,225,170))
    c.line(16,10,17,4,(111,81,43),2);c.poly([(17,8),(20,3),(26,4),(22,9)],(70,121,65))
    c.line(17,8,23,5,(157,178,80));c.line(18,20,21,17,(231,198,108));c.line(18,20,20,23,(231,198,108))
    c.outline((34,43,29));return c.rows


def furnace_texture():
    # Sixteen 16px material tiles in a single 64px atlas. No pseudo-random state.
    c = Canvas(64)
    colors=((111,92,55),(160,126,68),(63,104,91),(53,49,39), (226,117,38),(208,171,88),(82,67,44),(36,64,60))
    for y in range(64):
        for x in range(64):
            tile=(y//16)*4+x//16; col=colors[tile%len(colors)]
            u,v=x%16,y%16; noise=((u*37+v*19+u*v*3)%9)-4
            if tile in (0,1,6) and v in (2,13): noise+=8
            if tile==2 and (u+2*v)%13 < 3: noise+=12
            if tile==4: col=(240,100+v*6,30+v*2); noise=0
            if tile==5 and (u in (1,14) or v in (1,14)): noise-=25
            c.put(x,y,tuple(max(0,min(255,z+noise)) for z in col))
    return c.rows


def tile_uv(tile):
    x,y=(tile%4)*4,(tile//4)*4
    return [x,y,x+4,y+4]


def furnace_elements(lit=False):
    els=[]
    def box(name, lo, hi, tile=0, north=None):
        els.append({"name":name,"from":lo,"to":hi,"faces":{face:{"uv":tile_uv(tile if face!="north" or north is None else north),"texture":"#atlas"} for face in ("down","up","north","south","west","east")}})
    def octagon(name,y0,y1,r,tile):
        # Disjoint narrow strips cut broad chamfers, retaining exact cap UVs
        # without overlapping coplanar faces or invalid polygon elements.
        b=8-r; cut=r*.42; steps=3; step=cut/steps
        box(name+"_center",[b+cut,y0,b],[16-b-cut,y1,16-b],tile)
        for i in range(steps):
            x=b+i*step; inset=cut-i*step
            box(name+f"_west_{i}",[x,y0,b+inset],[x+step,y1,16-b-inset],tile)
            box(name+f"_east_{i}",[16-x-step,y0,b+inset],[16-x,y1,16-b-inset],tile)
    for name,x,z in (("front_left",4,4),("front_right",10,4),("rear",7,11)):
        box(name+"_foot",[x-.5,0,z-.5],[x+2.5,1,z+2.5],2)
        box(name+"_leg",[x,.8,z],[x+2,4.5,z+2],0)
        box(name+"_anklet",[x-.2,1.4,z-.2],[x+2.2,2.1,z+2.2],1)
    octagon("belly_base",3.5,4.3,4,6)
    octagon("belly_lower",4.3,5.4,5,0)
    octagon("belly",5.4,9.3,6,0)
    octagon("shoulder",9.3,10.3,5.5,0)
    octagon("neck",10.3,11.5,4.8,2)
    octagon("rim",11.5,12.3,5.8,1)
    octagon("lid",12.3,13.2,5.3,0)
    octagon("lid_step",13.2,14,4,2)
    box("knob_stem",[7.3,14,7.3],[8.7,14.8,8.7],1)
    box("knob_jade",[6.5,14.8,6.5],[9.5,16,9.5],2)
    for side in ("west","east"):
        x0,x1=(.5,3.5) if side=="west" else (12.5,15.5)
        box(side+"_handle_lower",[x0,8,7],[x1,8.8,9],1)
        box(side+"_handle_upper",[x0,11.2,7],[x1,12,9],1)
        a,b=(x0,x0+.8) if side=="west" else (x1-.8,x1)
        box(side+"_handle_outer",[a,8.8,7],[b,11.2,9],2)
    box("fire_vent",[5.5,6,1.93],[10.5,8.3,1.98],4 if lit else 3)
    for x in (5.5,7.7,9.9):
        box("vent_bar_"+str(x),[x,5.9,1.84],[x+.6,8.4,1.92],6)
    box("vent_header",[5.2,8.25,1.82],[10.8,8.8,2.05],1)
    box("vent_sill",[5.2,5.55,1.82],[10.8,6.1,2.05],1)
    for x in (5.2,10.2):
        box("front_rivet_"+str(x),[x,8.9,1.9],[x+.6,9.2,2.12],5)
    box("rear_seal",[6,6.8,14.02],[10,8.8,14.1],2)
    return els


def outputs(root=ROOT):
    assets=Path(root)/"src/main/resources/assets/myvillage"
    data=Path(root)/"src/main/resources/data"
    result={assets/"textures/block/alchemy_furnace.png":png(furnace_texture())}
    for lit in (False,True):
        suffix="_lit" if lit else ""
        result[assets/f"models/block/alchemy_furnace{suffix}.json"]=encode({"ambientocclusion":True,"textures":{"atlas":"myvillage:block/alchemy_furnace","particle":"myvillage:block/alchemy_furnace"},"elements":furnace_elements(lit)})
    result[assets/"blockstates/alchemy_furnace.json"]=encode({"variants":{f"facing={d},lit={str(lit).lower()}":{"model":"myvillage:block/alchemy_furnace"+("_lit" if lit else ""),"y":r} for lit in (False,True) for d,r in (("north",0),("east",90),("south",180),("west",270))}})
    result[assets/"models/item/alchemy_furnace.json"]=encode({"parent":"myvillage:block/alchemy_furnace","display":{
        "gui":{"rotation":[25,225,0],"translation":[0,0,0],"scale":[.8,.8,.8]},
        "ground":{"translation":[0,3,0],"scale":[.35,.35,.35]},
        "fixed":{"rotation":[0,180,0],"scale":[.6,.6,.6]},
        "firstperson_righthand":{"rotation":[0,135,0],"translation":[0,2,0],"scale":[.5,.5,.5]},
        "firstperson_lefthand":{"rotation":[0,45,0],"translation":[0,2,0],"scale":[.5,.5,.5]},
        "thirdperson_righthand":{"rotation":[75,45,0],"translation":[0,2.5,0],"scale":[.45,.45,.45]}}})
    for item in ITEM_IDS[1:]:
        rows=fruit_icon(item.endswith("high")) if item.startswith("ningyuan_fruit_") else herb_icon(item) if item in HERBS else formula_icon(item[8:]) if item.startswith("formula_") else pill_icon(item[5:])
        result[assets/f"textures/item/{item}.png"]=png(rows)
        result[assets/f"models/item/{item}.json"]=encode({"parent":"minecraft:item/generated","textures":{"layer0":f"myvillage:item/{item}"}})
    def shapeless(item,ingredients):
        result[data/f"myvillage/recipe/{item}.json"]=encode({"type":"minecraft:crafting_shapeless","category":"misc","ingredients":[{"tag":i[1:]} if i.startswith("#") else {"item":i} for i in ingredients],"result":{"id":"myvillage:"+item,"count":1}})
    for herb,source in zip(HERBS,("fern","blue_orchid","carrot","sweet_berries","brown_mushroom","lily_pad")):
        shapeless(herb,["minecraft:"+source,"#c:spirit_stones/low_grade"])
    for i,kind in enumerate(MEDICINES):
        shapeless("formula_"+kind,["minecraft:paper",*("myvillage:"+h for h in HERBS[i*2:i*2+2]),"#c:spirit_stones/low_grade"])
    # Fruit infusion follows existing herb acquisition; pills themselves exist only as furnace recipes.
    shapeless("ningyuan_fruit_low",["minecraft:apple","myvillage:spirit_leaf","myvillage:calm_lotus",*(["#c:spirit_stones/low_grade"]*4)])
    shapeless("ningyuan_fruit_high",["myvillage:ningyuan_fruit_low","minecraft:ghast_tear","minecraft:blaze_powder",*(["myvillage:medium_grade_spirit_stone"]*4)])
    for kind in FOUNDATION_FORMULAS:
        shapeless("formula_"+kind,["minecraft:paper","myvillage:ningyuan_fruit_"+("high" if kind.endswith("high") else "low"),"myvillage:spirit_leaf","myvillage:calm_lotus","#c:spirit_stones/low_grade"])
    result[data/"myvillage/recipe/alchemy_furnace.json"]=encode({"type":"minecraft:crafting_shaped","category":"misc","pattern":["CIC","CFC","C C"],"key":{"C":{"item":"minecraft:copper_ingot"},"I":{"item":"minecraft:iron_ingot"},"F":{"item":"minecraft:furnace"}},"result":{"id":"myvillage:alchemy_furnace","count":1}})
    result[data/"myvillage/loot_table/blocks/alchemy_furnace.json"]=encode({"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"myvillage:alchemy_furnace"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}],"random_sequence":"myvillage:blocks/alchemy_furnace"})
    return result


def merged_outputs(root=ROOT):
    """Check/merge only our lang keys and tag value; preserve other owners' keys."""
    root=Path(root); result={}
    for n,language in enumerate(("en_us","zh_cn")):
        path=root/f"src/main/resources/assets/myvillage/lang/{language}.json"
        obj=json.loads(path.read_text()) if path.exists() else {}
        obj.update({key:value[n] for key,value in LANG.items()})
        if language=="zh_cn":
            obj.update({"item.myvillage."+key:value for key,value in ZH_FOUNDATION.items()})
            obj.update(ZH_EXTRA)
        result[path]=encode(obj)
    path=root/"src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json"
    obj=json.loads(path.read_text()) if path.exists() else {"replace":False,"values":[]}
    if "myvillage:alchemy_furnace" not in obj["values"]:
        obj["values"].append("myvillage:alchemy_furnace")
    result[path]=encode(obj)
    return result


def preview():
    if str(ROOT) not in sys.path: sys.path.insert(0,str(ROOT))
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
    from tools.combat_preview import item_model as pim
    from tools.beastgen.preview import Frame, render
    out=ROOT/"out/preview/alchemy"; out.mkdir(parents=True,exist_ok=True)
    assets=pim.Assets([ROOT/"src/main/resources"],None)
    for lit in (False,True):
        ref="myvillage:block/alchemy_furnace"+("_lit" if lit else "")
        resolved=pim.resolve_model(assets,assets.model_json(ref),ref)
        if resolved.errors or resolved.warnings: raise ValueError(resolved.errors+resolved.warnings)
        quads=[((q.pos-np.array([8,0,8]))/16,q.uv,q.tex,None) for idx,el in enumerate(resolved.elements) for q in pim.element_quads(assets,resolved,el,idx)]
        for view,cam in (("front",(0,.28,-1)),("quarter",(-1,.6,-1.3)),("rear",(1,.5,1.3))):
            frame=Frame(cam,640,640,460,(0,.5,0),ss=2)
            render(quads,frame,bg=(30,39,37),body_rot=180).save(out/f"furnace_{'lit' if lit else 'unlit'}_{view}.png")
    font=ImageFont.truetype("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",19)
    small=ImageFont.truetype("/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",12)
    sheet=Image.new("RGB",(1040,780),(24,32,31)); draw=ImageDraw.Draw(sheet)
    for i,item in enumerate(ITEM_IDS):
        col,row=i%5,i//5; x,y=col*208,row*260
        draw.rounded_rectangle((x+8,y+8,x+199,y+251),8,fill=(37,47,44),outline=(70,83,73))
        if item=="alchemy_furnace":
            im=Image.open(out/"furnace_unlit_quarter.png").convert("RGBA").resize((180,180))
        else:
            im=Image.open(ASSETS/f"textures/item/{item}.png").resize((160,160),Image.Resampling.NEAREST)
        sheet.paste(im,(x+(208-im.width)//2,y+20),im)
        draw.text((x+104,y+202),NAMES[item][1],font=font,fill=(236,222,184),anchor="mt")
        draw.text((x+104,y+231),item,font=small,fill=(160,185,172),anchor="mt")
    sheet.save(out/"items.png")
    panels="".join(f'<figure><img src="furnace_{state}_{view}.png"><figcaption>{state} · {view}</figcaption></figure>' for state in ("unlit","lit") for view in ("front","quarter","rear"))
    (out/"index.html").write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>炼丹基础 · 实际资源预览</title><style>body{background:#18201f;color:#e8dfc6;font:16px system-ui;margin:30px}main{max-width:1250px;margin:auto}p{color:#b1c4b6;line-height:1.7}section{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}figure{margin:0;background:#25302c}figure img{width:100%}figcaption{padding:12px}img.sheet{width:100%;image-rendering:pixelated}h1{font-weight:550}</style><main><h1>青铜丹炉与十二味炼丹物品</h1><p>三足承炉、双耳透空、阶式圆腹、青玉盖钮、北向火窗。以下直接读回游戏 JSON 模型与纹理后离线渲染，点火只改变火窗。模型 16 像素高，宽 15 像素；药材、丹方、丹药为独立 32px 图标。</p><img class="sheet" src="items.png"><p><a style="color:#d9c586" href="runtime/index.html">炼制、服药与重启验证</a> · <a style="color:#d9c586" href="visual-runtime/index.html">实机丹炉与英文界面</a></p><h2>实际模型：冷炉与开炉</h2><section>'''+panels+'''</section><p>开发者检查：图标辨识、模型结构与引用；玩家尚未验收：实机材质、光照、摆放、手持、界面、炼制与服药体验。人类视觉结论：pending。</p></main></html>''',encoding="utf-8")
    print(f"Preview: {out/'index.html'}")


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check",action="store_true")
    parser.add_argument("--preview",action="store_true")
    parser.add_argument("--root",type=Path,default=ROOT,help="Alternate project root for isolated resource checks")
    args=parser.parse_args(argv)
    generated={**outputs(args.root),**merged_outputs(args.root)}; stale=[]
    for path,content in generated.items():
        if args.check:
            if not path.is_file() or path.read_bytes()!=content: stale.append(str(path.relative_to(args.root)))
        else:
            path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(content)
    if stale:
        print("Stale alchemy resources:\n"+"\n".join(stale)); return 1
    print(f"Alchemy resources {'match' if args.check else 'generated'} ({len(generated)} files).")
    if args.preview:
        if args.root != ROOT: parser.error("--preview reads actual repository resources; use the default --root")
        preview()
    return 0


if __name__=="__main__":
    raise SystemExit(main())
