/* Standalone design simulation. All balance values below are prototype values. */
(function (root) {
  'use strict';
  const VERSION = 1;
  const tiers = [null,
    { name: '炼气', material: 'iron', scale: 1 },
    { name: '筑基', material: 'xuantie', scale: 2.6 },
    { name: '金丹', material: 'gengjin', scale: 6.8 },
    { name: '元婴', material: 'star', scale: 17.6 },
    { name: '化神', material: 'void', scale: 46 }
  ];
  const materials = {
    iron: { name: '精铁', price: 3, note: '炼气器身的主材，矿坑和集市均可获得。' },
    xuantie: { name: '玄铁', price: 0, note: '筑基器身的主材，来自矿坑深层与山中古库。' },
    gengjin: { name: '庚金', price: 0, note: '金丹器身的主材，来自雷泽。' },
    star: { name: '星纹金', price: 0, note: '元婴器身的主材，来自古修洞府。' },
    void: { name: '太虚晶', price: 0, note: '化神器身的主材，来自天外遗墟。' },
    sand: { name: '淬锋砂', price: 4, note: '打造、淬炼和重炼使用的通用辅材。' },
    coal: { name: '炉炭', price: 2, note: '打造与工艺练习使用的燃料。' },
    frost: { name: '寒髓', price: 0, note: '刻制霜纹和护身器性的稀有材料。' },
    thunder: { name: '雷角', price: 0, note: '炼制回锋与承劫器性的稀有材料。' },
    mirror: { name: '回影晶', price: 0, note: '炼制返照器性的稀有材料。' }
  };
  const kinds = {
    sword: { name: '长剑', defaultName: '青衡剑', primary: '基础威力', base: 24, durability: 120, metal: 6 },
    armor: { name: '护甲', defaultName: '玄纹护甲', primary: '护体强度', base: 18, durability: 180, metal: 8 },
    focus: { name: '法印', defaultName: '引灵法印', primary: '引灵强度', base: 22, durability: 100, metal: 5 },
    tool: { name: '灵镐', defaultName: '青石灵镐', primary: '采掘能力', base: 16, durability: 160, metal: 5 }
  };
  const rarities = ['普通', '精良', '稀有', '珍奇', '传世'];
  const skills = [
    { name: '学徒', xp: 0, rarity: 0 }, { name: '熟练', xp: 60, rarity: 1 },
    { name: '专精', xp: 240, rarity: 2 }, { name: '大师', xp: 600, rarity: 3 },
    { name: '宗师', xp: 1200, rarity: 4 }
  ];
  const traits = {
    tough: { name: '坚固', rank: 1, kinds: ['sword', 'armor', 'focus', 'tool'], text: '日常使用的耐久消耗减少 20%。' },
    conduct: { name: '导灵', rank: 1, kinds: ['sword', 'focus'], text: '通过这件器物施展招式时，灵力消耗减少 10%。' },
    frost: { name: '霜纹', rank: 2, kinds: ['sword'], text: '连续命中同一目标三次后使其短暂减速；切换目标重新积累。' },
    ward: { name: '凝霜护身', rank: 2, kinds: ['armor'], text: '停留片刻后形成一层护身霜衣，抵挡一次冲击后消散。' },
    vein: { name: '探脉', rank: 2, kinds: ['tool'], text: '采下一块矿石后，短暂显示附近同类矿石的轮廓。' },
    returning: { name: '回锋', rank: 3, kinds: ['sword'], text: '剑气在尽头回返。往返分配同一份威力，最大射程缩短。' },
    delay: { name: '承劫', rank: 3, kinds: ['armor'], text: '将一次爆发伤害的一部分延后承受。总伤害不变，需要留出恢复时间。' },
    echo: { name: '返照', rank: 4, kinds: ['focus'], text: '下一道法术可从上一次记录的位置释放，施法者本身不再同时出招。' }
  };
  const patterns = {
    plain: { name: '基础成器', traits: [], costs: {}, note: '形成完整器身，不添加特殊器性。' },
    tough: { name: '坚固工艺', traits: ['tough'], costs: { sand: 3 }, note: '适用于全部器型。' },
    conduct: { name: '导灵工艺', traits: ['conduct'], costs: { xuantie: 2 }, note: '适用于长剑和法印。' },
    frost: { name: '霜纹器谱', traits: ['frost'], costs: { frost: 2 }, note: '在长剑上形成霜纹器性。' },
    ward: { name: '护身器谱', traits: ['ward'], costs: { frost: 2 }, note: '在护甲上形成护身器性。' },
    vein: { name: '探脉器谱', traits: ['vein'], costs: { xuantie: 3 }, note: '在灵镐上形成探脉器性。' },
    snowlink: { name: '霜纹导灵合炼谱', traits: ['frost', 'conduct'], costs: { frost: 3, xuantie: 3 }, note: '两种器性合炼后评为珍奇。' },
    returning: { name: '回锋器谱', traits: ['returning'], costs: { thunder: 2 }, note: '改变长剑释放剑气的方式。' },
    delay: { name: '承劫器谱', traits: ['delay'], costs: { thunder: 2, frost: 1 }, note: '为护甲提供缓解爆发伤害的手段。' },
    echo: { name: '返照器谱', traits: ['echo'], costs: { mirror: 2, star: 2 }, note: '在法印上重现古老的返照术式。' }
  };
  const places = {
    mine: { name: '山麓矿坑', realm: 1, limit: 4, wear: 5, coin: 24, rewards: { iron: 8, xuantie: 4, sand: 4, coal: 3 }, firstScrolls: ['vein'], text: '补充基础主材与辅材，深层还能发现少量玄铁。', kind: '采掘' },
    archive: { name: '山中古库', realm: 2, limit: 3, wear: 8, coin: 42, rewards: { xuantie: 6, frost: 2, sand: 3 }, firstScrolls: ['frost', 'ward'], firstGear: { kind: 'sword', tier: 2, traits: ['frost'], name: '霜纹古剑' }, text: '第一次探索会取得一柄稀有古剑，以及两份失传器谱。', kind: '遗迹' },
    marsh: { name: '雷泽旧战场', realm: 3, limit: 3, wear: 11, coin: 70, rewards: { gengjin: 6, thunder: 2, frost: 1 }, firstScrolls: ['returning', 'delay', 'snowlink'], text: '取得金丹主材、雷角与较复杂的合炼工艺。', kind: '险地' },
    cave: { name: '古修洞府', realm: 4, limit: 2, wear: 14, coin: 110, rewards: { star: 6, mirror: 1, thunder: 1 }, firstScrolls: ['echo'], text: '洞府中保留着高阶材料与返照器谱。', kind: '洞府' },
    sky: { name: '天外遗墟', realm: 5, limit: 2, wear: 17, coin: 160, rewards: { void: 6, mirror: 2, star: 2 }, firstScrolls: [], firstGear: { kind: 'focus', tier: 5, traits: ['echo'], name: '返照古印' }, text: '第一次探索可取得传世古印，使用它不要求成为炼器宗师。', kind: '遗墟' }
  };
  const refinement = [0, .12, .22, .30, .36, .40];
  const config = { version: VERSION, tiers, materials, kinds, rarities, skills, traits, patterns, places, refinement,
    maxRefine: 5, maxTier: 5, maxItems: 32, commissionTier: 3, commissionRarity: 3 };
  const clone = obj => JSON.parse(JSON.stringify(obj));
  const rarity = list => {
    if (!list.length) return 0;
    return Math.min(4, Math.max(...list.map(id => traits[id].rank)) + (list.length > 1 ? 1 : 0));
  };
  const skill = state => [...skills].reverse().find(x => state.xp >= x.xp);
  const compatible = (kind, pattern) => patterns[pattern].traits.every(id => traits[id].kinds.includes(kind));
  function stats(item, realm = 5) {
    const type = kinds[item.kind], scale = tiers[item.tier].scale;
    const primary = Math.round(type.base * scale * (1 + refinement[item.refine]));
    return { primary, effective: Math.round(primary * Math.pow(.45, Math.max(0, item.tier - realm))),
      capacity: Math.round(30 * scale * (1 + item.refine * .05)),
      durability: Math.round(type.durability * (1 + (item.tier - 1) * .5 + item.refine * .04)) };
  }
  function makeItem(state, kind, tier, traitList, name, source) {
    const item = { id: 'item-' + state.nextId++, kind, tier, refine: 0, traits: [...traitList],
      name: name || kinds[kind].defaultName, condition: 100, invested: 0, source,
      history: [{ step: state.step, text: source }] };
    state.items.push(item);
    return item;
  }
  function initial(preset = 'beginner') {
    if (!['beginner', 'artisan', 'master'].includes(preset)) preset = 'beginner';
    const stage = { beginner: 2, artisan: 3, master: 5 }[preset];
    const state = { version: VERSION, preset, realm: stage, station: stage,
      xp: { beginner: 30, artisan: 300, master: 1350 }[preset],
      coin: { beginner: 180, artisan: 650, master: 2400 }[preset],
      inventory: Object.fromEntries(Object.keys(materials).map(k => [k, 0])),
      known: ['plain', 'tough', 'conduct'], scrolls: {}, visits: {},
      items: [], equipped: {}, nextId: 1, step: 0, log: [], milestones: {} };
    Object.assign(state.inventory, { iron: 24, xuantie: 3, sand: 14, coal: 12 });
    if (preset !== 'beginner') {
      for (const id of Object.keys(materials)) state.inventory[id] = preset === 'master' ? 36 : 12;
      state.known.push('frost', 'ward', 'vein');
      if (preset === 'master') state.known = Object.keys(patterns);
    }
    const sword = makeItem(state, 'sword', stage - 1 || 1, preset === 'beginner' ? [] : ['conduct'], '青衡剑', '初始装备');
    const armor = makeItem(state, 'armor', stage - 1 || 1, [], '玄纹护甲', '初始装备');
    state.equipped.sword = sword.id; state.equipped.armor = armor.id;
    if (preset === 'master') makeItem(state, 'focus', 4, ['echo'], '返照古印', '探索所得；不受持有者炼器水平限制');
    state.log.push({ step: 0, title: '开始试玩', detail: '先选择一件装备，再查看淬炼或升阶后的变化。' });
    return state;
  }
  function validate(state) {
    const integer = (v, lo, hi) => Number.isSafeInteger(v) && v >= lo && v <= hi;
    if (!state || state.version !== VERSION || !integer(state.realm, 1, 5) || !integer(state.station, 1, 5)
        || !integer(state.coin, 0, 1e9) || !integer(state.xp, 0, 1e9) || !integer(state.nextId, 1, 1e9)
        || !integer(state.step, 0, 1e9) || !Array.isArray(state.items) || state.items.length > config.maxItems) return false;
    if (!state.inventory || Object.keys(materials).some(k => !integer(state.inventory[k], 0, 1e9))) return false;
    if (!Array.isArray(state.known) || new Set(state.known).size !== state.known.length || state.known.some(k => !patterns[k])) return false;
    if (!state.scrolls || Object.entries(state.scrolls).some(([k, v]) => !patterns[k] || !integer(v, 0, 1e5))) return false;
    if (!state.visits || Object.entries(state.visits).some(([k, v]) => !places[k] || !integer(v, 0, places[k].limit))) return false;
    const ids = new Set();
    for (const item of state.items) {
      if (!item || typeof item.id !== 'string' || !/^item-\d+$/.test(item.id) || ids.has(item.id)
          || Number(item.id.slice(5)) >= state.nextId || !kinds[item.kind] || !integer(item.tier, 1, 5)
          || !integer(item.refine, 0, 5) || !integer(item.condition, 0, 100) || !integer(item.invested, 0, 1e9)
          || typeof item.name !== 'string' || item.name.length > 40 || !Array.isArray(item.traits) || item.traits.length > 2
          || new Set(item.traits).size !== item.traits.length || item.traits.some(k => !traits[k] || !traits[k].kinds.includes(item.kind))
          || !Array.isArray(item.history) || item.history.length > 40) return false;
      ids.add(item.id);
    }
    if (!state.equipped || Object.entries(state.equipped).some(([k, id]) => !kinds[k] || !state.items.some(i => i.id === id && i.kind === k))) return false;
    return Array.isArray(state.log) && state.log.length <= 60 && !!state.milestones;
  }
  function plan(input, action, args = {}) {
    if (!validate(input)) throw new Error('进度数据不完整，请重新选择演示起点。');
    const state = clone(input), before = args.id ? state.items.find(i => i.id === args.id) : null;
    const result = { action, title: '', costs: {}, gains: {}, reasons: [], before: before ? clone(before) : null,
      after: null, next: state, detail: '', credit: 0, created: null };
    const need = (condition, message) => { if (!condition) result.reasons.push(message); };
    const cost = (key, amount) => { result.costs[key] = (result.costs[key] || 0) + amount; };
    const gain = (key, amount) => { result.gains[key] = (result.gains[key] || 0) + amount; };
    const xp = amount => { state.xp += amount; };
    const requireItem = () => { if (!before) throw new Error('请先选择一件装备。'); };
    const capacity = () => need(state.items.length < config.maxItems, '行囊已满，请先分解不再使用的装备。');
    const authority = (targetTier, traitRank, creatingTraits = true) => {
      if (args.commission) {
        need(targetTier <= config.commissionTier, '工坊师傅目前只能处理金丹及以下器物。');
        if (creatingTraits) need(traitRank <= config.commissionRarity, '这位师傅尚未掌握传世器性的工艺。');
        cost('coin', 30 + targetTier * 12 + (creatingTraits ? traitRank * 15 : 0));
      } else {
        need(state.station >= targetTier, '工位只能处理' + tiers[state.station].name + '及以下材料，请先升级工位或委托师傅。');
        if (creatingTraits) need(skill(state).rarity >= traitRank, '当前炼器水平只能定向制造' + rarities[skill(state).rarity] + '装备；可研习工艺或委托师傅。');
      }
    };
    const patternCost = id => { for (const [k, n] of Object.entries(patterns[id].costs)) cost(k, n); };
    const mark = text => {
      before.history.push({ step: state.step + 1, text });
      before.history = before.history.slice(-40);
      result.after = clone(before);
    };
    try {
      if (['refine', 'advance', 'reshape', 'repair', 'salvage', 'equip'].includes(action)) requireItem();
      switch (action) {
        case 'refine': {
          result.title = '淬炼' + before.name;
          need(before.refine < config.maxRefine, '这件器物已经达到当前品阶的淬炼上限。');
          authority(before.tier, 0, false);
          const level = before.refine + 1;
          const coins = 8 * before.tier + level * 4;
          cost('coin', coins); cost(tiers[before.tier].material, 2 + Math.floor(level / 2)); cost('sand', level);
          before.refine = Math.min(5, level); before.invested += coins;
          if (!args.commission) xp(16);
          state.milestones.refine = true;
          result.detail = '淬炼达到 ' + before.refine + '/5，品阶与稀有度保持不变。'; mark(result.detail); break;
        }
        case 'advance': {
          result.title = '重炼升阶';
          need(before.tier < config.maxTier, '已经达到本原型提供的最高品阶。');
          const target = Math.min(5, before.tier + 1), base = 50 + target * 15;
          authority(target, rarity(before.traits), false);
          result.credit = Math.min(Math.floor(before.invested * .5), Math.floor(base * .3));
          cost('coin', base - result.credit); cost(tiers[target].material, kinds[before.kind].metal + 2); cost('sand', 4);
          before.tier = target; before.refine = 0; before.invested = 0;
          if (!args.commission) xp(45);
          state.milestones.advance = true;
          result.detail = '重炼为' + tiers[target].name + '器物，保留器性与经历。旧阶投入抵扣 ' + result.credit + ' 灵石。'; mark(result.detail); break;
        }
        case 'reshape': {
          const p = patterns[args.pattern]; if (!p) throw new Error('请选择有效的器性工艺。');
          result.title = '改造器性';
          need(state.known.includes(args.pattern), '尚未研读这份器谱；请前往研习页面。');
          need(compatible(before.kind, args.pattern), '这份工艺不适用于当前器型。');
          need(JSON.stringify(before.traits) !== JSON.stringify(p.traits), '这件器物已经具有相同器性，无需重复投入。');
          authority(before.tier, rarity(p.traits));
          cost('coin', 18 + rarity(p.traits) * 12); patternCost(args.pattern);
          before.traits = [...p.traits];
          if (!args.commission) xp(35);
          state.milestones.reshape = true;
          result.detail = '采用' + p.name + '，稀有度评为' + rarities[rarity(before.traits)] + '；基础强度不额外乘倍。'; mark(result.detail); break;
        }
        case 'craft': {
          const kind = args.kind, tier = Number(args.tier), p = patterns[args.pattern];
          if (!kinds[kind] || !Number.isInteger(tier) || !tiers[tier] || !p) throw new Error('请先选择器型、品阶与工艺。');
          result.title = '打造' + kinds[kind].name;
          capacity(); need(state.known.includes(args.pattern), '尚未研读这份器谱。');
          need(compatible(kind, args.pattern), '这份工艺不适用于当前器型。');
          authority(tier, rarity(p.traits));
          cost('coin', 18 * tier); cost(tiers[tier].material, kinds[kind].metal); cost('coal', 3); cost('sand', 2); patternCost(args.pattern);
          const made = makeItem(state, kind, tier, p.traits, kinds[kind].defaultName, (args.commission ? '委托打造，使用' : '亲手打造，使用') + p.name);
          result.after = clone(made); result.created = made.id;
          if (!args.commission) xp(40);
          state.milestones.craft = true;
          result.detail = '取得' + tiers[tier].name + '品阶的' + rarities[rarity(p.traits)] + kinds[kind].name + '。'; break;
        }
        case 'repair': {
          result.title = '修补' + before.name;
          need(before.condition < 100, '这件器物没有损伤。');
          cost('coin', Math.max(2, Math.ceil((100 - before.condition) / 10)) * before.tier);
          cost(tiers[before.tier].material, 1); before.condition = 100;
          result.detail = '耐久状态恢复完整，所有成长和器性保留。'; mark(result.detail); break;
        }
        case 'salvage': {
          result.title = '分解' + before.name;
          gain(tiers[before.tier].material, Math.max(1, Math.floor(kinds[before.kind].metal * .35)));
          gain('coin', before.tier * 5);
          state.items = state.items.filter(i => i.id !== before.id);
          if (state.equipped[before.kind] === before.id) delete state.equipped[before.kind];
          result.detail = '回收部分主材。器物本身、器性与经历会失去。'; break;
        }
        case 'equip': {
          result.title = '装备' + before.name;
          need(before.condition > 0, '器物已经损坏，请先修补。');
          state.equipped[before.kind] = before.id;
          result.detail = before.tier > state.realm ? '已经装备。修为不足时，只能发挥部分基础强度。' : '已经装备，持有者的炼器水平不限制使用。';
          result.after = clone(before); break;
        }
        case 'learn': {
          const id = args.pattern, p = patterns[id]; if (!p) throw new Error('没有这份器谱。');
          result.title = '研读' + p.name;
          need(!state.known.includes(id), '已经掌握这份工艺。');
          need((state.scrolls[id] || 0) > 0, '行囊中没有这份器谱。');
          state.scrolls[id] = Math.max(0, (state.scrolls[id] || 0) - 1);
          if (!state.known.includes(id)) state.known.push(id);
          const experience = 60 + rarity(p.traits) * 70; xp(experience);
          state.milestones.learn = true;
          result.detail = '掌握这份工艺，并获得 ' + experience + ' 点炼器经验。'; break;
        }
        case 'practice': {
          result.title = '练习基础工艺';
          need(state.xp < skills.at(-1).xp, '已达到本原型的最高炼器水平。');
          cost('iron', 3); cost('coal', 2); cost('coin', 12); xp(65);
          result.detail = '完成一次材料处理练习，获得 65 点炼器经验。'; break;
        }
        case 'station': {
          result.title = '升级炼器工位';
          need(state.station < 5, '已经达到最高工位等级。');
          const tier = Math.min(5, state.station + 1);
          cost(tiers[tier].material, 5); cost('coin', tier * 45); cost('sand', 3);
          state.station = tier; result.detail = '工位现在能够处理' + tiers[tier].name + '及以下主材。'; break;
        }
        case 'explore': {
          const p = places[args.place]; if (!p) throw new Error('请选择一个探索地点。');
          result.title = '探索' + p.name;
          need(state.realm >= p.realm, '此演示地点要求' + tiers[p.realm].name + '修为。可切换演示起点体验后期内容。');
          const visits = state.visits[args.place] || 0, first = visits === 0;
          need(visits < p.limit, '这个地点的演示探索次数已经用完。');
          const weapon = state.items.find(i => i.id === state.equipped.sword);
          need(!!weapon && weapon.condition > 0, '需要装备一柄未损坏的长剑才能进行探索模拟。');
          if (first && p.firstGear) capacity();
          state.visits[args.place] = Math.min(p.limit, visits + 1);
          for (const id of Object.values(state.equipped)) {
            const item = state.items.find(i => i.id === id);
            item.condition = Math.max(0, item.condition - Math.ceil(p.wear * (item.traits.includes('tough') ? .8 : 1)));
          }
          for (const [k, n] of Object.entries(p.rewards)) gain(k, n);
          gain('coin', p.coin);
          const extras = [];
          if (first) {
            for (const id of p.firstScrolls) {
              if (!state.known.includes(id)) { state.scrolls[id] = (state.scrolls[id] || 0) + 1; extras.push(patterns[id].name); }
            }
            if (p.firstGear) {
              const g = p.firstGear, item = makeItem(state, g.kind, g.tier, g.traits, g.name, '探索' + p.name + '所得');
              result.created = item.id; result.after = clone(item); extras.push(g.name);
            }
          }
          state.milestones.explore = true;
          result.detail = '取得地点资源' + (extras.length ? '，另获' + extras.join('、') : '') + '。装备消耗少量耐久。'; break;
        }
        case 'buy': {
          const material = materials[args.material], amount = Number(args.amount);
          if (!material || !material.price || !Number.isSafeInteger(amount) || amount < 1 || amount > 20) throw new Error('这项材料不能按所选数量购买。');
          result.title = '购入' + material.name;
          cost('coin', material.price * amount); gain(args.material, amount);
          result.detail = '购入 ' + amount + ' 份' + material.name + '。'; break;
        }
        default: throw new Error('未知操作。');
      }
    } catch (error) { result.reasons.push(error.message); }
    for (const [key, amount] of Object.entries(result.costs)) {
      const held = key === 'coin' ? input.coin : input.inventory[key];
      need(held >= amount, (key === 'coin' ? '灵石' : materials[key].name) + '不足：需要 ' + amount + '，现有 ' + held + '。');
      if (key === 'coin') state.coin -= amount; else state.inventory[key] -= amount;
    }
    for (const [key, amount] of Object.entries(result.gains)) {
      if (key === 'coin') state.coin += amount; else state.inventory[key] += amount;
    }
    state.step += 1;
    if (!result.reasons.length) {
      const from = skill(input), to = skill(state);
      if (from.name !== to.name) result.detail += ' 炼器水平提升至' + to.name + '。';
      state.log.unshift({ step: state.step, title: result.title, detail: result.detail });
      state.log = state.log.slice(0, 60);
    }
    result.ok = !result.reasons.length;
    return result;
  }
  function execute(state, action, args = {}) {
    const result = plan(state, action, args);
    if (!result.ok) throw new Error(result.reasons.join('\n'));
    if (!validate(result.next)) throw new Error('操作结果未通过数据检查，没有保存本次变化。');
    return result;
  }
  const API = { config, initial, validate, skill, rarity, compatible, stats, plan, execute, clone };
  root.ForgeCore = API;
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
