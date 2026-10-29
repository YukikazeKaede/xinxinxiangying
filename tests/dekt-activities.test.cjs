'use strict';
// 第二课堂活动脚本（dekt-activities.js）在学校页面里的取数行为。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/dekt-activities.js'), 'utf8');
const LIST = 'https://xg.sit.edu.cn/hdgl/hdydlist?redirect=%2F';
const cases = [];
const test = (name, fn) => cases.push({name, fn});

function env(url, routes, options) {
  const calls = [], pending = [];
  class XHR {
    open(method, target, async) { this.method = method; this.target = target; this.async = async; this.headers = {}; }
    abort() { this.aborted = true; }
    setRequestHeader(key, value) { this.headers[key] = value; }
    send(body) {
      calls.push({method: this.method, target: this.target, body: body, headers: this.headers});
      const answer = this.target.endsWith('/gjcx/newList.zf') && !(options && options.customMetadata)
        ? metadata() : routes(this.method, this.target, body);
      const deliver = () => {
        if (this.aborted) return;
        if (answer && answer.event) { if (this['on' + answer.event]) this['on' + answer.event](); return; }
        this.status = answer && answer.httpStatus || (answer == null ? 404 : 200);
        this.responseText = JSON.stringify(answer);
        if (this.async && this.onload) this.onload();
      };
      if (this.async && options && options.defer) pending.push(deliver); else deliver();
    }
  }
  const form = options && options.login ? {value: 'x'} : null;
  const d = {
    title: '活动报名-上海应用技术大学',
    querySelector(selector) { return selector === 'input[type=password]' ? form : null; }
  };
  const e = {
    XMLHttpRequest: XHR, JSON, Date, Math, String, Number,
    location: new URL(url), document: d, calls, pending, flush() { while (pending.length) pending.shift()(); }
  };
  e.window = e;
  return vm.createContext(e);
}

function run(e, plan) {
  // 注释里也提到 __ACT_PLAN__，和 Java 的 String.replace 一样整段替换。
  const source = script.replaceAll('__ACT_PLAN__', () => JSON.stringify(JSON.stringify(plan)));
  return vm.runInContext(source, e);
}

function metadata() {
  return {code: 0, data: {conditionTypeList: [
    {value: 'xn', defaultValue: ['2026-2027'], typeOption: {options: [
      {value: '2026-2027', label: '2026-2027'}, {value: '2025-2026', label: '2025-2026'}]}},
    {value: 'xq', defaultValue: ['SYNTHETIC_TERM_A'], typeOption: {options: [
      {value: 'SYNTHETIC_TERM_A', label: '第一学期'}, {value: 'SYNTHETIC_TERM_B', label: '第二学期'}]}},
    {value: 'hdbmzt', typeOption: {options: [
      {value: 'SYNTHETIC_OPEN', label: '报名中'}, {value: 'SYNTHETIC_FULL', label: '报名已满'},
      {value: 'SYNTHETIC_CONFIG', label: '流程设置中'}]}}
  ]}};
}
function hit(id, extra) {
  return {id, hdmc: 'SYNTHETIC_ACTIVITY_' + id, lbdm: '0104', lbmc: '志愿公益', dlmc: '德育', dldm: '01',
    zbfmc: 'SYNTHETIC_HOST', ssxm: 'SYNTHETIC_PROJECT', hdkssj: '2026-11-01 13:00:00',
    hdjssj: '2026-11-01 16:00:00', hdbmzt: 'SYNTHETIC_OPEN', zdzt: '0', ...extra};
}
function listAnswer(items = [hit('A1'), hit('A2')], page = 1, total = items.length, size = 10) {
  return {code: 0, data: {items, currentPage: page, showCount: size, totalResult: total,
    totalPage: Math.ceil(total / size), pageSize: 15, limit: 15, pageNo: 0, totalCount: 0}};
}

test('reads the full endpoint with official defaults and metadata state names', () => {
  const e = env(LIST, () => listAnswer());
  assert.equal(run(e, {action: 'list'}), 'started');
  const state = e.__xinxinActs;
  assert.equal(state.status, 'complete');
  assert.equal(state.list.source, 'school-list');
  assert.equal(state.list.groups[0].items.length, 2);
  assert.equal(state.list.groups[0].items[0].schoolState, '报名中');
  assert.equal(state.list.groups[0].items[0].year, '2026-2027');
  assert.equal(state.list.scope.xq, 'SYNTHETIC_TERM_A');
  assert.equal(state.list.filters.xq[0].name, '第一学期');
  assert.equal(e.calls.length, 2);
  assert.equal(e.calls[0].target, '/zftal-xgxt-web/gjcx/newList.zf');
  assert.deepEqual(JSON.parse(e.calls[0].body), {ymdz: 'hdgl/hdlb'});
  assert.equal(e.calls[1].target, '/zftal-xgxt-web/hdgl/list.zf');
  const form = new URLSearchParams(e.calls[1].body);
  assert.deepEqual(JSON.parse(form.get('selectType')), {xn: ['2026-2027'], xq: ['SYNTHETIC_TERM_A']});
  assert.equal(form.get('all'), '1');
  assert.equal(form.get('currentPage'), '1');
  assert.equal(e.calls[1].headers['X-Requested-With'], 'XMLHttpRequest');
  assert.ok(e.calls.every(c => !c.target.includes('/apply/') && !c.target.includes('getHdgcHdList')));
});

