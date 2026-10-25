package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.xinxinxiangying.app.*;
import org.json.*;

/** Synthetic-only device. Dispatch continuous touch streams through the full activity. */
public final class WeekSwipeInstrumentation extends Instrumentation {
    private int checks;private MainActivity activity;private TimetableScreen screen;private View root;private boolean keepFixture;
    @Override public void onCreate(Bundle args){super.onCreate(args);keepFixture=args!=null&&"true".equals(args.getString("keepFixture"));start();}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private Object field(Object target,String name)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private View stage(View view){if(view.getClass().getSimpleName().equals("WeekSwipeFrame"))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=stage(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private void render(int week,String mode)throws Exception{
        java.lang.reflect.Field w=TimetableScreen.class.getDeclaredField("week");w.setAccessible(true);
        JSONObject doc=(JSONObject)field(screen,"document");doc.put("viewMode",mode).put("totalWeeks",3);
        java.lang.reflect.Method r=TimetableScreen.class.getDeclaredMethod("render");r.setAccessible(true);
        runOnMainSync(()->{try{w.setInt(screen,week);r.invoke(screen);root=(View)field(screen,"root");}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
    }
    private int dp(float value){return Math.round(value*getTargetContext().getResources().getDisplayMetrics().density);}
    private void viewport(int width)throws Exception{java.util.concurrent.CountDownLatch laidOut=new java.util.concurrent.CountDownLatch(1);runOnMainSync(()->{try{FrameLayout pages=(FrameLayout)field(activity,"pages");pages.getLayoutParams().width=dp(width);pages.requestLayout();pages.postOnAnimation(()->pages.postOnAnimation(laidOut::countDown));}catch(Exception e){throw new RuntimeException(e);}});if(!laidOut.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("viewport layout did not finish");waitForIdleSync();}
    private HorizontalScrollView horizontal()throws Exception{return (HorizontalScrollView)((View)field(screen,"timetableView")).getParent();}
    private View activeStage()throws Exception{return stage((View)field(screen,"root"));}
    private void awaitSettled()throws Exception{for(int i=0;i<120;i++){boolean[] done={false};runOnMainSync(()->{try{done[0]=field(activeStage(),"settleAnimator")==null;}catch(Exception e){throw new RuntimeException(e);}});if(done[0]){waitForIdleSync();return;}SystemClock.sleep(25);}throw new AssertionError("page settling did not finish");}
    private long dragTime;private float dragX,dragY,lastX,lastY;private View dragStage;
    private void beginDrag(float dx)throws Exception{beginDrag(dx,8,30);}
    private void beginDrag(float dx,int steps,int delay)throws Exception{dragStage=activeStage();int[] at=new int[2];runOnMainSync(()->dragStage.getLocationOnScreen(at));dragX=at[0]+dragStage.getWidth()*.7f;dragY=at[1]+Math.min(dragStage.getHeight()/2f,dp(180));dragTime=SystemClock.uptimeMillis();sendDrag(MotionEvent.ACTION_DOWN,dragX,dragY);for(int i=1;i<=steps;i++){sendDrag(MotionEvent.ACTION_MOVE,dragX+dx*i/steps,dragY);SystemClock.sleep(delay);}}
    private int[] location(View view){int[] at=new int[2];runOnMainSync(()->view.getLocationOnScreen(at));return at;}
    private View described(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=described(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}return null;}
    private void sendDrag(int action,float x,float y){lastX=x;lastY=y;runOnMainSync(()->{MotionEvent event=MotionEvent.obtain(dragTime,SystemClock.uptimeMillis(),action,x,y,0);activity.dispatchTouchEvent(event);event.recycle();});}
    private void endDrag(boolean cancel)throws Exception{sendDrag(cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,lastX,lastY);awaitSettled();}
    private void gesture(float dx,float dy,boolean cancel,boolean narrow)throws Exception{
        float[] origin=new float[2];runOnMainSync(()->{try{root=(View)field(screen,"root");View v=stage(root);int[] position=new int[2];v.getLocationOnScreen(position);origin[0]=position[0]+v.getWidth()*.7f;origin[1]=position[1]+Math.min(v.getHeight()/2f,dp(180));}catch(Exception e){throw new RuntimeException(e);}});long time=SystemClock.uptimeMillis();
        for(int i=0;i<=9;i++){final int action=i==0?MotionEvent.ACTION_DOWN:i==9?(cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP):MotionEvent.ACTION_MOVE;final float fraction=Math.min(i,8)/8f;runOnMainSync(()->{MotionEvent e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,origin[0]+dx*fraction,origin[1]+dy*fraction,0);activity.dispatchTouchEvent(e);e.recycle();});SystemClock.sleep(25);}
        waitForIdleSync();awaitSettled();
    }
    private int week()throws Exception{return (Integer)field(screen,"week");}
    @Override public void onStart(){Bundle report=new Bundle();Context c=getTargetContext();boolean owned=false;int result=Activity.RESULT_OK;
        try{
            check(new SchoolAccount(c).username(false).isEmpty()&&new SchoolAccount(c).username(true).isEmpty(),"requires a fresh synthetic device");owned=true;
            c.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();new SchoolAccount(c).save(false,"SYNTHETIC_SWIPE","",false);AppSession.invalidate(c);
            c.getSharedPreferences("school_session",0).edit().putString("identity","ug:SYNTHETIC_SWIPE").commit();c.getSharedPreferences("app_update",0).edit().putLong("last_check",System.currentTimeMillis()).commit();
            JSONArray fixture=new JSONArray();for(int target=1;target<=3;target++)for(int day=1;day<=5;day++)for(int slot=0;slot<3;slot++){int period=1+slot*3+(target+day)%2;fixture.put(new JSONObject().put("id","SYNTHETIC_"+target+"_"+day+"_"+slot).put("name","合成课程"+(day+slot*5)).put("weekday",day).put("startPeriod",period).put("endPeriod",period+1).put("startWeek",target).put("endWeek",target).put("parity","all").put("room","测试教室"+(day+slot)));}new TimetableRepository(c).save(new JSONObject().put("courses",fixture).put("semester","合成测试学期").put("totalWeeks",3).put("startDate","2026-10-12").put("periodTimes",TimetableModel.defaultPeriodTimes()));
            activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            screen=(TimetableScreen)field(activity,"timetable");viewport(360);
            float distance=c.getResources().getDisplayMetrics().density*90;
            render(2,"weekly");gesture(-distance,0,false,false);check(week()==3,"left swipe advances one week");
            gesture(-distance,0,false,false);check(week()==3,"last week is bounded");
            gesture(distance,0,false,false);check(week()==2,"right swipe goes back one week");
            render(1,"weekly");gesture(distance,0,false,false);check(week()==1,"first week is bounded");
            render(2,"weekly");gesture(-distance,0,true,false);check(week()==2,"cancel cannot switch weeks");
            gesture(-distance/5,0,false,false);check(week()==2,"short drag does not switch");
            gesture(-distance/2,-distance,false,false);check(week()==2,"vertical gesture does not switch");
            check(((ScrollView)horizontal().getParent()).getScrollY()>0,"vertical timetable scrolling remains available");
            render(2,"weekly");viewport(393);View sized=(View)field(screen,"timetableView");check(sized.getWidth()==stage(root).getWidth(),"grid follows actual viewport width (grid="+sized.getWidth()+", viewport="+stage(root).getWidth()+", requested="+field(sized,"width")+")");gesture(-distance,0,false,false);check(week()==3,"393dp viewport switches weeks");
            render(2,"weekly");viewport(320);HorizontalScrollView scroll=horizontal();runOnMainSync(()->scroll.scrollTo(0,0));gesture(-distance,0,false,true);check(week()==2&&scroll.getScrollX()>0,"wide grid scrolls before paging");
            runOnMainSync(()->scroll.scrollTo(scroll.getChildAt(0).getWidth(),0));gesture(-distance,0,false,true);check(week()==3,"right table edge permits next week");
            gesture(distance,0,false,true);check(week()==2,"left table edge permits previous week");viewport(360);
            render(2,"weekly");View title=(View)field(screen,"weekTitle"),nav=(View)field(activity,"navHost");int[] titleAt=location(title),navAt=location(nav);beginDrag(-dp(120));View moving=(View)field(dragStage,"page"),incoming=(View)field(dragStage,"preview");check(Math.abs(moving.getTranslationX()+dp(120))<dp(2),"current page follows the finger");check(incoming!=null&&incoming.getTranslationX()>0&&incoming.getTranslationX()<dragStage.getWidth(),"adjacent week enters alongside the current page");check(week()==2,"dragging does not commit a week prematurely");check((Integer)field(field(dragStage,"previewGrid"),"pageWeek")==3,"preview uses the actual adjacent week");Object block=((java.util.List<?>)field(field(dragStage,"previewGrid"),"blocks")).get(0);check(((JSONObject)((java.util.List<?>)field(block,"courses")).get(0)).optInt("startWeek")==3,"preview contains the adjacent week's courses");check(java.util.Arrays.equals(titleAt,location(title))&&java.util.Arrays.equals(navAt,location(nav)),"week header and bottom navigation stay fixed");endDrag(false);check(week()==3,"release settles onto the adjacent week");
            render(2,"weekly");ScrollView vertical=(ScrollView)horizontal().getParent();runOnMainSync(()->vertical.scrollTo(0,dp(160)));beginDrag(-dp(120));check(((ScrollView)field(dragStage,"preview")).getScrollY()==vertical.getScrollY(),"preview preserves the vertical timetable position");endDrag(false);check(((ScrollView)horizontal().getParent()).getScrollY()==dp(160),"committed week preserves vertical scroll position");
            render(2,"weekly");beginDrag(-dp(40));endDrag(false);check(week()==2,"insufficient drag rebounds without paging");check(((View)field(activeStage(),"page")).getTranslationX()==0&&field(activeStage(),"preview")==null,"rebound removes the preview and resets translation");
            render(2,"weekly");beginDrag(-dp(60),4,5);endDrag(false);check(week()==3,"quick flick can page without a long drag");
            render(2,"weekly");beginDrag(-dp(120));endDrag(true);check(week()==2,"cancelled long drag rebounds to the current week");
            render(1,"weekly");beginDrag(dp(120));check(((View)field(dragStage,"page")).getTranslationX()>0&&((View)field(dragStage,"page")).getTranslationX()<dp(120),"semester edge applies resistance");check(field(dragStage,"preview")==null,"semester edge never invents an adjacent page");endDrag(false);check(week()==1,"edge rebound remains on the first week");
            render(2,"weekly");beginDrag(-dp(120));sendDrag(MotionEvent.ACTION_MOVE,dragX,dragY);endDrag(false);check(week()==2,"reversing a held drag returns to the same week");
            render(2,"weekly");beginDrag(-dp(120));sendDrag(MotionEvent.ACTION_UP,lastX,lastY);SystemClock.sleep(40);beginDrag(0);check(((View)field(dragStage,"page")).getTranslationX()<0,"new touch can interrupt an unfinished settle");endDrag(true);check(week()==2,"interrupted settle can return to the original week");
            render(2,"weekly");beginDrag(-dp(120));runOnMainSync(()->screen.pause());check(field(activeStage(),"preview")==null&&((View)field(activeStage(),"page")).getTranslationX()==0,"pause cleans up a partial page transition");sendDrag(MotionEvent.ACTION_CANCEL,lastX,lastY);awaitSettled();check(week()==2,"pause cannot commit a partially dragged week");
            render(2,"weekly");View next=described((View)field(screen,"root"),"下一周");runOnMainSync(()->next.performClick());awaitSettled();check(week()==3,"next-week button uses the same animated paging");View previous=described((View)field(screen,"root"),"上一周");runOnMainSync(()->previous.performClick());awaitSettled();check(week()==2,"previous-week button returns with animated paging");
            render(2,"daily");gesture(-distance,0,false,false);check(week()==3,"daily view also switches weeks");
            report.putString("result","PASS "+checks+" checks");
        }catch(Throwable e){report.putString("result","FAIL "+e.getClass().getSimpleName()+": "+e.getMessage());result=Activity.RESULT_CANCELED;}
        finally{if(activity!=null)runOnMainSync(()->activity.finish());if(owned&&!keepFixture){new SchoolAccount(c).clear();AppSession.invalidate(c);}}
        finish(result,report);
    }
}
