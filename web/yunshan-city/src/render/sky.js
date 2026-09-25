// Sky dome: gradient atmosphere, sun & moon discs, stars, milky way and high wispy clouds.
import * as THREE from 'three';
import { COMMON, ATMOSPHERE } from './glsl.js';

const VERT = /* glsl */ `
varying vec3 vDir;
void main() {
  vec4 wp = modelMatrix * vec4(position, 1.0);
  vDir = wp.xyz - cameraPosition;
  gl_Position = projectionMatrix * viewMatrix * wp;
  gl_Position.z = gl_Position.w * 0.99999; // push to the far plane
}
`;

const FRAG = /* glsl */ `
${COMMON}
${ATMOSPHERE}
uniform float uStars;
uniform float uStarRot;
uniform vec3 uPole;
varying vec3 vDir;

vec3 celestial(vec3 d) {
  // rotate around the celestial pole
  vec3 p = normalize(uPole);
  float c = cos(uStarRot);
  float s = sin(uStarRot);
  return d * c + cross(p, d) * s + p * dot(p, d) * (1.0 - c);
}

float starField(vec3 d, float scale, float thresh) {
  vec3 q = d * scale;
  vec3 cell = floor(q);
  float h = hash13(cell);
  if (h < thresh) return 0.0;
  vec3 jitter = vec3(hash13(cell + 1.3), hash13(cell + 7.1), hash13(cell + 3.7)) * 0.7 + 0.15;
  float dist = length(fract(q) - jitter);
  float b = (h - thresh) / (1.0 - thresh);
  float tw = 0.75 + 0.25 * sin(uTime * (1.5 + h * 4.0) + h * 40.0);
  return smoothstep(0.09, 0.0, dist) * (0.35 + 2.5 * b * b) * tw;
}

void main() {
  vec3 dir = normalize(vDir);
  vec3 col = skyColor(dir);

  // high wispy clouds on a virtual plane
  if (dir.y > 0.0) {
    vec2 uv = dir.xz / (dir.y + 0.06) * 1.6 + vec2(uTime * 0.004, uTime * 0.002);
    float n = fbm2(uv * 1.3) * 0.7 + fbm2(uv * 4.1 + 3.0) * 0.3;
    float cov = smoothstep(0.52, 0.78, n) * smoothstep(0.0, 0.25, dir.y) * mix(0.55, 0.3, uNight);
    float sd = max(dot(dir, uSunDir), 0.0);
    vec3 cl = uSkyHorizon * 0.9 + uSkyGlow * (0.5 + 0.9 * pow(sd, 4.0)) + uSunCol * 0.04;
    cl = mix(cl, uSkyZenith * 1.3 + uMoonCol * 0.35, uNight * 0.9);
    col = mix(col, cl, cov);
  }

  // stars & milky way
  if (uStars > 0.001 && dir.y > -0.05) {
    vec3 cd = celestial(dir);
    float st = starField(cd, 170.0, 0.93) + starField(cd, 330.0, 0.965) * 0.6;
    vec3 band = normalize(vec3(0.3, 0.2, 0.93));
    float mw = exp(-pow(dot(cd, band), 2.0) * 22.0);
    float mwn = fbm2(vec2(atan(cd.z, cd.x) * 6.0, cd.y * 9.0));
    vec3 milky = vec3(0.10, 0.11, 0.16) * mw * smoothstep(0.3, 0.8, mwn) * 1.3;
    float horizonFade = smoothstep(-0.02, 0.18, dir.y);
    col += (vec3(0.9, 0.93, 1.0) * st * 0.9 + milky) * uStars * horizonFade;
  }

  // sun disc
  float sd = dot(dir, uSunDir);
  float sunDisc = smoothstep(0.99985, 0.99992, sd);
  col += uSunCol * sunDisc * 18.0 * smoothstep(-0.03, 0.0, uSunDir.y);

  // moon disc with phase and maria
  float md = dot(dir, uMoonDir);
  float moonR = 0.0165;
  if (md > cos(moonR * 1.1)) {
    vec3 up0 = abs(uMoonDir.y) > 0.95 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    vec3 mx = normalize(cross(up0, uMoonDir));
    vec3 my = cross(uMoonDir, mx);
    vec2 lp = vec2(dot(dir, mx), dot(dir, my)) / moonR;
    float r2 = dot(lp, lp);
    if (r2 < 1.0) {
      vec3 sn = vec3(lp, sqrt(1.0 - r2));
      // light from the sun direction projected into the moon's frame
      vec3 ls = normalize(vec3(dot(uSunDir, mx), dot(uSunDir, my), dot(uSunDir, uMoonDir) * -0.25 + 0.95));
      float lit = smoothstep(-0.08, 0.12, dot(sn, ls));
      float maria = 0.72 + 0.28 * smoothstep(0.35, 0.7, fbm2(lp * 2.2 + 4.0));
      float edge = smoothstep(1.0, 0.94, r2);
      vec3 mc = vec3(1.0, 0.97, 0.9) * maria * (0.08 + lit) * 2.2;
      col = mix(col, mc + col * 0.3, edge * smoothstep(-0.05, 0.02, uMoonDir.y));
    }
  }
  gl_FragColor = vec4(col, 1.0);
}
`;

export function createSky(uniforms) {
  const geo = new THREE.SphereGeometry(1, 48, 24);
  const mat = new THREE.ShaderMaterial({
    uniforms,
    vertexShader: VERT,
    fragmentShader: FRAG,
    side: THREE.BackSide,
    depthWrite: false,
    depthTest: false,
  });
  const mesh = new THREE.Mesh(geo, mat);
  mesh.frustumCulled = false;
  mesh.renderOrder = -1000;
  mesh.onBeforeRender = (renderer, scene, camera) => {
    mesh.position.copy(camera.position);
    mesh.scale.setScalar(camera.far * 0.5);
    mesh.updateMatrixWorld(true);
  };
  return mesh;
}
