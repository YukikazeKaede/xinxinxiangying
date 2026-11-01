'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/electricity-page.js'), 'utf8');
const roles = ['elesys', 'school', 'area', 'build', 'floor', 'room'];
const titles = {elesys: '新电费缴纳', school: '奉贤校区', area: '住宿区', build: '23号楼', floor: '第11层', room: '1104'};
const selected = {fields: roles.map(role => ({role, id: role, value: 'SYNTHETIC_' + role, title: titles[role]}))};

/** Minimal DOM good enough for the adapter: tag, class and attribute selectors, text, clicks. */
function node(tag, text = '', children = []) {
  const el = {
    tagName: tag.toUpperCase(), className: '', id: '', style: {}, children, parentElement: null,
    value: '', type: 'text', readOnly: false, hidden: false, _text: text, clicks: 0, selected: '',
    get textContent() { return [this._text, ...this.children.map(child => child.textContent)].filter(Boolean).join(' '); },
    getAttribute(name) { return name === 'data-values' ? (this.selected || null) : name === 'readonly' ? (this.readOnly ? '' : null) : null; },
    setAttribute(name, value) { if (name === 'data-values') this.selected = value; },
    querySelector(selector) { return selectors(this, selector)[0] || null; },
    querySelectorAll(selector) { return selectors(this, selector); },
    getElementsByTagName(name) { return descendants(this).filter(child => name === '*' || child.tagName === name.toUpperCase()); },
    click() { this.clicks++; if (this.onclick) this.onclick(); },
    append(child) { child.parentElement = this; this.children.push(child); return child; },
    remove(child) { this.children = this.children.filter(item => item !== child); }
  };
  children.forEach(child => { child.parentElement = el; });
  return el;
}
function descendants(root) { return root.children.flatMap(child => [child, ...descendants(child)]); }
function matches(el, selector) {
  const parts = selector.trim().match(/^([a-zA-Z*]*)((?:\.[\w-]+)*)((?:\[[^\]]+\])*)$/);
  if (!parts) return false;
  const [, tag, classes, attributes] = parts;
  if (tag && tag !== '*' && el.tagName !== tag.toUpperCase()) return false;
  for (const name of (classes.match(/\.[\w-]+/g) || [])) {
    if (!(' ' + el.className + ' ').includes(' ' + name.slice(1) + ' ')) return false;
  }
  for (const raw of (attributes.match(/\[[^\]]+\]/g) || [])) {
    const body = raw.slice(1, -1), split = body.indexOf('=');
    const name = split < 0 ? body : body.slice(0, split);
    const want = split < 0 ? null : body.slice(split + 1).replace(/^['"]|['"]$/g, '');
    const have = name === 'type' ? el.type : name === 'id' ? el.id : name === 'class' ? el.className
      : name === 'readonly' ? (el.readOnly ? '' : null) : el.getAttribute(name);
    if (want === null ? have == null : String(have) !== want) return false;
  }
  return true;
}
function selectors(root, selector) {
  const parts = selector.split(',').map(part => part.trim()).filter(Boolean);
  return descendants(root).filter(child => parts.some(part => matches(child, part)));
}
/** The school's result page: 房间, a labelled balance, and the recharge amount we never touch. */
function cells(label, value, room) {
  const list = [];
  if (room !== null) { const row = node('div', '', [node('div', '房间'), node('div', room)]); row.className = 'weui-cell'; list.push(row); }
  const balance = node('div', '', [node('div', label), node('div', value)]); balance.className = 'weui-cell'; list.push(balance);
  const amount = node('input'); amount.type = 'text';
  const recharge = node('div', '', [node('div', '充值'), amount]); recharge.className = 'weui-cell';
  list.push(recharge);
  return list;
}
/** The school's first page: six cascading selectors plus the read-only 下一步 query button. */
function env(options = {}) {
  let now = 0;
  const queue = [], calls = [], fields = {};
  const balance = node('div', '');
  const pay = node('button', '充值电费');
  const step = node('button', '下一步');
  const rows = roles.map(role => {
    const input = node('input'); input.id = role; input.type = 'text'; fields[role] = input;
    const row = node('div', '', [input]); row.className = 'weui-cell'; return row;
  });
  const body = node('body', '', [...rows, balance, step, pay]);
  if (options.login) { const secret = node('input'); secret.type = 'password'; body.append(secret); }
  const e = {location: new URL(options.url || 'https://ecard.sit.edu.cn/epay/h5/payele'),
    document: {body, querySelector: selector => selectors(body, selector)[0] || null,
      querySelectorAll: selector => selectors(body, selector),
      getElementById: id => descendants(body).includes(fields[id]) ? fields[id] : null},
    getComputedStyle: el => ({display: el.style.display || 'block', visibility: el.style.visibility || 'visible'}),
    Date: class extends Date { static now() { return now; } },
    setTimeout: (fn, ms = 0) => queue.push({fn, at: now + ms})};
  e.window = e; e.fields = fields; e.balance = balance; e.calls = calls; e.pay = pay; e.step = step; e.body = body;
  Object.defineProperty(e.document, 'cookie', {get() { throw new Error('不得读取 Cookie'); }});
  e.jQuery = input => (Array.isArray(input) ? {0: input[0], each() {}} : {data: () => input.picker});
  e.jQuery.fn = {select() {}}; e.jQuery.active = 0;
  roles.forEach((role, index) => {
    const input = fields[role];
    input.picker = {config: {items: index === 0 ? [selected.fields[index]] : []},
      updateInputValue(values, labels) {
        calls.push(role); input.selected = values[0]; input.value = labels[0];
        for (const child of roles.slice(index + 1)) { if (!fields[child]) continue; fields[child].selected = ''; fields[child].value = ''; fields[child].picker.config.items = []; }
        e.jQuery.active++;
        e.setTimeout(() => {
          if (index < roles.length - 1 && fields[roles[index + 1]]) fields[roles[index + 1]].picker.config.items = [selected.fields[index + 1]];
          else balance._text = options.text === undefined ? '电费余额：42.50 元' : options.text;
          e.jQuery.active--;
        }, 600);
      }};
  });
  step.onclick = () => {
    if (!options.swap) return;
    // The school's own query swaps a result layout into the same document.
    rows.forEach(row => body.remove(row));
    for (const cell of cells(options.label || '剩余电量', options.value || '14.58度', options.room === undefined ? titles.room : options.room)) body.append(cell);
  };
  e.tick = (count = 1) => {
    for (let i = 0; i < count && queue.length; i++) {
      queue.sort((a, b) => a.at - b.at); const next = queue.shift(); now = next.at; next.fn();
    }
  };
  e.drive = () => {
    for (let i = 0; queue.length && i < 4000 && e.__xinxinElectricityPage.status === 'pending'; i++) {
      queue.sort((a, b) => a.at - b.at); const next = queue.shift(); now = next.at; next.fn();
    }
    return e.__xinxinElectricityPage;
  };
  /** Plays the user: picks one level through the school's own picker. */
  e.user = role => {
    const index = roles.indexOf(role), field = fields[role];
    field.picker.updateInputValue([selected.fields[index].value], [titles[role]]);
  };
  return vm.createContext(e);
}
/** A separate document: the school's result page, reached by a full navigation. */
function resultEnv(options = {}) {
  const body = node('body', '', [...cells(options.label || '剩余电量', options.value || '14.58度',
    options.room === undefined ? titles.room : options.room), node('button', '充值')]);
  const e = {location: new URL(options.url || 'https://ecard.sit.edu.cn/epay/h5/payele/result'),
    document: {body, querySelector: selector => selectors(body, selector)[0] || null,
      querySelectorAll: selector => selectors(body, selector), getElementById: () => null},
    getComputedStyle: el => ({display: el.style.display || 'block', visibility: el.style.visibility || 'visible'}),
    Date: class extends Date { static now() { return 0; } },
    setTimeout: () => 0};
  e.window = e; e.body = body;
  Object.defineProperty(e.document, 'cookie', {get() { throw new Error('不得读取 Cookie'); }});
  return vm.createContext(e);
}
function run(e, plan) { return JSON.parse(vm.runInContext(source.replace('__ELECTRICITY_PLAN__', JSON.stringify(plan)), e)); }

// 选择页：未选完不能保存，也不能提前点下一步。
const fresh = env();
assert.equal(run(fresh, {action: 'capture'}).reason, 'room', '未选完寝室不能保存');
run(fresh, {action: 'balance', room: selected});
assert.equal(fresh.step.clicks, 0, '未恢复寝室前不能点下一步');
const complete = fresh.drive();
assert.equal(complete.status, 'complete'); assert.equal(complete.balance.value, '42.5'); assert.equal(complete.balance.unit, '元');
assert.equal(fresh.calls.join(','), roles.join(','), '按学校层级等待异步选项后恢复寝室');
assert.equal(fresh.pay.clicks, 0, '应用从不点击充值');

// 选寝室：只自动填写电控校区/缴费校区/缴费区域，楼栋楼层房间留给用户；选完自动保存。
const selection = env();
run(selection, {action: 'choose'});
selection.tick(30);
assert.equal(selection.pay.style.display, 'none', '选寝室阶段隐藏充值控件');
assert.equal(selection.step.style.display, 'none', '选寝室阶段不提供进入充值页的下一步');
assert.deepEqual(selection.calls, ['elesys', 'school', 'area'], '只自动填写前三层，不代选楼栋/楼层/房间');
assert.equal(selection.fields.build.selected, '', '更换寝室时不预填楼栋');
assert.equal(selection.fields.room.value, '', '更换寝室时不预填房间');
assert.equal(selection.__xinxinElectricityPage.status, 'pending', '未选完房间前不保存');
selection.user('build'); selection.tick(8);
selection.user('floor'); selection.tick(8);
selection.user('room');
const autoSaved = selection.drive();
assert.equal(autoSaved.status, 'complete', '选完房间自动保存');
assert.equal(autoSaved.room.fields[5].value, 'SYNTHETIC_room');
assert.equal(selection.pay.clicks, 0, '选寝室全程不点充值');

// 重新选回同一间寝室也会立刻保存并刷新，不会出现“选了没反应”。
const repeated = env();
run(repeated, {action: 'choose', room: JSON.parse(JSON.stringify(selected))});
repeated.tick(30);
repeated.user('build'); repeated.tick(8); repeated.user('floor'); repeated.tick(8); repeated.user('room');
const repeatedSave = repeated.drive();
assert.equal(repeatedSave.status, 'complete', '重新选回同一间寝室也保存');
assert.equal(repeatedSave.room.fields[5].value, 'SYNTHETIC_room');
// 换成另一间寝室同样自动保存。
const changed = env();
run(changed, {action: 'choose', room: JSON.parse(JSON.stringify(selected))});
changed.tick(30);
changed.user('build'); changed.tick(8); changed.user('floor'); changed.tick(8);
changed.fields.room.selected = 'SYNTHETIC_ROOM_2'; changed.fields.room.value = '合成寝室2';
const moved2 = changed.drive();
assert.equal(moved2.status, 'complete', '换寝室后自动保存新寝室');
assert.equal(moved2.room.fields[5].title, '合成寝室2');

// 真实学校流程：选好寝室后余额不出现，点一次学校的“下一步”，同页切换出结果。
const querying = env({text: '', swap: true});
run(querying, {action: 'balance', room: selected});
const queried = querying.drive();
assert.equal(querying.step.clicks, 1, '余额未出现时点一次学校的下一步');
assert.equal(querying.pay.clicks, 0, '查询过程不点充值');
assert.equal(queried.status, 'complete', '同页切换出结果后读取余额');
assert.equal(queried.balance.value, '14.58'); assert.equal(queried.balance.unit, '度');

// 结果页（新文档）：读取标了单位的剩余电量，并核对房号。
const page = resultEnv();
const read = run(page, {action: 'balance', room: selected});
assert.equal(read.status, 'complete'); assert.equal(read.balance.value, '14.58'); assert.equal(read.balance.unit, '度');
assert.equal(read.room.fields[5].value, 'SYNTHETIC_room', '结果页回传已保存的寝室');
const money = resultEnv({label: '电费余额', value: '42.50元'});
const moneyRead = run(money, {action: 'balance', room: selected});
assert.equal(moneyRead.balance.value, '42.5'); assert.equal(moneyRead.balance.unit, '元');
assert.equal(run(resultEnv({room: '9999'}), {action: 'balance', room: selected}).reason, 'room', '结果页房号不符时不能采信');
assert.equal(run(resultEnv({room: null}), {action: 'balance', room: selected}).reason, 'room', '结果页缺少房号时不能把余额归给已保存寝室');
const unnamedQuery = env({text: '', swap: true, room: null});
run(unnamedQuery, {action: 'balance', room: selected});
assert.equal(unnamedQuery.drive().reason, 'room', '同页切换出的结果也必须包含匹配房号');
const lateResult = resultEnv({value: '加载中'}), pendingResult = [];
lateResult.setTimeout = fn => pendingResult.push(fn);
assert.equal(run(lateResult, {action: 'balance', room: selected}).status, 'pending');
lateResult.body.children[1].children[1]._text = '14.58度';
pendingResult.shift()();
assert.equal(lateResult.__xinxinElectricityPage.status, 'complete', '结果页异步显示余额后完成读取');
assert.equal(lateResult.__xinxinElectricityPage.room.fields[5].value, 'SYNTHETIC_room', '异步结果保留已核对的寝室，供本机保存');
const stale = env(); stale.balance._text = '电费余额：99元';
const beforeRestore = run(stale, {action: 'balance', room: selected});
assert.equal(beforeRestore.status, 'pending', '选择页的已有余额不能跳过寝室恢复');
const restoredBalance = stale.drive();
assert.equal(stale.calls.join(','), roles.join(','), '读取选择页余额前先恢复并核对全部寝室选项');
assert.equal(restoredBalance.balance.value, '42.5', '不采信恢复寝室前的旧余额');

// 单位、歧义与来源检查。
for (const text of ['剩余电费：0 元', '电费余额：-3.21元', '剩余电量（度）：12.345', '剩余电量：6.5 kWh']) {
  const context = env({text}); run(context, {action: 'balance', room: selected}); const result = context.drive();
  assert.equal(result.status, 'complete', text);
  assert.equal(result.balance.unit, /电量/.test(text) ? '度' : '元');
}
const formatted = env({text: '电费余额：1,234.56元'}); run(formatted, {action: 'balance', room: selected});
assert.equal(formatted.drive().balance.value, '1234.56', '千分位金额不会被截断');
for (const text of ['电费余额：123456789元', '电费余额：1万元', '剩余金额：999元', '剩余电量：1Wh']) {
  const context = env({text}); run(context, {action: 'balance', room: selected});
  assert.equal(context.drive().reason, 'timeout', '拒绝不明确的单位、超长金额和非电费字段');
}
const restore = env(); run(restore, {action: 'restore', room: selected});
assert.equal(restore.drive().status, 'complete', '充值只恢复寝室，不执行任何支付动作');
assert.equal(restore.pay.style.display, undefined);
assert.equal(restore.step.clicks, 0, '充值页停在选择页，等用户自己核对并付款');

// 来源与登录检查。
for (const url of ['https://example.com/epay/h5/payele', 'https://ecard.sit.edu.cn/authserver/login',
  'http://ecard.sit.edu.cn/epay/h5/payele', 'https://ecard.sit.edu.cn:444/epay/h5/payele',
  'https://user@ecard.sit.edu.cn/epay/h5/payele', 'https://ecard.sit.edu.cn/epay/h5/login']) {
  const context = env({url}); assert.equal(run(context, {action: 'capture'}).reason, 'blocked', url); assert.equal(context.calls.length, 0);
}
const login = env({login: true}); assert.equal(run(login, {action: 'capture'}).reason, 'login');
const missing = env(); delete missing.fields.room; run(missing, {action: 'balance', room: selected});
assert.equal(missing.drive().status, 'failed', '选项已不存在时不能采信旧余额');
const unrelated = env({text: '校园卡余额：100 元 充值金额：20 元'}); run(unrelated, {action: 'balance', room: selected});
assert.equal(unrelated.drive().reason, 'timeout', '校园卡与充值金额不能被当作电费余额');
const ambiguous = env({text: '电费余额：20元 剩余电费：30元'}); run(ambiguous, {action: 'balance', room: selected});
assert.equal(ambiguous.drive().reason, 'timeout', '不明确的余额不能保存');
const moved = env(); run(moved, {action: 'balance', room: selected}); moved.location.href = 'https://example.com/';
assert.equal(moved.drive().reason, 'moved');
console.log('electricity-page.test.cjs: all assertions passed');
