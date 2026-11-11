package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.Uri;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.*;
import cn.xinxinxiangying.app.*;
import java.lang.reflect.*;
import java.util.*;

/** Empty dedicated emulator only. A cached fixture WebView intercepts every navigation before onCreate. */
public final class CardRechargeInstrumentation extends Instrumentation {
    private SchoolWebActivity activity;private FixtureWeb web;private Context context;private int checks;
    private void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    private static Object field(Class<?> type,Object object,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void set(Class<?> type,Object object,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private Object call(String name,Class<?>[] types,Object...args)throws Exception{Method m=SchoolWebActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
    private void ui(Work work)throws Exception{Throwable[] failure={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new Exception("fixture check failed",failure[0]);}
    interface Work{void run()throws Exception;}
    private final class FixtureWeb extends WebView {
        final List<String> loads=new ArrayList<>();String url=SchoolCatalog.CARD_ENTRY;int reloads;WebViewClient client;
        FixtureWeb(Context c){super(new MutableContextWrapper(c));}
        @Override public void loadUrl(String value){loads.add(value);url=value;}
        @Override public void loadUrl(String value,Map<String,String> headers){loadUrl(value);}
        @Override public void reload(){reloads++;}
        @Override public void setWebViewClient(WebViewClient value){client=value;super.setWebViewClient(value);}
        @Override public String getUrl(){return url;}
        @Override public void evaluateJavascript(String script,ValueCallback<String> callback){if(callback!=null)callback.onReceiveValue("\"ok\"");}
    }
    @Override public void callActivityOnCreate(Activity a,Bundle state){
        if(!(a instanceof SchoolWebActivity)){super.callActivityOnCreate(a,state);return;}
        try{
            activity=(SchoolWebActivity)a;
            // Reuse the existing payment-code injection seam, then restore the service under test.
            Class<?> cache=Class.forName("cn.xinxinxiangying.app.PaymentCodeWebView");
            Method owner=cache.getDeclaredMethod("owner",Context.class,boolean.class);owner.setAccessible(true);
            web=new FixtureWeb(a);set(cache,null,"cached",web);set(cache,null,"cachedOwner",owner.invoke(null,a,false));
            set(cache,null,"ready",true);set(cache,null,"deadline",SystemClock.elapsedRealtime()+60000);
            a.getIntent().putExtra("service","code");super.callActivityOnCreate(a,state);
            set(SchoolWebActivity.class,a,"service","card");web.url=SchoolCatalog.CARD_ENTRY;web.loads.clear();
        }catch(Exception e){throw new RuntimeException("offline fixture setup",e);}
    }
    private void click(String text)throws Exception{
        for(int i=0;i<25;i++){
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text))
                if(text.contentEquals(node.getText()==null?"":node.getText())&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){waitForIdleSync();return;}
            SystemClock.sleep(100);
        }
        throw new AssertionError("fixture dialog button missing");
    }
    private static final class FakePayTask extends com.alipay.sdk.app.PayTask {
        int calls;boolean accepted=true,fail;String received="";com.alipay.sdk.app.H5PayCallback callback;
        FakePayTask(Activity a){super(a);}
        @Override public synchronized boolean payInterceptorWithUrl(String url,boolean loading,com.alipay.sdk.app.H5PayCallback cb){
            calls++;received=url;callback=cb;if(fail)throw new IllegalStateException("SYNTHETIC_SDK_FAILURE");return accepted;
        }
        void complete(){callback.onPayResult(new com.alipay.sdk.util.H5PayResultModel());}
    }
    private WebResourceRequest post(String url){
        return new WebResourceRequest(){
            public Uri getUrl(){return Uri.parse(url);}public boolean isForMainFrame(){return true;}
            public boolean isRedirect(){return false;}public boolean hasGesture(){return true;}public String getMethod(){return "POST";}
            public Map<String,String> getRequestHeaders(){throw new AssertionError("payment headers must not be inspected");}
        };
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle report=new Bundle();int result=Activity.RESULT_OK;boolean owned=false;context=getTargetContext();
        try{
            check(new SchoolAccount(context).username(false).isEmpty()&&new SchoolAccount(context).username(true).isEmpty(),"empty device required");owned=true;
            new SchoolAccount(context).save(false,"SYNTHETIC_RECHARGE","",false);AppSession.invalidate(context);
            context.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();
            context.getSharedPreferences("school_session",0).edit().putString("identity","ug:SYNTHETIC_RECHARGE").commit();
            String[] permissions=context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
            check(!Arrays.asList(permissions).contains("android.permission.ACCESS_NETWORK_STATE")&&!Arrays.asList(permissions).contains("android.permission.ACCESS_WIFI_STATE"),"SDK manifest does not add network or Wi-Fi state permissions");
            activity=(SchoolWebActivity)startActivitySync(new Intent(context,SchoolWebActivity.class).putExtra("service","card").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            for(String url:new String[]{"weixin://wap/pay?SYNTHETIC=1","alipays://platformapi/startapp?SYNTHETIC=1","alipay://platformapi/startapp"}){
                Intent intent=(Intent)call("externalIntent",new Class<?>[]{String.class},url);
                check(intent!=null&&Intent.ACTION_VIEW.equals(intent.getAction())&&intent.hasCategory(Intent.CATEGORY_BROWSABLE),"payment is browsable VIEW");
                check(intent.getPackage()!=null&&intent.getComponent()==null&&intent.getExtras()==null,"payment is pinned and carries no extras");
            }
            String wrapper="intent://platformapi/startapp?SYNTHETIC=1#Intent;scheme=alipays;package=com.eg.android.AlipayGphone;component=evil.example/.Activity;S.browser_fallback_url=https%3A%2F%2Fevil.example;S.extra=SYNTHETIC;end";
            Intent intent=(Intent)call("externalIntent",new Class<?>[]{String.class},wrapper);
            check(intent!=null&&intent.getComponent()==null&&intent.getExtras()==null&&intent.getFlags()==0,"intent wrapper drops injected component, extras and fallback");
            for(String url:new String[]{"javascript:alert(1)","file:///private","http://example.com","intent://x#Intent;scheme=https;end","intent://x#Intent;scheme=weixin;package=evil.example;end","weixin://user@wap/pay"})
                check(call("externalIntent",new Class<?>[]{String.class},url)==null,"unsupported external target rejected");
            final String gateway="https://openapi.alipay.com/gateway.do?method=alipay.trade.wap.pay&SYNTHETIC=1";
            ui(()->{
                check(web.client.shouldInterceptRequest(web,post(gateway))!=null,"gateway blocked without school entry");
                web.client.onPageStarted(web,SchoolCatalog.CARD_ENTRY,null);
                check(!web.client.shouldOverrideUrlLoading(web,post(gateway)),"original payment navigation continues");
                check(web.client.shouldInterceptRequest(web,post(gateway))==null,"original POST is passed to WebView without reading headers");
                check(web.loads.isEmpty(),"gateway handoff never rebuilds a GET or resubmits payment");
                set(SchoolWebActivity.class,activity,"service","code");
                check(web.client.shouldInterceptRequest(web,post(gateway))!=null,"gateway exception excludes payment-code service");
                set(SchoolWebActivity.class,activity,"service","card");
                check(web.client.shouldInterceptRequest(web,post("https://openapi.alipay.com/other"))!=null,"other external POST stays blocked");
                web.url=gateway;web.client.onPageStarted(web,gateway,null);
                check(!(Boolean)field(SchoolWebActivity.class,activity,"navigationFailed"),"observed gateway is not stopped on page start");
                check((Boolean)call("isPaymentSource",new Class<?>[]{}),"gateway can hand off to the payment client");
            });
            final String cashier="https://mclient.alipay.com/cashier/mobilepay.htm?SYNTHETIC=%2B%26%25&return_url=https%3A%2F%2Fecard.sit.edu.cn%2F";
            final FakePayTask[] sdk={null};
            ui(()->{sdk[0]=new FakePayTask(activity);set(SchoolWebActivity.class,activity,"cardPayTask",sdk[0]);web.url="https://example.com/SYNTHETIC";});
            ui(()->call("startCardPayment",new Class<?>[]{String.class,int.class},cashier,2));
            check(sdk[0].calls==0,"untrusted page cannot initiate SDK payment");ui(()->web.url=gateway);
            IntentFilter payment=new IntentFilter(Intent.ACTION_VIEW);payment.addCategory(Intent.CATEGORY_BROWSABLE);payment.addDataScheme("alipays");
            ActivityMonitor monitor=addMonitor(payment,new ActivityResult(Activity.RESULT_CANCELED,null),true);
            ui(()->call("confirmExternal",new Class<?>[]{String.class},"alipays://platformapi/startapp?SYNTHETIC=1"));
            check(monitor.getHits()==0,"showing confirmation does not launch payment");click("取消");
            check(monitor.getHits()==0,"cancel does not launch payment");
            ui(()->call("confirmExternal",new Class<?>[]{String.class},"alipays://platformapi/startapp?SYNTHETIC=1"));click("打开");
            check(monitor.getHits()==1,"explicit confirmation launches exactly once");
            ui(()->call("confirmExternal",new Class<?>[]{String.class},cashier));click("取消");
            check(monitor.getHits()==1&&sdk[0].calls==0,"cashier cancellation never starts SDK or browser");
            ui(()->call("confirmExternal",new Class<?>[]{String.class},cashier));click("打开");
            check(monitor.getHits()==1&&sdk[0].calls==1&&cashier.equals(sdk[0].received),"confirmed cashier URL is passed unchanged to SDK, without browser handoff");removeMonitor(monitor);
            ui(()->call("startCardPayment",new Class<?>[]{String.class,int.class},cashier,2));
            check(sdk[0].calls==1,"in-flight SDK payment cannot be submitted twice");
            ui(()->sdk[0].complete());
            check(web.loads.size()==1&&SchoolCatalog.CARD_ENTRY.equals(web.loads.get(0)),"callback returns to the known school entry, not an untrusted result URL");
            ui(()->web.client.onPageFinished(web,SchoolCatalog.CARD_ENTRY));
            check(!(Boolean)field(SchoolWebActivity.class,activity,"cardResetAfterPay"),"return finishes at school entry with payment history cleared");
            ui(()->sdk[0].complete());check(web.loads.size()==1,"duplicate callback cannot replay navigation or payment");
            ui(()->{web.loads.clear();sdk[0].accepted=false;web.url=gateway;call("startCardPayment",new Class<?>[]{String.class,int.class},cashier,2);});
            check(!(Boolean)field(SchoolWebActivity.class,activity,"cardPaying")&&web.loads.isEmpty(),"unsupported SDK URL stops without browser fallback or resubmission");
            ui(()->{sdk[0].accepted=true;sdk[0].fail=true;call("startCardPayment",new Class<?>[]{String.class,int.class},cashier,2);});
            check(!(Boolean)field(SchoolWebActivity.class,activity,"cardPaying")&&web.loads.isEmpty(),"SDK exception releases busy state without retries");
            ui(()->{sdk[0].fail=false;call("startCardPayment",new Class<?>[]{String.class,int.class},cashier,2);set(SchoolWebActivity.class,activity,"pageVersion",3);sdk[0].complete();});
            check(web.loads.isEmpty(),"late payment callback cannot redirect another page");
            ui(()->call("confirmExternal",new Class<?>[]{String.class},"weixin://wap/pay?SYNTHETIC=1"));click("打开");
            check(!activity.isFinishing(),"missing payment app is handled without closing the school page");
            final int version=(Integer)field(SchoolWebActivity.class,activity,"pageVersion");
            ui(()->call("offerCardRestart",new Class<?>[]{int.class},version));waitForIdleSync();
            check(web.loads.isEmpty(),"expiry alone never navigates or submits");click("暂不");
            check(web.loads.isEmpty()&&web.reloads==0,"cancel never replays payment");
            ui(()->{set(SchoolWebActivity.class,activity,"cardExpired",false);call("offerCardRestart",new Class<?>[]{int.class},version);});click("重新打开");
            check(web.loads.size()==1&&SchoolCatalog.CARD_ENTRY.equals(web.loads.get(0))&&web.reloads==0,"explicit recovery uses one fresh entry GET");
            ui(()->call("showMenu",new Class<?>[]{}));click("重新加载");
            check(web.loads.size()==2&&SchoolCatalog.CARD_ENTRY.equals(web.loads.get(1))&&web.reloads==0,"menu reload opens entry instead of replaying the payment page");
            ui(()->{set(SchoolWebActivity.class,activity,"cardExpired",false);call("offerCardRestart",new Class<?>[]{int.class},-1);});
            check(!(Boolean)field(SchoolWebActivity.class,activity,"cardExpired"),"stale document cannot prompt recovery");
            final int beforeAccount=sdk[0].calls;
            final com.alipay.sdk.app.H5PayCallback staleCallback=sdk[0].callback;
            ui(()->{web.url=gateway;call("startCardPayment",new Class<?>[]{String.class,int.class},cashier,version);});
            check(sdk[0].calls==beforeAccount+1,"current account can start one controlled SDK operation");
            ui(()->staleCallback.onPayResult(new com.alipay.sdk.util.H5PayResultModel()));
            check((Boolean)field(SchoolWebActivity.class,activity,"cardPaying"),"old callback cannot clear another in-flight payment");
            new SchoolAccount(context).save(false,"SYNTHETIC_OTHER","",false);
            ui(()->sdk[0].complete());
            check(web.loads.size()==2,"payment callback after account change cannot navigate the new account");
            ui(()->call("offerCardRestart",new Class<?>[]{int.class},version));
            check(!(Boolean)field(SchoolWebActivity.class,activity,"cardExpired"),"old account callback cannot prompt recovery");
            ui(()->{
                set(SchoolWebActivity.class,activity,"service","electricity");
                String charge=SchoolCatalog.ECARD_PORTAL+"elecCharge";
                check(!web.client.shouldOverrideUrlLoading(web,post(SchoolCatalog.ELECTRICITY)),"electricity query opens");
                check(!web.client.shouldOverrideUrlLoading(web,post(charge)),"electricity query loads the room result");
                web.client.onPageStarted(web,charge,null);
                check(web.getVisibility()==android.view.View.INVISIBLE,"query keeps the recharge form hidden");
                activity.getIntent().putExtra("electricityRecharge",true);
                check(!web.client.shouldOverrideUrlLoading(web,post(charge)),"explicit recharge can open the school recharge page");
            });
            new SchoolAccount(context).save(false,"SYNTHETIC_RECHARGE","",false);
            ElectricityStore electricity=new ElectricityStore(context);
            org.json.JSONObject first=new org.json.JSONObject()
                .put("name","测试寝室").put("roomId","101").put("buildingId","2").put("value","0");
            electricity.savePortalBalance(first,false);
            check("0.00".equals(electricity.read().getJSONObject("balance").getString("value"))
                &&"元".equals(electricity.read().getJSONObject("balance").getString("unit")),
                "reported zero is a valid balance");
            org.json.JSONObject second=new org.json.JSONObject(first.toString())
                .put("name","另一寝室").put("roomId","102").put("value","84.77");
            boolean mismatch=false;
            try{electricity.savePortalBalance(second,false);}catch(IllegalStateException expected){mismatch=true;}
            check(mismatch&&"101".equals(electricity.read().getJSONObject("portalRoom").getString("roomId")),
                "refresh preserves the confirmed room when school returns a different one");
            electricity.savePortalBalance(second,true);
            check("84.77".equals(electricity.read().getJSONObject("balance").getString("value")),
                "explicit selection replaces the room and balance together");
            report.putString("result","PASS: "+checks+" offline recharge assertions");
        }catch(Throwable e){result=Activity.RESULT_CANCELED;report.putString("failure",e.toString());}
        finally{
            if(activity!=null)try{ui(()->activity.finish());}catch(Exception ignored){}
            if(owned){ElectricityStore.clearAll(context);new SchoolAccount(context).clear();context.getSharedPreferences("school_profile",0).edit().clear().commit();}
            finish(result,report);
        }
    }
}
