package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import cn.xinxinxiangying.app.*;
import org.json.*;
import java.lang.reflect.*;
import java.util.List;

/** Offline synthetic data; only an isolated .pr23test application may be modified. */
public final class DektRecordsInstrumentation extends Instrumentation {
    private Context context;
    private Activity activity;
    private int checks;
    private interface Work { void run() throws Exception; }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    private void ui(Work work) throws Exception {
        final Throwable[] failure = {null};
        runOnMainSync(() -> { try { work.run(); } catch (Throwable e) { failure[0] = e; } });
        if (failure[0] != null) throw new Exception("UI fixture failed", failure[0]);
        waitForIdleSync();
    }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(target, args);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static View text(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = text(((ViewGroup) view).getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }
    private void owner(String user, boolean pg) throws Exception {
        context.getSharedPreferences("school_profile", 0).edit().putBoolean("postgraduate", pg).commit();
        new SchoolAccount(context).save(pg, user, "", false);
        AppSession.invalidate(context);
        context.getSharedPreferences("school_session", 0).edit().putString("identity", (pg ? "pg:" : "ug:") + user).commit();
        check(AppSession.valid(context), "synthetic session established");
    }
    private JSONObject payload(boolean old) throws Exception {
        JSONArray records = new JSONArray().put(new JSONObject().put("activity", "SYNTHETIC_NEW_ACTIVITY")
            .put("credit", "0.1").put("time", "2026-10-26 16:43:54")
            .put("dl", "SYNTHETIC_CATEGORY").put("lb", "SYNTHETIC_CHILD").put("year", "2026-2027"));
        if (old) records.put(new JSONObject().put("activity", "SYNTHETIC_OLD_ACTIVITY").put("credit", "0.2")
            .put("time", "2025-10-26 16:43:54").put("dl", "SYNTHETIC_CATEGORY")
            .put("lb", "SYNTHETIC_CHILD").put("year", "2025-2026"));
        return new JSONObject().put("status", "complete").put("total", records.length()).put("records", records);
    }
    private void launch(Class<?> type) {
        activity = startActivitySync(new Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
    }
    private void close() throws Exception { if (activity != null) { ui(() -> activity.finish()); activity = null; } }
    private void click(String label) throws Exception {
        ui(() -> {
            View found = text(activity.getWindow().getDecorView(), label);
            check(found != null, "expected UI control exists");
            if (found.hasOnClickListeners()) found.performClick(); else ((View) found.getParent()).performClick();
        });
    }
    @Override public void onStart() {
        Bundle report = new Bundle(); int result = Activity.RESULT_OK; boolean owned = false;
        context = getTargetContext();
        try {
            check(context.getPackageName().endsWith(".pr23test"), "isolated test package required");
            owned = true;
            DektRecordStore.clearAll(context);
            Class.forName("DektRecordParserTest").getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            owner("SYNTHETIC_RECORD_A", false);
            DektRecordStore a = new DektRecordStore(context); a.save(DektRecordParser.parse(payload(true)));
            owner("SYNTHETIC_RECORD_B", false);
            DektRecordStore b = new DektRecordStore(context);
            check(b.read() == null && a.read() == null, "account reads are isolated");
            try { a.save(DektRecordParser.parse(payload(true))); throw new AssertionError("stale write accepted"); }
            catch (IllegalStateException expected) { checks++; }
            b.save(DektRecordParser.parse(payload(true)));
            a.clear();
            check(b.read() != null, "local clear does not delete another account");
            owner("SYNTHETIC_RECORD_B", true);
            DektRecordStore pg = new DektRecordStore(context);
            check(pg.read() == null, "student types are isolated");
            pg.save(DektRecordParser.parse(payload(true)));
            owner("SYNTHETIC_RECORD_B", false);
            new DektStore(context).save(DektParser.parse(new JSONObject().put("earned", "0.3").put("required", "1")
                .put("rows", new JSONArray().put(new JSONObject().put("name", "SYNTHETIC_CATEGORY").put("earned", "0.3").put("required", "1"))
                    .put(new JSONObject().put("name", "SYNTHETIC_CHILD").put("earned", "0.3").put("depth", 1)))));
            launch(DektActivity.class);
            View root = activity.getWindow().getDecorView();
            check(text(root, "加分记录") != null, "record entry is reachable");
            check((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) == 0, "credit page allows screenshots");
            click("SYNTHETIC_CHILD");
            check(text(root, "SYNTHETIC_NEW_ACTIVITY") != null && text(root, "SYNTHETIC_OLD_ACTIVITY") != null, "records expand under category");
            click("2025-2026");
            check(text(root, "SYNTHETIC_NEW_ACTIVITY") == null && text(root, "SYNTHETIC_OLD_ACTIVITY") != null, "year filters records");
            click("全部");
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ui(() -> {
                clipboard.setPrimaryClip(ClipData.newPlainText("synthetic", "SYNTHETIC_CLIPBOARD"));
                call(activity, "buildWeb", new Class<?>[0]);
                set(activity, "recordsTarget", true); set(activity, "importing", true); set(activity, "generation", 7);
                call(activity, "save", new Class<?>[]{JSONObject.class, int.class}, payload(false).put("total", 2), 7);
            });
            check(b.read().getJSONArray("records").length() == 2, "incomplete result preserves cache");
            ui(() -> check("SYNTHETIC_CLIPBOARD".contentEquals(clipboard.getPrimaryClip().getItemAt(0).getText()), "failure does not export diagnostics"));
            ui(() -> call(activity, "save", new Class<?>[]{JSONObject.class, int.class}, payload(false), 7));
            check(b.read().getJSONArray("records").length() == 1, "full refresh replaces deleted records");
            ui(() -> call(activity, "save", new Class<?>[]{JSONObject.class, int.class}, payload(true), 6));
            check(b.read().getJSONArray("records").length() == 1, "stale callback cannot overwrite cache");
            close();
            new DektStore(context).clear();
            launch(DektActivity.class);
            root = activity.getWindow().getDecorView();
            check(text(root, "加分记录") != null, "record entry survives missing credit overview");
            click("SYNTHETIC_CHILD");
            check(text(root, "SYNTHETIC_NEW_ACTIVITY") != null, "records render without overview snapshot");
            close();
            owner("SYNTHETIC_RECORD_A", false);
            new DektRecordStore(context).save(DektRecordParser.parse(payload(true)));
            owner("SYNTHETIC_RECORD_B", false);
            Field checked = UpdateChecker.class.getDeclaredField("autoChecked"); checked.setAccessible(true); checked.setBoolean(null, true);
            launch(MainActivity.class);
            ui(() -> call(activity, "confirmClear", new Class<?>[0]));
            boolean pressed = false;
            for (int attempt = 0; attempt < 30 && !pressed; attempt++) {
                AccessibilityNodeInfo window = getUiAutomation().getRootInActiveWindow();
                if (window != null) {
                    List<AccessibilityNodeInfo> nodes = window.findAccessibilityNodeInfosByText("清除");
                    for (AccessibilityNodeInfo node : nodes) {
                        if ("android.widget.Button".contentEquals(node.getClassName()) && node.isClickable())
                            pressed = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        node.recycle();
                    }
                    window.recycle();
                }
                if (!pressed) SystemClock.sleep(100);
            }
            check(pressed, "global clear confirmation is actionable");
            for (int attempt = 0; attempt < 50 && !context.getSharedPreferences("dekt_records_v1", 0).getAll().isEmpty(); attempt++) SystemClock.sleep(100);
            check(context.getSharedPreferences("dekt_records_v1", 0).getAll().isEmpty(), "global clear removes all student types and accounts");
            owner("SYNTHETIC_RECORD_B", false);
            check(new DektRecordStore(context).read() == null, "relogin does not recover cleared records");
            owner("SYNTHETIC_RECORD_B", true);
            check(new DektRecordStore(context).read() == null, "postgraduate records also stay cleared");
            report.putString("result", "PASS DektRecordsInstrumentation: " + checks + " checks");
        } catch (Throwable failure) {
            result = Activity.RESULT_CANCELED;
            report.putString("result", "FAIL " + android.util.Log.getStackTraceString(failure));
        } finally {
            if (owned) {
                try { close(); } catch (Exception ignored) { }
                DektRecordStore.clearAll(context); new SchoolAccount(context).clear();
            }
        }
        finish(result, report);
    }
}
