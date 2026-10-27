package cn.xinxinxiangying.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** 第二课堂活动的整理规则：时间、状态、筛选与排序；与界面无关，便于单元测试。 */
public final class ActivityFeed {
    /** 结束超过这么多天的活动不再列出：用户只关心还能参加或刚结束的。 */
    public static final long KEEP = 31L * 24 * 60 * 60 * 1000;
    public static final String NONE = "全部";

    private ActivityFeed() { }

    /** 学校返回的分组列表压成一层活动，保留显示需要的字段。 */
    public static JSONArray flatten(JSONObject list) {
        JSONArray items = new JSONArray();
        JSONArray groups = list == null ? null : list.optJSONArray("groups");
        if (groups == null) return items;
        for (int g = 0; g < groups.length(); g++) {
            JSONObject group = groups.optJSONObject(g);
            if (group == null) continue;
            String groupName = group.optString("name", "");
            JSONArray rows = group.optJSONArray("items");
            if (rows == null) continue;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || row.optString("id", "").isEmpty()) continue;
                JSONObject item = new JSONObject();
                try {
                    item.put("id", row.optString("id", ""));
                    item.put("name", row.optString("name", ""));
                    item.put("dl", row.optString("dlmc", groupName));
                    item.put("dlcode", row.optString("dldm", group.optString("code", "")));
                    item.put("lb", row.optString("lbmc", ""));
                    item.put("lbdm", row.optString("lbdm", ""));
                    item.put("host", row.optString("zbf", ""));
                    item.put("project", row.optString("project", ""));
                    item.put("start", row.optString("start", ""));
                    item.put("end", row.optString("end", ""));
                    item.put("signupStart", row.optString("signupStart", ""));
                    item.put("signupEnd", row.optString("signupEnd", ""));
                    item.put("top", row.optBoolean("top", false));
                } catch (Exception e) {
                    continue;
                }
                items.put(item);
            }
        }
        return items;
    }

    /** 类型选项：选了板块就只列这个板块出现过的类型，避免点进去是空列表。 */
    public static List<String> types(JSONObject list, JSONArray items, String block) {
        List<String> out = new ArrayList<>();
        String prefix = block == null || block.isEmpty() ? "" : blockCode(list, items, block);
        JSONArray menus = list == null ? null : list.optJSONArray("menus");
        if (menus != null && (!prefix.isEmpty() || (block == null || block.isEmpty()))) {
            for (int i = 0; i < menus.length(); i++) {
                JSONObject menu = menus.optJSONObject(i);
                if (menu == null) continue;
                String code = menu.optString("code", "");
                String name = menu.optString("name", "");
                if (name.isEmpty()) continue;
                // 学校菜单里 lbdm 的前两位就是大类代码：01 德育、02 劳育、03 美育、04 健康教育。
                if (!prefix.isEmpty() && !code.startsWith(prefix)) continue;
                if (!out.contains(name)) out.add(name);
            }
        }
        // 菜单可能缺项或与活动数据不同步：把该板块活动里实际出现过的类型补在后面，
        // 避免“板块下明明有这类活动，类型栏却选不到”。
        for (String name : fromItems(items, block)) if (!out.contains(name)) out.add(name);
        return out;
    }

    /** 板块名对应的学校大类代码；旧缓存没有代码时返回空串。 */
    public static String blockCode(JSONObject list, JSONArray items, String block) {
        JSONArray groups = list == null ? null : list.optJSONArray("groups");
        if (groups != null) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i);
                if (group == null || !block.equals(group.optString("name", ""))) continue;
                String code = group.optString("code", "");
                if (!code.isEmpty()) return code;
            }
        }
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || !block.equals(item.optString("dl", ""))) continue;
                String code = item.optString("dlcode", "");
                if (!code.isEmpty()) return code;
                // 旧缓存没有大类代码时，用类型代码的前两位补：0101 讲座报告 → 01 德育。
                String label = item.optString("lbdm", "");
                if (label.length() >= 2) return label.substring(0, 2);
            }
        }
        return "";
    }

    private static List<String> fromItems(JSONArray items, String block) {
        List<String> out = new ArrayList<>();
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            if (block != null && !block.isEmpty() && !block.equals(item.optString("dl", ""))) continue;
            String lb = item.optString("lb", "");
            if (!lb.isEmpty() && !out.contains(lb)) out.add(lb);
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** 去掉结束超过一个月的活动，置顶的排前面，其余按开始时间从新到旧。 */
    public static JSONArray visible(JSONArray items, long now) {
        List<JSONObject> kept = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                long end = time(item.optString("end", ""));
                if (end > 0 && now - end > KEEP) continue;
                kept.add(item);
            }
        }
        java.util.Collections.sort(kept, (left, right) -> {
            boolean a = left.optBoolean("top", false), b = right.optBoolean("top", false);
            if (a != b) return a ? -1 : 1;
            long one = time(right.optString("start", "")), two = time(left.optString("start", ""));
            return Long.compare(one, two);
        });
        JSONArray out = new JSONArray();
        for (JSONObject item : kept) out.put(item);
        return out;
    }

    /** 报名状态：已结束 / 已截止 / 报名中 / 未开始 / 无需报名。 */
    public static String state(JSONObject item, long now) {
        if (item == null) return "已结束";
        long end = time(item.optString("end", ""));
        long signupStart = time(item.optString("signupStart", ""));
        long signupEnd = time(item.optString("signupEnd", ""));
        if (end > 0 && end < now) return "已结束";
        if (signupStart <= 0 && signupEnd <= 0) return "无需报名";
        if (signupEnd > 0 && signupEnd < now) return "已截止";
        if (signupStart > 0 && signupStart > now) return "未开始";
        return "报名中";
    }

    /** 学年：9 月以后算新学年，例如 2026-10-13 属于 2026-2027。 */
    public static String year(String start) {
        long value = time(start);
        if (value <= 0) return "";
        String text = format("yyyy-M").format(new Date(value));
        String[] parts = text.split("-");
        try {
            int y = Integer.parseInt(parts[0]), m = Integer.parseInt(parts[1]);
            int first = m >= 9 ? y : y - 1;
            return first + "-" + (first + 1);
        } catch (Exception e) { return ""; }
    }

    public static long time(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return 0;
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "yyyy/M/d H:mm"};
        for (String pattern : patterns) {
            try {
                ParsePosition position = new ParsePosition(0);
                Date date = format(pattern).parse(value, position);
                if (date != null && position.getIndex() == value.length()) return date.getTime();
            } catch (Exception ignored) { }
        }
        return 0;
    }

    /** 「11-01 13:00 – 16:00」；跨天或跨年时两边都带日期。 */
    public static String span(String start, String end) {
        long one = time(start), two = time(end);
        if (one <= 0 && two <= 0) return "";
        if (one <= 0) return brief(two);
        if (two <= 0) return brief(one);
        String left = brief(one), right = brief(two);
        String sameDay = "yyyy-MM-dd";
        SimpleDateFormat day = format(sameDay);
        if (day.format(new Date(one)).equals(day.format(new Date(two))))
            return left + " – " + right.substring(right.indexOf(' ') + 1);
        return left + " – " + right;
    }

    private static String brief(long millis) {
        return format("MM-dd HH:mm").format(new Date(millis));
    }

    private static SimpleDateFormat format(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return format;
    }
}
