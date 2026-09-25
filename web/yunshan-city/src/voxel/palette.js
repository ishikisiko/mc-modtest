// Structure voxel palette. Each entry: [sRGB hex, jitter (0..3), type]
// types: 0 plain, 1 glazed tile (specular), 2 foliage, 3 window paper (glows at night),
//        4 lantern (emissive at night), 5 gold (metallic), 6 falling water, 7 flame (always lit),
//        8 interior (never visible), 9 blossom foliage
export const T_PLAIN = 0;
export const T_GLAZE = 1;
export const T_FOLIAGE = 2;
export const T_WINDOW = 3;
export const T_LANTERN = 4;
export const T_GOLD = 5;
export const T_WATER = 6;
export const T_FLAME = 7;
export const T_INTERIOR = 8;
export const T_BLOSSOM = 9;

const P = {};
const LIST = [];
function def(name, hex, jitter, type = T_PLAIN) {
  const id = LIST.length + 1;
  P[name] = id;
  LIST.push({ name, hex, jitter, type });
  return id;
}

def('STONE', '#8b877e', 2);
def('STONE_D', '#6c6964', 2);
def('MARBLE', '#e6e2d7', 1);
def('BRICK', '#6d6b68', 2);
def('BRICK_D', '#565452', 2);
def('PLASTER', '#e8e4d9', 1);
def('PLASTER_O', '#cfb892', 1);
def('RED', '#9b2b1d', 1);
def('RED_WALL', '#8f3a2b', 1);
def('WOOD_D', '#48301f', 1);
def('WOOD', '#6b4630', 1);
def('WOOD_L', '#8e6945', 1);
def('TILE', '#3c4148', 1);
def('TILE_L', '#4c5259', 1);
def('TILE_Y', '#c8912a', 1, T_GLAZE);
def('TILE_YL', '#dca93c', 1, T_GLAZE);
def('TILE_G', '#2a6a4c', 1, T_GLAZE);
def('TILE_GL', '#3a8760', 1, T_GLAZE);
def('TILE_B', '#2b4d7b', 1, T_GLAZE);
def('RIDGE', '#2b2e33', 1);
def('GOLD', '#d9ab2c', 0, T_GOLD);
def('PAINT_B', '#2f5d8b', 1);
def('PAINT_G', '#2f7b5c', 1);
def('PAINT_R', '#a53a2a', 1);
def('PAPER', '#eadfc2', 0, T_WINDOW);
def('LATTICE', '#583826', 1);
def('LANTERN', '#d9412a', 0, T_LANTERN);
def('LANTERN_W', '#f2dba2', 0, T_LANTERN);
def('BLACK', '#1d1d1f', 1);
def('WHITE', '#f3f1eb', 1);
def('EAVE', '#30343a', 1);
def('SOFFIT', '#3b2b22', 1);
def('SIGN', '#1f1f1d', 0);
def('SIGN_GOLD', '#d4a643', 0, T_GOLD);
def('CLOTH_R', '#b3322a', 1);
def('CLOTH_B', '#3a5b8a', 1);
def('CLOTH_W', '#e9e5da', 1);
def('ROCK', '#7b7d7b', 2);
def('INTERIOR', '#2a2420', 0, T_INTERIOR);
def('BARK', '#3f3026', 1);
def('BARK_G', '#5b534b', 1);
def('PINE_D', '#1f3b2b', 2, T_FOLIAGE);
def('PINE', '#2b4d35', 2, T_FOLIAGE);
def('PINE_L', '#3d6743', 2, T_FOLIAGE);
def('LEAF', '#4b7639', 2, T_FOLIAGE);
def('LEAF_L', '#679244', 2, T_FOLIAGE);
def('LEAF_D', '#365a2c', 2, T_FOLIAGE);
def('BLOSSOM', '#e8b0c2', 2, T_BLOSSOM);
def('BLOSSOM_W', '#f5e8ec', 1, T_BLOSSOM);
def('BLOSSOM_D', '#d6849f', 2, T_BLOSSOM);
def('MAPLE', '#b53d27', 2, T_FOLIAGE);
def('MAPLE_O', '#d2702c', 2, T_FOLIAGE);
def('MAPLE_Y', '#d9a238', 2, T_FOLIAGE);
def('WILLOW', '#7da349', 2, T_FOLIAGE);
def('WILLOW_D', '#5d8638', 2, T_FOLIAGE);
def('BAMBOO', '#6f9444', 1);
def('BAMBOO_LEAF', '#5a8a3c', 2, T_FOLIAGE);
def('CYPRESS', '#28452f', 2, T_FOLIAGE);
def('FALLWATER', '#dbe8ef', 1, T_WATER);
def('THATCH', '#9f8a5a', 2);
def('FLAME', '#ffb445', 0, T_FLAME);
def('TEAL', '#2f6f73', 1);
def('OCHRE', '#b0773a', 1);

export const PAL = P;
export const PALETTE = LIST;

// linear-ish float colours for averaging (sRGB decoded)
export const PAL_RGB = new Float32Array((LIST.length + 1) * 3);
export const PAL_TYPE = new Uint8Array(LIST.length + 1);
export const PAL_JIT = new Uint8Array(LIST.length + 1);
for (let i = 0; i < LIST.length; i++) {
  const h = LIST[i].hex;
  const r = parseInt(h.slice(1, 3), 16) / 255;
  const g = parseInt(h.slice(3, 5), 16) / 255;
  const b = parseInt(h.slice(5, 7), 16) / 255;
  PAL_RGB[(i + 1) * 3] = r;
  PAL_RGB[(i + 1) * 3 + 1] = g;
  PAL_RGB[(i + 1) * 3 + 2] = b;
  PAL_TYPE[i + 1] = LIST[i].type;
  PAL_JIT[i + 1] = LIST[i].jitter;
}