test('collects all pages using actual page fields, preserving order across blocks', () => {
  const e = env(LIST, (m, url, body) => {
    const page = Number(new URLSearchParams(body).get('currentPage'));
    return page === 1 ? listAnswer([hit('L', {dlmc: '劳育'}), hit('D')], 1, 3, 2)
      : listAnswer([hit('OLD', {hdjssj: '2000-01-01', zdzt: '1'})], 2, 3, 2);
  });
  run(e, {action: 'list'});
  assert.equal(e.__xinxinActs.status, 'complete');
  assert.deepEqual(Array.from(e.__xinxinActs.list.groups[0].items, x => x.id), ['L', 'D', 'OLD']);
  assert.equal(new URLSearchParams(e.calls[2].body).get('showCount'), '2');
  assert.equal(e.__xinxinActs.pages, 2);
});

test('deduplicates ids without grouping or reordering rows', () => {
  const e = env(LIST, () => listAnswer([hit('A'), hit('A'), hit('B')]));
  run(e, {action: 'list'});
  assert.deepEqual(Array.from(e.__xinxinActs.list.groups[0].items, x => x.id), ['A', 'B']);
});

test('a failed later page cannot publish a partial list', () => {
  for (const later of [null, {httpStatus: 401}, {event: 'timeout'}, listAnswer([hit('B')], 1, 2, 1), listAnswer([hit('B')], 2, 3, 1)]) {
    let n = 0;
    const e = env(LIST, () => ++n === 1 ? listAnswer([hit('A')], 1, 2, 1) : later);
    run(e, {action: 'list'});
    assert.equal(e.__xinxinActs.status, 'failed');
    assert.equal(e.__xinxinActs.list, null);
    assert.equal(e.calls.length, 3);
  }
});

test('unsupported state codes stay unknown and do not infer from dates', () => {
  const e = env(LIST, () => listAnswer([hit('A', {hdbmzt: 'UNKNOWN', hdjssj: '2000-01-01'})]));
  run(e, {action: 'list'});
  assert.equal(e.__xinxinActs.list.groups[0].items[0].schoolState, '状态待核实');
});

test('explicit all and selected academic scope use server codes, not date inference', () => {
  for (const filters of [{xn: '', xq: ''}, {xn: '2025-2026', xq: 'SYNTHETIC_TERM_B'}]) {
    const e = env(LIST, () => listAnswer([hit('A', {hdkssj: '2099-01-01'})]));
    run(e, {action: 'list', filters});
    assert.equal(e.__xinxinActs.status, 'complete');
    assert.equal(e.__xinxinActs.list.groups[0].items[0].year, filters.xn);
    const select = JSON.parse(new URLSearchParams(e.calls[1].body).get('selectType'));
    assert.deepEqual(select.xn || [], filters.xn ? [filters.xn] : []);
    assert.deepEqual(select.xq || [], filters.xq ? [filters.xq] : []);
  }
});

test('metadata failure and invalid requested codes do not query activity pages', () => {
  const e = env(LIST, () => null, {customMetadata: true});
  run(e, {action: 'list'});
  assert.equal(e.__xinxinActs.status, 'failed');
  assert.equal(e.calls.length, 1);
  const invalid = env(LIST, () => listAnswer());
  run(invalid, {action: 'list', filters: {xq: 'GUESSED_TERM'}});
  assert.equal(invalid.__xinxinActs.reason, 'filters');
  assert.equal(invalid.calls.length, 1);
});

test('cancel or reinject aborts pending reads without advancing pages', () => {
  const e = env(LIST, () => listAnswer(), {defer: true});
  run(e, {action: 'list'});
  assert.equal(e.__xinxinActs.status, 'pending');
  const old = e.__xinxinActs;
  old.cancel(); e.flush();
  assert.equal(old.status, 'cancelled');
  assert.equal(e.calls.length, 1);
  run(e, {action: 'list'}); const replaced = e.__xinxinActs;
  run(e, {action: 'list'}); e.flush();
  assert.equal(replaced.status, 'cancelled');
  assert.equal(e.__xinxinActs.status, 'complete');
});

