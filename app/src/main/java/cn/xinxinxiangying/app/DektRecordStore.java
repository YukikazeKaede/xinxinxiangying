package cn.xinxinxiangying.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** 本机保存的第二课堂加分记录（学分认定页），按学校账号区分。 */
public final class DektRecordStore {
    private static final String STORE = "dekt_records_v1";
    private final Context context;
    private final String owner;

    public DektRecordStore(Context context) { this.context = context.getApplicationContext(); owner = key(); }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    private String key() {
        boolean postgraduate = context.getSharedPreferences("school_profile", Context.MODE_PRIVATE)
            .getBoolean("postgraduate", false);
        String scope = new SchoolAccount(context).scope(postgraduate);
        return (postgraduate ? "pg:" : "ug:") + scope + "_second_records";
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

    public long importedAt() {
        JSONObject saved = read();
        return saved == null ? 0 : saved.optLong("importedAt", 0);
    }

    public void clear() { preferences().edit().remove(owner).commit(); }

    public static void clearAll(Context context) {
        context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().commit();
    }
}
