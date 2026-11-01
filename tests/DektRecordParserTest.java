import cn.xinxinxiangying.app.DektRecordParser;
import cn.xinxinxiangying.app.SchoolCatalog;
import org.json.*;

/** Synthetic records only; no school page, account or session data. */
public final class DektRecordParserTest {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++; if (!value) throw new AssertionError(label);
    }
    private static JSONObject row(String time, String category) throws Exception {
        return new JSONObject().put("activity", "SYNTHETIC_ACTIVITY").put("credit", "0.1")
            .put("time", time).put("dl", "SYNTHETIC_CATEGORY").put("lb", category)
            .put("year", "2026-2027").put("term", "1").put("project", "SYNTHETIC_PROJECT")
            .put("source", "SYNTHETIC_SOURCE").put("name", "SYNTHETIC_NAME")
            .put("student", "SYNTHETIC_STUDENT");
    }
    private static JSONObject payload(JSONArray rows, int total) throws Exception {
        return new JSONObject().put("status", "complete").put("total", total).put("pages", 2)
            .put("records", rows).put("tail", "SYNTHETIC_PRIVATE_HTML");
    }
    private static void reject(JSONObject state, String label) throws Exception {
        try { DektRecordParser.parse(state); throw new AssertionError(label); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        JSONArray rows = new JSONArray().put(row("2026-10-26 16:43:54", "A"))
            .put(row("2026-10-26 16:43:55", "A")).put(row("2026-10-26 16:43:55", "B"));
        JSONObject parsed = DektRecordParser.parse(payload(rows, 3));
        check(parsed.getJSONArray("records").length() == 3, "distinct seconds and categories preserved");
        JSONObject first = parsed.getJSONArray("records").getJSONObject(0);
        check("2026-10-26 16:43:55".equals(first.getString("time")), "latest records first");
        check("0.10".equals(first.getString("credit")), "credit normalized");
        check(!parsed.toString().contains("SYNTHETIC_STUDENT"), "identity excluded");
        check(!parsed.toString().contains("SYNTHETIC_PRIVATE_HTML"), "HTML excluded");
        check(SchoolCatalog.DEKT_RECORDS.equals(parsed.getString("source")), "fixed source");
        check(parsed.getLong("importedAt") > 0 && parsed.getString("pages").equals("2"), "snapshot metadata");
        JSONObject only = row("2026-10-26 16:43:54", "A");
        JSONObject deduped = DektRecordParser.parse(payload(new JSONArray().put(only).put(only), 1));
        check(deduped.getJSONArray("records").length() == 1, "exact duplicate removed");
        reject(payload(rows, 4), "incomplete total rejected");
        reject(payload(rows, 3).put("status", "failed"), "failed read rejected");
        reject(payload(new JSONArray(), 0), "empty extraction rejected");
        reject(payload(new JSONArray().put(row("", "A").put("credit", "invalid")), 1), "invalid rows rejected");
        JSONArray excessive = new JSONArray();
        for (int i = 0; i < 801; i++) excessive.put(only);
        reject(payload(excessive, 801), "limit never silently truncates");
        check("2026-10-26 16:43:54".equals(DektRecordParser.stamp("2026-10-26T16:43:54.123")), "timestamp precision");
        check("-0.10".equals(DektRecordParser.credit("-0.1")), "negative adjustment");
        check("".equals(DektRecordParser.credit("1000")), "out of range credit");
        System.out.println("DektRecordParserTest: " + checks + " checks passed");
    }
}
