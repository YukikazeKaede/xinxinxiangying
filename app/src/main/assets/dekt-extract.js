// 在已登录的第二课堂学分页面（xg.sit.edu.cn）里读取学分明细。
// 页面结构未经核对：这里只用「分数单元格 + 同级名称」的通用启发式，
// 读不到就返回原因，由应用提示用户展开页面后重试。
(function () {
    var state = window.__xinxinDekt = {
        status: 'pending', reason: '', earned: '', required: '', updated: '', rows: []
    };
    var SCORE = /^(\d{1,3}(?:\.\d{1,2})?)\s*\/\s*(\d{1,3}(?:\.\d{1,2})?)(?:\s*~\s*(\d{1,3}(?:\.\d{1,2})?))?$/;
    var LINE = /^(.{1,24}?)\s*[：:]\s*(\d{1,3}(?:\.\d{1,2})?)$/;
    var SKIP = /得分|积分|学时|年级|学院|专业|班级|学号|姓名|时间|日期/;
    var TROUBLE = /\d\s*\/\s*\d/;
    var END = Date.now() + 8000;
    var tries = 0;

    function textOf(el) {
        return String((el && el.textContent) || '').replace(/[\s\u00a0]+/g, ' ').trim();
    }
    function leaf(el) { return !el.children || el.children.length === 0; }
    function shown(el) {
        for (var node = el; node; node = node.parentElement) {
            if (node.hidden) return false;
            var style = null;
            try { style = window.getComputedStyle ? window.getComputedStyle(node) : null; } catch (e) { style = null; }
            if (style && (style.display === 'none' || style.visibility === 'hidden')) return false;
        }
        return true;
    }
    function cleanName(t) {
        return String(t || '').replace(/[›»><⌄⌃▾▴▼▲]/g, '').replace(/[\s\u00a0]+/g, ' ').trim();
    }
    function leftOf(el) {
        try { var box = el.getBoundingClientRect(); return box ? box.left : 0; } catch (e) { return 0; }
    }
    // 分数单元格旁边那一格文字就是名称；名称格没有子分数，也不该过长。
    function labelOf(cell) {
        var node = cell;
        for (var up = 0; up < 4 && node && node.parentElement; up++) {
            var parent = node.parentElement, kids = parent.children, best = null;
            if (kids && kids.length >= 2) {
                for (var i = 0; i < kids.length; i++) {
                    var sib = kids[i];
                    if (sib === node) continue;
                    if (node.contains && node.contains(sib)) continue;
                    if (sib.contains && sib.contains(node)) continue;
                    var t = textOf(sib);
                    if (!t || SCORE.test(t) || TROUBLE.test(t) || t.length > 40) continue;
                    if (!best || textOf(best).length < t.length) best = sib;
                }
                if (best) return best;
            }
            node = parent;
        }
        return null;
    }
    function totals(body) {
        var all = textOf(body), m = null;
        m = /累计获得[^\d]{0,16}(\d{1,3}(?:\.\d{1,2})?)\s*\/\s*(\d{1,3}(?:\.\d{1,2})?)/.exec(all);
        if (!m) m = /累计(?:学分|获得学分)[^\d]{0,16}(\d{1,3}(?:\.\d{1,2})?)\s*\/\s*(\d{1,3}(?:\.\d{1,2})?)/.exec(all);
        if (!m) return {earned: '', required: ''};
        return {earned: m[1], required: m[2]};
    }
    function stamp(body) {
        var all = textOf(body);
        var m = /更新(?:于|时间|日期)[^\d]{0,8}(\d{1,4}[\/\-.]\d{1,2}[\/\-.]\d{1,4}\s+\d{1,2}:\d{2}(?::\d{2})?)/.exec(all);
        return m ? m[1] : '';
    }
    function read(doc) {
        var body = doc.body;
        var all = body ? body.getElementsByTagName('*') : [], rows = [], seen = {};
        for (var i = 0; i < all.length; i++) {
            var cell = all[i];
            if (!leaf(cell)) continue;
            var value = textOf(cell);
            var m = SCORE.exec(value);
            if (!m) continue;
            if (!shown(cell)) continue;
            var label = labelOf(cell);
            if (!label) continue;
            var name = cleanName(textOf(label));
            if (!name) continue;
            var key = name + '|' + value;
            if (seen[key]) continue;
            seen[key] = true;
            rows.push({
                name: name,
                earned: m[1],
                required: m[2] + (m[3] ? '~' + m[3] : ''),
                left: Math.round(leftOf(label))
            });
        }
        // 明细里子项比大类缩进一档：把最靠左的一列算大类，缩进超过 8px 的算子项。
        var min = Infinity;
        for (var j = 0; j < rows.length; j++) if (rows[j].left > 0 && rows[j].left < min) min = rows[j].left;
        for (var k = 0; k < rows.length; k++) {
            rows[k].depth = (rows[k].left > 0 && min !== Infinity && rows[k].left - min > 8) ? 1 : 0;
            delete rows[k].left;
        }
        var total = totals(body);
        return {earned: total.earned, required: total.required, updated: stamp(body), rows: rows};
    }
    // 第二种页面写法：「名称 ： 分数」逐行排列，大类写成「德育合计 ： 2.40」，页尾「学分 ： 3.75」。
    // 这种情况页面上没有分母，需求留空，由界面只显示已得学分。
    function readLines(doc) {
        var body = doc.body;
        var all = body ? body.getElementsByTagName('*') : [];
        var rows = [], pending = [], earned = '', seen = {};
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            if (!shown(el)) continue;
            var whole = textOf(el);
            if (!whole || whole.length > 40) continue;
            var m = LINE.exec(whole);
            if (!m) continue;
            // 取最内层：子元素自己就已经是「名称 ： 分数」时，父元素不再重复算一次。
            var kids = el.children || [], nested = false;
            for (var c = 0; c < kids.length; c++) if (LINE.test(textOf(kids[c]))) { nested = true; break; }
            if (nested) continue;
            var name = cleanName(m[1]).replace(/[：:]+$/, '');
            if (!name || name.length > 16) continue;
            var value = m[2];
            if (/合计|小计/.test(name)) {
                var category = cleanName(name.replace(/(合计|小计)/g, ''));
                if (!category) continue;
                rows.push({name: category, earned: value, required: '', depth: 0});
                for (var p = 0; p < pending.length; p++) rows.push({name: pending[p].name, earned: pending[p].earned, required: '', depth: 1});
                pending = [];
                continue;
            }
            if (name === '学分' || name === '总学分') { earned = value; continue; }
            if (SKIP.test(name)) continue;
            if (seen[name]) continue;
            seen[name] = true;
            pending.push({name: name, earned: value});
        }
        for (var r = 0; r < pending.length; r++) rows.push({name: pending[r].name, earned: pending[r].earned, required: '', depth: 0});
        return {earned: earned, required: '', updated: stamp(body), rows: rows};
    }
    function finish(status, reason) {
        if (state.status !== 'pending') return;
        state.status = status;
        state.reason = reason || '';
    }
    // 学分页可能整块放在同源 iframe 里（外层只留顶部导航），所以把同源 iframe 的文档也一起找。
    function docs() {
        var out = [document], frames = [];
        try { frames = document.getElementsByTagName('iframe'); } catch (e) { frames = []; }
        for (var i = 0; i < frames.length; i++) {
            var doc = null;
            try { doc = frames[i].contentDocument; } catch (e) { doc = null; }
            if (doc && doc.body) out.push(doc);
        }
        return out;
    }
    function attempt() {
        if (state.status !== 'pending') return;
        tries++;
        if (document.querySelector && document.querySelector('input[type=password]')) return finish('failed', 'login');
        var list = docs(), data = null, notes = [];
        for (var d = 0; d < list.length; d++) {
            var one = null;
            try { one = read(list[d]); } catch (e) { state.detail = String((e && e.message) || e); }
            if (!one || (!one.rows.length && !one.earned)) {
                try { one = readLines(list[d]); } catch (e) { state.detail = String((e && e.message) || e); }
            }
            if (one) notes.push((one.rows.length || 0) + (one.earned ? '+' : ''));
            if (one && (one.rows.length || one.earned)) { data = one; break; }
        }
        state.detail = 'docs=' + list.length + ' got=' + notes.join(',');
        if (data && (data.rows.length > 0 || data.earned)) {
            state.earned = data.earned;
            state.required = data.required;
            state.updated = data.updated;
            state.rows = data.rows;
            state.status = 'complete';
            return;
        }
        if (tries >= 20 || Date.now() >= END) return finish('failed', 'empty');
        setTimeout(attempt, 400);
    }
    if (location.protocol !== 'https:' || location.hostname !== 'xg.sit.edu.cn'
        || (location.port && location.port !== '443') || location.username || location.password)
        return finish('failed', 'blocked');
    attempt();
})();