test('unexpected pagination limits fail instead of truncating silently', () => {
  const e = env(LIST, () => listAnswer([hit('A')], 1, 2010));
  run(e, {action: 'list'});
  assert.equal(e.__xinxinActs.reason, 'limit');
  assert.equal(e.__xinxinActs.list, null);
});

test('reads one activity detail with the form fields merged in', () => {
  const routes = (method, target) => {
    if (target.indexOf('details.zf') >= 0) return {code: 0, data: {
      id: 'A1', hdmc: '图书馆志愿服务', xn: '2026-2027', xq: '第一学期',
      hdkssj: '2026-11-01 13:00:00', hdjssj: '2026-11-01 16:00:00', bm: 1, sfbm: 0,
      hdms: '活动名称：图书馆志愿服务&amp;#10;活动内容：整理图书&#10;注意事项：不要迟到'
    }};
    if (target.indexOf('xsck.zf') >= 0) return {code: 0, data: {groupFields: [{fields: [
      {fieldName: '活动地点', defaultValue: '奉贤图书馆'},
      {fieldName: '学分', defaultValue: '0.15'},
      {fieldName: '活动时长（分钟）', defaultValue: '180'},
      {fieldName: '负责人', defaultValue: 'SYNTHETIC_LEADER'},
      {fieldName: '负责人联系方式', defaultValue: 'SYNTHETIC_PHONE'},
      {fieldName: '主办方', defaultValue: '110700'},
      {fieldName: '所属项目', defaultValue: '29A24BDD27F82E1E3EB69E6656A29B1E'},
      {fieldName: '活动描述', defaultValue: '备用描述'}
    ]}]}};
    return null;
  };
  const e = env(LIST, routes);
  assert.equal(run(e, {action: 'detail', id: 'A1'}), 'started');
  const state = e.__xinxinActs;
  assert.equal(state.status, 'complete');
  const detail = state.detail;
  assert.equal(detail.name, '图书馆志愿服务');
  assert.equal(detail.year, '2026-2027');
  assert.equal(detail.term, '第一学期');
  assert.equal(detail.place, '奉贤图书馆');
  assert.equal(detail.credit, '0.15');
  assert.equal(detail.duration, '180');
  assert.equal(detail.leader, 'SYNTHETIC_LEADER');
  assert.equal(detail.phone, 'SYNTHETIC_PHONE');
  assert.equal(detail.host, '110700');
  assert.equal(detail.signed, true);
  assert.equal(detail.desc.indexOf('&#10;'), -1);
  assert.equal(detail.desc.indexOf('&amp;'), -1);
  assert.match(detail.desc, /活动内容：整理图书\n注意事项：不要迟到/);
  assert.equal(e.calls.length, 2);
  assert.match(e.calls[0].target, /\/hdgl\/details\.zf\?id=A1$/);
  assert.match(e.calls[1].target, /dataId=data%7Chdglsq%7CA1$/);
});

test('signs up through the school form and reports the school reason', () => {
  const routes = (method, target) => {
    if (target.indexOf('/hdsq/default.zf') >= 0) return {code: 0, data: {groupFields: [{fields: [
      {fieldCode: 'xh', defaultValue: 'SYNTHETIC_STUDENT'},
      {fieldCode: 'xm', defaultValue: 'SYNTHETIC_NAME'},
      {fieldCode: 'hdkssj|hdjssj', defaultValue: '2026-10-31 09:00:00,2026-10-31 12:00:00'},
      {fieldCode: 'hdid', defaultValue: 'A1'}
    ]}]}};
    if (target.indexOf('/flow/apply/hdsq.zf') >= 0) return {msg: '报名人数已满', code: -1};
    return null;
  };
  const e = env(LIST, routes);
  assert.equal(run(e, {action: 'signup', id: 'A1'}), 'started');
  const state = e.__xinxinActs;
  assert.equal(state.status, 'complete');
  assert.equal(state.signup.ok, false);
  assert.equal(state.signup.message, '报名人数已满');
  assert.equal(e.calls.length, 2);
  assert.match(e.calls[0].target, /\/dynamic\/form\/group\/hdsq\/default\.zf\?dataId=xsxx%7C%26hdsq%7CA1$/);
  assert.equal(e.calls[1].method, 'POST');
  assert.match(e.calls[1].target, /\/hdgl\/flow\/apply\/hdsq\.zf$/);
  assert.match(e.calls[1].body, /xh=SYNTHETIC_STUDENT/);
  assert.match(e.calls[1].body, /hdkssj=2026-10-31%2009%3A00%3A00/);
  assert.match(e.calls[1].body, /hdjssj=2026-10-31%2012%3A00%3A00/);
  // 表单里必须带活动 id，否则不要提交。
  assert.match(e.calls[1].body, /hdid=A1/);
});

