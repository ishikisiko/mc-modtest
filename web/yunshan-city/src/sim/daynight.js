// Continuous 24-hour cycle: sun & moon ephemeris (simplified), sky palette keyed on sun
// elevation, lighting, fog, lamps and exposure. One source of truth for every shader.
import * as THREE from 'three';

const LAT = THREE.MathUtils.degToRad(29.5);
const DECL = THREE.MathUtils.degToRad(11);

function col(hex) {
  return new THREE.Color(hex); // hex is sRGB; three converts to linear working space
}

// keyframes on sun elevation (degrees)
const KEYS = [
  { e: -90, zen: '#02040b', hor: '#070b18', glow: '#000000', amb: 0.2 },
  { e: -18, zen: '#03060f', hor: '#0a1022', glow: '#000000', amb: 0.2 },
  { e: -11, zen: '#060d22', hor: '#141d38', glow: '#140c1c', amb: 0.24 },
  { e: -6, zen: '#101d42', hor: '#40466a', glow: '#4c3350', amb: 0.34 },
  { e: -3, zen: '#1d3060', hor: '#7c6a86', glow: '#b0645a', amb: 0.46 },
  { e: 0, zen: '#2e4a7c', hor: '#c99a88', glow: '#f29a62', amb: 0.62 },
  { e: 3, zen: '#3a5e96', hor: '#dcb69c', glow: '#f7b27a', amb: 0.76 },
  { e: 8, zen: '#3f6fb0', hor: '#d6cbbd', glow: '#ffcf96', amb: 0.88 },
  { e: 16, zen: '#3c74bf', hor: '#bcd0e2', glow: '#fff0d4', amb: 0.96 },
  { e: 32, zen: '#3570c4', hor: '#abc7e4', glow: '#fff4e2', amb: 1.0 },
  { e: 90, zen: '#2e66bc', hor: '#a3c2e2', glow: '#fff8f0', amb: 1.0 },
].map((k) => ({ ...k, zenC: col(k.zen), horC: col(k.hor), glowC: col(k.glow) }));

const SUNKEYS = [
  { e: -3, c: '#ff7a3a', i: 0 },
  { e: 0, c: '#ff9254', i: 0.7 },
  { e: 3, c: '#ffab6c', i: 1.5 },
  { e: 8, c: '#ffb97c', i: 2.2 },
  { e: 16, c: '#ffd9ae', i: 2.5 },
  { e: 30, c: '#fff0dc', i: 2.65 },
  { e: 90, c: '#fff6ea', i: 2.75 },
].map((k) => ({ ...k, C: col(k.c) }));

function lerpKeys(keys, e, fn) {
  if (e <= keys[0].e) return fn(keys[0], keys[0], 0);
  for (let i = 0; i < keys.length - 1; i++) {
    const a = keys[i];
    const b = keys[i + 1];
    if (e <= b.e) return fn(a, b, (e - a.e) / (b.e - a.e));
  }
  const l = keys[keys.length - 1];
  return fn(l, l, 0);
}

function sunDirection(hours, decl, out) {
  const H = ((hours - 12) / 24) * Math.PI * 2;
  const sinAlt = Math.sin(LAT) * Math.sin(decl) + Math.cos(LAT) * Math.cos(decl) * Math.cos(H);
  const alt = Math.asin(sinAlt);
  let cosAz = (Math.sin(decl) - sinAlt * Math.sin(LAT)) / (Math.cos(alt) * Math.cos(LAT));
  cosAz = Math.min(1, Math.max(-1, cosAz));
  let az = Math.acos(cosAz); // from north, clockwise (east positive)
  if (Math.sin(H) > 0) az = Math.PI * 2 - az;
  out.set(Math.cos(alt) * Math.sin(az), sinAlt, -Math.cos(alt) * Math.cos(az));
  return alt;
}

const SHICHEN = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥'];
export function shichen(hours) {
  const idx = Math.floor(((hours + 1) % 24) / 2);
  return SHICHEN[idx] + '时';
}

export class DayNight {
  constructor() {
    this.time = 17.2; // hours
    this.speed = 0.35; // game hours per real second
    this.playing = true;
    this.sunDir = new THREE.Vector3();
    this.moonDir = new THREE.Vector3();
    this.sunCol = new THREE.Color();
    this.moonCol = new THREE.Color();
    this.zenith = new THREE.Color();
    this.horizon = new THREE.Color();
    this.glow = new THREE.Color();
    this.ambSky = new THREE.Color();
    this.ambGround = new THREE.Color();
    this.haze = new THREE.Color();
    this.lampCol = col('#ffb35c');
    this.keyDir = new THREE.Vector3();
    this.update(0);
  }

  setTime(h) {
    this.time = ((h % 24) + 24) % 24;
    this.update(0);
  }

