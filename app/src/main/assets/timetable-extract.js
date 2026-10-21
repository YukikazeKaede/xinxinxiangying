(function () {
  'use strict';
  // Reads course responses only; no credentials, cookies, request bodies, or writes.
  var academic = /^(jwxt|yjsgl)\.sit\.edu\.cn$/i.test(location.hostname);
  if (!academic || location.protocol !== 'https:') return JSON.stringify({error: '请先打开学校教务系统中的个人课表'});
  var state = window.__xinxinTimetableV1;
  function courseUrl(url) {
    try {
      var u = new URL(url, location.href);
      return u.origin === location.origin && /\/(xskbcx_cxXsgrkb\.html|getXqkb4Xs|queryXq4Pk)(?:[;/?]|$)/i.test(u.pathname);
    } catch (_) { return false; }
  }
  if (!state) {
    state = {raw: [], installed: true};
    window.__xinxinTimetableV1 = state;
    function retain(url, value) {
      try {
        if (!courseUrl(url)) return;
        var data = typeof value === 'string' ? JSON.parse(value) : value;
        if (!data || typeof data !== 'object' || JSON.stringify(data).length > 2500000) return;
        var normalized = String(url).split('?')[0];
        state.raw = state.raw.filter(function (v) { return v.url !== normalized; });
        state.raw.push({url: normalized, data: data});
        if (state.raw.length > 6) state.raw.shift();
      } catch (_) {}
    }
    var originalOpen = XMLHttpRequest.prototype.open;
    var originalSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (method, url) {
      this.__xinxinCourseUrl = courseUrl(url) ? String(url) : null;
      return originalOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
      if (this.__xinxinCourseUrl) {
        this.addEventListener('load', function () {
          try {
            if (this.status >= 200 && this.status < 300)
              retain(this.__xinxinCourseUrl, this.responseType === 'json' ? this.response : this.responseText);
          } catch (_) {}
        });
      }
      return originalSend.apply(this, arguments);
    };
    if (window.fetch) {
      var originalFetch = window.fetch;
      window.fetch = function (input) {
        var url = typeof input === 'string' ? input : input && input.url;
        return originalFetch.apply(this, arguments).then(function (response) {
          if (courseUrl(url) && response.ok) response.clone().text().then(function (value) { retain(url, value); }).catch(function () {});
          return response;
        });
      };
    }
  }
  function text(element) { return (element.innerText || element.textContent || '').replace(/ /g, ' ').trim(); }
  function selected(el) { return el && el.options && el.selectedIndex >= 0 ? text(el.options[el.selectedIndex]) : ''; }
  // 学年/学期下拉文字拼成用户可读的学期名；真实教务学期选项文字是 "1"/"2"。
  function semesterLabel(yearText, termText) {
    if (/^\d{4}-\d{4}$/.test(yearText) && /^\d{1,2}$/.test(termText)) return yearText + '学年第' + termText + '学期';
    return [yearText, termText].filter(Boolean).join(' ');
  }
  // 按周次课表页把第一教学周的周一写在周次下拉里："1(2026-09-14至2026-09-20)"。
  function weekOneStart(zs) {
    if (!zs || !zs.options) return '';
    for (var i = 0; i < zs.options.length; i++) {
      if (String(zs.options[i].value) !== '1') continue;
      var match = /\(\s*(\d{4}-\d{2}-\d{2})/.exec(text(zs.options[i]));
      return match ? match[1] : '';
    }
    return '';
  }
  function weekCount(zs) {
    if (!zs || !zs.options) return 0;
    var max = 0;
    for (var i = 0; i < zs.options.length; i++) { var v = parseInt(zs.options[i].value, 10); if (v > max) max = v; }
    return max;
  }
  function lessonOf(element) {
    return {
      kcmc: element.getAttribute('data-kcmc') || '',
      jsxm: element.getAttribute('data-jsxm') || '',
      zcd: element.getAttribute('data-zcd') || '',
      cdmc: element.getAttribute('data-cdmc') || '',
      xqj: element.getAttribute('data-xqj') || '',
      jcor: element.getAttribute('data-jcor') || '',
      xnm: element.getAttribute('data-xnm') || '',
      xqm: element.getAttribute('data-xqm') || ''
    };
  }
  function lessonKey(item) { return [item.kcmc, item.jsxm, item.cdmc, item.xqj, item.jcor, item.zcd].join('\u001f'); }
  var query = window.__xinxinTimetableQuery;
  var raw = query && query.status === 'complete' && query.raw ? [query.raw] : state.raw.slice();
  // Known page-owned data containers; never evaluate scripts or invoke getters.
  ['kbList', 'kbData', 'kcbData', 'courseList', 'timetableData', 'kbxx'].forEach(function (key) {
    if (state.raw.length || query && query.status === 'complete') return;
    try {
      var descriptor = Object.getOwnPropertyDescriptor(window, key);
      if (descriptor && descriptor.value && typeof descriptor.value === 'object') {
        var value = descriptor.value;
        if (JSON.stringify(value).length < 2500000) raw.push({url: location.origin + location.pathname, data: key === 'kbList' ? {kbList: value} : value});
      }
    } catch (_) {}
  });
  var documents = [document];
  Array.prototype.forEach.call(document.querySelectorAll('iframe'), function (frame) {
    try { if (frame.contentDocument && frame.contentWindow.location.origin === location.origin) documents.push(frame.contentDocument); } catch (_) {}
  });
  var tables = [], blocks = [], lessons = [], seen = {}, semester = '', termStart = '', termWeeks = 0;
  // 周次遍历驱动已汇总的课程优先，再补充当前 DOM 中可见的课程块。
  if (query && query.status === 'complete' && query.lessons) query.lessons.forEach(function (item) {
    var key = lessonKey(item);
    if (!seen[key]) { seen[key] = true; lessons.push(item); }
  });
  documents.forEach(function (doc) {
    if (!semester) {
      var year = doc.querySelector('#xnm,select[name="xnm"]');
      var term = doc.querySelector('#xqm,select[name="xqm"]');
      semester = semesterLabel(selected(year), selected(term));
    }
    if (!termStart) { var zs = doc.querySelector('#zs,select[name="zs"]'); termStart = weekOneStart(zs); termWeeks = weekCount(zs); }
    Array.prototype.forEach.call(doc.querySelectorAll('div.lesson, [data-kcmc][data-xqj][data-jcor]'), function (element) {
      var item = lessonOf(element), key = lessonKey(item);
      if (!item.kcmc || seen[key]) return;
      seen[key] = true; lessons.push(item);
    });
    var timetableTables = doc.querySelectorAll('table#subtab');
    if (!timetableTables.length) timetableTables = doc.querySelectorAll('table');
    Array.prototype.forEach.call(timetableTables, function (table) {
      if (tables.length >= 12 || table.rows.length > 100) return;
      var rows = [];
      Array.prototype.forEach.call(table.rows, function (row) {
        var cells = [];
        Array.prototype.forEach.call(row.cells, function (cell) {
          cells.push({text: text(cell).slice(0, 12000), rowSpan: cell.rowSpan || 1, colSpan: cell.colSpan || 1});
        });
        rows.push(cells);
      });
      if (rows.length) tables.push({rows: rows});
    });
    // Card-style renderers sometimes expose timetable coordinates on the cells.
    Array.prototype.forEach.call(doc.querySelectorAll('[data-weekday][data-start-period], [data-xqj][data-jcs]'), function (element) {
      if (blocks.length >= 1000) return;
      var periods = (element.getAttribute('data-jcs') || '').match(/\d+/g) || [];
      blocks.push({text: text(element).slice(0,12000), weekday: Number(element.getAttribute('data-weekday') || element.getAttribute('data-xqj')),
        startPeriod: Number(element.getAttribute('data-start-period') || periods[0]),
        endPeriod: Number(element.getAttribute('data-end-period') || periods[periods.length-1])});
    });
  });
  if (query && query.status === 'complete') {
    if (query.semester) semester = query.semester;
    if (query.termStart) { termStart = query.termStart; termWeeks = query.termWeeks || termWeeks; }
  }
  var failedWeeks = query && query.status === 'complete' && query.failedWeeks ? query.failedWeeks : [];
  return JSON.stringify({sourceUrl: location.origin + location.pathname, title: document.title, semester: semester,
    termStart: termStart, termWeeks: termWeeks, failedWeeks: failedWeeks,
    raw: raw, lessons: lessons, tables: tables, blocks: blocks, text: ''});
})()
