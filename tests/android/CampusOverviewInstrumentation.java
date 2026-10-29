package cn.xinxinxiangying.app.test;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import cn.xinxinxiangying.app.*;
import org.json.*;
import java.lang.reflect.*;

/** Dedicated empty emulator only. No school requests are sent. */
public final class CampusOverviewInstrumentation extends Instrumentation {
    private int checks;private Context c;private ActivitiesActivity a;private WebView realWeb,mock;private long savedStamp;
    private void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private Object call(String n,Class<?>[] types,Object...args)throws Exception{Method m=ActivitiesActivity.class.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(a,args);}
    private TextView text(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=text(((ViewGroup)v).getChildAt(i),s);if(found!=null)return found;}return null;}
    private void ui(Work work)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{work.run();}catch(Throwable ex){e[0]=ex;}});if(e[0]!=null)throw new Exception("UI check failed",e[0]);}
    interface Work{void run()throws Exception;}
    private void renderArtifact(View v,String name)throws Exception{
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(v.getWidth(),v.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(CampusUi.BG);v.draw(canvas);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        bitmap.recycle();
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnCreate(Activity activity, Bundle state) {
        super.callActivityOnCreate(activity, state);
        if (!(activity instanceof ActivitiesActivity)) return;
        a=(ActivitiesActivity)activity;
        try {
            call("buildWeb",new Class<?>[]{});realWeb=(WebView)field(a,"web");
            mock=new WebView(a){@Override public void loadUrl(String url){}};
            set(a,"web",mock);set(a,"webPage",new LinearLayout(a));
        } catch(Exception e){throw new RuntimeException("offline fixture setup",e);}
    }
    @Override public void onStart(){Bundle report=new Bundle();int result=Activity.RESULT_OK;boolean owned=false;c=getTargetContext();
        try{
            check(new SchoolAccount(c).username(false).isEmpty()&&new SchoolAccount(c).username(true).isEmpty(),"fresh device only");owned=true;
            new SchoolAccount(c).save(false,"SYNTHETIC_ALIGNMENT","",false);AppSession.invalidate(c);
            c.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();
            c.getSharedPreferences("school_session",0).edit().putString("identity","ug:SYNTHETIC_ALIGNMENT").commit();
            JSONObject filters=new JSONObject().put("xn",new JSONArray().put(new JSONObject().put("code","2098-2099").put("name","2098-2099")))
                .put("xq",new JSONArray().put(new JSONObject().put("code","SYNTHETIC_TERM").put("name","SYNTHETIC_TERM_LABEL")).put(new JSONObject().put("code","SYNTHETIC_TERM_B").put("name","SYNTHETIC_SECOND_TERM")))
                .put("hdbmzt",new JSONArray().put(new JSONObject().put("code","FULL").put("name","报名已满")).put(new JSONObject().put("code","OPEN").put("name","报名中")));
            JSONArray rows=new JSONArray().put(new JSONObject().put("id","SYNTHETIC_A").put("name","SYNTHETIC_FULL").put("dlmc","德育").put("lbmc","讲座报告").put("lbdm","0101").put("dldm","01").put("end","2000-01-01").put("schoolState","报名已满"))
                .put(new JSONObject().put("id","SYNTHETIC_B").put("name","SYNTHETIC_OPEN").put("dlmc","劳育").put("lbmc","三创活动").put("lbdm","0201").put("dldm","02").put("schoolState","报名中"));
            for(int i=0;i<8;i++)rows.put(new JSONObject(rows.getJSONObject(0).toString()).put("id","SYNTHETIC_MORE_"+i).put("name","SYNTHETIC_MORE_"+i));
            JSONObject criteria=new JSONObject().put("text","").put("conditions",new JSONObject());
            JSONObject list=new JSONObject().put("page",1).put("pageSize",10).put("total",12).put("hasMore",true).put("query",criteria)
                .put("blocks",new JSONArray().put(new JSONObject().put("code","01").put("name","德育")).put(new JSONObject().put("code","02").put("name","劳育")))
                .put("source","school-list").put("filters",filters).put("scope",new JSONObject().put("xn","2098-2099").put("xq","SYNTHETIC_TERM"))
                .put("menus",new JSONArray()).put("groups",new JSONArray().put(new JSONObject().put("items",rows)));
            JSONObject saved=new JSONObject().put("savedAt",1).put("list",list);new ActivityStore(c).save(saved);
            a=(ActivitiesActivity)startActivitySync(new Intent(c,ActivitiesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            View root=a.getWindow().getDecorView();
            check(((View)field(a,"page")).isShown()&&!((View)field(a,"webPage")).isShown(),"entry refresh stays on native UI");
            check(!new JSONObject((String)field(a,"plan")).has("filters"),"entry refresh requests school current-term defaults");
            JSONObject early = new JSONObject(list.toString());
            early.getJSONObject("filters").getJSONArray("xq").put(new JSONObject().put("code","SYNTHETIC_EARLY").put("name","SYNTHETIC_EARLY_TERM"));
            ui(()->call("showOptions",new Class<?>[]{JSONObject.class},early));
            check(text(root,"SYNTHETIC_EARLY_TERM")!=null,"semester options display before activities finish");
            check(new ActivityStore(c).read().getJSONObject("list").getJSONObject("filters").getJSONArray("xq").length()==2,"early options do not replace complete cached snapshot");
            ui(()->call("failed",new Class<?>[]{String.class},"login"));
            check(((View)field(a,"webPage")).isShown(),"authentication is revealed only when required");
            ui(()->call("quitWeb",new Class<?>[]{}));
            check(text(root,"SYNTHETIC_FULL")!=null&&text(root,"SYNTHETIC_OPEN")!=null,"history and cross-block order remain visible");
            check("2098-2099".equals(field(a,"year")),"year comes from server scope");
            check("SYNTHETIC_TERM".equals(field(a,"term"))&&text(root,"SYNTHETIC_TERM_LABEL")!=null,"semester choices retain the server code");
            check(text(root,"报名已满")!=null,"official full status displayed");
            ui(()->call("onList",new Class<?>[]{JSONObject.class},new JSONObject().put("list",new JSONObject(list.toString()))));
            waitForIdleSync();
            for(int i=0;i<30;i++){
                final boolean[] tall={false};ui(()->{ScrollView v=(ScrollView)field(a,"activityScroll");tall[0]=v.getChildAt(0).getHeight()>v.getHeight();});
                if(tall[0])break;SystemClock.sleep(50);
            }
            ui(()->{ScrollView v=(ScrollView)field(a,"activityScroll");v.scrollTo(0,Math.max(0,v.getChildAt(0).getHeight()-v.getHeight()));});
            waitForIdleSync();
            check(new JSONObject((String)field(a,"plan")).getInt("page")==2,"scrolling to bottom requests next page; y="+((ScrollView)field(a,"activityScroll")).getScrollY()+", height="+((ScrollView)field(a,"activityScroll")).getHeight()+", content="+((ScrollView)field(a,"activityScroll")).getChildAt(0).getHeight()+", importing="+field(a,"importing")+", failed="+field(a,"loadMoreFailed"));
            check(((View)field(a,"page")).isShown(),"load more keeps native list visible");
            ui(()->{set(a,"listRead",true);call("failed",new Class<?>[]{String.class},"timeout");});
            check(ActivityFeed.flatten(new ActivityStore(c).read().getJSONObject("list")).length()==10,"failed next page preserves first ten rows");
            check((Boolean)field(a,"loadMoreFailed")&&!((Boolean)field(a,"importing")),"failure stops automatic retries");
            ui(()->text(root,"加载失败，点击重试").performClick());
            check(new JSONObject((String)field(a,"plan")).getInt("page")==2&&!((Boolean)field(a,"loadMoreFailed")),"retry requests the same next page");
            JSONObject next=new JSONObject(list.toString()).put("page",2).put("hasMore",false);
            JSONArray tail=new JSONArray().put(new JSONObject(rows.getJSONObject(0).toString()).put("id","SYNTHETIC_TAIL_A"))
                .put(new JSONObject(rows.getJSONObject(0).toString()).put("id","SYNTHETIC_TAIL_B"));
            next.put("groups",new JSONArray().put(new JSONObject().put("items",tail)));
            ui(()->call("onList",new Class<?>[]{JSONObject.class},new JSONObject().put("list",next)));
            waitForIdleSync();
            check(ActivityFeed.flatten(new ActivityStore(c).read().getJSONObject("list")).length()==12,"next page appends without replacing first page");
            check(((ScrollView)field(a,"activityScroll")).getScrollY()>0,"append preserves scrolling position");
            ui(()->text((View)field(a,"filterHost"),"报名已满").performClick());
            check(new JSONObject((String)field(a,"plan")).getJSONObject("conditions").getString("hdbmzt").equals("FULL"),"state filter is sent to server, not limited to loaded rows");
            JSONObject filtered=new JSONObject(list.toString()).put("total",1).put("hasMore",false)
                .put("query",new JSONObject().put("text","").put("conditions",new JSONObject().put("hdbmzt","FULL")))
                .put("groups",new JSONArray().put(new JSONObject().put("items",new JSONArray().put(rows.getJSONObject(0)))));
            ui(()->call("onList",new Class<?>[]{JSONObject.class},new JSONObject().put("list",filtered)));
            savedStamp=new ActivityStore(c).read().getLong("savedAt");
            check(text(root,"SYNTHETIC_FULL")!=null&&text(root,"SYNTHETIC_OPEN")==null,"official state filter works");
            ui(()->{set(a,"stateFilter","");call("render",new Class<?>[]{});});
            ui(()->{
                View filtersView=(View)field(a,"filterHost");
                View termRow=((ViewGroup)filtersView).getChildAt(0);check(((ViewGroup)termRow).getChildCount()==5,"semester selector added without changing list style");termRow=((ViewGroup)termRow).getChildAt(1);
                check(text(termRow,"全部")==null,"no unbounded all-semester option");
                text(termRow,"SYNTHETIC_SECOND_TERM").performClick();
            });
            check(((View)field(a,"page")).isShown()&&!((View)field(a,"webPage")).isShown(),"manual semester switch keeps the native list visible");
            JSONObject request=new JSONObject((String)field(a,"plan"));
            check("SYNTHETIC_TERM_B".equals(request.getJSONObject("filters").getString("xq")),"manual semester selection queries only that semester");
            check("2098-2099".equals(request.getJSONObject("filters").getString("xn")),"changing term preserves selected year");
            ui(()->call("quitWeb",new Class<?>[]{}));
            check("SYNTHETIC_TERM".equals(field(a,"term")),"cancelling restores cached scope");
            check(new ActivityStore(c).read().getLong("savedAt")==savedStamp,"cancel leaves old snapshot untouched");
            ui(()->{
                set(a,"detail",new JSONObject().put("id","SYNTHETIC_A").put("name","SYNTHETIC_DETAIL").put("end","2099-01-01").put("schoolState","报名已满"));
                call("renderDetail",new Class<?>[]{});
            });
            check(text(root,"报名已满") instanceof Button&&!text(root,"报名已满").isEnabled(),"full activity never enables native signup");
            check((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0,"screenshot protection retained");
            ui(()->{set(a,"detail",null);call("render",new Class<?>[]{});text(root,"我的活动").performClick();});
            check(new JSONObject((String)field(a,"plan")).optBoolean("mine"),"my activities changes the server query");
            check(text(root,"SYNTHETIC_FULL")==null,"all-activity cache never masquerades as my activities");
            JSONObject mineList=new JSONObject(list.toString()).put("mine",true);
            ui(()->call("onList",new Class<?>[]{JSONObject.class},new JSONObject().put("list",mineList)));
            check(text(root,"已报名")!=null,"my activity rows are marked as registered");
            ui(()->call("loadNextPage",new Class<?>[]{}));
            check(new JSONObject((String)field(a,"plan")).optBoolean("mine"),"next page preserves my-activity scope");
            ui(()->call("onList",new Class<?>[]{JSONObject.class},new JSONObject().put("list",new JSONObject(next.toString()).put("mine",true))));
            check(new ActivityStore(c).read().getJSONObject("list").optBoolean("mine"),"personal pagination remains in its scope");
            ui(()->{call("render",new Class<?>[]{});text(root,"所有活动").performClick();});
            check(!new JSONObject((String)field(a,"plan")).optBoolean("mine"),"switching back restores all-activity query");
            java.util.Calendar now=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            int year=now.get(java.util.Calendar.YEAR)-(now.get(java.util.Calendar.MONTH)<8?1:0);
            Method recent=ActivitiesActivity.class.getDeclaredMethod("recentYear",String.class);recent.setAccessible(true);
            for(int delta=-2;delta<=2;delta++)check(((Boolean)recent.invoke(null,(year+delta)+"-"+(year+delta+1)))==(Math.abs(delta)<=1),"only adjacent academic years remain");
            ui(()->a.finish());
            JSONObject credits=DektParser.parse(new JSONObject().put("earned","5.50").put("required","8")
                .put("rows",new JSONArray().put(new JSONObject().put("name","德育").put("earned","1.50").put("required","3"))
                    .put(new JSONObject().put("name","SYNTHETIC_CREDIT_CHILD").put("earned","0.50").put("required","1").put("depth",1))
                    .put(new JSONObject().put("name","劳育").put("earned","2").put("required","3"))
                    .put(new JSONObject().put("name","美育").put("earned","1").put("required","1"))
                    .put(new JSONObject().put("name","健康教育").put("earned","1").put("required","1"))));
            new DektStore(c).save(credits);
            Activity dekt=startActivitySync(new Intent(c,DektActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            View creditRoot=dekt.getWindow().getDecorView();
            check(text(creditRoot,"SYNTHETIC_CREDIT_CHILD")!=null,"credit details start expanded as requested");
            ui(()->((View)text(creditRoot,"德育").getParent()).performClick());
            check(text(creditRoot,"SYNTHETIC_CREDIT_CHILD")==null,"credit card collapses");
            ui(()->((View)text(creditRoot,"德育").getParent()).performClick());
            check(text(creditRoot,"SYNTHETIC_CREDIT_CHILD")!=null&&text(creditRoot,"查看活动")!=null,"credit card reopens and has activity action");
            check((dekt.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0,"credit screenshot protection stays enabled");
            ui(()->{View v=(View)field(dekt,"content");v.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(4000,View.MeasureSpec.AT_MOST));v.layout(0,0,v.getMeasuredWidth(),v.getMeasuredHeight());renderArtifact(v,"credits.png");dekt.finish();});
            JSONObject grades=AcademicParser.parse("grades",new JSONObject().put("allTerms",true).put("items",new JSONArray()
                .put(new JSONObject().put("kcmc","SYNTHETIC_OLD").put("xf","4").put("jd","4"))
                .put(new JSONObject().put("kcmc","SYNTHETIC_NEW").put("xf","2").put("jd","1"))),null);
            new AcademicRepository(c).save("grades",grades);
            c.getSharedPreferences("app_update",0).edit().putLong("last_check",System.currentTimeMillis()).commit();
            MainActivity main=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).putExtra("tab",2).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            ui(()->{Field f=MainActivity.class.getDeclaredField("paymentHostVisible");f.setAccessible(true);f.set(null,false);main.selectTab(1);});
            check(text(main.getWindow().getDecorView(),"3.00")!=null&&text(main.getWindow().getDecorView(),"5.50")!=null,"overview shows real synthetic weighted GPA and second-class total");
            ui(()->{View v=(View)field(main,"content");v.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(6000,View.MeasureSpec.AT_MOST));v.layout(0,0,v.getMeasuredWidth(),v.getMeasuredHeight());renderArtifact(v,"overview.png");main.finish();});
            Class.forName("AcademicParserTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
            Class.forName("ActivityFeedTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
            report.putString("result","PASS "+checks+" UI checks; ActivityFeedTest passed");
        }catch(Throwable e){report.putString("result","FAIL "+e.getClass().getSimpleName()+": "+e.getMessage());result=Activity.RESULT_CANCELED;}
        finally{
            try{ui(()->{if(realWeb!=null)realWeb.destroy();if(a!=null)a.finish();});}catch(Exception ignored){}
            if(owned){new ActivityStore(c).clear();new DektStore(c).clear();new AcademicRepository(c).clear();new SchoolAccount(c).clear();AppSession.invalidate(c);}
            finish(result,report);
        }
    }
}
