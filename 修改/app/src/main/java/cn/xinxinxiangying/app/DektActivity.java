package cn.xinxinxiangying.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 第二课堂学分：本机快照的查看页；读取时临时用内嵌页面登录学校系统。 */
public final class DektActivity extends Activity {
    private static final int ORANGE = 0xffd0873f;
    /** 记录页的诊断用脚本：记下页面自己发出的 XHR/Fetch，便于定位翻页接口。只在记录页注入。 */
    private static final String CAPTURE_JS =
        "(function(){if(window.__xinxinCapture)return;window.__xinxinCapture=[];"
        + "function push(u,s,b){try{if(window.__xinxinCapture.length<40)"
        + "window.__xinxinCapture.push({u:String(u).slice(0,300),s:s,b:String(b==null?'':b).slice(0,4000)});}catch(e){}}"
        + "var O=XMLHttpRequest.prototype.open,S=XMLHttpRequest.prototype.send;"
        + "XMLHttpRequest.prototype.open=function(m,u){this.__xinxinUrl=u;return O.apply(this,arguments);};"
        + "XMLHttpRequest.prototype.send=function(){var x=this;x.addEventListener('load',function(){"
        + "push(x.__xinxinUrl,x.status,x.responseText);});return S.apply(this,arguments);};"
        + "if(window.fetch){var F=window.fetch;window.fetch=function(){return F.apply(this,arguments).then(function(r){"
        + "try{r.clone().text().then(function(t){push(r.url,r.status,t);});}catch(e){}return r;});};}"
        + "})();";
    private final Set<String> open = new HashSet<>();
    private final Set<String> openRecords = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DektStore store;
    private DektRecordStore recordStore;
    private JSONObject records;
    private LinearLayout page, content, webPage;
    private TextView status, webStatus;
    private WebView web;
    private String script;
    private String recordsScript;
    private String enterScript;
    private String loginScript;
    private String pendingTarget;
    private boolean loginSubmitted;
    private boolean recordsTarget;
    private String year = "";
    private boolean pg;
    private boolean autoSync;
    private boolean importing, auto;
    private boolean reading;
    private int generation;
    private int polls;
    private int autoTries;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CampusUi.window(this);
        store = new DektStore(this);
        recordStore = new DektRecordStore(this);
        pg = getSharedPreferences("school_profile", 0).getBoolean("postgraduate", false);
        autoSync = getIntent().getBooleanExtra("autoSync", false);
        if (!AppSession.require(this, "second")) return;
        script = asset("dekt-extract.js");
        recordsScript = asset("dekt-records.js");
        enterScript = asset("dekt-enter.js");
        loginScript = asset("school-login.js");
        page = CampusUi.column(this);
        page.setBackgroundColor(CampusUi.BG);
        CampusUi.insets(page);
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(Color.WHITE);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout back = new LinearLayout(this);
        back.setGravity(Gravity.CENTER);
        back.setBackground(CampusUi.ripple(this, Color.WHITE, 6));
        back.addView(new CampusUi.Icon(this, "back", CampusUi.GREEN), new LinearLayout.LayoutParams(dp(22), dp(22)));
        back.setContentDescription("返回");
        back.setFocusable(true);
        back.setOnClickListener(v -> leave());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = CampusUi.text(this, SchoolCatalog.title("second"), 18, CampusUi.INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(12), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = CampusUi.button(this, "刷新", true, v -> importPage());
        bar.addView(refresh, new LinearLayout.LayoutParams(-2, dp(44)));
        LinearLayout more = new LinearLayout(this);
        more.setGravity(Gravity.CENTER);
        more.setBackground(CampusUi.ripple(this, Color.WHITE, 6));
        more.addView(new CampusUi.Icon(this, "more", CampusUi.GREEN), new LinearLayout.LayoutParams(dp(22), dp(22)));
        more.setContentDescription("更多");
        more.setFocusable(true);
        more.setOnClickListener(v -> menu());
        bar.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));
        page.addView(bar);
        status = CampusUi.text(this, "", 11, CampusUi.MUTED);
        status.setPadding(dp(20), dp(2), dp(20), dp(8));
        page.addView(status);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = CampusUi.column(this);
        content.setPadding(dp(14), 0, dp(14), dp(20));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        render();
        if (autoSync) handler.postDelayed(this::importPage, 300);
    }

    @Override protected void onResume() {
        super.onResume();
        if (page != null && (!AppSession.require(this, "second") || !store.isCurrent())) {
            if (autoSync) finishToApp(null); else finish();
            return;
        }
        if (!importing && page != null && AppSession.valid(this)) render();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private int dp(int value) { return CampusUi.dp(this, value); }

    /** 导入页可能独占一个任务，返回时要回到主界面而不是直接退出应用。 */
    private void leave() {
        if (isTaskRoot()) startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override public void onBackPressed() {
        if (importing) { quitWeb(); return; }
        if (autoSync) { finishToApp(null); return; }
        leave();
    }

    private void render() {
        content.removeAllViews();
        records = recordStore.read();
        JSONObject saved = store.read();
        if (saved == null) { empty(); return; }
        status.setText("本机保存 · " + when(saved.optLong("importedAt", 0)));
        hero(saved);
        JSONArray categories = saved.optJSONArray("categories");
        if (categories != null && categories.length() > 0) {
            yearRow();
            heading("学分明细");
            detail(withPlaceholders(categories));
        }
        browseRow();
        recordsEntry();
        footnote("数据来自学校第二课堂系统，以学校页面为准。");
    }

    /** 顶部总学分卡：与学校页面一致，显示「累计获得 已获 / 需求」。 */
    private void hero(JSONObject saved) {
        LinearLayout card = CampusUi.column(this);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(CampusUi.shape(this, Color.WHITE, 14));
        card.addView(CampusUi.text(this, "累计获得", 13, CampusUi.MUTED));
        String earned = saved.optString("earned", ""), required = saved.optString("required", "");
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.BOTTOM);
        TextView value = CampusUi.text(this, earned.isEmpty() ? "—" : earned, 34, CampusUi.INK);
        value.setTypeface(null, Typeface.BOLD);
        line.addView(value);
        TextView need = CampusUi.text(this, required.isEmpty() ? " 学分" : " / " + required + " 学分", 16, CampusUi.MUTED);
        need.setPadding(dp(2), 0, 0, dp(5));
        line.addView(need);
        card.addView(line);
        String updated = saved.optString("updated", "");
        if (!updated.isEmpty()) {
            TextView sub = CampusUi.text(this, "更新于 " + updated, 11, CampusUi.MUTED);
            sub.setPadding(0, dp(8), 0, 0);
            card.addView(sub);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(18);
        content.addView(card, lp);
    }

    /** 学年筛选：样式对齐学校页面的胶囊按钮；年份取「今年 ±1」和记录里实际出现的年份。 */
    private void yearRow() {
        java.util.TreeSet<String> years = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        String current = academicYear();
        int start = Integer.parseInt(current.substring(0, 4));
        for (int offset = 1; offset >= -1; offset--) {
            int y = start + offset;
            years.add(y + "-" + (y + 1));
        }
        JSONArray all = records == null ? null : records.optJSONArray("records");
        if (all != null) for (int i = 0; i < all.length(); i++) {
            JSONObject record = all.optJSONObject(i);
            if (record == null) continue;
            String value = record.optString("year", "");
            if (!value.isEmpty()) years.add(value);
        }
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = CampusUi.text(this, "学年", 11, CampusUi.MUTED);
        label.setPadding(0, 0, dp(10), 0);
        line.addView(label);
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.addView(chip("全部", year.isEmpty(), v -> { year = ""; render(); }));
        for (String value : years) {
            chips.addView(chip(value, value.equals(year), v -> { year = value; render(); }));
        }
        scroll.addView(chips);
        line.addView(scroll, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        content.addView(line, lp);
    }

    /** 学年以 9 月为界：2026 年 10 月属于 2026-2027 学年。 */
    private static String academicYear() {
        java.util.Calendar calendar = java.util.Calendar.getInstance(
            java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        int month = calendar.get(java.util.Calendar.MONTH) + 1;
        int year = calendar.get(java.util.Calendar.YEAR);
        int start = month >= 9 ? year : year - 1;
        return start + "-" + (start + 1);
    }

    private View chip(String text, boolean on, View.OnClickListener click) {
        TextView view = CampusUi.text(this, text, 13, on ? 0xff2f7fd8 : 0xff6b7280);
        view.setPadding(dp(14), dp(7), dp(14), dp(7));
        view.setBackground(CampusUi.shape(this, on ? 0xffe3f0ff : 0xfff2f4f7, 18));
        view.setFocusable(true);
        view.setContentDescription(text);
        view.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        view.setLayoutParams(lp);
        return view;
    }

    private void heading(String text) {
        TextView view = CampusUi.text(this, text, 13, CampusUi.MUTED);
        view.setPadding(dp(4), 0, 0, dp(8));
        content.addView(view);
    }

    /** 学分明细页没列出的大类/类别，按加分记录补出来，保证每条记录都能看到。 */
    private JSONArray withPlaceholders(JSONArray categories) {
        java.util.List<JSONObject> cards = new java.util.ArrayList<>();
        if (categories != null) for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category != null) cards.add(category);
        }
        JSONArray all = records == null ? null : records.optJSONArray("records");
        if (all != null) for (int i = 0; i < all.length(); i++) {
            JSONObject record = all.optJSONObject(i);
            if (record == null) continue;
            String dl = record.optString("dl", ""), lb = record.optString("lb", "");
            if (dl.isEmpty() || lb.isEmpty()) continue;
            JSONObject card = null;
            for (JSONObject one : cards) {
                String name = one.optString("name", "");
                if (name.equals(dl) || name.contains(dl) || dl.contains(name)) { card = one; break; }
            }
            if (card == null) {
                try {
                    card = new JSONObject().put("name", dl).put("earned", "").put("required", "")
                        .put("children", new JSONArray()).put("computed", true);
                    cards.add(card);
                } catch (Exception ignored) { continue; }
            }
            JSONArray children = card.optJSONArray("children");
            if (children == null) {
                children = new JSONArray();
                try { card.put("children", children); } catch (Exception ignored) { }
            }
            boolean found = false;
            for (int k = 0; k < children.length(); k++) {
                JSONObject child = children.optJSONObject(k);
                if (child == null) continue;
                String childName = child.optString("name", "");
                if (lb.equals(childName)) { found = true; break; }
                for (String part : childName.split("[、,，/／]")) {
                    if (lb.equals(part.trim())) { found = true; break; }
                }
                if (found) break;
            }
            if (found) continue;
            double sum = 0;
            for (int j = 0; j < all.length(); j++) {
                JSONObject one = all.optJSONObject(j);
                if (one == null || !lb.equals(one.optString("lb", "")) || !dl.equals(one.optString("dl", ""))) continue;
                try { sum += Double.parseDouble(one.optString("credit", "0")); } catch (Exception ignored) { }
            }
            try {
                children.put(new JSONObject().put("name", lb).put("earned", DektParser.fixed(sum))
                    .put("required", "").put("computed", true));
                card.put("computed", true);
            } catch (Exception ignored) { }
        }
        for (String known : new String[]{"德育", "劳育", "美育", "健康教育"}) {
            boolean found = false;
            for (JSONObject card : cards) if (card.optString("name", "").contains(known)) { found = true; break; }
            if (found) continue;
            try {
                cards.add(new JSONObject().put("name", known).put("earned", "").put("required", "")
                    .put("children", new JSONArray()));
            } catch (Exception ignored) { }
        }
        JSONArray out = new JSONArray();
        for (JSONObject card : cards) out.put(card);
        return out;
    }

    /** 分类独立成卡，默认展开；需求数值直接沿用学校快照。 */
    private void detail(JSONArray categories) {
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category == null) continue;
            String name = category.optString("name", "");
            String earned = category.optString("earned", ""), required = category.optString("required", "");
            boolean met = reached(earned, required), expanded = !open.contains(name);
            LinearLayout card = CampusUi.column(this);
            android.graphics.drawable.GradientDrawable shape = CampusUi.shape(this, Color.WHITE, 16);
            shape.setStroke(dp(1), 0xffececec);
            card.setBackground(shape);
            card.setPadding(dp(16), dp(10), dp(16), dp(10));
            JSONArray children = category.optJSONArray("children");
            int count = children == null ? 0 : children.length();
            View head = row(name, earned, required, count == 0 ? "" : expanded ? "⌃" : "⌄", true, tint(name));
            if (count > 0) {
                head.setFocusable(true);
                head.setContentDescription(name + (expanded ? " 收起" : " 展开"));
                head.setOnClickListener(v -> { if (!open.remove(name)) open.add(name); render(); });
            }
            card.addView(head);
            if (expanded) for (int k = 0; k < count; k++) {
                JSONObject child = children.optJSONObject(k);
                if (child != null) card.addView(childRow(name, child));
            }
            if (category.optBoolean("computed", false)) {
                TextView note = CampusUi.text(this, "学校明细页未列出 · 按加分记录推算", 11, CampusUi.MUTED);
                note.setPadding(dp(14), 0, 0, dp(10));
                card.addView(note);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(16);
            content.addView(card, lp);
        }
    }

    /** 小项行：点一下展开这一类已认定的加分记录；记录没读过时给出读取入口。 */
    private View childRow(String category, JSONObject child) {
        final String label = child.optString("name", "");
        final String key = category + "|" + label;
        final boolean expanded = openRecords.contains(key);
        JSONArray list = recordsFor(category, label);
        LinearLayout box = CampusUi.column(this);
        View head = row(label, child.optString("earned", ""), child.optString("required", ""), expanded ? "⌃" : "⌄", false);
        head.setFocusable(true);
        head.setContentDescription(label + (expanded ? " 收起加分记录" : " 展开加分记录"));
        head.setBackground(CampusUi.ripple(this, Color.WHITE, 0));
        head.setOnClickListener(v -> { if (!openRecords.remove(key)) openRecords.add(key); render(); });
        box.addView(head, new LinearLayout.LayoutParams(-1, -2));
        if (!expanded) return box;
        if (list.length() == 0) {
            String message;
            if (records == null) message = "还没有读取加分记录，点下方「加分记录」读取";
            else if (year.isEmpty()) message = "这一类暂时没有对应的加分记录";
            else message = year + " 学年这一类没有记录";
            TextView hint = CampusUi.text(this, message, 12, CampusUi.MUTED);
            hint.setPadding(dp(14), dp(4), 0, dp(12));
            box.addView(hint);
            return box;
        }
        LinearLayout panel = CampusUi.column(this);
        panel.setBackground(CampusUi.shape(this, 0xfffafbfb, 10));
        panel.setPadding(0, dp(4), 0, dp(4));
        double sum = 0;
        int added = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject record = list.optJSONObject(i);
            if (record == null) continue;
            if (added++ > 0) panel.addView(thinLine());
            panel.addView(recordRow(record));
            try { sum += Double.parseDouble(record.optString("credit", "0")); } catch (Exception ignored) { }
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(2);
        box.addView(panel, lp);
        note(box, "共 " + list.length() + " 条 · 合计 " + DektParser.fixed(sum) + " · 以学校页面为准");
        return box;
    }

    private View thinLine() {
        View line = new View(this);
        line.setBackgroundColor(0xffeef1f1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.leftMargin = dp(14);
        lp.rightMargin = dp(14);
        line.setLayoutParams(lp);
        return line;
    }

    /** 一条加分记录：日期｜活动名｜+0.10。 */
    private View recordRow(JSONObject record) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(12), dp(9), 0, dp(9));
        String time = record.optString("time", "");
        String day = time.length() >= 10 ? time.substring(5, 10) : "—";
        line.addView(CampusUi.text(this, day, 11, CampusUi.MUTED), new LinearLayout.LayoutParams(dp(46), -2));
        TextView name = CampusUi.text(this, record.optString("activity", ""), 13, CampusUi.INK);
        name.setMaxLines(2);
        line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        String credit = record.optString("credit", "");
        TextView value = CampusUi.text(this, credit.startsWith("-") ? credit : "+" + credit, 13, CampusUi.GREEN);
        value.setTypeface(null, Typeface.BOLD);
        value.setPadding(dp(8), 0, 0, 0);
        line.addView(value);
        return line;
    }

    /** 小项名可能是「讲座报告、主题教育」这种合并写法，按分隔符拆开逐个匹配学校的学分类别。 */
    private JSONArray recordsFor(String category, String label) {
        JSONArray out = new JSONArray();
        JSONArray all = records == null ? null : records.optJSONArray("records");
        if (all == null) return out;
        String[] parts = label.split("[、,，/／]");
        for (int i = 0; i < all.length(); i++) {
            JSONObject record = all.optJSONObject(i);
            if (record == null) continue;
            String dl = record.optString("dl", ""), lb = record.optString("lb", "");
            if (!year.isEmpty() && !year.equals(record.optString("year", ""))) continue;
            // 大类名可能不完全一致（「美育」vs「美育实践」），互相包含也算同一类。
            if (!dl.isEmpty() && !dl.equals(category) && !dl.contains(category) && !category.contains(dl)) continue;
            for (String part : parts) {
                String one = part.trim();
                if (!one.isEmpty() && one.equals(lb)) { out.put(record); break; }
            }
        }
        return out;
    }

    private void note(LinearLayout box, String text) {
        TextView view = CampusUi.text(this, text, 11, CampusUi.MUTED);
        view.setPadding(dp(12), dp(2), 0, dp(10));
        box.addView(view);
    }

    private View row(String name, String earned, String required, String chevron, boolean top) {
        return row(name, earned, required, chevron, top, CampusUi.GREEN);
    }

    /** 大类卡的标题行：左侧色条按大类区分，数值右侧对齐。 */
    private View row(String name, String earned, String required, String chevron, boolean top, int accent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(top ? 13 : 12), 0, dp(top ? 13 : 12));
        if (top) {
            View marker = new View(this); marker.setBackground(CampusUi.shape(this, accent, 2));
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(4), dp(22)); mp.rightMargin = dp(11); row.addView(marker, mp);
        } else row.setPadding(dp(14), dp(12), 0, dp(12));
        TextView label = CampusUi.text(this, name, top ? 17 : 14, top ? CampusUi.INK : 0xff555b61);
        if (top) label.setTypeface(null, Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(values(earned, required, top));
        if (!chevron.isEmpty()) {
            TextView mark = CampusUi.text(this, chevron, 22, CampusUi.MUTED);
            mark.setPadding(dp(10), 0, 0, 0); row.addView(mark);
        }
        return row;
    }

    private LinearLayout values(String earned, String required, boolean top) {
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        if (top) { line.setBackground(CampusUi.shape(this, 0xfff2f5f4, 8)); line.setPadding(dp(10), dp(6), dp(10), dp(6)); }
        int color = earned.isEmpty() || required.isEmpty() ? CampusUi.MUTED : reached(earned, required) ? 0xff1f9d7a : 0xffd9603b;
        TextView got = CampusUi.text(this, earned.isEmpty() ? "—" : earned, top ? 17 : 15, color);
        got.setTypeface(null, Typeface.BOLD); line.addView(got);
        if (!required.isEmpty()) line.addView(CampusUi.text(this, " / " + required.replace("~", " ~ "), top ? 13 : 12, CampusUi.MUTED));
        return line;
    }

    private static int tint(String name) {
        if (name.contains("德育")) return 0xff2f9e78;
        if (name.contains("劳育")) return 0xffd0873f;
        if (name.contains("美育")) return 0xffc2559a;
        if (name.contains("健康")) return 0xff3d7fd1;
        return CampusUi.GREEN;
    }

    private static boolean reached(String earned, String required) {
        if (earned.isEmpty() || required.isEmpty()) return false;
        try {
            String need = required.indexOf('~') >= 0 ? required.substring(0, required.indexOf('~')) : required;
            return Double.parseDouble(earned) >= Double.parseDouble(need);
        } catch (NumberFormatException e) { return false; }
    }

    /** 加分记录入口：打开服务大厅的学分认定页，读取本人逐条加分记录。 */
    private void recordsEntry() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(14), dp(13));
        row.setBackground(CampusUi.shape(this, Color.WHITE, 14));
        LinearLayout texts = CampusUi.column(this);
        texts.addView(CampusUi.text(this, "加分记录", 15, CampusUi.INK));
        JSONArray all = records == null ? null : records.optJSONArray("records");
        String sub;
        if (all == null) sub = "读取每项活动在哪天加了多少分";
        else {
            String total = records.optString("total", "");
            String pages = records.optString("pages", "");
            boolean same = total.isEmpty() || total.equals(String.valueOf(all.length()));
            sub = "已读取 " + all.length() + " 条" + (pages.isEmpty() ? "" : " · " + pages + " 页")
                + (same ? "" : " · 学校页面共 " + total + " 条")
                + " · " + when(records.optLong("importedAt", 0));
        }
        TextView hint = CampusUi.text(this, sub, 11, CampusUi.MUTED);
        hint.setPadding(0, dp(3), 0, 0);
        texts.addView(hint);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(CampusUi.text(this, "›", 24, CampusUi.MUTED));
        row.setFocusable(true);
        row.setContentDescription("读取加分记录");
        row.setOnClickListener(v -> readRecords());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        content.addView(row, lp);
    }

    private void browseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(14), dp(14));
        android.graphics.drawable.GradientDrawable action = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{CampusUi.GREEN, 0xff00a48b});
        action.setCornerRadius(dp(14)); row.setBackground(action);
        TextView label = CampusUi.text(this, "查看活动", 17, Color.WHITE);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(CampusUi.text(this, "›", 26, Color.WHITE));
        row.setFocusable(true);
        row.setContentDescription("查看第二课堂活动");
        row.setOnClickListener(v -> startActivity(new Intent(this, ActivitiesActivity.class)));
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void empty() {
        status.setText("");
        LinearLayout box = CampusUi.column(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, dp(38), 0, dp(10));
        box.addView(new CampusUi.ScheduleArt(this), new LinearLayout.LayoutParams(dp(150), dp(140)));
        TextView head = CampusUi.text(this, "还没有导入第二课堂学分", 17, CampusUi.INK);
        head.setGravity(Gravity.CENTER);
        head.setPadding(0, dp(20), 0, 0);
        box.addView(head);
        TextView hint = CampusUi.text(this, "登录学校账号后，应用会在第二课堂页面里读取学分明细并保存在本机。之后打开就不必再登录。", 13, CampusUi.MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(4), 1);
        hint.setPadding(dp(18), dp(10), dp(18), dp(18));
        box.addView(hint);
        Button load = CampusUi.button(this, "从学校读取", true, v -> importPage());
        box.addView(load, new LinearLayout.LayoutParams(-2, dp(46)));
        content.addView(box);
    }

    private void footnote(String text) {
        TextView note = CampusUi.text(this, text, 11, CampusUi.MUTED);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(16), 0, dp(8));
        content.addView(note);
    }

    private static String when(long millis) {
        if (millis <= 0) return "尚未更新";
        return new SimpleDateFormat("M月d日 HH:mm 更新", Locale.CHINA).format(new Date(millis));
    }

    private void menu() {
        String[] items = {"查看学校页面", "重新读取学分明细", "读取加分记录", "清除本机缓存"};
        new AlertDialog.Builder(this).setItems(items, (dialog, which) -> {
            if (which == 0) openWeb(SchoolCatalog.DEKT_HOME, false, false);
            else if (which == 1) importPage();
            else if (which == 2) readRecords();
            else new AlertDialog.Builder(this).setTitle("清除第二课堂学分？")
                .setMessage("只清除本机保存的这份记录，学校系统里的数据不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (d, w) -> { store.clear(); recordStore.clear(); open.clear(); openRecords.clear(); render(); }).show();
        }).show();
    }

    private void importPage() { openWeb(SchoolCatalog.DEKT_HOME, true, false); }

    /** 服务大厅首页：由用户点进「学分明细查询」，再点「读取」抓当前列表（含翻页与学年）。 */
    private void readRecords() { openWeb(SchoolCatalog.DEKT_HALL, false, true); }

    private void openWeb(String url, boolean extract, boolean records) {
        if (!AppSession.require(this, "second")) { if (autoSync) finishToApp(null); return; }
        importing = true;
        generation++;
        reading = false;
        loginSubmitted = false;
        recordsTarget = records;
        autoTries = 0;
        pendingTarget = url;
        handler.removeCallbacksAndMessages(null);
        auto = extract;
        polls = 0;
        if (webPage == null) webPage = buildWeb();
        webStatus.setText("正在复用学校登录状态…");
        setContentView(webPage);
        // 先走学工系统的统一认证入口；已有 CAS 会话时直接换票，不再要求输入密码。
        web.loadUrl(SchoolCatalog.DEKT_SSO);
    }

    /** 内嵌的学校页面：只允许 sit.edu.cn，设置与其它学校页面一致。 */
    private LinearLayout buildWeb() {
        LinearLayout root = CampusUi.column(this);
        root.setBackgroundColor(CampusUi.BG);
        CampusUi.insets(root);
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(Color.WHITE);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout back = new LinearLayout(this);
        back.setGravity(Gravity.CENTER);
        back.setBackground(CampusUi.ripple(this, Color.WHITE, 6));
        back.addView(new CampusUi.Icon(this, "back", CampusUi.GREEN), new LinearLayout.LayoutParams(dp(22), dp(22)));
        back.setContentDescription("关闭学校页面");
        back.setFocusable(true);
        back.setOnClickListener(v -> quitWeb());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = CampusUi.text(this, "学校第二课堂", 15, CampusUi.INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(10), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button read = CampusUi.button(this, "读取", true, v -> extractNow());
        read.setOnLongClickListener(v -> { copyPage(); return true; });
        bar.addView(read, new LinearLayout.LayoutParams(-2, dp(44)));
        root.addView(bar);
        webStatus = CampusUi.text(this, "", 11, CampusUi.MUTED);
        webStatus.setPadding(dp(20), dp(2), dp(20), dp(8));
        root.addView(webStatus);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSaveFormData(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (web != view || !importing || isFinishing()) return;
                generation++;
                reading = false;
                handler.removeCallbacksAndMessages(null);
                if (recordsTarget) view.evaluateJavascript(CAPTURE_JS, null);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (SchoolCatalog.isSchoolUrl(url) || "about:blank".equals(url)) return false;
                Toast.makeText(DektActivity.this, "已停止打开外部页面", Toast.LENGTH_SHORT).show();
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame() && !SchoolCatalog.isSchoolUrl(request.getUrl().toString()))
                    return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", Collections.emptyMap(), null);
                return null;
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                if (recordsTarget) view.evaluateJavascript(CAPTURE_JS, null);
                if (!importing || !store.isCurrent() || isFinishing() || !SchoolCatalog.isSchoolUrl(url) || !url.equals(view.getUrl())) return;
                if (isAuthPage(url)) {
                    webStatus.setText("请登录学校账号，登录后会自动回到第二课堂");
                    autoFillAuth();
                    return;
                }
                // CAS 换票后先落在学工首页，再打开用户本来要看的学分页。
                if (pendingTarget != null) {
                    if (url.equals(pendingTarget)) pendingTarget = null;
                    else if (isXgPage(url) && SessionPolicy.isLoginUrl(url)) {
                        webStatus.setText("请登录学校账号，登录后会自动回到第二课堂");
                        return;
                    } else if (isXgPage(url)) {
                        String target = pendingTarget;
                        pendingTarget = null;
                        webStatus.setText("正在打开第二课堂页面…");
                        web.loadUrl(target);
                        return;
                    }
                }
                boolean credits = recordsTarget ? recordsPage(url) : creditsPage(url);
                if (credits) webStatus.setText(recordsTarget
                    ? "正在读取这一页的记录…"
                    : "第二课堂学分页面已打开 · 正在读取…");
                else if (recordsTarget) webStatus.setText("请点「学分明细查询」进入，打开后会自动读取");
                else if (loginPage(url)) webStatus.setText("请登录学校账号，登录后会回到第二课堂页面");
                else webStatus.setText("学校页面已打开 · 可点右上角“读取”");
                if (importing && !reading && !loginPage(url) && (recordsTarget || (auto && credits))) {
                    polls = 0;
                    handler.postDelayed(DektActivity.this::extractNow, recordsTarget ? 2500 : 800);
                }
                // 还没进到明细页时，替用户点一下服务大厅里的「学分明细查询」。
                if (recordsTarget && !credits && enterScript != null && !enterScript.isEmpty())
                    view.evaluateJavascript(enterScript, null);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame() || !currentPage(view, request.getUrl().toString())) return;
                if (autoSync) { finishToApp("连接学校失败，第二课堂未读取"); return; }
                webStatus.setText("连接学校页面失败 · 请确认在校园网内");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (!request.isForMainFrame() || !currentPage(view, request.getUrl().toString())) return;
                String current = request.getUrl().toString();
                // 统一认证入口失效时不要卡在 404：直接打开用户本来要看的学分页。
                if (response.getStatusCode() >= 400 && pendingTarget != null && !current.equals(pendingTarget)) {
                    String target = pendingTarget;
                    pendingTarget = null;
                    webStatus.setText("统一认证入口暂不可用，直接打开第二课堂…");
                    view.loadUrl(target);
                    return;
                }
                if (autoSync && response.getStatusCode() >= 400) finishToApp("第二课堂页面暂不可用，已保留上次数据");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                if (!currentPage(view, error.getUrl())) return;
                if (autoSync) { finishToApp("无法验证学校网站证书，第二课堂未读取"); return; }
                webStatus.setText("无法验证学校网站证书 · 已停止加载");
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private static boolean creditsPage(String url) {
        String path = Uri.parse(url).getPath();
        return path != null && path.contains("dektxf");
    }

    /** 学分认定页在服务大厅的 apply-manage 应用里，和学分页不同路径。 */
    private static boolean recordsPage(String url) {
        String path = Uri.parse(url).getPath();
        return path != null && path.contains("apply-manage");
    }

    private boolean currentPage(WebView view, String url) {
        return view == web && importing && !isFinishing() && !isDestroyed() && store.isCurrent()
            && url != null && url.equals(view.getUrl());
    }

    private static boolean isAuthPage(String url) {
        return SessionPolicy.isAuthHost(Uri.parse(url).getHost());
    }

    private static boolean isXgPage(String url) {
        return "xg.sit.edu.cn".equalsIgnoreCase(Uri.parse(url).getHost());
    }

    private static boolean loginPage(String url) {
        return SessionPolicy.isLoginUrl(url);
    }

    /** 记住密码时只在统一认证页提交一次；验证码交给用户，失败不反复重试。 */
    private void autoFillAuth() {
        if (loginScript == null || loginScript.isEmpty() || !store.isCurrent()) return;
        JSONObject remembered = new SchoolAccount(this).remembered(pg);
        if (remembered == null) return;
        final int token = generation;
        try {
            JSONObject credentials = new JSONObject();
            if (!loginSubmitted) credentials.put("username", remembered.optString("username"))
                .put("password", remembered.optString("password")).put("submit", true);
            loginSubmitted = true;
            web.evaluateJavascript(loginScript.replace("__CREDENTIALS__", credentials.toString()), value -> {
                if (!active(token) || !isAuthPage(web.getUrl())) return;
                String state = unquote(value);
                if ("challenge".equals(state)) webStatus.setText("请完成学校验证码，完成后会自动继续");
                else if ("rejected".equals(state)) {
                    new SchoolAccount(this).forget(pg);
                    webStatus.setText("保存的密码未通过，请在学校页面重新输入");
                } else if ("submitted".equals(state)) webStatus.setText("正在验证学校账号…");
                else if ("not-login".equals(state) || "needs-login".equals(state)) loginSubmitted = false;
            });
        } catch (Exception e) {
            loginSubmitted = false;
        }
    }

    /** 长按「读取」：把页面地址、页面发出的接口和页面文字复制到剪贴板，用于排查（不含密码）。 */
    private void copyPage() {
        if (web == null || !importing) {
            Toast.makeText(this, "请先打开学校页面再复制", Toast.LENGTH_SHORT).show();
            return;
        }
        String js = "JSON.stringify({url:location.href,xhr:window.__xinxinCapture||[],"
            + "text:(document.body?document.body.innerText:'').slice(0,6000)})";
        web.evaluateJavascript(js, value -> {
            String text = unquote(value);
            if (text.isEmpty()) {
                Toast.makeText(this, "页面没有可复制的信息", Toast.LENGTH_SHORT).show();
                return;
            }
            android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("xinxin-page", text));
            Toast.makeText(this, "已复制诊断信息（" + text.length() + " 字），粘贴发我", Toast.LENGTH_LONG).show();
        });
    }

    /** 只读到一部分时，自动把分页器结构复制到剪贴板，方便用户直接发来排查。 */
    private void copyDiagnostics(JSONObject state, int count) {
        try {
            JSONObject diag = new JSONObject();
            diag.put("url", web == null ? "" : String.valueOf(web.getUrl()));
            diag.put("total", state.optString("total", ""));
            diag.put("got", count);
            diag.put("detail", state.optString("detail", ""));
            diag.put("pager", state.optString("pager", ""));
            diag.put("tail", state.optString("tail", ""));
            android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("xinxin-diag", diag.toString()));
            Toast.makeText(this, "没读全，已把分页结构复制到剪贴板，请粘贴发我", Toast.LENGTH_LONG).show();
        } catch (Exception ignored) { }
    }

    /** 读完的提示：读到的条数和学校页面写的总条数对不上时直接说出来。 */
    private String recordToast() {
        JSONArray all = records == null ? null : records.optJSONArray("records");
        int count = all == null ? 0 : all.length();
        String total = records == null ? "" : records.optString("total", "");
        try {
            if (!total.isEmpty() && count < Integer.parseInt(total))
                return "读到 " + count + " 条，学校页面共 " + total + " 条";
        } catch (NumberFormatException ignored) { }
        return "加分记录已更新 · 共 " + count + " 条";
    }

    private void quitWeb() {
        generation++;
        reading = false;
        handler.removeCallbacksAndMessages(null);
        if (web != null) web.stopLoading();
        importing = false;
        if (autoSync) { finishToApp(null); return; }
        if (page != null) { setContentView(page); render(); }
    }

    /** 手动或在页面结束时读取当前页：脚本自己会在页面里重试，这里只等它给出结果。 */
    private void extractNow() {
        if (web == null || !importing) return;
        if (reading || !store.isCurrent()) return;
        String url = web.getUrl();
        if (url == null || !SchoolCatalog.isSchoolUrl(url)) { webStatus.setText("请先打开学校页面"); return; }
        String run = recordsTarget ? recordsScript : script;
        if (run == null || run.isEmpty()) { webStatus.setText("读取脚本缺失，请安装完整版本"); return; }
        reading = true;
        final int token = ++generation;
        polls = 0;
        handler.removeCallbacksAndMessages(null);
        webStatus.setText(recordsTarget ? "正在读取加分记录…" : "正在读取这一页…");
        web.evaluateJavascript(run, null);
        handler.postDelayed(() -> poll(token), 700);
    }

    private boolean active(int token) {
        return token == generation && importing && !isFinishing() && !isDestroyed() && store.isCurrent();
    }

    private void poll(int token) {
        if (web == null || !active(token)) return;
        String variable = recordsTarget ? "window.__xinxinRecords" : "window.__xinxinDekt";
        web.evaluateJavascript(variable + "?JSON.stringify(" + variable + "):''", value -> {
            if (web == null || !active(token)) return;
            String raw = unquote(value);
            JSONObject state = null;
            if (!raw.isEmpty()) { try { state = new JSONObject(raw); } catch (Exception e) { state = null; } }
            if (state == null) { again(token); return; }
            String status = state.optString("status", "");
            if ("complete".equals(status)) { save(state, token); return; }
            if ("failed".equals(status)) { failed(state.optString("reason", ""), state.optString("detail", "")); return; }
            String phase = state.optString("phase", "");
            if (!phase.isEmpty()) webStatus.setText("正在读取加分记录 · " + phase);
            again(token);
        });
    }

    private void again(int token) {
        polls++;
        if (polls > (recordsTarget ? 400 : 20)) { failed("empty"); return; }
        webStatus.setText("正在读取这一页…");
        handler.postDelayed(() -> poll(token), 500);
    }

    private void failed(String reason) { failed(reason, ""); }

    private void failed(String reason, String detail) {
        reading = false;
        if (recordsTarget && "empty".equals(reason) && importing && autoTries < 3) {
            autoTries++;
            webStatus.setText("还没读到记录，3 秒后自动再试…");
            handler.postDelayed(this::extractNow, 3000);
            return;
        }
        if (autoSync && !"login".equals(reason)) {
            finishToApp("第二课堂暂未读取，已保留上次数据");
            return;
        }
        if ("login".equals(reason)) webStatus.setText("学校登录状态已失效 · 请点右上角“刷新”重新验证，本机缓存不会丢失");
        else if ("blocked".equals(reason)) webStatus.setText("只在学校页面内读取");
        else if (recordsTarget) webStatus.setText("没有读到加分记录（" + (detail.isEmpty() ? "页面里没有记录" : detail) + "）· 请确认记录列表已显示");
        else webStatus.setText("没有读到学分明细 · 请展开“学分明细”后再点“读取”");
    }

    private void save(JSONObject state, int token) {
        webStatus.setText("正在整理…");
        if (!active(token)) return;
        try {
                if (recordsTarget) {
                    JSONObject merged = DektRecordParser.merge(recordStore.read(), DektRecordParser.parse(state));
                    recordStore.save(merged);
                    records = merged;
                    JSONArray got = merged.optJSONArray("records");
                    int count = got == null ? 0 : got.length();
                    String total = merged.optString("total", "");
                    try {
                        if (!total.isEmpty() && count < Integer.parseInt(total)) copyDiagnostics(state, count);
                    } catch (NumberFormatException ignored) { }
                } else {
                    store.save(DektParser.parse(state));
                    open.clear();
                }
                importing = false;
                handler.removeCallbacksAndMessages(null);
                if (autoSync) { finishToApp("第二课堂已更新"); return; }
                setContentView(page);
                render();
                Toast.makeText(this, recordsTarget ? recordToast() : "第二课堂学分已更新", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            reading = false;
            if (autoSync) finishToApp("第二课堂未读取，原有学分已保留");
            else webStatus.setText("读取失败，请重试，原有学分已保留");
        }
    }

    /** 自动同步读完（或确认读不到）后回到主界面，不把用户留在临时 WebView。 */
    private void finishToApp(String message) {
        if (isFinishing()) return;
        generation++;
        importing = reading = false;
        handler.removeCallbacksAndMessages(null);
        if (web != null) web.stopLoading();
        if (!store.isCurrent()) { finish(); return; }
        if (message != null && !message.isEmpty())
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class).putExtra("tab", 0)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private static String unquote(String value) {
        if (value == null || "null".equals(value)) return "";
        try {
            Object parsed = new org.json.JSONTokener(value).nextValue();
            return parsed instanceof String ? (String) parsed : "";
        } catch (Exception e) { return ""; }
    }

    private String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toString("UTF-8");
        } catch (Exception e) { return ""; }
    }
}
