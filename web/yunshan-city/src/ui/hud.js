// Heads-up display: title, 24h time bar (play/pause, scrub, speed, presets), viewpoints,
// camera modes, quality toggles, landmark labels and the help panel.
import * as THREE from 'three';
import { shichen } from '../sim/daynight.js';

const SPEEDS = [
  { label: '缓', dayMinutes: 48 },
  { label: '常', dayMinutes: 12 },
  { label: '疾', dayMinutes: 3 },
  { label: '飞', dayMinutes: 0.75 },
];

const VIEWS = [
  ['overview', '全景'],
  ['gate', '正阳门'],
  ['avenue', '御街'],
  ['palace', '宫城'],
  ['waterfall', '飞云瀑'],
  ['tower', '观瀑阁'],
  ['summit', '揽云亭'],
  ['clouds', '云海'],
];

function el(tag, cls, html) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (html !== undefined) e.innerHTML = html;
  return e;
}

export class HUD {
  constructor({ day, rig, plan, camera, settings, onQuality }) {
    this.day = day;
    this.rig = rig;
    this.plan = plan;
    this.camera = camera;
    this.settings = settings;
    this.onQuality = onQuality;
    this.root = document.getElementById('hud');
    this.visible = true;
    this.speedIdx = 1;
    this._build();
    this._keys();
    this.setSpeed(1);
  }