test('signs up successfully and reports the school message', () => {
  const routes = (method, target) => {
    if (target.indexOf('/hdsq/default.zf') >= 0) return {code: 0, data: {groupFields: [{fields: [
      {fieldCode: 'hdid', defaultValue: 'A1'},
      {fieldCode: 'xm', defaultValue: 'SYNTHETIC_NAME'}
    ]}]}};
    if (target.indexOf('/flow/apply/hdsq.zf') >= 0) return {msg: '操作成功', code: 0};
    return null;
  };
  const e = env(LIST, routes);
  run(e, {action: 'signup', id: 'A1'});
  assert.equal(e.__xinxinActs.signup.ok, true);
  assert.equal(e.__xinxinActs.signup.message, '操作成功');
});

test('refuses to sign up when the form has no activity id', () => {
  const routes = (method, target) => {
    if (target.indexOf('/hdsq/default.zf') >= 0) return {code: 0, data: {groupFields: [{fields: [
      {fieldCode: 'xm', defaultValue: 'SYNTHETIC_NAME'}
    ]}]}};
    return {msg: '操作成功', code: 0};
  };
  const e = env(LIST, routes);
  run(e, {action: 'signup', id: 'A1'});
  assert.equal(e.__xinxinActs.status, 'failed');
  assert.equal(e.__xinxinActs.reason, 'detail');
  assert.equal(e.calls.length, 1);
});

test('reports a login page instead of querying', () => {
  const e = env(LIST, () => listAnswer(), {login: true});
  run(e, {action: 'list'});
  assert.equal(e.__xinxinActs.status, 'failed');
  assert.equal(e.__xinxinActs.reason, 'login');
  assert.equal(e.calls.length, 0);
});

test('ignores pages outside the school site', () => {
  const e = env('https://example.com/hdgl/hdydlist', () => listAnswer());
  run(e, {action: 'list'});
  assert.equal(e.__xinxinActs.status, 'failed');
  assert.equal(e.__xinxinActs.reason, 'blocked');
  assert.equal(e.calls.length, 0);
});

test('reports a failed list request', () => {
  const e = env(LIST, () => null);
  run(e, {action: 'list'});
  assert.equal(e.__xinxinActs.status, 'failed');
  assert.equal(e.__xinxinActs.reason, 'list');
});

test('a malformed success response is a failure rather than an empty cache', () => {
  for (const data of [undefined, {}, {items: null}]) {
    const e = env(LIST, () => ({code: 0, data}));
    run(e, {action: 'list'});
    assert.equal(e.__xinxinActs.status, 'failed');
    assert.equal(e.__xinxinActs.list, null);
  }
});

test('a genuine empty activity list remains a successful result', () => {
  const e = env(LIST, () => listAnswer([]));
  run(e, {action: 'list'});
  assert.equal(e.__xinxinActs.status, 'complete');
  assert.equal(e.__xinxinActs.list.groups.length, 0);
});

test('only calls the second-classroom HTTPS origin with the default port', () => {
  for (const url of ['http://xg.sit.edu.cn/hdgl/', 'https://authserver.sit.edu.cn/',
    'https://xg.sit.edu.cn:444/hdgl/', 'https://user@xg.sit.edu.cn/hdgl/']) {
    const e = env(url, () => listAnswer());
    run(e, {action: 'signup', id: 'A1'});
    assert.equal(e.__xinxinActs.reason, 'blocked');
    assert.equal(e.calls.length, 0);
  }
});

test('reinjection never repeats a signup POST, including an unknown outcome', () => {
  for (const answer of [{code: 0, msg: '操作成功'}, null]) {
    const e = env(LIST, (method, target) => target.includes('/default.zf')
      ? {code: 0, data: {groupFields: [{fields: [{fieldCode: 'hdid', defaultValue: 'A1'}]}]}}
      : answer);
    const plan = {action: 'signup', id: 'A1', requestId: 'SYNTHETIC_REQUEST'};
    run(e, plan);
    run(e, plan);
    assert.equal(e.calls.filter(c => c.target.includes('/flow/apply/')).length, 1);
    assert.equal(e.__xinxinActs.signup.ok, answer !== null);
  }
});

test('a mismatched activity id is never submitted', () => {
  const e = env(LIST, () => ({code: 0, data: {groupFields: [{fields: [
    {fieldCode: 'hdid', defaultValue: 'SYNTHETIC_OTHER'}
  ]}]}}));
  run(e, {action: 'signup', id: 'A1'});
  assert.equal(e.__xinxinActs.status, 'failed');
  assert.equal(e.calls.length, 1);
});

for (const item of cases) {
  item.fn();
  process.stdout.write('.');
}
process.stdout.write(`\ndekt-activities.test.cjs: ${cases.length} cases passed\n`);
