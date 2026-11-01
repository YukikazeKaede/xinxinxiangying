package cn.xinxinxiangying.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Only selected dormitory fields and an explicitly labelled school balance are persisted. */
public final class ElectricityParser {
    private ElectricityParser() { }
    private static final String[] ROLES = {"elesys", "school", "area", "build", "floor", "room"};
    public static JSONObject room(JSONObject input) throws Exception {
        JSONArray rows = input == null ? null : input.optJSONArray("fields");
        if (rows == null || rows.length() > ROLES.length) throw new IllegalArgumentException("请先选择寝室");
        JSONArray fields = new JSONArray(), key = new JSONArray();
        boolean building = false, bedroom = false;
        for (String role : ROLES) {
            JSONObject found = null;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row != null && role.equals(row.optString("role"))) {
                    if (found != null) throw new IllegalArgumentException("寝室选项不明确，请重新选择");
                    found = row;
                }
            }
            if (found == null) continue;
            String id = text(found.optString("id"), 64), value = text(found.optString("value"), 120);
            String title = text(found.optString("title"), 120);
            if (!id.matches("[A-Za-z][A-Za-z0-9_.:-]{0,63}") || value.isEmpty() || "-1".equals(value)
                || title.isEmpty() || title.contains("请选择")) throw new IllegalArgumentException("请完成寝室选择后再保存");
            fields.put(new JSONObject().put("role", role).put("id", id).put("value", value).put("title", title));
            key.put(new JSONArray().put(role).put(id).put(value));
            if ("build".equals(role)) building = true;
            if ("room".equals(role)) bedroom = true;
        }
        if (!building || !bedroom || fields.length() != rows.length()) throw new IllegalArgumentException("请完成楼栋和房间选择");
        return new JSONObject().put("fields", fields).put("key", key.toString());
    }
    public static JSONObject balance(JSONObject input) throws Exception {
        if (input == null || !"complete".equals(input.optString("status")))
            throw new IllegalArgumentException("本次未读取到电费余额");
        JSONObject found = input.optJSONObject("balance");
        String value = found == null ? "" : found.optString("value", "").trim();
        String unit = found == null ? "" : found.optString("unit", "").trim();
        if (!value.matches("-?\\d{1,8}(\\.\\d{1,4})?") || !("元".equals(unit) || "度".equals(unit)))
            throw new IllegalArgumentException("学校余额格式暂无法识别");
        BigDecimal number = new BigDecimal(value);
        value = "元".equals(unit) ? number.setScale(2, RoundingMode.HALF_UP).toPlainString()
            : number.stripTrailingZeros().toPlainString();
        return new JSONObject().put("room", room(input.optJSONObject("room")))
            .put("balance", new JSONObject().put("value", value).put("unit", unit));
    }
    public static String description(JSONObject room) {
        JSONArray fields = room == null ? null : room.optJSONArray("fields");
        StringBuilder result = new StringBuilder();
        if (fields != null) for (int i = 0; i < fields.length(); i++) {
            JSONObject row = fields.optJSONObject(i);
            if (row == null || "elesys".equals(row.optString("role"))) continue;
            if (result.length() > 0) result.append(" / ");
            result.append(row.optString("title"));
        }
        return result.length() == 0 ? "尚未选择寝室" : result.toString();
    }
    private static String text(String raw, int max) throws Exception {
        String value = raw == null ? "" : raw.replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
        if (value.length() > max || value.matches("(?s).*[\\p{Cntrl}].*")) throw new IllegalArgumentException("寝室信息格式无法识别");
        return value;
    }
}
