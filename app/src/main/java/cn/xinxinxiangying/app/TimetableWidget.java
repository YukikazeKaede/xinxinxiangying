package cn.xinxinxiangying.app;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import org.json.*;
import java.util.*;

/** Today's actual local courses; tapping opens the app. */
public final class TimetableWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){render(context,manager,ids);}
    public static void update(Context context){AppWidgetManager manager=AppWidgetManager.getInstance(context);render(context,manager,manager.getAppWidgetIds(new ComponentName(context,TimetableWidget.class)));}
    private static void render(Context context,AppWidgetManager manager,int[] ids){
        if(ids.length==0)return;String title="今日课表",body="打开应用登录学校账号，同步课表";
        try{JSONObject doc=new TimetableRepository(context).read();Calendar now=Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));now.set(Calendar.HOUR_OF_DAY,12);now.set(Calendar.MINUTE,0);now.set(Calendar.SECOND,0);now.set(Calendar.MILLISECOND,0);Calendar start=TimetableModel.date(doc.optString("startDate"));
            if(start==null)body="请在课表设置中选择开学日期";
            else{int week=TimetableModel.week(doc,now),day=(now.get(Calendar.DAY_OF_WEEK)+5)%7+1;Calendar last=(Calendar)start.clone();last.add(Calendar.DATE,doc.optInt("totalWeeks",20)*7);if(now.before(start)||!now.before(last))body="当前日期不在学期内";else{title="第"+week+"周 · 周"+"一二三四五六日".charAt(day-1);JSONArray courses=TimetableModel.day(doc,week,day);StringBuilder lines=new StringBuilder();for(int i=0;i<Math.min(5,courses.length());i++){JSONObject c=courses.getJSONObject(i);if(i>0)lines.append("\n");lines.append(c.optInt("startPeriod")).append("–").append(c.optInt("endPeriod")).append("节  ").append(c.optString("name")).append("  ").append(c.optString("room"));}body=courses.length()==0?"今天没有课程":lines.toString();if(courses.length()>5)body+="\n还有"+(courses.length()-5)+"项课程，打开查看";}}
        }catch(Exception e){body="课表暂时无法读取，请打开应用检查";}
        for(int id:ids){RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.timetable_widget);views.setTextViewText(R.id.widget_title,title);views.setTextViewText(R.id.widget_courses,body);Intent intent=new Intent(context,MainActivity.class);PendingIntent open=PendingIntent.getActivity(context,50,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);views.setOnClickPendingIntent(R.id.widget_root,open);manager.updateAppWidget(id,views);}
    }
}
