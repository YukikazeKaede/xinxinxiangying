package cn.xinxinxiangying.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Account-scoped selected dormitory and last successfully read balance. */
public final class ElectricityStore {
    private static final String STORE = "electricity_v1";
    private final Context context;
    private final String owner;
    public ElectricityStore(Context context) { this.context = context.getApplicationContext(); owner = key(); }
    private String key() {
        boolean pg = context.getSharedPreferences("school_profile", 0).getBoolean("postgraduate", false);
        return (pg ? "pg:" : "ug:") + new SchoolAccount(context).scope(pg);
    }
    private SharedPreferences preferences() { return context.getSharedPreferences(STORE, 0); }
    public boolean isCurrent() { return AppSession.valid(context) && owner.equals(key()); }
    public JSONObject read() {
        if (!isCurrent()) return null;
        try { String raw = preferences().getString(owner, ""); return raw == null || raw.isEmpty() ? null : new JSONObject(raw); }
        catch (Exception ignored) { return null; }
    }
    private void write(JSONObject value) throws Exception {
        if (!isCurrent()) throw new IllegalStateException("账号已变更，请重新打开宿舍电费");
        if (!preferences().edit().putString(owner, value.toString()).commit()) throw new IllegalStateException("保存失败，请重试");
    }
    public void saveRoom(JSONObject raw) throws Exception {
        JSONObject room = ElectricityParser.room(raw), old = read();
        JSONObject previous = old == null ? null : old.optJSONObject("room");
        JSONObject saved = previous != null && room.optString("key").equals(previous.optString("key")) ? old : new JSONObject();
        saved.put("room", room);
        write(saved);
    }
    public void saveBalance(JSONObject expected, JSONObject raw) throws Exception {
        JSONObject parsed = ElectricityParser.balance(raw), saved = read();
        JSONObject current = saved == null ? null : saved.optJSONObject("room");
        String key = ElectricityParser.room(expected).getString("key");
        if (current == null || !key.equals(current.optString("key"))
            || !key.equals(parsed.getJSONObject("room").optString("key")))
            throw new IllegalStateException("寝室已变更，本次结果未保存");
        saved.put("room", parsed.getJSONObject("room")).put("balance", parsed.getJSONObject("balance"))
            .put("updatedAt", System.currentTimeMillis());
        write(saved);
    }
    public static void clearAll(Context context) {
        context.getApplicationContext().getSharedPreferences(STORE, 0).edit().clear().commit();
    }
}
