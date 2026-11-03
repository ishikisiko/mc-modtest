const { test } = require('node:test');
const assert = require('node:assert/strict');
const C = require('./core.js');

function rich(preset = 'beginner') {
  const s = C.initial(preset);
  s.coin = 100000;
  for (const key of Object.keys(s.inventory)) s.inventory[key] = 1000;
  return s;
}
function run(s, operation, args = {}) { return C.execute(s, operation, args).next; }

test('all starting scenarios are valid, with distinct profession and material tiers', () => {
  for (const p of ['beginner', 'artisan', 'master']) assert.equal(C.validate(C.initial(p)), true);
  assert.equal(C.skill(C.initial()).rarity, 0);
  assert.equal(C.initial().station, 2);
});
test('quoting never changes input or spends resources', () => {
  const s = C.initial(), snapshot = JSON.stringify(s);
  C.plan(s, 'refine', { id: s.items[0].id });
  assert.equal(JSON.stringify(s), snapshot);
});
test('an unaffordable operation is atomic', () => {
  const s = C.initial(); s.coin = 0;
  const snapshot = JSON.stringify(s);
  assert.throws(() => run(s, 'refine', { id: 'item-1' }), /灵石不足/);
  assert.equal(JSON.stringify(s), snapshot);
});
test('refinement increases strength without changing tier or rarity', () => {
  const s = C.initial(), item = s.items[0], next = run(s, 'refine', { id: item.id }).items[0];
  assert.equal(next.refine, 1); assert.equal(next.tier, item.tier);
  assert.equal(C.rarity(next.traits), C.rarity(item.traits));
  assert.ok(C.stats(next).primary > C.stats(item).primary);
});
test('refinement cap refuses further resource spending', () => {
  let s = rich(); for (let i = 0; i < 5; i++) s = run(s, 'refine', { id: 'item-1' });
  const snapshot = JSON.stringify(s);
  assert.throws(() => run(s, 'refine', { id: 'item-1' }), /淬炼上限/);
  assert.equal(JSON.stringify(s), snapshot);
});
test('every new tier starts stronger than the fully refined previous tier', () => {
  for (const kind of Object.keys(C.config.kinds)) for (let tier = 1; tier < 5; tier++) {
    assert.ok(C.stats({ kind, tier: tier + 1, refine: 0 }).primary > C.stats({ kind, tier, refine: 5 }).primary);
  }
});
test('ascension preserves rare traits even when the owner cannot manufacture them', () => {
  const s = rich(); s.items[0].traits = ['returning'];
  const n = run(s, 'advance', { id: 'item-1' });
  assert.equal(C.skill(n).rarity, 1);
  assert.deepEqual(n.items[0].traits, ['returning']); assert.equal(n.items[0].tier, 2);
});
test('ascension does not require max refinement and preserves condition', () => {
  const s = rich(); s.items[0].condition = 42;
  const n = run(s, 'advance', { id: 'item-1' });
  assert.equal(n.items[0].refine, 0); assert.equal(n.items[0].condition, 42);
});
test('old investment credit is bounded and consumed once', () => {
  let s = rich('master'); s.items[0].tier = 1; s.items[0].traits = [];
  for (let i = 0; i < 5; i++) s = run(s, 'refine', { id: 'item-1' });
  const p = C.execute(s, 'advance', { id: 'item-1' });
  assert.equal(p.credit, 24); assert.equal(p.next.items[0].invested, 0);
  assert.equal(C.plan(p.next, 'advance', { id: 'item-1' }).credit, 0);
});
test('high crafting skill alone cannot create a rare item', () => {
  const s = rich('master');
  const p = C.execute(s, 'craft', { kind: 'sword', tier: 1, pattern: 'plain' });
  assert.equal(C.rarity(p.after.traits), 0);
});
test('profession cap blocks self-crafting but not a qualified commission', () => {
  const s = rich(); s.known.push('frost');
  const args = { kind: 'sword', tier: 2, pattern: 'frost' };
  assert.throws(() => run(s, 'craft', args), /炼器水平/);
  const p = C.execute(s, 'craft', { ...args, commission: true });
  assert.equal(C.rarity(p.after.traits), 2); assert.equal(p.next.xp, s.xp);
  assert.ok(p.costs.coin > C.plan(s, 'craft', args).costs.coin);
});
test('commission cannot bypass missing patterns or the master ceiling', () => {
  const s = rich();
  assert.throws(() => run(s, 'craft', { kind: 'sword', tier: 1, pattern: 'frost', commission: true }), /未研读/);
  s.known.push('echo');
  assert.throws(() => run(s, 'craft', { kind: 'focus', tier: 3, pattern: 'echo', commission: true }), /传世/);
});
test('transformation changes rarity while preserving base progression', () => {
  const s = rich('artisan'); s.items[0].refine = 3; s.items[0].condition = 70;
  const before = C.stats(s.items[0]);
  const n = run(s, 'reshape', { id: 'item-1', pattern: 'frost' });
  assert.equal(C.rarity(n.items[0].traits), 2); assert.deepEqual(C.stats(n.items[0]), before);
  assert.equal(n.items[0].condition, 70);
});
test('reapplying the same traits and incompatible work both reject', () => {
  const s = rich('artisan');
  assert.throws(() => run(s, 'reshape', { id: 'item-1', pattern: 'conduct' }), /相同器性/);
  assert.throws(() => run(s, 'reshape', { id: 'item-2', pattern: 'frost' }), /不适用/);
});
test('exploration awards a rare item to a novice without teaching its manufacture', () => {
  const s = C.initial(), p = C.execute(s, 'explore', { place: 'archive' });
  assert.equal(C.skill(p.next).rarity, 0); assert.equal(C.rarity(p.after.traits), 2);
  assert.equal(p.next.known.includes('frost'), false); assert.equal(p.next.scrolls.frost, 1);
  assert.equal(run(p.next, 'equip', { id: p.created }).equipped.sword, p.created);
});
test('first-discovery gear and scrolls cannot be claimed repeatedly', () => {
  const s = run(C.initial(), 'explore', { place: 'archive' });
  const n = run(s, 'explore', { place: 'archive' });
  assert.equal(n.items.length, s.items.length); assert.equal(n.scrolls.frost, 1);
});
test('exploration is bounded and empty durability prevents a further trip', () => {
  let s = C.initial();
  for (let i = 0; i < 4; i++) s = run(s, 'explore', { place: 'mine' });
  assert.throws(() => run(s, 'explore', { place: 'mine' }), /次数已经用完/);
  s.items[0].condition = 0;
  assert.throws(() => run(s, 'explore', { place: 'archive' }), /未损坏/);
});
test('learning a found pattern consumes it once and advances knowledge', () => {
  const s = run(C.initial(), 'explore', { place: 'archive' });
  const n = run(s, 'learn', { pattern: 'frost' });
  assert.equal(n.scrolls.frost, 0); assert.equal(n.known.includes('frost'), true);
  assert.equal(n.xp, s.xp + 200);
  assert.throws(() => run(n, 'learn', { pattern: 'frost' }), /已经掌握/);
});
test('repair preserves identity, traits and investments', () => {
  const s = rich('artisan'); s.items[0].condition = 10; s.items[0].invested = 40;
  const n = run(s, 'repair', { id: 'item-1' });
  assert.equal(n.items[0].condition, 100); assert.equal(n.items[0].invested, 40);
  assert.deepEqual(n.items[0].traits, s.items[0].traits);
});
test('salvage removes the owned instance and any equipped reference', () => {
  const s = C.initial(), n = run(s, 'salvage', { id: 'item-1' });
  assert.equal(n.items.some(x => x.id === 'item-1'), false); assert.equal(n.equipped.sword, undefined);
  assert.equal(C.validate(n), true); assert.throws(() => run(n, 'salvage', { id: 'item-1' }), /选择/);
});
test('storage validates incompatible traits, duplicate identities and negative counts', () => {
  const a = C.initial(); a.items[0].traits = ['ward']; assert.equal(C.validate(a), false);
  const b = C.initial(); b.items[1].id = b.items[0].id; assert.equal(C.validate(b), false);
  const c = C.initial(); c.inventory.iron = -1; assert.equal(C.validate(c), false);
});
test('full beginner journey reaches a rare higher-tier weapon without a reset', () => {
  let s = C.initial();
  for (const [action, args] of [
    ['refine', { id: 'item-1' }], ['explore', { place: 'mine' }], ['explore', { place: 'archive' }],
    ['learn', { pattern: 'frost' }], ['reshape', { id: 'item-1', pattern: 'frost' }], ['advance', { id: 'item-1' }]
  ]) s = run(s, action, args);
  assert.equal(s.items[0].tier, 2); assert.equal(C.rarity(s.items[0].traits), 2);
  assert.equal(s.items[0].refine, 0); assert.ok(s.coin >= 0);
  assert.ok(s.items[0].history.length >= 4); assert.equal(C.validate(s), true);
});
test('selling a freshly crafted basic sword cannot refund its full costs', () => {
  const s = rich(), p = C.execute(s, 'craft', { kind: 'sword', tier: 1, pattern: 'plain' });
  const n = run(p.next, 'salvage', { id: p.created });
  assert.ok(n.coin < s.coin); assert.ok(n.inventory.iron < s.inventory.iron);
});
