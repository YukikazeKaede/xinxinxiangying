// 在「服务大厅」里自动点进「学分明细查询」，进入后由 dekt-records.js 自动读取。
// 只认明确的服务名称，点不到就放弃，交给用户手动点。
(function () {
    if (window.__xinxinEntered) return;
    var NAMES = ['学分明细查询', '学分明细', '第二课堂学分明细'];
    var tries = 0;

    function textOf(el) {
        return String((el && el.textContent) || '').replace(/[\s\u00a0]+/g, ' ').trim();
    }
    function fire(el) {
        try {
            ['mousedown', 'mouseup', 'click'].forEach(function (type) {
                el.dispatchEvent(new MouseEvent(type, {bubbles: true, cancelable: true, view: window}));
            });
        } catch (e) {
            try { el.click(); } catch (e2) { }
        }
    }
    function target() {
        var all = document.body ? document.body.getElementsByTagName('*') : [];
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            if (el.children && el.children.length) continue;
            var text = textOf(el);
            if (NAMES.indexOf(text) < 0) continue;
            var node = el;
            for (var up = 0; up < 4 && node; up++) {
                var tag = String(node.tagName || '').toUpperCase();
                if (tag === 'A' || tag === 'BUTTON' || tag === 'LI' || node.onclick) return node;
                node = node.parentElement;
            }
        }
        return null;
    }
    (function step() {
        if (window.__xinxinEntered) return;
        if (document.querySelector && document.querySelector('input[type=password]')) return;
        var el = target();
        if (el) {
            window.__xinxinEntered = true;
            fire(el);
            return;
        }
        if (++tries > 20) return;
        setTimeout(step, 400);
    })();
})();
