package cn.xinxinxiangying.app;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Offline weekly timetable; school credentials never enter this screen. */
public final class TimetableScreen extends android.content.ContextWrapper {
    private final Activity activity;private final java.util.function.Consumer<View> display;
    private final int ink=CampusUi.INK, mint=CampusUi.GREEN, pale=CampusUi.BG;
    private TimetableRepository repository;
    private TimetableTools tools;
    private View timetableView;
    private TextView liveStatus;private final android.os.Handler statusHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable statusTick=new Runnable(){public void run(){updateStatus();statusHandler.postDelayed(this,30000);}};
    private int visibleDayCount=7;
    private int selectedDay=(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)+5)%7+1;
    private JSONObject document=new JSONObject();
    private LinearLayout root;
    private TextView weekTitle, semesterTitle;
    private int week=1;
    private boolean initialized;
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView label(String value,int size,int color) { TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v; }
    private Button button(String value,View.OnClickListener listener){return CampusUi.button(this,value,false,listener);}
    private Button capsule(String value,boolean selected,View.OnClickListener listener){return CampusUi.button(this,value,selected,listener);}
    public TimetableScreen(Activity activity,Bundle state,java.util.function.Consumer<View> display){
        super(activity);this.activity=activity;this.display=display;repository=new TimetableRepository(this);tools=new TimetableTools(activity,()->{initialized=false;reload();});
        if(state!=null){week=state.getInt("week",1);selectedDay=state.getInt("day",selectedDay);initialized=true;}
    }
    public void resume(){reload();statusHandler.removeCallbacks(statusTick);statusHandler.post(statusTick);}
    public void pause(){statusHandler.removeCallbacks(statusTick);}
    public void saveState(Bundle state){state.putInt("week",week);state.putInt("day",selectedDay);}
    public void showSettings(){menu();}
    private void reload(){
        try{document=repository.read();}catch(RuntimeException e){document=new JSONObject();Toast.makeText(this,"本机课表暂时无法读取，可从设置中重新导入",Toast.LENGTH_LONG).show();}if(document==null)document=new JSONObject();
        if(!initialized){week=currentWeek();initialized=true;}
        week=Math.max(1,Math.min(totalWeeks(),week));render();
    }
    private int totalWeeks(){return Math.max(1,Math.min(60,document.optInt("totalWeeks",20)));}
    private Calendar startCalendar(){
        try{SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);format.setLenient(false);Date date=format.parse(document.optString("startDate",""));Calendar c=Calendar.getInstance();c.setTime(date);return c;}catch(Exception e){return null;}
    }
    private int currentWeek(){
        Calendar start=startCalendar();if(start==null)return 1;
        Calendar today=Calendar.getInstance();int days=0;
        // Calendar day stepping avoids daylight-saving changes and partial-day rounding.
        start.set(Calendar.HOUR_OF_DAY,12);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
        today.set(Calendar.HOUR_OF_DAY,12);today.set(Calendar.MINUTE,0);today.set(Calendar.SECOND,0);today.set(Calendar.MILLISECOND,0);
        if(today.before(start))return 1;
        while(start.before(today)&&days<420){start.add(Calendar.DATE,1);days++;}
        return Math.min(totalWeeks(),days/7+1);
    }
    private void render(){
        root=CampusUi.column(this);root.setBackgroundColor(pale);
        JSONArray courses=document.optJSONArray("courses");boolean hasCourses=courses!=null&&courses.length()>0;
        Map<String,JSONArray> schedule=TimetableModel.schedule(document);JSONArray visibleCourses=new JSONArray();for(int day=1;day<=7;day++){JSONArray items=schedule.get(week+":"+day);if(items!=null)for(int i=0;i<items.length();i++)visibleCourses.put(items.optJSONObject(i));}
        visibleDayCount=document.optInt("visibleDays",7);if(document.optBoolean("smartWeekend",false)){visibleDayCount=5;for(int i=0;i<visibleCourses.length();i++){JSONObject c=visibleCourses.optJSONObject(i);if(c!=null)visibleDayCount=Math.max(visibleDayCount,c.optInt("displayDay",5));}}
        LinearLayout header=CampusUi.column(this);header.setBackground(CampusUi.wash(false));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(14),dp(10),dp(12),dp(4));
        LinearLayout settings=new LinearLayout(this);settings.setGravity(Gravity.CENTER);settings.setBackground(CampusUi.ripple(this,Color.TRANSPARENT,8));settings.addView(new CampusUi.Icon(this,"settings",ink),new LinearLayout.LayoutParams(dp(21),dp(21)));settings.setContentDescription("课表设置");settings.setFocusable(true);settings.setOnClickListener(v->menu());top.addView(settings,new LinearLayout.LayoutParams(dp(44),dp(44)));
        weekTitle=label("第 "+week+" 周 ⌄",17,ink);weekTitle.setGravity(Gravity.CENTER_VERTICAL);weekTitle.setOnClickListener(v->chooseWeek());weekTitle.setContentDescription("第"+week+"周，点击切换教学周");weekTitle.setFocusable(true);top.addView(weekTitle,new LinearLayout.LayoutParams(0,dp(44),1));
        Button previous=button("‹",v->{week=Math.max(1,week-1);render();});previous.setTextSize(23);previous.setContentDescription("上一周");previous.setEnabled(week>1);top.addView(previous,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button next=button("›",v->{week=Math.min(totalWeeks(),week+1);render();});next.setTextSize(23);next.setContentDescription("下一周");next.setEnabled(week<totalWeeks());LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(44),dp(44));np.leftMargin=dp(6);top.addView(next,np);header.addView(top);
        LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(dp(22),0,dp(16),dp(9));
        semesterTitle=label(hasCourses?document.optString("semester","我的课表"):"我的课表",12,CampusUi.MUTED);semesterTitle.setOnClickListener(v->tools.semesters());semesterTitle.setContentDescription("切换学期");semesterTitle.setGravity(Gravity.CENTER_VERTICAL);meta.addView(semesterTitle,new LinearLayout.LayoutParams(0,dp(40),1));
        boolean daily="daily".equals(document.optString("viewMode"));Button mode=button(daily?"周课表":"每日安排",v->setViewMode(daily?"weekly":"daily"));mode.setTextSize(12);mode.setGravity(Gravity.CENTER);mode.setPadding(dp(10),0,dp(10),0);mode.setBackground(CampusUi.ripple(this,0x88ffffff,8));mode.setOnClickListener(v->setViewMode(daily?"weekly":"daily"));meta.addView(mode,new LinearLayout.LayoutParams(-2,dp(40)));header.addView(meta);root.addView(header);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(Color.WHITE);
        if(daily){renderDaily(schedule);}else{
            ScrollView vertical=new ScrollView(this);vertical.setFillViewport(true);vertical.setVerticalScrollBarEnabled(false);HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setFillViewport(true);horizontal.setHorizontalScrollBarEnabled(false);
            int screen=getResources().getDisplayMetrics().widthPixels;int width=Math.max(screen,dp(30+visibleDayCount*44));Grid grid=new Grid(visibleCourses,width);timetableView=grid;horizontal.addView(grid,new HorizontalScrollView.LayoutParams(width,grid.height));vertical.addView(horizontal,new ScrollView.LayoutParams(-1,-2));stage.addView(vertical,new FrameLayout.LayoutParams(-1,-1));
            if(!hasCourses){LinearLayout invitation=CampusUi.column(this);invitation.setPadding(dp(16),dp(12),dp(16),dp(12));invitation.setBackground(CampusUi.shape(this,CampusUi.APRICOT,12));TextView notice=label("还没有课表，登录学校账号后自动同步",12,0xff906518);notice.setGravity(Gravity.CENTER);invitation.addView(notice);Button sync=CampusUi.button(this,"登录并同步课表",true,v->startImport());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(44));bp.topMargin=dp(10);invitation.addView(sync,bp);Button local=button("手动添加课程 / 管理学期",v->tools.semesters());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.topMargin=dp(6);invitation.addView(local,lp);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);ip.setMargins(dp(30),0,dp(30),dp(22));stage.addView(invitation,ip);}
            else {Button sync=CampusUi.button(this,"更新课表",true,v->startImport());FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-2,dp(44),Gravity.BOTTOM|Gravity.RIGHT);fp.setMargins(0,0,dp(16),dp(15));stage.addView(sync,fp);}
            root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        }
        liveStatus=null;if(document.optBoolean("showStatus",false)){liveStatus=label("",12,mint);liveStatus.setGravity(Gravity.CENTER);liveStatus.setPadding(dp(16),dp(12),dp(16),dp(12));liveStatus.setBackground(CampusUi.ripple(this,CampusUi.SOFT,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(20),dp(5),dp(20),0);root.addView(liveStatus,p);liveStatus.setOnClickListener(v->tools.times());updateStatus();}
        display.accept(root);
    }
    private void updateStatus(){if(liveStatus==null)return;JSONArray times=document.optJSONArray("periodTimes");Calendar today=Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));Calendar start=TimetableModel.date(document.optString("startDate"));if(start==null||times==null||times.length()==0){liveStatus.setText("设置开学日期和上课时间，查看当前课程");return;}Calendar end=(Calendar)start.clone();end.add(Calendar.DATE,document.optInt("totalWeeks",20)*7);Calendar normalized=(Calendar)today.clone();normalized.set(Calendar.HOUR_OF_DAY,12);if(normalized.before(start)||!normalized.before(end)){liveStatus.setText("当前日期不在本学期内");return;}JSONArray courses=TimetableModel.day(document,TimetableModel.week(document,today),(today.get(Calendar.DAY_OF_WEEK)+5)%7+1);String now=String.format(Locale.ROOT,"%02d:%02d",today.get(Calendar.HOUR_OF_DAY),today.get(Calendar.MINUTE));for(int i=0;i<courses.length();i++){JSONObject c=courses.optJSONObject(i);if(c.optInt("endPeriod")>times.length())continue;String from=times.optJSONObject(c.optInt("startPeriod")-1).optString("start"),to=times.optJSONObject(c.optInt("endPeriod")-1).optString("end");if(now.compareTo(to)<0){liveStatus.setText((now.compareTo(from)>=0?"正在上课：":"下一节 "+from+"：")+c.optString("name")+"  "+c.optString("room"));return;}}liveStatus.setText("今天的课程已结束");}
    private void chooseWeek(){String[] options=new String[totalWeeks()];for(int i=0;i<options.length;i++)options[i]="第 "+(i+1)+" 周";new AlertDialog.Builder(this).setTitle("选择教学周").setSingleChoiceItems(options,week-1,(d,i)->{week=i+1;d.dismiss();render();}).setNegativeButton("取消",null).show();}
    private void startImport(){startActivity(new Intent(this,SchoolLoginActivity.class));}
    private void menu(){String[] options={"登录 / 自动同步课表","我的学期","管理课程","调课与停课","开学日期与总周数","上课时间","课前提醒","课表外观","分享与备份","回到本周","清空当前课表","切换学校账号"};new AlertDialog.Builder(this).setTitle("课表设置").setItems(options,(d,i)->{if(i==0)startImport();if(i==1)tools.semesters();if(i==2)tools.courses();if(i==3)tools.patches();if(i==4)settings();if(i==5)tools.times();if(i==6)tools.reminders();if(i==7)tools.appearance();if(i==8)tools.share(timetableView==null?root:timetableView);if(i==9){week=currentWeek();render();}if(i==10)new AlertDialog.Builder(this).setTitle("清空当前课表？").setMessage("将删除本机当前学期课表。其他学期保留。").setNegativeButton("取消",null).setPositiveButton("清空",(x,w)->{repository.clear();reload();}).show();if(i==11)startActivity(new Intent(this,SchoolLoginActivity.class).putExtra("edit",true));}).setNegativeButton("关闭",null).show();}
    private void setViewMode(String mode){try{document.put("viewMode",mode);repository.save(document);reload();}catch(Exception e){Toast.makeText(this,"视图设置失败",Toast.LENGTH_SHORT).show();}}
    public void result(int request,int result,Intent data){tools.result(request,result,data);}
    private void renderDaily(Map<String,JSONArray> schedule){
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(12),0,dp(12),dp(10));for(int d=1;d<=7;d++){final int day=d;TextView tab=label("周"+"一二三四五六日".charAt(d-1),12,d==selectedDay?Color.WHITE:mint);tab.setGravity(Gravity.CENTER);tab.setBackground(CampusUi.ripple(this,d==selectedDay?mint:CampusUi.SOFT,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(2),0,dp(2),0);tabs.addView(tab,p);tab.setOnClickListener(v->{selectedDay=day;render();});}root.addView(tabs);
        ScrollView scroll=new ScrollView(this);LinearLayout list=CampusUi.column(this);list.setPadding(dp(20),dp(8),dp(20),dp(16));JSONArray items=schedule.get(week+":"+selectedDay);if(items==null)items=new JSONArray();
        if(items.length()==0){TextView empty=label("今天没有课程\n留一点时间给自己",18,CampusUi.MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(50),0,dp(50));list.addView(empty);}
        JSONArray times=document.optJSONArray("periodTimes");for(int i=0;i<items.length();i++){JSONObject c=items.optJSONObject(i);LinearLayout card=CampusUi.column(this);card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(CampusUi.ripple(this,Color.WHITE,22));String time="第"+c.optInt("startPeriod")+"–"+c.optInt("endPeriod")+"节";if(times!=null&&times.length()>=c.optInt("endPeriod"))time+="  "+times.optJSONObject(c.optInt("startPeriod")-1).optString("start")+"–"+times.optJSONObject(c.optInt("endPeriod")-1).optString("end");card.addView(label(time,12,mint));TextView title=label(c.optString("name"),20,ink);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,dp(9),0,dp(8));card.addView(title);card.addView(label(c.optString("room")+(c.optString("teacher").isEmpty()?"":" · "+c.optString("teacher")),13,CampusUi.MUTED));card.setOnClickListener(v->details(c));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);list.addView(card,p);}
        timetableView=list;scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void settings(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(24),dp(12),dp(24),0);
        final String[] date={document.optString("startDate","")};
        TextView hint=label("选择第一教学周的周一",14,ink);form.addView(hint);
        Button dateButton=button(date[0].isEmpty()?"设置开学日期":date[0],null);form.addView(dateButton);
        dateButton.setOnClickListener(v->{Calendar c=startCalendar();if(c==null)c=Calendar.getInstance();new DatePickerDialog(this,(picker,y,m,day)->{Calendar chosen=Calendar.getInstance();chosen.set(y,m,day);if(chosen.get(Calendar.DAY_OF_WEEK)!=Calendar.MONDAY){new AlertDialog.Builder(this).setMessage("请选择第一教学周的周一，以便正确计算教学周。").setPositiveButton("知道了",null).show();return;}date[0]=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,day);dateButton.setText(date[0]);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});
        TextView weeksLabel=label("总教学周数（1–60）",14,ink);weeksLabel.setPadding(0,dp(16),0,dp(6));form.addView(weeksLabel);
        NumberPicker picker=new NumberPicker(this);picker.setMinValue(1);picker.setMaxValue(60);picker.setValue(totalWeeks());form.addView(picker);
        new AlertDialog.Builder(this).setTitle("学期设置").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",(d,i)->{try{document.put("startDate",date[0]);document.put("totalWeeks",picker.getValue());repository.save(document);reload();}catch(Exception e){Toast.makeText(this,"设置保存失败，请重试",Toast.LENGTH_SHORT).show();}}).show();
    }
    private boolean occurs(JSONObject course){JSONArray weeks=course.optJSONArray("weeks");if(weeks!=null&&weeks.length()>0){for(int i=0;i<weeks.length();i++)if(weeks.optInt(i)==week)return true;return false;}if(week<course.optInt("startWeek",1)||week>course.optInt("endWeek",totalWeeks()))return false;String parity=course.optString("parity","all");return !("odd".equals(parity)&&week%2==0||"even".equals(parity)&&week%2!=0);}
    private String weekRange(JSONObject course){JSONArray weeks=course.optJSONArray("weeks");if(weeks!=null&&weeks.length()>0){StringBuilder b=new StringBuilder();for(int i=0;i<weeks.length();i++){if(i>0)b.append("、");b.append(weeks.optInt(i));}return "第 "+b+" 周";}String p=course.optString("parity","all");return "第 "+course.optInt("startWeek",1)+"–"+course.optInt("endWeek",totalWeeks())+" 周"+("odd".equals(p)?"（单周）":"even".equals(p)?"（双周）":"");}
    private void details(JSONObject course){String note=course.optString("note","");new AlertDialog.Builder(this).setTitle(course.optString("name","未命名课程")).setMessage("教师："+course.optString("teacher","")+"\n教室："+course.optString("room","")+"\n星期"+"一二三四五六日".charAt(Math.max(0,Math.min(6,course.optInt("displayDay",course.optInt("weekday",1))-1)))+" · 第 "+course.optInt("startPeriod",1)+"–"+course.optInt("endPeriod",1)+" 节\n"+weekRange(course)+(note.isEmpty()?"":"\n备注："+note)).setPositiveButton("关闭",null).setNeutralButton("编辑课程",(d,w)->tools.editCourse(course)).show();}
    private Bitmap loadWallpaper(){try{String path=document.optString("wallpaper","");if(path.isEmpty())return null;android.net.Uri uri=android.net.Uri.parse(path);if(!"content".equals(uri.getScheme()))return null;BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}options.inSampleSize=1;while(options.outWidth/options.inSampleSize>1600||options.outHeight/options.inSampleSize>1600)options.inSampleSize*=2;options.inJustDecodeBounds=false;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){return BitmapFactory.decodeStream(in,null,options);}}catch(Exception e){return null;}}
    private final class Block {int day,start,end;ArrayList<JSONObject> courses=new ArrayList<>();RectF bounds;}
    private final class Grid extends View {
        final ArrayList<Block> blocks=new ArrayList<>();final Paint paint=new Paint(3);final int width,height,rows;final float left=dp(30),header=dp(54),row=dp(59),column;final int dayCount;final Bitmap wallpaper;
        final int[] colors={0xffdceee4,0xffe0e9f7,0xfff6e8d5,0xffebe2f1,0xffdceef0,0xfff5e0dc,0xffe9edda};
        Grid(JSONArray courses,int width){super(TimetableScreen.this);this.width=width;dayCount=visibleDayCount;column=(width-left)/(float)dayCount;wallpaper=loadWallpaper();int maximum=12;
            if("blue".equals(document.optString("palette"))){for(int i=0;i<colors.length;i++)colors[i]=i%2==0?0xffdce9fa:0xffe7eef8;}else if("apricot".equals(document.optString("palette"))){for(int i=0;i<colors.length;i++)colors[i]=i%2==0?0xfff6e8d5:0xfff3dfd7;}
            for(int day=1;day<=dayCount;day++){ArrayList<JSONObject> list=new ArrayList<>();for(int i=0;i<courses.length();i++){JSONObject c=courses.optJSONObject(i);if(c==null||c.optInt("displayDay",c.optInt("weekday"))!=day)continue;maximum=Math.max(maximum,Math.min(16,c.optInt("endPeriod",12)));if(c.optInt("startPeriod",1)<=16)list.add(c);}Collections.sort(list,(a,b)->Integer.compare(a.optInt("startPeriod",1),b.optInt("startPeriod",1)));
                Block active=null;for(JSONObject c:list){int start=Math.max(1,c.optInt("startPeriod",1)),end=Math.max(start,Math.min(16,c.optInt("endPeriod",start)));if(active==null||start>active.end){active=new Block();active.day=day;active.start=start;active.end=end;blocks.add(active);}else active.end=Math.max(active.end,end);active.courses.add(c);}}
            rows=maximum;height=(int)(header+rows*row+dp(12));setContentDescription("第"+week+"周课表，七天、"+rows+"节。点击课程查看详情。");setFocusable(true);
        }
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(width,height);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(0xfffefcfe);if(wallpaper!=null){paint.setAlpha(65);canvas.drawBitmap(wallpaper,null,new Rect(0,0,width,height),paint);paint.setAlpha(255);}paint.setTypeface(Typeface.DEFAULT);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(12));paint.setColor(mint);
            Calendar date=startCalendar();if(date!=null)date.add(Calendar.DATE,(week-1)*7);
            String[] days={"一","二","三","四","五","六","日"};for(int i=0;i<dayCount;i++){float x=left+(i+.5f)*column;paint.setColor(mint);canvas.drawText(days[i],x,dp(21),paint);if(date!=null){paint.setTextSize(dp(10));canvas.drawText((date.get(Calendar.MONTH)+1)+"/"+date.get(Calendar.DAY_OF_MONTH),x,dp(39),paint);paint.setTextSize(dp(12));date.add(Calendar.DATE,1);}}
            for(int r=0;r<rows;r++){paint.setColor(CampusUi.LINE);paint.setStrokeWidth(dp(.5f));paint.setColor(CampusUi.MUTED);paint.setTextSize(dp(11));canvas.drawText(String.valueOf(r+1),left/2,header+r*row+dp(17),paint);JSONArray times=document.optJSONArray("periodTimes");if(times!=null&&r<times.length()){JSONObject time=times.optJSONObject(r);if(time!=null){paint.setTextSize(dp(7));canvas.drawText(time.optString("start"),left/2,header+r*row+dp(30),paint);canvas.drawText(time.optString("end"),left/2,header+r*row+dp(42),paint);}}}
            for(Block block:blocks){float x=left+(block.day-1)*column+dp(2),y=header+(block.start-1)*row+dp(2);block.bounds=new RectF(x,y,x+column-dp(4),header+block.end*row-dp(2));JSONObject first=block.courses.get(0);paint.setColor(colors[Math.floorMod(first.optString("name").hashCode(),colors.length)]);paint.setAlpha(document.optInt("cardAlpha",255));canvas.drawRoundRect(block.bounds,dp(9),dp(9),paint);paint.setAlpha(255);
                String text=block.courses.size()>1?block.courses.size()+"门课\n冲突":first.optString("name","课程")+(first.optString("room","").isEmpty()?"":"\n@"+first.optString("room"));
                paint.setColor(ink);paint.setTextSize(dp(11));paint.setTextAlign(Paint.Align.LEFT);paint.setTypeface(block.courses.size()>1?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);drawWrapped(canvas,text,block.bounds);paint.setTypeface(Typeface.DEFAULT);paint.setTextAlign(Paint.Align.CENTER);
            }
            if(blocks.isEmpty()&&document.optJSONArray("courses")!=null&&document.optJSONArray("courses").length()>0){paint.setTextAlign(Paint.Align.CENTER);paint.setColor(mint);paint.setTextSize(dp(14));canvas.drawText("本周没有课程",width/2f,header+dp(36),paint);}
        }
        private void drawWrapped(Canvas canvas,String value,RectF rect){float max=rect.width()-dp(8),y=rect.top+dp(17);int limit=Math.max(1,(int)((rect.height()-dp(7))/dp(15)));ArrayList<String> lines=new ArrayList<>();for(String part:value.split("\n")){while(!part.isEmpty()){int n=paint.breakText(part,true,max,null);if(n<=0)n=1;lines.add(part.substring(0,n));part=part.substring(n);}}for(int i=0;i<Math.min(limit,lines.size());i++){String line=lines.get(i);if(i==limit-1&&lines.size()>limit){while(line.length()>0&&paint.measureText(line+"…")>max)line=line.substring(0,line.length()-1);line+="…";}canvas.drawText(line,rect.left+dp(4),y,paint);y+=dp(15);}}
        @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getAction()==android.view.MotionEvent.ACTION_DOWN)return true;if(event.getAction()==android.view.MotionEvent.ACTION_UP){performClick();for(Block b:blocks)if(b.bounds!=null&&b.bounds.contains(event.getX(),event.getY())){if(b.courses.size()==1)details(b.courses.get(0));else{String[] names=new String[b.courses.size()];for(int i=0;i<names.length;i++)names[i]=b.courses.get(i).optString("name","课程")+" · "+b.courses.get(i).optString("room","");new AlertDialog.Builder(TimetableScreen.this).setTitle("课程时间冲突").setItems(names,(d,i)->details(b.courses.get(i))).setNegativeButton("关闭",null).show();}return true;}return true;}return super.onTouchEvent(event);}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
