// Instanced voxel-structure material: 90-degree instance rotation, per-voxel colour jitter,
// voxel AO, glazed / gold specular, foliage translucency, night windows & lanterns, fog.
import * as THREE from 'three';
import { COMMON, ATMOSPHERE, LIGHTING } from './glsl.js';
import { SHADOW_PARS } from './shadows.js';

const ROT = /* glsl */ `
vec3 rotY(vec3 p, float r) {
  if (r < 0.5) return p;
  if (r < 1.5) return vec3(p.z, p.y, -p.x);
  if (r < 2.5) return vec3(-p.x, p.y, -p.z);
  return vec3(-p.z, p.y, p.x);
}
vec3 nrmFromIdx(float i) {
  if (i < 0.5) return vec3(1.0, 0.0, 0.0);
  if (i < 1.5) return vec3(-1.0, 0.0, 0.0);
  if (i < 2.5) return vec3(0.0, 1.0, 0.0);
  if (i < 3.5) return vec3(0.0, -1.0, 0.0);
  if (i < 4.5) return vec3(0.0, 0.0, 1.0);
  return vec3(0.0, 0.0, -1.0);
}
`;

const VERT = /* glsl */ `
attribute vec4 aPos;
attribute vec4 aCol;
attribute vec4 aInst;
varying vec3 vWorld;
varying vec3 vLocal;
flat varying vec3 vNormal;
flat varying vec3 vLNormal;
varying float vAO;
flat varying vec4 vCol;
flat varying float vType;
flat varying float vScale;
flat varying float vJit;
flat varying float vSeed;
flat varying float vGlow;
${ROT}
void main() {
  float info = aPos.w;
  float nIdx = mod(info, 8.0);
  float ao = mod(floor(info / 8.0), 4.0);
  float lod = mod(floor(info / 32.0), 8.0);
  vJit = mod(floor(info / 256.0), 4.0);
  vec3 sgn = vec3(mod(floor(info / 1024.0), 4.0), mod(floor(info / 4096.0), 4.0), mod(floor(info / 16384.0), 4.0)) - 1.0;
  vScale = exp2(lod);
  float rot = mod(aInst.w, 4.0);
  vSeed = floor(aInst.w / 4.0);
  vec3 lp = aPos.xyz * 0.2;
  vec3 wp = aInst.xyz + rotY(lp, rot);
  float eps = 0.0008 * distance(wp, cameraPosition) + 0.002;
  wp += rotY(sgn, rot) * eps;
  vLNormal = nrmFromIdx(nIdx);
  vNormal = rotY(vLNormal, rot);
  vLocal = aPos.xyz;
  vAO = ao / 3.0;
  vCol = aCol;
  float tb = floor(aCol.a * 255.0 + 0.5);
  vType = mod(tb, 16.0);
  vGlow = floor(tb / 16.0) / 15.0;
  vWorld = wp;
  gl_Position = projectionMatrix * viewMatrix * vec4(wp, 1.0);
}
`;

