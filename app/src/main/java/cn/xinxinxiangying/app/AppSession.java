package cn.xinxinxiangying.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * 本机已验证账号的身份记录。
 *
 * <p>它只回答“这台设备曾经验证过哪个账号”，不代表学校会话仍然有效。
 * 学校会话是否可用，由每次真实页面/接口响应决定；需要重新认证时走
 * {@link #reauth(Activity, String, String)}，不能把局部失败升级成全局退出。</p>
 */
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
        SchoolSession.endReauth(c);
        c.getApplicationContext().getSharedPreferences(STORE,0).edit().remove(KEY).remove(OWNER).commit();
    }

    /**
     * 会话恢复：保留本机身份与 Cookie，只打开一次认证流程；成功后回到原功能。
     * 返回 false 表示已有认证流程在进行，本次不重复启动。
     */
    public static boolean reauth(Activity a,String service,String message){
        String target=normalize(service);
        if(!SchoolSession.beginReauth(a,target)){a.finish();return false;}
        Intent i=new Intent(a,SchoolLoginActivity.class)
            .putExtra("message",message)
            .putExtra("service",target)
            .putExtra("authFlowId",SchoolSession.currentReauthId())
            .putExtra("reauth",true);
        a.startActivity(i);
        a.finish();
        return true;
    }

    public static void login(Activity a,String message){login(a,message,"timetable");}

    /** 明确退出或切换账号：清本机身份，并要求重新输入。 */
    public static void login(Activity a,String message,String service){
        invalidate(a);
        startLogin(a,service,message,true);
    }
    public static boolean require(Activity a){return require(a,"timetable");}
    public static boolean require(Activity a,String service){
        if(valid(a))return true;
        startLogin(a,service,"请使用学校账号和密码登录后使用",false);
        return false;
    }

    private static void startLogin(Activity a,String service,String message,boolean clearSession){
        String target=normalize(service);
        // 多个页面同时发现未登录时只保留一个登录流程，避免叠出多个登录页。
        if(!SchoolSession.beginReauth(a,target)){a.finish();return;}
        Intent i=new Intent(a,SchoolLoginActivity.class)
            .putExtra("edit",true)
            .putExtra("message",message)
            .putExtra("service",target)
            .putExtra("authFlowId",SchoolSession.currentReauthId())
            .putExtra("clearSession",clearSession);
        a.startActivity(i);
        a.finish();
    }

    private static String normalize(String service){
        return service==null||service.trim().isEmpty()?"timetable":service;
    }
}
