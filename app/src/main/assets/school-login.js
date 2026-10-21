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
  var challenge = document.querySelector('#captchaResponse,#yzm,input[name="captchaResponse"],input[name="yzm"]');
  if (visible(challenge) && !challenge.value) return 'challenge';
  if (credentials.submit === false) return 'needs-login';
  // The school's real button runs its own salt/encryption/validation handlers.
  // Never use form.submit(), which would bypass those handlers.
  window.__xinxinLoginSubmitted = true;
  submit.click();
  return 'submitted';
})(__CREDENTIALS__)
