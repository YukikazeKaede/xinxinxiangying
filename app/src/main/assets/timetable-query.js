(function () {
  'use strict';
  // User-triggered read-only timetable query; observed endpoint and selected term only.
  // Research-student requests are captured from the actual page instead of guessed.
  if (location.protocol !== 'https:' || location.hostname !== 'jwxt.sit.edu.cn') return 'skip';
  window.__xinxinTimetableQuery = null;
  var contexts = [{doc: document, win: window}];
  Array.prototype.forEach.call(document.querySelectorAll('iframe'), function (frame) {
    try { if (frame.contentWindow.location.origin === location.origin) contexts.push({doc: frame.contentDocument, win: frame.contentWindow}); } catch (_) {}
  });
  var context = contexts.filter(function (item) { return /\/jwglxt\/kbcx\//.test(item.win.location.pathname); })[0];
  if (!context) return 'skip';
  var doc = context.doc;
  var win = context.win;
  var year = doc.querySelector('#xnm,select[name="xnm"],input[name="xnm"]');
  var term = doc.querySelector('#xqm,select[name="xqm"],input[name="xqm"]');
  if (!year || !term || !year.value || !term.value) return 'skip';
  function byId(id) { try { return typeof doc.getElementById === 'function' ? doc.getElementById(id) : doc.querySelector('#' + id); } catch (_) { return null; } }
  function selection(element) { return element.options && element.selectedIndex >= 0 ? (element.options[element.selectedIndex].innerText || element.options[element.selectedIndex].textContent || '').trim() : ''; }
  var yearText = selection(year), termText = selection(term);
  // 真实教务学期选项文字是 "1"/"2"，拼成 "2026-2027学年第1学期" 便于用户核对。
  var semester = /^\d{4}-\d{4}$/.test(yearText) && /^\d{1,2}$/.test(termText) ? yearText + '学年第' + termText + '学期' : [yearText, termText].filter(Boolean).join(' ');
  var queryState = {status: 'pending', semester: semester};
  window.__xinxinTimetableQuery = queryState;

  // 「学生课表查询（按周次）」页面：#zs 周次下拉 + #myTab/#table_tb 课程表。
  // 每周表格只含当周课程，因此逐项切换周次、读取渲染结果并按完整周次描述合并，
  // 不猜测任何未观察到的接口。
  var zs = byId('zs');
  if (zs && zs.options && zs.options.length >= 2 && (byId('table_tb') || byId('myTab')) &&
      (typeof win.searchResult === 'function' || byId('to-search'))) {
    var weeks = [];
    for (var o = 0; o < zs.options.length; o++) { var v = parseInt(zs.options[o].value, 10); if (v >= 1 && v <= 60 && weeks.indexOf(v) < 0) weeks.push(v); }
    weeks.sort(function (a, b) { return a - b; });
    for (var s = 0; s < zs.options.length; s++) {
      if (String(zs.options[s].value) === '1') {
        var label = (zs.options[s].innerText || zs.options[s].textContent || '');
        var match = /\(\s*(\d{4}-\d{2}-\d{2})/.exec(label);
        if (match) queryState.termStart = match[1];
        break;
      }
    }
    queryState.termWeeks = weeks.length ? weeks[weeks.length - 1] : 0;
    queryState.weeks = weeks.length;
    var lessons = {}, failed = [], index = 0;
    function container() { return byId('table_tb') || byId('myTab'); }
    function snapshot() { var el = container(); return el ? el.innerHTML : null; }
    function later(fn) { setTimeout(fn, 120); }
    function lessonKey(el) { return [el.getAttribute('data-kcmc'), el.getAttribute('data-jsxm'), el.getAttribute('data-cdmc'), el.getAttribute('data-xqj'), el.getAttribute('data-jcor'), el.getAttribute('data-zcd')].join('\u001f'); }
    function harvest() {
      Array.prototype.forEach.call(doc.querySelectorAll('div.lesson, [data-kcmc][data-xqj][data-jcor]'), function (el) {
        var key = lessonKey(el);
        if (lessons[key]) return;
        lessons[key] = {kcmc: el.getAttribute('data-kcmc') || '', jsxm: el.getAttribute('data-jsxm') || '', zcd: el.getAttribute('data-zcd') || '', cdmc: el.getAttribute('data-cdmc') || '', xqj: el.getAttribute('data-xqj') || '', jcor: el.getAttribute('data-jcor') || '', xnm: el.getAttribute('data-xnm') || '', xqm: el.getAttribute('data-xqm') || ''};
      });
    }
    function fire() {
      var value = String(weeks[index]);
      queryState.week = index + 1;
      try {
        if (win.jQuery) win.jQuery(zs).val(value).trigger('change');
        else { zs.value = value; zs.dispatchEvent(new Event('change', {bubbles: true})); }
      } catch (_) { try { zs.value = value; } catch (_) {} }
      try {
        if (typeof win.searchResult === 'function') win.searchResult();
        else { var btn = byId('to-search'); if (btn) btn.click(); }
      } catch (_) {}
    }
    function finish() {
      queryState.lessons = Object.keys(lessons).map(function (key) { return lessons[key]; });
      if (failed.length) queryState.failedWeeks = failed;
      queryState.status = queryState.lessons.length ? 'complete' : 'failed';
    }
    function step() {
      if (index >= weeks.length) { finish(); return; }
      var before = snapshot(), settled = false, ticks = 0, unhook = null;
      try {
        if (win.jQuery) {
          var handler = function () { settled = true; };
          win.jQuery(doc).one('ajaxComplete.xinxin', handler);
          unhook = function () { try { win.jQuery(doc).off('ajaxComplete.xinxin', handler); } catch (_) {} };
        }
      } catch (_) {}
      fire();
      (function poll() {
        var changed = snapshot() !== before;
        if ((settled && ticks >= 2) || (changed && ticks >= 3)) {
          later(function () { if (unhook) unhook(); harvest(); index += 1; step(); });
          return;
        }
        ticks += 1;
        if (ticks > 60) { if (unhook) unhook(); failed.push(weeks[index]); index += 1; step(); return; }
        later(poll);
      })();
    }
    step();
    return 'started';
  }

  var params = 'xnm=' + encodeURIComponent(year.value) + '&xqm=' + encodeURIComponent(term.value);
  var mode = doc.querySelector('[name="kzlx"],#kzlx');
  if (mode && mode.value) params += '&kzlx=' + encodeURIComponent(mode.value);
  var headers = {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest'};
  var token = doc.querySelector('meta[name="_csrf"]'), header = doc.querySelector('meta[name="_csrf_header"]');
  if (token && header && /^[a-zA-Z0-9_-]+$/.test(header.content)) headers[header.content] = token.content;
  var url = location.origin + '/jwglxt/kbcx/xskbcx_cxXsgrkb.html';
  window.fetch(url, {method: 'POST', credentials: 'same-origin', headers: headers, body: params, redirect: 'error'})
    .then(function (response) { if (!response.ok) throw new Error('HTTP ' + response.status); return response.json(); })
    .then(function (data) {
      if (!data || !Array.isArray(data.kbList)) throw new Error('unrecognized');
      var state = window.__xinxinTimetableV1;
      if (!state) throw new Error('missing capture');
      state.raw = state.raw.filter(function (item) { return item.url !== url; });
      state.raw.push({url: url, data: data});
      queryState.raw = {url: url, data: data};
      queryState.status = 'complete';
    }).catch(function () { queryState.status = 'failed'; });
  return 'started';
})()
