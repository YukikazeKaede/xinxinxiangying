package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import cn.xinxinxiangying.app.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Separate signed test APK. Synthetic fixtures only; restores prior timetable/profile data. */
public final class TimetableInstrumentation extends Instrumentation {
    private int checks;
    private final ArrayList<Activity> activities=new ArrayList<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private JSONObject extraction()throws Exception {
        JSONArray list=new JSONArray();
        String[] names={"测试·高等数学","测试·大学英语","测试·程序设计","测试·实验课"};
        int[] day={1,2,3,1},start={1,3,5,2},end={2,4,6,3};
        for(int i=0;i<names.length;i++)list.put(new JSONObject().put("kcmc",names[i]).put("xm","测试教师").put("cdmc","测试教室A10"+i).put("xqjmc","星期"+"一二三四五六日".charAt(day[i]-1)).put("jcs",start[i]+"-"+end[i]).put("zcd",i==2?"1-16周(单)":"1-16周"));
        return new JSONObject().put("semester","2026秋 · 合成测试").put("raw",new JSONArray().put(new JSONObject().put("data",new JSONObject().put("kbList",list))));
    }
    private Activity launch(Class<?> type,boolean web)throws Exception {
        Intent i=new Intent(getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("skipTimetable",true);
        if(web)i.putExtra("service","timetable").putExtra("postgraduate",false).putExtra("importMode",true);
        Activity a=startActivitySync(i);activities.add(a);waitForIdleSync();return a;
    }
    private void screenshot(String name)throws Exception {
        final java.util.ArrayList<Activity> secured=new java.util.ArrayList<>();runOnMainSync(()->{for(Activity a:activities)if(!a.isFinishing()&&(a.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0){secured.add(a);a.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);}});
        SystemClock.sleep(500);Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"screenshot available");
        File directory=getTargetContext().getExternalFilesDir(null);
        if(directory==null)directory=getTargetContext().getFilesDir();
        try(FileOutputStream stream=new FileOutputStream(new File(directory,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();runOnMainSync(()->{for(Activity a:secured)if(!a.isFinishing())a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);});
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String text){
        if(root==null)return null;if(root.getText()!=null&&text.equals(root.getText().toString()))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=find(root.getChild(i),text);if(found!=null)return found;}return null;
    }
    private boolean clickText(String text){AccessibilityNodeInfo node=find(getUiAutomation().getRootInActiveWindow(),text);return node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
    private AccessibilityNodeInfo findDescription(AccessibilityNodeInfo root,String description){
        if(root==null)return null;if(description.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=findDescription(root.getChild(i),description);if(found!=null)return found;}return null;
    }
    private boolean clickDescription(String description){AccessibilityNodeInfo node=findDescription(getUiAutomation().getRootInActiveWindow(),description);return node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
    private void publicFixtureScreenshot(Activity a,String name)throws Exception{
        // Only locally generated fixtures. Production keeps FLAG_SECURE on school/account screens.
        runOnMainSync(()->a.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE));
        screenshot(name);
        runOnMainSync(()->a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE));
    }
    private String uiText(AccessibilityNodeInfo root){if(root==null)return "";StringBuilder text=new StringBuilder();if(root.getText()!=null)text.append(root.getText()).append(" | ");for(int i=0;i<root.getChildCount();i++)text.append(uiText(root.getChild(i)));return text.toString();}
    private void restore(SharedPreferences prefs,Map<String,?> entries){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> item:entries.entrySet()){Object value=item.getValue();if(value instanceof String)e.putString(item.getKey(),(String)value);if(value instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)value);}e.commit();}
    @Override public void onStart(){
        SharedPreferences storage=getTargetContext().getSharedPreferences("timetables_v1",0);
        SharedPreferences profile=getTargetContext().getSharedPreferences("school_profile",0);
        Map<String,?> original=storage.getAll();boolean originalType=profile.getBoolean("postgraduate",false);
        SharedPreferences accounts=getTargetContext().getSharedPreferences("school_accounts",0),sessions=getTargetContext().getSharedPreferences("school_session",0);
        Map<String,?> originalAccounts=accounts.getAll(),originalSessions=sessions.getAll();accounts.edit().clear().commit();
        Bundle result=new Bundle();
        try {
            profile.edit().putBoolean("postgraduate",false).commit();
            new SchoolAccount(getTargetContext()).save(false,"SYNTHETIC_UI_TEST","",false);java.lang.reflect.Method grant=AppSession.class.getDeclaredMethod("verified",boolean.class,String.class);grant.setAccessible(true);grant.invoke(null,false,"SYNTHETIC_UI_TEST");TimetableRepository repo=new TimetableRepository(getTargetContext());
            JSONObject doc=TimetableParser.parse(extraction()).put("studentType","ug").put("sourceUrl","https://jwxt.sit.edu.cn/jwglxt/kbcx/test.html?ticket=TEST_SECRET").put("startDate","2026-09-07").put("totalWeeks",20);
            repo.save(doc);check(repo.read().getJSONArray("courses").length()==4,"repository round trip");
            check(!repo.read().getString("sourceUrl").contains("ticket"),"source URL strips session query");
            repo.save(doc);check(repo.read().getJSONArray("courses").length()==4,"replacement does not duplicate");
            String before=repo.read().toString();JSONObject invalid=new JSONObject(doc.toString());invalid.getJSONArray("courses").getJSONObject(0).put("startPeriod",0);
            try{repo.save(invalid);throw new AssertionError("invalid rejected");}catch(IllegalArgumentException expected){check(before.equals(repo.read().toString()),"invalid import preserves prior data");}
            profile.edit().putBoolean("postgraduate",true).commit();repo.clear();check(repo.read().getJSONArray("courses").length()==0,"type isolation");
            profile.edit().putBoolean("postgraduate",false).commit();check(repo.read().getJSONArray("courses").length()==4,"UG data remains");
            Activity timetable=launch(MainActivity.class,false);screenshot("timetable-synthetic.png");
            final SchoolWebActivity page=(SchoolWebActivity)launch(SchoolWebActivity.class,true);
            java.lang.reflect.Field field=SchoolWebActivity.class.getDeclaredField("web");field.setAccessible(true);final WebView view=(WebView)field.get(page);
            JSONArray rows=extraction().getJSONArray("raw").getJSONObject(0).getJSONObject("data").getJSONArray("kbList");
            final String responseJson=new JSONObject().put("kbList",rows).toString();
            final String html="<!doctype html><meta charset='utf-8'><h1>合成测试个人课表</h1><select id='xnm'><option value='2026'>2026秋 ·</option></select><select id='xqm'><option value='3'>合成测试</option></select><script>window.kbList="+rows.toString()+";</script>";
            runOnMainSync(()->{
                view.stopLoading();
                // Substitute transport only. The real importer, origin checks, parser,
                // preview dialog, repository, and timetable activity remain unchanged.
                view.setWebViewClient(new android.webkit.WebViewClient(){
                    @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,android.webkit.WebResourceRequest r){
                        if(r.getUrl().getPath().endsWith("xskbcx_cxXsgrkb.html")) return new android.webkit.WebResourceResponse("application/json","UTF-8",new java.io.ByteArrayInputStream(responseJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        return new android.webkit.WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    }
                });
                view.loadUrl("https://jwxt.sit.edu.cn/jwglxt/kbcx/test.html");
            });
            SystemClock.sleep(1800);waitForIdleSync();publicFixtureScreenshot(page,"ui-web.png");
            java.lang.reflect.Method method=SchoolWebActivity.class.getDeclaredMethod("importCurrentTimetable");method.setAccessible(true);
            runOnMainSync(()->{try{method.invoke(page);}catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(1600);waitForIdleSync();check(find(getUiAutomation().getRootInActiveWindow(),"导入本机")!=null,"WebView extraction reaches native preview");
            publicFixtureScreenshot(page,"ui-import-preview.png");
            check(clickText("取消"),"cancel preview");SystemClock.sleep(500);check(before.equals(repo.read().toString()),"cancel preserves prior timetable");
            runOnMainSync(()->{try{method.invoke(page);}catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(1000);check(clickText("导入本机"),"confirm import");SystemClock.sleep(1000);
            check(repo.read().getJSONArray("courses").length()==4,"confirmed import persisted");check(repo.read().getString("startDate").equals("2026-09-07"),"same semester keeps academic start date");
            SystemClock.sleep(2500);screenshot("timetable-imported.png");
            repo.clear();
            java.lang.reflect.Field screenField=MainActivity.class.getDeclaredField("timetable");screenField.setAccessible(true);Object screen=screenField.get(timetable);java.lang.reflect.Method reload=TimetableScreen.class.getDeclaredMethod("reload");reload.setAccessible(true);
            runOnMainSync(()->{try{reload.invoke(screen);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();screenshot("ui-empty.png");
            check(find(getUiAutomation().getRootInActiveWindow(),"登录并同步课表")!=null,"empty timetable has import action");
            
            check(clickDescription("工具"),"bottom navigation opens campus");SystemClock.sleep(600);waitForIdleSync();
            check(find(getUiAutomation().getRootInActiveWindow(),"校园卡")!=null,"campus service shortcuts visible");
            Activity campus=timetable;check(campus==timetable,"campus shares timetable activity");publicFixtureScreenshot(campus,"ui-campus.png");
            check(clickDescription("我的"),"bottom navigation opens profile");SystemClock.sleep(500);waitForIdleSync();
            check(find(getUiAutomation().getRootInActiveWindow(),"学生类型")!=null,"profile contains student identity");
            publicFixtureScreenshot(campus,"ui-profile.png");
            check(clickDescription("课表"),"profile navigation returns to timetable");SystemClock.sleep(500);waitForIdleSync();
            check(find(getUiAutomation().getRootInActiveWindow(),"登录并同步课表")!=null,"return to empty timetable");
            check(clickDescription("课表设置"),"timetable settings action");SystemClock.sleep(300);
            check(find(getUiAutomation().getRootInActiveWindow(),"开学日期与总周数")!=null,"settings menu retains academic settings");screenshot("ui-settings.png");
            check(clickText("开学日期与总周数"),"open semester settings");SystemClock.sleep(300);screenshot("ui-semester.png");check(clickText("取消"),"cancel semester settings");
            result.putString("result","PASS "+checks+" Android repository/WebView/preview/import checks");
        } catch(Throwable e){result.putString("failure",e.toString());result.putString("stack",android.util.Log.getStackTraceString(e));result.putString("ui",uiText(getUiAutomation().getRootInActiveWindow()));}
        finally {
            AppSession.invalidate(getTargetContext());runOnMainSync(()->{for(Activity activity:activities)if(!activity.isFinishing())activity.finish();});
            SharedPreferences.Editor restore=storage.edit().clear();for(Map.Entry<String,?> entry:original.entrySet())if(entry.getValue() instanceof String)restore.putString(entry.getKey(),(String)entry.getValue());restore.commit();
            profile.edit().putBoolean("postgraduate",originalType).commit();
            restore(accounts,originalAccounts);restore(sessions,originalSessions);CourseReminder.schedule(getTargetContext());TimetableWidget.update(getTargetContext());
        }
        finish(result.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
