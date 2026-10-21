/* 在学校教务系统里直接读取成绩、考试安排。只在已登录的教务页面运行，
   请求与学校页面同源、带本机会话 Cookie，结果交给原生解析后保存在本机。 */
(function (plan) {
    var cfg = {};
    try { cfg = JSON.parse(plan || '{}'); } catch (e) { cfg = {}; }
    var service = cfg.service === 'exams' ? 'exams' : 'grades';
    var state = {status: 'pending', service: service, page: 0, pages: 1, items: []};
    window.__xinxinAcademic = state;

    function finish(status, reason) {
        state.status = status;
        state.reason = reason || '';
        state.items = state.items || [];
        return status;
    }
    function hostOk() {
        return location.protocol === 'https:' && /^(jwxt|yjsgl)\.sit\.edu\.cn$/.test(location.hostname);
    }
    function domTerm() {
        var xnm = document.querySelector('#xnm'), xqm = document.querySelector('#xqm');
        var year = xnm && xnm.value ? String(xnm.value) : '';
        var term = xqm && xqm.value ? String(xqm.value) : '';
        return /^\d{4}$/.test(year) && /^\d{1,2}$/.test(term) ? {xnm: year, xqm: term} : null;
    }
    function datedTerm() {
        var now = new Date(), month = now.getMonth() + 1, year = now.getFullYear();
        return month >= 8 || month <= 1 ? {xnm: String(month <= 1 ? year - 1 : year), xqm: '3'} : {xnm: String(year - 1), xqm: '12'};
    }
    function pickTerm(supplied, explicit) {
        // 用户明确选的学期优先级最高，其次页面上的学年学期，最后按当前日期推算。
        if (explicit && /^\d{4}$/.test(String(explicit.xnm || '')) && /^\d{1,2}$/.test(String(explicit.xqm || ''))) {
            return {xnm: String(explicit.xnm), xqm: String(explicit.xqm)};
        }
        var direct = domTerm();
        if (direct) return direct;
        // 学校页面没有学年学期下拉时按当前日期推算“本学期”。
        var now = datedTerm();
        // 课表里记的学期只在不早于一年时才采用，避免用过期课表的学期查成绩。
        if (supplied && /^\d{4}$/.test(String(supplied.xnm || '')) && /^\d{1,2}$/.test(String(supplied.xqm || ''))) {
            if (Math.abs(parseInt(supplied.xnm, 10) - parseInt(now.xnm, 10)) <= 1) {
                return {xnm: String(supplied.xnm), xqm: String(supplied.xqm)};
            }
        }
        return now;
    }
    function body(term, page) {
        var parts = ['xnm=' + term.xnm, 'xqm=' + term.xqm, '_search=false', 'nd=' + Date.now(),
            'queryModel.showCount=100', 'queryModel.currentPage=' + page,
            'queryModel.sortName=', 'queryModel.sortOrder=asc'];
        if (service === 'exams') parts.push('ksmcdmb_id=', 'kch=', 'kcbmc=', 'ksrq=');
        return parts.join('&');
    }
    function request(endpoint, term, page) {
        return fetch(endpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest'
            },
            credentials: 'same-origin',
            cache: 'no-store',
            body: body(term, page)
        }).then(function (response) {
            if (!response.ok) throw new Error('http');
            return response.text();
        }).then(function (text) {
            var data;
            try { data = JSON.parse(text); } catch (e) { throw new Error('login'); }
            if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error('login');
            return data;
        });
    }
    function keysOf(row) { return row && typeof row === 'object' ? Object.keys(row) : []; }
    function run(endpoint, term, page) {
        state.page = page;
        request(endpoint, term, page).then(function (data) {
            var rows = Array.isArray(data.items) ? data.items : (Array.isArray(data.rows) ? data.rows : []);
            if (!state.shape) state.shape = {keys: keysOf(data), total: String(data.totalResult || data.total || ''), row: keysOf(rows[0])};
            if (!state.keys) state.keys = keysOf(rows[0]);
            for (var i = 0; i < rows.length; i++) state.items.push(rows[i]);
            var total = parseInt(data.totalResult || data.total || '0', 10) || 0;
            state.pages = Math.max(1, Math.ceil(Math.min(total, 600) / 100));
            if (data.xsxx && !state.student) state.student = {name: data.xsxx.XM || '', number: data.xsxx.XH || ''};
            if (page < state.pages && state.items.length > 0) { run(endpoint, term, page + 1); return; }
            finish('complete', state.items.length ? '' : 'empty');
        }).catch(function (error) {
            finish('failed', error && error.message === 'login' ? 'login' : 'http');
        });
    }
    try {
        if (!hostOk()) return finish('failed', 'blocked');
        var endpoint = new URL(cfg.endpoint || '', location.href);
        if (endpoint.origin !== location.origin) return finish('failed', 'blocked');
        if (!/^\/jwglxt\/(cjcx|kwgl)\//.test(endpoint.pathname)) return finish('failed', 'endpoint');
        var term = pickTerm(cfg.term, cfg.explicitTerm);
        if (!term) return finish('failed', 'no-term');
        state.term = term;
        var query = endpoint.href + (endpoint.search ? '&' : '?') + 'doType=query&gnmkdm=' + encodeURIComponent(cfg.gnmkdm || '');
        run(query, term, 1);
        return 'started';
    } catch (e) {
        return finish('failed', 'script');
    }
})(typeof __ACADEMIC_PLAN__ === 'string' ? __ACADEMIC_PLAN__ : '');
