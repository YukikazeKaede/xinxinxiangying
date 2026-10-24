'use strict';
// 0.6.2：学校手机版课表接口（xskbcxMobile_cxXsKb）逐周取回 + 登录后直达课表页的脚本检查。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const read = name => fs.readFileSync(path.join(__dirname, '../app/src/main/assets/', name), 'utf8');
const api = read('timetable-api.js');
const extract = read('timetable-extract.js');
const navigate = read('timetable-navigate.js');
const ENDPOINT = 'https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxMobile_cxXsKb.html?gnmkdm=N2154';
const WEEKLY = 'https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html?gnmkdm=N2154&layout=default';
const cases = [];
const test = (name, fn) => cases.push({name, fn});
const flush = () => new Promise(resolve => setImmediate(resolve));

function term(page) {
  const year = {value: page.year === undefined ? '2026' : page.year, selectedIndex: 0, options: [{textContent: '2026-2027'}]};
  const termSelect = {value: page.term === undefined ? '3' : page.term, selectedIndex: 0, options: [{textContent: '1'}]};
  const weeks = page.weeks === undefined ? [1, 2, 3] : page.weeks;
  const zs = {value: '1', options: weeks.map(week => typeof week === 'object' ? week : {value: String(week), textContent: String(week)})};
  // 真实页面在第 1 项写了第一教学周的日期区间。
  if (page.weeks === undefined) zs.options[0] = {value: '1', textContent: '1(2026-09-14至2026-09-20)'};
  return {xnm: year, xqm: termSelect, zs};
}
function apiEnv(url = WEEKLY, page = {}) {
  const elements = term(page), calls = [];
  const d = {
    title: '学生课表查询（按周次）',
    querySelector(selector) {
      const match = /#(\w+)/.exec(selector) || /name="(\w+)"/.exec(selector);
      return match ? elements[match[1]] || null : null;
    },
    querySelectorAll() { return []; }
  };
  // 脚本不得读取 Cookie，也不得触碰密码框。
  Object.defineProperty(d, 'cookie', {get() { throw Error('must not read cookies'); }});
  class XHR { open() {} addEventListener() {} send() {} }
  const e = {URL, location: new URL(url), document: d, XMLHttpRequest: XHR, __timers: [], calls};
  e.setTimeout = fn => { e.__timers.push(fn); return e.__timers.length; };
  e.body = page.body || (() => ({ok: true, status: 200, text: async () => JSON.stringify({
    kbList: [(page.row || {kcmc: '高等数学', xqj: '1', jcs: '1-2', zcd: '1-16周'})],
    xsxx: {XM: '测试学生', XNMC: '2026-2027', XQMMC: '1'},
    rqazcList: [{xqj: '1', rq: '2026-09-14'}]
  })}));
  e.fetch = async (target, options) => { calls.push({target, options}); return e.body(options); };
  e.window = e;
  return vm.createContext(e);
}
const runApi = (e, endpoint = ENDPOINT) => vm.runInContext(api.replace('typeof __TIMETABLE_API__ === \'string\' ? __TIMETABLE_API__ : \'\'', () => JSON.stringify(endpoint)), e);
async function drive(e, max = 80) {
  for (let i = 0; i < max; i++) {
    await flush();
    const state = e.__xinxinTimetableQuery;
    if (!state || state.status !== 'pending') return state;
    const next = e.__timers.shift();
    if (next) next(); else await flush();
  }
  throw new Error('接口遍历未收敛');
}
function navEnv(url, options = {}) {
  const location = new URL(url), assigned = [];
  location.assign = href => { assigned.push(href); };
  const present = options.form || [];
  const d = {
    querySelector(selector) {
      if (options.password && selector.includes('password')) return {value: 'secret'};
      for (const id of present) {
        if (selector.startsWith('#' + id) || selector.includes('name="' + id + '"')) return {value: '2026'};
      }
      return null;
    },
    querySelectorAll(selector) {
      if (selector.includes('iframe')) return [];
      if (selector.includes('a[href]')) return options.links || [];
      if (selector.includes('onclick')) return options.handlers || [];
      return [];
    }
  };
  const e = {URL, location, document: d, assigned};
  e.window = {location, _path: options.prefix};
  if (options.session) e.sessionStorage = options.session;
  return vm.createContext(e);
}
const runNav = (e, entry = WEEKLY) => vm.runInContext(navigate.replace("typeof __TIMETABLE_ENTRY__ === 'string' ? __TIMETABLE_ENTRY__ : ''", () => JSON.stringify(entry)), e);

