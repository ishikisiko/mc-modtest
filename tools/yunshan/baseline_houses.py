"""Hand-authored sample houses for the scale probe, vanilla blocks only.
Local coordinates: x east, y up, z south; the front faces +z. y=0 is the first layer above the pad.
Each builder returns (structure, attachments): structure blocks can go in any order,
attachments (doors, lanterns, ladders, beds) must follow, in list order."""
ROOF = "deepslate_tile"
TILES = "deepslate_tiles"
WALL = "calcite"
STONE = "stone_bricks"
PLANK = "spruce_planks"
LOG = "spruce_log"

def stairs(facing, top=False, mat=ROOF): return f"{mat}_stairs[facing={facing},half={'top' if top else 'bottom'}]"
def slab(top=False, mat=ROOF): return f"{mat}_slab[type={'top' if top else 'bottom'}]"
def log(axis="y"): return f"{LOG}[axis={axis}]"
def trap(kind, facing="south"): return f"{kind}_trapdoor[facing={facing},half=bottom,open=true]"

def curve(u, c=0.62): return (1 - c) * u + c * u * u

def roof_profile(run, rise=None, two_cell_ridge=False):
    """Top-surface levels (half blocks above the middle of the wall-top row) for slope cells
    0..n-1, cell 0 being the eave overhang: a top slab, a bottom slab, then stairs all the way
    (gentle at the eave, 45 degrees above, the usual way to get the concave Chinese roof line)."""
    n = run + (1 if two_cell_ridge else 0)
    return [i + 1 if i <= 2 else 3 + 2 * (i - 2) for i in range(n)]