  update(dt) {
    if (this.playing) this.time = (this.time + dt * this.speed) % 24;
    const t = this.time;
    const sunAlt = sunDirection(t, DECL, this.sunDir);
    // moon roughly opposite the sun (a waning-gibbous moon rising after dusk)
    const moonAlt = sunDirection(t + 12 - 1.1, -DECL * 0.8, this.moonDir);
    const e = THREE.MathUtils.radToDeg(sunAlt);
    this.sunElev = e;
    this.moonElev = THREE.MathUtils.radToDeg(moonAlt);

    lerpKeys(KEYS, e, (a, b, f) => {
      this.zenith.copy(a.zenC).lerp(b.zenC, f);
      this.horizon.copy(a.horC).lerp(b.horC, f);
      this.glow.copy(a.glowC).lerp(b.glowC, f);
      this.ambK = a.amb + (b.amb - a.amb) * f;
    });
    lerpKeys(SUNKEYS, e, (a, b, f) => {
      this.sunCol.copy(a.C).lerp(b.C, f).multiplyScalar(a.i + (b.i - a.i) * f);
    });

    // night factor
    this.night = THREE.MathUtils.smoothstep(-e, 2, 14);
    const moonUp = THREE.MathUtils.smoothstep(this.moonElev, -2, 12);
    this.moonCol.setRGB(0.42, 0.5, 0.72).multiplyScalar(0.55 * moonUp * this.night);

    // ambient: sky dome average, boosted slightly at night for readability
    this.ambSky.copy(this.zenith).lerp(this.horizon, 0.55).multiplyScalar(1.25 + this.night * 1.8);
    this.ambSky.r += 0.004 * this.night;
    this.ambSky.g += 0.007 * this.night;
    this.ambSky.b += 0.016 * this.night;
    this.ambGround.copy(this.horizon).multiplyScalar(0.45).lerp(col('#4a5a44'), 0.35).multiplyScalar(0.9 + this.night * 0.6);

    // haze follows the horizon but stays soft and slightly blue-grey (ink wash)
    this.haze.copy(this.horizon).lerp(col('#8898ad'), 0.5 * (1 - this.night));
    // morning mist
    const morning = Math.exp(-Math.pow((t - 6.4) / 1.6, 2));
    this.fogDensity = 0.00036 + 0.00055 * morning + 0.00008 * this.night;
    this.fogFalloff = 0.0024;
    this.fogBase = 0;

    // lamps: on around dusk, off after dawn; per-lamp jitter handled in shaders
    const eve = THREE.MathUtils.smoothstep(t, 17.4, 19.0);
    const morn = 1 - THREE.MathUtils.smoothstep(t, 5.0, 6.6);
    this.lamp = Math.max(eve, t < 12 ? morn : 0);
    // late-night windows dim a little
    this.windows = this.lamp * (t > 23 || t < 4.5 ? 0.55 : 1.0);

    // exposure: brighten nights so moonlit scenes stay legible
    this.exposure = 0.78 + this.night * 2.1;
    this.stars = THREE.MathUtils.smoothstep(-e, 5, 13);

    // key light for shadows
    if (e > -1.5) this.keyDir.copy(this.sunDir);
    else this.keyDir.copy(this.moonDir.y > 0.05 ? this.moonDir : this.sunDir);
    if (this.keyDir.y < 0.05) this.keyDir.y = 0.05;
    this.keyDir.normalize();
  }

  applyTo(u) {
    u.uSunDir.value.copy(this.sunDir);
    u.uMoonDir.value.copy(this.moonDir);
    u.uSunCol.value.set(this.sunCol.r, this.sunCol.g, this.sunCol.b);
    u.uMoonCol.value.set(this.moonCol.r, this.moonCol.g, this.moonCol.b);
    u.uSkyZenith.value.set(this.zenith.r, this.zenith.g, this.zenith.b);
    u.uSkyHorizon.value.set(this.horizon.r, this.horizon.g, this.horizon.b);
    u.uSkyGlow.value.set(this.glow.r, this.glow.g, this.glow.b);
    u.uAmbSky.value.set(this.ambSky.r, this.ambSky.g, this.ambSky.b);
    u.uAmbGround.value.set(this.ambGround.r, this.ambGround.g, this.ambGround.b);
    u.uHaze.value.set(this.haze.r, this.haze.g, this.haze.b);
    u.uFogDensity.value = this.fogDensity;
    u.uFogFalloff.value = this.fogFalloff;
    u.uFogBase.value = this.fogBase;
    u.uNight.value = this.night;
    u.uLamp.value = this.lamp;
    u.uWindows.value = this.windows;
    u.uStars.value = this.stars;
    u.uLampCol.value.set(this.lampCol.r, this.lampCol.g, this.lampCol.b);
  }
}
