package cn.xinxinxiangying.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/**
 * Local user-entered data. All collections start empty.
 * Course fields: name, teacher, room, weekday (1=Monday), startPeriod, endPeriod,
 * startWeek, endWeek, parity (all/odd/even). Grade fields: name, score, credits.
 * Feed fields: text, createdAt, liked. Conversation: title. Message: text, createdAt, sender.
 * IDs are generated on create and preserved on update. Lists retain insertion order.
 */
public final class LocalStore {
    private static final String DATA = "data";
    private final SharedPreferences preferences;
    private JSONObject root;

    public LocalStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("xinxin_local_v1", Context.MODE_PRIVATE);
        try { root = new JSONObject(preferences.getString(DATA, "{}")); }
        catch (JSONException e) { throw new IllegalStateException("本地数据损坏，请从备份恢复", e); }
    }

    private static JSONObject copy(JSONObject value) {
        try { return new JSONObject(value.toString()); }
        catch (JSONException e) { throw new IllegalArgumentException("无效 JSON", e); }
    }

    private void persist(JSONObject next) {
        if (!preferences.edit().putString(DATA, next.toString()).commit())
            throw new IllegalStateException("本地数据保存失败");
        root = next;
    }

    private JSONArray array(String key) {
        JSONArray value = root.optJSONArray(key);
        if (value == null) return new JSONArray();
        try { return new JSONArray(value.toString()); }
        catch (JSONException e) { throw new IllegalStateException(e); }
    }

    private String save(String key, JSONObject item) {
        if (item == null) throw new IllegalArgumentException("记录不能为空");
        JSONObject record = copy(item);
        String id = record.optString("id", "").trim();
        if (id.isEmpty()) id = UUID.randomUUID().toString();
        try {
            record.put("id", id);
            JSONArray values = array(key);
            boolean updated = false;
            for (int i = 0; i < values.length(); i++) {
                if (id.equals(values.getJSONObject(i).optString("id"))) {
                    values.put(i, record); updated = true; break;
                }
            }
            if (!updated) values.put(record);
            JSONObject next = copy(root); next.put(key, values); persist(next);
            return id;
        } catch (JSONException e) { throw new IllegalArgumentException("记录格式错误", e); }
    }

    private boolean delete(String key, String id) {
        JSONArray values = array(key);
        for (int i = 0; i < values.length(); i++) {
            JSONObject record = values.optJSONObject(i);
            if (record != null && record.optString("id").equals(id)) {
                values.remove(i);
                try { JSONObject next = copy(root); next.put(key, values); persist(next); }
                catch (JSONException e) { throw new IllegalStateException(e); }
                return true;
            }
        }
        return false;
    }

    public synchronized JSONArray listCourses() { return array("courses"); }
    public synchronized String saveCourse(JSONObject course) { return save("courses", course); }
    public synchronized boolean deleteCourse(String id) { return delete("courses", id); }
    public synchronized JSONArray listGrades() { return array("grades"); }
    public synchronized String saveGrade(JSONObject grade) {
        if (grade == null) throw new IllegalArgumentException("成绩不能为空");
        double score = grade.optDouble("score", Double.NaN);
        double credits = grade.optDouble("credits", Double.NaN);
        if (!Double.isFinite(score) || score < 0 || score > 100 || !Double.isFinite(credits) || credits <= 0)
            throw new IllegalArgumentException("成绩须为 0–100，学分须大于 0");
        return save("grades", grade);
    }
    public synchronized boolean deleteGrade(String id) { return delete("grades", id); }

    // No GPA conversion: school-issued values and inclusion rules must be verified first.

    public synchronized String getSemesterStart() { return root.optString("semesterStart", ""); }
    public synchronized int getSemesterWeeks() { return root.optInt("semesterWeeks", 0); }
    public synchronized void setSemesterStart(String date) {
        if (date == null || (!date.isEmpty() && !validDate(date)))
            throw new IllegalArgumentException("日期格式应为 YYYY-MM-DD");
        setValue("semesterStart", date);
    }
    private static boolean validDate(String date) {
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
        format.setLenient(false);
        try { return format.parse(date) != null; } catch (java.text.ParseException e) { return false; }
    }
    public synchronized void setSemesterWeeks(int weeks) {
        if (weeks < 0 || weeks > 60) throw new IllegalArgumentException("学期周数须为 0–60");
        setValue("semesterWeeks", weeks);
    }
    private void setValue(String key, Object value) {
        try { JSONObject next = copy(root); next.put(key, value); persist(next); }
        catch (JSONException e) { throw new IllegalArgumentException(e); }
    }

    public synchronized JSONArray listFeeds() { return array("feeds"); }
    public synchronized String saveFeed(JSONObject feed) { return save("feeds", feed); }
    public synchronized boolean deleteFeed(String id) { return delete("feeds", id); }
    public synchronized boolean toggleFeedLike(String id) {
        JSONArray values = listFeeds();
        for (int i = 0; i < values.length(); i++) {
            JSONObject record = values.optJSONObject(i);
            if (record != null && record.optString("id").equals(id)) {
                boolean liked = !record.optBoolean("liked", false);
                try { record.put("liked", liked); } catch (JSONException e) { throw new IllegalStateException(e); }
                saveFeed(record); return liked;
            }
        }
        throw new IllegalArgumentException("动态不存在");
    }

    public synchronized JSONArray listConversations() { return array("conversations"); }
    public synchronized String saveConversation(JSONObject conversation) { return save("conversations", conversation); }
    private String messageKey(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) throw new IllegalArgumentException("会话不能为空");
        JSONArray values = listConversations();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item != null && conversationId.equals(item.optString("id"))) return "messages:" + conversationId;
        }
        throw new IllegalArgumentException("会话不存在");
    }
    public synchronized boolean deleteConversation(String id) {
        JSONArray values = listConversations();
        for (int i = 0; i < values.length(); i++) {
            if (id != null && id.equals(values.optJSONObject(i).optString("id"))) {
                values.remove(i);
                try {
                    JSONObject next = copy(root); next.put("conversations", values);
                    next.remove("messages:" + id); persist(next); return true;
                } catch (JSONException e) { throw new IllegalStateException(e); }
            }
        }
        return false;
    }
    public synchronized JSONArray listMessages(String conversationId) { return array(messageKey(conversationId)); }
    public synchronized String saveMessage(String conversationId, JSONObject message) { return save(messageKey(conversationId), message); }
    public synchronized boolean deleteMessage(String conversationId, String id) { return delete(messageKey(conversationId), id); }

    public synchronized String exportBackup() {
        try { return new JSONObject().put("format", "xinxinxiangying-local").put("version", 1).put("data", copy(root)).toString(2); }
        catch (JSONException e) { throw new IllegalStateException(e); }
    }
    /** Validates the whole backup before an atomic replacement; does not merge partial data. */
    public synchronized void restoreBackup(String json) {
        try {
            JSONObject backup = new JSONObject(json);
            if (!"xinxinxiangying-local".equals(backup.optString("format")) || backup.optInt("version") != 1)
                throw new IllegalArgumentException("不支持的备份格式");
            JSONObject next = backup.getJSONObject("data");
            for (java.util.Iterator<String> it = next.keys(); it.hasNext();) {
                String key = it.next();
                if (key.equals("semesterStart")) {
                    String date = next.getString(key);
                    if (!date.isEmpty() && !validDate(date)) throw new IllegalArgumentException("备份日期无效");
                } else if (key.equals("semesterWeeks")) {
                    int weeks = next.getInt(key);
                    if (weeks < 0 || weeks > 60) throw new IllegalArgumentException("备份周数无效");
                } else if (key.equals("courses") || key.equals("grades") || key.equals("feeds") || key.equals("conversations") || key.startsWith("messages:")) {
                    JSONArray items = next.getJSONArray(key); java.util.HashSet<String> ids = new java.util.HashSet<>();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i); String id = item.getString("id");
                        if (id.trim().isEmpty() || !ids.add(id)) throw new IllegalArgumentException("备份记录 ID 无效或重复");
                        if (key.equals("grades")) {
                            double score = item.getDouble("score"), credits = item.getDouble("credits");
                            if (!Double.isFinite(score) || score < 0 || score > 100 || !Double.isFinite(credits) || credits <= 0)
                                throw new IllegalArgumentException("备份成绩无效");
                        }
                    }
                } else throw new IllegalArgumentException("备份包含未知字段：" + key);
            }
            java.util.HashSet<String> conversationIds = new java.util.HashSet<>();
            JSONArray conversations = next.optJSONArray("conversations");
            if (conversations != null) for (int i = 0; i < conversations.length(); i++)
                conversationIds.add(conversations.getJSONObject(i).getString("id"));
            for (java.util.Iterator<String> it = next.keys(); it.hasNext();) {
                String key = it.next();
                if (key.startsWith("messages:") && !conversationIds.contains(key.substring(9)))
                    throw new IllegalArgumentException("备份消息引用不存在的会话");
            }
            persist(copy(next));
        } catch (JSONException e) { throw new IllegalArgumentException("备份 JSON 无效", e); }
    }
}
