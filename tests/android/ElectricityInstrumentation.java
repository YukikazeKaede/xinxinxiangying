package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import cn.xinxinxiangying.app.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline PR32 regressions, on a dedicated empty synthetic installation only. */
public final class ElectricityInstrumentation extends Instrumentation {
    private static final String USER="SYNTHETIC_PR32";
    private Context context;
    private int checks;
    private MainActivity main;
    private SchoolWebActivity school;
    private volatile String matchDebug="";
    private final List<Activity> pages=new ArrayList<>();
    private interface Work { void run() throws Exception; }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
    private void ui(Work work)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{work.run();}catch(Throwable t){e[0]=t;}});if(e[0]!=null)throw new Exception("UI fixture",e[0]);waitForIdleSync();}
    private View find(View root,String label){
        if(label.equals(root.getContentDescription())||label.equals(root.getTag())||(root instanceof TextView&&label.contentEquals(((TextView)root).getText())))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View hit=find(((ViewGroup)root).getChildAt(i),label);if(hit!=null)return hit;}return null;
    }
    private Activity open(Class<?> type,String service)throws Exception{
        ActivityMonitor monitor=addMonitor(type.getName(),null,false);
        ui(()->{Intent intent=new Intent(main,type);if(service!=null)intent.putExtra("service",service);main.startActivity(intent);});
        Activity page=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);check(page!=null,"native child opens");waitForIdleSync();return page;
    }
    private JSONObject payload(boolean all,String term,String point)throws Exception{
        return new JSONObject().put("allTerms",all).put("term",new JSONObject().put("xnm",all?"":"2026").put("xqm",all?"":term))
            .put("items",new JSONArray().put(new JSONObject().put("kcmc","SYNTHETIC_COURSE").put("xf","2").put("jd",point)));
    }
    private String html(String url){
        if(!url.contains("/epeortal/"))return "<!doctype html><meta charset=utf-8><body>SYNTHETIC_PAGE</body>";
        String base="<!doctype html><meta charset=utf-8><body><script>setInterval(function(){if(window.__xinxinDormMatch)console.log('SYNTHETIC_MATCH '+JSON.stringify(window.__xinxinDormMatch))},100)</script>";
        if(url.contains("elecCharge"))return base+"<div class=elecCharge>合成电费余额</div><script>document.querySelector('.elecCharge').__vue__={disabled:false,roomName:'合成23号楼1104',room:{roomId:'SYNTHETIC_R1104',buiId:'SYNTHETIC_B23',restElecDegree:'42.50'},initRoomInfo:function(){},back:function(){location.href='/epeortal/pages/h5/elecQuery'}};</script>";
        return base+"<div class=elecQuery>合成六级选项</div><script>var p={sysList:[{elcname:'新电费缴纳'}],sysChange:function(e){this.sysIndex=e.detail.value;this.areaList=[];setTimeout(function(){p.areaList=[{areaName:'奉贤校区'}]},50)},areaChange:function(e){this.areaIndex=e.detail.value;this.distList=[];setTimeout(function(){p.distList=[{districtName:'住宿区'}]},50)},distChange:function(e){this.distIndex=e.detail.value;this.buildList=[];setTimeout(function(){p.buildList=[{buiName:'23号楼',buiId:'SYNTHETIC_B23'}]},50)},buildChange:function(e){this.buildIndex=e.detail.value;this.floorList=[];setTimeout(function(){p.floorList=[{floorName:'第11层',floorId:'SYNTHETIC_F11'}]},50)},floorChange:function(e){this.floorIndex=e.detail.value;this.roomList=[];setTimeout(function(){p.roomList=[{roomName:'1104',roomId:'SYNTHETIC_R1104'}]},50)},roomChange:function(e){this.roomIndex=e.detail.value},query:function(){location.href='/epeortal/pages/h5/elecCharge'}};document.querySelector('.elecQuery').__vue__=p;</script>";
    }
    @Override public void callActivityOnCreate(Activity page,Bundle state){
        super.callActivityOnCreate(page,state);pages.add(page);
        if(!(page instanceof SchoolWebActivity))return;
        try{
            school=(SchoolWebActivity)page;WebView web=(WebView)field(page,"web");web.stopLoading();WebViewClient real=web.getWebViewClient();
            web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){if(m.message().startsWith("SYNTHETIC_MATCH "))matchDebug=m.message();return true;}});
            if(SchoolCatalog.isAcademic(page.getIntent().getStringExtra("service"))){set(page,"autoStopped",true);set(page,"authenticationAccepted",true);}
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html(request.getUrl().toString()).getBytes(StandardCharsets.UTF_8)));}
                @Override public void onPageStarted(WebView view,String url,Bitmap icon){real.onPageStarted(view,url,icon);}
                @Override public void onPageFinished(WebView view,String url){real.onPageFinished(view,url);}
                @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload){real.doUpdateVisitedHistory(view,url,reload);}
            });
            web.loadUrl("electricity".equals(page.getIntent().getStringExtra("service"))?SchoolCatalog.ELECTRICITY:SchoolCatalog.UG_HOME);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private void capture(String name)throws Exception{
        waitForIdleSync();SystemClock.sleep(500);
        Bitmap picture=getUiAutomation().takeScreenshot();check(picture!=null,"default window allows screenshot");
        try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){picture.compress(Bitmap.CompressFormat.PNG,100,out);}picture.recycle();
    }
    @Override public void onStart(){context=getTargetContext();Bundle result=new Bundle();int code=Activity.RESULT_OK;boolean owned=false;
        try{
            check(new SchoolAccount(context).username(false).isEmpty()&&new SchoolAccount(context).username(true).isEmpty(),"empty synthetic device required");owned=true;
            context.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();
            new SchoolAccount(context).save(false,USER,"",false);AppSession.invalidate(context);
            context.getSharedPreferences("school_session",0).edit().putString("identity","ug:"+USER).putString("owner","ug:"+USER).commit();
            context.getSharedPreferences("app_update",0).edit().putLong("last_check",System.currentTimeMillis()).commit();
            AcademicRepository repository=new AcademicRepository(context);
            JSONObject all=AcademicParser.parse("grades",payload(true,"","3.75"),null);
            repository.save("grades",all);
            for(String term:new String[]{"3","12","3"}){
                repository.save("gpa",AcademicParser.parse("grades",payload(false,term,"2.00"),null));
                check("3.75".equals(AcademicParser.overallGpa(repository.readAllGrades())),"term changes preserve overall GPA");
                check("2.00".equals(repository.read("grades").getJSONObject("summary").getString("gpa")),"detail filter keeps the selected term");
            }
            repository.save("grades",all);
            SharedPreferences cache=context.getSharedPreferences("academics_v1",0);
            for(String key:cache.getAll().keySet())if(key.endsWith("_all"))cache.edit().remove(key).commit();
            repository.save("grades",AcademicParser.parse("grades",payload(false,"12","1.00"),null));
            check("3.75".equals(AcademicParser.overallGpa(repository.readAllGrades())),"legacy all-term snapshot migrates before overwrite");
            new SchoolAccount(context).save(false,"SYNTHETIC_OTHER","",false);
            check(repository.readAllGrades()==null,"overall GPA is isolated by account");
            new SchoolAccount(context).save(false,USER,"",false);
            check("3.75".equals(AcademicParser.overallGpa(repository.readAllGrades())),"restoring the account restores its own overall GPA");
            main=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).putExtra("tab",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            ui(()->main.selectTab(1));check(find(main.getWindow().getDecorView(),"3.75")!=null,"tools shows overall GPA after filtering");
            AcademicActivity academic=(AcademicActivity)open(AcademicActivity.class,"gpa");
            ui(()->call(academic,"refresh",new Class<?>[]{}));
            for(int i=0;i<50&&school==null;i++)SystemClock.sleep(100);
            check(school!=null,"semester refresh opens school reader");
            ui(()->call(school,"saveAcademic",new Class<?>[]{String.class,String.class},payload(false,"3","2.50").toString(),"gpa"));
            for(int i=0;i<100&&!school.isFinishing();i++)SystemClock.sleep(100);
            check(school.isFinishing()&&!academic.isFinishing()&&!academic.isTaskRoot(),"refresh keeps the original detail and parent stack");
            ui(()->find(academic.getWindow().getDecorView(),"返回").performClick());
            check((Integer)field(main,"selectedTab")==1&&!main.isFinishing(),"header back returns to tools");
            check(find(main.getWindow().getDecorView(),"3.75")!=null,"overall GPA remains visible after return");capture("SYNTHETIC-tools-gpa.png");
            for(Class<?> type:new Class<?>[]{AcademicActivity.class,DektActivity.class,ActivitiesActivity.class}){
                Activity page=open(type,type==AcademicActivity.class?"gpa":null);ui(()->page.onBackPressed());
                check(page.isFinishing()&&(Integer)field(main,"selectedTab")==1,"system back returns one native level");
            }
            ElectricityActivity electricity=(ElectricityActivity)open(ElectricityActivity.class,null);
            EditText building=(EditText)find(electricity.getWindow().getDecorView(),"electricity-building");
            EditText room=(EditText)find(electricity.getWindow().getDecorView(),"electricity-room");
            check(building!=null&&room!=null,"entry exposes building and room fields");
            ui(()->{building.setText("23");room.setText("1104");});capture("SYNTHETIC-electricity-input.png");
            school=null;ui(()->call(electricity,"queryRoom",new Class<?>[]{}));
            ElectricityStore store=new ElectricityStore(context);JSONObject saved=null;
            for(int i=0;i<200;i++){saved=store.read();if(saved!=null&&saved.has("portalRoom"))break;SystemClock.sleep(100);}
            check(saved!=null&&saved.has("portalRoom"),"school six-level match completes and returns; native="+field(electricity,"error")+"; school="+(school==null?"missing":((TextView)field(school,"status")).getText())+"; match="+matchDebug);
            check("42.50".equals(saved.getJSONObject("balance").getString("value")),"queried balance is displayed in yuan");
            check("1104".equals(saved.getJSONObject("portalRoom").getString("roomNumber")),"matching inputs are retained with the confirmed room");
            check(saved.getJSONObject("portalRoom").getJSONArray("selection").length()==6,"all six matched labels are saved");
            capture("SYNTHETIC-electricity-balance.png");
            school=null;ui(()->{building.setText("999");call(electricity,"queryRoom",new Class<?>[]{});});
            for(int i=0;i<100;i++){if(school!=null&&school.isFinishing())break;SystemClock.sleep(100);}
            check(school!=null&&school.isFinishing(),"unknown building returns a matching error");
            check(((String)field(electricity,"error")).contains("未找到"),"matching failure explains the missing room");
            check("42.50".equals(store.read().getJSONObject("balance").getString("value")),"unknown building preserves prior balance");
            ui(()->electricity.onBackPressed());check((Integer)field(main,"selectedTab")==1,"electricity back returns to tools");
            repository.clear("gpa");check(repository.readAllGrades()==null,"clearing grades also clears the overall snapshot");
            result.putString("result","PASS PR32: "+checks+" checks");
        }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("failure",android.util.Log.getStackTraceString(e));}
        finally{if(owned){try{ui(()->{for(Activity p:pages)if(!p.isFinishing())p.finish();});}catch(Exception ignored){}new AcademicRepository(context).clear();ElectricityStore.clearAll(context);new SchoolAccount(context).clear();AppSession.invalidate(context);}}
        finish(code,result);
    }
}
