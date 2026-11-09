'use strict';
// The balance path now uses the school's own JSON cascade. These cases pin the
// two new branches: jumping straight to /epay/h5/eleresult with the saved ids,
// and rebuilding those ids once when the result page carries no balance.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/electricity-page.js'), 'utf8');

const roles = ['elesys', 'school', 'area', 'build', 'floor', 'room'];
const titles = {elesys: '新电费缴纳', school: '奉贤校区', area: '住宿区', build: '23号楼', floor: '第11层', room: '1104'};
const values = {elesys: 'SYS1', school: 'AREA1', area: 'D1', build: 'B1', floor: 'F1', room: 'R1'};
const saved = {fields: roles.map(role => ({role, id: role, value: values[role], title: titles[role]}))};

function node(tag, text = '', children = []) {
  const el = {
    tagName: tag.toUpperCase(), className: '', id: '', style: {}, children, parentElement: null,
    value: '', type: 'text', hidden: false, _text: text,
    get textContent() { return [this._text, ...this.children.map(child => child.textContent)].filter(Boolean).join(' '); },
    getAttribute() { return null; },
    querySelector(selector) { return selectors(this, selector)[0] || null; },
    querySelectorAll(selector) { return selectors(this, selector); },
    getElementsByTagName(name) { return descendants(this).filter(child => name === '*' || child.tagName === name.toUpperCase()); },
    append(child) { child.parentElement = this; this.children.push(child); return child; }
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
    const have = name === 'type' ? el.type : name === 'id' ? el.id : name === 'class' ? el.className : el.getAttribute(name);
    if (want === null ? have == null : String(have) !== want) return false;
  }
  return true;
}
function selectors(root, selector) {
  const parts = selector.split(',').map(part => part.trim()).filter(Boolean);
  return descendants(root).filter(child => parts.some(part => matches(child, part)));
}
function cell(label, value) {
  const row = node('div', '', [node('div', label), node('div', value)]);
  row.className = 'weui-cell';
  return row;
}

/** A result page plus the JSON cascade the school exposes for rebuilding the ids. */
function env(options = {}) {
  let now = 0;
  const timers = [];
  const rows = [];
  if (options.room !== null) rows.push(cell('房间', options.room === undefined ? '1104' : options.room));
  if (options.balance !== null) rows.push(cell(options.label || '剩余电量', options.value || '14.58度'));
  const body = node('body', '', rows);
  const jumped = [], posted = [];
  const e = {
    location: new URL(options.url || 'https://ecard.sit.edu.cn/epay/h5/eleresult'),
    document: {
      body,
      querySelector: selector => selectors(body, selector)[0] || null,
      querySelectorAll: selector => selectors(body, selector),
      getElementById: () => null
    },
    getComputedStyle: el => ({display: el.style.display || 'block', visibility: el.style.visibility || 'visible'}),
    Date: class extends Date { static now() { return now; } },
    setTimeout: (fn, ms = 0) => timers.push({fn, at: now + ms}),
    jumped
  };
  e.location.replace = url => jumped.push(url);
  e.location.assign = url => jumped.push(url);
  e.window = e;
  const defaultFetch = (url, init) => {
    posted.push({url, init});
    const routes = {
      '/epay/h5/getelesys.json': [{elcname: '新电费缴纳', elcsysid: 'SYS9'}],
      '/epay/h5/getarea.json': [{areaName: '奉贤校区', areaId: 'AREA9'}],
      '/epay/h5/getdistrict.json': [{districtName: '住宿区', districtId: 'D9'}],
      '/epay/h5/getbuild.json': [{buiName: '23号楼', buiId: 'B9'}],
      '/epay/h5/getfloor.json': [{floorName: '第11层', floorId: 'F9'}],
      '/epay/h5/getroom.json': [{roomName: '1104', roomId: 'R9'}]
    };
    const list = routes[url];
    return Promise.resolve({
      ok: !!list,
      status: list ? 200 : 404,
      json: () => Promise.resolve(list ? {retcode: '0', list} : {retcode: '1', retmsg: 'missing'})
    });
  };
  if (options.fetch !== null) e.fetch = options.fetch || defaultFetch;
  e.posted = posted;
  e.advance = ms => {
    const until = now + ms;
    for (;;) {
      timers.sort((a, b) => a.at - b.at);
      if (!timers.length || timers[0].at > until) break;
      const timer = timers.shift();
      now = timer.at;
      timer.fn();
    }
    now = until;
  };
  return vm.createContext(e);
}
function run(e, plan) { return JSON.parse(vm.runInContext(source.replace('__ELECTRICITY_PLAN__', JSON.stringify(plan)), e)); }
const flush = () => new Promise(resolve => setImmediate(resolve));

