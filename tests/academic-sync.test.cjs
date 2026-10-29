'use strict';
// 0.6.5：成绩/考试查询脚本（academic-api.js）在已登录教务页面里的取数行为。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/academic-api.js'), 'utf8');
const HOME = 'https://jwxt.sit.edu.cn/jwglxt/xtgl/index_initMenu.html';
const GRADES = 'https://jwxt.sit.edu.cn/jwglxt/cjcx/cjcx_cxXsgrcj.html';
const EXAMS = 'https://jwxt.sit.edu.cn/jwglxt/kwgl/kscx_cxXsksxxIndex.html';
const cases = [];
const test = (name, fn) => cases.push({name, fn});
const flush = () => new Promise(resolve => setImmediate(resolve));

function env(url = HOME, options = {}) {
  const elements = options.form || {}, calls = [];
  const d = {
    title: '教务系统',
    querySelector(selector) {
      const match = /#(\w+)/.exec(selector);
      return (match ? elements[match[1]] : null) || null;
    },
    querySelectorAll() { return []; }
  };
  Object.defineProperty(d, 'cookie', {get() { throw Error('脚本不得读取 Cookie'); }});
  class FakeDate extends Date {
    constructor(...args) { super(args.length ? args[0] : '2026-10-19T09:00:00+08:00'); }
  }
  const e = {URL, location: new URL(url), document: d, Date: FakeDate, calls};
  e.window = e;
  e.fetch = async (target, init) => {
    calls.push({target: String(target), body: init && init.body, headers: init && init.headers, credentials: init && init.credentials});
    const answer = options.body ? options.body(calls.length, init) : {items: [], totalResult: '0'};
    if (answer === 'html') return {ok: true, status: 200, text: async () => '<html>登录</html>'};
    if (answer === 'http') return {ok: false, status: 500, text: async () => ''};
    return {ok: true, status: 200, text: async () => JSON.stringify(answer)};
  };
  return vm.createContext(e);
}
function run(e, plan) {
  const source = script.replace("typeof __ACADEMIC_PLAN__ === 'string' ? __ACADEMIC_PLAN__ : ''", () => JSON.stringify(JSON.stringify(plan)));
  return vm.runInContext(source, e);
}
async function settle(e, max = 40) {
  for (let i = 0; i < max; i++) {
    await flush();
    const state = e.__xinxinAcademic;
    if (state && state.status !== 'pending') return state;
  }
  throw new Error('取数没有收敛：' + JSON.stringify(e.__xinxinAcademic));
}

test('all-term grades clear both term constraints and retain every returned term', async () => {
  const e = env(HOME, {form: {xnm: {value: '2026'}, xqm: {value: '3'}}, body: () => ({items: [
    {kcmc: 'SYNTHETIC_OLD', xnm: '2024', xqm: '12', xf: '4', jd: '4'},
    {kcmc: 'SYNTHETIC_NEW', xnm: '2026', xqm: '3', xf: '2', jd: '2'}
  ], totalResult: 2})});
  run(e, {service: 'grades', endpoint: GRADES, allTerms: true});
  const state = await settle(e), params = new URLSearchParams(e.calls[0].body);
  assert.equal(state.allTerms, true);
  assert.equal(params.get('xnm'), ''); assert.equal(params.get('xqm'), '');
  assert.equal(state.items.length, 2);
});

test('all-term mode never removes the exam term and rejects truncated grade totals', async () => {
  const exams = env(); run(exams, {service: 'exams', endpoint: EXAMS, allTerms: true});
  assert.equal((await settle(exams)).allTerms, false);
  assert.equal(new URLSearchParams(exams.calls[0].body).get('xnm'), '2026');
  const large = env(HOME, {body: () => ({items: [{kcmc: 'SYNTHETIC_ONLY'}], totalResult: 601})});
  run(large, {service: 'grades', endpoint: GRADES, allTerms: true});
  assert.equal((await settle(large)).status, 'failed');
});

