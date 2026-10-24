package cn.xinxinxiangying.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.*;

/** Independent campus service entry; does not claim unverified native integrations. */
public final class MainActivity extends Activity {
    private final int ink=CampusUi.INK, muted=CampusUi.MUTED, teal=CampusUi.GREEN;
    private LinearLayout content;
    private SharedPreferences prefs;
    private int selectedTab=0;
    private static final int PICK_AVATAR=4102;
    private String avatarOwner="";
    private LinearLayout shell,navHost;private FrameLayout pages;private final View[] tabViews=new View[3];private TimetableScreen timetable;
    private int dp(float n){return CampusUi.dp(this,n);}
    private TextView text(String s,int size,int color){return CampusUi.text(this,s,size,color);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);CampusUi.window(this);
        if(!AppSession.require(this))return;
        prefs=getSharedPreferences("school_profile",MODE_PRIVATE);selectedTab=state!=null?state.getInt("tab",0):getIntent().getIntExtra("tab",0);
        if(state!=null)avatarOwner=state.getString("avatarOwner","");
        shell=CampusUi.column(this);shell.setBackgroundColor(CampusUi.BG);CampusUi.insets(shell);pages=new FrameLayout(this);shell.addView(pages,new LinearLayout.LayoutParams(-1,0,1));navHost=CampusUi.column(this);shell.addView(navHost);setContentView(shell);
        timetable=new TimetableScreen(this,state,view->{tabViews[0]=view;if(selectedTab==0)showHome();});
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(!AppSession.require(this))return;selectedTab=intent.getIntExtra("tab",0);if(timetable!=null)timetable.resume();showHome();showRequestedSettings();}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("tab",selectedTab);state.putString("avatarOwner",avatarOwner);if(timetable!=null)timetable.saveState(state);}
    @Override public void onResume(){super.onResume();if(isFinishing()||timetable==null)return;if(!AppSession.require(this))return;tabViews[1]=null;tabViews[2]=null;timetable.resume();showHome();showRequestedSettings();if(!UpdateChecker.resumeInstall(this))UpdateChecker.autoCheck(this);}
    @Override public void onPause(){if(timetable!=null)timetable.pause();super.onPause();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICK_AVATAR){saveAvatar(result,data);return;}if(timetable!=null&&AppSession.valid(this))timetable.result(request,result,data);}
    @Override public void onBackPressed(){if(selectedTab!=0){selectTab(0);return;}super.onBackPressed();}
    private void showRequestedSettings(){if(timetable!=null&&getIntent().getBooleanExtra("showSettings",false)){getIntent().removeExtra("showSettings");timetable.showSettings();}}
    public void selectTab(int tab){if(!AppSession.require(this))return;if(tab<0||tab>2)return;if(tab==1)tabViews[1]=null;selectedTab=tab;showHome();}
    private void gap(int h){content.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private void showHome(){
        if(pages==null||!AppSession.valid(this))return;
        if(selectedTab<0||selectedTab>2)selectedTab=0;
        if(tabViews[selectedTab]==null&&selectedTab!=0){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);content=CampusUi.column(this);content.setPadding(0,0,0,dp(20));content.setBackground(selectedTab==2?CampusUi.wash(true):CampusUi.shape(this,CampusUi.BG,0));scroll.addView(content);if(selectedTab==2)showProfile();else showCampus();tabViews[selectedTab]=scroll;}
        View current=tabViews[selectedTab];if(current!=null){pages.removeAllViews();if(current.getParent()!=null)((android.view.ViewGroup)current.getParent()).removeView(current);pages.addView(current,new FrameLayout.LayoutParams(-1,-1));}
        navHost.removeAllViews();navHost.addView(CampusUi.navigation(this,selectedTab));
    }
    private void heading(String title,String subtitle){
        TextView h=text(title,30,ink);h.setTypeface(null,Typeface.BOLD);content.addView(h);
        TextView sub=text(subtitle,13,muted);sub.setPadding(0,dp(7),0,0);content.addView(sub);gap(24);
    }
    private void selectProfile(){new AlertDialog.Builder(this).setTitle("选择学生类型").setSingleChoiceItems(new String[]{"本科生","研究生"},isPostgraduate()?1:0,(d,w)->{prefs.edit().putBoolean("postgraduate",w==1).apply();TimetableWidget.update(this);CourseReminder.schedule(this);((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(77);d.dismiss();AppSession.login(this,"学生类型已切换，请验证对应学校账号");}).setNegativeButton("取消",null).show();}
    private LinearLayout panel(int inset){LinearLayout box=CampusUi.column(this);box.setPadding(dp(16),dp(14),dp(16),dp(14));box.setBackground(CampusUi.shape(this,Color.WHITE,14));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(inset),0,dp(inset),0);content.addView(box,lp);return box;}
    private void showCampus(){
        TextView bar=text("心心相应",16,Color.WHITE);bar.setGravity(Gravity.CENTER);bar.setBackgroundColor(teal);content.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        FrameLayout banner=new FrameLayout(this);banner.addView(new CampusUi.CampusArt(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout caption=CampusUi.column(this);caption.setGravity(Gravity.BOTTOM);caption.setPadding(dp(20),dp(16),dp(20),dp(16));caption.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x20052f29,0xe6052f29}));
        TextView schoolName=text("上海应用技术大学",18,Color.WHITE);schoolName.setTypeface(null,Typeface.BOLD);caption.addView(schoolName);
        java.util.Calendar today=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        java.text.SimpleDateFormat dateFormat=new java.text.SimpleDateFormat("M月d日 EEEE",java.util.Locale.CHINA);dateFormat.setTimeZone(today.getTimeZone());
        TextView date=text(dateFormat.format(today.getTime()),12,0xffe5f4ef);date.setPadding(0,dp(7),0,dp(5));caption.addView(date);
        String summary;try{summary=campusSummary(new TimetableRepository(this).read(),today);}catch(RuntimeException e){summary="课表暂无法读取，请重新同步";}
        caption.addView(text(summary,14,Color.WHITE));banner.addView(caption,new FrameLayout.LayoutParams(-1,-1));content.addView(banner,new LinearLayout.LayoutParams(-1,dp(176)));gap(14);
        LinearLayout identity=panel(12);identity.setOrientation(LinearLayout.HORIZONTAL);identity.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info=CampusUi.column(this);TextView school=text("我的校园",18,teal);school.setTypeface(null,Typeface.BOLD);info.addView(school);TextView student=text(isPostgraduate()?"研究生服务":"本科生服务",12,muted);student.setPadding(0,dp(4),0,0);info.addView(student);identity.addView(info,new LinearLayout.LayoutParams(0,-2,1));Button change=CampusUi.button(this,"切换身份",false,v->selectProfile());identity.addView(change,new LinearLayout.LayoutParams(-2,dp(44)));gap(14);
        LinearLayout services=panel(12);
        serviceGrid(services,new String[]{"exams","gpa"},new String[]{"考试安排","学分绩点"},new int[]{0xff5986cf,0xffec956a});gap(18);
        LinearLayout life=panel(12);section(life,"生活助手");serviceGrid(life,new String[]{"card","code","electricity"},new String[]{"校园卡","校园付款码","宿舍电费"},new int[]{0xff458ec7,0xffd5a34e,0xff62a481});gap(16);
        gap(20);TextView note=text("校园服务由学校系统提供",11,muted);note.setGravity(Gravity.CENTER);content.addView(note);
    }
    static String campusSummary(org.json.JSONObject doc,java.util.Calendar today){
        org.json.JSONArray courses=doc.optJSONArray("courses");if(courses==null||courses.length()==0)return "同步课表，查看今日课程";
        java.util.Calendar start=TimetableModel.date(doc.optString("startDate"));if(start==null)return "设置开学日期，查看本周与今日课程";
        java.util.Calendar date=(java.util.Calendar)today.clone();date.set(java.util.Calendar.HOUR_OF_DAY,12);date.set(java.util.Calendar.MINUTE,0);date.set(java.util.Calendar.SECOND,0);date.set(java.util.Calendar.MILLISECOND,0);
        java.util.Calendar end=(java.util.Calendar)start.clone();end.add(java.util.Calendar.DATE,doc.optInt("totalWeeks",20)*7);
        if(date.before(start)||!date.before(end))return "当前不在教学周";
        int week=TimetableModel.week(doc,date),day=(date.get(java.util.Calendar.DAY_OF_WEEK)+5)%7+1;
        int count=TimetableModel.day(doc,week,day).length();return "第 "+week+" 周 · "+(count==0?"今日没有课程":"今日 "+count+" 项课程安排");
    }
    private void section(LinearLayout box,String title){TextView t=text(title,17,ink);t.setPadding(dp(10),0,0,0);GradientDrawable marker=CampusUi.shape(this,teal,0);android.graphics.drawable.LayerDrawable bg=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{marker});bg.setLayerWidth(0,dp(3));bg.setLayerHeight(0,dp(18));bg.setLayerGravity(0,Gravity.LEFT|Gravity.CENTER_VERTICAL);t.setBackground(bg);box.addView(t);}
    private void serviceGrid(LinearLayout parent,String[] ids,String[] titles,int[] colors){
        LinearLayout grid=new LinearLayout(this);grid.setPadding(0,dp(10),0,dp(4));for(int i=0;i<ids.length;i++){final String id=ids[i];LinearLayout cell=CampusUi.column(this);cell.setGravity(Gravity.CENTER);cell.setPadding(0,dp(12),0,dp(10));cell.setBackground(CampusUi.ripple(this,Color.WHITE,8));
            LinearLayout iconBox=new LinearLayout(this);iconBox.setGravity(Gravity.CENTER);iconBox.setBackground(CampusUi.shape(this,(colors[i]&0x00ffffff)|0x14000000,10));iconBox.addView(new CampusUi.Icon(this,"timetable".equals(id)?"calendar":id,colors[i]),new LinearLayout.LayoutParams(dp(29),dp(29)));cell.addView(iconBox,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView name=text(titles[i],12,ink);name.setGravity(Gravity.CENTER);name.setPadding(0,dp(9),0,0);cell.addView(name);grid.addView(cell,new LinearLayout.LayoutParams(0,-2,1));cell.setFocusable(true);cell.setContentDescription(titles[i]);cell.setOnClickListener(v->openService(id));}parent.addView(grid);
    }
    private View row(String icon,String title,String subtitle,View.OnClickListener click){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(12),0,dp(12));row.setMinimumHeight(dp(56));row.setBackground(CampusUi.ripple(this,Color.WHITE,14));
        row.addView(new CampusUi.Icon(this,icon,teal),new LinearLayout.LayoutParams(dp(24),dp(24)));LinearLayout words=CampusUi.column(this);TextView t=text(title,15,ink);words.addView(t);
        if(!subtitle.isEmpty()){TextView sub=text(subtitle,11,muted);sub.setPadding(0,dp(5),0,0);words.addView(sub);}LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(15);row.addView(words,wp);row.addView(text("›",22,muted));row.setOnClickListener(click);return row;
    }
    private void divider(LinearLayout group){View line=new View(this);line.setBackgroundColor(CampusUi.LINE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.leftMargin=dp(39);group.addView(line,lp);}
    private void showProfile(){
        LinearLayout profile=new LinearLayout(this);profile.setGravity(Gravity.CENTER_VERTICAL);profile.setPadding(dp(35),dp(55),dp(35),dp(25));
        LocalProfile local=new LocalProfile(this,isPostgraduate());
        LinearLayout info=CampusUi.column(this);String username=new SchoolAccount(this).username(isPostgraduate());String displayName=local.name();TextView name=text(displayName.isEmpty()?"姓名待同步":displayName,25,Color.WHITE);info.addView(name);
        TextView studentId=text(username.isEmpty()?"登录学校账号":username,17,Color.WHITE);studentId.setPadding(0,dp(5),0,0);info.addView(studentId);
        TextView school=text("上海应用技术大学\n"+(isPostgraduate()?"研究生":"本科生"),13,0xffedf7f4);school.setPadding(0,dp(7),0,0);info.addView(school);profile.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout avatar=new FrameLayout(this);avatar.setBackground(CampusUi.shape(this,0xfff5f6f5,5));avatar.setClipToOutline(true);
        android.graphics.Bitmap picture=local.avatar();
        if(picture!=null){ImageView image=new ImageView(this);image.setImageBitmap(picture);image.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.addView(image,new FrameLayout.LayoutParams(-1,-1));}
        else avatar.addView(new CampusUi.Icon(this,"person",0xff91a69f),new FrameLayout.LayoutParams(dp(35),dp(35),Gravity.CENTER));
        avatar.setContentDescription("更换头像");avatar.setFocusable(true);avatar.setOnClickListener(v->pickAvatar());profile.addView(avatar,new LinearLayout.LayoutParams(dp(62),dp(62)));info.setOnClickListener(v->startActivity(new Intent(this,SchoolLoginActivity.class).putExtra("edit",true)));content.addView(profile);
        LinearLayout account=panel(30);account.setPadding(dp(20),dp(2),dp(20),dp(2));
        account.addView(row("campus","学校账号与课表同步","",v->startActivity(new Intent(this,SchoolLoginActivity.class).putExtra("edit",true))));divider(account);
        account.addView(row("person","学生类型",isPostgraduate()?"研究生":"本科生",v->selectProfile()));divider(account);
        account.addView(row("settings","设置","",v->timetable.showSettings()));divider(account);
        account.addView(row("refresh","检查更新","当前版本 "+UpdateChecker.versionName(this),v->UpdateChecker.check(this,true)));divider(account);
        account.addView(row("more","关于与隐私","",v->new AlertDialog.Builder(this).setTitle("心心相应 "+UpdateChecker.versionName(this)).setMessage("面向上海应用技术大学的独立校园工具。\n\n课表与教务页面文本保存在本机。密码仅用于学校认证；选择记住密码后由此设备的系统密钥加密保存。\n学校服务以官方门户为准。").setPositiveButton("知道了",null).show()));divider(account);
        account.addView(row("back","退出登录","",v->confirmClear()));gap(40);
        TextView footer=text("校园服务以学校官方信息为准\n心心相应  "+UpdateChecker.versionName(this),11,muted);footer.setGravity(Gravity.CENTER);footer.setLineSpacing(dp(5),1);content.addView(footer);
    }
    private boolean isPostgraduate() { return prefs.getBoolean("postgraduate",false); }
    private void openService(String id) {
        if("timetable".equals(id)) { selectTab(0); return; }
        // 考试、成绩、绩点使用与课表相同的学校登录流程，读到数据后在本机列表里查看。
        if(SchoolCatalog.isAcademic(id)) { startActivity(new Intent(this,AcademicActivity.class).putExtra("service",id).putExtra("postgraduate",isPostgraduate())); return; }
        Intent intent=new Intent(this,SchoolWebActivity.class); intent.putExtra("service",id); intent.putExtra("postgraduate",isPostgraduate()); startActivity(intent);
    }
    private void pickAvatar(){
        avatarOwner=new LocalProfile(this,isPostgraduate()).key;
        Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(choose,PICK_AVATAR);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"未找到系统图片选择器",Toast.LENGTH_LONG).show();}
    }
    private void saveAvatar(int result,Intent data){
        if(result!=RESULT_OK||data==null||data.getData()==null||!AppSession.valid(this))return;
        LocalProfile local=new LocalProfile(this,isPostgraduate());
        if(!local.key.equals(avatarOwner)){Toast.makeText(this,"账号已切换，请重新选择头像",Toast.LENGTH_SHORT).show();return;}
        new Thread(()->{
            try{
                local.saveAvatar(data.getData());
                runOnUiThread(()->{if(isFinishing()||isDestroyed()||!AppSession.valid(this)||!local.key.equals(new LocalProfile(this,isPostgraduate()).key))return;tabViews[2]=null;if(selectedTab==2)showHome();Toast.makeText(this,"头像已保存在本机",Toast.LENGTH_SHORT).show();});
            }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())Toast.makeText(this,"图片无法读取或保存，请换一张重试",Toast.LENGTH_LONG).show();});}
        },"profile-picture").start();
    }
    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("退出并清除数据？").setMessage("将清除本应用保存的网页登录状态、教务页面文本和本地记录。其他浏览器中的登录不受影响。")
            .setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{
                AppSession.invalidate(this);android.webkit.CookieManager.getInstance().removeAllCookies(done->{
                    android.webkit.CookieManager.getInstance().flush(); android.webkit.WebStorage.getInstance().deleteAllData();
                    android.webkit.WebView temp=new android.webkit.WebView(this); temp.clearCache(true); temp.clearFormData(); temp.destroy();
                    getSharedPreferences("academic_snapshots",MODE_PRIVATE).edit().clear().commit();
                    getSharedPreferences("xinxin_local_v1",MODE_PRIVATE).edit().clear().commit();
                    getSharedPreferences("timetables_v1",MODE_PRIVATE).edit().clear().commit();
                    LocalProfile.clear(this);new SchoolAccount(this).clear();((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(77);getSharedPreferences("school_session",0).edit().clear().commit();TimetableWidget.update(this);CourseReminder.schedule(this);
                    Toast.makeText(this,"本机数据已清除",Toast.LENGTH_SHORT).show(); AppSession.login(this,"已退出，请重新登录学校账号");
                });
            }).show();
    }
}