test('does not touch the school interface from other origins, insecure pages, or without an endpoint', async () => {
  for (const [url, endpoint] of [['https://example.com/', ENDPOINT], ['https://jwxt.sit.edu.cn.evil.example/', ENDPOINT], ['http://jwxt.sit.edu.cn/jwglxt/kbcx/x.html', ENDPOINT], ['https://yjsgl.sit.edu.cn/jwglxt/kbcx/x.html', ENDPOINT], [WEEKLY, '']]) {
    const e = apiEnv(url);
    assert.equal(runApi(e, endpoint), 'blocked');
    await flush();
    assert.equal(e.calls.length, 0);
    assert.equal(e.__xinxinTimetableQuery, undefined);
  }
});

test('asks the school which term is current when the page has no year/term pickers', async () => {
  // 登录后停在首页时页面没有 xnm/xqm；接口在任意已登录页面都可用，靠 xsxx 确认学期。
  const e = apiEnv('https://jwxt.sit.edu.cn/jwglxt/xtgl/index_initMenu.html', {year: '', term: '', weeks: [1]});
  e.body = options => {
    const confirmed = options.body.startsWith('xnm=2026&xqm=3');
    return {ok: true, status: 200, text: async () => JSON.stringify(confirmed
      ? {kbList: [{kcmc: '高等数学', xqj: '1', jcs: '1-2', zcd: '1-16周'}], xsxx: {XM: '学生', XNMC: '2026-2027', XQMMC: '1'}, rqazcList: [{xqj: '1', rq: '2026-09-14'}]}
      : {kbList: null})};
  };
  assert.equal(runApi(e), 'started');
  const state = await drive(e);
  assert.equal(state.status, 'complete');
  assert.equal(state.semester, '2026-2027学年第1学期');
  assert.equal(state.termStart, '2026-09-14');
  assert.equal(e.calls.length, 1, '确认学期的那次请求同时带回第 1 周课程，不重复请求');
});

test('reports no-term when the school never confirms a term', async () => {
  const e = apiEnv('https://jwxt.sit.edu.cn/jwglxt/xtgl/index_initMenu.html', {year: '', term: '', weeks: [1]});
  e.body = () => ({ok: true, status: 200, text: async () => JSON.stringify({kbList: null})});
  assert.equal(runApi(e), 'started');
  const state = await drive(e);
  assert.equal(state.status, 'failed');
  assert.equal(state.reason, 'no-term');
});

test('walks every week with the observed request body and same-origin cookies', async () => {
  const e = apiEnv(WEEKLY, {weeks: [{value: '1', textContent: '1(2026-09-14至2026-09-20)'}, {value: '2', textContent: '2'}, {value: '3', textContent: '3'}]});
  assert.equal(runApi(e), 'started');
  const state = await drive(e);
  assert.equal(state.status, 'complete');
  assert.equal(e.calls.length, 3);
  assert.deepEqual(e.calls.map(call => call.target), [ENDPOINT, ENDPOINT, ENDPOINT]);
  assert.deepEqual(e.calls.map(call => call.options.body), [
    'xnm=2026&xqm=3&zs=1&kblx=1&doType=app&xh=',
    'xnm=2026&xqm=3&zs=2&kblx=1&doType=app&xh=',
    'xnm=2026&xqm=3&zs=3&kblx=1&doType=app&xh='
  ]);
  for (const {options} of e.calls) {
    assert.equal(options.method, 'POST');
    assert.equal(options.credentials, 'same-origin');
    assert.equal(options.headers['Content-Type'], 'application/x-www-form-urlencoded;charset=UTF-8');
    assert.equal(options.headers['X-Requested-With'], 'XMLHttpRequest');
    assert.ok(!JSON.stringify(options).includes('password'));
  }
  assert.equal(state.semester, '2026-2027学年第1学期');
  assert.equal(state.termStart, '2026-09-14');
  assert.equal(state.termWeeks, 3);
  assert.equal(state.raw.data.kbList.length, 1, '逐周返回的同一条课程合并为一条');
  assert.equal(state.raw.data.xsxx.XM, '测试学生', '姓名元数据随学校课表响应保留');
  assert.equal(state.raw.data.xsxx.XH, '', '没有返回学号时不推测学号');
  assert.equal(state.raw.url, ENDPOINT);
});

