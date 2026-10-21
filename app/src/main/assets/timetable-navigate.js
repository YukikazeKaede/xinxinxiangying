(function (entry) {
  'use strict';
  // 任何异常都不允许把同步卡死：把原因回传给原生侧记录，再当作「还没到课表页」。
  try {
    if (location.protocol !== 'https:' || !/^(jwxt|yjsgl)\.sit\.edu\.cn$/.test(location.hostname)) return 'not-academic';
    if (document.querySelector('input[type="password"]')) return 'needs-login';
    function usable(doc) { return !!doc && typeof doc.querySelector === 'function'; }
    function field(doc, id) { return usable(doc) ? doc.querySelector('#' + id + ',select[name="' + id + '"]') : null; }
    // 「学生课表查询（按周次）」页的稳定特征：周次下拉 #zs 与学年/学期下拉同时存在。
    // 旧版入口 xskbcx_cxXSkbcxIndex.html 只有学年/学期，没有 #zs，不能当作课表页。
    function weekPage(doc) { return !!(field(doc, 'zs') && field(doc, 'xnm') && field(doc, 'xqm')); }
    function byWeekPath(value) { return /xskbcxZccx/i.test(String(value || '')); }
    function frameDoc(frame) {
      var doc;
      try { doc = frame.contentDocument; } catch (_) { return null; }
      if (!usable(doc)) return null;
      try { if (!frame.contentWindow || frame.contentWindow.location.origin !== location.origin) return null; } catch (_) { return null; }
      return doc;
    }
    var contexts = [{doc: document, win: window}];
    var frames = document.querySelectorAll('iframe');
    for (var f = 0; f < frames.length; f++) {
      var doc = frameDoc(frames[f]);
      if (doc) contexts.push({doc: doc, win: frames[f].contentWindow});
    }
    for (var c = 0; c < contexts.length; c++) {
      var path = '';
      try { path = contexts[c].win.location.pathname; } catch (_) {}
      if (weekPage(contexts[c].doc) || (byWeekPath(path) && field(contexts[c].doc, 'xnm') && field(contexts[c].doc, 'xqm'))) {
        try { sessionStorage.removeItem('__xinxinNavTries'); } catch (_) {}
        return 'ready';
      }
    }
    function target(value) {
      try {
        var url = new URL(value, location.href);
        if (url.origin !== location.origin || url.username || url.password) return null;
        return url;
      } catch (_) { return null; }
    }
    function assign(value) {
      var url = target(value);
      if (!url) return false;
      location.assign(url.href); return true;
    }
    function alreadyThere(value) {
      var url = target(value);
      return !!url && url.pathname === location.pathname && url.search === location.search;
    }
    // 本科生：课表页地址是学校菜单自己给出的，登录后无论停在首页还是旧版入口，一律直接打开它，
    // 不再靠猜菜单链接，避免在旧课表页和首页之间来回打转（同一页面最多尝试三次）。
    if (entry) {
      var tries = 0;
      try { tries = parseInt(sessionStorage.getItem('__xinxinNavTries') || '0', 10) || 0; } catch (_) {}
      if (!alreadyThere(entry) && tries < 3) {
        try { sessionStorage.setItem('__xinxinNavTries', String(tries + 1)); } catch (_) {}
        if (assign(entry)) return 'navigating';
      }
      return 'waiting-menu';
    }
    // 研究生系统没有固定课表地址：只跟随页面自己给出的课表链接与菜单。
    var links = document.querySelectorAll('a[href],a[data-url],li[data-url]');
    for (var i = 0; i < links.length; i++) {
      var e = links[i];
      if (!e || typeof e.getAttribute !== 'function') continue;
      var value = e.getAttribute('data-url') || e.getAttribute('href');
      if (value && /\/kbcx\/xskbcx/i.test(value) && assign(value)) return 'navigating';
    }
    // The school top menu links through onclick="onClickMenu('/kbcx/xskbcx…','N…')" with a plain '#' href.
    // The handler path is relative to the page's own _path prefix (observed "/jwglxt"); read the
    // prefix from the page instead of inventing a route, and skip the handler when it is absent.
    var handlers = document.querySelectorAll('[onclick]');
    for (var j = 0; j < handlers.length; j++) {
      var node = handlers[j];
      if (!node || typeof node.getAttribute !== 'function') continue;
      var onclick = node.getAttribute('onclick') || '';
      if (onclick.length > 300) continue;
      var match = /['"](\/kbcx\/xskbcx[^'"]*)['"]/i.exec(onclick);
      if (!match) continue;
      var prefix = '';
      try { if (typeof window._path === 'string' && /^\/[A-Za-z0-9_-]{1,20}$/.test(window._path)) prefix = window._path; } catch (_) {}
      if (!prefix) continue;
      if (assign(prefix + match[1])) return 'navigating';
    }
    return 'waiting-menu';
  } catch (error) {
    return 'error:' + ((error && error.message) || error);
  }
})(typeof __TIMETABLE_ENTRY__ === 'string' ? __TIMETABLE_ENTRY__ : '')
