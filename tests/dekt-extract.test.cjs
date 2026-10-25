'use strict';
// 第二课堂学分脚本（dekt-extract.js）在合成页面上的取数行为。
// 真实页面结构未能联网核对，这里是按截图与通用列表结构造的合成页。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/dekt-extract.js'), 'utf8');

function element(tag, text, kids, left) {
  const node = {
    tagName: tag.toUpperCase(),
    children: kids || [],
    hidden: false,
    parentElement: null,
    _text: text || '',
    _left: left || 0,
    get textContent() {
      if (!this.children.length) return this._text;
      return this._text + this.children.map(kid => kid.textContent).join('');
    },
    contains(other) {
      for (let n = other; n; n = n.parentElement) if (n === this) return true;
      return false;
    },
    getBoundingClientRect() { return {left: this._left}; }
  };
  node.children.forEach(kid => { kid.parentElement = node; });
  return node;
}
function walk(node, out) {
  for (const kid of node.children) { out.push(kid); walk(kid, out); }
  return out;
}
function env(options) {
  const queue = [];
  const host = options.host || 'xg.sit.edu.cn';
  const body = element('body', '', options.nodes || []);
  body.getElementsByTagName = () => walk(body, []);
  const ctx = {
    location: {protocol: 'https:', hostname: host, href: 'https://' + host + '/dektxf/xfqktj?redirect=%2F'},
    document: {
      body,
      getElementsByTagName(tag) { return tag === 'iframe' ? (options.frames || []) : []; },
      querySelector(selector) { return options.login && selector === 'input[type=password]' ? {} : null; },
      querySelectorAll() { return []; }
    },
    getComputedStyle() { return {display: 'block', visibility: 'visible'}; },
    setTimeout(fn) { queue.push(fn); }
  };
  ctx.window = ctx;
  ctx.__queue = queue;
  vm.createContext(ctx);
  return ctx;
}
function frame(nodes) {
  const body = element('body', '', nodes);
  body.getElementsByTagName = () => walk(body, []);
  return {contentDocument: {body}};
}
function run(ctx) {
  vm.runInContext(script, ctx);
  for (let i = 0; i < 30 && ctx.__queue.length; i++) ctx.__queue.shift()();
  return ctx.__xinxinDekt;
}
function row(name, value, left) {
  return element('div', '', [element('span', name, [], left), element('span', value, [], 360)], left);
}
function page(head, list) {
  return [
    element('div', head, [], 0),
    element('div', '更新于 25/10/2026 19:29', [], 0),
    element('ul', '', list, 16)
  ];
}

const full = page('累计获得 3.65 / 8.00 学分', [
  element('li', '', [
    row('德育', '2.40 / 3.00', 16),
    element('ul', '', [
      element('li', '', [row('讲座报告、主题教育', '1.20 / 1.80', 44)], 44),
      element('li', '', [row('安全教育', '0.20 / 0.20', 44)], 44),
      element('li', '', [row('志愿公益 ›', '1.00 / 1.00', 44)], 44)
    ], 20)
  ], 16),
  element('li', '', [
    row('劳育', '0.45 / 3.00', 16),
    element('ul', '', [
      element('li', '', [row('三创活动', '0.25 / 1.50 ~ 3.00', 44)], 44)
    ], 20)
  ], 16)
]);

const state = run(env({nodes: full}));
assert.equal(state.status, 'complete');
assert.equal(state.earned, '3.65');
assert.equal(state.required, '8.00');
assert.equal(state.updated, '25/10/2026 19:29');
assert.equal(state.rows.length, 6);
assert.equal(state.rows[0].name, '德育');
assert.equal(state.rows[0].earned, '2.40');
assert.equal(state.rows[0].required, '3.00');
assert.equal(state.rows[0].depth, 0);
assert.equal(state.rows[1].name, '讲座报告、主题教育');
assert.equal(state.rows[1].depth, 1);
assert.equal(state.rows[3].name, '志愿公益', '去掉展开箭头再取名称');
assert.equal(state.rows[4].name, '劳育');
assert.equal(state.rows[4].depth, 0);
assert.equal(state.rows[5].required, '1.50~3.00', '区间需求保留区间的两个数');
assert.equal(state.rows[5].depth, 1);

