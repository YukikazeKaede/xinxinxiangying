package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import cn.xinxinxiangying.app.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Synthetic pages only; this suite refuses to run against the production package. */
public final class SessionFlowInstrumentation extends Instrumentation {
    private static final String USER="SYNTHETIC_SESSION_USER", SECRET="SYNTHETIC_SESSION_SECRET";
    private final List<Activity> pages=new ArrayList<>();
    private final Map<SchoolWebActivity,WebViewClient> clients=new HashMap<>();
    private volatile String mode="login";
    private int checks;
    private void stage(String name){Bundle update=new Bundle();update.putString("stage",name);sendStatus(1,update);}
    private interface Task { void run() throws Exception; }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private Object field(Object target,String name)throws Exception{
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    private void set(Object target,String name,Object value)throws Exception{
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    private Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{
        Class<?> type=target instanceof Class?(Class<?>)target:target.getClass();
        Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);
        return m.invoke(target instanceof Class?null:target,args);
    }
    private void ui(Task task)throws Exception{
        Throwable[] failure={null};runOnMainSync(()->{try{task.run();}catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new AssertionError("UI task failed",failure[0]);
    }
    private String js(WebView view,String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] result={""};
        ui(()->view.evaluateJavascript(script,value->{result[0]=value;done.countDown();}));
        check(done.await(5,TimeUnit.SECONDS),"WebView callback completes");return result[0];
    }
    private String html(){
        if("verified".equals(mode))return "<!doctype html><meta charset='utf-8'><body>"+USER+"<a href='/logout'>退出</a></body>";
        String error="rejected".equals(mode)?"<div id='msg'>用户名或密码错误</div>":"";
        return "<!doctype html><meta charset='utf-8'>"+error+"<form action='/authserver/login'><input id='username'><input id='password' type='password'><input id='captchaResponse'><button type='submit'>登录</button></form>";
    }
    @Override public void callActivityOnCreate(Activity activity,Bundle state){
        super.callActivityOnCreate(activity,state);pages.add(activity);
        if(!(activity instanceof SchoolWebActivity))return;
        try{
            SchoolWebActivity page=(SchoolWebActivity)activity;WebView web=(WebView)field(page,"web");
            if(web==null)return;WebViewClient real=web.getWebViewClient();clients.put(page,real);
            web.stopLoading();web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
                    return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html().getBytes(StandardCharsets.UTF_8)));
                }
                @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap b){real.onPageStarted(v,u,b);}
                @Override public void onPageFinished(WebView v,String u){real.onPageFinished(v,u);}
            });
            web.loadUrl(activity.getIntent().getBooleanExtra("autoSync",false)?SchoolCatalog.AUTH:SchoolCatalog.CARD_HOME);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private void closePages()throws Exception{
        ui(()->{for(Activity activity:new ArrayList<>(pages))if(!activity.isFinishing())activity.finish();});
        for(int i=0;i<100;i++){
            boolean[] closed={true};ui(()->{for(Activity activity:pages)if(!activity.isDestroyed())closed[0]=false;});
            if(closed[0])break;SystemClock.sleep(50);
        }
        waitForIdleSync();pages.clear();clients.clear();SchoolSession.endReauth(getTargetContext());
    }
    private void seed(boolean remember)throws Exception{
        closePages();Context c=getTargetContext();
        ui(()->{
            AppSession.invalidate(c);
            c.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();
            c.getSharedPreferences("school_session",0).edit().clear().commit();
            c.getSharedPreferences("school_accounts",0).edit().clear().commit();
            new AcademicRepository(c).clear();
            new SchoolAccount(c).save(false,USER,SECRET,remember);
            call(AppSession.class,"verified",new Class<?>[]{Context.class,boolean.class,String.class},c,false,USER);
            c.getSharedPreferences("school_session",0).edit().putString("owner","ug:"+USER).commit();
        });
    }
    private SchoolWebActivity open(boolean password)throws Exception{
        SchoolSession.beginReauth(getTargetContext(),"card");
        Intent intent=new Intent(getTargetContext(),SchoolWebActivity.class)
            .putExtra("service","card").putExtra("returnService","card")
            .putExtra("autoSync",true).putExtra("sessionOnly",true).putExtra("username",USER)
            .putExtra("password",password?SECRET:"").putExtra("passwordFromStore",password)
            .putExtra("authFlowId",SchoolSession.currentReauthId())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        SchoolWebActivity page=(SchoolWebActivity)startActivitySync(intent);
        for(int i=0;i<100;i++){
            boolean[] waiting={false};ui(()->waiting[0]=(Boolean)field(page,"waitingForUser"));
            if(waiting[0])return page;SystemClock.sleep(50);
        }
        throw new AssertionError("login page did not wait for the user");
    }
    private WebResourceRequest request(final String url){return new WebResourceRequest(){
        public android.net.Uri getUrl(){return android.net.Uri.parse(url);}
        public boolean isForMainFrame(){return true;}public boolean isRedirect(){return false;}
        public boolean hasGesture(){return false;}public String getMethod(){return "GET";}
        public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}
    };}
    private void policiesAndCredentials()throws Exception{
        seed(true);Context c=getTargetContext();
        check(new SchoolAccount(c).remembered(false)!=null,"remembered credentials available");
        new SchoolAccount(c).save(false,USER,"",false);
        check(new SchoolAccount(c).remembered(false)!=null,"session-only update preserves the same account secret");
        new SchoolAccount(c).save(false,"SYNTHETIC_OTHER","",false);
        check(new SchoolAccount(c).remembered(false)==null,"switching without a password cannot retain the previous secret");
        seed(false);check(new SchoolAccount(c).remembered(false)==null,"opt-out does not save credentials");
        c.getSharedPreferences("timetables_v1",0).edit().putString("ug"+new SchoolAccount(c).scope(false),"{}").commit();
        ui(()->AppSession.invalidate(c));
        check(!AppSession.valid(c),"invalidation cannot migrate the old owner back into a valid identity");
        check(SchoolSession.beginReauth(c,"card"),"first authentication starts");
        check(!SchoolSession.beginReauth(c,"second"),"concurrent authentication is suppressed");
        check("card".equals(SchoolSession.pendingService(c)),"duplicate does not replace the original target");
        SchoolSession.endReauth(c);check(SchoolSession.beginReauth(c,"second"),"cancel permits another authentication");
        long oldFlow=SchoolSession.currentReauthId();SchoolSession.endReauth(c);
        SchoolSession.beginReauth(c,"card");SchoolSession.endReauth(c,oldFlow);
        check(SchoolSession.reauthActive(),"old activity cannot release a newer authentication flow");
        SchoolSession.endReauth(c);
    }
    private void unverifiedAndRecovery()throws Exception{
        seed(false);mode="login";SchoolWebActivity page=open(false);
        ui(()->{set(page,"autoLoginAttempts",1);call(page,"advanceNavigation",new Class<?>[]{int.class,String.class},field(page,"pageVersion"),SchoolCatalog.AUTH);});
        check(!(Boolean)field(page,"authenticationAccepted"),"a local identity and login attempt do not prove remote authentication");
        check(!page.isFinishing(),"unverified authentication stays on the current page");
        SystemClock.sleep(31000);
        check(!SchoolSession.beginReauth(getTargetContext(),"second"),"verification longer than 30 seconds still suppresses duplicate authentication");
        WebView web=(WebView)field(page,"web");
        WebResourceResponse unauthorized=new WebResourceResponse("text/html","UTF-8",401,"Unauthorized",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
        ui(()->clients.get(page).onReceivedHttpError(web,request(SchoolCatalog.AUTH),unauthorized));
        check(!page.isFinishing(),"401 during authentication does not close its own flow");
        check((Integer)field(page,"sessionRecoveryAttempts")==1,"401 attempts SSO recovery once");
        check(SchoolSession.reauthActive(),"SSO recovery keeps authentication deduplicated");
        ui(()->clients.get(page).onReceivedHttpError(web,request(SchoolCatalog.AUTH),unauthorized));
        check((Boolean)field(page,"autoStopped"),"second 401 stops automatic recovery");
        check(AppSession.valid(getTargetContext()),"401 does not erase the cached identity");
        ui(()->page.finish());
        for(int i=0;i<100&&SchoolSession.reauthActive();i++)SystemClock.sleep(50);
        check(!SchoolSession.reauthActive(),"closing authentication releases the guard");
    }
    private void rejectionAndCaptcha()throws Exception{
        seed(true);mode="rejected";SchoolWebActivity rejected=open(true);
        check(!rejected.isFinishing(),"rejected password stays on the school form");
        check("".equals(field(rejected,"pendingPassword")),"rejected password is discarded");
        check(new SchoolAccount(getTargetContext()).remembered(false)==null,"rejected saved password is forgotten");
        seed(true);mode="login";SchoolWebActivity page=open(true);WebView web=(WebView)field(page,"web");
        check("true".equals(js(web,"document.querySelector('#username').value==='"+USER+"'&&document.querySelector('#password').value==='"+SECRET+"'")),"remembered credentials fill the trusted synthetic form");
        check("false".equals(js(web,"!!window.__xinxinLoginSubmitted")),"visible challenge is left for the user");
        js(web,"document.querySelector('#captchaResponse').value='SYNTHETIC_USER_INPUT';true");
        ui(()->clients.get(page).onPageFinished(web,SchoolCatalog.AUTH));
        check("true".equals(js(web,"document.querySelector('#captchaResponse').value==='SYNTHETIC_USER_INPUT'")),"page callback preserves user verification input");
        ActivityMonitor monitor=addMonitor(SchoolWebActivity.class.getName(),null,false);
        mode="verified";ui(()->web.loadUrl(SchoolCatalog.UG_HOME));
        Activity resumed=waitForMonitorWithTimeout(monitor,7000);removeMonitor(monitor);
        check(resumed instanceof SchoolWebActivity,"verified school identity resumes the original service");
        check("card".equals(resumed.getIntent().getStringExtra("service")),"authentication returns to campus card instead of timetable");
        check(!resumed.getIntent().getBooleanExtra("autoSync",false),"resumed service does not start another authentication");
        check(!SchoolSession.reauthActive(),"verified identity releases authentication guard");
        check(new SchoolAccount(getTargetContext()).remembered(false)!=null,"reused credentials remain remembered");
    }
    private void accountIsolation()throws Exception{
        seed(false);mode="login";SchoolWebActivity page=open(false);Context c=getTargetContext();
        ui(()->{
            new SchoolAccount(c).save(false,"SYNTHETIC_OTHER","",false);
            c.getSharedPreferences("school_session",0).edit().putString("owner","ug:SYNTHETIC_OTHER").commit();
            try{call(page,"acceptAuthentication",new Class<?>[]{});throw new AssertionError("old authentication was accepted");}
            catch(InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException,"old authentication is rejected after switching accounts");}
        });
        String body=new JSONObject().put("term",new JSONObject().put("xnm","2026").put("xqm","3"))
            .put("student",new JSONObject().put("number",USER))
            .put("items",new JSONArray().put(new JSONObject().put("kcmc","SYNTHETIC_COURSE").put("cj","90").put("xf","2").put("jd","4"))).toString();
        ui(()->call(page,"saveAcademic",new Class<?>[]{String.class,String.class},body,"grades"));
        SystemClock.sleep(500);waitForIdleSync();
        check(new AcademicRepository(c).read("grades")==null,"late response cannot write old grades into the new account");
        check("SYNTHETIC_OTHER".equals(new SchoolAccount(c).username(false)),"late authentication cannot restore the old account");
    }
    @Override public void onStart(){Bundle report=new Bundle();try{
        check(getTargetContext().getPackageName().endsWith(".sessiontest"),"suite is restricted to the independent test application");
        String existing=new SchoolAccount(getTargetContext()).username(false);
        check(existing.isEmpty()||existing.startsWith("SYNTHETIC_"),"suite never clears a real account");
        stage("credentials");policiesAndCredentials();
        stage("recovery and 31-second challenge wait");unverifiedAndRecovery();
        stage("rejection and return to original service");rejectionAndCaptcha();
        stage("late-response account isolation");accountIsolation();
        report.putString("result","PASS "+checks+" login/session checks");
    }catch(Throwable e){report.putString("failure",e.toString());report.putString("stack",android.util.Log.getStackTraceString(e));}
    finally{try{closePages();}catch(Exception ignored){} }
    finish(report.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,report);}
}
