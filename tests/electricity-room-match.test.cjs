'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../app/src/main/assets/electricity-room-match.js'), 'utf8');

function fixture(options = {}) {
  let time = 0, queries = 0, backs = 0;
  const timers = [], calls = [];
  const data = {
    sysList: [{elcname: '旧电费系统'}, {elcname: '新电费缴纳'}],
    areaList: [{areaName: '徐汇校区'}, {areaName: '奉贤校区'}],
    distList: [{districtName: '教学区'}, {districtName: '住宿区'}],
    buildList: [{buiName: '123号楼', buiId: 'SYNTHETIC_OTHER'}, {buiName: '23号楼', buiId: 'SYNTHETIC_BUILDING'}],
    floorList: [{floorName: '第1层'}, {floorName: '第11层'}],
    roomList: [{roomName: '1104', roomId: 'SYNTHETIC_ROOM'}],
    ...options.data
  };
  const page = {sysList: data.sysList, query() { queries++; }};
  const stages = ['sys', 'area', 'dist', 'build', 'floor', 'room'];
  stages.forEach((stage, i) => {
    page[stage + 'Change'] = event => {
      page[stage + 'Index'] = event.detail.value;
      calls.push(stage);
      if (i === 5) return;
      const child = stages[i + 1] + 'List';
      page[child] = [];
      if (options.stall === child) return;
      timers.push(() => { page[child] = child === 'roomList' && options.roomsByFloor
        ? options.roomsByFloor[page.floorIndex] : data[child]; });
    };
  });
  const location = new URL(options.url || 'https://ecard.sit.edu.cn/epeortal/pages/h5/elecQuery');
  const charge = {back() { backs++; location.pathname = '/epeortal/pages/h5/elecQuery'; }};
  const context = {location, Date: {now: () => time}, Promise,
    setTimeout(fn, delay) { timers.push(() => {time += delay; fn();}); },
    document: {querySelector(selector) {
      if (selector === '.elecQuery' && location.pathname.endsWith('elecQuery'))
        return options.vue3 ? {__vueParentComponent: {proxy: page}} : {__vue__: {$parent: page}};
      return selector === '.elecCharge' && location.pathname.endsWith('elecCharge') ? {__vue__: charge} : null;
    }}
  };
  context.window = context;
  vm.createContext(context);
  function run(plan = {building: '23', room: '1104'}) {
    return JSON.parse(vm.runInContext(source.replace('__ELECTRICITY_ROOM_MATCH__', () => JSON.stringify(plan)), context));
  }
  async function settle() {
    for (let i = 0; i < 2000; i++) {
      await Promise.resolve(); await Promise.resolve();
      const state = context.__xinxinDormMatch;
      if (state && ['matched', 'failed', 'submitted'].includes(state.status)) return state;
      const next = timers.shift(); if (next) next();
    }
    throw Error('fixture did not settle');
  }
  return {run, settle, context, page, calls, timers, queries: () => queries, backs: () => backs};
}

test('23 / 1104 selects all six actual options and only submits on request', async () => {
  const f = fixture(); f.run(); const state = await f.settle();
  assert.equal(state.status, 'matched');
  assert.deepEqual(Array.from(state.room.selection), ['新电费缴纳', '奉贤校区', '住宿区', '23号楼', '第11层', '1104']);
  assert.equal(state.room.buildingId, 'SYNTHETIC_BUILDING');
  assert.equal(state.room.roomId, 'SYNTHETIC_ROOM');
  assert.equal(f.queries(), 0);
  assert.equal(state.submit(), 'submitted'); assert.equal(state.submit(), 'cancelled');
  assert.equal(f.queries(), 1);
});
test('polling is idempotent, including an index of zero', async () => {
  const f = fixture({data: {buildList: [{buiName: '23号楼', buiId: 'SYNTHETIC_ZERO'}]}});
  f.run(); f.run(); const s = await f.settle(); f.run();
  assert.equal(s.status, 'matched'); assert.equal(f.page.buildIndex, 0);
  assert.equal(f.calls.filter(x => x === 'sys').length, 1);
  assert.equal(s.submit(), 'submitted');
});
test('Vue 3 component discovery uses the same school methods', async () => {
  const f = fixture({vue3: true}); f.run(); assert.equal((await f.settle()).status, 'matched');
});
test('a previously bound room is reselected before matching', async () => {
  const f = fixture({url: 'https://ecard.sit.edu.cn/epeortal/pages/h5/elecCharge'});
  f.run(); assert.equal(f.backs(), 1); f.run();
  assert.equal((await f.settle()).status, 'matched');
});
test('floor hint is verified against the real room list, with search fallback', async () => {
  const f = fixture({roomsByFloor: [[{roomName: '1104', roomId: 'SYNTHETIC_REAL'}], []]});
  f.run(); const s = await f.settle();
  assert.equal(s.status, 'matched'); assert.equal(f.page.floorIndex, 0);
  assert.equal(s.room.roomId, 'SYNTHETIC_REAL');
});
for (const [name, data, reason] of [
  ['unknown building', {buildList: [{buiName: '123号楼'}]}, 'building'],
  ['duplicate building', {buildList: [{buiName: '23号楼'}, {buiName: '23栋'}]}, 'ambiguous'],
  ['duplicate room', {roomList: [{roomName: '1104'}, {roomName: '1104'}]}, 'ambiguous'],
  ['missing room', {roomList: []}, 'room'],
  ['unknown campus', {areaList: [{areaName: '其他校区'}]}, 'campus'],
  ['duplicate campus', {areaList: [{areaName: '奉贤校区'}, {areaName: '奉贤校区'}]}, 'ambiguous']
]) test(name + ' stops without querying', async () => {
  const f = fixture({data}); f.run(); const s = await f.settle();
  assert.equal(s.status, 'failed'); assert.equal(s.reason, reason); assert.equal(f.queries(), 0);
});
test('stalled cascade times out and never queries', async () => {
  const f = fixture({stall: 'floorList'}); f.run(); const s = await f.settle();
  assert.equal(s.reason, 'timeout'); assert.equal(f.queries(), 0);
});
test('changed selectors and route changes invalidate submission', async () => {
  const f = fixture(); f.run(); const s = await f.settle();
  f.page.roomIndex = 99; assert.equal(s.submit(), 'changed'); assert.equal(f.queries(), 0);
  f.page.roomIndex = 0; f.context.location.pathname = '/epeortal/pages/h5/login';
  assert.equal(s.submit(), 'cancelled');
});
test('only the exact school origin and electricity paths are allowed', () => {
  for (const url of ['https://ecard.sit.edu.cn.attacker.test/epeortal/pages/h5/elecQuery',
    'http://ecard.sit.edu.cn/epeortal/pages/h5/elecQuery', 'https://ecard.sit.edu.cn/epeortal/pages/h5/login']) {
    const f = fixture({url}); assert.equal(f.run().reason, 'page'); assert.equal(f.calls.length, 0);
  }
  const f = fixture(); assert.equal(f.run({building: '23);evil()', room: '1104'}).reason, 'input');
});
