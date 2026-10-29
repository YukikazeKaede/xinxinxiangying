package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import cn.xinxinxiangying.app.*;
import org.json.*;
import java.lang.reflect.*;

/** Offline synthetic fixtures. Refuses devices containing a non-synthetic account. */
public final class SecondClassroomInstrumentation extends Instrumentation {
    private int checks;
    private Activity activity;
    private Context context;
    private boolean keepFixture;
    private interface Work { void run() throws Exception; }
    @Override public void onCreate(Bundle args) { super.onCreate(args); keepFixture = args != null && "true".equals(args.getString("keepFixture")); start(); }
    private void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    private void ui(Work work) throws Exception {
        final Throwable[] error = new Throwable[1];
        runOnMainSync(() -> { try { work.run(); } catch (Throwable e) { error[0] = e; } });
        if (error[0] != null) throw new Exception("UI fixture failed", error[0]);
        waitForIdleSync();
    }
    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    private static void call(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(target);
    }
    private static View text(View root, String wanted) {
        if (root instanceof TextView && wanted.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = text(((ViewGroup) root).getChildAt(i), wanted); if (found != null) return found;
        }
        return null;
    }
    private static Button button(View root, String wanted) {
        if (root instanceof Button && wanted.contentEquals(((Button) root).getText())) return (Button) root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            Button found = button(((ViewGroup) root).getChildAt(i), wanted); if (found != null) return found;
        }
        return null;
    }
    private void owner(String name, boolean pg) throws Exception {
        context.getSharedPreferences("school_profile", 0).edit().putBoolean("postgraduate", pg).commit();
        new SchoolAccount(context).save(pg, name, "", false);
        AppSession.invalidate(context);
        context.getSharedPreferences("school_session", 0).edit().putString("identity", (pg ? "pg:" : "ug:") + name).commit();
        check(AppSession.valid(context), "synthetic session granted");
    }
    private void launch(Class<?> type) {
        activity = startActivitySync(new Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
    }
    private void close() throws Exception { if (activity != null) { ui(() -> activity.finish()); activity = null; } }
    private JSONObject credits() throws Exception {
        return DektParser.parse(new JSONObject().put("earned", "2.40").put("required", "8.00")
            .put("rows", new JSONArray().put(new JSONObject().put("name", "SYNTHETIC_CATEGORY").put("earned", "2.4").put("required", "3"))
                .put(new JSONObject().put("name", "SYNTHETIC_CHILD").put("earned", "1.2").put("depth", 1))));
    }
    private JSONObject activities() throws Exception {
        JSONArray items = new JSONArray();
        for (String id : new String[]{"A", "B"}) items.put(new JSONObject().put("id", "SYNTHETIC_" + id)
            .put("name", "SYNTHETIC_ACTIVITY_" + id).put("dlmc", "德育").put("lbmc", "志愿公益")
            .put("start", "2099-11-01 13:00:00").put("end", "2099-11-01 16:00:00"));
        JSONArray menus = new JSONArray();
        for (int i = 1; i <= 12; i++) {
            menus.put(new JSONObject().put("code", String.format(java.util.Locale.ROOT, "01%02d", i))
                .put("name", i == 1 ? "志愿公益" : "SYNTHETIC_MORAL_TYPE_" + i));
            menus.put(new JSONObject().put("code", String.format(java.util.Locale.ROOT, "03%02d", i))
                .put("name", "SYNTHETIC_ART_MENU_" + i));
        }
        JSONArray art = new JSONArray().put(new JSONObject().put("id", "SYNTHETIC_ART")
            .put("name", "SYNTHETIC_ART_ACTIVITY").put("dlmc", "美育").put("lbmc", "SYNTHETIC_ART_TYPE")
            .put("start", "2099-11-01 13:00:00").put("end", "2099-11-01 16:00:00"));
        return new JSONObject().put("savedAt", System.currentTimeMillis()).put("list", new JSONObject()
            .put("menus", menus).put("groups", new JSONArray()
                .put(new JSONObject().put("name", "德育").put("code", "01").put("items", items))
                .put(new JSONObject().put("name", "美育").put("code", "03").put("items", art))));
    }
    private ViewGroup filterRow(String name) throws Exception {
        return (ViewGroup) text((View) field(activity, "filterHost"), name).getParent();
    }
    private void filterInteractions() throws Exception {
        ui(() -> text(filterRow("板块"), "德育").performClick());
        ui(() -> text(filterRow("类型"), "志愿公益").performClick());
        check("志愿公益".equals(field(activity, "type")), "type selection takes effect");
        ui(() -> text(filterRow("板块"), "美育").performClick());
        check("".equals(field(activity, "type")), "changing block clears the previous type");
        View root = activity.getWindow().getDecorView();
        check(text(root, "SYNTHETIC_ART_ACTIVITY") != null, "new block is not hidden by a stale type");
        check(text(filterRow("类型"), "SYNTHETIC_ART_TYPE") != null, "type missing from menus is available in the UI");
        ViewGroup row = filterRow("类型");
        View all = text(row, ActivityFeed.NONE);
        HorizontalScrollView scroll = null;
        for (int i = 0; i < row.getChildCount(); i++)
            if (row.getChildAt(i) instanceof HorizontalScrollView) scroll = (HorizontalScrollView) row.getChildAt(i);
        final HorizontalScrollView scroller = scroll;
        check(scroller != null, "type options support horizontal scrolling");
        final int[] before = new int[2], after = new int[2];
        ui(() -> { all.getLocationOnScreen(before); scroller.scrollTo(Integer.MAX_VALUE, 0); });
        ui(() -> all.getLocationOnScreen(after));
        check(scroller.getScrollX() > 0, "long type list actually scrolls");
        check(before[0] == after[0], "all option stays fixed while types scroll");
        check(all.getGlobalVisibleRect(new android.graphics.Rect()), "fixed all option remains visible");
        ui(() -> text(filterRow("类型"), "SYNTHETIC_ART_TYPE").performClick());
        check("SYNTHETIC_ART_TYPE".equals(field(activity, "type")), "merged type can be selected");
        ui(() -> text(filterRow("类型"), ActivityFeed.NONE).performClick());
        check("".equals(field(activity, "type")), "fixed all option clears type selection");
        ui(() -> text(filterRow("板块"), ActivityFeed.NONE).performClick());
        check("".equals(field(activity, "block")), "all blocks can be restored");
    }
    @Override public void onStart() {
        Bundle report = new Bundle(); int result = Activity.RESULT_OK; boolean owned = false;
        context = getTargetContext();
        try {
            SchoolAccount account = new SchoolAccount(context);
            check((account.username(false).isEmpty() || account.username(false).startsWith("SYNTHETIC_"))
                && (account.username(true).isEmpty() || account.username(true).startsWith("SYNTHETIC_")), "synthetic device only");
            owned = true;
            owner("SYNTHETIC_DEKT_A", false);
            DektStore a = new DektStore(context); ActivityStore aa = new ActivityStore(context);
            a.save(credits()); aa.save(activities());
            owner("SYNTHETIC_DEKT_B", false);
            DektStore b = new DektStore(context); ActivityStore bb = new ActivityStore(context);
            check(b.read() == null && bb.read() == null, "accounts cannot read each other's snapshots");
            b.save(credits()); bb.save(activities());
            check(a.read() == null && aa.read() == null, "old store cannot reveal the previous account");
            try { a.save(credits()); throw new AssertionError("stale credits write accepted"); }
            catch (IllegalStateException expected) { checks++; }
            try { aa.save(activities()); throw new AssertionError("stale activity write accepted"); }
            catch (IllegalStateException expected) { checks++; }
            a.clear(); aa.clear();
            check(b.read() != null && bb.read() != null, "clearing old account leaves active account intact");
            owner("SYNTHETIC_DEKT_B", true);
            check(new DektStore(context).read() == null && new ActivityStore(context).read() == null, "student types are isolated");
            owner("SYNTHETIC_DEKT_B", false);
            launch(DektActivity.class);
            View root = activity.getWindow().getDecorView();
            check(text(root, "2.40") != null && text(root, "SYNTHETIC_CATEGORY") != null, "credit total and category render");
            check(text(root, "SYNTHETIC_CHILD") != null, "credit children start expanded");
            ui(() -> ((View) text(activity.getWindow().getDecorView(), "SYNTHETIC_CATEGORY").getParent()).performClick());
            check(text(root, "SYNTHETIC_CHILD") == null, "credit category collapses");
            check((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0, "credit screenshot protection retained");
            close();
            launch(ActivitiesActivity.class);
            root = activity.getWindow().getDecorView();
            check(text(root, "SYNTHETIC_ACTIVITY_A") != null && text(root, "SYNTHETIC_ACTIVITY_B") != null, "cached activities render");
            filterInteractions();
            ui(() -> ((EditText) field(activity, "search")).setText("SYNTHETIC_ACTIVITY_B"));
            check(text(root, "SYNTHETIC_ACTIVITY_A") == null && text(root, "SYNTHETIC_ACTIVITY_B") != null, "search filters cached activities");
            ui(() -> ((EditText) field(activity, "search")).setText(""));
            check(!((TextView) field(activity, "status")).getText().toString().contains("显示 1 个"), "clearing search restores total count");
            ui(() -> {
                JSONObject detail = new JSONObject().put("id", "SYNTHETIC_B").put("name", "SYNTHETIC_DETAIL")
                    .put("end", "2099-11-01 16:00:00").put("signed", true);
                Method m = ActivitiesActivity.class.getDeclaredMethod("onDetail", JSONObject.class); m.setAccessible(true);
                m.invoke(activity, new JSONObject().put("detail", detail));
            });
            View signed = text(root, "已报名");
            check(signed != null, "detail shows school signup state");
            check(!button(root, "已报名").isEnabled(), "signed activity cannot be submitted again");
            check((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0, "activity screenshot protection retained");
            ui(() -> {
                set(activity, "detail", new JSONObject().put("id", "SYNTHETIC_B").put("name", "SYNTHETIC_DETAIL").put("end", "2000-01-01 00:00:00"));
                call(activity, "renderDetail");
            });
            check(!text(root, "报名已结束").isEnabled(), "ended activity cannot be submitted");
            ui(() -> {
                set(activity, "detail", new JSONObject().put("id", "SYNTHETIC_B").put("name", "SYNTHETIC_DETAIL")
                    .put("end", "2099-11-01 16:00:00"));
                set(activity, "importing", true);
                set(activity, "plan", "{\"action\":\"signup\",\"id\":\"SYNTHETIC_B\",\"requestId\":\"SYNTHETIC_REQUEST\"}");
                set(activity, "webStatus", new TextView(activity));
                WebView web = new WebView(activity); web.getSettings().setJavaScriptEnabled(true);
                set(activity, "web", web); activity.setContentView(web);
                String html = "<html><body><script>window.posts=0;window.XMLHttpRequest=function(){"
                    + "this.open=function(method,url){this.url=url;};this.setRequestHeader=function(){};"
                    + "this.send=function(){this.status=200;if(this.url.indexOf('/default.zf')>=0)"
                    + "this.responseText=JSON.stringify({code:0,data:{groupFields:[{fields:[{fieldCode:'hdid',defaultValue:'SYNTHETIC_B'}]}]}});"
                    + "else{window.posts++;this.responseText=JSON.stringify({code:0,msg:'SYNTHETIC_SUCCESS'});}};};</script></body></html>";
                web.loadDataWithBaseURL("https://xg.sit.edu.cn/hdgl/hdydlist", html, "text/html", "UTF-8", "https://xg.sit.edu.cn/hdgl/hdydlist");
            });
            SystemClock.sleep(600);
            ui(() -> { call(activity, "extractNow"); call(activity, "extractNow"); });
            SystemClock.sleep(1400); waitForIdleSync();
            java.util.concurrent.CountDownLatch loaded = new java.util.concurrent.CountDownLatch(1);
            final String[] posts = {""};
            ui(() -> ((WebView) field(activity, "web")).evaluateJavascript("JSON.stringify({posts:window.posts,state:window.__xinxinActs})", value -> { posts[0] = value; loaded.countDown(); }));
            check(loaded.await(5, java.util.concurrent.TimeUnit.SECONDS) && posts[0].contains("\\\"posts\\\":1"),
                "double activation sends one synthetic signup POST: " + posts[0] + " / " + ((TextView) field(activity, "webStatus")).getText());
            check(((JSONObject) field(activity, "detail")).optBoolean("signed"), "WebView result updates signup state");
            close();
            AppSession.invalidate(context);
            check(b.read() == null && bb.read() == null, "logout prevents cached reads");
            report.putString("result", "PASS " + checks + " checks");
        } catch (Throwable e) {
            report.putString("result", "FAIL " + checks + " checks: " + e.toString());
            java.io.StringWriter trace = new java.io.StringWriter(); e.printStackTrace(new java.io.PrintWriter(trace));
            report.putString("trace", trace.toString()); result = Activity.RESULT_CANCELED;
        } finally {
            try { close(); } catch (Exception ignored) { }
            if (owned) {
                context.getSharedPreferences("dekt_v1", 0).edit().clear().commit();
                context.getSharedPreferences("activities_v1", 0).edit().clear().commit();
                new SchoolAccount(context).clear();
            }
        }
        if (result == Activity.RESULT_OK && keepFixture) {
            try {
                owner("SYNTHETIC_UI", false);
                new DektStore(context).save(credits()); new ActivityStore(context).save(activities());
                context.getSharedPreferences("app_update", 0).edit().putLong("last_check", System.currentTimeMillis()).commit();
                launch(MainActivity.class); ui(() -> ((MainActivity) activity).selectTab(1));
                report.putString("result", "PASS " + checks + " checks; synthetic preview retained");
            } catch (Exception e) { report.putString("result", "FAIL preview fixture"); result = Activity.RESULT_CANCELED; }
        }
        finish(result, report);
    }
}
