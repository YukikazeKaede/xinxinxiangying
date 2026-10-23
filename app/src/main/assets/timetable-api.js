(function (endpoint) {
  'use strict';
  // 学校手机版课表接口（同源 POST，仅读取本人课表）：按周取回权威 JSON 后合并，
  // 不改动页面、不写任何数据、不带额外凭据，cookie 由 WebView 自己维护。
  // 该接口在任意已登录的教务页面都能用，因此不需要先把 WebView 导航到课表页。
  if (location.protocol !== 'https:' || !/^(jwxt|yjsgl)\.sit\.edu\.cn$/i.test(location.hostname)) return 'blocked';
  if (typeof endpoint !== 'string' || !endpoint) return 'blocked';
  var target;
  try { target = new URL(endpoint, location.href); } catch (_) { return 'blocked'; }
  // 只允许同源接口：凭据由 WebView 自己带上，跨源地址一律不请求。
  if (target.origin !== location.origin || target.username || target.password) return 'blocked';
  var last = window.__xinxinTimetableQuery;
  if (last && last.status === 'pending') return 'busy';
  function element(id) {
    return document.querySelector('#' + id) || document.querySelector('select[name="' + id + '"]') || document.querySelector('input[name="' + id + '"]');
  }
  function value(id) { var el = element(id); return el && typeof el.value === 'string' ? el.value.trim() : ''; }
  var state = window.__xinxinTimetableQuery = {status: 'pending', week: 0, weeks: 20, semester: ''};
  var domYear = value('xnm'), domTerm = value('xqm'), domWeeks = 0, domStart = '';
  var zs = element('zs');
  if (zs && zs.options) {
    for (var i = 0; i < zs.options.length; i++) {
      var week = parseInt(zs.options[i].value, 10);
      if (week >= 1 && week <= 30 && week > domWeeks) domWeeks = week;
      if (String(zs.options[i].value) === '1') {
        var start = /\(\s*(\d{4}-\d{2}-\d{2})/.exec(String(zs.options[i].textContent || zs.options[i].innerText || ''));
        if (start) domStart = start[1];
      }
    }
  }
  if (domWeeks) state.weeks = domWeeks;
  if (domStart) state.termStart = domStart;
  var seen = {}, list = [], failed = [];
  function key(row) {
    return [row.kcmc, row.jxb_id, row.xqj, row.jcs, row.cdmc, row.zcd].join('\u001f');
  }
  function rows(data) { return Array.isArray(data.kbList) ? data.kbList : []; }
  function collect(data) {
    var items = rows(data);
    for (var i = 0; i < items.length; i++) {
      var row = items[i];
      if (!row || typeof row !== 'object') continue;
      var id = key(row);
      if (seen[id]) continue;
      seen[id] = true; list.push(row);
    }
  }
  function labels(data) {
    var xsxx = data && data.xsxx;
    if (!xsxx || typeof xsxx !== 'object') return '';
    var year = String(xsxx.XNMC || '').trim(), term = String(xsxx.XQMMC || '').trim();
    if (/^\d{4}-\d{4}$/.test(year) && /^\d{1,2}$/.test(term)) return year + '学年第' + term + '学期';
    return [year, term].filter(Boolean).join(' ');
  }
  function monday(data) {
    var list2 = data && data.rqazcList;
    if (!Array.isArray(list2)) return '';
    for (var i = 0; i < list2.length; i++) {
      if (String(list2[i].xqj) === '1' && /^\d{4}-\d{2}-\d{2}$/.test(String(list2[i].rq))) return String(list2[i].rq);
    }
    return '';
  }
  // 该接口按 zs（周次）过滤，但每条的 zcd 仍是整学期周次，因此逐周取回再按记录去重即可。
  function fetchWeek(xnm, xqm, week) {
    var body = 'xnm=' + encodeURIComponent(xnm) + '&xqm=' + encodeURIComponent(xqm) +
      '&zs=' + week + '&kblx=1&doType=app&xh=';
    return window.fetch(endpoint, {
      method: 'POST', credentials: 'same-origin', cache: 'no-store',
      headers: {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest'},
      body: body
    }).then(function (response) {
      if (!response.ok) throw new Error('HTTP ' + response.status);
      return response.text();
    }).then(function (text) {
      var data;
      try { data = JSON.parse(text); } catch (_) { throw new Error('login'); }
      if (!data || typeof data !== 'object' || !('kbList' in data)) throw new Error('login');
      return data;
    });
  }
  function fail(reason) { state.status = 'failed'; state.reason = reason; }
  // 不在课表页时页面没有学号/学年下拉：按当前日期推算候选学期，用接口返回的 xsxx 确认。
  // 只要 xsxx 里有学生姓名，就说明这是本人真实可用的学期。
  function candidates() {
    var out = [], now = new Date(), year = now.getFullYear(), month = now.getMonth() + 1;
    if (/^\d{4}$/.test(domYear) && /^\d{1,2}$/.test(domTerm)) out.push([domYear, domTerm]);
    var autumn = month >= 8 || month <= 1, start = month >= 8 ? year : year - 1;
    var terms = autumn ? ['3', '12'] : ['12', '3'];
    [start, start - 1].forEach(function (y) {
      terms.forEach(function (t) {
        var pair = [String(y), t];
        if (!out.some(function (p) { return p[0] === pair[0] && p[1] === pair[1]; })) out.push(pair);
      });
    });
    return out;
  }
  function probe(index, done) {
    var options = candidates();
    if (index >= options.length) { fail('no-term'); return; }
    var pair = options[index];
    state.semester = pair[0] + '-' + (parseInt(pair[0], 10) + 1);
    fetchWeek(pair[0], pair[1], 1).then(function (data) {
      if (!data.xsxx || !String(data.xsxx.XM || '').trim()) { probe(index + 1, done); return; }
      done(pair[0], pair[1], data);
    }, function (error) {
      if (String(error && error.message) === 'login') { fail('login'); return; }
      probe(index + 1, done);
    });
  }
  function walk(xnm, xqm, first, week) {
    if (week > state.weeks) { finish(); return; }
    state.week = week;
    var step = week === 1 ? Promise.resolve(first) : fetchWeek(xnm, xqm, week);
    step.then(function (data) {
      collect(data);
      window.setTimeout(function () { walk(xnm, xqm, first, week + 1); }, 120);
    }, function (error) {
      if (String(error && error.message) === 'login') { fail('login'); return; }
      failed.push(week);
      window.setTimeout(function () { walk(xnm, xqm, first, week + 1); }, 120);
    });
  }
  function finish() {
    if (!list.length) { state.status = 'empty'; state.reason = failed.length ? 'weeks-failed' : 'no-course'; return; }
    if (failed.length) state.failedWeeks = failed;
    state.raw = {url: endpoint, data: {kbList: list, xsxx: state.student || {}}};
    state.termWeeks = state.weeks;
    state.status = 'complete';
  }
  probe(0, function (xnm, xqm, first) {
    state.student = {XM: first.xsxx.XM, XH: first.xsxx.XH || ''};
    var label = labels(first);
    if (label) state.semester = label;
    var start = monday(first);
    if (start) state.termStart = start;
    collect(first);
    state.week = 1;
    window.setTimeout(function () { walk(xnm, xqm, first, 2); }, 120);
  });
  return 'started';
})(typeof __TIMETABLE_API__ === 'string' ? __TIMETABLE_API__ : '')
