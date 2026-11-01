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
  let now = 0;
  const body = element('body', '', options.nodes || []);
  body.getElementsByTagName = () => walk(body, []);
  const ctx = {
    location: {
      protocol: 'https:', hostname: options.host || 'xg.sit.edu.cn',
      pathname: options.path || '/apply-manage/xsrd', port: '',
      href: 'https://' + (options.host || 'xg.sit.edu.cn') + (options.path || '/apply-manage/xsrd')
    },
    document: {
      body,
      getElementsByTagName(tag) { return tag === 'iframe' ? (options.frames || []) : []; },
      querySelector(selector) { return (typeof options.login === 'function' ? options.login() : options.login)
        && selector === 'input[type=password]' ? {} : null; }
    },
    getComputedStyle() { return {display: 'block', visibility: 'visible'}; },
    Date: class extends Date { static now() { return now; } },
    setTimeout(fn, delay = 0) { queue.push({fn, at: now + delay}); }
  };
  ctx.window = ctx;
  ctx.__queue = queue;
  ctx.__tick = () => {
    queue.sort((a, b) => a.at - b.at);
    const task = queue.shift();
    now = task.at;
    task.fn();
  };
  vm.createContext(ctx);
  return ctx;
}
function run(ctx) {
  vm.runInContext(script, ctx);
  for (let i = 0; i < 5000 && ctx.__queue.length && ctx.__xinxinRecords.status === 'pending'; i++) ctx.__tick();
  assert.notEqual(ctx.__xinxinRecords.status, 'pending', '脚本必须在有界时间内结束');
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
assert.equal(basic.records[0].time, '2026-10-26 16:43:54', '创建时间保留秒数');
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

const privateContext = env({nodes: [element('span', '共 3 条'), card(Object.assign({}, FIRST, {
  '姓名': 'SYNTHETIC_NAME', '学号': 'SYNTHETIC_STUDENT'
}))]});
Object.defineProperty(privateContext.document.body, 'innerHTML', {get() { throw new Error('不得导出 HTML'); }});
const incomplete = run(privateContext);
assert.equal(incomplete.status, 'failed', '缺页不能作为完整快照保存');
assert.equal(incomplete.reason, 'incomplete');
assert.ok(!JSON.stringify(incomplete).includes('SYNTHETIC_STUDENT'), '身份字段不进入读取结果');
assert.ok(!('tail' in incomplete) && !('pager' in incomplete), '不采集原始 HTML');

const sameMinute = run(env({nodes: page([
  card(FIRST), card(Object.assign({}, FIRST, {'创建时间': '2026-10-26 16:43:55'})),
  card(Object.assign({}, FIRST, {'学分大类': '德育', '学分类别': '志愿公益'}))
])}));
assert.equal(sameMinute.records.length, 3, '同一分钟或不同类别的记录不得误合并');

const yearValues = run(env({nodes: page([
  element('div', '', [element('span', '学分大类：劳育 学分类别：三创活动 活动名称：SYNTHETIC_NEW 认定学分：0.1 学年：'), element('span', '2026-2027')]),
  element('div', '', [element('span', '学分大类：劳育 学分类别：三创活动 活动名称：SYNTHETIC_OLD 认定学分：0.2 学年：'), element('span', '2025-2026')])
])}));
assert.equal(yearValues.status, 'complete', '记录卡内的年份文字不能被误当作筛选按钮');
assert.equal(yearValues.records.length, 2);

assert.equal(run(env({path: '/service/fwdt', nodes: page([card(FIRST)])})).reason, 'blocked', '服务大厅不是记录页');

const changing = env({nodes: page([card(FIRST)])});
changing.setTimeout(() => { changing.location.href = 'https://xg.sit.edu.cn/service/fwdt'; }, 100);
assert.equal(run(changing).reason, 'moved', '读取过程中导航必须终止旧读取');

let loginShown = false;
const expired = env({nodes: page([card(FIRST)]), login: () => loginShown});
expired.setTimeout(() => { loginShown = true; }, 100);
assert.equal(run(expired).reason, 'login', '读取中会话失效不能继续保存');

const duplicate = env({nodes: page([card(FIRST)])});
vm.runInContext(script, duplicate);
const pending = duplicate.__xinxinRecords;
vm.runInContext(script, duplicate);
assert.equal(duplicate.__xinxinRecords, pending, '正在读取时不启动第二个任务');
assert.equal(run(duplicate).status, 'complete');

// 真正替换列表并延迟响应的分页，不能依赖列表追加或立即更新。
const delayed = (() => {
  const host = element('div', '', [card(FIRST)]);
  const next = element('span', '>');
  const pager = element('div', '', [element('span', '共 2 条'), next]);
  const context = env({nodes: [pager, host]});
  next.click = () => context.setTimeout(() => {
    const row = card(SECOND); row.parentElement = host; host.children = [row];
  }, 1400);
  return run(context);
})();
assert.equal(delayed.status, 'complete');
assert.equal(delayed.records.length, 2, '异步替换第二页仍读取完整');
assert.equal(delayed.pages, 2, '保存实际读取页数');

// 当前学年读全，历史学年缺页：不能用其他学年的记录数补足当前学年。
const incompleteYear = (() => {
  const host = element('div', '', [card(FIRST), card(SECOND)]);
  const total = element('span', '共 2 条');
  const nowYear = element('span', '2026-2027');
  const previousYear = element('span', '2025-2026');
  nowYear.click = () => {};
  previousYear.click = () => {
    const row = card(oldYear); row.parentElement = host; host.children = [row];
  };
  return run(env({nodes: [nowYear, previousYear, total, host]}));
})();
assert.equal(incompleteYear.reason, 'incomplete', '每个学年独立验证完整性');

const initiallyEmptyYear = (() => {
  const host = element('div', '', []);
  const total = element('span', '共 0 条');
  const nowYear = element('span', '2026-2027');
  const previousYear = element('span', '2025-2026');
  nowYear.click = () => { host.children = []; total._text = '共 0 条'; };
  previousYear.click = () => {
    const row = card(oldYear); row.parentElement = host; host.children = [row]; total._text = '共 1 条';
  };
  return run(env({nodes: [nowYear, previousYear, total, host]}));
})();
assert.equal(initiallyEmptyYear.status, 'complete', '当前学年为空仍读取其他学年');
assert.equal(initiallyEmptyYear.records.length, 1);

const noChange = env({nodes: [element('span', '共 999 条'), card(FIRST)]});
assert.equal(run(noChange).reason, 'limit', '超过保存上限时明确失败');

const enterScript = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/dekt-enter.js'), 'utf8');
for (const options of [{host: 'example.com', path: '/service/fwdt'}, {path: '/apply-manage/xsrd'}, {path: '/service/fwdt', login: true}]) {
  let clicked = false;
  const link = element('button', '学分明细查询'); link.click = () => { clicked = true; };
  vm.runInContext(enterScript, env(Object.assign({}, options, {nodes: [link]})));
  assert.equal(clicked, false, '不在其他页面或登录表单上点击');
}
let entered = 0;
const entry = element('button', '学分明细查询'); entry.click = () => { entered++; };
const hall = env({path: '/service/fwdt', nodes: [entry]});
vm.runInContext(enterScript, hall); vm.runInContext(enterScript, hall);
assert.equal(entered, 1, '服务大厅明确入口只点击一次');

console.log('dekt-records.test.cjs: all assertions passed');
