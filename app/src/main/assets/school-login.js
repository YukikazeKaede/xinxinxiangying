(function (credentials) {
  'use strict';
  var allowed = ['authserver.sit.edu.cn', 'jwxt.sit.edu.cn', 'yjsgl.sit.edu.cn'];
  if (location.protocol !== 'https:' || allowed.indexOf(location.hostname) < 0) return 'blocked';
  var error = document.querySelector('#msg,#showErrorTip,#errorMsg,.auth_error,.alert-danger');
  if (error && (error.offsetWidth || error.offsetHeight || error.getClientRects().length) && /密码.{0,12}(错误|不正确|无效)|账号.{0,12}(错误|不存在|锁定)|用户.{0,12}(不存在|锁定)|认证失败|登录失败/.test(error.textContent || '')) return 'rejected';
  if (window.__xinxinLoginSubmitted) return 'submitted';
  function visible(e) { return !!e && !!(e.offsetWidth || e.offsetHeight || e.getClientRects().length); }
  var user = document.querySelector('#username,#mobileUsername,#yhm,input[name="username"],input[name="yhm"]');
  var password = document.querySelector('#password[type="password"],#mobilePassword,#mm[type="password"],input[name="mm"][type="password"]');
  if (!user || !password || !user.form || user.form !== password.form) return 'not-login';
  var form = user.form, target;
  try { target = new URL(form.action || location.href, location.href); } catch (_) { return 'blocked'; }
  if (target.protocol !== 'https:' || target.origin !== location.origin || target.username || target.password) return 'blocked';
  if (!credentials || !credentials.username || !credentials.password) return 'needs-login';
  var submit = form.querySelector('button[type="submit"],input[type="submit"],#dl');
  if (!submit) return 'unsupported';
  function set(input, value) {
    var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', {bubbles: true}));
    input.dispatchEvent(new Event('change', {bubbles: true}));
  }
  set(user, credentials.username); set(password, credentials.password);
  // 验证码图片交给原生侧做本机识别：这里只取像素，不识别、不外发。
  // 用 window 变量回传而不是改返回值，老的调用方和状态机完全不用动。
  window.__xinxinCaptchaImage = '';
  // 学校页把验证码模板放在 #hidenCaptchaDiv 里，再由 document.ready 拷进 #cpatchaDiv，
  // 于是同一个 id 会出现两份（一份可见、一份 display:none）。只认可见的那个，
  // 否则会填进隐藏模板，看起来就是“填了但没生效”。
  function pick(list) {
    for (var i = 0; list && i < list.length; i++) if (visible(list[i])) return list[i];
    return list && list.length ? list[0] : null;
  }
  function draw(picture) {
    var canvas = document.createElement('canvas');
    canvas.width = picture.naturalWidth;
    canvas.height = picture.naturalHeight;
    canvas.getContext('2d').drawImage(picture, 0, 0);
    // 跨域图片会污染 canvas，这里抛错由调用方兜住，退回人工输入。
    return canvas.toDataURL('image/png');
  }
  function captchaImage(picture) {
    try {
      if (!picture) return '';
      // ready 之后图片才异步加载，第一次评估时通常还没画出来：
      // 挂一次性 load 回调，等它真的加载完再把图放进去，原生侧会重读。
      if (!picture.complete || !picture.naturalWidth) {
        if (!picture.__xinxinLoadHook) {
          picture.__xinxinLoadHook = 1;
          picture.addEventListener('load', function () {
            try { window.__xinxinCaptchaImage = draw(picture); } catch (_) { window.__xinxinCaptchaImage = ''; }
          });
        }
        return '';
      }
      return draw(picture);
    } catch (_) { return ''; }
  }
  var challenge = pick(document.querySelectorAll('#captchaResponse,#yzm,input[name="captchaResponse"],input[name="yzm"]'));
  if (visible(challenge) && !challenge.value) {
    window.__xinxinCaptchaImage = captchaImage(pick(document.querySelectorAll('#captchaImg,img#captcha,img[src*="captcha" i],input[name="captchaResponse"] + img')));
    return 'challenge';
  }
  if (credentials.submit === false) return 'needs-login';
  // The school's real button runs its own salt/encryption/validation handlers.
  // Never use form.submit(), which would bypass those handlers.
  window.__xinxinLoginSubmitted = true;
  submit.click();
  return 'submitted';
})(__CREDENTIALS__)
