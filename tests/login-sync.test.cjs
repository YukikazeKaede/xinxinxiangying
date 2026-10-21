const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const login = fs.readFileSync('app/src/main/assets/school-login.js', 'utf8');
const navigate = fs.readFileSync('app/src/main/assets/timetable-navigate.js', 'utf8');
let checks = 0;
function test(name, fn) { fn(); checks++; process.stdout.write('PASS ' + name + '\n'); }
function fixture(host = 'authserver.sit.edu.cn', action = '/authserver/login') {
  let submitted = 0;
  class Input { constructor() { this.events = []; this._value = ''; } dispatchEvent(e) { this.events.push(e.type); } }
  Object.defineProperty(Input.prototype, 'value', {get() { return this._value; }, set(v) { this._value = v; }});
  const button = {click() { submitted++; }};
  const form = {action, querySelector() { return button; }};
  const user = new Input(), password = new Input(); user.form = password.form = form;
  const challenge = new Input(); challenge.offsetWidth = 0; challenge.offsetHeight = 0; challenge.getClientRects = () => [];
  const doc = {querySelector(selector) { if (selector.includes('#username')) return user; if (selector.includes('#password')) return password; if (selector.includes('#captchaResponse')) return challenge; return null; }};
  const ctx = {location: {protocol: 'https:', hostname: host, origin: 'https://' + host, href: 'https://' + host + '/authserver/login'}, document: doc, window: {}, URL, HTMLInputElement: Input, Event: class { constructor(type) { this.type = type; } }};
  return {ctx, form, user, password, challenge, submitted: () => submitted};
}
function run(f, credentials = {username: 'TEST_STUDENT', password: 'TEST_PASSWORD'}) { return vm.runInNewContext(login.replace('__CREDENTIALS__', JSON.stringify(credentials)), f.ctx); }
test('fills only trusted school form and uses school submit handler', () => { const f = fixture(); assert.equal(run(f), 'submitted'); assert.equal(f.user.value, 'TEST_STUDENT'); assert.equal(f.password.value, 'TEST_PASSWORD'); assert.deepEqual(f.user.events, ['input', 'change']); assert.equal(f.submitted(), 1); });
test('does not resubmit credentials or loop after a failed school login', () => { const f = fixture(); run(f); run(f); assert.equal(f.submitted(), 1); });
test('never fills credentials on lookalike or insecure hosts', () => { for (const host of ['authserver.sit.edu.cn.evil.example', 'ecard.sit.edu.cn', 'example.com']) { const f = fixture(host); assert.equal(run(f), 'blocked'); assert.equal(f.password.value, ''); } const f = fixture(); f.ctx.location.protocol = 'http:'; assert.equal(run(f), 'blocked'); });
test('rejects form credential exfiltration, including other school origins', () => { for (const action of ['https://evil.example/login', 'https://ecard.sit.edu.cn/login', 'http://authserver.sit.edu.cn/login', 'https://evil@authserver.sit.edu.cn/login']) { const f = fixture('authserver.sit.edu.cn', action); assert.equal(run(f), 'blocked'); assert.equal(f.password.value, ''); }});
test('visible captcha is preserved for the student and never bypassed', () => { const f = fixture(); f.challenge.offsetWidth = 80; assert.equal(run(f), 'challenge'); assert.equal(f.submitted(), 0); assert.equal(f.password.value, 'TEST_PASSWORD'); });
test('fills a verification form without repeatedly submitting a rejected password', () => { const f = fixture(); assert.equal(run(f, {username: 'TEST_STUDENT', password: 'TEST_PASSWORD', submit: false}), 'needs-login'); assert.equal(f.submitted(), 0); assert.equal(f.password.value, 'TEST_PASSWORD'); });
test('missing credentials require login and never read page password/cookies', () => { const f = fixture(); assert.equal(run(f, {}), 'needs-login'); assert.equal(f.submitted(), 0); Object.defineProperty(f.ctx.document, 'cookie', {get() { throw new Error('cookie access'); }}); assert.equal(run(f), 'submitted'); });
test('unknown forms fail without guessing authentication protocol', () => { const f = fixture(); f.ctx.document.querySelector = () => null; assert.equal(run(f), 'not-login'); });
function navFixture(link, onclick) {
  let destination = '';
  const element = {getAttribute(name) { if (name === 'href' || name === 'data-url') return link; if (name === 'onclick') return onclick; return null; }};
  const doc = {querySelector() { return null; }, querySelectorAll(selector) {
    if (selector === 'iframe') return [];
    if (selector === '[onclick]') return onclick ? [element] : [];
    return link ? [element] : [];
  }};
  const location = {protocol: 'https:', hostname: 'jwxt.sit.edu.cn', origin: 'https://jwxt.sit.edu.cn', href: 'https://jwxt.sit.edu.cn/jwglxt/xtgl/index_initMenu.html', pathname: '/jwglxt/xtgl/index_initMenu.html', assign(url) { destination = url; }};
  const ctx = {location, document: doc, window: {_path: '/jwglxt'}, URL}; ctx.window.location = location;
  return {ctx, destination: () => destination};
}
test('follows timetable menu URL actually supplied by school page', () => { const f = navFixture('/jwglxt/kbcx/xskbcx_fixture.html'); assert.equal(vm.runInNewContext(navigate, f.ctx), 'navigating'); assert.equal(f.destination(), 'https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcx_fixture.html'); });
test('follows the real 按周次 timetable link xskbcxZccx_cxXskbcxIndex', () => { const f = navFixture('https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html?gnmkdm=N2154&layout=default'); assert.equal(vm.runInNewContext(navigate, f.ctx), 'navigating'); assert.equal(f.destination(), 'https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html?gnmkdm=N2154&layout=default'); });
test('follows timetable entry exposed only through onClickMenu handler', () => { const f = navFixture('#', "onClickMenu('/kbcx/xskbcxZccx_cxXskbcxIndex.html','N2154')"); assert.equal(vm.runInNewContext(navigate, f.ctx), 'navigating'); assert.equal(f.destination(), 'https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html'); });
test('does not invent routes or follow external timetable links', () => { for (const url of [null, 'https://evil.example/kbcx/xskbcx_fixture.html', 'javascript:alert(1)']) { const f = navFixture(url); assert.equal(vm.runInNewContext(navigate, f.ctx), 'waiting-menu'); assert.equal(f.destination(), ''); }});
test('onClickMenu handlers only accept a same-origin /kbcx path', () => { for (const handler of ["onClickMenu('https://evil.example/kbcx/xskbcx_x.html','N1')", "onClickMenu('/jwglxt/login_slogin.html','N1')"]) { const f = navFixture('#', handler); assert.equal(vm.runInNewContext(navigate, f.ctx), 'waiting-menu'); assert.equal(f.destination(), ''); }});
test('recognized course page is ready for automatic read-only query', () => { const f = navFixture(null); f.ctx.location.pathname = '/jwglxt/kbcx/xskbcxZccx_cxXSkbcxIndex.html'; f.ctx.document.querySelector = selector => selector.includes('#xnm') || selector.includes('#xqm') ? {} : null; assert.equal(vm.runInNewContext(navigate, f.ctx), 'ready'); });
test('old timetable entry with year/term but no week picker is not a ready page', () => { const f = navFixture(null); f.ctx.location.pathname = '/jwglxt/kbcx/xskbcx_cxXSkbcxIndex.html'; f.ctx.document.querySelector = selector => selector.includes('#xnm') || selector.includes('#xqm') ? {} : null; assert.equal(vm.runInNewContext(navigate, f.ctx), 'waiting-menu'); });
test('explicit visible school credential error rejects instead of polling submitted state', () => {const f=fixture();const prior=f.ctx.document.querySelector;f.ctx.document.querySelector=selector=>selector.includes('#msg')?{textContent:'用户名或密码错误',offsetWidth:120}:prior(selector);f.ctx.window.__xinxinLoginSubmitted=true;assert.equal(run(f),'rejected');assert.equal(f.submitted(),0);});
const session=fs.readFileSync('app/src/main/assets/school-session.js','utf8');
function proof({host='jwxt.sit.edu.cn',protocol='https:',text='学生 SYNTHETIC_STUDENT',logout=true,password=false}={}){return vm.runInNewContext(session.replace('__EXPECTED_USER__',JSON.stringify('SYNTHETIC_STUDENT')),{location:{hostname:host,protocol},document:{querySelector:()=>password?{}:null,querySelectorAll:()=>logout?[{textContent:'退出',getAttribute:n=>n==='href'?'/logout':null}]:[],body:{innerText:text}}});}
test('verified school page requires both matching account and logout action',()=>{assert.equal(proof(),'verified');assert.equal(proof({logout:false}),'unverified');assert.equal(proof({text:'学生 OTHER_STUDENT'}),'unverified');});
test('session proof rejects login page, hostile origin and insecure page',()=>{assert.equal(proof({password:true}),'unverified');assert.equal(proof({host:'jwxt.sit.edu.cn.evil.example'}),'unverified');assert.equal(proof({protocol:'http:'}),'unverified');});
test('session proof uses a complete account token, not a partial username match',()=>{assert.equal(proof({text:'SYNTHETIC_STUDENT_OTHER'}),'unverified');assert.equal(proof({text:'X_SYNTHETIC_STUDENT'}),'unverified');});
process.stdout.write(`${checks}/${checks} login/navigation tests passed\n`);
