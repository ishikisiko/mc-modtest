(function () {
  'use strict';
  const C = window.ForgeCore, D = C.config;
  const KEY = 'myvillage.forging-design.v1';
  const app = document.getElementById('app'), modal = document.getElementById('modal');
  let state = C.initial(), saved = true, timer;
  const ui = { tab: 'workshop', selected: 'item-1', op: 'refine', commission: false,
    pattern: 'plain', kind: 'sword', tier: 1, craftPattern: 'plain', overlay: null };
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) { const data = JSON.parse(raw); if (C.validate(data)) state = data; }
  } catch (_) { saved = false; }
  if (window === window.parent) document.body.classList.add('standalone');
  const h = value => String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll("'", '&#39;').replaceAll('"', '&quot;');
  const fmt = value => new Intl.NumberFormat('zh-CN').format(value);
  const attr = obj => encodeURIComponent(JSON.stringify(obj));
  const selected = () => state.items.find(i => i.id === ui.selected) || state.items[0];
  const badge = rank => `<span class='badge rarity-${rank}'>${D.rarities[rank]}</span>`;
  const btn = (label, action, args = {}, options = '') => `<button data-action='${action}' data-args='${attr(args)}' ${options}>${label}</button>`;
  const options = (values, chosen) => values.map(([value, label]) => `<option value='${h(value)}' ${String(chosen) === String(value) ? 'selected' : ''}>${h(label)}</option>`).join('');
  function itemArt(kind, rank = 0) {
    const metal = 'var(--ink)', accent = 'var(--rarity, var(--jade))';
    const shapes = {
      sword: `<path d='M72 10 L89 32 L86 110 L79 119 L70 110 L67 32Z' fill='${metal}' opacity='.82'/><path d='M78 24 L78 107' stroke='${accent}' stroke-width='3'/><path d='M48 112 H108 V122 H87 V151 H69 V122 H48Z' fill='${accent}'/><path d='M67 151 H89 V160 H67Z' fill='${metal}' opacity='.6'/><path d='M73 132H83 M73 140H83' stroke='var(--surface)' stroke-width='2'/>`,
      armor: `<path d='M31 37H58L68 48H89L99 37H126V81H109V143H48V81H31Z' fill='${metal}' opacity='.8'/><path d='M58 37L78 79L99 37 M78 79V137 M52 126H105' fill='none' stroke='${accent}' stroke-width='5'/><path d='M67 90L78 83L89 90L78 105Z' fill='${accent}' opacity='.65'/>`,
      focus: `<path d='M35 69L77 48L121 69V121L77 144L35 121Z' fill='${metal}' opacity='.8'/><path d='M35 69L77 91L121 69 M77 91V144' fill='none' stroke='var(--surface)' stroke-width='3'/><path d='M62 51V30H94V51L78 59Z' fill='${accent}'/><path d='M50 87L65 95V115L50 108 M89 103L107 94 M89 112L107 103' fill='none' stroke='${accent}' stroke-width='4'/>`,
      tool: `<path d='M69 62H85V154H69Z' fill='${accent}'/><path d='M20 40L60 27H101L136 55V90L102 57H57L20 76Z' fill='${metal}' opacity='.85'/><path d='M33 45L61 36H97L123 58' fill='none' stroke='${accent}' stroke-width='4'/>`
    };
    return `<svg class='rarity-${rank}' viewBox='0 0 156 170' fill='none' aria-hidden='true'>${shapes[kind]}</svg>`;
  }
  function placeArt(index) {
    return `<svg viewBox='0 0 330 80' fill='none' aria-hidden='true'><path d='M0 75L38 40L62 57L112 13L154 59L186 31L228 66L262 22L313 69L330 60V80H0Z' fill='currentColor' opacity='.09'/><path d='M10 75L60 57L112 13L154 59L186 31L228 66L262 22L310 69' stroke='currentColor' opacity='.4'/><path d='M${55 + index * 32} 72v-18h24v18m-28-18l16-12 16 12' stroke='currentColor' stroke-width='2'/><circle cx='${260 - index * 31}' cy='18' r='11' stroke='currentColor' opacity='.4'/></svg>`;
  }
  function traitText(list) {
    return list.length ? list.map(id => `<div class='trait-box rarity-${C.rarity(list)}'><strong>${D.traits[id].name}</strong><p>${D.traits[id].text}</p></div>`).join('')
      : `<p class='empty'>尚未炼入特殊器性。它仍可淬炼和升阶。</p>`;
  }
  function costs(plan) {
    const values = Object.entries(plan.costs).filter(([, amount]) => amount > 0);
    return `<div class='cost-list'>${values.map(([id, n]) => {
      const held = id === 'coin' ? state.coin : state.inventory[id];
      return `<span class='cost-chip ${held < n ? 'shortage' : ''}'>${id === 'coin' ? '灵石' : D.materials[id].name} ${n}<span class='muted'> / ${held}</span></span>`;
    }).join('') || `<span class='muted'>无需消耗材料</span>`}</div>`;
  }
  function reasons(plan) {
    return plan.ok ? `<p class='success-note'>材料和工艺条件已满足。</p>`
      : `<ul class='requirements'>${plan.reasons.map(x => `<li>${h(x)}</li>`).join('')}</ul>`;
  }
  function changeRows(before, after) {
    if (!after) return '';
    const base = before ? C.stats(before) : null, next = C.stats(after);
    const rows = [
      ['品阶', before ? D.tiers[before.tier].name : '—', D.tiers[after.tier].name],
      ['稀有度', before ? D.rarities[C.rarity(before.traits)] : '—', D.rarities[C.rarity(after.traits)]],
      [D.kinds[after.kind].primary, base?.primary ?? '—', next.primary],
      ['灵力承载', base?.capacity ?? '—', next.capacity],
      ['淬炼程度', before ? before.refine + '/5' : '—', after.refine + '/5']
    ];
    return `<div class='quote' aria-label='操作前后变化'>${rows.map(([label, a, b]) => `<div class='quote-row'><span>${label}</span><span class='muted'>${a}</span><span class='arrow'>→</span><span class='${a === b ? 'same' : 'next'}'>${b}</span></div>`).join('')}</div>`;
  }
  function chart(item) {
    const values = D.refinement.map((_, level) => C.stats({ ...item, refine: level }).primary);
    const max = Math.max(...values) * 1.15;
    return `<svg class='chart' viewBox='0 0 330 150' role='img' aria-label='各淬炼程度的${D.kinds[item.kind].primary}'>${values.map((v, i) => {
      const height = v / max * 100, x = 13 + i * 54;
      return `<rect x='${x}' y='${119 - height}' width='32' height='${height}' rx='3' fill='${i === item.refine ? 'var(--jade)' : 'var(--line)'}'/><text x='${x + 16}' y='${112 - height}' text-anchor='middle' fill='var(--ink)' font-size='11'>${v}</text><text x='${x + 16}' y='139' text-anchor='middle' fill='var(--quiet)' font-size='10'>${i === 0 ? '初成' : '淬炼 ' + i}</text>`;
    }).join('')}</svg>`;
  }
  function itemCard(item, equip = true) {
    const rank = C.rarity(item.traits), stat = C.stats(item, state.realm);
    return `<section class='panel rarity-${rank}'>
      <div class='gear-art'>${itemArt(item.kind, rank)}</div>
      <div class='gear-title'><h2>${h(item.name)}</h2>${badge(rank)}</div>
      <div class='gear-meta'><span class='muted'>${D.tiers[item.tier].name}品阶 · ${D.kinds[item.kind].name}</span>${state.equipped[item.kind] === item.id ? `<span class='badge equipped'>已装备</span>` : ''}</div>
      <p class='muted'>${h(item.source)}</p>
      <div class='stat-list'>
        <div class='stat-row'><span>${D.kinds[item.kind].primary}</span><strong>${fmt(stat.primary)}</strong></div>
        <div class='stat-row'><span>灵力承载</span><strong>${fmt(stat.capacity)}</strong></div>
        <div class='stat-row'><span>耐久上限</span><strong>${fmt(stat.durability)}</strong></div>
      </div>
      ${stat.effective !== stat.primary ? `<div class='callout'>当前修为只能发挥 ${stat.effective} 点${D.kinds[item.kind].primary}。炼器水平不会限制你使用它。</div>` : ''}
      <div class='condition'><div><span>耐久状态</span><span>${item.condition}%</span></div><div class='meter'><i style='width:${item.condition}%'></i></div></div>
      ${traitText(item.traits)}
      ${equip ? `<div class='button-row'>${btn(state.equipped[item.kind] === item.id ? '正在使用' : '装备此物', 'execute', { operation: 'equip', id: item.id }, state.equipped[item.kind] === item.id ? 'disabled' : '')}${btn('分解回收', 'salvage', { id: item.id }, `class='danger'`)}</div>` : ''}
    </section>`;
  }
  function patternOptions(kind) {
    return Object.keys(D.patterns).filter(id => C.compatible(kind, id)).map(id => [id, D.patterns[id].name + ' · ' + D.rarities[C.rarity(D.patterns[id].traits)] + (state.known.includes(id) ? '' : '（未掌握）')]);
  }
  function operations(item) {
    const descriptions = {
      refine: '在当前品阶内提升基础强度。器性和稀有度保持不变，每件器物最多淬炼五次。',
      advance: '以高阶主材重炼器身。原有器性和经历保留，新品阶从未淬炼状态继续成长。',
      reshape: '选择已经掌握的工艺，替换当前器性。稀有度按新的器性重新评定。',
      repair: '修复探索带来的损伤。修补不会改变品阶、淬炼程度或器性。'
    };
    const choices = patternOptions(item.kind);
    if (!choices.some(([id]) => id === ui.pattern)) ui.pattern = choices[0][0];
    const args = { id: item.id, pattern: ui.pattern, commission: ui.commission };
    const plan = C.plan(state, ui.op, args);
    const labels = { refine: '淬炼一次', advance: '重炼升阶', reshape: '应用这份工艺', repair: '修补装备' };
    return `<section class='panel'>
      <h3>处理这件装备</h3>
      <div class='operation-tabs'>${[['refine', '淬炼'], ['advance', '升阶'], ['reshape', '器性'], ['repair', '修补']].map(([id, text]) => btn(text, 'operation', { op: id }, `class='${ui.op === id ? 'active' : ''}' aria-pressed='${ui.op === id}'`)).join('')}</div>
      <p class='operation-description'>${descriptions[ui.op]}</p>
      ${ui.op === 'reshape' ? `<label class='form-field'><span>选择改造工艺</span><select id='reshape-pattern'>${options(choices, ui.pattern)}</select></label>${traitText(D.patterns[ui.pattern].traits)}${item.traits.length ? `<p class='plain-note'>原有的${item.traits.map(id => D.traits[id].name).join('、')}将被所选工艺替换。</p>` : ''}` : ''}
      ${ui.op !== 'repair' ? `<label class='check'><input id='commission' type='checkbox' ${ui.commission ? 'checked' : ''}>委托工坊师傅处理</label>` : ''}
      ${ui.commission && ui.op !== 'repair' ? `<p class='plain-note'>师傅可处理金丹及以下器物，制造珍奇及以下器性。委托不增加你的炼器经验。</p>` : ''}
      ${changeRows(plan.before, plan.after)}
      ${plan.credit ? `<p class='success-note'>旧阶淬炼投入抵扣 ${plan.credit} 灵石，已计入下方费用。</p>` : ''}
      <h3 class='small'>本次消耗 / 当前持有</h3>${costs(plan)}${reasons(plan)}
      ${btn(labels[ui.op], 'execute', { operation: ui.op, ...args }, `class='primary wide' id='perform-operation' ${plan.ok ? '' : 'disabled'}`)}
    </section>`;
  }
  function guide() {
    const entries = [
      ['refine', '淬炼一次已有装备', 'workshop', 'refine'],
      ['explore', '探索矿坑或古库，取得材料', 'explore'],
      ['learn', '研读找到的器谱', 'training'],
      ['reshape', '使用新工艺改造器性', 'workshop', 'reshape'],
      ['advance', '取得高阶主材后重炼升阶', 'workshop', 'advance'],
      ['craft', '按需求打造另一件装备', 'craft']
    ];
    const complete = entries.filter(([id]) => state.milestones[id]).length;
    return `<details class='panel guide' ${complete < 2 ? 'open' : ''}><summary>建议体验顺序 <span class='muted'>${complete} / ${entries.length} 已完成</span></summary><div class='guide-steps'>${entries.map(([id, label, tab, op], index) => btn(`<span class='step-number'>${state.milestones[id] ? '✓' : index + 1}</span>${label}`, 'navigate', { tab, op }, `class='guide-step ${state.milestones[id] ? 'done' : ''}'`)).join('')}</div></details>`;
  }
  function workshop() {
    const item = selected();
    if (!item) return `<section class='panel'><h2>行囊中没有装备</h2><p class='empty'>可以前往打造页面制作一柄长剑，再继续探索。</p>${btn('去打造', 'navigate', { tab: 'craft' }, `class='primary'`)}</section>`;
    ui.selected = item.id;
    return `<div class='section-top'><h2>我的装备 <span class='muted'>${state.items.length} / ${D.maxItems}</span></h2><span class='muted'>选择一件器物查看</span></div>
      <div class='inventory' aria-label='我的装备'>${state.items.map(i => btn(`${itemArt(i.kind, C.rarity(i.traits))}<div><strong>${h(i.name)}</strong><span>${D.tiers[i.tier].name} · ${D.rarities[C.rarity(i.traits)]} · 淬炼 ${i.refine}</span></div>`, 'select-item', { id: i.id }, `class='gear-choice ${i.id === item.id ? 'active' : ''}' aria-pressed='${i.id === item.id}'`)).join('')}</div>
      <div class='work-grid'><div>${itemCard(item)}<details class='panel'><summary>查看本品阶的淬炼收益</summary>${chart(item)}<p class='muted'>数值来自本页统一规则，稀有度不额外提供基础倍率。</p></details><details class='panel'><summary>这件器物的经历 <span class='muted'>${item.history.length} 条</span></summary><ol class='history-list'>${item.history.slice().reverse().map(x => `<li>${h(x.text)}</li>`).join('')}</ol></details></div><div>${operations(item)}<div class='callout'>升阶改变基础强度。改造器性时，稀有度才会重新评定。两种成长可以分别进行。</div></div></div>${guide()}`;
  }
  function craft() {
    const choices = patternOptions(ui.kind);
    if (!choices.some(([id]) => id === ui.craftPattern)) ui.craftPattern = choices[0][0];
    const args = { kind: ui.kind, tier: ui.tier, pattern: ui.craftPattern, commission: ui.commission };
    const plan = C.plan(state, 'craft', args);
    return `<div class='section-top'><h2>打造一件新器物</h2><span class='muted'>成品结果在投入前即可查看</span></div>
      <div class='two-col'><section class='panel'>
      <label class='form-field'><span>器型</span><select id='craft-kind'>${options(Object.entries(D.kinds).map(([id, kind]) => [id, kind.name]), ui.kind)}</select></label>
      <label class='form-field'><span>器物品阶</span><select id='craft-tier'>${options(D.tiers.slice(1).map((t, i) => [i + 1, t.name + ' · 主材为' + D.materials[t.material].name]), ui.tier)}</select></label>
      <label class='form-field'><span>器性工艺</span><select id='craft-pattern'>${options(choices, ui.craftPattern)}</select></label>
      <label class='check'><input id='commission' type='checkbox' ${ui.commission ? 'checked' : ''}>委托工坊师傅打造</label>
      <p class='plain-note'>器谱决定可以炼出哪些器性。炼器水平决定你能掌握多复杂的成器过程，普通材料不会自动成为稀有装备。</p>
      <h3 class='small'>本次消耗 / 当前持有</h3>${costs(plan)}${reasons(plan)}
      ${btn(ui.commission ? '委托打造' : '开始打造', 'execute', { operation: 'craft', ...args }, `class='primary wide' id='perform-craft' ${plan.ok ? '' : 'disabled'}`)}
      </section><div>${plan.after ? itemCard(plan.after, false) : ''}<div class='callout'>本页合并结算起坯、锻打、淬火与成器过程。材料不足时不会扣除任何资源。</div></div></div>`;
  }
  function explore() {
    return `<div class='section-top'><h2>探索与资源获取</h2><span class='muted'>使用当前装备进行模拟</span></div>
      <p class='plain-note'>本页直接结算探险结果。首次收获固定，便于体验稀有成品、器谱和材料三种获取方式；这不是游戏内战斗。</p>
      <div class='cards'>${Object.entries(D.places).map(([id, place], index) => {
        const plan = C.plan(state, 'explore', { place: id }), left = place.limit - (state.visits[id] || 0);
        return `<article class='place-card ${state.realm < place.realm ? 'locked' : ''}'>
          <div class='place-scene'>${placeArt(index)}</div><div class='place-meta'><span>${place.kind} · ${D.tiers[place.realm].name}修为</span><span>剩余 ${left} 次</span></div>
          <h3>${place.name}</h3><p>${place.text}</p>
          <div class='reward-line'>${Object.entries(place.rewards).map(([k, n]) => `${D.materials[k].name} ×${n}`).join('、')}<br>灵石 ×${place.coin}</div>
          ${!plan.ok ? `<p class='small danger'>${h(plan.reasons[0])}</p>` : ''}
          ${btn('进入并结算探索', 'execute', { operation: 'explore', place: id }, `class='wide ${plan.ok ? 'primary' : ''}' ${plan.ok ? '' : 'disabled'}`)}
        </article>`;
      }).join('')}</div>`;
  }
  function training() {
    const level = C.skill(state), next = D.skills.find(x => x.xp > state.xp);
    const practice = C.plan(state, 'practice'), station = C.plan(state, 'station');
    const unread = Object.keys(state.scrolls).filter(id => state.scrolls[id] > 0 && !state.known.includes(id));
    return `<div class='section-top'><h2>炼器修习</h2><span class='muted'>当前经验 ${state.xp}${next ? ' / ' + next.xp : ' · 已达到宗师'}</span></div>
      <section class='panel'><h3>目前能够定向制造${D.rarities[level.rarity]}及以下器性</h3>
      <div class='skills-track'>${D.skills.map(s => `<div class='skill-step ${state.xp >= s.xp ? 'unlocked' : ''}'><strong>${s.name}</strong><span>${s.xp} 经验</span><span>${D.rarities[s.rarity]}</span></div>`).join('')}</div>
      <p class='muted'>研读新器谱、亲手打造、淬炼和改造都会增长经验。以后掌握更好的工艺，可以继续改造旧装备；探索所得不受你的制造上限限制。</p></section>
      <div class='two-col' style='margin-top:14px'><div><section class='panel'><h3>尚未研读的器谱</h3>
      ${unread.length ? unread.map(id => { const p = D.patterns[id], plan = C.plan(state, 'learn', { pattern: id }); return `<div class='scroll-card'><div class='section-top'><strong>${p.name}</strong>${badge(C.rarity(p.traits))}</div><p>${p.note}</p>${btn('研读器谱', 'execute', { operation: 'learn', pattern: id }, `class='primary' ${plan.ok ? '' : 'disabled'}`)}</div>`; }).join('') : `<p class='empty'>行囊中没有未研读的器谱。探索矿坑和古库可取得第一批工艺。</p>${btn('查看探索地点', 'navigate', { tab: 'explore' })}`}
      </section><details class='panel'><summary>已掌握的工艺 <span class='muted'>${state.known.length} 份</span></summary>${state.known.map(id => `<div class='scroll-card'><strong>${D.patterns[id].name}</strong><p>${D.patterns[id].note}</p>${traitText(D.patterns[id].traits)}</div>`).join('')}</details></div>
      <div><section class='panel'><h3>练习基础工艺</h3><p class='plain-note'>处理一批基础材料，获得 65 点炼器经验。</p>${costs(practice)}${reasons(practice)}${btn('进行一次练习', 'execute', { operation: 'practice' }, `class='wide primary' ${practice.ok ? '' : 'disabled'}`)}</section>
      <section class='panel'><h3>炼器工位 · ${D.tiers[state.station].name}</h3><p class='plain-note'>工位等级决定可以处理的材料品阶，与器性稀有度分别判断。</p>${state.station < 5 ? `<p class='small'>下一级可处理${D.tiers[state.station + 1].name}材料。</p>${costs(station)}${reasons(station)}${btn('升级工位', 'execute', { operation: 'station' }, `class='wide' ${station.ok ? '' : 'disabled'}`)}` : `<p class='success-note'>已能够处理全部品阶的主材。</p>`}</section></div></div>`;
  }
  function rules() {
    return `<section class='panel rules'><h2>系统规则与图鉴</h2><p>本页实现本次讨论的基础成长和获取流程。数值、消耗、工艺与地点均为可调整的设计样例，尚未接入模组。</p>
      <div class='rule-flow'><div><strong>打造完整器身</strong><span>选择器型、主材与工艺，取得可立即使用的装备。</span></div><div><strong>在同阶内淬炼</strong><span>稳定提升基础强度，达到材料上限后考虑重炼。</span></div><div><strong>以高阶主材重炼</strong><span>保留器性和经历，在新一阶继续成长。</span></div></div>
      <h3>品阶、淬炼和稀有度分别表示什么</h3><div class='table-wrap'><table><thead><tr><th>项目</th><th>决定什么</th><th>如何改变</th></tr></thead><tbody>
      <tr><td>器物品阶</td><td>基础强度和灵力承载</td><td>用高阶主材重炼</td></tr><tr><td>淬炼程度</td><td>当前品阶已实现的基础强度</td><td>投入本阶主材与淬锋砂</td></tr><tr><td>稀有度</td><td>实际器性的特殊程度</td><td>打造、改造器性或探索所得</td></tr><tr><td>炼器水平</td><td>自己可制造的器性上限</td><td>研读器谱和亲手炼制</td></tr><tr><td>工位等级</td><td>可处理的材料品阶</td><td>投入对应主材升级工位</td></tr></tbody></table></div>
      <h3>五个品阶使用的基础数值</h3><p>下面以未淬炼长剑为例。普通与传世长剑在同品阶、同淬炼程度下使用相同基础威力，差别来自实际器性。</p>
      <div class='table-wrap'><table><thead><tr><th>品阶</th><th>主材</th><th>初成威力</th><th>淬炼满威力</th></tr></thead><tbody>${D.tiers.slice(1).map((tier, i) => `<tr><td>${tier.name}</td><td>${D.materials[tier.material].name}</td><td>${C.stats({ kind: 'sword', tier: i + 1, refine: 0 }).primary}</td><td>${C.stats({ kind: 'sword', tier: i + 1, refine: 5 }).primary}</td></tr>`).join('')}</tbody></table></div>
      <h3>稀有度如何评定</h3><p>无器性为普通。单一器性按自身档次评定，两种器性合炼时比其中较高者增加一档，最高为传世。这里只评价已形成的器性，不额外叠加基础属性倍率。</p>
      <div class='table-wrap'><table><thead><tr><th>器性</th><th>适用器型</th><th>单一器性档次</th></tr></thead><tbody>${Object.values(D.traits).map(t => `<tr><td>${t.name}</td><td>${t.kinds.map(id => D.kinds[id].name).join('、')}</td><td>${badge(t.rank)}</td></tr>`).join('')}</tbody></table></div>
      <h3>升阶如何保留原有投入</h3><p>升阶不要求先淬炼到满。器性、稀有度、名称和经历保留；淬炼次数回到零，但新阶初成威力高于旧阶淬炼满的威力。旧阶亲自支付的淬炼灵石可按 50% 抵扣本次重炼费用，抵扣不超过重炼基础灵石费用的 30%，不返还现金或重复计算。</p>
      <h3>探索和炼器各自提供什么</h3><p>探索既提供可直接使用的稀有成品，也提供主材、特殊辅材和器谱。炼器让玩家在满足条件后得到明确结果。师傅可代为处理自己暂时做不了的工序，但仍需带齐材料和器谱；委托费计入操作预览。</p>
      <h3>修为不足时怎样使用高阶器物</h3><p>持有者的炼器水平不限制使用。器物每高出自身修为一阶，本页暂按 45% 的系数降低可发挥的基础强度；这是演示规则，正式数值仍需后续设计。</p>
      <h3>可保存的状态与原型边界</h3><p>装备、材料、工艺、探索次数及操作记录会保存在当前浏览器。切换演示起点会先请求确认，再建立一份新进度。器性在本页提供规则说明和稀有度计算，不模拟真实战斗中的减速、剑气或法术；探索直接结算固定样例。没有修改 Minecraft 存档，也没有安装神化或改变模组玩法。</p></section>`;
  }
  function render() {
    const skill = C.skill(state);
    app.innerHTML = `<header class='top'><div class='brand'><svg class='brand-mark' viewBox='0 0 40 40' fill='none' aria-hidden='true'><path d='M9 12H31L28 29H12L9 12ZM13 29L10 35M27 29L30 35M6 12V22H10M34 12V22H30M17 8V3M23 8V1' stroke='currentColor' stroke-width='2'/><path d='M16 18H24M20 15V25' stroke='currentColor'/></svg><div><h1>炼器工坊</h1><p>交互设计原型 · 本页数值用于试玩</p></div></div><div class='scenario'><label class='muted' for='preset'>演示起点</label><select id='preset'>${options([['beginner', '筑基修士 · 炼器学徒'], ['artisan', '金丹修士 · 工艺专精'], ['master', '化神修士 · 炼器宗师']], state.preset)}</select>${btn('重置', 'reset', {}, `class='icon-button'`)}</div></header>
      <div class='status-row'><div class='status'><label>人物修为</label><strong>${D.tiers[state.realm].name}</strong><span>决定器物发挥</span></div><div class='status'><label>炼器水平</label><strong>${skill.name}</strong><span>${state.xp} 经验</span></div><div class='status money'><label>可用灵石</label><strong>${fmt(state.coin)}</strong><span>下品</span></div>${btn('材料与交易', 'stock', {}, `class='stock-button'`)}</div>
      <nav class='navigation' role='tablist' aria-label='炼器功能'>${[['workshop', '装备成长'], ['craft', '打造器物'], ['explore', '外出探索'], ['training', '炼器修习'], ['rules', '系统规则']].map(([id, text]) => btn(text, 'navigate', { tab: id }, `role='tab' aria-selected='${ui.tab === id}'`)).join('')}</nav>
      <div id='view' role='tabpanel'>${({ workshop, craft, explore, training, rules })[ui.tab]()}</div>
      <footer class='footer'><span>${saved ? '进度保存在当前浏览器中' : '浏览器未允许保存，当前进度仅在本次打开期间保留'}</span><span>${btn('操作记录', 'log')} · ${btn('查看设计说明', 'navigate', { tab: 'rules' })}</span></footer>`;
  }
  function persist() {
    try { localStorage.setItem(KEY, JSON.stringify(state)); saved = true; }
    catch (_) { saved = false; }
  }
  function toast(message) {
    clearTimeout(timer);
    const box = document.getElementById('toast'); box.textContent = message; box.classList.add('visible');
    timer = setTimeout(() => box.classList.remove('visible'), 5000);
  }
  function modalShell(title, content) {
    modal.innerHTML = `<div class='modal-top'><h2 id='modal-title'>${title}</h2>${btn('关闭', 'close')}</div>${content}`;
    if (!modal.open) modal.showModal();
  }
  function stock() {
    ui.overlay = 'stock';
    modalShell('材料库存与集市', `<p class='plain-note'>可用灵石 ${state.coin}。集市出售通用材料，高阶主材和特殊辅材需要探索取得。</p><div class='material-list'>${Object.entries(D.materials).map(([id, m]) => {
      const p = C.plan(state, 'buy', { material: id, amount: 5 });
      return `<article class='material'><span class='count'>${state.inventory[id]}</span><strong>${m.name}</strong><p>${m.note}</p>${m.price ? btn('买入 5 份 · ' + m.price * 5 + ' 灵石', 'execute', { operation: 'buy', material: id, amount: 5 }, p.ok ? '' : 'disabled') : `<span class='muted'>探索获取</span>`}</article>`;
    }).join('')}</div>`);
  }
  function logs() {
    ui.overlay = 'log';
    modalShell('操作记录', state.log.map(entry => `<div class='log-entry'><span>第 ${entry.step} 次操作</span><strong>${h(entry.title)}</strong><p>${h(entry.detail)}</p></div>`).join(''));
  }
  function close() { modal.close(); ui.overlay = null; }
  function reset(preset) {
    state = C.initial(preset); ui.selected = state.items[0].id; ui.tab = 'workshop'; ui.op = 'refine'; ui.commission = false; ui.pattern = 'plain'; ui.tier = 1; ui.craftPattern = 'plain';
    persist(); close(); render(); toast('已建立新的演示进度。');
  }
  function requestReset(preset) {
    ui.overlay = 'reset';
    modalShell('重新开始演示', `<p>这会替换当前浏览器中保存的装备、材料和操作记录。不会影响 Minecraft。</p><div class='button-row'>${btn('保留当前进度', 'close')}${btn('确认重新开始', 'confirm-reset', { preset }, `class='primary'`)}</div>`);
  }
  function execute(args) {
    try {
      const { operation, ...input } = args;
      const result = C.execute(state, operation, input); state = result.next;
      if (operation === 'craft') { ui.selected = result.created; ui.tab = 'workshop'; ui.op = 'refine'; }
      if (operation === 'salvage') { ui.selected = state.items[0]?.id; close(); }
      persist(); render();
      if (ui.overlay === 'stock') stock();
      toast(result.detail);
      if (operation === 'explore') {
        ui.overlay = 'loot';
        const gained = Object.entries(result.gains).map(([id, n]) => `<span class='cost-chip'>${id === 'coin' ? '灵石' : D.materials[id].name} ×${n}</span>`).join('');
        modalShell(result.title + ' · 本次收获', `<p>${h(result.detail)}</p><div class='cost-list'>${gained}</div>${result.after ? `<div class='scroll-card rarity-${C.rarity(result.after.traits)}'><div class='section-top'><strong>${h(result.after.name)}</strong>${badge(C.rarity(result.after.traits))}</div><p>${D.tiers[result.after.tier].name}品阶，已经放入装备行囊。</p>${traitText(result.after.traits)}${btn('查看这件装备', 'inspect-loot', { id: result.after.id }, `class='primary'`)}</div>` : ''}<div class='button-row'>${btn('继续探索', 'close')}${btn('前往研读器谱', 'navigate', { tab: 'training', close: true })}</div>`);
      }
    } catch (error) { toast(error.message); }
  }
  document.addEventListener('click', event => {
    const button = event.target.closest('[data-action]');
    if (!button || button.disabled) return;
    const args = JSON.parse(decodeURIComponent(button.dataset.args || '%7B%7D'));
    switch (button.dataset.action) {
      case 'navigate': ui.tab = args.tab; if (args.op) ui.op = args.op; if (args.close) close(); render(); break;
      case 'select-item': ui.selected = args.id; render(); break;
      case 'operation': ui.op = args.op; render(); break;
      case 'execute': execute(args); break;
      case 'stock': stock(); break;
      case 'log': logs(); break;
      case 'close': close(); render(); break;
      case 'reset': requestReset(state.preset); break;
      case 'confirm-reset': reset(args.preset); break;
      case 'inspect-loot': ui.selected = args.id; ui.tab = 'workshop'; close(); render(); break;
      case 'salvage': {
        const item = state.items.find(x => x.id === args.id), p = C.plan(state, 'salvage', args);
        ui.overlay = 'salvage';
        modalShell('确认分解' + h(item.name), `<p>分解后将失去这件器物及其器性和经历，只回收部分材料。</p><div class='cost-list'>${Object.entries(p.gains).map(([id, n]) => `<span class='cost-chip'>${id === 'coin' ? '灵石' : D.materials[id].name} ×${n}</span>`).join('')}</div><div class='button-row'>${btn('取消', 'close')}${btn('确认分解', 'execute', { operation: 'salvage', id: item.id }, `class='danger'`)}</div>`); break;
      }
    }
  });
  document.addEventListener('change', event => {
    const el = event.target;
    if (el.id === 'preset') { const next = el.value; el.value = state.preset; requestReset(next); return; }
    if (el.id === 'commission') ui.commission = el.checked;
    if (el.id === 'reshape-pattern') ui.pattern = el.value;
    if (el.id === 'craft-kind') ui.kind = el.value;
    if (el.id === 'craft-tier') ui.tier = Number(el.value);
    if (el.id === 'craft-pattern') ui.craftPattern = el.value;
    render();
  });
  modal.addEventListener('cancel', () => { ui.overlay = null; render(); });
  window.ForgeDemo = { state: () => C.clone(state), reset, execute, storageKey: KEY };
  render();
})();