  _build() {
    const r = this.root;
    r.innerHTML = '';
    // title
    const title = el('div', 'title');
    title.innerHTML = '<div class="t-main">云山巨城</div><div class="t-sub">千峰叠翠 · 百里云涛 · 体素零点二米</div>';
    r.appendChild(title);

    // time bar
    const bar = el('div', 'timebar panel');
    this.playBtn = el('button', 'btn play', '');
    this.playBtn.title = '播放 / 暂停 (空格)';
    this.playBtn.onclick = () => this.togglePlay();
    const clock = el('div', 'clock');
    this.timeText = el('div', 'time-text', '');
    this.shiText = el('div', 'shi-text', '');
    clock.append(this.timeText, this.shiText);
    const sliderWrap = el('div', 'slider-wrap');
    this.slider = el('input', 'slider');
    this.slider.type = 'range';
    this.slider.min = 0;
    this.slider.max = 24;
    this.slider.step = 0.01;
    this.slider.addEventListener('input', () => {
      this.day.setTime(parseFloat(this.slider.value));
      this._dragging = true;
    });
    this.slider.addEventListener('change', () => (this._dragging = false));
    const ticks = el('div', 'ticks');
    const names = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥'];
    for (let i = 0; i < 12; i++) {
      const t = el('span', 'tick', names[i]);
      t.style.left = `${((i * 2) / 24) * 100}%`;
      ticks.appendChild(t);
    }
    sliderWrap.append(this.slider, ticks);
    const speeds = el('div', 'speeds');
    this.speedBtns = SPEEDS.map((s, i) => {
      const b = el('button', 'btn chip', s.label);
      b.title = `一昼夜 ≈ ${s.dayMinutes >= 1 ? s.dayMinutes + ' 分钟' : Math.round(s.dayMinutes * 60) + ' 秒'}`;
      b.onclick = () => this.setSpeed(i);
      speeds.appendChild(b);
      return b;
    });
    const presets = el('div', 'presets');
    for (const [label, h] of [
      ['晨', 6.1],
      ['午', 12],
      ['暮', 18.2],
      ['夜', 22],
    ]) {
      const b = el('button', 'btn chip ghost', label);
      b.onclick = () => this.day.setTime(h);
      presets.appendChild(b);
    }
    bar.append(this.playBtn, clock, sliderWrap, speeds, presets);
    r.appendChild(bar);

    // right panel: modes, views, settings
    const side = el('div', 'side panel');
    const modes = el('div', 'group');
    modes.appendChild(el('div', 'group-title', '游览'));
    const modeRow = el('div', 'row');
    this.modeBtns = {};
    for (const [m, label, tip] of [
      ['orbit', '环视', '拖拽旋转 · 右键平移 · 滚轮缩放 (O)'],
      ['tour', '漫游', '沿城中道路自动游览：正阳门 → 御街 → 宫城 → 观瀑阁 → 揽云亭 (T)'],
      ['fly', '飞行', 'WASD 移动 · QE 升降 · 拖拽转向 · Shift 加速 (F)'],
    ]) {
      const b = el('button', 'btn chip', label);
      b.title = tip;
      b.onclick = () => this.rig.setMode(m);
      this.modeBtns[m] = b;
      modeRow.appendChild(b);
    }
    modes.appendChild(modeRow);
    side.appendChild(modes);

    const views = el('div', 'group');
    views.appendChild(el('div', 'group-title', '名胜'));
    const grid = el('div', 'grid');
    VIEWS.forEach(([k, label], i) => {
      const b = el('button', 'btn chip', label);
      b.title = `快捷键 ${i + 1}`;
      b.onclick = () => this.rig.flyTo(this.plan.views[k]);
      grid.appendChild(b);
    });
    views.appendChild(grid);
    side.appendChild(views);

    const sets = el('div', 'group');
    sets.appendChild(el('div', 'group-title', '设置'));
    const row2 = el('div', 'row');
    this.qBtns = {};
    for (const [q, label] of [
      ['low', '低'],
      ['medium', '中'],
      ['high', '高'],
    ]) {
      const b = el('button', 'btn chip', label);
      b.title = '画质';
      b.onclick = () => this.onQuality && this.onQuality(q);
      this.qBtns[q] = b;
      row2.appendChild(b);
    }
    sets.appendChild(row2);
    const row3 = el('div', 'row');
    this.toggles = {};
    for (const [k, label] of [
      ['labels', '标注'],
      ['clouds', '云海'],
      ['shadows', '阴影'],
    ]) {
      const b = el('button', 'btn chip toggle', label);
      b.onclick = () => {
        this.settings[k] = !this.settings[k];
        this._syncToggles();
        if (this.settings.onChange) this.settings.onChange(k);
      };
      this.toggles[k] = b;
      row3.appendChild(b);
    }
    sets.appendChild(row3);
    side.appendChild(sets);
    const helpBtn = el('button', 'btn chip ghost wide', '操作说明 ?');
    helpBtn.onclick = () => this.toggleHelp();
    side.appendChild(helpBtn);
    r.appendChild(side);

    // tour status
    this.tourBox = el('div', 'tourbox panel hidden');
    this.tourText = el('span', 'tour-text', '');
    this.tourPause = el('button', 'btn chip', '暂停漫游');
    this.tourPause.onclick = () => {
      this.rig.tourPlaying = !this.rig.tourPlaying;
      this.tourPause.textContent = this.rig.tourPlaying ? '暂停漫游' : '继续漫游';
    };
    const tourSlow = el('button', 'btn chip ghost', '慢');
    tourSlow.onclick = () => (this.rig.tourSpeed = Math.max(2, this.rig.tourSpeed / 1.5));
    const tourFast = el('button', 'btn chip ghost', '快');
    tourFast.onclick = () => (this.rig.tourSpeed = Math.min(60, this.rig.tourSpeed * 1.5));
    this.tourBox.append(this.tourText, tourSlow, this.tourPause, tourFast);
    r.appendChild(this.tourBox);

    // labels layer
    this.labelLayer = el('div', 'labels');
    this.labelEls = this.plan.labels.map((l) => {
      const e = el('div', 'label', `<span>${l.text}</span>`);
      this.labelLayer.appendChild(e);
      return { e, pos: new THREE.Vector3(...l.pos), text: l.text, far: l.far };
    });
    document.body.appendChild(this.labelLayer);

    // help
    this.help = el('div', 'help hidden');
    this.help.innerHTML = `
      <div class="help-card panel">
        <div class="help-title">操作说明</div>
        <div class="help-cols">
          <div>
            <h4>视角</h4>
            <p><b>左键拖拽</b> 旋转视角　<b>右键拖拽</b> 平移　<b>滚轮</b> 缩放</p>
            <p><b>W A S D</b> 移动　<b>Q / E</b> 升降　<b>Shift</b> 加速</p>
            <p><b>O</b> 环视　<b>T</b> 沿道路漫游　<b>F</b> 自由飞行</p>
            <p><b>1–8</b> 名胜视点：全景、正阳门、御街、宫城、飞云瀑、观瀑阁、揽云亭、云海</p>
            <p>触屏：单指旋转，双指缩放/平移</p>
          </div>
          <div>
            <h4>昼夜</h4>
            <p><b>空格</b> 播放 / 暂停时间流逝</p>
            <p><b>[ / ]</b> 时间后退 / 前进半个时辰（按住连续）</p>
            <p><b>, / .</b> 减慢 / 加快流速（缓·常·疾·飞）</p>
            <p>拖动底部时间轴可任意调整 0–24 时；晨、午、暮、夜为快捷时刻</p>
            <h4>其他</h4>
            <p><b>H</b> 隐藏 / 显示界面　<b>?</b> 本说明</p>
          </div>
        </div>
        <div class="help-foot">全部地形与建筑均由 0.2 米体素构成 · 日月星辰、天光、云海与城市灯火随时间联动</div>
        <button class="btn chip wide" id="help-close">开始游览</button>
      </div>`;
    document.body.appendChild(this.help);
    this.help.querySelector('#help-close').onclick = () => this.toggleHelp(false);
    this.help.addEventListener('click', (e) => {
      if (e.target === this.help) this.toggleHelp(false);
    });
    this._syncToggles();
  }

