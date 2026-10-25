import cn.xinxinxiangying.app.ActivityFeed;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** 第二课堂活动整理规则的单元测试：只用合成数据，不联网。 */
public final class ActivityFeedTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static void check(String actual, String expected, String message) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(message + " → " + actual);
    }

    private static JSONObject item(String id, String name, String start, String end, String signupStart, String signupEnd, boolean top) {
        JSONObject row = new JSONObject();
        row.put("id", id);
        row.put("name", name);
        row.put("lbdm", "0104");
        row.put("lbmc", "志愿公益");
        row.put("dlmc", "德育");
        row.put("zbf", "团委");
        row.put("project", "天天讲");
        row.put("start", start);
        row.put("end", end);
        row.put("signupStart", signupStart);
        row.put("signupEnd", signupEnd);
        row.put("top", top);
        return row;
    }

    private static long at(String text) {
        long value = ActivityFeed.time(text);
        if (value <= 0) throw new AssertionError("时间解析失败：" + text);
        return value;
    }

    public static void main(String[] args) throws Exception {
        JSONObject row = item("A1", "图书馆志愿服务", "2026-11-01 13:00:00", "2026-11-01 16:00:00",
                "2026-10-20 09:00:00", "2026-10-31 18:00:00", false);
        JSONArray rows = new JSONArray();
        rows.put(row);

        JSONObject group = new JSONObject();
        group.put("name", "德育");
        group.put("items", rows);
        JSONArray groups = new JSONArray();
        groups.put(group);
        JSONObject list = new JSONObject();
        list.put("groups", groups);
        list.put("menus", new JSONArray());

        JSONArray flat = ActivityFeed.flatten(list);
        check(flat.length() == 1, "压平后应只有一条活动");
        JSONObject one = flat.getJSONObject(0);
        check("A1".equals(one.getString("id")), "活动 id 应保留");
        check("图书馆志愿服务".equals(one.getString("name")), "活动名称应保留");
        check("德育".equals(one.getString("dl")), "大类名应保留");
        check("志愿公益".equals(one.getString("lb")), "类型名应保留");
        check("团委".equals(one.getString("host")), "主办方应保留");
        check("天天讲".equals(one.getString("project")), "所属项目应保留");
        check(!one.getBoolean("top"), "普通活动不是置顶");
        check(ActivityFeed.flatten(new JSONObject()).length() == 0, "空列表应返回空数组");
        check(ActivityFeed.flatten(null).length() == 0, "null 列表应返回空数组");

        long now = at("2026-10-25 21:00:00");
        check("报名中".equals(ActivityFeed.state(one, now)), "报名时间窗内应为报名中");
        check("未开始".equals(ActivityFeed.state(item("A2", "x", "2026-11-01 13:00:00", "2026-11-01 16:00:00",
                "2026-11-10 09:00:00", "2026-11-20 18:00:00", false), now)), "报名尚未开始");
        check("已截止".equals(ActivityFeed.state(item("A3", "x", "2026-11-01 13:00:00", "2026-11-01 16:00:00",
                "2026-10-01 09:00:00", "2026-10-20 18:00:00", false), now)), "报名已截止");
        check("无需报名".equals(ActivityFeed.state(item("A4", "x", "2026-11-01 13:00:00", "2026-11-01 16:00:00",
                "", "", false), now)), "没有报名时间应为无需报名");
        check("已结束".equals(ActivityFeed.state(item("A5", "x", "2026-10-01 13:00:00", "2026-10-02 16:00:00",
                "2026-09-01 09:00:00", "2026-09-20 18:00:00", false), now)), "已经结束的活动");

        // 一年前结束的活动会被丢掉，一个月内的保留；置顶排最前。
        JSONArray mixed = new JSONArray();
        mixed.put(item("OLD", "旧活动", "2025-03-01 10:00:00", "2025-03-01 12:00:00", "", "", false));
        mixed.put(item("NEW", "新活动", "2026-11-05 10:00:00", "2026-11-05 12:00:00", "", "", false));
        mixed.put(item("PIN", "置顶活动", "2026-10-30 10:00:00", "2026-10-30 12:00:00", "", "", true));
        mixed.put(item("EDGE", "刚结束", "2026-09-27 10:00:00", "2026-09-27 12:00:00", "", "", false));
        JSONArray kept = ActivityFeed.visible(mixed, now);
        check(kept.length() == 3, "结束超过一个月的活动应被隐藏");
        check("PIN".equals(kept.getJSONObject(0).getString("id")), "置顶活动排最前");
        check("NEW".equals(kept.getJSONObject(1).getString("id")), "其余按开始时间从新到旧");
        check("EDGE".equals(kept.getJSONObject(2).getString("id")), "一个月内结束的仍显示");

        check("2026-2027".equals(ActivityFeed.year("2026-10-13 18:30:00")), "9 月以后算新学年");        check("2025-2026".equals(ActivityFeed.year("2026-03-01 10:00:00")), "3 月仍属上一学年");
        check("".equals(ActivityFeed.year("")), "空时间没有学年");

        check(ActivityFeed.time("2026-11-01 13:00:00") > 0, "标准时间可解析");
        check(ActivityFeed.time("2026-11-01 13:00") > 0, "没有秒也可解析");
        check(ActivityFeed.time("2026-11-01") > 0, "只有日期也可解析");
        check(ActivityFeed.time("2026/11/1 9:05") > 0, "斜杠格式也可解析");
        check(ActivityFeed.time("不是时间") == 0, "无法解析应返回 0");

        check("11-01 13:00 – 16:00".equals(ActivityFeed.span("2026-11-01 13:00:00", "2026-11-01 16:00:00")), "同一天只写一次日期");
        check("11-01 13:00 – 11-02 16:00".equals(ActivityFeed.span("2026-11-01 13:00:00", "2026-11-02 16:00:00")), "跨天两边都写日期");
        check("".equals(ActivityFeed.span("", "")), "没有时间时不显示");

        // 类型选项按板块归属：学校菜单里 lbdm 前两位就是大类代码。
        JSONArray menus = new JSONArray();
        String[][] menu = {
            {"0101", "讲座报告"}, {"0102", "主题教育"}, {"0103", "安全教育"}, {"0104", "志愿公益"},
            {"0201", "三创活动"}, {"0202", "社会实践"}, {"0203", "校园文明"}, {"0204", "劳动教育实践"},
            {"0301", "校园文化活动"}, {"0302", "文艺竞赛活动"}, {"0401", "体育"}, {"0402", "心理健康教育"}
        };
        for (String[] pair : menu) {
            JSONObject menuOne = new JSONObject();
            menuOne.put("code", pair[0]);
            menuOne.put("name", pair[1]);
            menus.put(menuOne);
        }
        JSONObject deGroup = new JSONObject();
        deGroup.put("name", "德育");
        deGroup.put("code", "01");
        JSONObject laoGroup = new JSONObject();
        laoGroup.put("name", "劳育");
        laoGroup.put("code", "02");
        JSONArray menuGroups = new JSONArray();
        menuGroups.put(deGroup);
        menuGroups.put(laoGroup);
        JSONObject cached = new JSONObject();
        cached.put("menus", menus);
        cached.put("groups", menuGroups);

        JSONArray items = new JSONArray();
        JSONObject moral = item("D1", "德育活动", "2026-11-01 10:00:00", "2026-11-01 12:00:00", "", "", false);
        moral.put("dl", "德育");
        moral.put("dlcode", "01");
        moral.put("lb", "志愿公益");
        items.put(moral);
        JSONObject labor = item("L1", "劳育活动", "2026-11-02 10:00:00", "2026-11-02 12:00:00", "", "", false);
        labor.put("dl", "劳育");
        labor.put("dlcode", "02");
        labor.put("lb", "三创活动");
        items.put(labor);

        List<String> all = ActivityFeed.types(cached, items, "");
        check(all.size() == 12, "全部板块列出学校菜单里的 12 个类型");
        List<String> deTypes = ActivityFeed.types(cached, items, "德育");
        check(deTypes.size() == 4, "德育有 4 个类型");
        check("讲座报告".equals(deTypes.get(0)) && "志愿公益".equals(deTypes.get(3)), "德育类型按学校顺序：讲座报告…志愿公益");
        List<String> laoTypes = ActivityFeed.types(cached, items, "劳育");
        check(laoTypes.size() == 4 && "三创活动".equals(laoTypes.get(0)), "劳育类型从三创活动开始");
        check(ActivityFeed.types(cached, items, "美育").isEmpty(), "菜单里没有归属的板块不列类型");
        check(ActivityFeed.types(null, items, "劳育").size() == 1, "旧缓存没有菜单时退回按活动里出现过的类型");
        check("01".equals(ActivityFeed.blockCode(cached, items, "德育")), "板块代码来自学校菜单");

        // 旧缓存没有 dldm/分组代码时，用类型代码前两位补出板块归属。
        JSONArray oldItems = new JSONArray();
        JSONObject oldMoral = item("O1", "旧德育活动", "2026-11-03 10:00:00", "2026-11-03 12:00:00", "", "", false);
        oldMoral.put("dl", "德育");
        oldMoral.put("lbdm", "0101");
        oldMoral.put("lb", "讲座报告");
        oldItems.put(oldMoral);
        JSONObject oldCache = new JSONObject();
        oldCache.put("menus", menus);
        oldCache.put("groups", new JSONArray());
        List<String> oldTypes = ActivityFeed.types(oldCache, oldItems, "德育");
        check(oldTypes.size() == 4 && "讲座报告".equals(oldTypes.get(0)), "旧缓存也能按类型代码补板块归属");

        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
            check(ActivityFeed.time("2026-11-01 13:00:00") == 1793509200000L, "学校时间固定为上海时区");
            check(ActivityFeed.year("2026-09-01 00:00:00"), "2026-2027", "跨时区手机仍按学校学年");
            check(ActivityFeed.span("2026-11-01 13:00:00", "2026-11-01 16:00:00"),
                "11-01 13:00 – 16:00", "跨时区手机仍显示学校时间");
        } finally { java.util.TimeZone.setDefault(previous); }
        check(ActivityFeed.time("2026-11-01 13:00:00invalid") == 0, "不接受日期尾部垃圾");
        check(ActivityFeed.time("2026-02-30 13:00:00") == 0, "不接受无效日期");
        System.out.println("ActivityFeedTest: " + checks + " checks passed");
    }
}
