// Shared GLSL chunks: hashing/noise, atmosphere (sky colour + aerial perspective), lighting.

export const COMMON = /* glsl */ `
uniform float uTime;
float hash12(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}
float hash13(vec3 p3) {
  p3 = fract(p3 * 0.1031);
  p3 += dot(p3, p3.zyx + 31.32);
  return fract((p3.x + p3.y) * p3.z);
}
vec2 hash22(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.xx + p3.yz) * p3.zy);
}
float vnoise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  float a = hash12(i);
  float b = hash12(i + vec2(1.0, 0.0));
  float c = hash12(i + vec2(0.0, 1.0));
  float d = hash12(i + vec2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float fbm2(vec2 p) {
  float s = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    s += a * vnoise(p);
    p = p * 2.03 + vec2(1.7, 9.2);
    a *= 0.5;
  }
  return s;
}
vec3 srgb2lin(vec3 c) { return pow(c, vec3(2.2)); }
`;

// Sky model shared by the sky dome, fog colour and water reflections.
export const ATMOSPHERE = /* glsl */ `
uniform vec3 uSunDir;
uniform vec3 uMoonDir;
uniform vec3 uSunCol;
uniform vec3 uMoonCol;
uniform vec3 uSkyZenith;
uniform vec3 uSkyHorizon;
uniform vec3 uSkyGlow;      // warm glow around the sun near the horizon
uniform vec3 uAmbSky;
uniform vec3 uAmbGround;
uniform vec3 uHaze;         // aerial perspective tint
uniform float uFogDensity;
uniform float uFogFalloff;
uniform float uFogBase;
uniform float uNight;       // 0 day .. 1 deep night
uniform float uLamp;        // 0 lamps off .. 1 lamps fully on

vec3 skyColor(vec3 dir) {
  float y = dir.y;
  float hy = clamp(y, -0.2, 1.0);
  float h = 1.0 - clamp(hy, 0.0, 1.0);
  vec3 col = mix(uSkyZenith, uSkyHorizon, pow(h, 4.0));
  // below the horizon fade toward the haze colour (cloud sea / distant mist)
  col = mix(col, uHaze * 0.9, smoothstep(0.0, -0.15, y));
  float sd = max(dot(dir, uSunDir), 0.0);
  float horizonBand = exp(-abs(y) * 6.0);
  col += uSkyGlow * (pow(sd, 6.0) * 0.55 * (0.35 + horizonBand) + pow(sd, 48.0) * 0.8);
  // moon glow
  float md = max(dot(dir, uMoonDir), 0.0);
  col += uMoonCol * (pow(md, 32.0) * 0.6 + pow(md, 6.0) * 0.08);
  return col;
}

// aerial perspective along a view ray from the camera to p
vec3 applyFog(vec3 col, vec3 p, vec3 camPos) {
  vec3 v = p - camPos;
  float d = length(v);
  vec3 dir = v / max(d, 1e-4);
  float b = uFogFalloff;
  float h0 = camPos.y - uFogBase;
  float dy = v.y;
  float k = uFogDensity * exp(-b * h0);
  float integ = abs(dy) > 0.01 ? k * (1.0 - exp(-b * dy)) / (b * dy) : k;
  float fogAmt = 1.0 - exp(-integ * d);
  vec3 fogCol = mix(uHaze, skyColor(normalize(vec3(dir.x, max(dir.y, 0.02), dir.z))), 0.55);
  // in-scattering toward the sun
  float sd = max(dot(dir, uSunDir), 0.0);
  fogCol += uSkyGlow * pow(sd, 8.0) * 0.35;
  return mix(col, fogCol, clamp(fogAmt, 0.0, 1.0));
}
`;

export const LIGHTING = /* glsl */ `
uniform sampler2D uLightMap;   // lantern light pools over the city
uniform vec4 uLightMapBounds;  // x0, z0, 1/sizeX, 1/sizeZ
uniform vec3 uLampCol;

vec3 lampLight(vec3 wp, vec3 N) {
  vec2 uv = (wp.xz - uLightMapBounds.xy) * uLightMapBounds.zw;
  if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0 || uLamp <= 0.001) return vec3(0.0);
  vec4 lm = texture2D(uLightMap, uv);
  float dh = abs(wp.y - lm.g * 400.0 + 20.0);
  float att = lm.r * exp(-dh * 0.12);
  return uLampCol * att * uLamp * (0.6 + 0.4 * max(N.y, 0.0));
}

vec3 shade(vec3 albedo, vec3 N, float occ, float sunShadow, vec3 wp) {
  float ndlS = max(dot(N, uSunDir), 0.0);
  float ndlM = max(dot(N, uMoonDir), 0.0);
  float sunUp = smoothstep(-0.03, 0.05, uSunDir.y);
  float moonUp = smoothstep(-0.03, 0.08, uMoonDir.y) * (1.0 - sunUp);
  vec3 amb = mix(uAmbGround, uAmbSky, 0.5 + 0.5 * N.y);
  vec3 direct = uSunCol * ndlS * sunShadow * sunUp + uMoonCol * ndlM * mix(1.0, sunShadow, 0.85) * moonUp;
  vec3 lit = albedo * (direct * mix(0.55, 1.0, occ) + amb * occ);
  lit += albedo * lampLight(wp, N) * mix(0.5, 1.0, occ);
  return lit;
}
`;
