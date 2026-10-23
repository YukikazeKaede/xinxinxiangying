package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.*;
import android.widget.TextView;
import cn.xinxinxiangying.app.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Run only on a fresh test device; uses synthetic profiles and generated pictures. */
public final class CampusProfileInstrumentation extends Instrumentation {
    private int checks;private MainActivity main;private SchoolWebActivity electric;private Uri picture;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private View find(View v,String value){
        if(v instanceof TextView&&value.contentEquals(((TextView)v).getText())||v.getContentDescription()!=null&&value.contentEquals(v.getContentDescription()))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),value);if(found!=null)return found;}
        return null;
    }
    private void capture(String name)throws Exception{
        SystemClock.sleep(2500);runOnMainSync(()->main.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE));SystemClock.sleep(300);
        Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"synthetic screenshot");
        try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        runOnMainSync(()->main.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE));
    }
    private Object field(Object target,String name)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void checkElectricity(Context c)throws Exception{
        electric=(SchoolWebActivity)startActivitySync(new Intent(c,SchoolWebActivity.class).putExtra("service","electricity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        WebView web=(WebView)field(electric,"web");TextView status=(TextView)field(electric,"status");
        String html="<!doctype html><meta charset='utf-8'>";
        for(String id:new String[]{"elesys","school","area","build","floor","room"})html+="<div class='weui-cell'><input id='"+id+"'></div>";
        html+="<script>var ids=['elesys','school','area'],titles=['新电费缴纳','奉贤校区','住宿区'];var data=[{}],data1=[],data2=[];function jQuery(x){if(Array.isArray(x)){x.each=function(f){x.forEach(function(v,i){f(i,v)})};return x}return {data:function(){return x.picker}}}jQuery.fn={select:function(){}};ids.forEach(function(id,i){var input=document.getElementById(id);input.picker={config:{items:i?[]:[{title:titles[i],value:'SYNTHETIC_'+i}]},updateInputValue:function(v,t){input.value=t[0];input.setAttribute('data-values',v[0]);if(i<2)setTimeout(function(){window['data'+(i+1)]=[{}];document.getElementById(ids[i+1]).picker.config.items=[{title:titles[i+1],value:'SYNTHETIC_'+(i+1)}]},20)}}});</script>";
        final String fixture=html;final WebViewClient[] real={null};
        runOnMainSync(()->{real[0]=web.getWebViewClient();web.stopLoading();web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(fixture.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
            @Override public void onPageStarted(WebView v,String u,Bitmap b){real[0].onPageStarted(v,u,b);}
            @Override public void onPageFinished(WebView v,String u){real[0].onPageFinished(v,u);}
        });web.loadUrl("https://ecard.sit.edu.cn/epay/h5/payele");});
        final String[] message={""};for(int i=0;i<50;i++){SystemClock.sleep(100);runOnMainSync(()->message[0]=status.getText().toString());if(message[0].contains("请选择楼栋"))break;}
        check(message[0].contains("请选择楼栋"),"native WebView hook completes electricity prefill");
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);final String[] result={""};
        runOnMainSync(()->web.evaluateJavascript("['elesys','school','area'].every(id=>document.getElementById(id).closest('.weui-cell').style.display==='none')&&['build','floor','room'].every(id=>document.getElementById(id).value==='')",value->{result[0]=value;done.countDown();}));
        check(done.await(3,java.util.concurrent.TimeUnit.SECONDS)&&"true".equals(result[0]),"only default rows are hidden and remaining inputs are untouched");
        java.lang.reflect.Method save=SchoolWebActivity.class.getDeclaredMethod("saveProfileName",JSONObject.class);save.setAccessible(true);
        save.invoke(electric,new JSONObject().put("studentOwner","SYNTHETIC_STUDENT_B").put("studentName","合成其他学生"));
        check("合成学生".equals(new LocalProfile(c,false).name()),"mismatched identity cannot overwrite name");
        save.invoke(electric,new JSONObject().put("studentOwner","SYNTHETIC_STUDENT_A").put("studentName","合成同步姓名"));
        check("合成同步姓名".equals(new LocalProfile(c,false).name()),"verified account name reaches profile storage");new LocalProfile(c,false).saveName("合成学生");
        runOnMainSync(()->electric.finish());waitForIdleSync();
    }
    @Override public void onStart(){
        Bundle report=new Bundle();Context c=getTargetContext();
        try{
            check(new SchoolAccount(c).username(false).isEmpty(),"test requires a fresh device without real accounts");
            c.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();
            new SchoolAccount(c).save(false,"SYNTHETIC_STUDENT_A","",false);
            c.getSharedPreferences("school_session",0).edit().putString("identity","ug:SYNTHETIC_STUDENT_A").commit();
            c.getSharedPreferences("app_update",0).edit().putLong("last_check",System.currentTimeMillis()).commit();
            AppSession.invalidate(c);c.getSharedPreferences("school_session",0).edit().putString("identity","ug:SYNTHETIC_STUDENT_A").commit();
            LocalProfile a=new LocalProfile(c,false);check(a.saveName("合成学生"),"name is saved");
            check("合成学生".equals(new LocalProfile(c,false).name()),"name survives a new store instance");
            check(!a.saveName("\n")&&!a.saveName("x\ny"),"invalid names are rejected");
            main=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).putExtra("tab",2).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();
            final View root=main.getWindow().getDecorView();check(find(root,"已保存的教务页面")==null,"profile saved-pages entry removed");
            final int[][] positions={new int[2],new int[2]};
            runOnMainSync(()->{find(root,"合成学生").getLocationInWindow(positions[0]);find(root,"SYNTHETIC_STUDENT_A").getLocationInWindow(positions[1]);});
            check(positions[0][1]<positions[1][1],"name appears above student ID");
            ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"SYNTHETIC_AVATAR.jpg");values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
            picture=c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);check(picture!=null,"synthetic picker source created");
            Bitmap source=Bitmap.createBitmap(1600,1200,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(source);canvas.drawColor(0xff167568);Paint paint=new Paint();paint.setColor(0xffffd389);canvas.drawCircle(800,600,360,paint);
            try(OutputStream out=c.getContentResolver().openOutputStream(picture)){source.compress(Bitmap.CompressFormat.JPEG,90,out);}source.recycle();
            try(ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(picture,"rw")){android.media.ExifInterface exif=new android.media.ExifInterface(fd.getFileDescriptor());exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");exif.saveAttributes();}
            IntentFilter chooseFilter=new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);chooseFilter.addCategory(Intent.CATEGORY_OPENABLE);chooseFilter.addDataType("image/*");
            ActivityMonitor pick=addMonitor(chooseFilter,new ActivityResult(Activity.RESULT_OK,new Intent().setData(picture)),true);
            runOnMainSync(()->find(main.getWindow().getDecorView(),"更换头像").performClick());
            Bitmap saved=null;for(int i=0;i<40&&saved==null;i++){SystemClock.sleep(100);saved=a.avatar();}check(pick.getHits()>0,"system image picker intent intercepted");removeMonitor(pick);
            check(saved!=null&&saved.getWidth()<=512&&saved.getHeight()<=512,"picker result copied and downsampled locally");
            check(saved!=null&&saved.getWidth()<saved.getHeight(),"camera EXIF orientation is normalized");if(saved!=null)saved.recycle();
            try(OutputStream out=c.getContentResolver().openOutputStream(picture,"wt")){out.write("SYNTHETIC_INVALID_IMAGE".getBytes("UTF-8"));}
            try{a.saveAvatar(picture);throw new AssertionError("invalid picture accepted");}catch(IOException expected){checks++;}
            c.getContentResolver().delete(picture,null,null);picture=null;
            saved=new LocalProfile(c,false).avatar();check(saved!=null,"avatar remains after original picture removal");if(saved!=null)saved.recycle();
            ActivityMonitor cancel=addMonitor(chooseFilter,new ActivityResult(Activity.RESULT_CANCELED,null),true);
            runOnMainSync(()->find(main.getWindow().getDecorView(),"更换头像").performClick());SystemClock.sleep(200);removeMonitor(cancel);
            saved=a.avatar();check(saved!=null,"cancel keeps previous avatar");if(saved!=null)saved.recycle();
            try{a.saveAvatar(Uri.parse("file:///SYNTHETIC_INVALID"));throw new AssertionError("non-content URI accepted");}catch(IOException expected){checks++;}
            // Account isolation includes both the student type and account hash.
            check(new LocalProfile(c,true).name().isEmpty(),"student types have separate names");
            new SchoolAccount(c).save(false,"SYNTHETIC_STUDENT_B","",false);LocalProfile b=new LocalProfile(c,false);
            check(b.name().isEmpty()&&b.avatar()==null,"second account cannot see first profile");
            new SchoolAccount(c).save(false,"SYNTHETIC_STUDENT_A","",false);
            checkElectricity(c);
            runOnMainSync(()->{main.selectTab(1);});waitForIdleSync();
            check(find(root,"教务资料")==null&&find(root,"已保存的教务页面")==null&&find(root,"宿舍电费")!=null,"tools retains services without saved-pages section");capture("campus-tools.png");
            runOnMainSync(()->main.selectTab(2));waitForIdleSync();capture("campus-profile.png");
            ActivityMonitor logout=addMonitor(SchoolLoginActivity.class.getName(),null,false);
            runOnMainSync(()->{View exit=find(main.getWindow().getDecorView(),"退出登录");while(!exit.hasOnClickListeners())exit=(View)exit.getParent();exit.performClick();});waitForIdleSync();SystemClock.sleep(300);
            boolean clicked=false;for(AccessibilityNodeInfo node:getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("清除"))if("清除".contentEquals(node.getText()==null?"":node.getText()))clicked=node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            check(clicked,"logout confirmation accepted");Activity login=waitForMonitorWithTimeout(logout,5000);removeMonitor(logout);
            check(login instanceof SchoolLoginActivity&&!AppSession.valid(c),"logout returns to mandatory login");runOnMainSync(()->{if(login!=null)login.finish();});
            check(a.name().isEmpty()&&a.avatar()==null,"logout clears name and avatar");
            try{a.saveAvatar(Uri.parse("content://SYNTHETIC_INVALID"));throw new AssertionError("invalid picture accepted");}catch(Exception expected){checks++;}
            report.putString("result","PASS "+checks+" campus profile checks");
        }catch(Throwable e){report.putString("failure",e.toString());report.putString("stack",android.util.Log.getStackTraceString(e));}
        finally{
            if(electric!=null)runOnMainSync(()->electric.finish());
            if(picture!=null)c.getContentResolver().delete(picture,null,null);
            if(new SchoolAccount(c).username(false).startsWith("SYNTHETIC_")){
                if(main!=null)runOnMainSync(()->main.finish());LocalProfile.clear(c);new SchoolAccount(c).clear();
                c.getSharedPreferences("school_session",0).edit().clear().commit();
            }
        }
        finish(report.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,report);
    }
}
