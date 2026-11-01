package cn.xinxinxiangying.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/** 「学分认定」页读到的加分记录 → 本机快照。只做校验、去重与字段归一。 */
public final class DektRecordParser {
    private DektRecordParser() {}
    private static final Pattern CREDIT = Pattern.compile("[+-]?\\d{1,3}(\\.\\d{1,4})?");
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}(:\\d{2})?)?");
    private static final int MAX_RECORDS = 800;

    public static JSONObject parse(JSONObject payload) throws Exception {
        if (payload == null) throw new IllegalArgumentException("没有读到页面内容，请刷新后重试");
        if (!"complete".equals(payload.optString("status", "")))
            throw new IllegalArgumentException("加分记录尚未读完，原缓存保留");
        JSONArray rows = payload.optJSONArray("records");
        if (rows == null || rows.length() == 0)
            throw new IllegalArgumentException("页面里没有找到学分认定记录，请确认已登录并按学校默认条件查询");
        if (rows.length() > MAX_RECORDS) throw new IllegalArgumentException("加分记录过多，原缓存保留");
        List<JSONObject> valid = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) throw new IllegalArgumentException("加分记录不完整，原缓存保留");
            String activity = text(row.optString("activity", ""), 120);
            String credit = credit(row.optString("credit", ""));
            if (activity.isEmpty() || credit.isEmpty()) throw new IllegalArgumentException("加分记录不完整，原缓存保留");
            JSONObject record = new JSONObject()
                .put("activity", activity)
                .put("credit", credit)
                .put("time", stamp(row.optString("time", "")))
                .put("dl", text(row.optString("dl", ""), 40))
                .put("lb", text(row.optString("lb", ""), 40))
                .put("project", text(row.optString("project", ""), 60))
                .put("year", text(row.optString("year", ""), 20))
                .put("term", text(row.optString("term", ""), 20))
                .put("source", text(row.optString("source", ""), 20));
            JSONArray key = new JSONArray();
            for (String field : new String[]{"activity", "credit", "time", "dl", "lb", "project", "year", "term", "source"})
                key.put(record.optString(field, ""));
            if (seen.add(key.toString())) valid.add(record);
        }
        valid.sort((a, b) -> b.optString("time", "").compareTo(a.optString("time", "")));
        JSONArray records = new JSONArray();
        for (JSONObject row : valid) records.put(row);
        String total = payload.optString("total", "").trim();
        if (!total.matches("\\d{1,5}") || Integer.parseInt(total) != records.length())
            throw new IllegalArgumentException("加分记录条数不完整，原缓存保留");
        JSONObject snapshot = new JSONObject();
        snapshot.put("records", records);
        snapshot.put("total", total);
        String pages = payload.optString("pages", "").trim();
        if (!pages.isEmpty() && pages.matches("\\d{1,3}")) snapshot.put("pages", pages);
        snapshot.put("importedAt", System.currentTimeMillis());
        snapshot.put("source", SchoolCatalog.DEKT_RECORDS);
        return snapshot;
    }

    /** 认定学分只接受 -99～999 的数字，统一两位小数；其它写法丢弃这一条。 */
    public static String credit(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!CREDIT.matcher(value).matches()) return "";
        try {
            double number = Double.parseDouble(value);
            if (number < -99 || number > 999) return "";
            return String.format(Locale.ROOT, "%.2f", number);
        } catch (NumberFormatException e) { return ""; }
    }

    /** 保留学校创建时间的秒数，避免同一分钟内的不同记录被合并。 */
    public static String stamp(String raw) {
        String value = raw == null ? "" : raw.trim().replace('T', ' ');
        int dot = value.indexOf('.');
        if (dot > 10) value = value.substring(0, dot);
        if (!DAY.matcher(value).matches()) return "";
        return value;
    }

    private static String text(String raw, int max) {
        String value = raw == null ? "" : raw.replace('\u00a0', ' ').trim();
        value = value.replaceAll("\\s+", " ");
        return value.length() > max ? value.substring(0, max) : value;
    }
}
