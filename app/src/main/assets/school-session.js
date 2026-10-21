(function(expected){
  'use strict';
  // 页面结构复杂时宁可判为未验证，也不能抛异常打断同步（原生侧还有接口成功后的兜底验证）。
  try{
  if(location.protocol!=='https:'||!/^(jwxt|yjsgl)\.sit\.edu\.cn$/.test(location.hostname)||!expected||document.querySelector('input[type="password"]'))return 'unverified';
  var logout=Array.prototype.some.call(document.querySelectorAll('a[href],button[onclick]'),function(e){if(!e||typeof e.getAttribute!=='function')return false;var action=e.getAttribute('href')||e.getAttribute('onclick')||'';return /logout|login_slogin|login_logout/i.test(action)&&/退出|注销|登出/.test(e.textContent||'');});
  var body=document.body&&document.body.innerText||'';var offset=body.indexOf(expected);var identity=false;
  while(offset>=0){var before=body[offset-1]||'',after=body[offset+expected.length]||'';if(!/[A-Za-z0-9_]/.test(before)&&!/[A-Za-z0-9_]/.test(after)){identity=true;break;}offset=body.indexOf(expected,offset+1);}
  return logout&&identity?'verified':'unverified';
  }catch(e){return 'unverified';}
})(__EXPECTED_USER__)
