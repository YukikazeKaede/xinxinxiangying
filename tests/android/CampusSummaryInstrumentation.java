package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import cn.xinxinxiangying.app.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Calendar edge cases and a synthetic tool-page screenshot; no school network is needed. */
public final class CampusSummaryInstrumentation extends Instrumentation {
    private int checks;private MainActivity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private boolean contains(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(contains(((ViewGroup)v).getChildAt(i),text))return true;return false;}
    private String summary(JSONObject doc,String date)throws Exception{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("campusSummary",JSONObject.class,Calendar.class);m.setAccessible(true);return (String)m.invoke(null,doc,TimetableModel.date(date));}
    @Override public void onStart(){Bundle report=new Bundle();Context c=getTargetContext();boolean owned=false;int result=Activity.RESULT_OK;
        try{
            check(new SchoolAccount(c).username(false).isEmpty()&&new SchoolAccount(c).username(true).isEmpty(),"requires a fresh synthetic device");owned=true;
            c.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();new SchoolAccount(c).save(false,"SYNTHETIC_SUMMARY","",false);AppSession.invalidate(c);
            c.getSharedPreferences("school_session",0).edit().putString("identity","ug:SYNTHETIC_SUMMARY").commit();c.getSharedPreferences("app_update",0).edit().putLong("last_check",System.currentTimeMillis()).commit();
            JSONObject doc=new JSONObject().put("courses",new JSONArray());check(summary(doc,"2026-09-14").contains("同步课表"),"empty timetable has an action hint");
            JSONObject course=new JSONObject().put("id","SYNTHETIC_COURSE").put("name","合成课程").put("weekday",1).put("startPeriod",1).put("endPeriod",2).put("startWeek",1).put("endWeek",2);
            doc.put("courses",new JSONArray().put(course));check(summary(doc,"2026-09-14").contains("设置开学日期"),"missing start date does not invent week");doc.put("startDate","2026-09-14").put("totalWeeks",2);
            check(summary(doc,"2026-09-14").equals("第 1 周 · 今日 1 项课程安排"),"first Monday includes today's course");
            check(summary(doc,"2026-09-22").equals("第 2 周 · 今日没有课程"),"correct second week and empty day");
            check(summary(doc,"2026-09-13").equals("当前不在教学周"),"before term is not clamped to first week");check(summary(doc,"2026-09-28").equals("当前不在教学周"),"term end is exclusive");
            doc.put("patches",new JSONArray().put(new JSONObject().put("kind","move").put("week",1).put("day",1).put("targetWeek",1).put("targetDay",2)));
            check(summary(doc,"2026-09-14").contains("没有课程")&&summary(doc,"2026-09-15").contains("1 项"),"summary respects moved courses");
            activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).putExtra("tab",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();SystemClock.sleep(500);
            Bitmap picture=getUiAutomation().takeScreenshot();check(picture!=null,"tool page screenshot");try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"SYNTHETIC-campus-summary.png"))){picture.compress(Bitmap.CompressFormat.PNG,100,out);}picture.recycle();
            check(contains(activity.getWindow().getDecorView(),"同步课表，查看今日课程"),"empty state appears on tools page");
            new TimetableRepository(c).save(doc);runOnMainSync(()->activity.selectTab(1));waitForIdleSync();
            Calendar now=Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));check(contains(activity.getWindow().getDecorView(),summary(doc,new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(now.getTime()))),"reselecting tools refreshes changed local timetable");
            report.putString("result","PASS "+checks+" checks");
        }catch(Throwable e){report.putString("result","FAIL "+e.getClass().getSimpleName()+": "+e.getMessage());result=Activity.RESULT_CANCELED;}
        finally{if(activity!=null)runOnMainSync(()->activity.finish());if(owned){new TimetableRepository(c).clear();new SchoolAccount(c).clear();AppSession.invalidate(c);}}
        finish(result,report);
    }
}
