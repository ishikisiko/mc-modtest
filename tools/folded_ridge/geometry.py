"""折山岭预览确认的折层、裂隙和步行路线。水平以两格取样，竖向逐格。"""

from collections import deque

import numpy as np


NX, NY, NZ = 276, 208, 196
X0, Z0, STEP = -276, -196, 2
SEED = 752914
PALETTE = [
    (37, 46, 53), (52, 67, 73), (69, 86, 91), (77, 99, 92),
    (96, 117, 101), (105, 66, 43), (159, 103, 61), (197, 156, 95),
    (196, 177, 137), (93, 106, 93), (99, 104, 92), (62, 72, 61),
]
SOILS = (6, 7, 8)
ROUTE = np.array([
    (-172, 24, -151), (-140, 26, -151), (-107, 29, -148),
    (-72, 34, -142), (-35, 40, -133), (-5, 47, -113),
    (31, 53, -96), (57, 58, -69), (72, 66, -31),
    (59, 74, 3), (62, 81, 40), (86, 88, 66),
    (119, 92, 86), (145, 96, 111), (177, 103, 119),
    (195, 107, 142), (183, 111, 158), (159, 114, 164),
], dtype=np.float32)
CURVE = [
    [(-231, 19), (-173, 24), (-103, 11), (-67, 38)],
    [(-67, 38), (-39, 60), (-48, 100), (-26, 124)],
    [(-26, 124), (-6, 148), (44, 151), (86, 142)],
    [(86, 142), (128, 133), (141, 116), (175, 117)],
    [(175, 117), (202, 118), (220, 130), (241, 124)],
]


def bell(value, width):
    return np.exp(-np.square(value / width))


def ribbon_lookup():
    """Return the signed distance to a bent cross-section at one-block intervals."""
    x = np.arange(-350, 351, dtype=np.float32)[None, :]
    y = np.arange(-38, 240, dtype=np.float32)[:, None]
    best = np.full((len(y), x.size), np.inf, np.float32)
    signed = np.zeros_like(best)
    points = []
    for control in CURVE:
        a, b, c, d = np.asarray(control, np.float32)
        for t in np.linspace(0, 1, 28, endpoint=False):
            u = 1 - t
            points.append(u**3 * a + 3*u*u*t*b + 3*u*t*t*c + t**3*d)
    points.append(CURVE[-1][-1])
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        dx, dy = bx - ax, by - ay
        along = np.clip(((x-ax)*dx + (y-ay)*dy)/(dx*dx+dy*dy), 0, 1)
        rx, ry = x-ax-along*dx, y-ay-along*dy
        d2 = rx*rx+ry*ry
        nearer = d2 < best
        signed[nearer] = (np.sqrt(d2) * np.where(dx*ry-dy*rx < 0, -1, 1))[nearer]
        best[nearer] = d2[nearer]
    return signed


def path_fields(x, z):
    nearest = np.full((len(z), x.size), np.inf, np.float32)
    height = np.zeros_like(nearest)
    for (ax, ay, az), (bx, by, bz) in zip(ROUTE, ROUTE[1:]):
        dx, dz = bx-ax, bz-az
        t = np.clip(((x-ax)*dx + (z-az)*dz)/(dx*dx+dz*dz), 0, 1)
        d2 = np.square(x-ax-dx*t) + np.square(z-az-dz*t)
        use = d2 < nearest
        height[use] = (ay + (by-ay)*t)[use]
        nearest[use] = d2[use]
    return np.sqrt(nearest), height


