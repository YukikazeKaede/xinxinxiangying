package cn.xinxinxiangying.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import java.util.*;

/** One upcoming alarm at a time; standard alarms may be delayed by Android battery policy. */
public final class CourseReminder extends BroadcastReceiver {
    private static PendingIntent alarm(Context c,Intent data){return PendingIntent.getBroadcast(c,77,data,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void schedule(Context context){
        Context c=context.getApplicationContext();AlarmManager alarms=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);Intent intent=new Intent(c,CourseReminder.class).setAction("cn.xinxinxiangying.COURSE");PendingIntent old=alarm(c,intent);alarms.cancel(old);
        try{JSONObject doc=new TimetableRepository(c).read();int minutes=doc.optInt("reminderMinutes",-1);if(minutes<0)return;JSONArray times=doc.optJSONArray("periodTimes");Calendar start=TimetableModel.date(doc.optString("startDate"));if(start==null||times==null||times.length()==0)return;
            Map<String,JSONArray> schedule=TimetableModel.schedule(doc);long now=System.currentTimeMillis(),best=Long.MAX_VALUE;JSONObject next=null;for(int w=1;w<=doc.optInt("totalWeeks",20);w++)for(int d=1;d<=7;d++){Calendar date=(Calendar)start.clone();date.add(Calendar.DATE,(w-1)*7+d-1);if(date.getTimeInMillis()+86400000L<now)continue;JSONArray courses=schedule.get(w+":"+d);for(int i=0;i<courses.length();i++){JSONObject course=courses.getJSONObject(i);int period=course.optInt("startPeriod");if(period<1||period>times.length())continue;String[] hm=times.getJSONObject(period-1).getString("start").split(":");Calendar when=(Calendar)date.clone();when.set(Calendar.HOUR_OF_DAY,Integer.parseInt(hm[0]));when.set(Calendar.MINUTE,Integer.parseInt(hm[1]));when.set(Calendar.SECOND,0);long due=when.getTimeInMillis()-minutes*60000L;if(due>now&&due<best){best=due;next=course;}}}
            if(next!=null){intent.putExtra("name",next.optString("name")).putExtra("room",next.optString("room")).putExtra("minutes",minutes).putExtra("due",best);alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,best,alarm(c,intent));}
        }catch(Exception ignored){}
    }
    @Override public void onReceive(Context c,Intent intent){
        if("cn.xinxinxiangying.COURSE".equals(intent.getAction())&&Math.abs(System.currentTimeMillis()-intent.getLongExtra("due",0))<10*60000L){
            NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);String channel="course_reminders";if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(channel,"课前提醒",NotificationManager.IMPORTANCE_DEFAULT));
            if(Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){PendingIntent open=PendingIntent.getActivity(c,78,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel):new Notification.Builder(c);int minutes=intent.getIntExtra("minutes",0);manager.notify(77,b.setSmallIcon(R.drawable.ic_notification).setContentTitle(intent.getStringExtra("name")).setContentText((minutes==0?"即将上课":minutes+"分钟后上课")+" · "+intent.getStringExtra("room")).setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());}
        }
        final PendingResult pending=goAsync();new Thread(()->{try{TimetableWidget.update(c);schedule(c);}finally{pending.finish();}},"course-schedule").start();
    }
}
