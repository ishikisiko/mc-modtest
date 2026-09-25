// Terrain surface material ids (heightfield layer). Colours live in the terrain shader.
export const TM = {
  NATURAL: 0, // decided by the mesher from slope / forest density
  GRASS: 1,
  FOREST: 2,
  ROCK: 3,
  CLIFF: 4,
  DIRT: 5,
  GRAVEL: 6,
  ROAD: 7,
  STEPS: 8,
  BRICK: 9,
  MASONRY: 10,
  MARBLE: 11,
  WATER: 12,
  PADDY: 13,
  MOSS: 14,
  PLAZA: 15,
  FIELD: 16,
  WALLTOP: 17,
  SAND: 18,
  FOAM: 19,
  COURT: 20,
  REDWALL: 21,
  EARTH: 22,
  WOOD: 23,
  GARDEN: 24,
  FALL: 25, // falling water (side faces)
  GLAZE_Y: 26, // yellow glazed wall caps
  STONEPATH: 27,
  CLOUDFLOOR: 28, // hidden floor under the sea of clouds
};

export const TM_WATERLIKE = new Set([TM.WATER, TM.PADDY, TM.FOAM]);