test('observed grade rows carry identity and actual page size, without xsxx', async () => {
  const rows = Array.from({length: 26}, (_, i) => ({kcmc: 'SYNTHETIC_' + i, xh: 'SYNTHETIC_ACCOUNT', xm: 'SYNTHETIC_NAME'}));
  const e = env(HOME, {body: n => ({items: rows.slice((n - 1) * 15, n * 15), totalResult: 26, showCount: 15})});
  run(e, {service: 'grades', endpoint: GRADES, allTerms: true});
  const state = await settle(e);
  assert.equal(state.status, 'complete'); assert.equal(e.calls.length, 2);
  assert.equal(state.items.length, 26); assert.equal(state.student.number, 'SYNTHETIC_ACCOUNT');
});

test('mixed account grade rows are never completed', async () => {
  const e = env(HOME, {body: () => ({items: [{xh: 'SYNTHETIC_A'}, {xh: 'SYNTHETIC_B'}], totalResult: 2})});
  run(e, {service: 'grades', endpoint: GRADES, allTerms: true});
  assert.equal((await settle(e)).status, 'failed');
});

test('reads the grade list through the school query endpoint', async () => {
  const e = env(HOME, {body: () => ({items: [
    {kcmc: '高等数学', cj: '92', xf: '4.0', jd: '4.0', kcxzmc: '必修', jsxm: '张三'},
    {kcmc: '大学英语', cj: '85', xf: '2.0', jd: '3.3', kcxzmc: '必修'}
  ], totalResult: '2'})});
  assert.equal(run(e, {service: 'grades', endpoint: GRADES, gnmkdm: 'N305005'}), 'started');
  const state = await settle(e);
  assert.equal(state.status, 'complete');
  assert.equal(state.items.length, 2);
  assert.equal(state.items[0].kcmc, '高等数学');
  assert.deepEqual(Array.from(state.term ? [state.term.xnm, state.term.xqm] : []), ['2026', '3']);
  assert.equal(e.calls.length, 1);
  assert.match(e.calls[0].target, /cjcx_cxXsgrcj\.html\?doType=query&gnmkdm=N305005$/);
  assert.match(e.calls[0].body, /xnm=2026&xqm=3/);
  assert.match(e.calls[0].body, /queryModel\.showCount=100/);
  assert.equal(e.calls[0].credentials, 'same-origin');
  assert.equal(e.calls[0].headers['X-Requested-With'], 'XMLHttpRequest');
});

test('does not add exam-only filters to grade queries', async () => {
  const e = env(HOME, {body: () => ({items: [{kcmc: '线性代数', cj: '78'}], totalResult: '1'})});
  run(e, {service: 'grades', endpoint: GRADES, gnmkdm: 'N305005'});
  await settle(e);
  assert.doesNotMatch(e.calls[0].body, /ksmcdmb_id/);
});

test('sends the exam filters and keeps the exam rows', async () => {
  const e = env(HOME, {body: () => ({items: [
    {kcmc: '大学物理', ksmc: '期末考试', kssj: '2026-01-06 09:00-11:00', cdmc: '奉贤一教A101', zwh: '12'}
  ], totalResult: '1'})});
  run(e, {service: 'exams', endpoint: EXAMS, gnmkdm: 'N358105'});
  const state = await settle(e);
  assert.equal(state.status, 'complete');
  assert.equal(state.service, 'exams');
  assert.match(e.calls[0].body, /ksmcdmb_id=&kch=&kcbmc=&ksrq=/);
  assert.match(e.calls[0].target, /kwgl\/kscx_cxXsksxxIndex\.html\?doType=query&gnmkdm=N358105$/);
  assert.equal(state.items[0].kssj, '2026-01-06 09:00-11:00');
});

test('follows the school paging when a term has more than one page', async () => {
  const e = env(HOME, {body: page => page === 1
    ? {items: Array.from({length: 100}, (_, i) => ({kcmc: '课程' + i, cj: '80'})), totalResult: '150'}
    : {items: Array.from({length: 50}, (_, i) => ({kcmc: '课程' + (100 + i), cj: '81'})), totalResult: '150'}});
  run(e, {service: 'grades', endpoint: GRADES, gnmkdm: 'N305005'});
  const state = await settle(e, 60);
  assert.equal(state.status, 'complete');
  assert.equal(state.items.length, 150);
  assert.equal(e.calls.length, 2);
  assert.match(e.calls[1].body, /queryModel\.currentPage=2/);
});

