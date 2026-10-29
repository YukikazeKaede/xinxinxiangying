// 第二课堂活动：在学校页面里读取活动列表与活动详情。
// Java 侧把 __ACT_PLAN__ 换成 {"action":"list"}、{"action":"detail","id":"..."}
// 或 {"action":"signup","id":"..."}。
(function (plan) {
    var cfg = {};
    try { cfg = JSON.parse(plan || '{}'); } catch (e) { cfg = {}; }
    if (window.__xinxinActs && typeof window.__xinxinActs.cancel === 'function') window.__xinxinActs.cancel();
    var state = window.__xinxinActs = {
        status: 'pending', reason: '', action: cfg.action || '', list: null, detail: null, signup: null
    };

    function fail(reason) {
        state.status = 'failed';
        state.reason = reason;
        return '';
    }

    function schoolHost() {
        return location.protocol === 'https:' && location.hostname === 'xg.sit.edu.cn'
            && (!location.port || location.port === '443') && !location.username && !location.password;
    }

    function request(method, url, body) {
        var x = new XMLHttpRequest();
        x.open(method, url, false);
        if (method === 'POST') x.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        if (method === 'POST') x.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        x.send(body || null);
        if (x.status < 200 || x.status >= 300) return null;
        try { return JSON.parse(x.responseText); } catch (e) { return null; }
    }

    function clean(value) {
        return String(value === undefined || value === null ? '' : value).replace(/\s+/g, ' ').trim();
    }

    /** 学校把换行和引号存成了 HTML 实体，甚至会二次转义，所以还原两遍。 */
    function decode(value) {
        var text = String(value === undefined || value === null ? '' : value);
        for (var pass = 0; pass < 2; pass++) {
            text = text
                .replace(/&#10;/g, '\n')
                .replace(/&#13;/g, '')
                .replace(/&lt;/g, '<')
                .replace(/&gt;/g, '>')
                .replace(/&quot;/g, '"')
                .replace(/&#39;/g, "'")
                .replace(/&nbsp;/g, ' ')
                .replace(/&amp;/g, '&');
        }
        return text;
    }

    function block(value) {
        return decode(value).replace(/\r/g, '').replace(/\n{3,}/g, '\n\n').trim();
    }

    function row(hit, groupName, groupCode) {
        return {
            id: clean(hit.id),
            name: clean(hit.hdmc),
            lbdm: clean(hit.lbdm),
            lbmc: clean(hit.lbmc),
            dlmc: clean(hit.dlmc) || clean(groupName),
            dldm: clean(hit.dldm) || clean(groupCode),
            zbf: clean(hit.zbfmc) || clean(hit.zbf),
            project: clean(hit.xmmc) || clean(hit.ssxm),
            start: clean(hit.hdkssj),
            end: clean(hit.hdjssj),
            signupStart: clean(hit.hdbmkssj),
            signupEnd: clean(hit.hdbmjzsj),
            top: clean(hit.zdzt) === '1',
            year: clean(hit.xn), term: clean(hit.xq), schoolState: ''
        };
    }

    /** 官方完整列表：先读取动态筛选字典，再顺序读取所有分页；失败不交付半份缓存。 */
    function readList() {
        var xhr, filters = {}, scope = {}, selected = {}, names = Object.create(null), blocks = [], menus = [];
        var number = cfg.page === undefined ? 1 : Number(cfg.page), pageSize = 10;
        state.cancel = function () { if (state.status === 'pending') { state.status = 'cancelled'; if (xhr) xhr.abort(); } };
        function active() { return window.__xinxinActs === state && state.status === 'pending'; }
        function fetch(url, body, done) {
            if (!active()) return;
            var x = xhr = new XMLHttpRequest();
            x.open('POST', url, true); x.timeout = 15000;
            x.setRequestHeader('Content-Type', url.indexOf('/hdgl/list.zf') >= 0
                ? 'application/x-www-form-urlencoded; charset=UTF-8' : 'application/json;charset=UTF-8');
            x.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
            x.onload = function () {
                if (!active()) return;
                if (x.status === 401 || x.status === 403) { fail('login'); return; }
                if (x.status < 200 || x.status >= 300) { fail('list'); return; }
                try {
                    var answer = JSON.parse(x.responseText);
                    if (!answer || answer.code !== 0 || !answer.data) { fail('list'); return; }
                    done(answer.data);
                } catch (e) { fail('list'); }
            };
            x.onerror = x.ontimeout = function () { if (active()) fail('list'); };
            try { x.send(body); } catch (e) { if (active()) fail('list'); }
        }
        function integer(value) {
            if (typeof value !== 'number' && typeof value !== 'string' || clean(value) === '') return -1;
            var n = Number(value); return isFinite(n) && n >= 0 && Math.floor(n) === n ? n : -1;
        }
        function dictionary(data) {
            if (!data || !data.options || typeof data.options !== 'object') return [];
            var out = [], seen = Object.create(null);
            Object.keys(data.options).forEach(function (key) {
                var bucket = data.options[key]; if (!Array.isArray(bucket)) return;
                bucket.forEach(function (option) {
                    var code = clean(option.value), name = clean(option.label);
                    if (code && name && !seen[code]) { seen[code] = true; out.push({code: code, name: name}); }
                });
            });
            out.sort(function (a, b) { return a.code < b.code ? -1 : a.code > b.code ? 1 : 0; });
            return out;
        }
        function query() {
            var conditions = cfg.conditions || {}, choices = {dldm: blocks, lbdm: menus, hdbmzt: filters.hdbmzt, hdsfjxz: filters.hdsfjxz || []};
            var valid = true;
            Object.keys(choices).forEach(function (key) {
                var code = clean(conditions[key]); if (!code) return;
                if (!choices[key].some(function (x) { return x.code === code; })) { valid = false; return; }
                selected[key] = [code];
            });
            if (!valid) { fail('filters'); return; }
            var criteria = {text: clean(cfg.search), conditions: {}};
            Object.keys(choices).forEach(function (key) { criteria.conditions[key] = clean(conditions[key]); });
            state.options = {source: 'school-list', filters: filters, scope: scope, blocks: blocks, menus: menus, groups: []};
            var fields = {inputType: JSON.stringify({hdmc: [criteria.text], qthdlx: ['']}), selectType: JSON.stringify(selected),
                inputSqlType: '0', orders: '[]', showCount: String(pageSize), currentPage: String(number),
                checkBoxType: '{}', dateType: '{}', numberType: '{}', all: '1'};
            fetch('/zftal-xgxt-web/hdgl/list.zf', Object.keys(fields).map(function (key) { return key + '=' + encodeURIComponent(fields[key]); }).join('&'), function (data) {
                var total = integer(data.totalResult), size = integer(data.showCount), pages = integer(data.totalPage);
                if (!Array.isArray(data.items) || integer(data.currentPage) !== number || total < 0 || size <= 0 || size > 10 || pages < 0
                    || (total > 0 && pages !== Math.ceil(total / size)) || (total === 0 && pages > 1)) { fail('list'); return; }
                if (number > 1 && (integer(cfg.expectedTotal) !== total || integer(cfg.pageSize) !== size)) { fail('changed'); return; }
                if (data.items.length !== Math.min(size, Math.max(0, total - (number - 1) * size))) { fail('changed'); return; }
                var items = [], seen = Object.create(null);
                for (var i = 0; i < data.items.length; i++) {
                    var hit = data.items[i];
                    if (!hit || !clean(hit.id) || !clean(hit.hdmc)) { fail('list'); return; }
                    var one = row(hit, '', ''); one.year = one.year || scope.xn; one.term = one.term || scope.xq;
                    one.schoolState = names[clean(hit.hdbmzt)] || '状态待核实';
                    if (!seen[one.id]) { seen[one.id] = true; items.push(one); }
                }
                state.list = {source: 'school-list', filters: filters, scope: scope, blocks: blocks, menus: menus,
                    query: criteria, page: number, pageSize: size, total: total, hasMore: number < pages,
                    groups: items.length ? [{name: '', code: '', items: items}] : []};
                state.status = 'complete';
            });
        }
        function configure(meta, categories) {
            if (!Array.isArray(meta.conditionTypeList)) { fail('filters'); return; }
            meta.conditionTypeList.forEach(function (condition) {
                if (!condition || ['xn', 'xq', 'hdbmzt', 'hdsfjxz'].indexOf(condition.value) < 0) return;
                var options = condition.typeOption && condition.typeOption.options; if (!Array.isArray(options)) return;
                filters[condition.value] = options.map(function (option) { return {code: clean(option.value), name: clean(option.label)}; })
                    .filter(function (option) { return option.code && option.name; });
                if (condition.value === 'hdbmzt') { filters.hdbmzt.forEach(function (x) { names[x.code] = x.name; }); return; }
                if (condition.value === 'hdsfjxz') return;
                var code = clean(cfg.filters && cfg.filters[condition.value]) || clean((condition.defaultValue || [])[0]);
                if (!code || !filters[condition.value].some(function (x) { return x.code === code; })) return;
                scope[condition.value] = code; selected[condition.value] = [code];
            });
            blocks = dictionary(categories.dldm); menus = dictionary(categories.lbdm);
            if (!filters.hdbmzt || !filters.hdbmzt.length || !scope.xn || !scope.xq || !blocks.length || !menus.length) { fail('filters'); return; }
            query();
        }
        if (integer(number) !== number || number < 1 || number > 5000) return fail('list');
        var cached = window.__xinxinActivityDictionary;
        if ((number > 1 || cfg.reuseDictionary === true) && cached && Date.now() - cached.at < 300000) { configure(cached.meta, cached.categories); return; }
        fetch('/zftal-xgxt-web/gjcx/newList.zf', JSON.stringify({ymdz: 'hdgl/hdlb'}), function (meta) {
            fetch('/zftal-xgxt-web/ywdmgl/searchXsDllb2.zf', '{}', function (categories) {
                window.__xinxinActivityDictionary = {at: Date.now(), meta: meta, categories: categories};
                configure(meta, categories);
            });
        });
    }

    function readDetail(id) {
        var answer = request('GET', '/zftal-xgxt-web/hdgl/details.zf?id=' + encodeURIComponent(id));
        if (!answer || answer.code !== 0 || !answer.data) return null;
        var data = answer.data;
        if (!clean(data.hdmc) || (clean(data.id) && clean(data.id) !== id)) return null;
        var form = request('GET', '/zftal-xgxt-web/dynamic/form/group/hdgl/xsck.zf?dataId=' +
            encodeURIComponent('data|hdglsq|' + id));
        var fields = {};
        if (form && form.data) {
            (form.data.groupFields || []).forEach(function (group) {
                (group.fields || []).forEach(function (field) {
                    var value = field.defaultValue;
                    if (value === undefined || value === null || value === '') value = field.fieldValue;
                    if (value !== undefined && value !== null) fields[clean(field.fieldName)] = clean(decode(value));
                });
            });
        }
        return {
            id: clean(data.id) || id,
            name: clean(data.hdmc),
            year: clean(data.xn),
            term: clean(data.xq),
            desc: block(data.hdms) || block(fields['活动描述'] || ''),
            start: clean(data.hdkssj),
            end: clean(data.hdjssj),
            place: fields['活动地点'] || '',
            credit: fields['学分'] || '',
            duration: fields['活动时长（分钟）'] || '',
            leader: fields['负责人'] || '',
            phone: fields['负责人联系方式'] || '',
            host: fields['主办方'] || '',
            project: fields['所属项目'] || '',
            groupCode: fields['学分大类'] || '',
            labelCode: fields['学分类别'] || '',
            signed: clean(data.bm) === '1',
            state: clean(data.hdbmzt)
        };
    }

    /**
     * 报名：学校把「学生活动申请」做成业务流表单，字段默认值已经带好学生与活动信息，
     * 提交就是把整张表回给 apply 接口。这里先读表单默认值，再原样提交，
     * 失败原因直接用学校返回的 msg（如「报名人数已满」）。
     */
    function signup(id) {
        if (!id) return null;
        var sent = window.__xinxinSignupRequests || (window.__xinxinSignupRequests = Object.create(null));
        var key = cfg.requestId || id;
        if (sent[key]) return sent[key];
        var form = request('GET', '/zftal-xgxt-web/dynamic/form/group/hdsq/default.zf?dataId=' +
            encodeURIComponent('xsxx|&hdsq|' + id));
        if (!form || form.code !== 0 || !form.data) return null;
        var pairs = [];
        (form.data.groupFields || []).forEach(function (group) {
            (group.fields || []).forEach(function (field) {
                var code = clean(field.fieldCode);
                if (!code) return;
                var value = field.defaultValue;
                if (value === undefined || value === null) value = '';
                value = String(value);
                var codes = code.split('|');
                if (codes.length > 1 && value.indexOf(',') >= 0) {
                    var parts = value.split(',');
                    for (var i = 0; i < codes.length; i++)
                        pairs.push(encodeURIComponent(codes[i]) + '=' + encodeURIComponent(clean(parts[i] || '')));
                    return;
                }
                pairs.push(encodeURIComponent(code) + '=' + encodeURIComponent(value));
            });
        });
        if (pairs.indexOf('hdid=' + encodeURIComponent(id)) < 0) return null;
        // Keep an uncertain outcome too: reinjection must never repeat a POST.
        sent[key] = { ok: false, message: '报名结果待确认，请返回活动详情刷新后再决定是否重试' };
        var answer = request('POST', '/zftal-xgxt-web/hdgl/flow/apply/hdsq.zf', pairs.join('&'));
        if (!answer) return sent[key];
        return sent[key] = {
            ok: answer.code === 0,
            message: clean(answer.msg) || (answer.code === 0 ? '报名成功' : '学校拒绝了这次报名')
        };
    }

    function run() {
        if (!schoolHost()) return fail('blocked');
        if (document.querySelector('input[type=password]')) return fail('login');
        if (cfg.action === 'signup') {
            var result = signup(clean(cfg.id));
            if (!result) return fail('detail');
            state.signup = result;
            state.status = 'complete';
            return 'started';
        }
        if (cfg.action === 'detail') {
            var detail = readDetail(clean(cfg.id));
            if (!detail) return fail('detail');
            state.detail = detail;
            state.status = 'complete';
            return 'started';
        }
        readList();
        return 'started';
    }

    try {
        return run();
    } catch (e) {
        return fail(String(e));
    }
})(__ACT_PLAN__);