def build_volume():
    x = (X0 + np.arange(NX) * STEP + 1).astype(np.float32)[None, :]
    z = (Z0 + np.arange(NZ) * STEP + 1).astype(np.float32)[:, None]
    y = (0.5 + np.arange(NY, dtype=np.float32))[:, None]
    rho = (np.abs(x)/269)**3.15 + (np.abs(z)/189)**3.15
    rho += .027 * np.sin(.041*x + .026*z) + .02*np.cos(.07*z - .035*x)
    valid_ground = rho < 1.06
    h = 18 + .085*(x+225) + .055*(z+164)
    h += 29*bell(x+200 - 14*np.sin(z/40), 35)*bell(z-21, 107)
    h += 43*bell(x-184 + 20*np.cos(z/56), 37)*bell(z-76, 93)
    h += 46*bell(z-145 + 13*np.sin(x/57), 31)*bell(x+139, 102)
    h += 15*bell(z+102, 28)*bell(x-101, 51)
    h += 6*np.sin(.038*x+.019*z) + 3.1*np.sin(.093*x-.061*z)
    h += 1.75*np.sin(.19*x+.12*z) + 1.4*np.cos(.12*x-.21*z)
    h *= np.clip(1.22 - .43*rho, .69, 1)
    distance, foot = path_fields(x, z)
    weight = np.clip((19-distance)/14, 0, 1)
    weight *= weight*(3 - 2*weight)
    surface = np.rint(h*(1-weight) + foot*weight).astype(np.int16)
    floor = np.rint(foot).astype(np.int16)

    volume = np.zeros((NZ, NY, NX), np.uint8)
    lookup = ribbon_lookup()
    for i, zz in enumerate(z[:, 0]):
        ground = valid_ground[i] & (y < surface[i])
        band = (y + 3*np.sin(x/37) + 5*np.cos(zz/46 + x/57)) % 28
        stone = np.select([band < 5, band < 12, band < 22], [1, 2, 3], default=4)
        top = np.select([
            distance[i] < 4.1, np.sin(.11*x+.13*zz) + np.cos(zz*.19-x*.08) > .77,
            band < 11,
        ], [11, 10, 4], default=3)
        stone = np.where(y >= surface[i]-2, top, stone)
        low = np.rint(3.3*np.clip(rho[i], 0, 1)**2)
        whole = np.where(ground & (y >= low), stone, 0).astype(np.uint8)

        drift = 14*np.sin((zz+14)/72) + 6*np.cos(zz/32)
        lift = 9*np.sin((zz+37)/65) - 5*np.cos(zz/27)
        lean = .067*np.sin(zz/72)
        yy = np.clip(np.rint(y-lift).astype(int)+38, 0, lookup.shape[0]-1)
        xx = np.clip(np.rint(x-drift-(y-89)*lean).astype(int)+350, 0, lookup.shape[1]-1)
        d = lookup[yy, xx] + 1.8*np.sin(zz*.14+x*.092+y*.071) + .85*np.sin(.27*x+.22*zz-.18*y)
        front = (-132 + .17*(x+32) - .083*(y-55) + 9*np.sin(.036*x+.049*y)
                 + 5.5*np.sin(.085*x-.081*y) + 2.8*np.cos(x*.17+y*.09))
        front += 18*bell(x-111, 18)*bell(y-134, 17) - 11*bell(x+12, 24)*bell(y-101, 45)
        rear = 148 - .11*(x+2) + 9*np.sin(x*.041-y*.062) + 6*np.sin(y*.097+x*.07)
        shore = np.minimum(zz-front, rear-zz)
        lip = 10*bell(shore, 9)
        hollow = bell(x-57, 68)*bell(zz+10, 56)*np.clip((y-86)/26, 0, 1)
        upper_limit = 28 - lip + 4*np.sin(zz*.12+y*.083) + 3*np.cos(x*.16-zz*.17)
        exit_ledge = 24*bell(x-176, 47)*bell(zz-122, 37)*np.clip((y-91)/26, 0, 1)
        lower_limit = -29 + np.maximum.reduce([lip, 26*hollow, exit_ledge]) - 1.9*np.cos(x*.13+zz*.071)
        lip_material = np.select(
            [d < -20, d < -11, d < -5, d < -1, d < 4, d < 8, d < 13, d < 20],
            [1, 2, 3, 6, 7, 9, 8, 4], default=2,
        )
        crack_axis = 40 + .34*(x-70) - .13*(y-130) + 3*np.sin(y*.18+x*.072)
        crack_width = 13*bell(x-78, 40)*(1 + .23*np.sin(y*.081+x*.041))
        roof_opening = (y > 97) & (x > 21) & (x < 145) & (np.abs(zz-crack_axis) < crack_width)
        ribbon = (d > lower_limit) & (d < upper_limit) & (shore > 0) & valid_ground[i] & ~roof_opening
        whole[ribbon] = lip_material[ribbon]
        its_route = distance[i] < 5.05
        whole[(y >= floor[i]) & (y < floor[i]+4) & its_route] = 0
        whole[(y >= floor[i]-1) & (y < floor[i]) & its_route] = 11
        volume[i] = whole
    return volume, distance, floor


def cell_at(x, z):
    return int((z-Z0)//STEP), int((x-X0)//STEP)


def check_footpath(volume, distance, floor):
    """Verify the authored walk joins every waypoint with grounded, one-block steps."""
    iz, ix = np.nonzero(distance < 4.5)
    y = floor[iz, ix]
    hollow_overhead = volume[iz, y, ix] | volume[iz, y+1, ix] | volume[iz, y+2, ix]
    if np.any(hollow_overhead) or np.any(volume[iz, y-1, ix] == 0):
        raise ValueError("步道出现地基缺口或净空不足")
    heights = dict(zip(zip(iz.tolist(), ix.tolist()), y.tolist()))
    source = cell_at(ROUTE[0][0], ROUTE[0][2])
    visited, queue = {source}, deque([source])
    while queue:
        i, j = queue.popleft()
        for next_cell in [(i-1, j), (i+1, j), (i, j-1), (i, j+1)]:
            if next_cell in heights and next_cell not in visited:
                if abs(heights[next_cell]-heights[i, j]) <= 1:
                    visited.add(next_cell)
                    queue.append(next_cell)
    for x, _, z in ROUTE:
        if cell_at(x, z) not in visited:
            raise ValueError(f"步道在 {x}, {z} 处断开")
    return len(visited)