const FRAG = /* glsl */ `
${COMMON}
${ATMOSPHERE}
${LIGHTING}
${SHADOW_PARS}
uniform float uWindows;
varying vec3 vWorld;
varying vec3 vLocal;
flat varying vec3 vNormal;
flat varying vec3 vLNormal;
varying float vAO;
flat varying vec4 vCol;
flat varying float vType;
flat varying float vScale;
flat varying float vJit;
flat varying float vSeed;
flat varying float vGlow;

void main() {
  vec3 N = vNormal;
  // voxel cell (model-local) for jitter and patterns
  vec3 cellP = floor((vLocal - vLNormal * 0.5) / vScale);
  float h = hash13(cellP + vSeed * 17.0);
  float jit = vJit * 0.035 * (vScale > 1.5 ? 0.5 : 1.0);
  vec3 c = vCol.rgb * (1.0 + (h - 0.5) * 2.0 * jit);
  int t = int(vType + 0.5);
  float occ = 0.35 + 0.65 * vAO;
  vec3 albedo = srgb2lin(c);
  float shadow = getShadow(vWorld, N);
  vec3 col;
  if (t == 8) {
    col = albedo * 0.3 * (uAmbSky * 0.5 + uLampCol * uWindows * 0.6);
  } else if (t == 2 || t == 9) {
    // foliage: wrapped diffuse + light transmission
    float ndl = dot(N, uSunDir);
    float wrap = clamp((ndl + 0.45) / 1.45, 0.0, 1.0);
    float sunUp = smoothstep(-0.03, 0.05, uSunDir.y);
    vec3 amb = mix(uAmbGround, uAmbSky, 0.5 + 0.5 * N.y);
    float lum = 0.75 + 0.5 * h;
    col = albedo * lum * (uSunCol * wrap * shadow * sunUp * 0.85 + amb * occ);
    col += albedo * uMoonCol * clamp((dot(N, uMoonDir) + 0.4) / 1.4, 0.0, 1.0) * 0.8;
    col += albedo * lampLight(vWorld, N) * occ;
  } else {
    col = shade(albedo, N, occ, shadow, vWorld);
    if (t == 1 || t == 5) {
      vec3 V = normalize(vWorld - cameraPosition);
      vec3 H = normalize(uSunDir - V);
      float sp = t == 5 ? 1.2 : 0.55;
      float pw = t == 5 ? 40.0 : 90.0;
      float sunUp = smoothstep(-0.02, 0.05, uSunDir.y);
      col += uSunCol * pow(max(dot(N, H), 0.0), pw) * sp * shadow * sunUp * mix(vec3(1.0), albedo * 2.0, t == 5 ? 0.8 : 0.2);
      // sky reflection on glazed tiles
      vec3 R = reflect(V, N);
      col += skyColor(R) * (t == 5 ? 0.25 : 0.12) * occ;
    }
    if (t == 3) {
      // window paper: warm glow at night, lit progressively per window block
      float cellId = hash13(floor(vLocal / vec3(8.0, 10.0, 8.0)) + vSeed * 3.1);
      float on = step(1.0 - uWindows * 1.15, cellId) * uWindows;
      float flick = 0.92 + 0.08 * sin(uTime * (2.0 + cellId * 3.0) + cellId * 20.0);
      col += vec3(1.0, 0.58, 0.26) * 2.6 * on * flick;
    } else if (t == 4) {
      float flick = 0.9 + 0.1 * sin(uTime * 3.1 + h * 30.0) * sin(uTime * 1.7 + h * 11.0);
      vec3 lc = c.r > 0.8 && c.g > 0.7 ? vec3(1.0, 0.72, 0.4) : vec3(1.0, 0.28, 0.12);
      col += lc * 4.0 * uLamp * flick;
    } else if (t == 7) {
      col += vec3(1.0, 0.6, 0.25) * 4.0;
    } else if (t == 6) {
      float st = hash12(vec2(floor(vLocal.x + vLocal.z), floor((vLocal.y + uTime * 30.0) / 3.0)));
      col = mix(col, shade(vec3(0.95), N, 1.0, 1.0, vWorld) + uAmbSky * 0.3, smoothstep(0.3, 0.8, st));
    }
  }
  if (vGlow > 0.0) {
    float cellId = hash13(floor(vLocal / vec3(16.0, 16.0, 16.0)) + vSeed * 5.3);
    col += vec3(1.0, 0.55, 0.25) * 1.8 * vGlow * uWindows * step(1.0 - uWindows * 1.1, cellId);
  }
  col = applyFog(col, vWorld, cameraPosition);
  gl_FragColor = vec4(col, 1.0);
}
`;

export function createStructMaterial(uniforms) {
  return new THREE.ShaderMaterial({ uniforms, vertexShader: VERT, fragmentShader: FRAG });
}

const DEPTH_VERT = /* glsl */ `
attribute vec4 aPos;
attribute vec4 aInst;
${ROT}
void main() {
  float rot = mod(aInst.w, 4.0);
  vec3 wp = aInst.xyz + rotY(aPos.xyz * 0.2, rot);
  gl_Position = projectionMatrix * viewMatrix * vec4(wp, 1.0);
}
`;
export function createStructDepthMaterial() {
  const m = new THREE.ShaderMaterial({ vertexShader: DEPTH_VERT, fragmentShader: 'void main(){ gl_FragColor = vec4(1.0); }', side: THREE.DoubleSide });
  m.colorWrite = false;
  return m;
}
