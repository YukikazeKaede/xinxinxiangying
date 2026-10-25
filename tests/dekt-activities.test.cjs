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
  const calls = [];
  class XHR {
    open(method, target) { this.method = method; this.target = target; this.headers = {}; }
    setRequestHeader(key, value) { this.headers[key] = value; }
    send(body) {
      calls.push({method: this.method, target: this.target, body: body, headers: this.headers});
      const answer = routes(this.method, this.target);
      if (answer === null || answer === undefined) { this.status = 404; this.responseText = ''; return; }
      this.status = 200;
      this.responseText = JSON.stringify(answer);
    }
  }
  const form = options && options.login ? {value: 'x'} : null;
  const d = {
    title: '活动报名-上海应用技术大学',
    querySelector(selector) { return selector === 'input[type=password]' ? form : null; }
  };
  const e = {
    XMLHttpRequest: XHR, JSON, Date, Math, String, Number,
    location: new URL(url), document: d, calls
  };
  e.window = e;
  return vm.createContext(e);
}

function run(e, plan) {
  // 注释里也提到 __ACT_PLAN__，和 Java 的 String.replace 一样整段替换。
  const source = script.replaceAll('__ACT_PLAN__', () => JSON.stringify(JSON.stringify(plan)));
  return vm.runInContext(source, e);
}

function listAnswer() {
  return {
    msg: '操作成功', code: 0, data: {
      menuList: [{lbdm: '0101', lbmc: '讲座报告'}, {lbdm: '0104', lbmc: '志愿公益'}],
      resultList: [{
        dlmc: '德育', dldm: '01', lblist: [{
          lbmc: '志愿公益', lbdm: '0104', hdlist: [
            {id: 'A1', hdmc: '图书馆志愿服务', lbdm: '0104', lbmc: '志愿公益', dlmc: '德育', dldm: '01',
              zbfmc: '团委', zbf: '110700', xmmc: '天天讲', ssxm: '29A24BDD',
              hdkssj: '2026-11-01 13:00:00', hdjssj: '2026-11-01 16:00:00',
              hdbmkssj: '2026-10-20 09:00:00', hdbmjzsj: '2026-10-31 18:00:00', zdzt: '0'},
            {id: 'A2', hdmc: '旧活动', lbdm: '0101', lbmc: '讲座报告', dlmc: '德育', dldm: '01',
              zbfmc: '团委', hdkssj: '2025-03-01 10:00:00', hdjssj: '2025-03-01 12:00:00', zdzt: '1'}
          ]
        }]
      }]
    }
  };
}

test('reads the activity list with the school query endpoint', () => {
  const e = env(LIST, () => listAnswer());
  assert.equal(run(e, {action: 'list'}), 'started');
  const state = e.__xinxinActs;
  assert.equal(state.status, 'complete');
  assert.equal(state.list.menus.length, 2);
  assert.equal(state.list.menus[0].code, '0101');
  assert.equal(state.list.menus[0].name, '讲座报告');
  assert.equal(state.list.groups.length, 1);
  assert.equal(state.list.groups[0].code, '01');
  const items = state.list.groups[0].items;
  assert.equal(items.length, 2);
  assert.equal(items[0].id, 'A1');
  assert.equal(items[0].dldm, '01');
  assert.equal(items[0].name, '图书馆志愿服务');
  assert.equal(items[0].dlmc, '德育');
  assert.equal(items[0].lbmc, '志愿公益');
  assert.equal(items[0].zbf, '团委');
  assert.equal(items[0].project, '天天讲');
  assert.equal(items[0].start, '2026-11-01 13:00:00');
  assert.equal(items[0].signupEnd, '2026-10-31 18:00:00');
  assert.equal(items[0].top, false);
  assert.equal(items[1].top, true);
  assert.equal(e.calls.length, 1);
  assert.match(e.calls[0].target, /^\/zftal-xgxt-web\/hdgl\/getHdgcHdList\.zf$/);
  assert.equal(e.calls[0].method, 'POST');
  assert.match(e.calls[0].body, /showCount=300/);
  assert.equal(e.calls[0].headers['Content-Type'], 'application/x-www-form-urlencoded; charset=UTF-8');
  assert.equal(e.calls[0].headers['X-Requested-With'], 'XMLHttpRequest');
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
  for (const data of [undefined, {}, {resultList: null}]) {
    const e = env(LIST, () => ({code: 0, data}));
    run(e, {action: 'list'});
    assert.equal(e.__xinxinActs.status, 'failed');
    assert.equal(e.__xinxinActs.list, null);
  }
});

test('a genuine empty activity list remains a successful result', () => {
  const e = env(LIST, () => ({code: 0, data: {resultList: []}}));
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
