package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.xinxinxiangying.app.*;
import org.json.*;

/** Synthetic-only device. Dispatch real touch streams through the timetable container. */
public final class WeekSwipeInstrumentation extends Instrumentation {
    private int checks;private MainActivity activity;private TimetableScreen screen;private View root;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private Object field(Object target,String name)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private View stage(View view){if(view.getClass().getSimpleName().equals("WeekSwipeFrame"))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=stage(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private void render(int week,String mode)throws Exception{
        java.lang.reflect.Field w=TimetableScreen.class.getDeclaredField("week");w.setAccessible(true);
        JSONObject doc=(JSONObject)field(screen,"document");doc.put("viewMode",mode).put("totalWeeks",3);
        java.lang.reflect.Method r=TimetableScreen.class.getDeclaredMethod("render");r.setAccessible(true);
        runOnMainSync(()->{try{w.setInt(screen,week);r.invoke(screen);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
    }
    private void gesture(float dx,float dy,boolean cancel,boolean narrow){
        runOnMainSync(()->{View v=stage(root);if(narrow)v.layout(0,0,100,v.getHeight());float x=v.getWidth()/2f,y=150;long time=SystemClock.uptimeMillis();
            int[] actions={MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP};
            for(int i=0;i<actions.length;i++){MotionEvent e=MotionEvent.obtain(time,time+i*100,actions[i],x+(i==0?0:dx),y+(i==0?0:dy),0);v.dispatchTouchEvent(e);e.recycle();}
        });waitForIdleSync();
    }
    private int week()throws Exception{return (Integer)field(screen,"week");}
    @Override public void onStart(){Bundle report=new Bundle();Context c=getTargetContext();boolean owned=false;int result=Activity.RESULT_OK;
        try{
            check(new SchoolAccount(c).username(false).isEmpty()&&new SchoolAccount(c).username(true).isEmpty(),"requires a fresh synthetic device");owned=true;
            c.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();new SchoolAccount(c).save(false,"SYNTHETIC_SWIPE","",false);AppSession.invalidate(c);
            c.getSharedPreferences("school_session",0).edit().putString("identity","ug:SYNTHETIC_SWIPE").commit();c.getSharedPreferences("app_update",0).edit().putLong("last_check",System.currentTimeMillis()).commit();
            activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->screen=new TimetableScreen(activity,null,v->{root=v;activity.setContentView(v);}));
            float distance=c.getResources().getDisplayMetrics().density*90;
            render(2,"weekly");gesture(-distance,0,false,false);check(week()==3,"left swipe advances one week");
            gesture(-distance,0,false,false);check(week()==3,"last week is bounded");
            gesture(distance,0,false,false);check(week()==2,"right swipe goes back one week");
            render(1,"weekly");gesture(distance,0,false,false);check(week()==1,"first week is bounded");
            render(2,"weekly");gesture(-distance,0,true,false);check(week()==2,"cancel cannot switch weeks");
            gesture(-distance/5,0,false,false);check(week()==2,"short drag does not switch");
            gesture(-distance/2,distance,false,false);check(week()==2,"vertical gesture does not switch");
            gesture(-distance,0,false,true);check(week()==2,"narrow grid retains horizontal scrolling");
            render(2,"daily");gesture(-distance,0,false,false);check(week()==3,"daily view also switches weeks");
            report.putString("result","PASS "+checks+" checks");
        }catch(Throwable e){report.putString("result","FAIL "+e.getClass().getSimpleName()+": "+e.getMessage());result=Activity.RESULT_CANCELED;}
        finally{if(activity!=null)runOnMainSync(()->activity.finish());if(owned){new SchoolAccount(c).clear();AppSession.invalidate(c);}}
        finish(result,report);
    }
}