test('a school login page means the session expired, not an empty term', async () => {
  const e = env(HOME, {body: () => 'html'});
  run(e, {service: 'grades', endpoint: GRADES, gnmkdm: 'N305005'});
  const state = await settle(e);
  assert.equal(state.status, 'failed');
  assert.equal(state.reason, 'login');
});

test('a term without rows is complete, not a failure', async () => {
  const e = env(HOME, {body: () => ({items: [], totalResult: '0'})});
  run(e, {service: 'grades', endpoint: GRADES, gnmkdm: 'N305005'});
  const state = await settle(e);
  assert.equal(state.status, 'complete');
  assert.equal(state.items.length, 0);
  assert.equal(state.reason, 'empty');
});

test('uses the page year and term when the school page exposes them', async () => {
  const e = env(GRADES + '?gnmkdm=N305005', {form: {xnm: {value: '2025'}, xqm: {value: '12'}}, body: () => ({items: [{kcmc: '数据结构', cj: '90'}], totalResult: '1'})});
  run(e, {service: 'grades', endpoint: GRADES, gnmkdm: 'N305005'});
  const state = await settle(e);
  assert.equal(state.term.xnm, '2025');
  assert.equal(state.term.xqm, '12');
  assert.match(e.calls[0].body, /xnm=2025&xqm=12/);
});

test('uses the term recorded in the timetable when it is still recent', async () => {
  const e = env(HOME, {body: () => ({items: [{kcmc: '操作系统', cj: '88'}], totalResult: '1'})});
  run(e, {service: 'grades', endpoint: GRADES, gnmkdm: 'N305005', term: {xnm: '2025', xqm: '12'}});
  const state = await settle(e);
  assert.equal(state.term.xnm, '2025');
  assert.equal(state.term.xqm, '12');
  assert.match(e.calls[0].body, /xnm=2025&xqm=12/);
});

test('ignores a timetable term that is more than a year old', async () => {
  const e = env(HOME, {body: () => ({items: [{kcmc: '操作系统', cj: '88'}], totalResult: '1'})});
  run(e, {service: 'grades', endpoint: GRADES, gnmkdm: 'N305005', term: {xnm: '2023', xqm: '3'}});
  const state = await settle(e);
  assert.equal(state.term.xnm, '2026');
  assert.equal(state.term.xqm, '3');
});

test('refuses endpoints outside the two academic modules', async () => {  const e = env(HOME);
  run(e, {service: 'grades', endpoint: 'https://jwxt.sit.edu.cn/jwglxt/xtgl/index_initMenu.html', gnmkdm: 'N305005'});
  const state = await settle(e);
  assert.equal(state.status, 'failed');
  assert.equal(state.reason, 'endpoint');
  assert.equal(e.calls.length, 0);
});

test('refuses a cross-origin endpoint', async () => {
  const e = env(HOME);
  run(e, {service: 'grades', endpoint: 'https://example.com/jwglxt/cjcx/cjcx_cxXsgrcj.html', gnmkdm: 'N305005'});
  const state = await settle(e);
  assert.equal(state.status, 'failed');
  assert.equal(state.reason, 'blocked');
  assert.equal(e.calls.length, 0);
});

test('stops when the page is not on the school academic host', async () => {
  const e = env('https://example.com/jwglxt/cjcx/cjcx_cxXsgrcj.html');
  run(e, {service: 'grades', endpoint: GRADES, gnmkdm: 'N305005'});
  const state = await settle(e);
  assert.equal(state.status, 'failed');
  assert.equal(state.reason, 'blocked');
  assert.equal(e.calls.length, 0);
});

test('reports a server error instead of inventing rows', async () => {
  const e = env(HOME, {body: () => 'http'});
  run(e, {service: 'exams', endpoint: EXAMS, gnmkdm: 'N358105'});
  const state = await settle(e);
  assert.equal(state.status, 'failed');
  assert.equal(state.reason, 'http');
});

(async () => {
  let failed = 0;
  for (const item of cases) {
    try { await item.fn(); console.log('PASS ' + item.name); }
    catch (error) { failed++; console.log('FAIL ' + item.name + '\n  ' + (error && error.message)); }
  }
  console.log('\n' + (cases.length - failed) + '/' + cases.length + ' passed');
  process.exit(failed ? 1 : 0);
})();
