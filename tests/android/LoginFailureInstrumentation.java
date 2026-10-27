package cn.xinxinxiangying.app.test;
import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import android.view.accessibility.AccessibilityNodeInfo;
import cn.xinxinxiangying.app.*;
import java.io.*;
import java.util.*;
public final class LoginFailureInstrumentation extends Instrumentation {
    int checks;Activity page,login;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    Object get(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    @Override public void onStart(){Bundle result=new Bundle();Map<String,?> original=getTargetContext().getSharedPreferences("school_session",0).getAll();try{
        AppSession.invalidate(getTargetContext());ActivityMonitor monitor=addMonitor(SchoolLoginActivity.class.getName(),null,false);
        page=startActivitySync(new Intent(getTargetContext(),SchoolWebActivity.class).putExtra("service","timetable").putExtra("autoSync",true).putExtra("username","REJECTED_SYNTHETIC_USER").putExtra("password","WRONG_SYNTHETIC_PASSWORD").putExtra("clearSession",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        WebView web=(WebView)get(page,"web");final WebViewClient[] real={null};runOnMainSync(()->real[0]=web.getWebViewClient());String html="<!doctype html><meta charset='utf-8'><div id='msg'>用户名或密码错误</div><form><input id='username'><input id='password' type='password'><button type='submit'>登录</button></form>";
        runOnMainSync(()->{web.stopLoading();web.setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}@Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap b){real[0].onPageStarted(v,u,b);}@Override public void onPageFinished(WebView v,String u){real[0].onPageFinished(v,u);}});web.loadUrl("https://authserver.sit.edu.cn/authserver/login");});
        login=waitForMonitorWithTimeout(monitor,1000);removeMonitor(monitor);check(login==null&&!page.isFinishing(),"rejected credentials stay on the school form");check(!AppSession.valid(getTargetContext()),"rejected credentials never grant access");check(!new SchoolAccount(getTargetContext()).username(false).equals("REJECTED_SYNTHETIC_USER"),"rejected username never saved");waitForIdleSync();SystemClock.sleep(500);
        runOnMainSync(()->page.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE));SystemClock.sleep(300);android.graphics.Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"failure screenshot");try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"v06-rejected-login.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        result.putString("result","PASS "+checks+" wrong-password refusal checks");
    }catch(Throwable e){result.putString("failure",e.toString());result.putString("stack",android.util.Log.getStackTraceString(e));}finally{AppSession.invalidate(getTargetContext());runOnMainSync(()->{if(page!=null&&!page.isFinishing())page.finish();if(login!=null&&!login.isFinishing())login.finish();});android.content.SharedPreferences.Editor edit=getTargetContext().getSharedPreferences("school_session",0).edit().clear();for(Map.Entry<String,?> item:original.entrySet())if(item.getValue() instanceof String)edit.putString(item.getKey(),(String)item.getValue());edit.commit();}finish(result.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
