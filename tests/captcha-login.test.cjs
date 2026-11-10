// 验证码取图与回填的 JS 层测试：不联网、不碰真实验证码，全部用合成 DOM。
//
// 这些用例专门盯住实机暴露过的两个坑：
//   1. 学校页把验证码模板放在 #hidenCaptchaDiv 再拷进 #cpatchaDiv，同一个 id 有两份；
//      必须挑可见的那份，否则值会写进隐藏模板。
//   2. 图片是 document.ready 之后才异步加载的，第一次评估时天然为空；
//      必须挂 load 回调，等图真加载出来再回传。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const login = fs.readFileSync('app/src/main/assets/school-login.js', 'utf8');
const captcha = fs.readFileSync('app/src/main/assets/school-captcha.js', 'utf8');
let checks = 0;
function test(name, fn) { fn(); checks++; process.stdout.write('PASS ' + name + '\n'); }

class Field {
  constructor(name) { this.events = []; this._value = ''; this.name = name || ''; }
  dispatchEvent(event) { this.events.push(event.type); }
  get value() { return this._value; }
  set value(next) { this._value = next; }
}

function makeImage(width, height, { loaded = true, hidden = false } = {}) {
  const listeners = {};
  return {
    complete: loaded, naturalWidth: loaded ? width : 0, naturalHeight: loaded ? height : 0,
    offsetWidth: hidden ? 0 : 92, offsetHeight: hidden ? 0 : 34,
    getClientRects: () => (hidden ? [] : [{}]),
    addEventListener(type, handler) { (listeners[type] = listeners[type] || []).push(handler); },
    fire(type) { (listeners[type] || []).forEach(fn => fn()); }
  };
}

function fixture({ host = 'authserver.sit.edu.cn', action = '/authserver/login', images, inputs, canvasThrows = false } = {}) {
  let submitted = 0;
  const button = { click() { submitted++; } };
  const form = { action, querySelector() { return button; } };
  const user = new Field('username'), password = new Field('password');
  user.form = password.form = form;
  const canvas = {
    width: 0, height: 0,
    getContext() { return { drawImage() {} }; },
    toDataURL() { if (canvasThrows) throw new Error('tainted canvas'); return 'data:image/png;base64,U1lOVEg='; }
  };
  const pictureList = images === undefined ? [] : images;
  const inputList = inputs === undefined ? [] : inputs;
  inputList.forEach(input => { if (!input.form) input.form = form; });
  const document = {
    createElement(name) { assert.equal(name, 'canvas'); return canvas; },
    querySelector(selector) {
      if (selector.includes('#username')) return user;
      if (selector.includes('#password')) return password;
      return null;
    },
    querySelectorAll(selector) {
      if (selector.includes('img')) return pictureList;
      if (selector.includes('#captchaResponse')) return inputList;
      return [];
    }
  };
  const window = {};
  const ctx = {
    location: { protocol: 'https:', hostname: host, origin: 'https://' + host, href: 'https://' + host + action },
    document, window, URL,
    HTMLInputElement: Field,
    Event: class { constructor(type) { this.type = type; } }
  };
  return { ctx, user, password, window, canvas, inputs: inputList, pictures: pictureList, submitted: () => submitted };
}

function visibleCaptcha(value = '') {
  const input = new Field('captchaResponse');
  input.value = value;
  input.offsetWidth = 80;
  input.getClientRects = () => [{}];
  return input;
}

function hiddenCaptcha() {
  const input = new Field('captchaResponse');
  input.offsetWidth = 0;
  input.offsetHeight = 0;
  input.getClientRects = () => [];
  return input;
}

const credentials = { username: 'SYNTHETIC_STUDENT', password: 'SYNTHETIC_PASSWORD' };
function runLogin(f, creds = credentials) {
  return vm.runInNewContext(login.replace('__CREDENTIALS__', JSON.stringify(creds)), f.ctx);
}
function runCaptcha(f, text) {
  return vm.runInNewContext(captcha.replace('__CAPTCHA_TEXT__', JSON.stringify(text)), f.ctx);
}

test('captcha image is captured on the school host and handed over as a data URL', () => {
  const f = fixture({ images: [makeImage(92, 34)], inputs: [visibleCaptcha()] });
  assert.equal(runLogin(f), 'challenge');
  assert.equal(f.window.__xinxinCaptchaImage, 'data:image/png;base64,U1lOVEg=');
  assert.equal(f.canvas.width, 92);
  assert.equal(f.canvas.height, 34);
  assert.equal(f.submitted(), 0);
});

test('the hidden template copy is skipped in favour of the visible captcha input', () => {
  const visible = visibleCaptcha();
  const f = fixture({ images: [makeImage(92, 34)], inputs: [hiddenCaptcha(), visible] });
  assert.equal(runLogin(f), 'challenge');
  assert.equal(f.window.__xinxinCaptchaImage, 'data:image/png;base64,U1lOVEg=', 'must still capture from the visible row');
});