  _syncToggles() {
    for (const k in this.toggles) this.toggles[k].classList.toggle('on', !!this.settings[k]);
    for (const q in this.qBtns) this.qBtns[q].classList.toggle('on', this.settings.quality === q);
    this.labelLayer.style.display = this.settings.labels ? '' : 'none';
  }

  setQualityLabel(q) {
    this.settings.quality = q;
    this._syncToggles();
  }

  setSpeed(i) {
    this.speedIdx = Math.max(0, Math.min(SPEEDS.length - 1, i));
    this.day.speed = 24 / (SPEEDS[this.speedIdx].dayMinutes * 60);
    this.speedBtns.forEach((b, k) => b.classList.toggle('on', k === this.speedIdx));
  }

  togglePlay(force) {
    this.day.playing = force !== undefined ? force : !this.day.playing;
  }

  toggleHelp(force) {
    const show = force !== undefined ? force : this.help.classList.contains('hidden');
    this.help.classList.toggle('hidden', !show);
  }

  _keys() {
    window.addEventListener('keydown', (e) => {
      if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT')) return;
      const k = e.key;
      if (k === ' ') {
        this.togglePlay();
        e.preventDefault();
      } else if (k === '[') this.day.setTime(this.day.time - 0.5);
      else if (k === ']') this.day.setTime(this.day.time + 0.5);
      else if (k === ',' || k === '<') this.setSpeed(this.speedIdx - 1);
      else if (k === '.' || k === '>') this.setSpeed(this.speedIdx + 1);
      else if (k === 'h' || k === 'H') {
        this.visible = !this.visible;
        this.root.style.display = this.visible ? '' : 'none';
        this.labelLayer.style.visibility = this.visible ? '' : 'hidden';
      } else if (k === '?' || k === '/') this.toggleHelp();
      else if (k === 't' || k === 'T') this.rig.setMode(this.rig.mode === 'tour' ? 'orbit' : 'tour');
      else if (k === 'f' || k === 'F') this.rig.setMode(this.rig.mode === 'fly' ? 'orbit' : 'fly');
      else if (k === 'o' || k === 'O') this.rig.setMode('orbit');
      else if (k === 'Escape') this.toggleHelp(false);
      else if (/^[1-8]$/.test(k)) {
        const v = VIEWS[Number(k) - 1];
        if (v) this.rig.flyTo(this.plan.views[v[0]]);
      }
    });
  }

  update() {
    const d = this.day;
    const h = Math.floor(d.time);
    const m = Math.floor((d.time - h) * 60);
    this.timeText.textContent = `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
    this.shiText.textContent = shichen(d.time) + (d.sunElev > 0 ? ' · 日' : d.moonElev > 0 ? ' · 月' : '');
    if (!this._dragging) this.slider.value = d.time.toFixed(2);
    this.playBtn.classList.toggle('paused', !d.playing);
    this.playBtn.innerHTML = d.playing ? '<span class="i-pause"></span>' : '<span class="i-play"></span>';
    for (const m2 in this.modeBtns) this.modeBtns[m2].classList.toggle('on', this.rig.mode === m2);
    // tour status
    const touring = this.rig.mode === 'tour';
    this.tourBox.classList.toggle('hidden', !touring);
    if (touring) {
      const pct = Math.round(this.rig.tourT * 100);
      this.tourText.textContent = `沿道漫游 ${pct}%`;
    }
    // labels
    if (this.settings.labels && this.visible) {
      const cam = this.camera;
      const w = window.innerWidth;
      const hh = window.innerHeight;
      const v = new THREE.Vector3();
      for (const L of this.labelEls) {
        v.copy(L.pos).project(cam);
        const dist = cam.position.distanceTo(L.pos);
        const maxD = L.far || 1800;
        const visible = v.z < 1 && v.z > -1 && Math.abs(v.x) < 1.1 && Math.abs(v.y) < 1.1 && dist < maxD && dist > 25;
        if (!visible) {
          L.e.style.opacity = 0;
          continue;
        }
        const op = Math.min(1, Math.max(0, (maxD - dist) / 500)) * Math.min(1, (dist - 25) / 40);
        L.e.style.opacity = op.toFixed(2);
        L.e.style.transform = `translate(${((v.x + 1) / 2) * w}px, ${((1 - v.y) / 2) * hh}px) translate(-50%, -100%)`;
      }
    }
  }
}
