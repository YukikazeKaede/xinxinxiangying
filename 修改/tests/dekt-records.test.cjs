'use strict';
// 学分认定页脚本（dekt-records.js）在合成页面上的取数行为。
// 真实页面结构未能联网核对，这里按截图里的「标签：值」卡片结构造合成页。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/dekt-records.js'), 'utf8');

function element(tag, text, kids) {
  const node = {
    tagName: tag.toUpperCase(),
    children: kids || [],
    hidden: false,
    parentElement: null,
    _text: text || '',
    get textContent() {
      if (!this.children.length) return this._text;
      return this._text + this.children.map(kid => kid.textContent).join('');
    },
    contains(other) {
      for (let n = other; n; n = n.parentElement) if (n === this) return true;
      return false;
    },
    getBoundingClientRect() { return {left: 0}; }
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
  const body = element('body', '', options.nodes || []);
  body.getElementsByTagName = () => walk(body, []);
  const ctx = {
    location: {
      protocol: 'https:', hostname: options.host || 'xg.sit.edu.cn',
      pathname: options.path || '/apply-manage/xsrd', port: ''
    },
    document: {
      body,
      getElementsByTagName(tag) { return tag === 'iframe' ? (options.frames || []) : []; },
      querySelector(selector) { return options.login && selector === 'input[type=password]' ? {} : null; }
    },
    getComputedStyle() { return {display: 'block', visibility: 'visible'}; },
    setTimeout(fn) { queue.push(fn); }
  };
  ctx.window = ctx;
  ctx.__queue = queue;
  vm.createContext(ctx);
  return ctx;
}
function run(ctx) {
  vm.runInContext(script, ctx);
  for (let i = 0; i < 400 && ctx.__queue.length; i++) ctx.__queue.shift()();
  return ctx.__xinxinRecords;
}
function card(fields) {
  return element('div', Object.keys(fields).map(key => key + '：' + fields[key] + ' ').join(''));
}
function page(cards) {
  return [element('div', '', cards)];
}
const FIRST = {
  '项目名称': '三创活动', '学分大类': '劳育', '学分类别': '三创活动',
  '活动名称': '生涯导航：如何做一名合格的大学生', '学年': '2026-2027', '学期': '第一学期',
  '学分来源': '活动', '认定学分': '0.1', '创建时间': '2026-10-26 16:43:54'
};
const SECOND = {
  '项目名称': '志愿公益', '学分大类': '德育', '学分类别': '志愿公益',
  '活动名称': '海湾为老活动志愿者', '学年': '2026-2027', '学期': '第一学期',
  '学分来源': '活动', '认定学分': '1', '创建时间': '2026-10-17 09:00:00'
};

const basic = run(env({nodes: page([card(FIRST), card(SECOND)])}));
assert.equal(basic.status, 'complete', '正在加载的页面要能读到记录');
assert.equal(basic.records.length, 2, '两条记录');
assert.equal(basic.records[0].activity, '生涯导航：如何做一名合格的大学生', '活动名称');
assert.equal(basic.records[0].credit, '0.10', '认定学分归一两位');
assert.equal(basic.records[0].time, '2026-10-26 16:43', '创建时间归一');
assert.equal(basic.records[0].dl, '劳育', '学分大类');
assert.equal(basic.records[0].lb, '三创活动', '学分类别');
assert.equal(basic.records[1].credit, '1.00', '整数分值也归一');

const repeat = run(env({nodes: page([card(FIRST), card(FIRST)])}));
assert.equal(repeat.records.length, 1, '同一条记录不重复计数');

const login = run(env({nodes: page([card(FIRST)]), login: true}));
assert.equal(login.status, 'failed', '登录页不能当数据页');
assert.equal(login.reason, 'login', '登录页给出 login 原因');

const blocked = run(env({nodes: page([card(FIRST)]), host: 'example.com'}));
assert.equal(blocked.status, 'failed');
assert.equal(blocked.reason, 'blocked', '只允许学工域名');

const empty = run(env({nodes: page([])}));
assert.equal(empty.status, 'failed');
assert.equal(empty.reason, 'empty', '没有记录时明确失败，不保存半份数据');

const fragment = run(env({nodes: page([
  element('div', '', [element('span', '学分大类：德育 学分类别：讲座报告'), element('span', '活动名称：新生讲座 认定学分：0.2 创建时间：2026-09-30')])
])}));
assert.equal(fragment.records.length, 1, '标签被拆到两个子元素也能读');
assert.equal(fragment.records[0].lb, '讲座报告', '拆分后的类别');

// 翻页：点「下一页」后列表追加第二页，两页记录都要保留。
const third = {
  '项目名称': '健康教育', '学分大类': '健康教育', '学分类别': '健康教育',
  '活动名称': '急救知识培训', '学年': '2026-2027', '学期': '第一学期',
  '学分来源': '活动', '认定学分': '0.5', '创建时间': '2026-09-12 10:00:00'
};
const paged = (function () {
  const container = element('div', '', []);
  const next = element('span', '下一页');
  let clicked = false;
  next.click = () => {
    if (clicked) return;
    clicked = true;
    const extra = card(third);
    extra.parentElement = container;
    container.children.push(extra);
  };
  container.children.push(card(FIRST), next);
  return run(env({nodes: [container]}));
})();
assert.equal(paged.status, 'complete', '翻页后仍要给出完整结果');
assert.equal(paged.records.length, 2, '第二页的记录不能丢');
assert.equal(paged.records[1].activity, '急救知识培训', '第二页记录内容');

// 学年切换：两个学年按钮，点哪个就换一批记录，两个学年的记录都要留下。
const oldYear = Object.assign({}, SECOND, {'学年': '2025-2026', '活动名称': '上一学年志愿活动'});
const swept = (function () {
  const host = element('div', '', [card(FIRST)]);
  const show = (fields) => {
    const one = card(fields);
    one.parentElement = host;
    host.children = [one];
  };
  const chipNow = element('span', '2026-2027');
  chipNow.click = () => show(FIRST);
  const chipOld = element('span', '2025-2026');
  chipOld.click = () => show(oldYear);
  return run(env({nodes: [element('div', '', [chipNow, chipOld, host])]}));
})();
assert.equal(swept.status, 'complete', '逐个学年读完要能收尾');
assert.equal(swept.records.length, 2, '两个学年的记录都要留下');
const years = swept.records.map(r => r.year).sort().join(',');
assert.equal(years, '2025-2026,2026-2027', '记录的学年都要在');

// 页面底部的真实分页样式：「共 3 条  ‹ 1 6 ›  前往 1 页」，点 › 才翻页。
const arrowPaged = (function () {
  const host = element('div', '', [card(FIRST)]);
  let page = 1;
  const next = element('span', '>');
  next.click = () => {
    if (page >= 3) return;
    page++;
    const one = card(Object.assign({}, FIRST, {
      '活动名称': '第' + page + '页活动',
      '创建时间': '2026-09-0' + page + ' 10:00:00'
    }));
    one.parentElement = host;
    host.children = [one];
  };
  const total = element('span', '共 3 条');
  const prev = element('span', '<');
  const first = element('span', '1');
  const last = element('span', '6');
  const jump = element('span', '前往');
  const box = element('div', '', [total, prev, first, last, next, jump, host]);
  return run(env({nodes: [box]}));
})();
assert.equal(arrowPaged.status, 'complete', '箭头分页也要能收尾');
assert.equal(arrowPaged.records.length, 3, '箭头翻页要把每一页都读进来');

console.log('dekt-records.test.cjs: all assertions passed');
