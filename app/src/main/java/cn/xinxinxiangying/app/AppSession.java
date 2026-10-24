package cn.xinxinxiangying.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Access grant issued after a verified school response and kept on this device. */
public final class AppSession {
    private static final String STORE="school_session",KEY="identity",OWNER="owner",TIMETABLES="timetables_v1";
    private static String identity="";
    private AppSession(){}
    public static synchronized boolean valid(Context c){
        Context app=c.getApplicationContext();
        boolean pg=app.getSharedPreferences("school_profile",0).getBoolean("postgraduate",false);
        SchoolAccount account=new SchoolAccount(app);
        String user=account.username(pg);
        if(user.isEmpty())return false;
        String expected=(pg?"pg:":"ug:")+user;
        if(identity.isEmpty()){
            SharedPreferences store=app.getSharedPreferences(STORE,0);
            identity=store.getString(KEY,"");
            if(identity.isEmpty()&&expected.equals(store.getString(OWNER,""))){
                // 旧版本只记录过同步所有者；本机已有该账号课表时沿用这条记录，升级后不必再登录一次。
                String saved=app.getSharedPreferences(TIMETABLES,0).getString((pg?"pg":"ug")+account.scope(pg),"");
                if(!saved.isEmpty()){identity=expected;store.edit().putString(KEY,identity).apply();}
            }
        }
        return identity.equals(expected);
    }
    static synchronized void verified(Context c,boolean pg,String user){
        if(user==null||user.trim().isEmpty())throw new IllegalArgumentException("学校未确认账号");
        identity=(pg?"pg:":"ug:")+user;
        c.getApplicationContext().getSharedPreferences(STORE,0).edit().putString(KEY,identity).commit();
    }
    public static synchronized void invalidate(Context c){
        identity="";
        PaymentCodeWebView.clear();
        c.getApplicationContext().getSharedPreferences(STORE,0).edit().remove(KEY).commit();
    }
    public static void login(Activity a,String message){login(a,message,"timetable");}
    public static void login(Activity a,String message,String service){invalidate(a);Intent i=new Intent(a,SchoolLoginActivity.class).putExtra("edit",true).putExtra("message",message).putExtra("service",service==null?"timetable":service).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);a.startActivity(i);a.finish();}
    public static boolean require(Activity a){return require(a,"timetable");}
    public static boolean require(Activity a,String service){if(valid(a))return true;login(a,"请使用学校账号和密码登录后使用",service);return false;}
}
