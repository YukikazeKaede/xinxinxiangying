package cn.xinxinxiangying.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;
import org.json.JSONTokener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** User-operated school website session. Never posts credentials or initiates orders itself. */
public final class SchoolWebActivity extends Activity {
    private WebView web;
    private boolean authenticationAccepted;
    private boolean autoSync, autoStopped, autoQueryStarted, rememberLogin;
    private String pendingUsername="", pendingPassword="", loginScript="", navigationScript="",sessionScript="";
    private int autoLoginAttempts, autoPolls, loginPolls, menuPolls, pageVersion;
    private View syncOverlay;
    private final android.os.Handler autoHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private TextView status;
    private ProgressBar progress;
    private String service;
    private boolean postgraduate;
    private boolean navigationFailed;
    private String timetableScript;
    private String timetableQueryScript;
    private String timetableApiScript;
    private boolean importing;
    private boolean manualMode;
    private boolean manualAfterNav;
    private int manualPages;
    private int navErrors;
    private String manualHint="";
    private String lastQueryState="";
    private String academicService;
    private boolean syncAll;
    private String syncStage;
    private String academicScript;
    private String academicXnm="";
    private String academicXqm="";
    private String lastAcademicState="";
    private static void log(String message){ android.util.Log.i("xinxinsync",message); }
    private Button importButton;
    private int dp(int v) { return (int)(getResources().getDisplayMetrics().density*v+.5f); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CampusUi.window(this);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
        service=getIntent().getStringExtra("service"); if(service==null) service="auth";
        postgraduate=getIntent().getBooleanExtra("postgraduate",false);
        autoSync=getIntent().getBooleanExtra("autoSync",false);
        // 成绩、考试与课表同一套登录与读取流程，只是最后取的学校接口不同。
        if(getIntent().getBooleanExtra("academicSync",false)&&SchoolCatalog.isAcademic(service)&&!"timetable".equals(service)){academicService=service;autoSync=true;}
        syncAll=getIntent().getBooleanExtra("syncAll",false)&&"timetable".equals(service)&&autoSync&&!postgraduate;
        if(!autoSync&&!AppSession.require(this))return;
        pendingUsername=getIntent().getStringExtra("username");if(pendingUsername==null)pendingUsername="";
        pendingPassword=getIntent().getStringExtra("password");if(pendingPassword==null)pendingPassword="";
        rememberLogin=getIntent().getBooleanExtra("remember",false);getIntent().removeExtra("password");
        if(autoSync){loginScript=readAsset("school-login.js");navigationScript=readAsset("timetable-navigate.js");sessionScript=readAsset("school-session.js");}
        if(academicService!=null)academicScript=assetOrNull("academic-api.js");
        if(syncAll)academicScript=assetOrNull("academic-api.js");
        academicXnm=getIntent().getStringExtra("academicXnm");if(academicXnm==null)academicXnm="";
        academicXqm=getIntent().getStringExtra("academicXqm");if(academicXqm==null)academicXqm="";
        if("timetable".equals(service)) {
            timetableScript=assetOrNull("timetable-extract.js");
            timetableQueryScript=assetOrNull("timetable-query.js");
            timetableApiScript=assetOrNull("timetable-api.js");
        }
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(CampusUi.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(0,bars.top,0,bars.bottom);
            }
            return insets;
        });
        LinearLayout bar=new LinearLayout(this);bar.setBackgroundColor(android.graphics.Color.WHITE);bar.setGravity(android.view.Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout back=new LinearLayout(this);back.setGravity(android.view.Gravity.CENTER);back.setBackground(CampusUi.ripple(this,android.graphics.Color.WHITE,6));back.addView(new CampusUi.Icon(this,"back",CampusUi.GREEN),new LinearLayout.LayoutParams(dp(22),dp(22)));back.setContentDescription("返回");back.setFocusable(true);back.setOnClickListener(v->leaveSchool());bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView title=CampusUi.text(this,academicService!=null?SchoolCatalog.title(service):syncAll?"同步课表与成绩":autoSync?"同步课表":"auth".equals(service)?"学校统一认证":SchoolCatalog.title(service),18,CampusUi.INK);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setPadding(dp(14),0,dp(4),0);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if("timetable".equals(service)){
            importButton=CampusUi.button(this,autoSync?"手动导入":"导入",true,v->manualImport());bar.addView(importButton,new LinearLayout.LayoutParams(-2,dp(44)));
        }
        LinearLayout more=new LinearLayout(this);more.setGravity(android.view.Gravity.CENTER);more.setBackground(CampusUi.ripple(this,android.graphics.Color.WHITE,6));more.addView(new CampusUi.Icon(this,"more",CampusUi.GREEN),new LinearLayout.LayoutParams(dp(22),dp(22)));more.setContentDescription("更多");more.setFocusable(true);more.setOnClickListener(v->showMenu());bar.addView(more,new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(bar);
        status=CampusUi.text(this,"正在连接学校系统",11,CampusUi.MUTED);status.setPadding(dp(20),dp(2),dp(20),dp(10));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        if(SchoolCatalog.isAcademic(service)){
            LinearLayout guide=CampusUi.column(this);guide.setPadding(dp(15),dp(12),dp(15),dp(12));guide.setBackground(CampusUi.shape(this,CampusUi.SOFT,8));
            TextView guideTitle=CampusUi.text(this,syncAll?"正在同步课表、绩点与考试安排":autoSync?(academicService!=null?"正在读取学校数据":"正在为你获取课表"):"timetable".equals(service)?"登录 · 查询 · 导入":"在学校系统中查看",13,CampusUi.GREEN);guideTitle.setTypeface(null,android.graphics.Typeface.BOLD);guide.addView(guideTitle);
            TextView hint=CampusUi.text(this,academicService!=null?"登录成功后自动读取并保存在本机，之后打开即可离线查看。验证码或二次验证请在学校页面完成；读取失败时原有记录不会改变。":syncAll?"登录成功后自动读取并保存课表、学分绩点与考试安排。验证码或二次验证请在学校页面完成；某一项读取失败不影响其它数据。":autoSync?"登录成功后自动读取并保存课表。验证码或二次验证请在学校页面完成；若没有自动完成，可在学校页面打开“学生课表查询（按周次）”，再点右上角“手动导入”。":"timetable".equals(service)?"打开个人课表，选择学期并查询，再点右上角“导入”。":(postgraduate?"研究生":"本科生")+"：登录后选择对应菜单；可在“更多”中返回学校首页。",12,CampusUi.INK);hint.setLineSpacing(dp(3),1);hint.setPadding(0,dp(5),0,0);guide.addView(hint);
            LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.setMargins(dp(14),0,dp(14),dp(12));root.addView(guide,gp);
        }
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(CampusUi.GREEN));root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));
        web=new WebView(this);
        if(autoSync){FrameLayout stage=new FrameLayout(this);stage.addView(web,new FrameLayout.LayoutParams(-1,-1));LinearLayout loading=CampusUi.column(this);loading.setGravity(android.view.Gravity.CENTER);loading.setBackgroundColor(CampusUi.BG);loading.addView(new CampusUi.ScheduleArt(this),new LinearLayout.LayoutParams(dp(150),dp(140)));TextView message=CampusUi.text(this,syncAll?"正在登录学校并同步课表与成绩…":academicService!=null?"正在登录学校并读取数据…":"正在登录学校并查询课表…",15,CampusUi.INK);message.setGravity(android.view.Gravity.CENTER);message.setPadding(dp(20),dp(24),dp(20),dp(20));loading.addView(message);loading.addView(new ProgressBar(this),new LinearLayout.LayoutParams(dp(28),dp(28)));syncOverlay=loading;stage.addView(loading,new FrameLayout.LayoutParams(-1,-1));root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));}
        else root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSaveFormData(false);
        settings.setBuiltInZoomControls(true); settings.setDisplayZoomControls(false); settings.setLoadWithOverviewMode(true); settings.setUseWideViewPort(true);
        if(android.os.Build.VERSION.SDK_INT>=26) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        WebView.setWebContentsDebuggingEnabled(true);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value) { progress.setProgress(value); progress.setVisibility(value>=100?View.GONE:View.VISIBLE); }
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                log("console "+message.message()+" @"+message.sourceId()+":"+message.lineNumber());return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                if(request.isForMainFrame()&&!SchoolCatalog.isSchoolUrl(request.getUrl().toString())) {
                    // Also covers cross-origin form POSTs, which bypass shouldOverrideUrlLoading.
                    runOnUiThread(()->status.setText("已停止外部页面 · 可在浏览器中继续"));
                    return new WebResourceResponse("text/plain","UTF-8",403,"External navigation blocked",java.util.Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                String url=request.getUrl().toString();
                if(SchoolCatalog.isSchoolUrl(url)) return false;
                if(request.isForMainFrame()) confirmExternal(url);
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url) {
                if(SchoolCatalog.isSchoolUrl(url)) return false; confirmExternal(url); return true;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) {
                navigationFailed=false;pageVersion++;
                log("start "+pageVersion+" "+url);
                if(!SchoolCatalog.isSchoolUrl(url)) {
                    navigationFailed=true; view.stopLoading(); status.setText("已停止外部页面 · 可在浏览器中继续"); return;
                }
                status.setText("正在连接 · "+safeHost(url));
            }
            @Override public void onPageFinished(WebView view,String url) {
                if(url==null||!url.equals(view.getUrl()))return;
                if(!navigationFailed&&SchoolCatalog.isSchoolUrl(url)) status.setText("校方页面 · "+safeHost(url));
                CookieManager.getInstance().flush();
                if(!navigationFailed&&timetableScript!=null&&SchoolCatalog.isAcademicUrl(url)&&!url.toLowerCase(Locale.ROOT).contains("login"))
                    view.evaluateJavascript(timetableScript,null);
                if(!autoSync&&!navigationFailed){view.evaluateJavascript("!!document.querySelector('input[type=password]')",value->{if("true".equals(value)&&!isFinishing())rejectLogin("学校登录已失效，请重新登录");});}
                if(autoSync&&!autoStopped&&!navigationFailed)advanceAuto(pageVersion);
                // 「手动导入」先打开课表页时，页面加载完再自动接着读一次，用户只需点一次。
                if(!navigationFailed&&manualAfterNav)continueManualImport(url);
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error) {
                navigationFailed=true; handler.cancel();if(autoSync&&!authenticationAccepted){rejectLogin("无法验证学校网站证书，请检查网络后重新登录");return;}autoStopped=true;revealSchool(); status.setText("安全连接失败，请勿在此页面输入账号");
                new AlertDialog.Builder(SchoolWebActivity.this).setTitle("无法验证学校网站证书")
                    .setMessage("连接已停止。请检查手机日期、网络，或稍后重试。").setPositiveButton("知道了",null).show();
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) {
                if(request.isForMainFrame()) { navigationFailed=true;if(autoSync&&!authenticationAccepted){rejectLogin("连接学校失败，请检查网络后重新登录");return;}autoStopped=true;revealSchool(); status.setText("连接失败 · 请检查校园网或学校提供的校外访问方式"); }
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response) {
                if(request.isForMainFrame()) { navigationFailed=true;if(response.getStatusCode()==401||response.getStatusCode()==403){rejectLogin("学校登录未通过或已失效，请重新登录");return;}if(autoSync&&!authenticationAccepted){rejectLogin("学校暂未通过登录验证，请稍后重试");return;}autoStopped=true;revealSchool(); status.setText("学校返回 "+response.getStatusCode()+" · 可从学校首页重新进入"); }
            }
        });
        web.setDownloadListener((url,userAgent,disposition,mimeType,length)->confirmExternal(url));
        if(autoSync){
            String owner=(postgraduate?"pg:":"ug:")+pendingUsername;
            android.content.SharedPreferences accountPrefs=getSharedPreferences("school_session",MODE_PRIVATE);
            Runnable load=()->{if(web==null||isFinishing()||isDestroyed())return;accountPrefs.edit().putString("owner",owner).apply();web.loadUrl(postgraduate?SchoolCatalog.PG_HOME:SchoolCatalog.UG_SSO);};
            if(getIntent().getBooleanExtra("forceLogin",false)||!owner.equals(accountPrefs.getString("owner","")))CookieManager.getInstance().removeAllCookies(done->{CookieManager.getInstance().flush();android.webkit.WebStorage.getInstance().deleteAllData();load.run();});else load.run();
        }else web.loadUrl("auth".equals(service)?SchoolCatalog.AUTH:SchoolCatalog.url(service,postgraduate));
    }
    private String readAsset(String name){try(java.io.InputStream in=getAssets().open(name)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}catch(Exception e){return "";}}
    private String assetOrNull(String name){String value=readAsset(name);return value.isEmpty()?null:value;}
    private void revealSchool(){if(syncOverlay!=null)syncOverlay.setVisibility(View.GONE);}
    private void rejectLogin(String message){if(isFinishing())return;autoStopped=true;autoHandler.removeCallbacksAndMessages(null);pendingPassword="";if(web!=null)web.stopLoading();AppSession.login(this,message,academicService!=null?academicService:"timetable");}
    private void leaveSchool(){if(!AppSession.valid(this)){rejectLogin("登录尚未通过，请重新登录");return;}finish();}
    private void acceptAuthentication()throws Exception{if(authenticationAccepted)return;if(pendingUsername.trim().isEmpty())throw new IllegalArgumentException("学校未确认学号，请重新登录");new SchoolAccount(this).save(postgraduate,pendingUsername,pendingPassword,rememberLogin&&!pendingPassword.isEmpty());AppSession.verified(this,postgraduate,pendingUsername);authenticationAccepted=true;}
    private void enterApp(){pendingPassword="";autoStopped=true;startActivity(new Intent(this,MainActivity.class).putExtra("tab",0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
    private void stopAutomatic(String message){if(autoStopped)return;if(!authenticationAccepted){rejectLogin(message);return;}autoStopped=true;resetImportButton();Toast.makeText(this,"登录成功，课表暂未同步："+message,Toast.LENGTH_LONG).show();enterApp();}
    /** 自动流程失败后不把用户踢回登录页：留在学校页面，把导入交回「手动导入」。 */
    private void offerManualImport(String message){
        if(isFinishing()||isDestroyed())return;
        boolean announce=!message.equals(manualHint);
        if(announce)manualHint=message;
        autoStopped=true;autoHandler.removeCallbacksAndMessages(null);manualMode=true;
        revealSchool();resetImportButton();
        log("manual-import-offer "+message+" "+(web==null?"":web.getUrl()));
        if(importButton!=null){importButton.setEnabled(true);importButton.setText("手动导入");}
        status.setText(academicService!=null?message+" · 可在学校页面完成后重试":message+" · 请在学校页面打开“学生课表查询（按周次）”，再点右上角“手动导入”");
        if(announce)Toast.makeText(this,academicService!=null?message+"。可在学校页面完成后再试。":message+"。可在学校页面打开课表后点“手动导入”。",Toast.LENGTH_LONG).show();
    }
    /** 登录页（统一认证）上的等待时间不占用同步预算，避免用户输验证码时被超时打断。 */
    private static boolean isLoginPage(String url){
        try{
            java.net.URI uri=java.net.URI.create(url);String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
            String path=uri.getPath()==null?"":uri.getPath().toLowerCase(Locale.ROOT);
            return !(host.equals("jwxt.sit.edu.cn")||host.equals("yjsgl.sit.edu.cn"))||path.contains("login");
        }catch(IllegalArgumentException e){return true;}
    }
    /** 登录后若菜单里找不到课表入口，直接打开学校菜单本身给出的课表页（同源、无凭据）。 */
    private String navigateScript(){return navigationScript.replace("__TIMETABLE_ENTRY__",org.json.JSONObject.quote(postgraduate?"":SchoolCatalog.UG_KB_ENTRY));}
    private void scheduleAuto(int version){autoHandler.postDelayed(()->advanceAuto(version),750);}
    private void advanceAuto(int version){
        if(web==null||isFinishing()||autoStopped||navigationFailed||autoQueryStarted||version!=pageVersion)return;
        String url=web.getUrl();if(url==null||!SchoolCatalog.isSchoolUrl(url)){stopAutomatic("学校登录页面来源无法确认");return;}
        log("auto v="+version+" auto="+autoPolls+" login="+loginPolls+" menu="+menuPolls+" auth="+authenticationAccepted+" "+url);
        if(isLoginPage(url)){if(++loginPolls>2400){offerManualImport("学校登录等待过久");return;}}
        else if(++autoPolls>240){offerManualImport("自动获取课表超时");return;}
        if(loginScript.isEmpty()||navigationScript.isEmpty()){stopAutomatic("同步组件无法读取，请重新安装应用");return;}
        org.json.JSONObject credentials=new org.json.JSONObject();
        try{if(!pendingPassword.isEmpty())credentials.put("username",pendingUsername).put("password",pendingPassword).put("submit",autoLoginAttempts==0);}catch(org.json.JSONException e){stopAutomatic("登录信息无效");return;}
        web.evaluateJavascript(loginScript.replace("__CREDENTIALS__",credentials.toString()),result->{
            if(web==null||autoStopped||version!=pageVersion)return;
            String state=unquote(result);
            log("login-state "+state);
            if("rejected".equals(state)){rejectLogin("学校账号或密码未通过验证，请重新输入");return;}
            if("submitted".equals(state)){if(autoLoginAttempts==0)autoLoginAttempts++;status.setText("正在验证学校账号…");if(loginPolls>8){revealSchool();status.setText("请完成学校验证码或登录提示，提交后将继续同步");}scheduleAuto(version);return;}
            if("challenge".equals(state)){autoLoginAttempts++;revealSchool();status.setText("请完成学校验证码，完成后将继续同步");return;}
            if("needs-login".equals(state)){rejectLogin("学校账号或密码未通过验证，请重新登录");return;}
            if("blocked".equals(state)||"unsupported".equals(state)){stopAutomatic("当前学校登录表单暂不支持自动验证，请稍后重试");return;}
            web.evaluateJavascript(sessionScript.replace("__EXPECTED_USER__",org.json.JSONObject.quote(pendingUsername)),proof->{
                if(isFinishing()||autoStopped||version!=pageVersion)return;
                log("session "+unquote(proof));
                if("verified".equals(unquote(proof))){try{acceptAuthentication();}catch(Exception e){rejectLogin("学校登录状态无法保存，请重新登录");return;}}
                advanceNavigation(version,url);
            });
        });
    }
    private void advanceNavigation(int version,String url){
            // 成绩与考试数据由学校页面的查询接口给出，登录后不必再导航到任何页面。
            if(academicService!=null){
                // 还停在统一认证页时不要发请求：否则会抢在用户输验证码之前就报读取失败。
                if(isLoginPage(url)){scheduleAuto(version);return;}
                autoQueryStarted=true;navErrors=0;revealSchool();status.setText("正在读取学校数据…");importAcademic(url);return;
            }
            // 本科生课表由学校手机版接口直接给出，接口在任意已登录页面都能用，
            // 不必先把 WebView 导航到课表页（学校页面被直接打开时会报“系统运行异常”）。
            if(timetableApiScript!=null&&!postgraduate){autoQueryStarted=true;navErrors=0;status.setText("正在读取学校课表…");importCurrentTimetable();return;}
            web.evaluateJavascript(navigateScript(),navResult->{
                if(web==null||autoStopped||version!=pageVersion)return;String nav=unquote(navResult);
                log("nav "+nav+" "+url);
                if("ready".equals(nav)){autoQueryStarted=true;status.setText("正在获取所选学期课程…");navErrors=0;importCurrentTimetable();return;}
                if(nav!=null&&nav.startsWith("error:")){if(++navErrors>=4){offerManualImport("自动打开课表页出错："+nav.substring(6));return;}}
                else navErrors=0;
                if("needs-login".equals(nav)){rejectLogin("学校登录已失效，请重新登录");return;}
                if("not-academic".equals(nav)){scheduleAuto(version);return;}
                if(authenticationAccepted)menuPolls++;
                if(menuPolls==6){revealSchool();status.setText("已登录，正在打开学校课表页…");}
                if(menuPolls>40){offerManualImport("已登录，但没能自动打开学校课表页");return;}
                // The PG adapter consumes only data already returned by a real school page.
                if(postgraduate&&timetableScript!=null){web.evaluateJavascript(timetableScript,data->{try{Object decoded=new JSONTokener(data).nextValue();org.json.JSONObject extraction=new org.json.JSONObject((String)decoded);TimetableParser.parse(extraction);autoQueryStarted=true;extractTimetable(url);}catch(Exception e){scheduleAuto(version);}});}
                else scheduleAuto(version);
            });
    }
    private String unquote(String result){try{Object value=new JSONTokener(result==null?"null":result).nextValue();return value instanceof String?(String)value:"";}catch(Exception e){return "";}}
    private void saveAutomaticTimetable(org.json.JSONObject parsed){
        try{
            if(parsed.optString("semester","").trim().isEmpty())throw new IllegalArgumentException("学校未返回明确的学期名称，请先选择学期");
            // A valid authenticated course response is a fallback school identity proof.
            acceptAuthentication();
            TimetableRepository repository=new TimetableRepository(this);org.json.JSONObject old=repository.findSemester(parsed.optString("semester"));
            if(old==null)old=new org.json.JSONObject();int maxWeek=20;org.json.JSONArray courses=parsed.getJSONArray("courses");for(int i=0;i<courses.length();i++)maxWeek=Math.max(maxWeek,courses.getJSONObject(i).getInt("endWeek"));
            String start=old.optString("startDate","");if(start.isEmpty())start=firstWeekMonday(parsed.optString("termStart",""));
            parsed.put("startDate",start).put("totalWeeks",Math.max(maxWeek,Math.max(old.optInt("totalWeeks",20),parsed.optInt("termWeeks",0))));
            for(String key:new String[]{"patches","periodTimes","viewMode","palette","wallpaper","visibleDays","reminderMinutes","deletedCourseIds","smartWeekend","showStatus","cardAlpha"})if(old.has(key))parsed.put(key,old.get(key));
            org.json.JSONArray previous=old.optJSONArray("courses"),deleted=old.optJSONArray("deletedCourseIds");java.util.Set<String> removed=new java.util.HashSet<>();if(deleted!=null)for(int i=0;i<deleted.length();i++)removed.add(deleted.optString(i));java.util.LinkedHashMap<String,org.json.JSONObject> merged=new java.util.LinkedHashMap<>();for(int i=0;i<courses.length();i++){org.json.JSONObject c=courses.getJSONObject(i);if(!removed.contains(c.optString("id")))merged.put(c.optString("id"),c);}if(previous!=null)for(int i=0;i<previous.length();i++){org.json.JSONObject c=previous.getJSONObject(i);if("manual".equals(c.optString("origin")))merged.put(c.optString("id"),c);}org.json.JSONArray complete=new org.json.JSONArray();for(org.json.JSONObject c:merged.values()){complete.put(c);maxWeek=Math.max(maxWeek,c.optInt("endWeek",1));}parsed.put("courses",complete).put("totalWeeks",Math.max(maxWeek,parsed.optInt("totalWeeks",20)));
            repository.save(parsed);org.json.JSONArray missing=parsed.optJSONArray("failedWeeks");
            Toast.makeText(this,missing==null||missing.length()==0?"登录成功，课表已同步":"课表已同步（第"+missing.join("、")+"周未抓取，请稍后重试）",Toast.LENGTH_LONG).show();if(syncAll)startAcademicStage("grades");else enterApp();
        }catch(Exception e){offerManualImport(e.getMessage()==null?"课表保存失败":e.getMessage());}
    }
    private String safeHost(String url) {
        String host=Uri.parse(url).getHost(); return host==null?"学校网站":host;
    }
    /** 学校周次表给出的第一周起始日，只有确实是周一时才自动填入开学日期。 */
    private static String firstWeekMonday(String date) {
        if(date==null||!date.matches("\\d{4}-\\d{2}-\\d{2}"))return "";
        try {
            SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);fmt.setLenient(false);
            java.util.Calendar calendar=java.util.Calendar.getInstance();calendar.setTime(fmt.parse(date));
            return fmt.format(calendar.getTime()).equals(date)&&calendar.get(java.util.Calendar.DAY_OF_WEEK)==java.util.Calendar.MONDAY?date:"";
        } catch(Exception e) { return ""; }
    }
    /** 点一次就到位：不在课表页时先按学校地址打开课表页，页面加载完自动接着读取。 */
    private void manualImport(){
        autoStopped=true;autoHandler.removeCallbacksAndMessages(null);manualMode=true;manualPages=0;
        if(timetableApiScript!=null&&!postgraduate){importCurrentTimetable();return;}
        if(web==null||navigationScript.isEmpty()){importCurrentTimetable();return;}
        final String url=web.getUrl();
        if(url==null){importCurrentTimetable();return;}
        web.evaluateJavascript(navigateScript(),result->{
            if(web==null||isFinishing()||isDestroyed())return;
            String nav=unquote(result);log("manual-nav "+nav+" "+url);
            if("navigating".equals(nav)){manualAfterNav=true;status.setText("正在打开学校课表页…");if(importButton!=null)importButton.setEnabled(false);return;}
            importCurrentTimetable();
        });
    }
    /** 「手动导入」跳转后的续跑：确认真的到了课表页再读，避免在学校首页误读空表。 */
    private void continueManualImport(String url){
        if(importing){manualAfterNav=false;return;}
        if(navigationScript.isEmpty()){manualAfterNav=false;if(importButton!=null)importButton.setEnabled(true);importCurrentTimetable();return;}
        web.evaluateJavascript(navigateScript(),result->{
            if(web==null||isFinishing()||isDestroyed())return;
            String nav=unquote(result);log("manual-after-nav "+nav+" "+url);
            if("navigating".equals(nav)&&++manualPages<3){status.setText("正在打开学校课表页…");return;}
            manualAfterNav=false;if(importButton!=null)importButton.setEnabled(true);importCurrentTimetable();
        });
    }
    private void importCurrentTimetable() {
        final String url=web.getUrl();
        if(importing) return;
        if(timetableScript==null) { new AlertDialog.Builder(this).setMessage("导入组件无法读取，请重新安装最新版应用。").setPositiveButton("知道了",null).show(); return; }
        if(!SchoolCatalog.isAcademicUrl(url)||url.toLowerCase(Locale.ROOT).contains("login")) {
            new AlertDialog.Builder(this).setMessage("请先登录学校教务系统，打开自己的个人课表并选择学期。").setPositiveButton("知道了",null).show(); return;
        }
        importing=true; importButton.setEnabled(false); importButton.setText("识别中");
        // 学校课表页本身调用的接口返回权威 JSON，优先用它；接口不可用时退回读取已渲染的页面。
        if(timetableApiScript!=null&&!postgraduate&&(autoSync||manualMode)){startTimetableApi(url);return;}
        queryPageTimetable(url);
    }
    /** 逐周调用学校课表接口并按记录合并；未能启动时退回读取页面。 */
    private void startTimetableApi(final String url) {
        web.evaluateJavascript(timetableApiScript.replace("__TIMETABLE_API__",org.json.JSONObject.quote(SchoolCatalog.UG_KB_JSON)),result->{
            if(web==null||isFinishing()||isDestroyed())return;
            if("started".equals(unquote(result))){status.setText("正在读取学校课表数据…");log("api started "+url);waitForTimetableQuery(url,0);return;}
            log("api fallback "+unquote(result));queryPageTimetable(url);
        });
    }
    private void queryPageTimetable(final String url) {
        if(web==null||isFinishing()||isDestroyed())return;
        // Install capture first. A direct undergraduate query is used only on the real
        // timetable page with its selected year/term; otherwise use the rendered page.
        web.evaluateJavascript(timetableScript,ignored->{
            if(web==null||isFinishing()||isDestroyed())return;
            if(timetableQueryScript==null){extractTimetable(url);return;}
            web.evaluateJavascript(timetableQueryScript,result->{
                if(web==null||isFinishing()||isDestroyed())return;
                if("\"started\"".equals(result)) waitForTimetableQuery(url,0);
                else extractTimetable(url);
            });
        });
    }
    private void waitForTimetableQuery(String url,int attempt) {
        if(web==null||isFinishing()||isDestroyed())return;
        if(!url.equals(web.getUrl())){resetImportButton();showImportError("页面已跳转，请重新打开课表");return;}
        // 接口或页面都要逐周取回数据（最多约 20 周），等待预算按周数放宽到两分钟。
        web.evaluateJavascript("(function(){var q=window.__xinxinTimetableQuery;return JSON.stringify(q?{status:q.status,week:q.week||0,weeks:q.weeks||0,reason:q.reason||''}:{status:'failed'});})()",result->{
            if(web==null||isFinishing()||isDestroyed())return;
            String state="failed",reason="";int week=0,weeks=0;
            try{org.json.JSONObject progress=new org.json.JSONObject(unquote(result));state=progress.optString("status","failed");week=progress.optInt("week",0);weeks=progress.optInt("weeks",0);reason=progress.optString("reason","");}catch(Exception ignored){}
            String stamp=state+":"+week+"/"+weeks+(reason.isEmpty()?"":":"+reason);
            if(!stamp.equals(lastQueryState)){lastQueryState=stamp;log("query "+stamp+" attempt="+attempt);}
            if("pending".equals(state)&&attempt<240){
                if(weeks>1&&week>0)status.setText("正在获取第 "+week+"/"+weeks+" 周课程…");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->waitForTimetableQuery(url,attempt+1),500);
            }
            else if("complete".equals(state)) extractTimetable(url);
            else if("empty".equals(state)){resetImportButton();showImportError("学校返回的本学期课表是空的，请确认学年学期选择后重试");}
            else if("login".equals(reason)&&autoSync&&!authenticationAccepted){rejectLogin("学校登录已失效，请重新输入密码并完成验证码");}
            else {resetImportButton();showImportError("学校课表查询未成功，请确认登录仍然有效并重新查询。没有使用之前学期的缓存结果。");}
        });
    }
    /** 登录已确认后直接调用学校的成绩/考试查询接口，不依赖菜单与页面渲染。 */
    private void importAcademic(String url) {
        importAcademic(url,academicService);
    }
    /** 自动同步时按阶段读取：先绩点（成绩），再考试安排。 */
    private void startAcademicStage(String stage) {
        if(isFinishing()||isDestroyed())return;
        syncStage=stage;
        if(web==null||academicScript==null){finishAcademicStage(stage,"读取组件不可用");return;}
        String url=web.getUrl();
        // 课表接口已经成功，说明当前 Cookie 有效；登录交接页（/sso/jziotlogin）也能同源调用教务接口。
        if(url==null||!SchoolCatalog.isAcademicUrl(url)){
            finishAcademicStage(stage,"学校页面尚未就绪");return;
        }
        status.setText("正在读取"+(("exams".equals(stage))?"考试安排":"学分绩点")+"…");
        importAcademic(url,stage);
    }
    private void finishAcademicStage(String stage,String message){
        if(!syncAll)return;
        log("sync-stage "+stage+" failed="+message);
        if("grades".equals(stage)){startAcademicStage("exams");return;}
        if(message!=null&&!message.isEmpty())Toast.makeText(this,"部分数据未读取："+message,Toast.LENGTH_LONG).show();
        enterApp();
    }
    private void importAcademic(String url,String target) {
        if(web==null||academicScript==null){if(syncAll)finishAcademicStage(target,"读取组件无法使用");else showImportError("读取组件无法使用，请重新安装应用");return;}
        String plan;
        try{
            org.json.JSONObject config=new org.json.JSONObject();
            config.put("service",SchoolCatalog.academicKind(target));
            config.put("endpoint",SchoolCatalog.academicEndpoint(target));
            config.put("gnmkdm",SchoolCatalog.academicGnmkdm(target));
            org.json.JSONObject term=termHint();
            if(term!=null)config.put("term",term);
            if(academicXnm.matches("\\d{4}")&&academicXqm.matches("\\d{1,2}"))
                config.put("explicitTerm",new org.json.JSONObject().put("xnm",academicXnm).put("xqm",academicXqm));
            plan=config.toString();
        }catch(org.json.JSONException e){if(syncAll)finishAcademicStage(target,"读取参数无法准备");else showImportError("读取参数无法准备");return;}
        web.evaluateJavascript(academicScript.replace("__ACADEMIC_PLAN__",org.json.JSONObject.quote(plan)),result->{
            if(web==null||isFinishing()||isDestroyed())return;
            if("started".equals(unquote(result))){log("academic started "+target+" "+url);status.setText("正在读取学校"+(("exams".equals(target))?"考试安排":"学分绩点")+"…");waitForAcademic(url,0,target);return;}
            log("academic start-failed "+target+" "+unquote(result));
            if(syncAll)finishAcademicStage(target,"学校数据读取没有启动");else showImportError("学校数据读取没有启动，请稍后重试");
        });
    }
    private void waitForAcademic(String url,int attempt,String target) {
        if(web==null||isFinishing()||isDestroyed())return;
        if(!url.equals(web.getUrl())){if(syncAll)finishAcademicStage(target,"页面已跳转");else showImportError("页面已跳转，请重新导入");return;}
        web.evaluateJavascript("(function(){var a=window.__xinxinAcademic;if(!a)return '';return JSON.stringify(a);})()",result->{
            if(web==null||isFinishing()||isDestroyed())return;
            String body=unquote(result);
            String state="failed",reason="";int count=0;
            try{
                org.json.JSONObject progress=new org.json.JSONObject(body);
                state=progress.optString("status","failed");reason=progress.optString("reason","");
                org.json.JSONArray items=progress.optJSONArray("items");count=items==null?0:items.length();
            }catch(Exception ignored){}
            String stamp=state+":"+count+(reason.isEmpty()?"":":"+reason);
            if(!stamp.equals(lastAcademicState)){lastAcademicState=stamp;log("academic "+target+" "+stamp+" attempt="+attempt);}
            if("pending".equals(state)&&attempt<200){
                if(count>0)status.setText("正在读取学校数据…已读取 "+count+" 条");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->waitForAcademic(url,attempt+1,target),600);
                return;
            }
            if("complete".equals(state)){log("academic shape "+body);saveAcademic(body,target);return;}
            String failure;
            if("login".equals(reason))failure="学校登录已失效";
            else if("no-term".equals(reason))failure="学校没有给出当前学年学期";
            else if("blocked".equals(reason)||"endpoint".equals(reason)||"host".equals(reason))failure="学校数据地址未通过校验";
            else if("script".equals(reason)||"http".equals(reason))failure="学校数据查询没有成功";
            else failure="学校数据查询未成功";
            if(syncAll){finishAcademicStage(target,failure);return;}
            if("login".equals(reason)){if(!authenticationAccepted)rejectLogin("学校登录已失效，请重新输入密码并完成验证码");else showImportError("学校登录已失效，请重新登录后再试");return;}
            if("no-term".equals(reason)){showImportError("学校没有给出当前学年学期，请先打开一次课表确认学期后重试");return;}
            if("blocked".equals(reason)||"endpoint".equals(reason)||"host".equals(reason)){showImportError("学校数据地址未通过校验，已停止读取");return;}
            if("script".equals(reason)||"http".equals(reason)){showImportError("学校数据查询没有成功，请稍后重试");return;}
            showImportError("学校数据查询未成功，请确认登录仍然有效后重试");
        });
    }
    private void saveAcademic(String body,String target) {
        final String source=web==null?"":web.getUrl();
        new Thread(()->{
            try{
                org.json.JSONObject payload=new org.json.JSONObject(body);
                org.json.JSONObject parsed=AcademicParser.parse(SchoolCatalog.academicKind(target),payload,termHint());
                parsed.put("studentType",postgraduate?"pg":"ug").put("sourceUrl",source==null?"":source);
                org.json.JSONArray items=parsed.optJSONArray("items");
                int count=items==null?0:items.length();
                boolean keepOld=syncAll&&count==0;
                if(keepOld)log("academic skip-empty "+target+" kept previous snapshot");
                else new AcademicRepository(SchoolWebActivity.this).save(target,parsed);
                log("academic "+(keepOld?"kept":"saved")+" "+target+" records="+count+" term="+parsed.optJSONObject("term").optString("label",""));
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    Toast.makeText(this,keepOld?"当前学期暂无"+("exams".equals(target)?"考试":"成绩")+"，已保留上次数据":"已读取 "+count+" 条记录",Toast.LENGTH_SHORT).show();
                    if(syncAll){
                        if("grades".equals(target))startAcademicStage("exams");
                        else enterApp();
                        return;
                    }
                    // 刷新入口可能在登录前已被清理，读完统一回到对应的列表页。
                    startActivity(new Intent(this,AcademicActivity.class).putExtra("service",target)
                        .putExtra("postgraduate",postgraduate).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                });
            }catch(Exception e){
                final String message=e.getMessage()==null?"学校数据格式尚未识别":e.getMessage();
                runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){if(syncAll)finishAcademicStage(target,message);else showImportError(message);}});
            }
        },"academic-parser").start();
    }
    /** 已保存课表的学期，学校页面没有学年学期下拉时作为兜底查询参数。 */
    private org.json.JSONObject termHint() {
        try{
            org.json.JSONObject timetable=new TimetableRepository(this).read();
            String semester=timetable==null?"":timetable.optString("semester","");
            java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(\\d{4})-(\\d{4})\\s*学年\\s*第([12])学期").matcher(semester);
            if(!matcher.find())return null;
            return new org.json.JSONObject().put("xnm",matcher.group(1)).put("xqm","2".equals(matcher.group(3))?"12":"3");
        }catch(Exception e){return null;}
    }
    private void extractTimetable(String url) {
        if(web==null||isFinishing()||isDestroyed())return;
        web.evaluateJavascript(timetableScript,value->{
            // Keep parsing off the UI thread; raw page content is never persisted or logged.
            new Thread(()->{
                try {
                    if(value==null||value.length()>8000000) throw new IllegalArgumentException("课表内容过大，请只打开当前学期个人课表");
                    Object outer=new JSONTokener(value).nextValue();
                    if(!(outer instanceof String)) throw new IllegalArgumentException("无法读取课表页面，请重新查询后重试");
                    org.json.JSONObject extraction=new org.json.JSONObject((String)outer);
                    if(extraction.has("error")) throw new IllegalArgumentException(extraction.optString("error"));
                    String source=extraction.optString("sourceUrl","");
                    if(!SchoolCatalog.isAcademicUrl(source)) throw new IllegalArgumentException("课表必须来自学校教务系统");
                    org.json.JSONObject parsed=TimetableParser.parse(extraction);
                    org.json.JSONArray rows=parsed.optJSONArray("courses");
                    log("parsed "+parsed.optString("semester")+" courses="+(rows==null?0:rows.length())+" weeks="+parsed.optInt("termWeeks",0));
                    parsed.put("sourceUrl",source).put("studentType",postgraduate?"pg":"ug").put("importedAt",System.currentTimeMillis());
                    runOnUiThread(()->{
                        resetImportButton();
                        if(isFinishing()||isDestroyed()) return;
                        if(!url.equals(web.getUrl())) { showImportError("页面已跳转，请重新打开课表再导入"); return; }
                        if(autoSync)saveAutomaticTimetable(parsed);else previewTimetable(parsed);
                    });
                } catch(Exception e) {
                    final String message=e.getMessage()==null?"课表格式尚未识别":e.getMessage();
                    runOnUiThread(()->{resetImportButton(); if(!isFinishing()&&!isDestroyed()) showImportError(message);});
                }
            },"timetable-parser").start();
        });
    }
    private void resetImportButton() { importing=false; if(importButton!=null){importButton.setEnabled(true);importButton.setText(autoSync?"手动导入":"导入");} }
    private void showImportError(String message) {
        if(autoSync){offerManualImport(message);return;}
        String detail=message.length()>200?message.substring(0,200):message;
        new AlertDialog.Builder(this).setTitle("暂未识别完整课表").setMessage(detail+"\n\n请在学校个人课表页重新点击“查询”，再点“导入”。原有本机课表未改动。")
            .setPositiveButton("继续查询",null).show();
    }
    private void previewTimetable(org.json.JSONObject parsed) {
        org.json.JSONArray courses=parsed.optJSONArray("courses");
        if(courses==null||courses.length()==0) { showImportError("当前学期没有识别到课程"); return; }
        TimetableRepository repository=new TimetableRepository(this);
        org.json.JSONObject old=repository.read();
        try {
            int maxWeek=20; for(int i=0;i<courses.length();i++) maxWeek=Math.max(maxWeek,courses.getJSONObject(i).getInt("endWeek"));
            boolean sameSemester=!parsed.optString("semester","").isEmpty()&&parsed.optString("semester").equals(old.optString("semester"));
            parsed.put("totalWeeks",Math.max(maxWeek,Math.max(sameSemester?old.optInt("totalWeeks",20):20,parsed.optInt("termWeeks",0))));
            parsed.put("startDate",sameSemester?old.optString("startDate",""):firstWeekMonday(parsed.optString("termStart","")));
        } catch(org.json.JSONException e) { showImportError("课程范围无效"); return; }
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20),dp(8),dp(20),dp(12));
        TextView summary=CampusUi.text(this,"",14,CampusUi.INK); summary.setLineSpacing(dp(5),1);summary.setText("识别到 "+courses.length()+" 条课程安排\n"+(parsed.optString("semester","").isEmpty()?"请核对当前学期":parsed.optString("semester"))+(parsed.optString("termStart","").isEmpty()?"":"\n开学日期："+parsed.optString("termStart")+"（学校周次表）")+"\n确认后替换当前身份的本机课表。请核对课程、周次和教室。");body.addView(summary);
        TextView semesterLabel=CampusUi.text(this,"",12,CampusUi.MUTED);semesterLabel.setText("学期名称");semesterLabel.setPadding(0,dp(12),0,0);body.addView(semesterLabel);
        EditText semesterInput=new EditText(this);semesterInput.setTextSize(14);semesterInput.setTextColor(CampusUi.INK);semesterInput.setSingleLine(true);semesterInput.setHint("如：2026–2027学年 第一学期");semesterInput.setText(parsed.optString("semester",""));
        semesterInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});body.addView(semesterInput);
        for(int i=0;i<courses.length();i++) {
            org.json.JSONObject c=courses.optJSONObject(i); if(c==null) continue;
            LinearLayout card=CampusUi.column(this);card.setPadding(dp(15),dp(14),dp(15),dp(14));card.setBackground(CampusUi.shape(this,CampusUi.SOFT,8));
            TextView name=CampusUi.text(this,c.optString("name"),16,CampusUi.INK);name.setTypeface(null,android.graphics.Typeface.BOLD);card.addView(name);
            String days="一二三四五六日";TextView line=CampusUi.text(this,"周"+days.charAt(c.optInt("weekday")-1)+"  第"+c.optInt("startPeriod")+"–"+c.optInt("endPeriod")+"节 · "+c.optString("room","")+"\n周次："+c.optJSONArray("weeks")+"\n教师："+c.optString("teacher",""),12,CampusUi.MUTED);line.setLineSpacing(dp(4),1);line.setPadding(0,dp(8),0,0);card.addView(line);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(10);body.addView(card,cp);
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("确认导入课表").setView(scroll).setNegativeButton("取消",null).setPositiveButton("导入本机",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                String semester=semesterInput.getText().toString().trim();
                if(semester.isEmpty()){semesterInput.setError("请确认本次导入的学期名称");return;}
                boolean sameSemester=semester.equals(old.optString("semester",""));
                parsed.put("semester",semester).put("startDate",sameSemester?old.optString("startDate",""):"");
                if(sameSemester)parsed.put("totalWeeks",Math.max(parsed.optInt("totalWeeks",20),old.optInt("totalWeeks",20)));
                repository.save(parsed);dialog.dismiss();Toast.makeText(this,"课表已导入，可离线查看",Toast.LENGTH_LONG).show();
                startActivity(new Intent(this,MainActivity.class).putExtra("tab",0).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();
            } catch(RuntimeException|org.json.JSONException e) { showImportError(e.getMessage()==null?"本机保存失败":e.getMessage()); }
        }));dialog.show();
    }
    private void showMenu() {
        String[] items=SchoolCatalog.isAcademic(service)
            ?new String[]{"重新加载","前往学校系统首页","保存当前页","在系统浏览器打开","学校统一门户"}
            :new String[]{"重新加载","前往学校系统首页","在系统浏览器打开","学校统一门户"};
        new AlertDialog.Builder(this).setTitle("学校服务").setItems(items,(d,which)->{
            if(which==0) web.reload();
            else if(which==1) web.loadUrl("auth".equals(service)?SchoolCatalog.AUTH:SchoolCatalog.isAcademic(service)?(postgraduate?SchoolCatalog.PG_HOME:SchoolCatalog.UG_HOME):SchoolCatalog.CARD_HOME);
            else if(which==items.length-1) web.loadUrl(SchoolCatalog.PORTAL);
            else if(which==2&&SchoolCatalog.isAcademic(service)) saveCurrentPage();
            else { String url=web.getUrl(); if(url!=null) confirmExternal(url); }
        }).show();
    }
    private void saveCurrentPage() {
        final String url=web.getUrl();
        if(!SchoolCatalog.isAcademicUrl(url)||url.toLowerCase(Locale.ROOT).contains("login")) {
            new AlertDialog.Builder(this).setMessage("请先在教务系统中登录，并打开对应的个人数据页面。").setPositiveButton("知道了",null).show(); return;
        }
        new AlertDialog.Builder(this).setTitle("保存当前教务页？").setMessage("将把当前页面可见的文字保存到本机，便于离线查阅。请确认已打开自己的"+SchoolCatalog.title(service)+"页面。付款码和充值页面不能保存。")
            .setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{
                // evaluateJavascript returns a value only; no bridge is exposed to remote content.
                web.evaluateJavascript("(function(){return JSON.stringify({url:location.href,title:document.title,text:document.body?document.body.innerText.slice(0,200000):''});})()",value->{
                    try {
                        if(!url.equals(web.getUrl())) throw new IllegalStateException("页面已跳转，请重新保存");
                        Object result=new JSONTokener(value).nextValue();
                        org.json.JSONObject snapshot=new org.json.JSONObject((String)result);
                        if(!SchoolCatalog.isAcademicUrl(snapshot.getString("url"))||!url.equals(snapshot.getString("url"))) throw new IllegalStateException("页面来源已变化");
                        String body=snapshot.getString("text").trim(); if(body.length()<10) throw new IllegalStateException("当前页面没有可保存的文字");
                        String stamp=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date());
                        boolean ok=getSharedPreferences("academic_snapshots",MODE_PRIVATE).edit().putString(new SchoolAccount(this).snapshotKey(postgraduate,service),"保存时间："+stamp+"\n来源："+safeHost(url)+"\n\n"+body).commit();
                        if(!ok) throw new IllegalStateException("本机保存失败"); Toast.makeText(this,"页面文字已保存",Toast.LENGTH_SHORT).show();
                    } catch(Exception e) { Toast.makeText(this,"保存失败，请保持页面不跳转并重试",Toast.LENGTH_LONG).show(); }
                });
            }).show();
    }
    private void confirmExternal(String url) {
        Uri uri; try { uri=Uri.parse(url); } catch(Exception e) { return; }
        String scheme=uri.getScheme();
        if(scheme==null||!(scheme.equalsIgnoreCase("https")||scheme.equalsIgnoreCase("weixin")||scheme.equalsIgnoreCase("alipays")||scheme.equalsIgnoreCase("alipay"))) {
            Toast.makeText(this,"此链接暂不支持，请从学校官方入口办理",Toast.LENGTH_LONG).show(); return;
        }
        String app=scheme.equalsIgnoreCase("https")?"浏览器（"+safeHost(url)+"）":scheme.equalsIgnoreCase("weixin")?"微信":"支付宝";
        new AlertDialog.Builder(this).setTitle("打开"+app+"？").setMessage("将交给外部应用处理。若涉及缴费，请在支付页面核对收款方、金额和房间号；本应用不会自动确认付款。")
            .setNegativeButton("取消",null).setPositiveButton("打开",(d,w)->{
                try { startActivity(new Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
                catch(ActivityNotFoundException e) { Toast.makeText(this,"没有可处理此链接的应用",Toast.LENGTH_LONG).show(); }
            }).show();
    }
    @Override public void onBackPressed() { if(!AppSession.valid(this)){leaveSchool();return;}if(web!=null&&web.canGoBack())web.goBack();else finish(); }
    @Override protected void onPause() { if(web!=null) web.onPause(); CookieManager.getInstance().flush(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if(web!=null) web.onResume(); }
    @Override protected void onDestroy() {
        autoHandler.removeCallbacksAndMessages(null);pendingPassword="";
        if(web!=null) { web.stopLoading(); web.setWebChromeClient(null); web.setWebViewClient(new WebViewClient()); web.destroy(); web=null; }
        super.onDestroy();
    }
}