// 「名称 ： 分数」逐行排列的页面：大类写成「德育合计」，页尾「学分 ： 3.75」，没有分母。
const lines = run(env({nodes: [
  element('div', '年级 ： 2025', [], 0),
  element('div', '第二课堂得分 ： 4.10', [], 0),
  element('div', '讲座报告、主题教育 ： 1.20', [], 0),
  element('div', '安全教育 ： 0.20', [], 0),
  element('div', '志愿公益 ： 1.0', [], 0),
  element('div', '德育合计 ： 2.40', [], 0),
  element('div', '三创活动 ： 0.35', [], 0),
  element('div', '劳育合计 ： 0.55', [], 0),
  element('div', '美育合计 ： 0.30', [], 0),
  element('div', '健康教育合计 ： 0.50', [], 0),
  element('div', '学分 ： 3.75', [], 0)
]}));
assert.equal(lines.status, 'complete');
assert.equal(lines.earned, '3.75');
assert.equal(lines.required, '', '这种页面没有分母');
assert.equal(lines.rows.length, 8);
assert.equal(lines.rows[0].name, '德育');
assert.equal(lines.rows[0].earned, '2.40');
assert.equal(lines.rows[0].depth, 0);
assert.equal(lines.rows[1].name, '讲座报告、主题教育');
assert.equal(lines.rows[1].depth, 1, '「合计」前面几行是它的小项');
assert.equal(lines.rows[4].name, '劳育');
assert.equal(lines.rows[5].name, '三创活动');
assert.equal(lines.rows[6].name, '美育');
assert.equal(lines.rows[7].name, '健康教育');
assert.ok(lines.rows.every(item => item.name !== '学分'), '总学分不当成明细行');

// 学分页整块放在同源 iframe 里时也要读到（外层只留导航）。
const framed = run(env({nodes: [element('div', '操作手册 我的服务', [], 0)], frames: [frame([
  element('div', '德育合计 ： 2.40', [], 0),
  element('div', '学分 ： 3.75', [], 0)
])]}));
assert.equal(framed.status, 'complete');
assert.equal(framed.earned, '3.75');
assert.equal(framed.rows[0].name, '德育');

const login = run(env({nodes: full, login: true}));
assert.equal(login.status, 'failed');
assert.equal(login.reason, 'login', '登录页先提示登录');

const empty = run(env({nodes: [element('div', '暂无数据', [], 0)]}));
assert.equal(empty.status, 'failed');
assert.equal(empty.reason, 'empty', '没有明细时给出原因');

const away = run(env({nodes: full, host: 'example.com'}));
assert.equal(away.status, 'failed');
assert.equal(away.reason, 'blocked', '只在学校域名里读取');

const twice = run(env({nodes: page('累计获得 2.40 / 3.00 学分', [
  element('li', '', [row('德育', '2.40 / 3.00', 16), row('德育', '2.40 / 3.00', 16)], 16)
])}));
assert.equal(twice.rows.length, 1, '同名同分的重复行只算一次');
assert.equal(twice.rows[0].depth, 0);

const flat = run(env({nodes: page('', [
  element('li', '', [element('span', '德育', [], 16), element('span', '2.40 / 3.00', [], 360)], 16)
])}));
assert.equal(flat.status, 'complete');
assert.equal(flat.earned, '', '没有「累计获得」时留空，交给应用求和');
assert.equal(flat.rows[0].depth, 0);
for (const protocol of ['http:', 'file:']) {
  const context = env({nodes: full});
  context.location.protocol = protocol;
  assert.equal(run(context).reason, 'blocked', '只在 HTTPS 学校页面读取');
}
const hiddenParent = element('section', '', [row('SYNTHETIC_HIDDEN', '8.00 / 9.00', 16)], 16);
hiddenParent.hidden = true;
const hidden = run(env({nodes: [hiddenParent, row('SYNTHETIC_VISIBLE', '1.00 / 2.00', 16)]}));
assert.equal(hidden.rows.length, 1, '隐藏容器里的旧数据不能读取');
assert.equal(hidden.rows[0].name, 'SYNTHETIC_VISIBLE');
console.log('dekt-extract.test.cjs passed');
