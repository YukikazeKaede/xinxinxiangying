package cn.xinxinxiangying.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/** 第二课堂学分页读到的文字 → 本机快照。页面结构未经核对，这里只做校验、去重与补全。 */
public final class DektParser {
    private DektParser() {}
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(\\.\\d{1,3})?");
    private static final int MAX_CATEGORIES = 60;

    public static JSONObject parse(JSONObject payload) throws Exception {
        if (payload == null) throw new IllegalArgumentException("没有读到页面内容，请刷新后重试");
        JSONArray rows = payload.optJSONArray("rows");
        if (rows == null || rows.length() == 0) throw new IllegalArgumentException("页面里没有找到学分明细，请展开“学分明细”后再读取");
        JSONArray categories = new JSONArray();
        JSONObject current = null;
        for (int i = 0; i < rows.length() && categories.length() < MAX_CATEGORIES; i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String name = text(row.optString("name", ""));
            if (name.isEmpty()) continue;
            boolean child = row.optInt("depth", 0) > 0;
            if (!child || current == null) {
                JSONObject known = find(categories, name);
                if (known != null) { current = known; continue; }
                if (categories.length() >= MAX_CATEGORIES) break;
                current = new JSONObject().put("name", name);
                current.put("earned", number(row.optString("earned", "")));
                current.put("required", range(row.optString("required", "")));
                current.put("children", new JSONArray());
                categories.put(current);
            } else {
                current.getJSONArray("children").put(item(row, name));
            }
        }
        if (categories.length() == 0) throw new IllegalArgumentException("页面里没有找到学分明细，请展开“学分明细”后再读取");
        JSONObject snapshot = new JSONObject();
        String earned = number(payload.optString("earned", ""));
        String required = range(payload.optString("required", ""));
        double sum = 0, want = 0;
        boolean sumReady = true, wantReady = true;
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.getJSONObject(i);
            String got = category.optString("earned", ""), need = category.optString("required", "");
            if (got.isEmpty()) sumReady = false; else sum += Double.parseDouble(got);
            if (need.isEmpty() || need.indexOf('~') >= 0) wantReady = false; else want += Double.parseDouble(need);
        }
        if (earned.isEmpty() && sumReady) earned = fixed(sum);
        if (required.isEmpty() && wantReady) required = fixed(want);
        snapshot.put("earned", earned);
        snapshot.put("required", required);
        snapshot.put("updated", text(payload.optString("updated", "")));
        snapshot.put("categories", categories);
        snapshot.put("importedAt", System.currentTimeMillis());
        snapshot.put("source", SchoolCatalog.DEKT_HOME);
        return snapshot;
    }

    private static JSONObject item(JSONObject row, String name) throws Exception {
        return new JSONObject().put("name", name)
            .put("earned", number(row.optString("earned", "")))
            .put("required", range(row.optString("required", "")));
    }

    private static JSONObject find(JSONArray categories, String name) {
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category != null && name.equals(category.optString("name", ""))) return category;
        }
        return null;
    }

    /** 页面上的数字，只接受 0–999.99；不认识的写法保留空串，由界面显示“—”。 */
    public static String number(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!NUMBER.matcher(value).matches()) return "";
        try {
            double number = Double.parseDouble(value);
            if (number < 0 || number > 999) return "";
            return String.format(Locale.ROOT, "%.2f", number);
        } catch (NumberFormatException e) { return ""; }
    }

    /** 需求可能是「1.50 ~ 3.00」这样的区间：保留区间，只归一小数位。 */
    public static String range(String raw) {
        String value = raw == null ? "" : raw.trim();
        int tilde = value.indexOf('~');
        if (tilde < 0) return number(value);
        String low = number(value.substring(0, tilde)), high = number(value.substring(tilde + 1));
        if (low.isEmpty() || high.isEmpty()) return "";
        return low + "~" + high;
    }

    public static String fixed(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    public static String text(String raw) {
        String value = raw == null ? "" : raw.replace('\u00a0', ' ').trim();
        value = value.replaceAll("\\s+", " ");
        return value.length() > 40 ? value.substring(0, 40) : value;
    }
}