def slope_block(T, i, wt, up):
    """(y, state) of slope cell i for wall-top row wt; `up` is the stairs facing (towards the ridge)."""
    a = 2 * wt + 1 + T[i]; d = T[i] - T[i - 1] if i else 0
    if a % 2 == 0:
        y = a // 2 - 1
        return (y, stairs(up) if d >= 1 else slab(top=True))
    return ((a - 1) // 2, slab())

# ---------------------------------------------------------------------------------- 江南, gable to the street
def jiangnan(scale):
    big = scale > 1
    S, A = {}, []
    if not big:
        W, D, H, base = 7, 7, 3, 0          # floor flush with the pad (y = -1), walls y 0..2
        cols_x = [0, 2, 4, 6]; side_cols = [0, 3, 6]
    else:
        W, D, H, base = 11, 11, 5, 1        # one-block platform, walls y 1..5
        cols_x = [0, 3, 7, 10]; side_cols = [0, 5, 10]
    wt = base + H - 1; front = D - 1; c = W // 2
    # floor / platform
    for x in range(W):
        for z in range(D):
            edge = x in (0, W - 1) or z in (0, D - 1)
            S[(x, base - 1, z)] = STONE if edge else PLANK
    # walls: timber frame with plaster infill
    for y in range(base, wt + 1):
        for x in range(W):
            for z in (0, front):
                S[(x, y, z)] = log() if x in (0, W - 1) else WALL
        for z in range(D):
            for x in (0, W - 1):
                S[(x, y, z)] = log() if z in side_cols else WALL
    for x in range(1, W - 1):
        S[(x, wt, 0)] = log("x"); S[(x, wt, front)] = log("x")
    for z in range(1, D - 1):
        if z not in side_cols: S[(0, wt, z)] = log("z"); S[(W - 1, wt, z)] = log("z")
    # front facade: columns, sill panels, lattice windows, door
    for x in cols_x:
        for y in range(base, wt): S[(x, y, front)] = log()
    door_x = c
    for x in range(1, W - 1):
        if x in cols_x or x == door_x: continue
        if big: S[(x, base, front)] = PLANK
        for y in range(base + (1 if big else 0), wt): S[(x, y, front)] = trap("bamboo")
    for y in (base, base + 1): S.pop((door_x, y, front), None)
    A.append(((door_x, base, front), "spruce_door[facing=north,half=lower,hinge=left]"))
    A.append(((door_x, base + 1, front), "spruce_door[facing=north,half=upper,hinge=left]"))
    for y in range(base + 2, wt): S[(door_x, y, front)] = trap("bamboo")
    if big:
        S[(door_x, wt - 1, front)] = "stripped_bamboo_block[axis=x]"          # plaque over the door
        for x in (door_x - 1, door_x + 1): S[(x, wt - 1, front)] = PLANK
        for x in (door_x - 1, door_x, door_x + 1): S[(x, base - 1, D)] = stairs("north", mat="stone_brick")
        S[(c, base + 2, 0)] = trap("bamboo", "north"); S[(c, base + 3, 0)] = trap("bamboo", "north")   # rear window
    # roof: ridge runs front to back, eaves on the two sides, one cell of overhang in front
    run = c + 1; rise = 0.72 * (W / 2)
    T = roof_profile(run, rise)
    zs = range(0, D + 1)
    top_at = {}
    for i in range(run):
        for xx, up in ((i - 1, "east"), (W - i, "west")):
            y, st = slope_block(T, i, wt, up)
            for z in zs:
                gable_line = z in (0, front)
                S[(xx, y, z)] = TILES if (gable_line and st.endswith("type=top]") and 0 <= xx < W) else st
            top_at[xx] = y
    yr = top_at[c - 1]
    last_is_stairs = "stairs" in S[(c - 1, yr, 1)]
    for z in zs:
        S[(c, yr, z)] = TILES
        if last_is_stairs: S[(c, yr + 1, z)] = slab()
    cap = yr + (2 if last_is_stairs else 1)
    if big: S[(c, cap, 0)] = "deepslate_tile_wall"; S[(c, cap, D)] = "deepslate_tile_wall"
    # gable infill, front and back
    for z in (0, front):
        for x in range(W):
            lim = yr if x == c else top_at[x]
            for y in range(wt + 1, lim): S[(x, y, z)] = WALL
        if big:
            for y in range(wt + 1, yr): S[(c, y, z)] = log()
            for x in range(c - 2, c + 3):
                if S.get((x, wt + 2, z)) == WALL and x != c: S[(x, wt + 2, z)] = log("x")
    # lanterns under the front overhang, inside light, furniture
    for x in ((cols_x[1], cols_x[2]) if big else (1, W - 2)):
        y = top_at[x] if x != c else yr
        A.append(((x, y - 1, D), "lantern[hanging=true]"))
    A.append(((c, yr - 1, D // 2), "lantern[hanging=true]"))
    A.append(((1, base, 1), "barrel[facing=up]"))
    A.append(((W - 2, base, 2), "red_bed[facing=north,part=foot]")); A.append(((W - 2, base, 1), "red_bed[facing=north,part=head]"))
    if big:
        A.append(((1, base, 2), "crafting_table")); A.append(((1, base, D - 3), "bookshelf")); A.append(((1, base + 1, D - 3), "bookshelf"))
        A.append(((W - 2, base, D - 3), "spruce_stairs[facing=east]"))        # a seat
        for x in (c - 1, c, c + 1):
            for z in (c - 1, c): A.append(((x, base, z), "red_carpet"))
    return S, A, dict(W=W, D=D, door_x=door_x, height=cap + 1, base=base)

# ---------------------------------------------------------------------------------- 徽派, two storeys, stepped gables
HUI_WALL = "smooth_quartz"      # clean warm white; calcite read as grey noise on a wall this large
BARS = "dark_oak_fence"         # window bars set in a real opening, so the window is dark and has depth

def hui(scale):
    big = scale > 1
    S, A = {}, []
    if not big:
        W, D, base = 9, 6, 0
        f1, floor2, f2 = (0, 2), 3, (4, 6)                 # storey rows
        win1 = [(2, 1, 1), (6, 1, 1)]; win2 = [(x, 5, 1) for x in (2, 4, 6)]; rear = [(2, 5, 1), (6, 5, 1)]   # x, y, height
        zones = [[0], [1, 2, 3, 4], [5]]
    else:
        W, D, base = 13, 9, 1
        f1, floor2, f2 = (1, 4), 5, (6, 10)
        win1 = [(2, 3, 1), (10, 3, 1)]; win2 = [(x, 7, 2) for x in (2, 5, 7, 10)]; rear = [(3, 7, 2), (9, 7, 2)]
        zones = [[0], [1, 2], [3, 4, 5], [6, 7], [8]]
    wt = f2[1]; front = D - 1; door_x = W // 2
    # floor, footing course, apron in front, pads under the rear piers
    for x in range(W):
        for z in range(D):
            edge = x in (0, W - 1) or z in (0, front)
            S[(x, base - 1, z)] = STONE if edge else PLANK
    for x in range(W): S[(x, base - 1, D)] = STONE
    for gx in (0, W - 1): S[(gx, base - 1, -1)] = STONE
    # walls
    for y in range(base, wt + 1):
        for x in range(W):
            for z in (0, front): S[(x, y, z)] = HUI_WALL
        for z in range(D):
            for x in (0, W - 1): S[(x, y, z)] = HUI_WALL
    if big:                                                                  # timber band under the eaves
        for x in range(1, W - 1): S[(x, wt, front)] = log("x"); S[(x, wt, 0)] = log("x")
    # upper floor
    for x in range(1, W - 1):
        for z in range(1, front): S[(x, floor2, z)] = PLANK
    # windows: barred openings, small and high on the ground floor
    for z, wins in ((front, win1 + win2), (0, rear)):
        for x, y, h in wins:
            for k in range(h): S[(x, y + k, z)] = BARS
    # door with a tiled hood (门罩); at 1.5 a stone frame with a carved lintel under it
    for y in (base, base + 1): S.pop((door_x, y, front), None)
    A.append(((door_x, base, front), "spruce_door[facing=north,half=lower,hinge=left]"))
    A.append(((door_x, base + 1, front), "spruce_door[facing=north,half=upper,hinge=left]"))
    if big:
        for x in (door_x - 1, door_x + 1):
            for y in (base, base + 1, base + 2): S[(x, y, front)] = STONE
        S[(door_x, base + 2, front)] = "chiseled_stone_bricks"
        hy = base + 3
        for x in range(door_x - 1, door_x + 2): S[(x, hy, D)] = stairs("north")
        for x in (door_x - 2, door_x + 2): S[(x, hy, D)] = slab(top=True)   # lifted ends
        S[(door_x, base - 1, D)] = stairs("north", mat="stone_brick")
    else:
        hy = base + 2
        for x in (door_x - 1, door_x, door_x + 1): S[(x, hy, D)] = stairs("north")
    A.append(((door_x - 1, hy - 1, D), "lantern[hanging=true]")); A.append(((door_x + 1, hy - 1, D), "lantern[hanging=true]"))
    # roof between the gable walls: ridge along x
    two = D % 2 == 0
    run = D // 2 + (0 if two else 1)                                        # slope cells incl. the overhang, excl. a single ridge cell
    T = roof_profile(run, two_cell_ridge=two)
    roof_y = {}
    n = len(T)
    for i in range(n):
        for zz, up in ((D - i, "north"), (i - 1, "south")):
            y, st = slope_block(T, i, wt, up)
            for x in range(1, W - 1): S[(x, y, zz)] = st
            roof_y[zz] = y
    if not two:
        c = D // 2; yr = roof_y[c - 1]
        for x in range(1, W - 1): S[(x, yr, c)] = TILES
        if "stairs" in S[(1, yr, c - 1)]:
            for x in range(1, W - 1): S[(x, yr + 1, c)] = slab()
            roof_y[c] = yr + 1
        else: roof_y[c] = yr
    # stepped gable walls (马头墙): one level per zone, the coping just above the tiles; the wall ends stand
    # one block proud of the facade for their full height (墀头), which frames the front and the eave
    level = {}
    mid = len(zones) // 2; tops = []
    for k, zone in enumerate(zones[:mid + 1]):                              # every step is at least one block higher than the one outside it
        top = max(roof_y[z] for z in zone + zones[len(zones) - 1 - k])     # wall head level with the roof, so the coping sits just above the tiles
        if tops: top = max(top, tops[-1] + 1)
        tops.append(top)
    for k, zone in enumerate(zones):
        for z in zone: level[z] = tops[min(k, len(zones) - 1 - k)]
    level[-1] = level[0]; level[D] = level[D - 1]
    for gx in (0, W - 1):
        for z in range(-1, D + 1):
            lo = base if z in (-1, D) else wt + 1
            for y in range(lo, level[z] + 1): S[(gx, y, z)] = HUI_WALL
            first = z == -1 or level[z - 1] < level[z]; last = z == D or level[z + 1] < level[z]
            if not big or level[z] != max(tops): first, last = z == -1, z == D   # tips at the two far ends, and on the top level at 1.5
            if first and last: cap = slab()
            elif first: cap = stairs("north")
            elif last: cap = stairs("south")
            else: cap = slab()
            S[(gx, level[z] + 1, z)] = cap
    top = max(level.values()) + 2
    # interior
    if not big:
        S.pop((W - 2, floor2, 1), None)
        for y in range(base, floor2 + 1): A.append(((W - 2, y, 1), "ladder[facing=west]"))
        A.append(((1, base, 1), "barrel[facing=up]")); A.append(((1, base, 2), "crafting_table"))
        A.append(((1, floor2 + 1, 3), "red_bed[facing=north,part=foot]")); A.append(((1, floor2 + 1, 2), "red_bed[facing=north,part=head]"))
        A.append(((door_x, floor2 - 1, 2), "lantern[hanging=true]")); A.append(((door_x, roof_y[2] - 1, 2), "lantern[hanging=true]"))
    else:
        for k in range(floor2 - base + 1):                                  # staircase along the rear wall
            S[(3 + k, base + k, 1)] = stairs("east", mat="spruce")
        for x in range(3, 3 + (floor2 - base)):
            if S.get((x, floor2, 1)) == PLANK: S.pop((x, floor2, 1))
        A.append(((1, base, 1), "barrel[facing=up]")); A.append(((1, base, 2), "barrel[facing=up]")); A.append(((1, base, front - 1), "crafting_table"))
        A.append(((W - 2, base, front - 1), "bookshelf")); A.append(((W - 2, base + 1, front - 1), "bookshelf"))
        for x in (door_x - 1, door_x, door_x + 1):
            for z in (3, 4): A.append(((x, base, z), "red_carpet"))
        A.append(((1, floor2 + 1, front - 1), "red_bed[facing=north,part=foot]")); A.append(((1, floor2 + 1, front - 2), "red_bed[facing=north,part=head]"))
        A.append(((W - 2, floor2 + 1, front - 1), "red_bed[facing=north,part=foot]")); A.append(((W - 2, floor2 + 1, front - 2), "red_bed[facing=north,part=head]"))
        A.append(((door_x, floor2 - 1, 4), "lantern[hanging=true]")); A.append(((door_x, yr - 1, 4), "lantern[hanging=true]"))
    return S, A, dict(W=W, D=D, door_x=door_x, height=top + 1, base=base)

if __name__ == "__main__":
    for name, fn in (("jiangnan", jiangnan), ("hui", hui)):
        for sc in (1, 1.5):
            S, A, m = fn(sc)
            xs = [p[0] for p in S]; ys = [p[1] for p in S]; zs = [p[2] for p in S]
            print(f"{name} x{sc}: body {m['W']}x{m['D']}, bbox x {min(xs)}..{max(xs)} y {min(ys)}..{max(ys)} z {min(zs)}..{max(zs)}, {len(S)} blocks + {len(A)} attachments")
    print("profiles:", roof_profile(4, 0.72 * 3.5), roof_profile(6, 0.72 * 5.5), roof_profile(3, 0.72 * 3, True), roof_profile(5, 0.72 * 4.5))
