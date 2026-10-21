'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const scripts = Object.fromEntries(['extract', 'query'].map(k => [k, fs.readFileSync(path.join(__dirname, '../app/src/main/assets/timetable-' + k + '.js'), 'utf8')]));
const cases = [];
const test = (name, fn) => cases.push({name, fn});
function doc(values = {}, frames = [], tables = []) {
  const d = {title: '个人课表', querySelector(s) { if (s.startsWith('#xnm')) return values.year; if (s.startsWith('#xqm')) return values.term; if (s.startsWith('[name="kzlx"')) return values.mode; return null; }, querySelectorAll(s) { return s === 'iframe' ? frames : s === 'table' ? tables : []; }};
  Object.defineProperty(d, 'cookie', {get() { throw Error('must not read cookies'); }});
  return d;
}
function env(url = 'https://jwxt.sit.edu.cn/jwglxt/kbcx/index.html', document = doc()) {
  const calls = [];
  class XHR {
    open(method, url) { this.method = method; this.url = url; }
    addEventListener(name, cb) { this.listener = cb; }
    send(body) { this.body = body; }
    respond(data, status = 200) { this.status = status; this.responseText = JSON.stringify(data); this.listener?.call(this); }
  }
  const e = {URL, location: new URL(url), document, XMLHttpRequest: XHR};
  e.window = e;
  e.fetch = async (url, options) => { calls.push({url, options}); return e.response || {ok: true, status: 200, json: async () => ({kbList: []}), clone() { return {text: async () => JSON.stringify({kbList: []})}; }}; };
  e.calls = calls;
  // WebView 定时器在测试里变成可 flush 的队列，避免真实等待。
  e.__timers = [];
  e.setTimeout = fn => { e.__timers.push(fn); return e.__timers.length; };
  e.Event = class { constructor(type) { this.type = type; } };
  return vm.createContext(e);
}
const run = (e, kind = 'extract') => vm.runInContext(scripts[kind], e);
const flush = () => new Promise(resolve => setImmediate(resolve));
function flushTimers(e, max = 20000) { let n = 0; while (e.__timers.length && n++ < max) { const fn = e.__timers.shift(); fn(); } assert.ok(n < max, '定时器未收敛'); }
function lessonNode(fields) { return {getAttribute(name) { return Object.prototype.hasOwnProperty.call(fields, name) ? fields[name] : null; }}; }

// 真实「学生课表查询（按周次）」页的最小结构：周次下拉 + #myTab 表格 + div.lesson 课程块。
function weeklyDoc(spec) {
  const state = {
    table: {innerHTML: '<initial>'},
    lessons: [],
    zs: {
      value: '',
      options: spec.weeks.map(week => ({value: String(week), textContent: week === 1 && spec.termStart ? `1(${spec.termStart}至2026-09-20)` : String(week)})),
      dispatchEvent() {}
    }
  };
  const year = {value: '2026', selectedIndex: 0, options: [{textContent: spec.yearText || '2026-2027'}]};
  const term = {value: '3', selectedIndex: 0, options: [{textContent: spec.termText || '1'}]};
  const d = {
    title: '学生课表查询（按周次）',
    getElementById(id) {
      if (id === 'zs') return state.zs;
      if (id === 'table_tb' || id === 'myTab') return state.table;
      return null;
    },
    querySelector(selector) {
      if (selector.startsWith('#xnm')) return year;
      if (selector.startsWith('#xqm')) return term;
      if (selector.startsWith('#zs')) return state.zs;
      return null;
    },
    querySelectorAll(selector) {
      if (selector === 'iframe' || selector === 'table') return [];
      if (selector === 'div.lesson, [data-kcmc][data-xqj][data-jcor]') return state.lessons;
      return [];
    }
  };
  Object.defineProperty(d, 'cookie', {get() { throw Error('must not read cookies'); }});
  return {doc: d, state};
}

