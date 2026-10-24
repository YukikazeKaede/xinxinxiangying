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
    static void retain(WebView view,String owner){
        clear();
        if(owner.isEmpty()||!MainActivity.paymentHostVisible){view.destroy();return;}
        view.setVisibility(View.INVISIBLE);view.stopLoading();
        if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
        view.setWebChromeClient(null);view.setDownloadListener(null);view.onPause();
        MutableContextWrapper context=(MutableContextWrapper)view.getContext();context.setBaseContext(context.getApplicationContext());
        cached=view;cachedOwner=owner;ready=false;deadline=SystemClock.elapsedRealtime()+60000;
        view.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){if(v==cached&&"about:blank".equals(url)){v.clearHistory();ready=true;}}});
        // onPause does not stop JS timers: discard the old document before making the instance reusable.
        view.loadUrl("about:blank");main.postDelayed(expire,60000);
    }
    static void clear(){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(PaymentCodeWebView::clear);return;}
        main.removeCallbacks(expire);WebView old=cached;cached=null;cachedOwner="";ready=false;
        if(old!=null){old.stopLoading();old.setWebViewClient(new WebViewClient());old.destroy();}
    }
}
