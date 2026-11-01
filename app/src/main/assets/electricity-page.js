// Restore the saved school selectors, submit the school's read-only query step, then read the
// labelled balance. Never clicks recharge, amount or payment controls.
(function () {
    'use strict';
    var plan = __ELECTRICITY_PLAN__;
    var HOST = 'ecard.sit.edu.cn';
    function trusted() {
        return location.protocol === 'https:' && location.hostname === HOST
            && (!location.port || location.port === '443') && !location.username && !location.password
            && /^\/epay\//.test(location.pathname) && !/login/i.test(location.pathname);
    }
    function fail(reason) { return {status: 'failed', reason: reason}; }
    if (!trusted()) return JSON.stringify(fail('blocked'));
    if (document.querySelector('input[type=password]')) return JSON.stringify(fail('login'));
    var roles = ['elesys', 'school', 'area', 'build', 'floor', 'room'];
    var aliases = {elesys: ['elesys'], school: ['school'], area: ['area'],
        build: ['build', 'building'], floor: ['floor'], room: ['room']};
    function clean(value) { return String(value == null ? '' : value).replace(/[\s\u00a0]+/g, ' ').trim(); }
    function shown(el) {
        for (var node = el; node; node = node.parentElement) {
            if (node.hidden) return false;
            var style = window.getComputedStyle(node);
            if (style.display === 'none' || style.visibility === 'hidden') return false;
        }
        return true;
    }
    function field(role) {
        var names = aliases[role];
        for (var i = 0; i < names.length; i++) { var found = document.getElementById(names[i]); if (found) return found; }
        var labels = {build: /^(楼栋|楼宇|楼号|宿舍楼)[:：]?$/, floor: /^楼层[:：]?$/, room: /^(寝室|房间|房间号|寝室号|宿舍号)[:：]?$/};
        if (!labels[role]) return null;
        var rows = document.querySelectorAll('.weui-cell');
        for (var j = 0; j < rows.length; j++) {
            var label = rows[j].querySelector('label, .weui-label');
            if (label && labels[role].test(clean(label.textContent))) {
                var input = rows[j].querySelector('input[id], select[id]');
                if (input && String(input.type).toLowerCase() !== 'password') return input;
            }
        }
        return null;
    }
    function selected(role, input) {
        var nativeSelect = String(input.tagName).toUpperCase() === 'SELECT';
        var value = nativeSelect ? clean(input.value) : clean(input.getAttribute('data-values'));
        var title = nativeSelect && input.selectedIndex >= 0 ? clean(input.options[input.selectedIndex].textContent) : clean(input.value);
        if (!value || value === '-1' || !title || /请选择/.test(title)) return null;
        return {role: role, id: input.id, value: value, title: title};
    }
    function room() {
        var fields = [], building = false, bedroom = false;
        for (var i = 0; i < roles.length; i++) {
            var input = field(roles[i]);
            if (!input) continue;
            var row = selected(roles[i], input);
            if (!row) return null;
            fields.push(row);
            if (roles[i] === 'build') building = true;
            if (roles[i] === 'room') bedroom = true;
        }
        return building && bedroom ? {fields: fields} : null;
    }
    function key(value) {
        return JSON.stringify(value.fields.map(function (row) { return [row.role, row.id, row.value]; }));
    }
    /** The dormitory number saved for this account, used to verify the school's result page. */
    function wantedRoom(value) {
        var fields = value && value.fields;
        if (!Array.isArray(fields)) return '';
        for (var i = 0; i < fields.length; i++) if (fields[i].role === 'room') return clean(fields[i].title);
        return '';
    }
    /** Runs of "房间 / 1104" on the school's result page; empty when the page has no such row. */
    function roomOnPage() {
        var rows = document.querySelectorAll('.weui-cell, tr, li');
        for (var i = 0; i < rows.length; i++) {
            var cells = rows[i].children, hit = -1;
            for (var j = 0; j < cells.length; j++) {
                if (/^(房间|寝室|房间号|寝室号)[:：]?$/.test(clean(cells[j].textContent))) { hit = j; break; }
            }
            if (hit < 0) continue;
            for (var k = 0; k < cells.length; k++) {
                var value = clean(cells[k].textContent);
                if (k !== hit && value && !/^(房间|寝室|房间号|寝室号)/.test(value)) return value;
            }
        }
        var labels = document.querySelectorAll('div, span, label, td');
        for (var m = 0; m < labels.length; m++) {
            if (!/^(房间|寝室|房间号|寝室号)[:：]?$/.test(clean(labels[m].textContent))) continue;
            var next = labels[m].nextElementSibling;
            if (next && clean(next.textContent)) return clean(next.textContent);
            var parent = labels[m].parentElement, last = parent && parent.lastElementChild;
            if (last && last !== labels[m] && clean(last.textContent)) return clean(last.textContent);
        }
        return '';
    }
    function balances() {
        var all = document.body ? document.body.getElementsByTagName('*') : [];
        var money = {}, energy = {};
        var pattern = /(电费余额|剩余电费|剩余金额|剩余电量|剩余电度|剩余购电量)\s*(?:[（(]\s*(元|度|kWh|千瓦时)\s*[）)])?\s*[:：]?\s*[￥¥]?\s*(-?(?:\d{1,3}(?:,\d{3}){1,2}|\d{1,8})(?:\.\d{1,4})?)(?![\d.,])\s*(元|度|kWh|千瓦时)?/i;
        for (var i = 0; i < all.length; i++) {
            var el = all[i], text = clean(el.textContent);
            if (!text || text.length > 180 || !shown(el) || /校园卡|充值金额|支付金额/.test(text)) continue;
            if ((text.match(/电费余额|剩余电费|剩余金额|剩余电量|剩余电度|剩余购电量/g) || []).length > 1) continue;
            var match = pattern.exec(text);
            if (!match) {
                var values = el.querySelectorAll('input[readonly]');
                if (values.length === 1) match = pattern.exec(text + ' ' + clean(values[0].value));
            }
            if (!match) continue;
            var raw = match[3].replace(/,/g, '');
            if (!/^-?\d{1,8}(?:\.\d{1,4})?$/.test(raw)) continue;
            if (!match[4] && !match[2] && /^[A-Za-z\u3400-\u9fff￥¥]/.test(text.slice(match.index + match[0].length).trim())) continue;
            if (match[1] === '剩余金额') {
                var scope = el, electricity = false;
                for (var up = 0; up < 3 && scope && scope !== document.body; up++, scope = scope.parentElement) {
                    var context = clean(scope.textContent);
                    if (context.length < 180 && /电费|电量/.test(context) && !/校园卡|一卡通/.test(context)) electricity = true;
                }
                if (!electricity) continue;
            }
            var unit = match[4] || match[2] || (/电量|电度/.test(match[1]) ? '度' : '元');
            unit = /^(度|kwh|千瓦时)$/i.test(unit) ? '度' : '元';
            var result = {value: String(Number(raw)), unit: unit};
            (unit === '元' ? money : energy)[result.value] = result;
        }
        var candidates = Object.keys(money).length ? money : energy, keys = Object.keys(candidates);
        return keys.length === 1 ? candidates[keys[0]] : null;
    }
    /** The school's own next-step button; matches that exact label only, never recharge controls. */
    function nextStep() {
        var all = document.querySelectorAll('a, button, input[type=button], input[type=submit], div, span, p, li');
        var found = null, depth = -1;
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            var label = String(el.tagName).toUpperCase() === 'INPUT' ? clean(el.value) : clean(el.textContent);
            if (label !== '下一步' || !shown(el)) continue;
            var level = 0;
            for (var up = el; up; up = up.parentElement) level++;
            if (level >= depth) { found = el; depth = level; }
        }
        return found;
    }
    /** Hides the school's payment controls; selection mode never offers them. */
    function hidePayment() {
        var controls = document.querySelectorAll('button, input[type=submit], a');
        for (var c = 0; c < controls.length; c++) {
            if (/^(充值|充值电费|立即充值|缴费|立即缴费|支付|确认支付)$/.test(clean(controls[c].textContent || controls[c].value)))
                controls[c].style.display = 'none';
        }
    }
    /** Fallback labels for the three top levels when nothing is saved yet (the school's own wording). */
    var DEFAULTS = {elesys: ['新电费缴纳'], school: ['奉贤校区'], area: ['住宿区']};
    function wantedLevel(role, saved) {
        var rows = saved && saved.fields;
        if (Array.isArray(rows)) for (var i = 0; i < rows.length; i++) {
            if (rows[i].role !== role) continue;
            var value = clean(rows[i].value);
            if (value && value !== '-1') return {value: value, titles: []};
        }
        return {value: '', titles: DEFAULTS[role] || []};
    }
    /** 'done' | 'picked' | 'wait' | 'missing' | 'failed' for one cascading level. */
    function pick(role, want) {
        var input = field(role);
        if (!input) return 'missing';
        var current = selected(role, input);
        if (current && (want.value ? current.value === want.value : want.titles.indexOf(current.title) >= 0)) return 'done';
        var $ = window.jQuery, picker = $ && $(input).data('weui-select');
        if (!picker || !picker.config || typeof picker.updateInputValue !== 'function') return 'wait';
        var items = picker.config.items || [], hit = null;
        for (var i = 0; i < items.length && !hit; i++) {
            if (want.value ? String(items[i].value) === want.value : want.titles.indexOf(clean(items[i].title)) >= 0) hit = items[i];
        }
        if (!hit) return 'wait';
        try { picker.updateInputValue($([hit.value]), [hit.title]); } catch (error) { return 'failed'; }
        return 'picked';
    }
    if (plan.action === 'choose') {
        // Fills only the three top levels and lets the user pick 楼栋/楼层/房间; the room is saved as
        // soon as the school page reports a complete selection that differs from the stored one.
        hidePayment();
        // The room is saved automatically, so the school's own 下一步 (which leads to the payment
        // page) is not offered while selecting.
        var forward = nextStep();
        if (forward) forward.style.display = 'none';
        var state = window.__xinxinElectricityPage;
        if (state && state.status !== 'pending') return JSON.stringify(state);
        var saved = plan.room && Array.isArray(plan.room.fields) ? plan.room : null;
        state = window.__xinxinElectricityPage = {status: 'pending', stage: 'prefill'};
        var started = Date.now(), level = 0, empty = 0, levels = ['elesys', 'school', 'area'];
        function done(status, reason) { state.status = status; state.reason = reason || ''; }
        (function watch() {
            if (window.__xinxinElectricityPage !== state || state.status !== 'pending') return;
            if (!trusted()) return done('failed', 'moved');
            if (document.querySelector('input[type=password]')) return done('failed', 'login');
            if (Date.now() - started > 600000) return done('failed', 'timeout');
            var $ = window.jQuery;
            if ($ && $.active > 0) return setTimeout(watch, 300);
            if (!field('room')) {
                if (++empty < 25) return setTimeout(watch, 400);
                return done('failed', 'page');
            }
            // Auto-filling is best effort: after ten seconds the user's own choice wins.
            if (level < levels.length && Date.now() - started < 10000) {
                var picked = pick(levels[level], wantedLevel(levels[level], saved));
                if (picked === 'failed') return done('failed', 'selector');
                if (picked === 'picked') { level++; return setTimeout(watch, 300); }
                if (picked === 'done' || picked === 'missing') { level++; return setTimeout(watch, 200); }
                return setTimeout(watch, 300);
            }
            level = levels.length;
            state.stage = 'room';
            var chosen = room();
            if (chosen) { state.room = chosen; return done('complete'); }
            setTimeout(watch, 400);
        })();
        return JSON.stringify(state);
    }
    if (plan.action === 'capture') {
        var chosen = room();
        return JSON.stringify(chosen ? {status: 'complete', room: chosen} : fail('room'));
    }
    if (plan.action !== 'restore' && plan.action !== 'balance') return JSON.stringify(fail('action'));
    if (!plan.room || !Array.isArray(plan.room.fields) || !plan.room.fields.length || plan.room.fields.length > 6)
        return JSON.stringify(fail('room'));
    var read = plan.action === 'balance', expected = wantedRoom(plan.room);
    var state = window.__xinxinElectricityPage;
    if (state && state.status !== 'pending') return JSON.stringify(state);
    if (!state && read && !field('room')) {
        // The school may keep the queried room for the session: read the result page directly.
        var already = balances();
        if (already) {
            var shownAlready = roomOnPage();
            if (!expected || shownAlready !== expected) return JSON.stringify(fail('room'));
            return JSON.stringify({status: 'complete', room: plan.room, balance: already, page: 'result'});
        }
    }
    state = window.__xinxinElectricityPage = {status: 'pending', stage: 'room', page: 'selection'};
    var started = Date.now(), index = 0, stable = '', same = 0, asked = false, empty = 0;
    function finish(status, reason) { state.status = status; state.reason = reason || ''; }
    (function step() {
        if (window.__xinxinElectricityPage !== state || state.status !== 'pending') return;
        if (!trusted()) return finish('failed', 'moved');
        if (document.querySelector('input[type=password]')) return finish('failed', 'login');
        if (Date.now() - started > 45000) return finish('failed', 'timeout');
        var $ = window.jQuery;
        if ($ && $.active > 0) return setTimeout(step, 300);
        if (!field('room')) {
            // The school's result page (fresh document, or swapped in place after 下一步): it carries
            // the room and a labelled balance instead of the selectors.
            var result = balances();
            if (result) {
                var shownResult = roomOnPage();
                if (!expected || shownResult !== expected) return finish('failed', 'room');
                state.room = plan.room;
                state.balance = result;
                state.page = 'result';
                return finish('complete');
            }
            if (++empty < 25) return setTimeout(step, 400);
            return finish('failed', 'page');
        }
        if (index < plan.room.fields.length) {
            var wanted = plan.room.fields[index], input = field(wanted.role);
            if (!input || input.id !== wanted.id) return finish('failed', 'room');
            var current = selected(wanted.role, input);
            if (current && current.value === String(wanted.value)) { index++; return setTimeout(step, 300); }
            try { if (String(input.tagName).toUpperCase() === 'SELECT') {
                var options = Array.prototype.filter.call(input.options, function (option) { return String(option.value) === String(wanted.value); });
                if (options.length !== 1) return setTimeout(step, 300);
                input.value = String(wanted.value); input.dispatchEvent(new Event('change', {bubbles: true}));
            } else {
                var picker = $ && $(input).data('weui-select');
                if (!picker || !picker.config || typeof picker.updateInputValue !== 'function') return setTimeout(step, 300);
                var items = (picker.config.items || []).filter(function (item) { return String(item.value) === String(wanted.value); });
                if (items.length !== 1) return setTimeout(step, 300);
                picker.updateInputValue($([items[0].value]), [items[0].title]);
            } } catch (error) { return finish('failed', 'selector'); }
            return setTimeout(step, 300);
        }
        var selectedRoom = room();
        if (!selectedRoom || key(selectedRoom) !== key(plan.room)) return finish('failed', 'room');
        state.room = plan.room;
        if (plan.action === 'restore') return finish('complete');
        var balance = balances();
        var signature = balance ? JSON.stringify(balance) : '';
        same = signature && signature === stable ? same + 1 : 0; stable = signature;
        if (same >= 2) {
            var shownRoom = roomOnPage();
            if (shownRoom && expected && shownRoom !== expected) return finish('failed', 'room');
            state.balance = balance;
            return finish('complete');
        }
        if (!balance && !asked) {
            // Submitting the school's own 下一步 performs the read-only query; the amount and the
            // recharge button stay untouched.
            var button = nextStep();
            asked = true;
            stable = '';
            same = 0;
            if (button) {
                state.stage = 'query';
                try { button.click(); } catch (error) { return finish('failed', 'query'); }
            }
            return setTimeout(step, 500);
        }
        setTimeout(step, 300);
    })();
    return JSON.stringify(state);
})()