test('a hidden-only captcha row is not treated as a challenge', () => {
  const f = fixture({ images: [makeImage(92, 34)], inputs: [hiddenCaptcha()] });
  assert.equal(runLogin(f), 'submitted');
  assert.equal(f.submitted(), 1);
});

test('the hidden template image is skipped in favour of the loaded visible one', () => {
  const hiddenPicture = makeImage(92, 34, { loaded: false, hidden: true });
  const visiblePicture = makeImage(92, 34, { loaded: true });
  const f = fixture({ images: [hiddenPicture, visiblePicture], inputs: [visibleCaptcha()] });
  assert.equal(runLogin(f), 'challenge');
  assert.equal(f.window.__xinxinCaptchaImage, 'data:image/png;base64,U1lOVEg=');
});

test('an image that is still loading is reported once its load event fires', () => {
  const picture = makeImage(92, 34, { loaded: false });
  const f = fixture({ images: [picture], inputs: [visibleCaptcha()] });
  assert.equal(runLogin(f), 'challenge');
  assert.equal(f.window.__xinxinCaptchaImage, '', 'nothing captured before the image is ready');
  picture.fire('load');
  assert.equal(f.window.__xinxinCaptchaImage, 'data:image/png;base64,U1lOVEg=', 'load hook must publish the image');
});

test('the load hook is attached only once across repeated polls', () => {
  const picture = makeImage(92, 34, { loaded: false });
  const f = fixture({ images: [picture], inputs: [visibleCaptcha()] });
  let attached = 0;
  const realAdd = picture.addEventListener.bind(picture);
  picture.addEventListener = (type, handler) => { attached++; realAdd(type, handler); };
  runLogin(f); runLogin(f); runLogin(f);
  assert.equal(attached, 1);
});

test('missing captcha image still reports challenge and leaves no stale image', () => {
  const f = fixture({ images: [], inputs: [visibleCaptcha()] });
  f.window.__xinxinCaptchaImage = 'data:image/png;base64,STALE';
  assert.equal(runLogin(f), 'challenge');
  assert.equal(f.window.__xinxinCaptchaImage, '');
  assert.equal(f.submitted(), 0);
});

test('a tainted canvas never breaks the login state machine', () => {
  const f = fixture({ images: [makeImage(92, 34)], inputs: [visibleCaptcha()], canvasThrows: true });
  assert.equal(runLogin(f), 'challenge');
  assert.equal(f.window.__xinxinCaptchaImage, '');
});

test('no captcha on the page keeps the old behaviour and clears the image slot', () => {
  const f = fixture({ images: [makeImage(92, 34)], inputs: [] });
  f.window.__xinxinCaptchaImage = 'data:image/png;base64,STALE';
  assert.equal(runLogin(f), 'submitted');
  assert.equal(f.window.__xinxinCaptchaImage, '');
  assert.equal(f.submitted(), 1);
});

test('an already filled captcha box is not treated as a challenge', () => {
  const f = fixture({ images: [makeImage(92, 34)], inputs: [visibleCaptcha('ab12')] });
  assert.equal(runLogin(f), 'submitted');
  assert.equal(f.submitted(), 1);
});

test('captcha text is filled into the visible input and submitted exactly once', () => {
  const hidden = hiddenCaptcha();
  const visible = visibleCaptcha();
  const f = fixture({ inputs: [hidden, visible] });
  assert.equal(runCaptcha(f, 'a1B2'), 'submitted');
  assert.equal(visible.value, 'a1B2');
  assert.equal(hidden.value, '', 'the hidden template must stay untouched');
  assert.deepEqual(visible.events, ['input', 'change', 'keyup']);
  assert.equal(f.submitted(), 1);
  assert.equal(f.window.__xinxinLoginSubmitted, true);
});

test('captcha fill refuses hostile hosts, insecure pages and junk text', () => {
  for (const host of ['authserver.sit.edu.cn.evil.example', 'ecard.sit.edu.cn']) {
    const f = fixture({ host, inputs: [visibleCaptcha()] });
    assert.equal(runCaptcha(f, 'a1B2'), 'blocked');
    assert.equal(f.submitted(), 0);
  }
  const insecure = fixture({ inputs: [visibleCaptcha()] });
  insecure.ctx.location.protocol = 'http:';
  assert.equal(runCaptcha(insecure, 'a1B2'), 'blocked');
  assert.equal(insecure.submitted(), 0);
  for (const text of ['', 'a 1b2', 'a1b2!', 'abcdefghi', null]) {
    const f = fixture({ inputs: [visibleCaptcha()] });
    assert.equal(runCaptcha(f, text), 'invalid');
    assert.equal(f.inputs[0].value, '');
    assert.equal(f.submitted(), 0);
  }
});

test('captcha fill does nothing when the school page has no captcha field', () => {
  const f = fixture({ inputs: [] });
  assert.equal(runCaptcha(f, 'a1B2'), 'not-login');
  assert.equal(f.submitted(), 0);
});

process.stdout.write(`${checks}/${checks} captcha tests passed\n`);
