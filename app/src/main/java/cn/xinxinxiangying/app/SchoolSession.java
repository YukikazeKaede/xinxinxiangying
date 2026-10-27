package cn.xinxinxiangying.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 学校会话的运行期状态。
 *
 * <p>本机身份（{@link AppSession}）只表示“这台设备曾经验证过这个账号”，
 * 不等于学校会话仍然有效。这里只记录最近一次学校返回的成功时间、当前认证流程，
 * 以及登录成功后要回到的功能；从不用本地标记冒充学校认证。</p>
 */
public final class SchoolSession {
    private static final String STORE = "school_session";
    private static final String PENDING = "pending_service";
    private static final String REAUTH_AT = "reauth_at";
    private static final String LAST_OK = "last_ok:";
    private static final SessionPolicy.SingleFlight FLIGHT =
        new SessionPolicy.SingleFlight();
    private static long sequence, activeFlow;

    private SchoolSession() { }

    /** 返回 false 表示已有一个认证流程在进行，调用方不要重复打开登录页。 */
    public static synchronized boolean beginReauth(Context context, String service) {
        long now = System.currentTimeMillis();
        if (!FLIGHT.begin()) return false;
        activeFlow = ++sequence;
        context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putString(PENDING, service == null ? "" : service)
            .putLong(REAUTH_AT, now).apply();
        return true;
    }

    public static synchronized void endReauth(Context context) {
        FLIGHT.end();
        activeFlow = 0;
        context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().remove(PENDING).remove(REAUTH_AT).apply();
    }

    public static synchronized long currentReauthId() { return activeFlow; }

    /** 旧页面延迟销毁时，不能释放新页面已经取得的认证锁。 */
    public static synchronized void endReauth(Context context, long flow) {
        if (flow != 0 && flow == activeFlow) endReauth(context);
    }

    public static boolean reauthActive() {
        return FLIGHT.active();
    }

    public static String pendingService(Context context) {
        return context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getString(PENDING, "");
    }

    /** 仅用于显示“上次成功验证”与诊断，不作为任何放行依据。 */
    public static void markActive(Context context, String service) {
        if (service == null || service.isEmpty()) return;
        boolean pg = context.getApplicationContext()
            .getSharedPreferences("school_profile", Context.MODE_PRIVATE)
            .getBoolean("postgraduate", false);
        String scope = new SchoolAccount(context.getApplicationContext()).scope(pg);
        String key = LAST_OK + (pg ? "pg" : "ug") + scope + "_" + service;
        context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putLong(key, System.currentTimeMillis()).apply();
    }

    public static long lastActive(Context context, String service) {
        if (service == null || service.isEmpty()) return 0;
        boolean pg = context.getApplicationContext()
            .getSharedPreferences("school_profile", Context.MODE_PRIVATE)
            .getBoolean("postgraduate", false);
        String scope = new SchoolAccount(context.getApplicationContext()).scope(pg);
        String key = LAST_OK + (pg ? "pg" : "ug") + scope + "_" + service;
        return context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getLong(key, 0);
    }
}
