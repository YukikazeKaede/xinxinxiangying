package cn.xinxinxiangying.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** 第二课堂活动列表的本机缓存，按账号分开保存。 */
public final class ActivityStore {
    private static final String STORE = "activities_v1";
    private final Context context;
    private final String owner;

    public ActivityStore(Context context) { this.context = context.getApplicationContext(); owner = key(); }

    private String key() {
        boolean postgraduate = context.getSharedPreferences("school_profile", Context.MODE_PRIVATE)
            .getBoolean("postgraduate", false);
        String scope = new SchoolAccount(context).scope(postgraduate);
        return (postgraduate ? "pg:" : "ug:") + scope + "_activities";
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public JSONObject read() {
        if (!isCurrent()) return null;
        String raw = preferences().getString(owner, "");
        if (raw == null || raw.isEmpty()) return null;
        try { return new JSONObject(raw); } catch (Exception e) { return null; }
    }

    public void save(JSONObject snapshot) throws Exception {
        if (!isCurrent()) throw new IllegalStateException("账号已变更，请重新读取");
        if (!preferences().edit().putString(owner, snapshot.toString()).commit())
            throw new IllegalStateException("保存失败，请重试");
    }

    public boolean isCurrent() { return AppSession.valid(context) && owner.equals(key()); }

    public long savedAt() {
        JSONObject saved = read();
        return saved == null ? 0 : saved.optLong("savedAt", 0);
    }

    public void clear() { preferences().edit().remove(owner).commit(); }
}