test('keeps distinct rows and records the weeks the school failed to return', async () => {
  const physics = {kcmc: '大学物理实验2', xqj: '2', jcs: '10-11', zcd: '第6周'};
  const design = {kcmc: '[自修]三维造型设计NX', xqj: '1', jcs: '1-2', zcd: '1-8周'};
  const e = apiEnv(WEEKLY, {weeks: [1, 2, 3]});
  e.body = options => options.body.includes('zs=2')
    ? {ok: false, status: 500, text: async () => ''}
    : {ok: true, status: 200, text: async () => JSON.stringify({kbList: [physics, design], xsxx: {XM: '学生'}, rqazcList: []})};
  assert.equal(runApi(e), 'started');
  const state = await drive(e);
  assert.equal(state.status, 'complete');
  assert.equal(state.raw.data.kbList.length, 2);
  assert.deepEqual(Array.from(state.failedWeeks), [2]);
  assert.equal(e.calls.length, 3, '单周失败不打断其余周');
});

test('a login page instead of JSON stops immediately without inventing an empty timetable', async () => {
  const e = apiEnv(WEEKLY, {weeks: [1, 2, 3]});
  e.body = () => ({ok: true, status: 200, text: async () => '<html><form id="casLoginForm"></form></html>'});
  assert.equal(runApi(e), 'started');
  const state = await drive(e);
  assert.equal(state.status, 'failed');
  assert.equal(state.raw, undefined);
  assert.equal(e.calls.length, 1, '登录失效后不再继续请求其它周');
});

test('a week without courses is not treated as a lost login', async () => {
  // 学校在没课的周返回 kbList:null；0.6.2 把它当成登录失效，整次同步直接失败。
  const e = apiEnv(WEEKLY, {weeks: [1, 2], body: () => ({ok: true, status: 200, text: async () => JSON.stringify({kbList: null, xsxx: {XM: '张三'}})})});
  assert.equal(runApi(e), 'started');
  const state = await drive(e);
  assert.equal(state.status, 'empty');
  assert.equal(state.reason, 'no-course');
  assert.equal(e.calls.length, 2, '空周照常继续下一周');
});

test('refuses to start a second pass while one is pending', async () => {
  const e = apiEnv(WEEKLY, {weeks: [1, 2]});
  assert.equal(runApi(e), 'started');
  assert.equal(runApi(e), 'busy');
  await drive(e);
  assert.equal(runApi(e), 'started', '完成后可重新查询');
});

test('extract adopts the completed interface payload as raw kbList', async () => {
  const e = apiEnv(WEEKLY, {weeks: [{value: '1', textContent: '1(2026-09-14至2026-09-20)'}, {value: '2', textContent: '2'}]});
  assert.equal(runApi(e), 'started');
  const state = await drive(e);
  const result = JSON.parse(vm.runInContext(extract, e));
  assert.equal(result.raw.length, 1);
  assert.equal(result.raw[0].data.kbList.length, state.raw.data.kbList.length);
  assert.equal(result.raw[0].data.kbList[0].kcmc, '高等数学');
  assert.equal(result.semester, '2026-2027学年第1学期');
  assert.equal(result.termStart, '2026-09-14');
  assert.equal(result.termWeeks, 2);
});

test('navigation opens the school timetable page from the post-login home page', () => {
  const e = navEnv('https://jwxt.sit.edu.cn/jwglxt/xtgl/index_initMenu.html', {prefix: '/jwglxt'});
  assert.equal(runNav(e), 'navigating');
  assert.deepEqual(e.assigned, [WEEKLY]);
});

test('navigation stays put on the timetable page, on insecure pages, and without an entry', () => {
  const ready = navEnv(WEEKLY, {});
  ready.document.querySelector = selector => selector.startsWith('#xnm') || selector.startsWith('#xqm') ? {value: '2026'} : null;
  assert.equal(runNav(ready), 'ready');
  assert.deepEqual(ready.assigned, []);
  const auth = navEnv('https://authserver.sit.edu.cn/authserver/login', {});
  assert.equal(runNav(auth), 'not-academic');
  assert.deepEqual(auth.assigned, []);
  const pg = navEnv('https://jwxt.sit.edu.cn/jwglxt/xtgl/index_initMenu.html', {});
  assert.equal(runNav(pg, ''), 'waiting-menu');
  assert.deepEqual(pg.assigned, []);
});

test('navigation leaves the old timetable entry instead of getting stuck on it', () => {
  // 旧入口只有学年/学期，没有周次下拉；0.6.2 曾在这里一直 waiting-menu，直到超时。
  const old = navEnv('https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcx_cxXSkbcxIndex.html?gnmkdm=N2154', {form: ['xnm', 'xqm']});
  assert.equal(runNav(old), 'navigating');
  assert.deepEqual(old.assigned, [WEEKLY]);
  // 按周次页本身（有 #zs）直接算就绪，不再触发跳转。
  const weekly = navEnv(WEEKLY, {form: ['xnm', 'xqm', 'zs']});
  assert.equal(runNav(weekly), 'ready');
  assert.deepEqual(weekly.assigned, []);
});

