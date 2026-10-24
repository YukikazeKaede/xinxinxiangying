package cn.xinxinxiangying.app;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.*;

/** One warm, blank WebView. Never retains a QR document or an Activity while idle. */
final class PaymentCodeWebView {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final Runnable expire=PaymentCodeWebView::clear;
    private static final Runnable preloadTimeout=PaymentCodeWebView::clear;
    private static WebView cached;private static String cachedOwner="";private static long deadline;private static boolean ready;
    private PaymentCodeWebView(){}
    static String owner(Context c,boolean pg){
        if(!AppSession.valid(c)||pg!=c.getSharedPreferences("school_profile",0).getBoolean("postgraduate",false))return "";
        return (pg?"pg":"ug")+new SchoolAccount(c).scope(pg);
    }
    static WebView obtain(Activity activity,String owner){
        if(!owner.isEmpty()&&owner.equals(cachedOwner)&&ready&&SystemClock.elapsedRealtime()<deadline){
            WebView view=cached;cached=null;cachedOwner="";ready=false;main.removeCallbacks(expire);
            ((MutableContextWrapper)view.getContext()).setBaseContext(activity);view.onResume();return view;
        }
        clear();return new WebView(new MutableContextWrapper(activity));
    }
    static void preload(Activity activity,boolean pg){
        String scope=owner(activity,pg);
        if(!MainActivity.paymentHostVisible||scope.isEmpty()||cached!=null)return;
        WebView view=new WebView(new MutableContextWrapper(activity.getApplicationContext()));
        WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSaveFormData(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);
        cached=view;cachedOwner=scope;ready=false;deadline=SystemClock.elapsedRealtime()+60000;
        String url=SchoolCatalog.url("code",pg);
        view.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                if(r.isForMainFrame()&&!url.equals(r.getUrl().toString())||!SchoolCatalog.isSchoolUrl(r.getUrl().toString()))return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return block(v,r.getUrl().toString());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String target){return block(v,target);}
            private boolean block(WebView v,String target){if(url.equals(target))return false;if(v==cached)clear();return true;}
            @Override public void onPageStarted(WebView v,String target,android.graphics.Bitmap icon){if(v==cached&&!url.equals(target))clear();}
            @Override public void onPageFinished(WebView v,String target){if(v==cached&&url.equals(target)&&target.equals(v.getUrl()))blank(v);}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler handler,android.net.http.SslError error){handler.cancel();if(v==cached)clear();}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error){if(v==cached&&r.isForMainFrame())clear();}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(v==cached&&r.isForMainFrame())clear();}
        });
        // Delay network work until the tools UI has completed its current lifecycle callback.
        main.post(()->{if(view==cached&&MainActivity.paymentHostVisible&&scope.equals(owner(activity,pg)))view.loadUrl(url);else if(view==cached)clear();});
        main.postDelayed(preloadTimeout,10000);main.postDelayed(expire,60000);
    }
    static void retain(WebView view,String owner){
        clear();
        if(owner.isEmpty()||!MainActivity.paymentHostVisible){view.destroy();return;}
        view.setVisibility(View.INVISIBLE);view.stopLoading();
        if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
        view.setWebChromeClient(null);view.setDownloadListener(null);view.onPause();
        MutableContextWrapper context=(MutableContextWrapper)view.getContext();context.setBaseContext(context.getApplicationContext());
        cached=view;cachedOwner=owner;ready=false;deadline=SystemClock.elapsedRealtime()+60000;
        blank(view);main.postDelayed(expire,60000);
    }
    private static void blank(WebView view){
        main.removeCallbacks(preloadTimeout);view.stopLoading();
        view.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){if(v==cached&&"about:blank".equals(url)){v.clearHistory();v.onPause();ready=true;}}});
        // onPause does not stop JS timers: discard the old document before making the instance reusable.
        view.loadUrl("about:blank");
    }
    static void clear(){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(PaymentCodeWebView::clear);return;}
        main.removeCallbacks(expire);main.removeCallbacks(preloadTimeout);WebView old=cached;cached=null;cachedOwner="";ready=false;
        if(old!=null){old.stopLoading();old.setWebViewClient(new WebViewClient());old.destroy();}
    }
}