test('rejects non-academic hosts and insecure pages', () => {
  for (const url of ['https://example.com/', 'https://jwxt.sit.edu.cn.evil.example/', 'https://card.sit.edu.cn/', 'http://jwxt.sit.edu.cn/']) {
    const e = env(url); const original = e.fetch;
    assert.ok(JSON.parse(run(e)).error); assert.equal(e.fetch, original); assert.equal(e.__xinxinTimetableV1, undefined);
  }
});
test('XHR captures only same-origin course endpoints; newer query replaces older', () => {
  const e = env(); run(e);
  function request(url, data, status) { const x = new e.XMLHttpRequest(); x.open('POST', url); x.send('password=secret'); x.respond(data, status); }
  for (const url of ['/login', '/payment', '/jwglxt/login_slogin.html', 'https://evil.example/xskbcx_cxXsgrkb.html', '/kbcx/not-xskbcx_cxXsgrkb.html']) request(url, {secret: true});
  assert.equal(e.__xinxinTimetableV1.raw.length, 0);
  request('/jwglxt/kbcx/xskbcx_cxXsgrkb.html?term=1', {kbList: [{kcmc: 'old'}]});
  request('/jwglxt/kbcx/xskbcx_cxXsgrkb.html?term=2', {kbList: [{kcmc: 'new'}]});
  assert.equal(e.__xinxinTimetableV1.raw.length, 1); assert.equal(e.__xinxinTimetableV1.raw[0].data.kbList[0].kcmc, 'new');
  request('/jwglxt/kbcx/getXqkb4Xs', {kbList: []}, 500); assert.equal(e.__xinxinTimetableV1.raw.length, 1);
});
test('fetch capture excludes login/payment responses', async () => {
  const e = env(); run(e);
  await e.fetch('/login'); await e.fetch('/payment'); await flush(); assert.equal(e.__xinxinTimetableV1.raw.length, 0);
  await e.fetch('/jwglxt/kbcx/xskbcx_cxXsgrkb.html'); await flush(); assert.equal(e.__xinxinTimetableV1.raw.length, 1);
});
test('reads kbList global without invoking accessors', () => {
  const e = env(); e.kbList = [{kcmc: 'Math'}]; let reads = 0;
  Object.defineProperty(e, 'kbData', {get() { reads++; throw Error('getter'); }});
  const result = JSON.parse(run(e)); assert.equal(result.raw[0].data.kbList[0].kcmc, 'Math'); assert.equal(reads, 0);
});
test('extracts same-origin iframe table and excludes cross-origin iframe', () => {
  const table = {rows: [{cells: [{textContent: 'Math', rowSpan: 2, colSpan: 1}]}]};
  const frames = [{contentWindow: {location: new URL('https://jwxt.sit.edu.cn/frame')}, contentDocument: doc({}, [], [table])}, {contentWindow: {location: new URL('https://evil.example/frame')}, contentDocument: doc({}, [], [table])}];
  const result = JSON.parse(run(env(undefined, doc({}, frames)))); assert.equal(result.tables.length, 1); assert.equal(result.tables[0].rows[0][0].text, 'Math');
});
test('extracts div.lesson data attributes from the weekly timetable page', () => {
  const {doc: d, state} = weeklyDoc({weeks: [1, 2, 3, 4, 5], termStart: '2026-09-14'});
  state.lessons = [
    lessonNode({'data-kcmc': '大学物理A2', 'data-jsxm': '戴凯', 'data-cdmc': '二教F206', 'data-xqj': '2', 'data-jcor': '1-2', 'data-zcd': '1-14周'}),
    lessonNode({'data-kcmc': '大学物理A2', 'data-jsxm': '戴凯', 'data-cdmc': '二教F206', 'data-xqj': '2', 'data-jcor': '1-2', 'data-zcd': '1-14周'}),
    lessonNode({'data-kcmc': '', 'data-xqj': '1', 'data-jcor': '1-2', 'data-zcd': '1-8周'})
  ];
  const result = JSON.parse(run(env('https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html', d)));
  assert.equal(result.lessons.length, 1, '空课程名忽略，重复课程只保留一条');
  assert.equal(result.lessons[0].kcmc, '大学物理A2');
  assert.equal(result.semester, '2026-2027学年第1学期');
  assert.equal(result.termStart, '2026-09-14');
  assert.equal(result.termWeeks, 5);
});
test('query requires jwxt course page and present selected year/term', () => {
  for (const [url, values] of [['https://yjsgl.sit.edu.cn/jwglxt/kbcx/', {year:{value:'2026'},term:{value:'3'}}], ['https://jwxt.sit.edu.cn/login', {year:{value:'2026'},term:{value:'3'}}], [undefined, {}], [undefined, {year:{value:'2026'},term:{value:''}}]]) {
    const e = env(url, doc(values)); assert.equal(run(e, 'query'), 'skip'); assert.equal(e.calls.length, 0);
  }
});
test('query encodes page selections and never submits password or reads cookies', async () => {
  const e = env(undefined, doc({year:{value:'2026 &extra=1'},term:{value:'3/春'},mode:{value:'x=y'},password:{value:'secret'}})); run(e);
  assert.equal(run(e, 'query'), 'started'); await flush();
  assert.equal(e.calls.length, 1); const {url, options} = e.calls[0]; assert.equal(url, 'https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html');
  assert.equal(options.body, 'xnm=2026%20%26extra%3D1&xqm=3%2F%E6%98%A5&kzlx=x%3Dy');
  assert.equal(options.credentials, 'same-origin'); assert.equal(options.redirect, 'error'); assert.ok(!JSON.stringify(options).includes('secret')); assert.equal(e.__xinxinTimetableQuery.status, 'complete');
});
test('query accepts selected term from same-origin iframe', async () => {
  const frame = {contentWindow:{location:new URL('https://jwxt.sit.edu.cn/jwglxt/kbcx/index.html')},contentDocument:doc({year:{value:'2026'},term:{value:'3'}})};
  const e = env('https://jwxt.sit.edu.cn/jwglxt/home', doc({}, [frame])); run(e); assert.equal(run(e, 'query'), 'started'); await flush(); assert.equal(e.__xinxinTimetableQuery.status, 'complete');
});
test('completed query isolates semester B from older captured and global semester A', async () => {
  const e = env(undefined, doc({year:{value:'2026'},term:{value:'3'}})); run(e);
  e.__xinxinTimetableV1.raw.push({url:'/jwglxt/kbcx/getXqkb4Xs',data:{kbList:[{kcmc:'semester A captured'}]}});
  e.kbList = [{kcmc:'semester A global'}];
  e.fetch = async()=>({ok:true,json:async()=>({kbList:[{kcmc:'semester B'}]})});
  assert.equal(run(e, 'query'), 'started'); await flush();
  const result = JSON.parse(run(e)); assert.equal(result.raw.length, 1);
  assert.equal(result.raw[0].data.kbList[0].kcmc, 'semester B');
  assert.ok(!JSON.stringify(result.raw).includes('semester A'));
});
test('semester labels are captured at request start even when selections change before completion', async () => {
  const year = {value:'2026',selectedIndex:0,options:[{textContent:'2026-2027'}]};
  const term = {value:'3',selectedIndex:0,options:[{textContent:'第一学期'}]};
  const e = env(undefined, doc({year,term})); run(e);
  let resolve;
  e.fetch = () => new Promise(r=>{resolve=r;});
  run(e, 'query'); assert.equal(e.__xinxinTimetableQuery.semester, '2026-2027 第一学期');
  year.value = '2025'; year.options[0].textContent = '2025-2026';
  term.value = '12'; term.options[0].textContent = '第二学期';
  resolve({ok:true,json:async()=>({kbList:[]})}); await flush();
  assert.equal(JSON.parse(run(e)).semester, '2026-2027 第一学期');
});
test('new query replaces completed marker immediately and skipped query clears it', async () => {
  const values = {year:{value:'2026'},term:{value:'3'}};
  const e = env(undefined, doc(values)); run(e);
  const old = {status:'complete',semester:'old',raw:{data:{kbList:[{kcmc:'old'}]}}};
  e.__xinxinTimetableQuery = old;
  let resolve; e.fetch = () => new Promise(r=>{resolve=r;});
  assert.equal(run(e, 'query'), 'started');
  assert.notEqual(e.__xinxinTimetableQuery, old); assert.equal(e.__xinxinTimetableQuery.status, 'pending'); assert.equal(e.__xinxinTimetableQuery.raw, undefined);
  resolve({ok:true,json:async()=>({kbList:[]})}); await flush();
  assert.equal(e.__xinxinTimetableQuery.status, 'complete');
  values.term.value = ''; assert.equal(run(e, 'query'), 'skip'); assert.equal(e.__xinxinTimetableQuery, null);
  e.__xinxinTimetableQuery = old; e.location = new URL('https://jwxt.sit.edu.cn/jwglxt/home');
  assert.equal(run(e, 'query'), 'skip'); assert.equal(e.__xinxinTimetableQuery, null);
});
test('query reports fetch rejection, HTTP failures, and non-kbList bodies as failed', async () => {
  for (const response of [null, {ok:false,status:403}, {ok:true,json:async()=>({error:'login'})}, {ok:true,json:async()=>({kbList:{}})}]) {
    const e = env(undefined, doc({year:{value:'2026'},term:{value:'3'}})); run(e);
    e.fetch = response ? async()=>response : async()=>{throw Error('offline');};
    assert.equal(run(e, 'query'), 'started'); await flush(); assert.equal(e.__xinxinTimetableQuery.status, 'failed'); assert.equal(e.__xinxinTimetableV1.raw.length, 0);
  }
});
test('weekly page driver iterates weeks, merges courses, and reports failed weeks', () => {
  const physics = {'data-kcmc': '大学物理A2', 'data-jsxm': '戴凯', 'data-cdmc': '二教F206', 'data-xqj': '2', 'data-jcor': '1-2', 'data-zcd': '1-14周'};
  const design = {'data-kcmc': '[自修]三维造型设计NX', 'data-jsxm': '杨磊', 'data-cdmc': '一科B405（公共机房）', 'data-xqj': '1', 'data-jcor': '1-2', 'data-zcd': '1-8周'};
  const {doc: d, state} = weeklyDoc({weeks: [1, 2, 3], termStart: '2026-09-14'});
  const byWeek = {1: [lessonNode(physics), lessonNode(design)], 2: [lessonNode(physics)], 3: [lessonNode(physics)]};
  const failedWeek = 3;
  const e = env('https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html', d);
  run(e);
  e.searchResult = function () {
    const week = Number(state.zs.value);
    if (week === failedWeek) return; // 模拟学校未返回该周课表
    state.table.innerHTML = 'week-' + week;
    state.lessons = byWeek[week] || [];
  };
  assert.equal(run(e, 'query'), 'started');
  flushTimers(e);
  assert.equal(e.__xinxinTimetableQuery.status, 'complete');
  assert.equal(e.__xinxinTimetableQuery.lessons.length, 2, '跨周重复课程合并');
  assert.equal(JSON.stringify(e.__xinxinTimetableQuery.failedWeeks), JSON.stringify([failedWeek]));
  assert.equal(e.__xinxinTimetableQuery.termStart, '2026-09-14');
  assert.equal(e.__xinxinTimetableQuery.termWeeks, 3);
  assert.equal(e.calls.length, 0, '不调用未观察到的查询接口');
  const result = JSON.parse(run(e));
  assert.equal(result.lessons.length, 2, '提取结果包含周次汇总课程');
  assert.equal(result.termStart, '2026-09-14');
  assert.equal(result.termWeeks, 3);
  assert.equal(JSON.stringify(result.failedWeeks), JSON.stringify([failedWeek]));
});
test('weekly page driver stops when every week fails and never touches endpoints', () => {
  const {doc: d} = weeklyDoc({weeks: [1, 2]});
  const e = env('https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html', d);
  run(e);
  e.searchResult = function () {};
  assert.equal(run(e, 'query'), 'started');
  flushTimers(e);
  assert.equal(e.__xinxinTimetableQuery.status, 'failed');
  assert.equal(e.__xinxinTimetableQuery.lessons.length, 0);
  assert.equal(e.calls.length, 0);
});
(async () => { let failures = 0; for (const {name, fn} of cases) { try { await fn(); console.log('PASS ' + name); } catch (error) { failures++; console.error('FAIL ' + name + '\n' + error.stack); }} console.log(`${cases.length - failures}/${cases.length} tests passed`); process.exitCode = failures ? 1 : 0; })();
