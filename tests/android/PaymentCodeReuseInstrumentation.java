package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import cn.xinxinxiangying.app.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Synthetic lifecycle checks; no real school pages, accounts, or QR payloads. */
public final class PaymentCodeReuseInstrumentation extends Instrumentation {
    private MainActivity host;private SchoolWebActivity page;private WebViewClient client;private int checks,generation,retentions;
    private Class<?> pool;private String owner;private boolean live;private long nativeCreateMs;
    @Override public void onCreate(Bundle args){super.onCreate(args);live=args!=null&&"benchmark".equals(args.getString("mode"));start();}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private Object field(Object target,String name)throws Exception{Class<?> c=target instanceof Class?(Class<?>)target:target.getClass();java.lang.reflect.Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class?null:target);}
    private Object invoke(String name,Class<?>[] types,Object...args)throws Exception{java.lang.reflect.Method m=pool.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}
    private void clear()throws Exception{invoke("clear",new Class<?>[]{});}
    private WebView take(String scope)throws Exception{return (WebView)invoke("obtain",new Class<?>[]{Activity.class,String.class},host,scope);}
    private void retain(WebView v,String scope)throws Exception{invoke("retain",new Class<?>[]{WebView.class,String.class},v,scope);}
    private void ui(Runnable r){runOnMainSync(r);}
    private void uiChecked(Callable<?> r){ui(()->{try{r.call();}catch(Exception e){throw new RuntimeException(e);}});}
    private void ready()throws Exception{int stage=++retentions;for(int i=0;i<50;i++){final boolean[] b={false};uiChecked(()->{b[0]=(Boolean)field(pool,"ready");return null;});if(b[0])return;SystemClock.sleep(100);}throw new AssertionError("blank pool not ready at retention "+stage+", cached="+(field(pool,"cached")!=null)+", host="+field(MainActivity.class,"paymentHostVisible"));}
    private String js(WebView v,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};ui(()->v.evaluateJavascript(script,s->{result[0]=s;done.countDown();}));check(done.await(3,TimeUnit.SECONDS),"JS callback completed");return result[0];}
    @Override public void callActivityOnCreate(Activity activity,Bundle state){
        long started=SystemClock.elapsedRealtime();super.callActivityOnCreate(activity,state);if(!(activity instanceof SchoolWebActivity))return;nativeCreateMs=SystemClock.elapsedRealtime()-started;if(live)return;
        try{page=(SchoolWebActivity)activity;WebView web=(WebView)field(page,"web");client=web.getWebViewClient();WebViewClient real=client;
            String html="<!doctype html><meta charset='utf-8'><script>window.SYNTHETIC_GENERATION="+(++generation)+"</script><div id='qrcode'><canvas width='120' height='120'></canvas></div>";
            web.stopLoading();web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
                @Override public void onPageStarted(WebView v,String u,Bitmap b){real.onPageStarted(v,u,b);}
                @Override public void onPageCommitVisible(WebView v,String u){real.onPageCommitVisible(v,u);}
                @Override public void onPageFinished(WebView v,String u){real.onPageFinished(v,u);}
            });web.loadUrl(SchoolCatalog.url("code",false));
        }catch(Exception e){throw new RuntimeException("synthetic setup failed",e);}
    }
    private WebView open(String service)throws Exception{
        page=(SchoolWebActivity)startActivitySync(new Intent(getTargetContext(),SchoolWebActivity.class).putExtra("service",service).putExtra("postgraduate",getTargetContext().getSharedPreferences("school_profile",0).getBoolean("postgraduate",false)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<50&&!(Boolean)field(page,"paymentReady");i++){if(!"code".equals(service))break;SystemClock.sleep(100);}
        return (WebView)field(page,"web");
    }
    private void back(){SchoolWebActivity old=page;ui(()->old.onBackPressed());waitForIdleSync();page=null;}
    private void benchmark(){Bundle report=new Bundle();int result=Activity.RESULT_OK;
        try{
            if(!AppSession.valid(getTargetContext())){report.putString("state","login-required");return;}
            pool=Class.forName("cn.xinxinxiangying.app.PaymentCodeWebView");
            host=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("tab",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            org.json.JSONArray samples=new org.json.JSONArray();
            // Warm up once, then alternate independent native instances and retained blank instances.
            String[] modes={"warmup","new","reuse","reuse","new","new","reuse"};
            for(String mode:modes){
                if("new".equals(mode))uiChecked(()->{clear();return null;});
                WebView before=(WebView)field(pool,"cached");long started=SystemClock.elapsedRealtime();WebView v=open("code");boolean shown=false;
                while(SystemClock.elapsedRealtime()-started<30000&&!page.isFinishing()){
                    if("true".equals(js(v,"location.origin==='https://ecard.sit.edu.cn'&&location.pathname==='/epay/h5/v5qrcode'&&Array.from(document.querySelectorAll('[id*=qrcode i] canvas,[id*=qrcode i] img,[class*=qrcode i] canvas,[class*=qrcode i] img')).some(function(n){var r=n.getBoundingClientRect();return r.width>60&&r.height>60&&(n.tagName==='CANVAS'?n.width>0:n.complete&&n.naturalWidth>0)})"))){shown=true;break;}
                    SystemClock.sleep(200);
                }
                long elapsed=SystemClock.elapsedRealtime()-started;
                if(!shown||!(Boolean)field(page,"paymentReady"))throw new AssertionError("official fresh page not ready");
                if(!"warmup".equals(mode)){
                    org.json.JSONObject sample=new org.json.JSONObject().put("mode",mode).put("nativeCreateMs",nativeCreateMs).put("graphicCandidateMs",elapsed).put("sameInstance",v==before);
                    samples.put(sample);Bundle update=new Bundle();update.putString("sample",sample.toString());sendStatus(0,update);
                }
                back();ready();
            }
            report.putString("samples",samples.toString());report.putString("state","complete");report.putString("meaning","Graphic presence is not proof of freshness, validity or cashier acceptance.");
        }catch(Throwable e){report.putString("state","benchmark-stopped-"+e.getClass().getSimpleName());result=Activity.RESULT_CANCELED;}
        finally{if(page!=null)ui(()->page.finish());if(host!=null)ui(()->host.finish());finish(result,report);}
    }
    @Override public void onStart(){if(live){benchmark();return;}Context c=getTargetContext();Bundle report=new Bundle();int result=Activity.RESULT_OK;boolean owned=false;
        try{
            check(new SchoolAccount(c).username(false).isEmpty()&&new SchoolAccount(c).username(true).isEmpty(),"requires a fresh synthetic device");owned=true;
            c.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();new SchoolAccount(c).save(false,"SYNTHETIC_REUSE","",false);AppSession.invalidate(c);
            c.getSharedPreferences("school_session",0).edit().putString("identity","ug:SYNTHETIC_REUSE").commit();c.getSharedPreferences("app_update",0).edit().putLong("last_check",System.currentTimeMillis()).commit();
            pool=Class.forName("cn.xinxinxiangying.app.PaymentCodeWebView");owner=(String)invoke("owner",new Class<?>[]{Context.class,boolean.class},c,false);
            check(!owner.isEmpty()&&"".equals(invoke("owner",new Class<?>[]{Context.class,boolean.class},c,true)),"owner requires matching verified student type");
            host=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).putExtra("tab",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            final WebView[] box={null};uiChecked(()->{box[0]=take(owner);box[0].getSettings().setJavaScriptEnabled(true);return null;});WebView first=box[0];
            check(first.getContext() instanceof MutableContextWrapper,"mutable context for safe Activity rebinding");
            ui(()->first.loadDataWithBaseURL("https://ecard.sit.edu.cn/epay/h5/v5qrcode","<script>window.SYNTHETIC_OLD_CODE=true;setInterval(function(){window.SYNTHETIC_TICK=1},20)</script>","text/html","UTF-8",null));
            for(int i=0;i<30&&!"true".equals(js(first,"window.SYNTHETIC_OLD_CODE===true"));i++)SystemClock.sleep(100);
            check("true".equals(js(first,"window.SYNTHETIC_OLD_CODE===true")),"synthetic old document executed before retention");
            uiChecked(()->{retain(first,owner);return null;});ready();
            check(((MutableContextWrapper)first.getContext()).getBaseContext()==c.getApplicationContext(),"idle view releases Activity context");
            uiChecked(()->{box[0]=take(owner);return null;});check(box[0]==first,"same-account warm instance reused");
            String blank=js(first,"[typeof window.SYNTHETIC_OLD_CODE==='undefined',typeof window.SYNTHETIC_TICK==='undefined',!document.body||document.body.innerText.trim()==='']");
            check("[true,true,true]".equals(blank),"old document and timer state removed before reuse: "+blank);
            uiChecked(()->{retain(first,owner);return null;});ready();
            WebView actual=open("code");check(actual==first,"native code entry takes warm view");check((Boolean)field(page,"paymentReady"),"official-origin fresh document completed");
            check(((MutableContextWrapper)actual.getContext()).getBaseContext()==page,"native Activity context is rebound");
            check("true".equals(js(actual,"window.SYNTHETIC_GENERATION==="+generation+"&&typeof window.SYNTHETIC_OLD_CODE==='undefined'")),"new visit executes a new school document");
            back();ready();WebView second=open("code");check(second==actual,"back then reopen reuses the instance");
            check("true".equals(js(second,"window.SYNTHETIC_GENERATION==="+generation)),"second visit refreshes instead of showing saved code");
            WebResourceRequest request=new WebResourceRequest(){public Uri getUrl(){return Uri.parse(SchoolCatalog.url("code",false));}public boolean isForMainFrame(){return true;}public boolean isRedirect(){return false;}public boolean hasGesture(){return false;}public String getMethod(){return "GET";}public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}};
            ui(()->client.onReceivedHttpError(second,request,new WebResourceResponse("text/plain","UTF-8",500,"Synthetic failure",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]))));
            back();check(field(pool,"cached")==null,"main-frame failure cannot be retained");
            uiChecked(()->{box[0]=take(owner);retain(box[0],owner);return null;});ready();WebView expiring=box[0];
            uiChecked(()->{java.lang.reflect.Field f=pool.getDeclaredField("deadline");f.setAccessible(true);f.setLong(null,SystemClock.elapsedRealtime()-1);box[0]=take(owner);return null;});check(box[0]!=expiring,"expired instance discarded");
            uiChecked(()->{retain(box[0],owner);return null;});ready();WebView old=box[0];uiChecked(()->{box[0]=take("SYNTHETIC_OTHER_OWNER");return null;});check(box[0]!=old,"different account cannot take old instance");
            uiChecked(()->{retain(box[0],owner);return null;});ready();ui(()->AppSession.invalidate(c));check(field(pool,"cached")==null,"session invalidation clears retained instance");
            c.getSharedPreferences("school_session",0).edit().putString("identity","ug:SYNTHETIC_REUSE").commit();
            uiChecked(()->{box[0]=take(owner);retain(box[0],owner);return null;});ready();WebView beforeOther=box[0];
            WebView other=open("card");check(other!=beforeOther&&!(other.getContext() instanceof MutableContextWrapper),"other school services use their own WebView");ui(()->page.finish());waitForIdleSync();page=null;
            uiChecked(()->{box[0]=take(owner);retain(box[0],owner);return null;});ready();ui(()->host.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));check(field(pool,"cached")==null,"memory pressure clears warm instance");
            uiChecked(()->{box[0]=take(owner);retain(box[0],owner);return null;});ready();ui(()->host.moveTaskToBack(true));
            for(int i=0;i<30&&field(pool,"cached")!=null;i++)SystemClock.sleep(100);
            check(field(pool,"cached")==null,"leaving app foreground clears warm instance");
            report.putString("result","PASS "+checks+" checks");
        }catch(Throwable e){report.putString("result","FAIL "+e.getClass().getSimpleName()+": "+e.getMessage());result=Activity.RESULT_CANCELED;}
        finally{if(page!=null)ui(()->page.finish());if(host!=null)ui(()->host.finish());if(owned){new SchoolAccount(c).clear();AppSession.invalidate(c);}}
        finish(result,report);
    }
}
