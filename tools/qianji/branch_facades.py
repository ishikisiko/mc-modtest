"""七州千机阁围院的楼房立面。坐标以开间横向、楼板高度和向内进深表示。"""
from dataclasses import dataclass
from functools import lru_cache
import json
from pathlib import Path

AIR = "minecraft:air"
DESIGN = Path(__file__).resolve().parents[2] / "blueprints/qianji/studies/branch_facades.json"


@lru_cache(maxsize=1)
def design():
    return json.loads(DESIGN.read_text())


def posts(span):
    """两边的柱对称，中间留出较宽的一间，余下长度按实际面宽分开。"""
    half = 5 if span >= 24 else 4 if span >= 13 else max(2, (span - 3) // 2)
    shoulder = max(1, (span - 1) // 2 - half)
    count = max(1, (shoulder + 3) // 7)
    left = {round(i * shoulder / count) for i in range(count + 1)}
    return sorted(left | {span - 1 - i for i in left})


@dataclass
class Face:
    model: object
    materials: dict
    layer: str
    origin: tuple
    along: tuple
    inward: tuple
    width: int
    height: int
    side: str

    def put(self, u, y, depth, material):
        x, floor, z = self.origin
        self.model.put(x + u*self.along[0] + depth*self.inward[0], floor + y,
                       z + u*self.along[1] + depth*self.inward[1],
                       self.materials.get(material, material), self.layer)

    def strip(self, first, stop, y, depth, material):
        for u in range(first, stop):
            self.put(u, y, depth, material)

    def pane(self, block):
        # 格扇随墙面转向；院落整体旋转时，原版连接方向由城市放置器一并旋转。
        ew, ns = ("true", "false") if self.along[0] else ("false", "true")
        return (f"minecraft:{block}"
                f"[east={ew},north={ns},south={ns},waterlogged=false,west={ew}]")

    @property
    def glass(self):
        return self.pane(self.materials["glazing"]+"_stained_glass_pane")


class BranchFacade:
    def __init__(self, region):
        spec = design()
        self.materials = spec["materials"] | spec["provinces"][region]
        self.uses = spec["buildings"]
        self.dials = sorted(spec["dial"], key=len, reverse=True)
        self.mirror_dial = region in spec["mirror_dial"]

    def storey(self, model, room, index, node, layer):
        x, floor, z = node.origin
        w, h, d = node.size
        cut = max(2, min(w, d)//5) if room.get("shape") == "octagon" else 0
        if cut:
            self.octagonal_corners(model, node, layer, cut)
        sides = (
            ("front", x+cut, z,       (1, 0), (0, 1),  w-2*cut),
            ("back",  x+cut, z+d-1,   (1, 0), (0, -1), w-2*cut),
            ("left",  x,     z+cut,   (0, 1), (1, 0),  d-2*cut),
            ("right", x+w-1, z+cut,   (0, 1), (-1, 0), d-2*cut),
        )
        role = self.uses[room["id"]]
        principal = room["id"] == model.spec["rooms"][-1]["id"]
        top_floor = len(room["levels"])-1
        for side, px, pz, along, inward, width in sides:
            face = Face(model, self.materials, layer, (px, floor, pz), along, inward, width, h, side)
            self.wall(face, role, index, principal, top_floor)

    def octagonal_corners(self, model, node, layer, cut):
        """斜面砌成有厚度的折角墙，楼板、木枋和腰线都沿八角轮廓收口。"""
        x, floor, z = node.origin
        w, h, d = node.size
        for x0, dx in ((x, 1), (x+w-1, -1)):
            for z0, dz in ((z, 1), (z+d-1, -1)):
                for i in range(cut):
                    for j in range(cut-i):
                        model.put(x0+i*dx, floor-1, z0+j*dz, AIR, layer)
                for i in range(1, cut):
                    xx, zz = x0+i*dx, z0+(cut-i)*dz
                    for y in range(h):
                        material = "plaster"
                        if y == 0:
                            material = "foot"
                        elif y in (1, h-2):
                            material = "panel"
                        elif y == h-1 or (i == cut//2 and 2 <= y < h-2):
                            material = "wood"
                        model.put(xx, floor+y, zz, self.materials[material], layer)
                        # 方块在斜线外侧只有点接触，内侧这排砌块封住斜缝。
                        model.put(xx+dx, floor+y, zz, self.materials[material], layer)

    def wall(self, face, role, storey, principal, top_floor):
        marks = posts(face.width)
        center = face.width//2
        porch = role == "office" and storey == 0 and face.side == "front"
        depth = 2 if porch else 0
        for i, (left, right) in enumerate(zip(marks, marks[1:])):
            central = left < center < right
            for y in range(face.height):
                material = "plaster"
                if y == 0:
                    material = "foot"
                elif y == 1 or y == face.height-2:
                    material = "panel"
                elif y == face.height-1:
                    material = "wood"
                face.strip(left+1, right, y, depth, material)

            focus = central and principal and face.side == "front"
            if focus and storey == top_floor:
                self.dial(face, left, right)
            elif focus and storey > 0:
                self.map_window(face, left, right)
            elif central and storey == 0 and face.side in ("front", "back"):
                self.door(face, left, right, depth, role)
                if focus:
                    self.entry_canopy(face, left, right)
            elif role == "supply" and (i % 2 == 0 or face.side == "back"):
                self.shutter(face, left, right, depth)
            elif role == "archive" and face.side != "front" and i % 3 == 0:
                self.shutter(face, left, right, depth)
            elif (principal and storey == 0 and face.side == "front" and right-left >= 5
                  and abs(i-(len(marks)-2)//2) == 1):
                self.tablet(face, left, right)
            else:
                self.window(face, left, right, depth, role, storey)
        for mark in marks:
            self.column(face, mark, depth)
        if porch:
            self.public_gallery(face, marks)

    def column(self, face, u, depth):
        for y in range(face.height):
            material = "post"
            if y == 0:
                material = "foot"
            elif y == face.height-2:
                material = "wood"
            face.put(u, y, depth, material)
        if u in (0, face.width-1):
            return
        face.put(u, face.height-2, depth-1, "wood")
        face.strip(u-1, u+2, face.height-1, depth-1, "bar")
        face.put(u, 0, depth-1, "ledge")

    def public_gallery(self, face, marks):
        """柱在檐下，厅墙后退两格。榜前有单独的小开口，沿廊可以走到正厅。"""
        h = face.height
        for u in marks:
            self.column(face, u, 0)
        face.strip(0, face.width, h-1, 0, "wood")
        face.strip(0, face.width, h-1, 1, "panel")
        for u in marks[1:-1]:
            for s in (-1, 1):
                face.put(u+s, h-2, 0, "panel")
        bx = face.model.spec["reader"][0] - face.origin[0]
        for u in range(max(1,bx-1), min(face.width-1,bx+2)):
            for y in range(min(4, h-2)):
                face.put(u, y, 2, AIR)
                face.put(u, y, 1, AIR)

    def door(self, face, left, right, depth, role):
        center = face.width//2
        radius = 2 if role in ("office", "command") and right-left >= 8 else 1
        clear = min(face.height-3, 6 if role == "command" else 5)
        for u in range(center-radius, center+radius+1):
            for y in range(clear):
                face.put(u, y, depth, AIR)
        for u in (center-radius-1, center+radius+1):
            for y in range(clear):
                face.put(u, y, depth, "panel" if y != 0 else "foot")
            face.put(u, clear-1, depth-1, "wood")
        face.strip(center-radius-1, center+radius+2, clear, depth, "wood")
        face.strip(center-radius-1, center+radius+2, clear, depth-1, "ledge")
        if clear+1 < face.height-1:
            face.put(center, clear+1, depth, "boss")

    def entry_canopy(self, face, left, right):
        """院内主阶尽头的门廊。双柱直接落到台基，短檐在首层檐带以下收住。"""
        h = min(8, face.height-3)
        for u in (left, right):
            # 门廊往外挑三格，给前柱补足台基与平台之间的高差。
            x, floor, z = face.origin
            xx, zz = x+u*face.along[0]-2*face.inward[0], z+u*face.along[1]-2*face.inward[1]
            for y in range(face.model.terrain[zz*face.model.w+xx]+1-floor, 1):
                face.put(u, y, -2, "foot")
            for y in range(1, h):
                face.put(u, y, -2, "post")
            face.put(u, h-1, -3, "wood")
            face.strip(u-1, u+2, h, -2, "wood")
        for depth in range(-3, 1):
            face.strip(left, right+1, h, depth, "panel")
            face.strip(left-1, right+2, h+1, depth, "roof" if depth >= -1 else "roof_slab")
        face.strip(left-1, right+2, h+2, 0, "roof_slab")
        for u in (left-1, right+1):
            face.put(u, h+1, -3, "roof")

    def map_window(self, face, left, right):
        """向庭中挑出两格的勘图窗。室内走进窗台后，左右两侧均能看到庭院。"""
        h = min(face.height-3, 7)
        for depth in (-1, -2):
            face.strip(left, right+1, -1, depth, "floor")
            face.strip(left, right+1, h+1, depth, "panel")
        for u in (left, right):
            for depth in (-1, -2):
                for y in range(h+1):
                    face.put(u, y, depth, "post" if depth == -2 else "panel")
            for y in range(2, h-1):
                face.put(u, y, -1, "grille")
            face.put(u, -2, -1, "wood")
        middle = (left+right)//2
        clear_pane = face.pane("glass_pane")
        for u in range(left+1, right):
            for y in range(h):
                face.put(u, y, 0, AIR)
                material = clear_pane if abs(u-middle) <= 2 else face.glass
                if y == 0:
                    material = "panel"
                if y == h-1 or abs(u-middle) == 3:
                    material = "wood" if y != h-1 else "grille"
                face.put(u, y, -2, material)
            face.put(u, h, -2, "wood")
        face.strip(left, right+1, 0, -3, "bar")
        for depth in (-3,-2,-1):
            face.strip(left-1, right+2, h+2, depth, "roof_slab")

    def window(self, face, left, right, depth, role, storey):
        """窗芯退进墙内。木枋挑出墙面，格扇和铜漏窗由实物构件组成。"""
        free = right-left-1
        if free < 3:
            return
        wide = min(free-2 if free >= 5 else free, 7 if role == "command" else 6)
        first = left+1+(free-wide)//2
        stop = first+wide
        low = 3 if (role == "archive" or storey > 0) and face.height >= 9 else 2
        high = min(face.height-3, low+(4 if role == "command" else 3))
        inset = depth if depth else 1
        for y in range(low, high+1):
            for u in range(first, stop):
                face.put(u, y, depth, AIR)
                face.put(u, y, inset, face.glass)
                # 书楼用对称的步阶边饰，议事厅的长窗中央留木梃。
                middle = wide >= 5 and u-first == wide//2
                corner = role == "archive" and y == high and u in (first, stop-1)
                if corner or (middle and role != "office"):
                    face.put(u, y, depth, "wood")
                elif y == high and (role == "archive" or storey > 0):
                    face.put(u, y, depth, "grille")
                elif depth == 0 and y == low+1 and high-low >= 2:
                    face.put(u, y, depth, "bar")
        face.strip(first, stop, low-1, depth, "panel")
        face.strip(first, stop, high+1, depth, "wood")
        face.strip(first, stop, low-1, depth-1, "ledge")
        if role == "archive" and high-low >= 3 and wide >= 4:
            for u in (first, stop-1):
                face.put(u, high-1, depth, "wood")

    def shutter(self, face, left, right, depth):
        free = right-left-1
        if free < 3:
            return
        mid = (left+right)//2
        low = 3 if face.height >= 9 else 2
        high = min(face.height-3, low+2)
        cols = (mid-1, mid+1) if free >= 5 else (mid,)
        for u in cols:
            for y in range(low, high+1):
                face.put(u, y, depth, "grille")
            face.put(u, high+1, depth, "wood")
        face.strip(cols[0], cols[-1]+1, low-1, depth-1, "ledge")
        if free >= 6:
            face.put(mid, 1, depth, "boss")

    def tablet(self, face, left, right):
        """主门两侧设竖向刻度龛，窄高窗位于龛顶，让下层能从院中采光。"""
        center = (left+right)//2
        for y in range(2, face.height-3):
            face.strip(center-1, center+2, y, 0, "panel")
            face.put(center, y, 0, "reveal")
        top = face.height-4
        for y in range(top-1, top+1):
            face.put(center, y, 0, AIR)
            face.put(center, y, 1, face.glass)
        for y in range(3, top-2, 2):
            face.put(center, y, -1, "dial_spindle")
        face.put(center, 2, 0, "boss")
        face.strip(center-1, center+2, 1, -1, "ledge")
        face.strip(center-1, center+2, top+1, -1, "bar")

    def dial(self, face, left, right):
        """外盘嵌墙，薄铜片拼出斜轨。水平铜轴和星钉凸出盘面。"""
        rows = next(r for r in self.dials if len(r) <= min(right-left-1, face.height-2))
        center = face.width//2
        n = len(rows)
        first, bottom = center-n//2, (face.height-n)//2
        for y in range(bottom, bottom+n):
            face.strip(left+1, right, y, 0, "reveal")
        for i, row in enumerate(rows):
            for j, symbol in enumerate(row[::-1] if self.mirror_dial else row):
                if symbol == " ":
                    continue
                u, y = first+j, bottom+n-1-i
                if symbol in "oa":
                    face.put(u, y, 0, "bronze" if symbol == "o" else "patina")
                if symbol in "a.":
                    face.put(u, y, -1, "dial_spindle")
                elif symbol == "s":
                    face.put(u, y, -1, "dial_plate")
                elif symbol == "-":
                    face.put(u, y, -1, "dial_axis")
                elif symbol == "*":
                    face.put(u, y, 0, "pin")
                    face.put(u, y, -1, "dial_spindle")
        for u in (left, right):
            for y in range(bottom, bottom+n):
                face.put(u, y, -1, "post")
            face.put(u, bottom, -2, "boss")
            face.put(u, bottom+n-1, -2, "wood")
        face.strip(left, right+1, bottom-1, -1, "ledge")
        face.strip(left, right+1, bottom+n, -1, "wood")
