(function () {
  'use strict';
  if (location.protocol !== 'https:' || location.hostname !== 'ecard.sit.edu.cn' ||
      !/^\/epay\/h5\/payele\/?$/.test(location.pathname)) return 'blocked';
  if (window.__xinxinElectricity) return window.__xinxinElectricity.status;
  var state = window.__xinxinElectricity = {status: 'pending'};
  var fields = ['elesys', 'school', 'area'];
  var titles = ['新电费缴纳', '奉贤校区', '住宿区'];
  var stage = 0, attempts = 0, rows = [];
  function fallback() { state.status = 'fallback'; }
  function step() {
    try {
      if (++attempts > 80) { fallback(); return; }
      var $ = window.jQuery;
      if (!$ || !$.fn.select) { window.setTimeout(step, 250); return; }
      var input = document.getElementById(fields[stage]);
      if (!input) { fallback(); return; }
      // School handlers clear these lists before fetching the next level; old picker items may still exist meanwhile.
      var lists = [window.data, window.data1, window.data2];
      if (!Array.isArray(lists[stage]) || !lists[stage].length) { window.setTimeout(step, 250); return; }
      var picker = $(input).data('weui-select');
      if (!picker || typeof picker.updateInputValue !== 'function' || !picker.config) { fallback(); return; }
      var matches = (picker.config.items || []).filter(function (item) { return item.title === titles[stage]; });
      if (matches.length !== 1 || matches[0].value == null || String(matches[0].value) === '' || String(matches[0].value) === '-1') {
        window.setTimeout(step, 250); return;
      }
      var row = input.closest('.weui-cell');
      if (!row) { fallback(); return; }
      rows.push(row);
      if (input.value !== titles[stage] || input.getAttribute('data-values') !== String(matches[0].value))
        picker.updateInputValue($([matches[0].value]), [titles[stage]]);
      if (input.value !== titles[stage] || input.getAttribute('data-values') !== String(matches[0].value)) { fallback(); return; }
      if (++stage < fields.length) { window.setTimeout(step, 250); return; }
      // Hide only after all three values are accepted by the school's own cascading selectors.
      rows.forEach(function (row) { row.style.display = 'none'; });
      state.status = 'complete';
    } catch (_) { fallback(); }
  }
  step();
  return state.status;
})()
