// Terrain (heightfield voxel) material: decodes packed 8-byte vertices, per-voxel colour
// variation and patterns, water, lighting, shadows and aerial perspective.
import * as THREE from 'three';
import { COMMON, ATMOSPHERE, LIGHTING } from './glsl.js';
import { SHADOW_PARS } from './shadows.js';

const VERT = /* glsl */ `
attribute vec4 aData;
varying vec3 vWorld;
flat varying vec3 vNormal;
varying float vOcc;
flat varying float vMat;
flat varying float vTopMat;
flat varying float vTopY;
flat varying float vCell;
vec3 nrmFromIdx(float i) {
  if (i < 0.5) return vec3(1.0, 0.0, 0.0);
  if (i < 1.5) return vec3(-1.0, 0.0, 0.0);
  if (i < 2.5) return vec3(0.0, 1.0, 0.0);
  if (i < 3.5) return vec3(0.0, -1.0, 0.0);
  if (i < 4.5) return vec3(0.0, 0.0, 1.0);
  return vec3(0.0, 0.0, -1.0);
}
void main() {
  float xz = aData.x;
  float cz = floor(xz / 256.0);
  float cx = xz - cz * 256.0;
  vec4 wp = modelMatrix * vec4(cx, aData.y, cz, 1.0);
  float info = aData.z;
  float nIdx = mod(info, 8.0);
  vOcc = mod(floor(info / 8.0), 32.0) / 31.0;
  // nudge vertices outward along the face plane to close T-junction pinholes
  vec3 sgn = vec3(mod(floor(info / 256.0), 4.0), mod(floor(info / 1024.0), 4.0), mod(floor(info / 4096.0), 4.0)) - 1.0;
  float eps = 0.0008 * distance(wp.xyz, cameraPosition) + 0.002;
  wp.xyz += sgn * eps;
  float mw = aData.w;
  vTopMat = floor(mw / 256.0);
  vMat = mw - vTopMat * 256.0;
  vNormal = nrmFromIdx(nIdx);
  vCell = length(modelMatrix[0].xyz);
  vTopY = wp.y;
  vWorld = wp.xyz;
  gl_Position = projectionMatrix * viewMatrix * wp;
}
`;

