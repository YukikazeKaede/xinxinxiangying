const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const script = fs.readFileSync('app/src/main/assets/card-session.js', 'utf8');
let checks = 0;
function run(text, { origin = 'https://ecard.sit.edu.cn', pathname = '/epay/h5/wechatcharge', visible = true } = {}) {
    return vm.runInNewContext(script, {
        location: { origin, pathname },
        document: { querySelectorAll: () => [{ innerText: text, getClientRects: () => visible ? [1] : [] }] }
    });
}
function check(actual, expected) { checks++; assert.equal(actual, expected); }
check(run('登录已过期，请重新打开页面'), 'expired');
check(run('错误\n登录已过期，请重新打开页面'), 'expired');
check(run('登录已过期，请重新打开页面', { pathname: '/epeortal/pages/h5/recharge' }), 'expired');
check(run('登录已过期，请重新打开页面', { visible: false }), 'ok');
check(run('连接超时，请稍后重试'), 'ok');
check(run('余额不足'), 'ok');
check(run('说明：若登录已过期，请重新打开页面'), 'ok');
check(run('登录已过期，请重新打开页面', { origin: 'https://ecard.sit.edu.cn.evil.example' }), 'unsupported');
check(run('登录已过期，请重新打开页面', { origin: 'http://ecard.sit.edu.cn' }), 'unsupported');
check(run('登录已过期，请重新打开页面', { pathname: '/epay/h5evil' }), 'unsupported');
check(run('登录已过期，请重新打开页面', { pathname: '/other' }), 'unsupported');
// Any attempt to click, submit, fetch, access cookies or store data fails in this minimal fixture.
assert(!/fetch\(|XMLHttpRequest|\.click\(|\.submit\(|document\.cookie|localStorage/.test(script));
console.log(`PASS: ${checks} read-only recharge session assertions`);
