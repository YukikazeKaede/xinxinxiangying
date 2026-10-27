package cn.xinxinxiangying.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 考试安排、考试成绩与学分绩点：本机快照的离线查看页。 */
public final class AcademicActivity extends Activity {
    private String service="grades";
    private boolean postgraduate;
    private LinearLayout content;
    private TextView status;
    private AcademicRepository repository;
    private String selectedXnm="";
    private String selectedXqm="";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        CampusUi.window(this);
        service=getIntent().getStringExtra("service");if(service==null)service="grades";
        postgraduate=getSharedPreferences("school_profile",0).getBoolean("postgraduate",false);
        repository=new AcademicRepository(this);
        if(!AppSession.require(this,service))return;
        LinearLayout root=CampusUi.column(this);root.setBackgroundColor(CampusUi.BG);CampusUi.insets(root);
        LinearLayout bar=new LinearLayout(this);bar.setBackgroundColor(Color.WHITE);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),dp(8),dp(8),dp(8));
        LinearLayout back=new LinearLayout(this);back.setGravity(Gravity.CENTER);back.setBackground(CampusUi.ripple(this,Color.WHITE,6));back.addView(new CampusUi.Icon(this,"back",CampusUi.GREEN),new LinearLayout.LayoutParams(dp(22),dp(22)));back.setContentDescription("返回");back.setFocusable(true);back.setOnClickListener(v->leave());bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView title=CampusUi.text(this,SchoolCatalog.title(service),18,CampusUi.INK);title.setTypeface(null,Typeface.BOLD);title.setPadding(dp(12),0,0,0);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=CampusUi.button(this,"刷新",true,v->refresh());bar.addView(refresh,new LinearLayout.LayoutParams(-2,dp(44)));
        LinearLayout more=new LinearLayout(this);more.setGravity(Gravity.CENTER);more.setBackground(CampusUi.ripple(this,Color.WHITE,6));more.addView(new CampusUi.Icon(this,"more",CampusUi.GREEN),new LinearLayout.LayoutParams(dp(22),dp(22)));more.setContentDescription("更多");more.setFocusable(true);more.setOnClickListener(v->menu());bar.addView(more,new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.addView(bar);
        status=CampusUi.text(this,"",11,CampusUi.MUTED);status.setPadding(dp(20),dp(2),dp(20),dp(8));root.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        content=CampusUi.column(this);content.setPadding(dp(14),0,dp(14),dp(20));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);render();
    }
    @Override protected void onResume(){super.onResume();if(content!=null&&AppSession.valid(this))render();}
    private int dp(int v){return CampusUi.dp(this,v);}

    /** 导入完成后本页可能独占一个任务，返回时要回到主界面而不是直接退出应用。 */
    private void leave(){
        if(isTaskRoot())startActivity(new Intent(this,MainActivity.class));
        finish();
    }
    @Override public void onBackPressed(){leave();}
    private void render(){
        content.removeAllViews();
        JSONObject saved=repository.read(service);
        JSONObject savedTerm=saved==null?null:saved.optJSONObject("term");
        termRow(savedTerm==null?"":savedTerm.optString("xnm",""),savedTerm==null?"":savedTerm.optString("xqm",""));
        if(saved==null||saved.optJSONArray("items")==null||saved.optJSONArray("items").length()==0){empty(saved);return;}
        JSONArray items=saved.optJSONArray("items");
        JSONObject term=saved.optJSONObject("term");
        String termLabel=term==null?"":term.optString("label","");
        status.setText(termLabel.isEmpty()?("本机保存 · "+when(saved.optLong("importedAt",0))):(termLabel+" · "+when(saved.optLong("importedAt",0))));
        if("exams".equals(SchoolCatalog.academicKind(service))){summaryCard("共 "+items.length()+" 场考试",termLabel);examList(items);}
        else if("gpa".equals(service)){gpaView(saved,items,termLabel);}
        else{gradeSummary(saved,termLabel);gradeList(items,false);}
        footnote("数据来自学校教务系统，以学校页面为准。");
    }
    /** 顶部学期行：显示当前查看的学期，点击可换到其它学期重新读取。 */
    private void termRow(String xnm,String xqm){
        String label=termText(xnm,xqm);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(12),dp(14),dp(12));
        row.setBackground(CampusUi.ripple(this,Color.WHITE,12));
        TextView name=CampusUi.text(this,"学期  "+label,13,CampusUi.INK);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(CampusUi.text(this,"切换 ›",12,CampusUi.GREEN));
        row.setFocusable(true);row.setContentDescription("切换学期");row.setOnClickListener(v->chooseTerm());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);content.addView(row,lp);
    }
    private String termText(String xnm,String xqm){
        String year=xnm==null||xnm.isEmpty()?selectedXnm:xnm;
        String term=xqm==null||xqm.isEmpty()?selectedXqm:xqm;
        String[] fallback=defaultTerm();
        if(year.isEmpty()||term.isEmpty()){year=fallback[0];term=fallback[1];}
        String label=AcademicParser.label(year,term);
        return label.isEmpty()?(year+"-"+term):label;
    }
    /** 当前学期：8 月到次年 1 月为第 1 学期，其余为第 2 学期。 */
    private String[] defaultTerm(){
        java.util.Calendar calendar=java.util.Calendar.getInstance();
        int month=calendar.get(java.util.Calendar.MONTH)+1,year=calendar.get(java.util.Calendar.YEAR);
        return month>=8||month<=1?new String[]{String.valueOf(month<=1?year-1:year),"3"}:new String[]{String.valueOf(year-1),"12"};
    }
    private void chooseTerm(){
        final java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        final java.util.ArrayList<String[]> values=new java.util.ArrayList<>();
        int start=Integer.parseInt(defaultTerm()[0]);
        for(int i=0;i<4;i++){
            int year=start-i;
            for(String term:new String[]{"3","12"}){
                labels.add(AcademicParser.label(String.valueOf(year),term));
                values.add(new String[]{String.valueOf(year),term});
            }
        }
        new AlertDialog.Builder(this).setTitle("选择学期").setItems(labels.toArray(new String[0]),(dialog,which)->{
            String[] pick=values.get(which);selectedXnm=pick[0];selectedXqm=pick[1];refresh();
        }).setNegativeButton("取消",null).show();
    }
    private void empty(JSONObject saved){        status.setText("");
        LinearLayout box=CampusUi.column(this);box.setGravity(Gravity.CENTER);box.setPadding(0,dp(38),0,dp(10));
        box.addView(new CampusUi.ScheduleArt(this),new LinearLayout.LayoutParams(dp(150),dp(140)));
        TextView head=CampusUi.text(this,"还没有导入"+SchoolCatalog.title(service),17,CampusUi.INK);head.setGravity(Gravity.CENTER);head.setPadding(0,dp(20),0,0);box.addView(head);
        TextView hint=CampusUi.text(this,"点下面的按钮登录学校账号，应用会自动读取并保存在本机。之后打开就不必再登录学校系统。",13,CampusUi.MUTED);
        hint.setGravity(Gravity.CENTER);hint.setLineSpacing(dp(4),1);hint.setPadding(dp(18),dp(10),dp(18),dp(18));box.addView(hint);
        Button load=CampusUi.button(this,"从学校导入",true,v->refresh());box.addView(load,new LinearLayout.LayoutParams(-2,dp(46)));
        content.addView(box);
    }
    private void summaryCard(String body,String term){
        LinearLayout card=CampusUi.column(this);card.setPadding(dp(18),dp(16),dp(18),dp(16));card.setBackground(CampusUi.shape(this,Color.WHITE,14));
        TextView value=CampusUi.text(this,body,17,CampusUi.INK);value.setTypeface(null,Typeface.BOLD);card.addView(value);
        if(!term.isEmpty()){TextView sub=CampusUi.text(this,term,12,CampusUi.MUTED);sub.setPadding(0,dp(6),0,0);card.addView(sub);}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);content.addView(card,lp);
    }
    private void gradeSummary(JSONObject saved,String term){
        JSONObject summary=saved.optJSONObject("summary");
        String credit=summary==null?"":summary.optString("credit","");
        String average=summary==null?"":summary.optString("average","");
        String gpa=summary==null?"":summary.optString("gpa","");
        StringBuilder line=new StringBuilder();
        if(!credit.isEmpty())line.append("学分 ").append(credit);
        if(!average.isEmpty())line.append(line.length()>0?" · ":"").append("平均分 ").append(average);
        if(!gpa.isEmpty())line.append(line.length()>0?" · ":"").append("绩点 ").append(gpa);
        summaryCard(line.length()==0?"本学期成绩":line.toString(),term);
    }
    private void gpaView(JSONObject saved,JSONArray items,String term){
        JSONObject summary=saved.optJSONObject("summary");
        String gpa=summary==null?"":summary.optString("gpa","");
        String credit=summary==null?"":summary.optString("credit","");
        String average=summary==null?"":summary.optString("average","");
        // 只有学校真的给出绩点时才显示平均绩点；没有绩点字段就不显示这张卡。
        if(!gpa.isEmpty()){
            LinearLayout hero=CampusUi.column(this);hero.setPadding(dp(18),dp(18),dp(18),dp(18));hero.setBackground(CampusUi.shape(this,Color.WHITE,14));
            TextView label=CampusUi.text(this,"全部课程平均学分绩点",12,CampusUi.MUTED);hero.addView(label);
            TextView value=CampusUi.text(this,gpa,34,CampusUi.GREEN);value.setTypeface(null,Typeface.BOLD);value.setPadding(0,dp(4),0,0);hero.addView(value);
            StringBuilder sub=new StringBuilder("共 ").append(items.length()).append(" 门课程");
            if(!credit.isEmpty())sub.append(" · 总学分 ").append(credit);
            if(!average.isEmpty())sub.append(" · 平均分 ").append(average);
            if(!term.isEmpty())sub.append(" · ").append(term);
            TextView note=CampusUi.text(this,sub.toString(),12,CampusUi.MUTED);note.setPadding(0,dp(8),0,0);hero.addView(note);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);content.addView(hero,lp);
        }
        gradeList(items,true);
        if(!gpa.isEmpty())footnote("按学分加权计算：Σ(学分×绩点) ÷ Σ学分。");
    }
    private void examList(JSONArray items){
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            LinearLayout card=CampusUi.column(this);card.setPadding(dp(16),dp(14),dp(16),dp(14));card.setBackground(CampusUi.shape(this,Color.WHITE,12));
            String course=item.optString("course",""),name=item.optString("name","");
            TextView head=CampusUi.text(this,course.isEmpty()?name:course,15,CampusUi.INK);head.setTypeface(null,Typeface.BOLD);card.addView(head);
            String detail=join(" · ",name,item.optString("type",""));
            if(!detail.isEmpty())line(card,detail,CampusUi.MUTED);
            String when=item.optString("time","");if(!when.isEmpty())line(card,"时间 "+when,CampusUi.INK);
            String place=join(" ",item.optString("place",""),seat(item.optString("seat","")));if(!place.isEmpty())line(card,"地点 "+place,CampusUi.INK);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);content.addView(card,lp);
        }
    }
    private void gradeList(JSONArray items,boolean withPoint){
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(16),dp(13),dp(16),dp(13));card.setBackground(CampusUi.shape(this,Color.WHITE,12));
            LinearLayout words=CampusUi.column(this);
            TextView course=CampusUi.text(this,item.optString("course",""),15,CampusUi.INK);course.setTypeface(null,Typeface.BOLD);words.addView(course);
            String meta=join(" · ",credit(item.optString("credit","")),item.optString("nature",""),item.optString("teacher",""),withPoint?point(item.optString("point","")):"");
            if(!meta.isEmpty()){TextView sub=CampusUi.text(this,meta,12,CampusUi.MUTED);sub.setPadding(0,dp(5),0,0);words.addView(sub);}
            card.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            String score=item.optString("score","");
            TextView value=CampusUi.text(this,score.isEmpty()?"—":score,17,CampusUi.GREEN);value.setTypeface(null,Typeface.BOLD);value.setGravity(Gravity.END);card.addView(value);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);content.addView(card,lp);
        }
    }
    private void line(LinearLayout parent,String text,int color){
        if(text==null||text.trim().isEmpty())return;
        TextView view=CampusUi.text(this,text,13,color);view.setPadding(0,dp(6),0,0);parent.addView(view);
    }
    private void footnote(String text){
        TextView note=CampusUi.text(this,text,11,CampusUi.MUTED);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(16),0,dp(8));content.addView(note);
    }
    private static String join(String separator,String... parts){
        StringBuilder out=new StringBuilder();
        for(String part:parts){if(part==null||part.trim().isEmpty())continue;if(out.length()>0)out.append(separator);out.append(part.trim());}
        return out.toString();
    }
    private static String credit(String value){return value==null||value.trim().isEmpty()?"":"学分 "+value.trim();}
    private static String point(String value){return value==null||value.trim().isEmpty()?"":"绩点 "+value.trim();}
    private static String seat(String value){return value==null||value.trim().isEmpty()?"":"座位 "+value.trim();}
    private static String when(long millis){
        if(millis<=0)return "尚未更新";
        return new SimpleDateFormat("M月d日 HH:mm 更新",Locale.CHINA).format(new Date(millis));
    }

    /** 从学校重新读取：沿用课表那套登录与验证码流程，成功后就地刷新。 */
    private void refresh(){
        if(!AppSession.require(this,service))return;
        SchoolAccount account=new SchoolAccount(this);String username=account.username(postgraduate);
        JSONObject remembered=account.remembered(postgraduate);
        Intent intent=new Intent(this,SchoolWebActivity.class);
        intent.putExtra("service",service).putExtra("academicSync",true).putExtra("postgraduate",postgraduate)
            .putExtra("username",username).putExtra("password",remembered==null?"":remembered.optString("password",""))
            .putExtra("passwordFromStore",remembered!=null).putExtra("remember",false);
        if(!selectedXnm.isEmpty())intent.putExtra("academicXnm",selectedXnm).putExtra("academicXqm",selectedXqm);
        startActivity(intent);
    }
    private void menu(){
        String[] items={"打开学校页面","清除本机缓存"};
        new AlertDialog.Builder(this).setItems(items,(dialog,which)->{
            if(which==0)startActivity(new Intent(this,SchoolWebActivity.class).putExtra("service",service).putExtra("postgraduate",postgraduate));
            else new AlertDialog.Builder(this).setTitle("清除"+SchoolCatalog.title(service)+"？").setMessage("只清除本机保存的这份记录，学校系统里的数据不受影响。")
                .setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{repository.clear(service);render();}).show();
        }).show();
    }
}
