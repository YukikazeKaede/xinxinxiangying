package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import cn.xinxinxiangying.app.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Synthetic CAS and credit pages; never uses a real account or submits school requests. */
public final class SecondClassSyncInstrumentation extends Instrumentation {
    private static final String USER="SYNTHETIC_AUTO_CREDITS";
    private static final String CHECK="https://xg.sit.edu.cn/zftal-xgxt-web/teacher/xtgl/index/check.zf";
    private final List<Activity> pages=new ArrayList<>();
    private final List<String> requests=Collections.synchronizedList(new ArrayList<>());
    private final Map<Activity,WebViewClient> clients=new HashMap<>();
    private String mode="success";
    private String only="";
    private volatile boolean schoolReady;
    private Context context;
    private int checks;
    private interface Task{void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);only=args==null?"":args.getString("case","");start();}
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{
        Class<?> type=target instanceof Class?(Class<?>)target:target.getClass();Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);
        return m.invoke(target instanceof Class?null:target,args);
    }
    private void ui(Task task)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError("UI fixture",error[0]);}
    private void stage(String name){Bundle b=new Bundle();b.putString("stage",name);sendStatus(1,b);}
    private boolean at(WebView web,String url)throws Exception{boolean[] result={false};ui(()->result[0]=url.equals(web.getUrl()));return result[0];}
    private String html(String fixtureMode){
        if("empty".equals(fixtureMode))return "<!doctype html><meta charset='utf-8'><body>暂无学分数据</body>";
        return "<!doctype html><meta charset='utf-8'><body><div>累计获得 3.50 / 8.00 学分</div>"
            +"<button type='button' aria-expanded='false' onclick=\"document.getElementById('details').hidden=false;this.setAttribute('aria-expanded','true')\"><span>学分明细</span><span>展开</span></button>"
            +"<section id='details' hidden><div><span>SYNTHETIC_CATEGORY</span><span>3.50 / 8.00</span></div></section></body>";
    }
    private void intercept(Activity activity)throws Exception{
        final String fixtureMode=mode;
        WebView web=(WebView)field(activity,"web");WebViewClient real=web.getWebViewClient();clients.put(activity,real);web.stopLoading();
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                String url=r.getUrl().toString();requests.add(url);
                boolean bad=CHECK.equals(url)&&("fallback".equals(fixtureMode)||"unavailable".equals(fixtureMode))
                    ||SchoolCatalog.DEKT_HOME.equals(url)&&"unavailable".equals(fixtureMode);
                String body=html(fixtureMode);
                if(url.contains("/login")&&"verification".equals(fixtureMode))body="<!doctype html><meta charset='utf-8'><form action='/authserver/login'><input id='username'><input id='password' type='password'><input id='captchaResponse'><button type='submit'>验证</button></form>";
                if(url.contains("/service/fwdt"))body="<!doctype html><meta charset='utf-8'><button onclick=\"location.href='/apply-manage/xsrd'\">学分明细查询</button>";
                else if(url.contains("/apply-manage/xsrd"))body="<!doctype html><meta charset='utf-8'><span>共 "+("records-failed".equals(fixtureMode)?"2":"1")+" 条</span>"
                    +"<div>学分大类：德育 学分类别：SYNTHETIC_CATEGORY 活动名称：SYNTHETIC_NEW_RECORD 认定学分：0.1 创建时间：2026-11-01 12:00:00 学年：2026-2027</div>";
                return new WebResourceResponse("text/html","UTF-8",bad?404:200,bad?"Synthetic missing":"OK",Collections.emptyMap(),
                    new ByteArrayInputStream((bad?"SYNTHETIC_404":body).getBytes(StandardCharsets.UTF_8)));
            }
            @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap b){requests.add("START "+u);real.onPageStarted(v,u,b);}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){
                requests.add("HTTP "+response.getStatusCode()+" "+r.getUrl()+" current="+v.getUrl());
                real.onReceivedHttpError(v,r,response);
                // A cancelled entry may deliver another error while the target navigation is still queued.
                if("fallback".equals(fixtureMode)&&CHECK.equals(r.getUrl().toString()))real.onReceivedHttpError(v,r,response);
                requests.add("FINISHING "+activity.isFinishing());
            }
            @Override public void onPageFinished(WebView v,String u){
                real.onPageFinished(v,u);
                if(SchoolCatalog.DEKT_HOME.equals(u)||u.contains("/apply-manage/xsrd")){
                    try{
                        check(((android.view.View)field(activity,"syncOverlay")).getVisibility()==android.view.View.VISIBLE,"second-class reads stay behind task overlay");
                        check(v.getImportantForAccessibility()==android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,"school content is excluded from foreground accessibility");
                        if(SchoolCatalog.DEKT_HOME.equals(u)){
                            android.view.View root=activity.getWindow().getDecorView();
                            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                            root.draw(new android.graphics.Canvas(bitmap));
                            try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"SYNTHETIC-sync-task.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
                        }
                    }catch(Exception error){throw new RuntimeException(error);}
                }
                requests.add("DONE "+u+" current="+v.getUrl());
                // Only this synthetic fixture simulates the CAS redirect; production follows the school's response.
                if(SchoolCatalog.DEKT_SSO.equals(u)&&!"login".equals(fixtureMode)&&!"verification".equals(fixtureMode)&&!activity.isFinishing())v.loadUrl(CHECK);
            }
        });
        web.loadUrl(SchoolCatalog.DEKT_SSO);
    }
    @Override public void callActivityOnCreate(Activity activity,Bundle state){
        super.callActivityOnCreate(activity,state);pages.add(activity);
        try{
            if(activity instanceof DektActivity){
                ((Handler)field(activity,"handler")).removeCallbacksAndMessages(null);
                call(activity,"importPage",new Class<?>[]{});intercept(activity);
            }else if(activity instanceof SchoolWebActivity){
                ((Handler)field(activity,"autoHandler")).removeCallbacksAndMessages(null);
                WebView web=(WebView)field(activity,"web");WebViewClient real=web.getWebViewClient();web.stopLoading();web.setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                        String url=r.getUrl().toString();requests.add(url);
                        boolean grade=url.contains("cjcx_cxXsgrcj");
                        boolean exam=url.contains("kscx_cxXsksxx");
                        String body="<!doctype html><meta charset='utf-8'><select id='xnm'><option selected value='2026'>2026</option></select><select id='xqm'><option selected value='3'>3</option></select>";
                        if(grade||exam)body="{\"items\":[],\"totalResult\":0,\"xsxx\":{\"XH\":\""+USER+"\"}}";
                        if(grade&&"grades-failed".equals(mode))return new WebResourceResponse("text/plain","UTF-8",500,"Synthetic failure",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
                        return new WebResourceResponse(grade||exam?"application/json":"text/html","UTF-8",new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
                    }
                    @Override public void onPageFinished(WebView v,String url){real.onPageFinished(v,url);schoolReady=true;}
                });
                set(activity,"autoStopped",true);
                schoolReady=false;web.loadUrl(SchoolCatalog.UG_HOME);
            }
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private void close()throws Exception{
        ui(()->{for(Activity a:new ArrayList<>(pages))if(!a.isFinishing())a.finish();});
        for(int i=0;i<100;i++){boolean[] done={true};ui(()->{for(Activity a:pages)if(!a.isDestroyed())done[0]=false;});if(done[0])break;SystemClock.sleep(50);}
        pages.clear();clients.clear();SchoolSession.endReauth(context);
    }
    private void seed()throws Exception{
        close();requests.clear();ui(()->{
            new SchoolAccount(context).clear();context.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();
            new SchoolAccount(context).save(false,USER,"",false);
            call(AppSession.class,"verified",new Class<?>[]{Context.class,boolean.class,String.class},context,false,USER);
            context.getSharedPreferences("school_session",0).edit().putString("owner","ug:"+USER).commit();
            new DektStore(context).save(DektParser.parse(new JSONObject().put("earned","1.25").put("required","8")
                .put("rows",new JSONArray().put(new JSONObject().put("name","SYNTHETIC_OLD").put("earned","1.25").put("required","8")))));
            new AcademicRepository(context).save("exams",new JSONObject().put("marker","SYNTHETIC_OLD_EXAMS"));
            new DektRecordStore(context).save(DektRecordParser.parse(new JSONObject().put("status","complete").put("total",1)
                .put("records",new JSONArray().put(new JSONObject().put("activity","SYNTHETIC_OLD_RECORD").put("credit","0.2")))));
        });
    }
    private DektActivity open(){return (DektActivity)startActivitySync(new Intent(context,DektActivity.class).putExtra("autoSync",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));}
    private MainActivity returned(ActivityMonitor monitor)throws Exception{
        final MainActivity[] main={null};
        // 只接受本用例新建的首页，不能误收其它任务中旧首页的短暂恢复事件。
        for(int i=0;i<160&&main[0]==null;i++){
            ui(()->{for(Activity a:pages)if(a instanceof MainActivity&&!a.isFinishing()&&!a.isDestroyed())main[0]=(MainActivity)a;});
            if(main[0]==null)SystemClock.sleep(100);
        }
        removeMonitor(monitor);check(main[0]!=null,"automatic sync returns to main: "+mode);return main[0];
    }
    private void direct(String kind,boolean success)throws Exception{
        seed();mode=kind;stage("credit sync "+kind);ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);DektActivity page=open();returned(monitor);
        JSONObject saved=new DektStore(context).read();check(saved!=null,"credit cache remains available: "+kind);
        check((success?"3.50":"1.25").equals(saved.optString("earned")),"fresh credits or previous snapshot: "+kind+" finished="+page.isFinishing()+" events="+requests);
        check("SYNTHETIC_OLD_EXAMS".equals(new AcademicRepository(context).read("exams").optString("marker")),"credit sync does not change exam snapshot");
        String record=new DektRecordStore(context).read().getJSONArray("records").getJSONObject(0).optString("activity");
        check(("records-failed".equals(kind)?"SYNTHETIC_OLD_RECORD":"SYNTHETIC_NEW_RECORD").equals(record),"records follow credits and preserve cache on failure");
        check(requests.contains(SchoolCatalog.DEKT_HALL),"record service hall is included automatically");
        check(requests.contains(SchoolCatalog.DEKT_SSO)&&requests.contains(CHECK)&&requests.contains(SchoolCatalog.DEKT_HOME),"CAS callback and credit target were exercised");
        if("fallback".equals(kind))check(Collections.frequency(requests,SchoolCatalog.DEKT_HOME)==1,"404 fallback is bounded to one target load");
    }
    private WebResourceRequest request(String url){return new WebResourceRequest(){
        public android.net.Uri getUrl(){return android.net.Uri.parse(url);}public boolean isForMainFrame(){return true;}
        public boolean isRedirect(){return false;}public boolean hasGesture(){return false;}public String getMethod(){return "GET";}
        public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}
    };}
    private void manualAndStale()throws Exception{
        seed();mode="login";stage("manual verification and stale callbacks");DektActivity page=open();WebView web=(WebView)field(page,"web");
        for(int i=0;i<50&&!at(web,SchoolCatalog.DEKT_SSO);i++)SystemClock.sleep(50);
        check(!page.isFinishing()&&AppSession.valid(context),"manual school verification does not invalidate the account");
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);ui(page::onBackPressed);returned(monitor);
        check("1.25".equals(new DektStore(context).read().optString("earned")),"cancel keeps previous credits");
        seed();mode="login";page=open();final DektActivity old=page;final WebView oldWeb=(WebView)field(page,"web");
        for(int i=0;i<50&&!at(oldWeb,SchoolCatalog.DEKT_SSO);i++)SystemClock.sleep(50);
        ui(()->{
            new SchoolAccount(context).save(false,"SYNTHETIC_NEW_OWNER","",false);
            call(AppSession.class,"verified",new Class<?>[]{Context.class,boolean.class,String.class},context,false,"SYNTHETIC_NEW_OWNER");
            clients.get(old).onReceivedHttpError(oldWeb,request(SchoolCatalog.DEKT_SSO),new WebResourceResponse("text/html","UTF-8",404,"Synthetic old response",Collections.emptyMap(),new ByteArrayInputStream(new byte[0])));
        });
        check(SchoolCatalog.DEKT_HOME.equals(field(old,"pendingTarget")),"old account HTTP callback cannot start a fallback");
        check(new DektStore(context).read()==null,"old account result does not populate the new account");
    }
    private void pipeline(boolean failure)throws Exception{
        seed();mode="success";stage(failure?"exam failure still continues to credits":"exam success continues to credits");
        SchoolSession.beginReauth(context,"timetable");
        SchoolWebActivity school=(SchoolWebActivity)startActivitySync(new Intent(context,SchoolWebActivity.class)
            .putExtra("service","timetable").putExtra("autoSync",true).putExtra("syncAll",true)
            .putExtra("username",USER).putExtra("authFlowId",SchoolSession.currentReauthId())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        ui(()->{
            if(failure){set(school,"authenticationAccepted",true);call(school,"finishAcademicStage",new Class<?>[]{String.class,String.class},"exams","SYNTHETIC_EXAM_FAILURE");}
            else call(school,"saveAcademic",new Class<?>[]{String.class,String.class},
                new JSONObject().put("term",new JSONObject().put("xnm","2026").put("xqm","3")).put("student",new JSONObject().put("number",USER)).put("items",new JSONArray()).toString(),"exams");
        });
        returned(monitor);check("3.50".equals(new DektStore(context).read().optString("earned")),"sync pipeline updates second-class credits");
        check("SYNTHETIC_OLD_EXAMS".equals(new AcademicRepository(context).read("exams").optString("marker")),"failed or empty exams preserve the previous snapshot");
    }
    private void fullPipeline(boolean timetableFailure,boolean gradeFailure)throws Exception{
        seed();mode=gradeFailure?"grades-failed":"success";stage("full pipeline "+mode+" timetableFailure="+timetableFailure);
        SchoolSession.beginReauth(context,"timetable");
        SchoolWebActivity school=(SchoolWebActivity)startActivitySync(new Intent(context,SchoolWebActivity.class)
            .putExtra("service","timetable").putExtra("autoSync",true).putExtra("syncAll",true)
            .putExtra("username",USER).putExtra("authFlowId",SchoolSession.currentReauthId())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        for(int i=0;i<100&&!schoolReady;i++)SystemClock.sleep(50);
        check(schoolReady,"synthetic academic document is ready");
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        ui(()->{
            set(school,"authenticationAccepted",true);
            check(((android.view.View)field(school,"syncOverlay")).getVisibility()==android.view.View.VISIBLE,"academic page stays behind progress");
            if(timetableFailure)call(school,"offerManualImport",new Class<?>[]{String.class},"SYNTHETIC_TIMETABLE_FAILURE");
            else call(school,"saveAutomaticTimetable",new Class<?>[]{JSONObject.class},new JSONObject().put("semester","2026-2027-1").put("courses",new JSONArray()));
            check(((android.widget.TextView)field(school,"status")).getText().toString().contains("学分绩点"),"foreground task follows grade stage");
        });
        returned(monitor);
        check(requests.stream().anyMatch(u->u.contains("cjcx_cxXsgrcj")),"pipeline queries grades");
        check(requests.stream().anyMatch(u->u.contains("kscx_cxXsksxx")),"pipeline queries exams after grade result");
        check("3.50".equals(new DektStore(context).read().optString("earned")),"pipeline includes second-class credits");
        check("SYNTHETIC_NEW_RECORD".equals(new DektRecordStore(context).read().getJSONArray("records").getJSONObject(0).optString("activity")),"pipeline includes records without manual entry");
    }
    private void verificationRequired(boolean mobile)throws Exception{
        seed();mode="verification";stage("verification reveal and resume");DektActivity page=open();
        WebView web=(WebView)field(page,"web");boolean[] revealed={false};
        if(mobile)ui(()->web.loadUrl("https://xg.sit.edu.cn/zftal-xgxt-web/login"));
        for(int i=0;i<100&&!revealed[0];i++){
            ui(()->revealed[0]=((android.view.View)field(page,"syncOverlay")).getVisibility()==android.view.View.GONE);
            if(!revealed[0])SystemClock.sleep(50);
        }
        check(revealed[0],"required school verification is visible");
        check(AppSession.valid(context),"verification does not clear local identity");
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        ui(()->web.loadUrl(CHECK));returned(monitor);
        check("SYNTHETIC_NEW_RECORD".equals(new DektRecordStore(context).read().getJSONArray("records").getJSONObject(0).optString("activity")),"verification resumes all remaining background tasks");
    }
    private void singleAcademicFailure()throws Exception{
        seed();mode="success";SchoolSession.beginReauth(context,"grades");
        SchoolWebActivity school=(SchoolWebActivity)startActivitySync(new Intent(context,SchoolWebActivity.class)
            .putExtra("service","grades").putExtra("autoSync",true).putExtra("academicSync",true)
            .putExtra("username",USER).putExtra("authFlowId",SchoolSession.currentReauthId())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        for(int i=0;i<100&&!schoolReady;i++)SystemClock.sleep(50);
        ui(()->{set(school,"authenticationAccepted",true);call(school,"offerManualImport",new Class<?>[]{String.class},"SYNTHETIC_QUERY_FAILURE");
            check(((android.view.View)field(school,"syncOverlay")).getVisibility()==android.view.View.VISIBLE,"single academic failure keeps school page hidden");
            check(((android.widget.TextView)field(school,"status")).getText().toString().contains("SYNTHETIC_QUERY_FAILURE"),"single academic failure shows native task result");});
        close();
    }
    private void manualBackgroundRead()throws Exception{
        seed();mode="success";stage("manual second-class read stays behind task overlay");
        DektActivity page=(DektActivity)startActivitySync(new Intent(context,DektActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        boolean[] done={false};
        for(int i=0;i<150&&!done[0];i++){
            ui(()->done[0]=!(Boolean)field(page,"importing"));if(!done[0])SystemClock.sleep(100);
        }
        check(done[0]&&!page.isFinishing(),"manual read returns to the native second-class screen");
        check("3.50".equals(new DektStore(context).read().optString("earned")),"manual background read saves credits");
        close();
    }
    @Override public void onStart(){context=getTargetContext();Bundle report=new Bundle();boolean owned=false;try{
        check(context.getPackageName().endsWith(".sessiontest")||context.getPackageName().endsWith(".pr23test"),"independent test application only");
        String user=new SchoolAccount(context).username(false);check(user.isEmpty()||user.startsWith("SYNTHETIC_"),"synthetic account only");
        owned=true;
        if("full".equals(only)){fullPipeline(false,false);fullPipeline(true,true);direct("records-failed",true);verificationRequired(false);verificationRequired(true);singleAcademicFailure();manualAndStale();manualBackgroundRead();}
        else if("fallback".equals(only))direct("fallback",true);
        else{direct("success",true);direct("fallback",true);direct("empty",false);direct("unavailable",false);
            manualAndStale();pipeline(false);pipeline(true);}
        report.putString("result","PASS "+checks+" automatic second-class sync checks");
    }catch(Throwable e){report.putString("failure",e.toString());report.putString("stack",android.util.Log.getStackTraceString(e));}
    finally{try{close();if(owned){new DektStore(context).clear();DektRecordStore.clearAll(context);new SchoolAccount(context).clear();}}catch(Exception ignored){}}
    finish(report.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,report);}
}
