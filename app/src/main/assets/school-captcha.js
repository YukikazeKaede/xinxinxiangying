(function (text) {
  'use strict';
  // 第二次注入：把本机识别/人工输入的验证码填回学校页面并提交一次。
  // 与 school-login.js 共用同一套同源校验和 set() 路径，避免绕过学校的
  // salt/加密 handler（绝不用 form.submit()）。
  var allowed = ['authserver.sit.edu.cn', 'jwxt.sit.edu.cn', 'yjsgl.sit.edu.cn'];
  if (location.protocol !== 'https:' || allowed.indexOf(location.hostname) < 0) return 'blocked';
  if (window.__xinxinLoginSubmitted) return 'submitted';
  function visible(e) { return !!e && !!(e.offsetWidth || e.offsetHeight || e.getClientRects().length); }
  // 学校页会同时存在可见副本和 #hidenCaptchaDiv 里的隐藏模板，必须挑可见的那个，
  // 否则值会写进隐藏模板，页面上看起来什么都没填。
  function pick(list) {
    for (var i = 0; list && i < list.length; i++) if (visible(list[i])) return list[i];
    return null;
  }
  if (typeof text !== 'string' || !/^[0-9A-Za-z]{1,8}$/.test(text)) return 'invalid';
  var input = pick(document.querySelectorAll('#captchaResponse,#yzm,input[name="captchaResponse"],input[name="yzm"]'));
  if (!input || !input.form) return 'not-login';
  var target;
  try { target = new URL(input.form.action || location.href, location.href); } catch (_) { return 'blocked'; }
  if (target.protocol !== 'https:' || target.origin !== location.origin || target.username || target.password) return 'blocked';
  if (input.value) return 'user-input';
  var submit = input.form.querySelector('button[type="submit"],input[type="submit"],#dl');
  if (!submit) return 'unsupported';
  try {
    var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(input, text);
    input.dispatchEvent(new Event('input', {bubbles: true}));
    input.dispatchEvent(new Event('change', {bubbles: true}));
    // 有的校验器只监听键盘事件，补一个 keyup 更稳。
    input.dispatchEvent(new Event('keyup', {bubbles: true}));
  } catch (_) { return 'unsupported'; }
  if (input.value !== text) return 'unsupported';
  // 与 school-login.js 的提交标记保持一致，避免自动流程把同一次提交算两遍。
  window.__xinxinLoginSubmitted = true;
  submit.click();
  return 'submitted';
})(__CAPTCHA_TEXT__)