test('navigation stops reopening the timetable page after three attempts', () => {
  const store = {value: '0'};
  const session = {
    getItem: () => store.value,
    setItem: (key, value) => { store.value = value; },
    removeItem: () => { store.value = '0'; }
  };
  const home = () => navEnv('https://jwxt.sit.edu.cn/jwglxt/xtgl/index_initMenu.html', {session});
  assert.equal(runNav(home()), 'navigating');
  assert.equal(runNav(home()), 'navigating');
  assert.equal(runNav(home()), 'navigating');
  const fourth = home();
  assert.equal(runNav(fourth), 'waiting-menu');
  assert.deepEqual(fourth.assigned, [], '第四次不再自动跳转，交给「手动导入」');
});

test('navigation never follows the entry to another origin and still honours real menu links', () => {
  const cross = navEnv('https://jwxt.sit.edu.cn/jwglxt/xtgl/index_initMenu.html', {});
  assert.equal(runNav(cross, 'https://evil.example/kbcx/xskbcxZccx_cxXskbcxIndex.html'), 'waiting-menu');
  assert.deepEqual(cross.assigned, []);
  const linked = navEnv('https://jwxt.sit.edu.cn/jwglxt/xtgl/index_initMenu.html', {
    links: [{getAttribute: name => name === 'href' ? '/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html?gnmkdm=N2154' : null}]
  });
  assert.equal(runNav(linked, ''), 'navigating');
  assert.equal(linked.assigned.length, 1, '没有固定入口时仍跟随页面自己的课表链接');
  const password = navEnv(WEEKLY, {password: true});
  assert.equal(runNav(password), 'needs-login');
});

function deferredWeeks(e) {
  const pending = new Map(); let active = 0, peak = 0;
  e.fetch = (target, options) => {
    const week = Number(new URLSearchParams(options.body).get('zs'));
    e.calls.push({target, options}); active++; peak = Math.max(peak, active);
    return new Promise(resolve => pending.set(week, data => {
      pending.delete(week); active--;
      resolve({ok: true, text: async () => typeof data === 'string' ? data : JSON.stringify(data || {
        kbList: [{kcmc: 'SYNTHETIC_WEEK_' + week, xqj: '1', jcs: '1-2', zcd: week + '周'}],
        xsxx: {XM: 'SYNTHETIC_STUDENT'}
      })});
    }));
  };
  return {pending, peak: () => peak};
}

test('bounds concurrency to two and merges out-of-order responses by week', async () => {
  const e = apiEnv(WEEKLY, {weeks: [1, 2, 3, 4, 5]}); const d = deferredWeeks(e);
  runApi(e); d.pending.get(1)(); await flush();
  assert.deepEqual([...d.pending.keys()], [2, 3]);
  d.pending.get(3)(); await flush(); assert.deepEqual([...d.pending.keys()], [2, 4]);
  d.pending.get(4)(); await flush(); d.pending.get(5)(); await flush();
  assert.equal(e.__xinxinTimetableQuery.status, 'pending', '第2周未完成时不能提前交付');
  d.pending.get(2)(); await flush(); const state = e.__xinxinTimetableQuery;
  assert.equal(state.status, 'complete'); assert.equal(state.week, 5); assert.equal(d.peak(), 2);
  assert.deepEqual(Array.from(state.raw.data.kbList, r => r.kcmc), [1,2,3,4,5].map(w => 'SYNTHETIC_WEEK_' + w));
  assert.equal(e.__timers.length, 0, '没有逐周固定等待');
});

test('mid-pass login expiry stops scheduling and ignores late concurrent responses', async () => {
  const e = apiEnv(WEEKLY, {weeks: [1, 2, 3, 4, 5]}); const d = deferredWeeks(e);
  runApi(e); d.pending.get(1)(); await flush();
  d.pending.get(2)('<html>login</html>'); await flush();
  assert.equal(e.__xinxinTimetableQuery.reason, 'login');
  d.pending.get(3)(); await flush();
  assert.equal(e.calls.length, 3, '最多保留一个已发出的请求，不继续新请求');
  assert.equal(e.__xinxinTimetableQuery.status, 'failed'); assert.equal(e.__xinxinTimetableQuery.raw, undefined);
});

(async () => {
  let failures = 0;
  for (const {name, fn} of cases) {
    try { await fn(); console.log('PASS ' + name); } catch (error) { failures++; console.error('FAIL ' + name + '\n' + error.stack); }
  }
  console.log(`${cases.length - failures}/${cases.length} tests passed`);
  process.exitCode = failures ? 1 : 0;
})();
