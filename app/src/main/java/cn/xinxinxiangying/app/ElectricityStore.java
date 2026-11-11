package cn.xinxinxiangying.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;

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
        try {
            String raw = preferences().getString(owner, "");
            if (raw == null || raw.isEmpty()) return null;
            JSONObject saved = new JSONObject(raw);
            JSONObject balance = saved.optJSONObject("balance");
            // The initial portal build saved the numeric amount correctly but tagged it as 度.
            if (saved.optJSONObject("portalRoom") != null && balance != null && "度".equals(balance.optString("unit")))
                saved.put("balance", portalAmount(balance.getString("value")));
            return saved;
        }
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
    public void savePortalBalance(JSONObject raw, boolean choosing) throws Exception {
        String name = raw.optString("name").trim();
        String roomId = raw.optString("roomId").trim();
        String buildingId = raw.optString("buildingId").trim();
        String value = raw.optString("value").trim();
        if (name.isEmpty() || name.length() > 120 || name.matches("(?s).*[\\p{Cntrl}].*")
            || !roomId.matches("[A-Za-z0-9_-]{1,64}") || !buildingId.matches("[A-Za-z0-9_-]{1,64}")
            || !value.matches("-?\\d{1,8}(\\.\\d{1,4})?"))
            throw new IllegalArgumentException("学校返回的寝室或电量格式无法识别");
        JSONObject saved = read();
        if (saved == null) saved = new JSONObject();
        JSONObject previous = saved.optJSONObject("portalRoom");
        if (!choosing && previous != null && (!roomId.equals(previous.optString("roomId"))
                || !buildingId.equals(previous.optString("buildingId"))))
            throw new IllegalStateException("学校当前寝室与已保存的不同，请用“切换宿舍”确认");
        saved.put("portalRoom", new JSONObject().put("name", name).put("roomId", roomId).put("buildingId", buildingId))
            .put("balance", portalAmount(value))
            .put("updatedAt", System.currentTimeMillis());
        write(saved);
    }
    private static JSONObject portalAmount(String value) throws org.json.JSONException {
        String amount = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
        return new JSONObject().put("unit", "元").put("value", amount);
    }
    public static void clearAll(Context context) {
        context.getApplicationContext().getSharedPreferences(STORE, 0).edit().clear().commit();
    }
}
