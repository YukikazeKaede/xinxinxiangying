package cn.xinxinxiangying.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared visual language for the independent campus app. */
public final class CampusUi {
    public static final int INK=0xff263c38, MUTED=0xff6d807b, GREEN=0xff086859,
        BG=0xfff5f8f8, SOFT=0xffe7f3ef, LINE=0xffedf1f0, APRICOT=0xfffff5de;
    private CampusUi(){}
    public static int dp(Context c,float n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
    public static TextView text(Context c,String s,int size,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    public static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    public static RippleDrawable ripple(Context c,int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x24086859),shape(c,color,radius),shape(c,Color.WHITE,radius));}
    public static Button button(Context c,String s,boolean primary,View.OnClickListener click){Button b=new Button(c);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(null,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:GREEN);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(c,44));b.setMinimumHeight(dp(c,44));b.setPadding(dp(c,18),0,dp(c,18),0);b.setElevation(0);b.setStateListAnimator(null);b.setBackground(ripple(c,primary?GREEN:SOFT,10));b.setOnClickListener(click);return b;}
    public static void window(Activity a){a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
    public static void insets(LinearLayout root){root.setOnApplyWindowInsetsListener((v,i)->{if(android.os.Build.VERSION.SDK_INT>=30){Insets bars=i.getInsets(WindowInsets.Type.systemBars());root.setPadding(bars.left,bars.top,bars.right,bars.bottom);}return i;});}
    public static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    public static void navigate(Activity a,int tab){if(a instanceof MainActivity){((MainActivity)a).selectTab(tab);return;}a.startActivity(new Intent(a,MainActivity.class).putExtra("tab",tab).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));a.overridePendingTransition(0,0);}
    public static GradientDrawable wash(boolean profile){return new GradientDrawable(profile?GradientDrawable.Orientation.TOP_BOTTOM:GradientDrawable.Orientation.LEFT_RIGHT,profile?new int[]{GREEN,0xff6da69b,Color.WHITE,Color.WHITE,Color.WHITE}:new int[]{0xffbde8e7,0xfff1dce7});}
    public static LinearLayout navigation(Activity a,int selected){
        LinearLayout bar=new LinearLayout(a);bar.setBackgroundColor(Color.WHITE);bar.setPadding(0,dp(a,5),0,dp(a,3));
        String[] names={"课表","工具","我的"}, icons={"calendar","tools","person"};
        for(int i=0;i<3;i++){final int index=i;LinearLayout item=column(a);item.setGravity(Gravity.CENTER);item.setBackground(ripple(a,Color.WHITE,0));
            item.addView(new Icon(a,icons[i],i==selected?GREEN:0xff8caaa1),new LinearLayout.LayoutParams(dp(a,23),dp(a,23)));
            TextView name=text(a,names[i],11,i==selected?GREEN:MUTED);name.setGravity(Gravity.CENTER);name.setPadding(0,dp(a,3),0,0);item.addView(name);
            bar.addView(item,new LinearLayout.LayoutParams(0,dp(a,52),1));item.setContentDescription(names[i]);item.setFocusable(true);item.setOnClickListener(v->{if(index!=selected)navigate(a,index);});}
        return bar;
    }
    /** Locally drawn campus illustration, not a simulated school news feed. */
    public static class CampusArt extends View {
        private final Paint p=new Paint(3);
        public CampusArt(Context c){super(c);setContentDescription("校园建筑插画");}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();p.setShader(new LinearGradient(0,0,0,h,0xff8bc6e8,0xffe3f3f6,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
            p.setColor(0xfff6fbfd);c.drawOval(w*.64f,h*.12f,w*.90f,h*.28f,p);c.drawOval(w*.11f,h*.22f,w*.34f,h*.34f,p);
            p.setColor(0xffbbd0d4);c.drawRect(w*.28f,h*.39f,w*.52f,h*.87f,p);c.drawRect(w*.62f,h*.28f,w*.82f,h*.88f,p);
            p.setColor(0xffedf3ef);c.drawRect(w*.04f,h*.57f,w*.24f,h*.9f,p);c.drawRect(w*.56f,h*.51f,w*.89f,h*.9f,p);
            p.setColor(0xff729aab);for(int i=0;i<6;i++)for(int j=0;j<4;j++)c.drawRect(w*(.30f+j*.05f),h*(.43f+i*.06f),w*(.33f+j*.05f),h*(.46f+i*.06f),p);
            p.setColor(0xff397b60);for(int i=0;i<12;i++)c.drawCircle(w*i/11,h*.87f,h*(.09f+(i%3)*.015f),p);
            p.setColor(0xffe8e8dc);c.drawRect(0,h*.85f,w,h*.91f,p);c.drawRect(w*.17f,h*.63f,w*.22f,h,p);c.drawRect(w*.78f,h*.63f,w*.83f,h,p);c.drawRect(w*.15f,h*.61f,w*.24f,h*.66f,p);c.drawRect(w*.76f,h*.61f,w*.85f,h*.66f,p);
        }
    }
    /** Consistent line icons, drawn locally without font/emoji dependencies. */
    public static class Icon extends View{
        private final Paint p=new Paint(3);private final String kind;private final int color;
        public Icon(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private void line(Canvas c,float x,float y,float x2,float y2){c.drawLine(x,y,x2,y2,p);}
        @Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(kind.equals("tools")){Path wrench=new Path();wrench.moveTo(14.7f,6.3f);wrench.quadTo(14.1f,7,14.7f,7.7f);wrench.lineTo(16.3f,9.3f);wrench.quadTo(17,9.9f,17.7f,9.3f);wrench.lineTo(21.3f,5.7f);wrench.cubicTo(22.4f,9.6f,18.7f,13.3f,14.8f,12.2f);wrench.lineTo(7.1f,19.9f);wrench.cubicTo(4.3f,22.7f,1.3f,19.7f,4.1f,16.9f);wrench.lineTo(11.8f,9.2f);wrench.cubicTo(10.7f,5.3f,14.4f,1.6f,18.3f,2.7f);wrench.close();c.drawPath(wrench,p);}
            else if(kind.equals("person")){c.drawCircle(12,7,3.4f,p);c.drawArc(5,13,19,25,180,180,false,p);}
            else if(kind.equals("campus")){Path path=new Path();path.moveTo(2,9);path.lineTo(12,3);path.lineTo(22,9);path.lineTo(12,14);path.close();c.drawPath(path,p);line(c,6,12,6,18);line(c,6,18,12,21);line(c,12,21,18,18);line(c,18,18,18,12);line(c,22,9,22,17);}
            else if(kind.equals("code")){c.drawRoundRect(3,3,9,9,1,1,p);c.drawRoundRect(15,3,21,9,1,1,p);c.drawRoundRect(3,15,9,21,1,1,p);line(c,15,15,18,15);line(c,18,15,18,21);line(c,21,18,21,21);line(c,14,21,15,21);}
            else if(kind.equals("electricity")){Path path=new Path();path.moveTo(14,2);path.lineTo(5,14);path.lineTo(11,14);path.lineTo(10,22);path.lineTo(20,9);path.lineTo(13,9);path.close();c.drawPath(path,p);}
            else if(kind.equals("card")){c.drawRoundRect(2,5,22,20,3,3,p);line(c,3,10,21,10);line(c,6,16,11,16);}
            else if(kind.equals("back")){line(c,15,5,8,12);line(c,8,12,15,19);}
            else if(kind.equals("refresh")){c.drawArc(4,4,20,20,45,270,false,p);line(c,19,3,19,8);line(c,19,8,14,8);}
            else if(kind.equals("settings")){c.drawCircle(12,12,7,p);c.drawCircle(12,12,2.5f,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10);}}
            else if(kind.equals("heart")){Path path=new Path();path.moveTo(12,21);path.cubicTo(8,17,2,13,2,8);path.cubicTo(2,2,9,1,12,6);path.cubicTo(15,1,22,2,22,8);path.cubicTo(22,13,16,17,12,21);c.drawPath(path,p);}
            else if(kind.equals("gpa")){line(c,4,21,21,21);line(c,6,17,6,12);line(c,12,17,12,8);line(c,18,17,18,3);}
            else if(kind.equals("second")){c.drawCircle(12,9,5.2f,p);Path path=new Path();path.moveTo(8,13);path.lineTo(6,22);path.lineTo(12,19);path.lineTo(18,22);path.lineTo(16,13);c.drawPath(path,p);}
            else if(kind.equals("grades")||kind.equals("saved")){c.drawRoundRect(5,3,20,22,2,2,p);line(c,9,8,16,8);line(c,9,12,16,12);line(c,9,16,13,16);}
            else if(kind.equals("more")){for(int i=0;i<3;i++)c.drawCircle(5+i*7,12,.8f,p);}
            else if(kind.equals("search")){c.drawCircle(10,10,6,p);line(c,14.5f,14.5f,20,20);}
            else{c.drawRoundRect(3,5,21,22,3,3,p);line(c,3,10,21,10);line(c,8,2,8,7);line(c,16,2,16,7);if(kind.equals("exams")){line(c,8,16,11,19);line(c,11,19,17,13);}else{line(c,8,15,10,15);line(c,14,15,16,15);line(c,8,19,10,19);}}
            c.restore();
        }
    }
    /** A timetable paper with a heart seal: the app's own empty-state illustration. */
    public static final class ScheduleArt extends View{
        final Paint p=new Paint(3);public ScheduleArt(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/180f,getHeight()/160f);p.setStyle(Paint.Style.FILL);p.setColor(SOFT);c.drawOval(8,9,168,153,p);c.save();c.rotate(-9,88,80);p.setColor(0xffc5ddd1);c.drawRoundRect(39,23,136,139,14,14,p);c.restore();p.setColor(Color.WHITE);c.drawRoundRect(36,18,134,136,13,13,p);p.setColor(GREEN);c.drawRoundRect(36,18,134,52,13,13,p);c.drawRect(36,39,134,52,p);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(59,12,59,27,p);c.drawLine(111,12,111,27,p);int[] colors={SOFT,APRICOT,0xffdce7f4};for(int r=0;r<3;r++)for(int col=0;col<3;col++){p.setColor(colors[(r+col)%3]);c.drawRoundRect(49+col*24,65+r*19,66+col*24,77+r*19,3,3,p);}p.setColor(APRICOT);c.drawCircle(135,122,26,p);p.setColor(GREEN);Path heart=new Path();heart.moveTo(135,134);heart.cubicTo(111,121,127,108,135,119);heart.cubicTo(143,108,159,121,135,134);c.drawPath(heart,p);c.restore();}
    }
}