const FRAG = /* glsl */ `
${COMMON}
${ATMOSPHERE}
${LIGHTING}
${SHADOW_PARS}
varying vec3 vWorld;
flat varying vec3 vNormal;
varying float vOcc;
flat varying float vMat;
flat varying float vTopMat;
flat varying float vTopY;
flat varying float vCell;

vec3 waterShade(vec3 base, vec3 N0, float shadow, float foam) {
  vec3 V = normalize(vWorld - cameraPosition);
  vec2 p = vWorld.xz;
  float t = uTime;
  // pixel-art ripples: normal perturbation quantised to 0.2 m cells near the camera
  vec2 qp = floor(p / 0.2) * 0.2;
  float dist = length(vWorld - cameraPosition);
  vec2 pp = mix(qp, p, smoothstep(30.0, 80.0, dist));
  float dx = sin(pp.x * 1.7 + t * 1.3) * 0.5 + sin(pp.x * 0.61 + pp.y * 0.9 - t * 0.8) * 0.7 + sin((pp.x + pp.y) * 3.1 + t * 2.1) * 0.2;
  float dz = sin(pp.y * 1.5 - t * 1.1) * 0.5 + sin(pp.y * 0.53 - pp.x * 0.77 + t * 0.7) * 0.7 + sin((pp.x - pp.y) * 2.7 - t * 1.9) * 0.2;
  float amp = mix(0.07, 0.025, smoothstep(20.0, 300.0, dist));
  vec3 n = normalize(vec3(dx * amp, 1.0, dz * amp));
  vec3 R = reflect(V, n);
  R.y = abs(R.y);
  vec3 refl = skyColor(R);
  float cosT = max(dot(-V, n), 0.0);
  float fres = 0.03 + 0.97 * pow(1.0 - cosT, 5.0);
  vec3 body = shade(base, N0, 1.0, shadow, vWorld);
  vec3 col = mix(body, refl, fres);
  // sun & moon glints
  float sunUp = smoothstep(-0.02, 0.05, uSunDir.y);
  vec3 H = normalize(uSunDir - V);
  col += uSunCol * pow(max(dot(n, H), 0.0), 320.0) * 3.0 * shadow * sunUp;
  vec3 Hm = normalize(uMoonDir - V);
  col += uMoonCol * pow(max(dot(n, Hm), 0.0), 200.0) * 4.0 * (1.0 - sunUp);
  // lantern shimmer on the water at night
  col += uLampCol * lampLight(vWorld, vec3(0.0, 1.0, 0.0)) * 0.35 * (0.6 + 0.4 * sin(p.x * 3.0 + p.y * 2.0 + t * 2.0));
  col = mix(col, shade(vec3(0.85, 0.9, 0.92), N0, 1.0, shadow, vWorld) + uAmbSky * 0.05, foam);
  return col;
}

void main() {
  vec3 N = vNormal;
  int m = int(vMat + 0.5);
  float cell = vCell;
  vec3 ip = vWorld - N * 0.05;
  vec3 bsize = vec3(max(cell, 0.2), 0.2, max(cell, 0.2));
  vec3 vox = floor(ip / bsize);
  float jit = mix(1.0, 0.3, smoothstep(0.2, 3.2, cell));
  float r = 0.5 + (hash13(vox) - 0.5) * jit;
  float r2 = hash13(vox + vec3(17.0, 31.0, 7.0));
  float fine = cell < 0.3 ? 1.0 : 0.0;
  float isTop = step(0.5, N.y);
  float below = vTopY - vWorld.y; // depth below the top edge (side faces)
  int topM = int(vTopMat + 0.5);

  // grass-covered edges on natural side faces; far LODs read as one surface
  bool vegTop = (topM == 1 || topM == 2 || topM == 14 || topM == 24);
  if (isTop < 0.5 && (m == 4 || m == 3 || m == 5) && vegTop && (below < 0.2 || (cell > 0.7 && below < cell * 2.5))) {
    m = topM;
  }

  vec3 c = vec3(0.5);
  float spec = 0.0;
  bool water = false;
  float foam = 0.0;
  // slab pattern helpers
  vec2 wpxz = vWorld.xz;
  switch (m) {
    case 1: // grass (malachite green)
      c = mix(vec3(0.24, 0.40, 0.21), vec3(0.31, 0.47, 0.26), r);
      c = mix(c, vec3(0.42, 0.46, 0.25), smoothstep(0.55, 0.95, vnoise(wpxz * 0.05)) * 0.45);
      if (fine > 0.5 && r2 > 0.982) c = hash13(vox + 3.0) > 0.5 ? vec3(0.92, 0.86, 0.55) : vec3(0.94, 0.66, 0.74);
      break;
    case 2: // forest floor (deep blue-green)
      c = mix(vec3(0.17, 0.30, 0.24), vec3(0.22, 0.35, 0.28), r);
      break;
    case 3: // rock (blue-grey)
      c = mix(vec3(0.45, 0.48, 0.49), vec3(0.54, 0.56, 0.56), r);
      break;
    case 4: { // cliff strata
      float band = floor(vWorld.y / 0.8);
      float bn = hash12(vec2(band, 3.0));
      c = mix(vec3(0.42, 0.45, 0.46), vec3(0.55, 0.54, 0.51), bn);
      c = mix(c, vec3(0.60, 0.52, 0.40), step(0.78, hash12(vec2(band, 9.0))) * 0.55);
      c *= 0.92 + 0.12 * r;
      break;
    }
    case 5: c = mix(vec3(0.45, 0.36, 0.26), vec3(0.52, 0.41, 0.30), r); break; // dirt
    case 6: c = mix(vec3(0.58, 0.56, 0.52), vec3(0.66, 0.63, 0.58), r); break; // gravel
    case 7: { // road paving slabs 0.6 x 0.4
      vec2 sl = floor(vec2(wpxz.x / 0.6 + step(1.0, mod(floor(wpxz.y / 0.4), 2.0)) * 0.5, wpxz.y / 0.4));
      float s = hash12(sl);
      c = mix(vec3(0.50, 0.49, 0.46), vec3(0.62, 0.60, 0.56), s) * (0.94 + 0.08 * r);
      break;
    }
    case 8: c = mix(vec3(0.60, 0.58, 0.54), vec3(0.68, 0.66, 0.61), r); if (isTop < 0.5) c *= 0.8; break; // steps
    case 9: { // city wall brick
      float row = floor(vWorld.y / 0.2);
      c = mix(vec3(0.36, 0.35, 0.34), vec3(0.44, 0.43, 0.41), hash12(vec2(row, floor((wpxz.x + wpxz.y) / 0.4 + row * 0.5))));
      break;
    }
    case 10: { // masonry blocks 0.8 x 0.4
      float row = floor(vWorld.y / 0.4);
      vec2 bl = vec2(floor((wpxz.x + wpxz.y) / 0.8 + row * 0.5), row);
      c = mix(vec3(0.50, 0.47, 0.42), vec3(0.62, 0.58, 0.52), hash12(bl));
      if (isTop > 0.5) c = mix(vec3(0.52, 0.50, 0.46), vec3(0.62, 0.60, 0.55), r);
      break;
    }
    case 11: c = mix(vec3(0.86, 0.85, 0.80), vec3(0.93, 0.92, 0.88), r); spec = 0.2; break; // marble
    case 12: water = true; c = vec3(0.10, 0.22, 0.24); break;
    case 13: water = true; c = mix(vec3(0.20, 0.30, 0.22), vec3(0.28, 0.40, 0.24), step(0.7, r)); break; // paddy
    case 14: c = mix(vec3(0.29, 0.43, 0.36), vec3(0.36, 0.50, 0.41), r); break; // moss
    case 15: { // plaza big slabs 1.0 x 1.0
      vec2 sl = floor(wpxz / 1.0);
      c = mix(vec3(0.60, 0.58, 0.54), vec3(0.70, 0.67, 0.62), hash12(sl)) * (0.95 + 0.06 * r);
      break;
    }
    case 16: c = mix(vec3(0.55, 0.60, 0.30), vec3(0.64, 0.64, 0.34), r); break; // field
    case 17: c = mix(vec3(0.46, 0.44, 0.42), vec3(0.54, 0.52, 0.49), r); break; // wall top
    case 18: c = mix(vec3(0.68, 0.63, 0.52), vec3(0.74, 0.69, 0.57), r); break; // sand
    case 19: water = true; foam = 0.85; c = vec3(0.8); break; // foam
    case 20: { // courtyard paving 0.4 grid
      vec2 sl = floor(wpxz / 0.4);
      c = mix(vec3(0.52, 0.50, 0.47), vec3(0.60, 0.58, 0.54), hash12(sl));
      if (isTop < 0.5) c = mix(vec3(0.50, 0.47, 0.42), vec3(0.60, 0.56, 0.50), r);
      break;
    }
    case 21: c = mix(vec3(0.50, 0.17, 0.12), vec3(0.56, 0.20, 0.14), r); break; // red wall
    case 22: c = mix(vec3(0.40, 0.33, 0.24), vec3(0.46, 0.37, 0.27), r); break; // earth bund
    case 23: c = mix(vec3(0.40, 0.27, 0.17), vec3(0.46, 0.31, 0.20), r); break; // wood
    case 24: c = mix(vec3(0.26, 0.43, 0.22), vec3(0.33, 0.49, 0.27), r); break; // garden grass
    case 25: { // falling water: per-0.2 m strands with varying speed, gaps and foam
      float colI = floor((vWorld.x + vWorld.z) / 0.2);
      float cr = hash12(vec2(colI, 1.0));
      float speed = 6.0 + cr * 7.0;
      float yy = vWorld.y + uTime * speed;
      float st = hash12(vec2(colI, floor(yy / (0.4 + cr * 0.8))));
      float gap = smoothstep(0.62, 0.9, vnoise(vec2(colI * 0.35, yy * 0.05)));
      c = mix(vec3(0.66, 0.76, 0.80), vec3(0.97, 0.99, 1.0), smoothstep(0.2, 0.8, st));
      c = mix(c, vec3(0.42, 0.50, 0.54), gap * 0.6);
      float foamTop = smoothstep(1.2, 0.0, below);
      c = mix(c, vec3(1.0), foamTop * 0.8);
      spec = -1.0;
      break;
    }
    case 26: c = mix(vec3(0.80, 0.58, 0.18), vec3(0.88, 0.66, 0.22), r); spec = 0.4; break; // glazed yellow
    case 27: c = mix(vec3(0.55, 0.53, 0.49), vec3(0.62, 0.60, 0.56), r); break;
    case 28: c = vec3(0.30, 0.36, 0.36); break; // floor beneath the cloud sea
    default: c = vec3(0.5, 0.5, 0.5);
  }
  if (m == 1 || m == 2 || m == 14) {
    // qinglv: vegetation turns malachite / azurite blue-green with altitude
    float hi = smoothstep(160.0, 400.0, vWorld.y);
    c = mix(c, c * vec3(0.8, 0.98, 1.08) + vec3(0.0, 0.015, 0.035), hi * 0.6);
  }
  vec3 albedo = srgb2lin(c);
  float shadow = getShadow(vWorld, N);
  float occ = vOcc;
  vec3 col;
  if (water && isTop > 0.5) {
    if (m == 19) {
      // churning foam: animated voxel speckle
      float fz = hash13(vec3(vox.xz, floor(uTime * 6.0 + r * 5.0)));
      foam = 0.55 + 0.45 * step(0.35, fz);
    }
    col = waterShade(albedo, N, shadow, foam);
  } else if (spec < -0.5) {
    // falling water: bright, partly self-lit by sky
    col = shade(albedo, N, 1.0, 1.0, vWorld) + albedo * uAmbSky * 0.6;
  } else {
    col = shade(albedo, N, occ, shadow, vWorld);
    if (spec > 0.0) {
      vec3 V = normalize(vWorld - cameraPosition);
      vec3 H = normalize(uSunDir - V);
      col += uSunCol * pow(max(dot(N, H), 0.0), 60.0) * spec * shadow * smoothstep(-0.02, 0.05, uSunDir.y);
    }
  }
  col = applyFog(col, vWorld, cameraPosition);
  gl_FragColor = vec4(col, 1.0);
}
`;

export function createTerrainMaterial(uniforms) {
  return new THREE.ShaderMaterial({
    uniforms,
    vertexShader: VERT,
    fragmentShader: FRAG,
  });
}

// depth-only variant for shadow maps
const DEPTH_VERT = /* glsl */ `
attribute vec4 aData;
void main() {
  float xz = aData.x;
  float cz = floor(xz / 256.0);
  float cx = xz - cz * 256.0;
  vec4 wp = modelMatrix * vec4(cx, aData.y, cz, 1.0);
  gl_Position = projectionMatrix * viewMatrix * wp;
}
`;
const DEPTH_FRAG = /* glsl */ `
void main() { gl_FragColor = vec4(1.0); }
`;
export function createTerrainDepthMaterial() {
  const m = new THREE.ShaderMaterial({ vertexShader: DEPTH_VERT, fragmentShader: DEPTH_FRAG, side: THREE.DoubleSide });
  m.colorWrite = false;
  return m;
}
