// 在「学分认定管理」页（xg.sit.edu.cn/apply-manage/xsrd）读取本人全部加分记录：
// 先读完当前筛选的每一页，再逐个学年点一遍，把每个学年的每一页都读进来。
// 页面是移动端 H5：每条记录是一张卡，卡内是「标签：值」逐行排列；
// 这里不依赖具体标签元素，只按可见文字里的「标签：」切段，页面改版时容错。
(function () {
    var state = window.__xinxinRecords = {
        status: 'pending', reason: '', records: [], total: '', detail: '', phase: '准备中',
        pager: '', pages: 0, capped: false, tail: ''
    };
    var LABELS = ['姓名', '学号', '性别', '年级', '学院', '专业', '班级', '项目名称',
        '学分大类', '学分类别', '活动名称', '学年', '学期', '学分来源', '认定学分', '创建时间'];
    var SPLIT = new RegExp('(' + LABELS.join('|') + ')\\s*[:：]', 'g');
    var YEAR = /^20\d{2}-20\d{2}$/;
    var END = Date.now() + 150000;

    function textOf(el) {
        return String((el && el.textContent) || '').replace(/[\s\u00a0]+/g, ' ').trim();
    }
    function count(text, part) { return text.split(part).length - 1; }
    function shown(el) {
        for (var node = el; node; node = node.parentElement) {
            if (node.hidden) return false;
            var style = null;
            try { style = window.getComputedStyle ? window.getComputedStyle(node) : null; } catch (e) { style = null; }
            if (style && (style.display === 'none' || style.visibility === 'hidden')) return false;
        }
        return true;
    }
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
    function leaves(doc) {
        var out = [];
        var all = doc && doc.body ? doc.body.getElementsByTagName('*') : [];
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            if (!el.children || !el.children.length) out.push(el);
        }
        return out;
    }
    /** 一张卡：以「活动名称」为锚点，往上找最小的同时带「认定学分」和类别标签的祖先。 */
    function cards(doc) {
        var all = doc.body ? doc.body.getElementsByTagName('*') : [], out = [];
        for (var i = 0; i < all.length; i++) {
            var el = all[i], text = textOf(el);
            if (text.indexOf('活动名称') < 0) continue;
            if (count(text, '活动名称') !== 1) continue;
            if (!shown(el)) continue;
            var node = el;
            while (node && textOf(node).indexOf('认定学分') < 0) node = node.parentElement;
            if (!node || count(textOf(node), '活动名称') !== 1) continue;
            var rich = node, up = node;
            while (up) {
                var one = textOf(up);
                if (count(one, '活动名称') !== 1) break;
                if (one.indexOf('学分大类') >= 0 || one.indexOf('学分类别') >= 0) { rich = up; break; }
                up = up.parentElement;
            }
            out.push(rich);
        }
        var unique = [];
        for (var u = 0; u < out.length; u++) if (unique.indexOf(out[u]) < 0) unique.push(out[u]);
        var keep = [];
        for (var k = 0; k < unique.length; k++) {
            var inner = false;
            for (var j = 0; j < unique.length; j++)
                if (j !== k && unique[k].contains(unique[j])) { inner = true; break; }
            if (!inner) keep.push(unique[k]);
        }
        return keep;
    }
    function fields(text) {
        var hits = [], m;
        SPLIT.lastIndex = 0;
        while ((m = SPLIT.exec(text))) hits.push({key: m[1], start: m.index, after: SPLIT.lastIndex});
        var found = {};
        for (var i = 0; i < hits.length; i++) {
            var end = i + 1 < hits.length ? hits[i + 1].start : text.length;
            var value = text.slice(hits[i].after, end).trim();
            if (!found[hits[i].key]) found[hits[i].key] = value;
        }
        return found;
    }
    function clean(value) {
        var text = String(value === undefined || value === null ? '' : value).replace(/[\s\u00a0]+/g, ' ').trim();
        return text.length > 120 ? text.substring(0, 120) : text;
    }
    /** 认定学分：允许带「分/学分」后缀，保留两位小数。 */
    function credit(value) {
        var text = clean(value).replace(/(?:学分|分)$/, '').trim();
        var m = /^([+-]?\d{1,3}(?:\.\d{1,4})?)$/.exec(text);
        if (!m) return '';
        var n = Number(m[1]);
        if (!isFinite(n) || n < -99 || n > 999) return '';
        return n.toFixed(2);
    }
    function stamp(value) {
        var text = clean(value);
        var m = /(\d{4})[-\/.](\d{1,2})[-\/.](\d{1,2})(?:[ T](\d{1,2}):(\d{2}))?/.exec(text);
        if (!m) return '';
        var pad = function (v) { return String(v).length < 2 ? '0' + v : String(v); };
        return m[1] + '-' + pad(m[2]) + '-' + pad(m[3]) + (m[4] ? ' ' + pad(m[4]) + ':' + m[5] : '');
    }
    function read(doc) {
        var list = cards(doc), out = [], seen = {};
        for (var i = 0; i < list.length; i++) {
            var f = fields(textOf(list[i]));
            var name = clean(f['活动名称']);
            var value = credit(f['认定学分']);
            if (!name || !value) continue;
            var record = {
                activity: name, credit: value, time: stamp(f['创建时间']),
                dl: clean(f['学分大类']), lb: clean(f['学分类别']),
                project: clean(f['项目名称']), year: clean(f['学年']),
                term: clean(f['学期']), source: clean(f['学分来源'])
            };
            var key = record.activity + '|' + record.credit + '|' + record.time;
            if (seen[key]) continue;
            seen[key] = true;
            out.push(record);
        }
        return out;
    }
    function finish(status, reason) {
        if (state.status !== 'pending') return;
        state.status = status;
        state.reason = reason || '';
    }

    var collected = [], seenKeys = Object.create(null), pagesRead = 0, yearsRead = 0;
    var startHref = location.href;

    function keyOf(r) { return r.activity + '|' + r.credit + '|' + r.time; }
    function absorb(list) {
        var added = 0;
        for (var i = 0; i < list.length; i++) {
            var key = keyOf(list[i]);
            if (seenKeys[key]) continue;
            seenKeys[key] = true;
            collected.push(list[i]);
            added++;
        }
        if (added) state.records = collected.slice();
        return added;
    }
    /** 页面自己写的「共 27 条」，用来判断有没有读全。 */
    function totalOf() {
        var list = docs();
        for (var i = 0; i < list.length; i++) {
            if (!list[i].body) continue;
            var m = /共\s*(\d{1,5})\s*条/.exec(textOf(list[i].body));
            if (m) return m[1];
        }
        return '';
    }
    function readNow() {
        var list = docs(), best = [];
        for (var i = 0; i < list.length; i++) {
            var one = null;
            try { one = read(list[i]); } catch (e) { state.detail = String((e && e.message) || e); }
            if (one && one.length > best.length) best = one;
        }
        return best;
    }
    function clickable(el) {
        var node = el;
        for (var up = 0; up < 4 && node; up++) {
            var tag = String(node.tagName || '').toUpperCase();
            if (tag === 'BUTTON' || tag === 'A' || tag === 'LI' || tag === 'SPAN' || node.onclick) return node;
            node = node.parentElement;
        }
        return null;
    }
    function usable(target) {
        if (!target) return false;
        if (target.disabled || /disabled|is-disabled/i.test(String(target.className || ''))) return false;
        var href = target.getAttribute ? String(target.getAttribute('href') || '') : '';
        if (String(target.tagName || '').toUpperCase() === 'A' && href && href !== '#'
            && href.indexOf('javascript:') !== 0) return false;
        return true;
    }
    /** 页面上可见的学年按钮，按显示顺序返回。 */
    function yearChips() {
        var list = docs(), out = [], seen = {};
        for (var d = 0; d < list.length; d++) {
            var all = leaves(list[d]);
            for (var i = 0; i < all.length; i++) {
                var text = textOf(all[i]);
                if (!YEAR.test(text) || seen[text] || !shown(all[i])) continue;
                var target = clickable(all[i]);
                if (!usable(target)) continue;
                seen[text] = true;
                out.push({label: text, node: target});
            }
        }
        return out;
    }
    function leavesIn(node, out) {
        out = out || [];
        var kids = node.children || [];
        for (var i = 0; i < kids.length; i++) {
            var kid = kids[i];
            if (kid.children && kid.children.length) leavesIn(kid, out);
            else out.push(kid);
        }
        return out;
    }
    /** 分页区：找含「前往…页 / 共 N 条」、且真有点击控件的最小元素（箭头可能只是图标，没有文字）。 */
    function pagerRoot() {
        var list = docs(), best = null, bestLen = 100000;
        for (var d = 0; d < list.length; d++) {
            var all = list[d].body ? list[d].body.getElementsByTagName('*') : [];
            for (var i = 0; i < all.length; i++) {
                var el = all[i], text = textOf(el);
                if (!text || text.length > 600 || text.length >= bestLen) continue;
                if (!/前往/.test(text) && !/共\s*\d{1,5}\s*条/.test(text)) continue;
                var hasInput = false;
                try { hasInput = el.getElementsByTagName && el.getElementsByTagName('input').length > 0; } catch (e) { }
                var looks = text.indexOf('>') >= 0 || text.indexOf('›') >= 0 || text.indexOf('»') >= 0
                    || hasInput || /page|pager|pagination/i.test(String(el.className || ''));
                if (!looks) continue;
                best = el;
                bestLen = text.length;
            }
        }
        return best;
    }
    /** 有的组件只监听 mousedown/mouseup，光 .click() 不生效，所以整串都发一遍。 */
    function fire(el) {
        var done = false;
        try {
            ['mousedown', 'mouseup', 'click'].forEach(function (type) {
                el.dispatchEvent(new MouseEvent(type, {bubbles: true, cancelable: true, view: window}));
                done = true;
            });
        } catch (e) { done = false; }
        if (!done) { try { el.click(); } catch (e2) { } }
    }
    /** 翻页控件：优先用分页区里的「>」箭头，其次页码数字，最后退化成全页搜索。 */
    function nextControl() {
        var root = pagerRoot();
        state.detail = 'pager=' + (root ? textOf(root).slice(0, 60) : 'none');
        if (root) state.pager = String(root.outerHTML || textOf(root)).slice(0, 900);
        else if (document.body) state.tail = String(document.body.innerHTML || '').slice(-4000);
        if (root) {
            var inner = leavesIn(root);
            for (var i = 0; i < inner.length; i++) {
                var arrow = textOf(inner[i]);
                if (arrow !== '>' && arrow !== '›' && arrow !== '»') continue;
                if (!shown(inner[i])) continue;
                var hit = clickable(inner[i]);
                if (usable(hit)) return hit;
            }
            var wanted = String(pagesRead + 2);
            for (var j = 0; j < inner.length; j++) {
                if (textOf(inner[j]) !== wanted || !shown(inner[j])) continue;
                var number = clickable(inner[j]);
                if (usable(number)) return number;
            }
            // 图标字体没有文字时，退一步认 class 里的 next/right/forward。
            for (var m = 0; m < inner.length; m++) {
                var icon = inner[m];
                var cls = String(icon.className || '') + ' '
                    + String((icon.parentElement && icon.parentElement.className) || '');
                if (!/next|right|forward/i.test(cls) || !shown(icon)) continue;
                var hitIcon = clickable(icon);
                if (usable(hitIcon)) return hitIcon;
            }
        }
        var list = docs();
        for (var d = 0; d < list.length; d++) {
            var all = leaves(list[d]);
            for (var k = 0; k < all.length; k++) {
                var text = textOf(all[k]);
                var named = text === '下一页' || text === '下页' || text === '加载更多' || text === '查看更多';
                var numbered = pagesRead > 0 && text === String(pagesRead + 1);
                if (!named && !numbered) continue;
                if (!shown(all[k])) continue;
                var target = clickable(all[k]);
                if (usable(target)) return target;
            }
        }
        return null;
    }
    /** 等页面稳定：连续两次读到的记录完全一样才算稳定。 */
    function waitStable(cb, budget) {
        var last = null, same = 0, ticks = 0, started = Date.now();
        (function tick() {
            if (state.status !== 'pending') return;
            ticks++;
            if (Date.now() >= END || ticks > 40) return cb(last !== null && JSON.parse(last).length > 0);
            var now = readNow();
            var signature = JSON.stringify(now);
            same = (last !== null && signature === last) ? same + 1 : 0;
            last = signature;
            if (same >= 2 && now.length) return cb(true);
            if (ticks > 6 && Date.now() - started > budget) return cb(now.length > 0);
            setTimeout(tick, 200);
        })();
    }
    /** 兜底：分页器有「前往 N 页」输入框时，直接填页码并按回车。 */
    function typePage(wanted) {
        var list = docs();
        for (var d = 0; d < list.length; d++) {
            if (!list[d].getElementsByTagName) continue;
            var inputs = list[d].getElementsByTagName('input');
            for (var i = 0; i < inputs.length; i++) {
                var input = inputs[i];
                if (!shown(input)) continue;
                var label = '', node = input.parentElement;
                for (var up = 0; up < 3 && node; up++) { label += textOf(node); node = node.parentElement; }
                if (label.indexOf('前往') < 0 && label.indexOf('页') < 0) continue;
                try {
                    input.value = String(wanted);
                    ['input', 'change', 'keyup'].forEach(function (type) {
                        input.dispatchEvent(new Event(type, {bubbles: true}));
                    });
                    input.dispatchEvent(new KeyboardEvent('keydown', {bubbles: true, key: 'Enter', keyCode: 13}));
                } catch (e) { }
                return true;
            }
        }
        return false;
    }
    /** 点了翻页后，等列表内容真的变了再继续；没变就说明翻不动，收工。 */
    function waitChange(previous, cb, budget) {
        var ticks = 0, started = Date.now();
        (function tick() {
            if (state.status !== 'pending') return;
            ticks++;
            var now = readNow();
            if (now.length && JSON.stringify(now) !== previous) return cb(true);
            if (ticks > 30 || Date.now() - started > budget) return cb(false);
            setTimeout(tick, 200);
        })();
    }
    /** 先回到第 1 页，避免从第 3 页开始读、漏掉前面几页。 */
    function firstPage(cb) {
        var root = pagerRoot();
        if (!root) return cb();
        var inner = leavesIn(root), hit = null;
        for (var i = 0; i < inner.length; i++) {
            if (textOf(inner[i]) !== '1' || !shown(inner[i])) continue;
            var one = clickable(inner[i]);
            if (usable(one)) { hit = one; break; }
        }
        if (!hit) {
            if (typePage(1)) setTimeout(cb, 500); else cb();
            return;
        }
        fire(hit);
        setTimeout(cb, 700);
    }
    /** 跳到指定页：先点页码，再试「前往」输入框，最后才点箭头。 */
    function gotoPage(wanted, before, cb) {
        var root = pagerRoot();
        if (root) {
            var inner = leavesIn(root);
            for (var i = 0; i < inner.length; i++) {
                if (textOf(inner[i]) !== String(wanted) || !shown(inner[i])) continue;
                var chip = clickable(inner[i]);
                if (!usable(chip)) continue;
                fire(chip);
                return waitChange(before, cb, 5000);
            }
        }
        if (typePage(wanted)) return waitChange(before, cb, 5000);
        var next = nextControl();
        if (next) {
            fire(next);
            return waitChange(before, cb, 5000);
        }
        return cb(false);
    }
    /** 读完当前筛选下的所有页：按「共 N 条 ÷ 每页条数」算出页数，逐页跳过去。 */
    function readPages(done) {
        var guard = 0, target = 0;
        function step() {
            waitStable(function () {
                if (state.status !== 'pending') return;
                var now = readNow();
                if (!now.length) return done();
                var before = JSON.stringify(now);
                absorb(now);
                var total = totalOf();
                if (total) state.total = total;
                if (!target && total) target = Math.min(60, Math.ceil(Number(total) / Math.max(1, now.length)));
                if (!pagesRead) pagesRead = 1;
                if (++guard > 60) { state.capped = true; return done(); }
                if (target && pagesRead >= target) return done();
                state.phase = '读取第 ' + (pagesRead + 1) + ' 页';
                gotoPage(pagesRead + 1, before, function (changed) {
                    if (!changed) return done();
                    pagesRead++;
                    setTimeout(step, 120);
                });
            }, 5000);
        }
        firstPage(function () { step(); });
    }
    /** 逐个学年点一遍：每个学年都重新翻完所有页。 */
    function sweep(done) {
        var chips = yearChips();
        if (!chips.length) { state.phase = '完成'; return done(); }
        var labels = [];
        for (var i = 0; i < chips.length; i++) labels.push(chips[i].label);
        (function step(index) {
            if (index >= labels.length || Date.now() >= END) { state.phase = '完成'; return done(); }
            var current = yearChips(), target = null;
            for (var k = 0; k < current.length; k++) if (current[k].label === labels[index]) target = current[k].node;
            if (!target) return step(index + 1);
            yearsRead++;
            state.phase = '读取 ' + labels[index];
            pagesRead = 0;
            try { target.click(); } catch (e) { }
            setTimeout(function () {
                readPages(function () { step(index + 1); });
            }, 700);
        })(0);
    }

    function finishUp(attempt) {
        var total = state.total ? Number(state.total) : 0;
        if (total && collected.length < total && (attempt || 0) < 2) {
            state.phase = '补读没读到的页';
            return readPages(function () { finishUp((attempt || 0) + 1); });
        }
        if (!collected.length) return finish('failed', 'empty');
        state.records = collected.slice();
        state.detail = 'pages=' + pagesRead + ' years=' + yearsRead
            + ' got=' + collected.length + (state.capped ? ' capped' : '');
        state.status = 'complete';
    }

    function start() {
        state.phase = '等待列表加载';
        var waited = 0;
        (function ready() {
            if (state.status !== 'pending') return;
            var total = totalOf();
            if (total) state.total = total;
            // 先等页面把「共 N 条」或记录渲染出来，避免在列表还没加载时就开读。
            if (!total && waited < 40 && !readNow().length) {
                waited++;
                setTimeout(ready, 400);
                return;
            }
            waitStable(function (ok) {
                if (state.status !== 'pending') return;
                if (!ok) return finish('failed', 'empty');
                state.phase = '读取当前列表';
                readPages(function () {
                    sweep(function () { finishUp(0); });
                });
            }, 9000);
        })();
    }

    if (location.protocol !== 'https:' || location.hostname !== 'xg.sit.edu.cn'
        || (location.port && location.port !== '443') || location.username || location.password)
        return finish('failed', 'blocked');
    if (document.querySelector && document.querySelector('input[type=password]')) return finish('failed', 'login');
    if (location.href !== startHref) return finish('failed', 'moved');
    start();
})();