(async () => {
  // 1. Saved ids are complete: jump straight to the result page, no cascade, no picker.
  const direct = env({url: 'https://ecard.sit.edu.cn/epay/h5/payele', balance: null, room: null});
  const started = run(direct, {action: 'balance', room: saved});
  assert.equal(started.status, 'pending');
  direct.advance(0);
  assert.equal(direct.jumped.length, 1, '选择页直接跳到结果页');
  assert.equal(direct.jumped[0], '/epay/h5/eleresult?sysid=SYS1&roomid=R1&areaid=AREA1&buildid=B1');
  assert.equal(direct.posted.length, 0, '有完整 id 时不调用阶梯接口');

  // 2. Result page with a labelled balance and the matching room.
  const plain = env();
  const read = run(plain, {action: 'balance', room: saved});
  assert.equal(read.status, 'complete');
  assert.equal(read.balance.value, '14.58');
  assert.equal(read.balance.unit, '度');
  assert.equal(plain.posted.length, 0);

  // 3. Room mismatch on the result page is still refused.
  assert.equal(run(env({room: '9999'}), {action: 'balance', room: saved}).reason, 'room');
  assert.equal(run(env({room: null}), {action: 'balance', room: saved}).reason, 'room', '缺少房号不能采信');

  // Both the first result and the rebuilt result may render the balance asynchronously.
  for (const retry of [true, false]) {
    const delayed = env({balance: null, url: 'https://ecard.sit.edu.cn/epay/h5/eleresult' + (retry ? '?retry=1' : '')});
    assert.equal(run(delayed, {action: 'balance', room: saved}).status, 'pending', '结果未渲染时先等待');
    delayed.advance(9000);
    assert.equal(delayed.__xinxinElectricityPage.status, 'pending');
    assert.equal(delayed.posted.length, 0, '等待期间不重建编号');
    assert.equal(delayed.jumped.length, 0);
    // A repeated injection must reuse the watcher and its original deadline.
    assert.equal(run(delayed, {action: 'balance', room: saved}).status, 'pending');
    delayed.document.body.append(cell('剩余电量', '14.58度'));
    delayed.advance(300);
    const result = delayed.__xinxinElectricityPage;
    assert.equal(result.status, 'complete', '延迟出现的余额能完成读取');
    assert.equal(result.balance.value, '14.58');
    assert.equal(result.balance.unit, '度');
    assert.equal(JSON.stringify(result.room), JSON.stringify(saved), '异步结果保留寝室供 Java 保存');
    delayed.advance(10000);
    assert.equal(delayed.posted.length, 0, '完成后不再重建编号');
    assert.equal(delayed.jumped.length, 0);
  }

  // 4. No balance: rebuild the ids from the cascade, then open the result page once with retry=1.
  const rebuild = env({balance: null, room: '1104'});
  const running = run(rebuild, {action: 'balance', room: saved});
  assert.equal(running.status, 'pending');
  rebuild.advance(9900);
  assert.equal(rebuild.posted.length, 0, '等待满十秒后才重建编号');
  run(rebuild, {action: 'balance', room: saved});
  rebuild.advance(300);
  for (let i = 0; i < 8; i++) await flush();
  assert.deepEqual(rebuild.posted.map(call => call.url), [
    '/epay/h5/getelesys.json', '/epay/h5/getarea.json', '/epay/h5/getdistrict.json',
    '/epay/h5/getbuild.json', '/epay/h5/getfloor.json', '/epay/h5/getroom.json'
  ], '按学校的六级阶梯重建 id');
  assert.equal(rebuild.jumped.length, 1);
  assert.equal(rebuild.jumped[0], '/epay/h5/eleresult?sysid=SYS9&roomid=R9&areaid=AREA9&buildid=B9&retry=1');
  assert.equal(rebuild.posted[0].init.headers['Content-Type'], 'application/json', '第一级用 JSON');
  assert.match(String(rebuild.posted[1].init.body), /^sysid=SYS9$/, '后续层级用表单');
  assert.equal(rebuild.posted[1].init.headers['Content-Type'], 'application/x-www-form-urlencoded; charset=UTF-8');

  // 5. retry=1 with no balance stops instead of looping.
  const exhausted = env({balance: null, url: 'https://ecard.sit.edu.cn/epay/h5/eleresult?sysid=1&retry=1'});
  assert.equal(run(exhausted, {action: 'balance', room: saved}).status, 'pending');
  exhausted.advance(9900);
  assert.equal(exhausted.__xinxinElectricityPage.status, 'pending', '重试页同样等待余额');
  exhausted.advance(300);
  assert.equal(exhausted.__xinxinElectricityPage.status, 'failed');
  assert.equal(exhausted.__xinxinElectricityPage.reason, 'page');
  assert.equal(exhausted.posted.length, 0, '重建过一次后不再重试');
  assert.equal(exhausted.jumped.length, 0);

  // 6. A cascade that cannot match the saved room name fails, it does not jump.
  const broken = env({balance: null, room: '1104', fetch: url => Promise.resolve({
    ok: true, status: 200,
    json: () => Promise.resolve({retcode: '0', list: url === '/epay/h5/getroom.json'
      ? [{roomName: '2201', roomId: 'R9'}]
      : [{elcname: '新电费缴纳', elcsysid: 'SYS9'}, {areaName: '奉贤校区', areaId: 'AREA9'},
         {districtName: '住宿区', districtId: 'D9'}, {buiName: '23号楼', buiId: 'B9'},
         {floorName: '第11层', floorId: 'F9'}]})
  })});
  const missingRoom = run(broken, {action: 'balance', room: saved});
  assert.equal(missingRoom.status, 'pending');
  broken.advance(10200);
  for (let i = 0; i < 8; i++) await flush();
  assert.equal(broken.__xinxinElectricityPage.status, 'failed');
  assert.equal(broken.jumped.length, 0, '房间名对不上时不跳转');

  // 7. Without fetch the old selector flow still runs (offline WebViews and tests).
  const legacy = env({url: 'https://ecard.sit.edu.cn/epay/h5/payele', balance: null, room: null, fetch: null});
  const fallback = run(legacy, {action: 'balance', room: saved});
  assert.equal(fallback.stage, 'room', '缺少 fetch 时回退到选择器流程');
  assert.equal(legacy.jumped.length, 0, '缺少 fetch 时不跳结果页');

  // Waiting still checks the room and the page's trust boundary before accepting a balance.
  const wrongRoom = env({balance: null, room: '9999'});
  run(wrongRoom, {action: 'balance', room: saved});
  wrongRoom.document.body.append(cell('剩余电量', '14.58度'));
  wrongRoom.advance(300);
  assert.equal(wrongRoom.__xinxinElectricityPage.reason, 'room', '异步余额仍核对房号');
  assert.equal(wrongRoom.posted.length, 0);

  const moved = env({balance: null});
  run(moved, {action: 'balance', room: saved});
  moved.location.href = 'https://example.com/';
  moved.document.body.append(cell('剩余电量', '14.58度'));
  moved.advance(300);
  assert.equal(moved.__xinxinElectricityPage.reason, 'moved', '跳到非学校页面后停止等待');

  const login = env({balance: null});
  run(login, {action: 'balance', room: saved});
  const password = node('input'); password.type = 'password';
  login.document.body.append(password);
  login.advance(300);
  assert.equal(login.__xinxinElectricityPage.reason, 'login', '等待期间发现登录页则交还认证');

  console.log('electricity-result.test.cjs: all assertions passed');
})();
