package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import cn.xinxinxiangying.app.*;
import java.io.*;

/** Real native login-state callbacks over synthetic school pages only. */
public final class PostgraduateLoginInstrumentation extends Instrumentation {
    private SchoolWebActivity page;private int checks;private MainActivity main;private String fixtureBody="";private boolean fixturePg,initiallyDisabled;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private Object field(Object target,String name)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    @Override public void callActivityOnCreate(Activity activity,Bundle state){
        super.callActivityOnCreate(activity,state);
        if(!(activity instanceof SchoolWebActivity))return;
        try{
        page=(SchoolWebActivity)activity;Button entry=(Button)field(page,"postgraduateEntry");initiallyDisabled=entry!=null&&!entry.isEnabled();
        WebView web=(WebView)field(page,"web");String fixture="<!doctype html><meta charset='utf-8'><body>"+fixtureBody+"</body>";
        WebViewClient real=web.getWebViewClient();web.stopLoading();web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(fixture.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
            @Override public void onPageStarted(WebView v,String u,Bitmap b){real.onPageStarted(v,u,b);}
            @Override public void onPageFinished(WebView v,String u){real.onPageFinished(v,u);}
        });web.loadUrl(fixturePg?"https://yjsgl.sit.edu.cn/SYNTHETIC_home":"https://jwxt.sit.edu.cn/SYNTHETIC_home");
        }catch(Exception e){throw new RuntimeException("synthetic fixture setup failed",e);}
    }
    private void open(boolean pg,String body)throws Exception{
        fixtureBody=body;fixturePg=pg;
        page=(SchoolWebActivity)startActivitySync(new Intent(getTargetContext(),SchoolWebActivity.class).putExtra("service","timetable").putExtra("postgraduate",pg).putExtra("autoSync",true).putExtra("username","SYNTHETIC_PG").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SystemClock.sleep(800);
    }
    private void close(){if(page!=null){runOnMainSync(()->page.finish());page=null;}waitForIdleSync();}
    @Override public void onStart(){Context c=getTargetContext();Bundle report=new Bundle();int result=Activity.RESULT_OK;boolean owned=false;
        try{
            check(new SchoolAccount(c).username(false).isEmpty()&&new SchoolAccount(c).username(true).isEmpty(),"requires a fresh synthetic device");owned=true;
            c.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",true).commit();c.getSharedPreferences("app_update",0).edit().putLong("last_check",System.currentTimeMillis()).commit();
            open(true,"SYNTHETIC_OTHER <a href='/logout'>退出</a>");Button entry=(Button)field(page,"postgraduateEntry");check(entry!=null&&!entry.isEnabled(),"mismatched school identity cannot unlock entry");check(!AppSession.valid(c),"mismatched identity cannot create session");close();
            open(true,"SYNTHETIC_PG");entry=(Button)field(page,"postgraduateEntry");check(!entry.isEnabled(),"identity text alone without school logout evidence cannot unlock");close();
            open(true,"SYNTHETIC_PG <a href='/logout'>退出</a>");
            check(initiallyDisabled,"entry starts disabled before school verification");
            for(int i=0;i<40&&!(Boolean)field(page,"authenticationAccepted");i++)SystemClock.sleep(100);
            entry=(Button)field(page,"postgraduateEntry");check(entry.isEnabled()&&AppSession.valid(c),"verified PG identity enables entry even without a timetable");
            check("SYNTHETIC_PG".equals(new SchoolAccount(c).username(true))&&new SchoolAccount(c).username(false).isEmpty(),"student-type account isolation");
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);Button confirmed=entry;runOnMainSync(()->confirmed.performClick());main=(MainActivity)waitForMonitorWithTimeout(monitor,3000);removeMonitor(monitor);check(main!=null,"verified entry actually opens app");close();
            if(main!=null){runOnMainSync(()->main.finish());main=null;}AppSession.invalidate(c);new SchoolAccount(c).clear();
            c.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();open(false,"SYNTHETIC_OTHER");check(field(page,"postgraduateEntry")==null,"UG flow does not gain a PG shortcut");
            report.putString("result","PASS "+checks+" checks");
        }catch(Throwable e){report.putString("result","FAIL "+e.getClass().getSimpleName()+": "+e.getMessage());result=Activity.RESULT_CANCELED;}
        finally{close();if(main!=null)runOnMainSync(()->main.finish());if(owned){new SchoolAccount(c).clear();AppSession.invalidate(c);c.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();}}
        finish(result,report);
    }
}
