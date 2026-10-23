'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const {test} = require('node:test');
const source = fs.readFileSync(require('node:path').join(__dirname, '../app/src/main/assets/electricity-prefill.js'), 'utf8');
function env(url = 'https://ecard.sit.edu.cn/epay/h5/payele') {
  const timers = [], calls = [], fields = {}, titles = ['新电费缴纳', '奉贤校区', '住宿区'];
  ['elesys', 'school', 'area', 'build', 'floor', 'room'].forEach((id, i) => {
    const row = {style: {}}, input = {value: '', id, selected: '', row,
      getAttribute: () => input.selected, closest: () => row};
    const items = [{title: titles[i] || 'SYNTHETIC_OPTION', value: 'SYNTHETIC_' + id}];
    input.picker = {config: {items: i === 0 ? items : []}, updateInputValue(values, labels) {
      assert.equal(typeof values.each, 'function', 'school selector expects a jQuery collection');
      calls.push(id); input.value = labels[0]; input.selected = values[0];
      if (i < 2) timers.push(() => { e['data' + (i + 1)] = [{}]; fields[['school', 'area'][i]].picker.config.items = [{title: titles[i + 1], value: 'SYNTHETIC_' + i}]; });
    }};
    fields[id] = input;
  });
  const e = {location: new URL(url), timers, calls, fields, document: {getElementById: id => fields[id]}};
  e.data = [{}];
  e.jQuery = input => Array.isArray(input) ? {0: input[0], each(fn) { input.forEach((value, index) => fn(index, value)); }} : {data: () => input.picker}; e.jQuery.fn = {select() {}};
  e.setTimeout = fn => timers.push(fn); e.window = e;
  return vm.createContext(e);
}
function drive(e) { let count = 0; while (e.timers.length && count++ < 120) e.timers.shift()(); return e.__xinxinElectricity.status; }
test('selects the three defaults in order, leaving room selection and payment to the user', () => {
  const e = env(); vm.runInContext(source, e);
  assert.equal(e.fields.elesys.row.style.display, undefined, 'do not hide a partial form');
  assert.equal(drive(e), 'complete'); assert.deepEqual(e.calls, ['elesys', 'school', 'area']);
  for (const id of ['elesys', 'school', 'area']) assert.equal(e.fields[id].row.style.display, 'none');
  for (const id of ['build', 'floor', 'room']) { assert.equal(e.fields[id].value, ''); assert.equal(e.fields[id].row.style.display, undefined); }
  assert.equal(vm.runInContext(source, e), 'complete'); assert.equal(e.calls.length, 3, 'reinjection is idempotent');
});
test('keeps the original form visible when expected options are missing, ambiguous or unavailable', () => {
  for (const mode of ['missing', 'ambiguous', 'placeholder', 'plugin', 'row']) {
    const e = env(), input = e.fields.elesys;
    if (mode === 'missing') input.picker.config.items = [];
    if (mode === 'ambiguous') input.picker.config.items.push(input.picker.config.items[0]);
    if (mode === 'placeholder') input.picker.config.items[0].value = '-1';
    if (mode === 'plugin') input.picker = null;
    if (mode === 'row') input.closest = () => null;
    vm.runInContext(source, e); assert.equal(drive(e), 'fallback', mode);
    for (const id of ['elesys', 'school', 'area']) assert.equal(e.fields[id].row.style.display, undefined);
  }
});
test('does not select controls on login pages, other hosts, or insecure URLs', () => {
  for (const url of ['https://ecard.sit.edu.cn/epay/h5/login', 'https://ecard.sit.edu.cn.evil.example/epay/h5/payele', 'http://ecard.sit.edu.cn/epay/h5/payele']) {
    const e = env(url); assert.equal(vm.runInContext(source, e), 'blocked'); assert.equal(e.calls.length, 0);
  }
});
test('does not reset already selected matching defaults', () => {
  const e = env();
  for (const [i, id] of ['elesys', 'school', 'area'].entries()) {
    const f = e.fields[id]; f.picker.config.items = [{title: ['新电费缴纳', '奉贤校区', '住宿区'][i], value: 'SYNTHETIC_' + id}];
    if (i) e['data' + i] = [{}];
    f.value = f.picker.config.items[0].title; f.selected = f.picker.config.items[0].value;
  }
  e.fields.room.value = 'SYNTHETIC_ROOM'; vm.runInContext(source, e);
  assert.equal(drive(e), 'complete'); assert.equal(e.calls.length, 0); assert.equal(e.fields.room.value, 'SYNTHETIC_ROOM');
});
