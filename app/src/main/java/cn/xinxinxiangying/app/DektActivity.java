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
    private final Set<String> open = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DektStore store;
    private LinearLayout page, content, webPage;
    private TextView status, webStatus;
    private WebView web;
    private String script;
    private boolean importing, auto;
    private boolean reading;
    private int generation;
    private int polls;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CampusUi.window(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store = new DektStore(this);
        if (!AppSession.require(this)) return;
        script = asset("dekt-extract.js");
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
    }

    @Override protected void onResume() {
        super.onResume();
        if (page != null && (!AppSession.require(this) || !store.isCurrent())) { finish(); return; }
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
        leave();
    }

    private void render() {
        content.removeAllViews();
        JSONObject saved = store.read();
        if (saved == null) { empty(); return; }
        status.setText("本机保存 · " + when(saved.optLong("importedAt", 0)));
        hero(saved);
        JSONArray categories = saved.optJSONArray("categories");
        if (categories != null && categories.length() > 0) {
            heading("学分明细");
            detail(categories);
        }
        browseRow();
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

    private void heading(String text) {
        TextView view = CampusUi.text(this, text, 13, CampusUi.MUTED);
        view.setPadding(dp(4), 0, 0, dp(8));
        content.addView(view);
    }

    /** 学分明细：大类可展开看到子项，点击一次记住展开状态。 */
    private void detail(JSONArray categories) {
        LinearLayout card = CampusUi.column(this);
        card.setBackground(CampusUi.shape(this, Color.WHITE, 14));
        boolean first = true;
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category == null) continue;
            if (!first) {
                View line = new View(this);
                line.setBackgroundColor(CampusUi.LINE);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
                lp.leftMargin = dp(16);
                lp.rightMargin = dp(16);
                card.addView(line, lp);
            }
            first = false;
            String name = category.optString("name", "");
            JSONArray children = category.optJSONArray("children");
            int count = children == null ? 0 : children.length();
            boolean expanded = open.contains(name);
            View row = row(name, category.optString("earned", ""), category.optString("required", ""),
                count > 0 ? (expanded ? "▾" : "›") : "", true);
            if (count > 0) {
                row.setFocusable(true);
                row.setContentDescription(name + (expanded ? " 收起" : " 展开"));
                row.setOnClickListener(v -> {
                    if (!open.remove(name)) open.add(name);
                    render();
                });
            }
            card.addView(row);
            if (!expanded || count == 0) continue;
            for (int k = 0; k < count; k++) {
                JSONObject child = children.optJSONObject(k);
                if (child == null) continue;
                card.addView(row(child.optString("name", ""), child.optString("earned", ""), child.optString("required", ""), "", false));
            }
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        content.addView(card, lp);
    }

    private View row(String name, String earned, String required, String chevron, boolean top) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(top ? 16 : 32), dp(top ? 14 : 10), dp(14), dp(top ? 14 : 10));
        row.setBackground(CampusUi.ripple(this, Color.WHITE, 0));
        TextView label = CampusUi.text(this, name, top ? 15 : 13, CampusUi.INK);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(values(earned, required, top));
        if (!chevron.isEmpty()) {
            TextView mark = CampusUi.text(this, chevron, 15, CampusUi.MUTED);
            mark.setPadding(dp(8), 0, 0, 0);
            row.addView(mark);
        }
        return row;
    }

    /** 已获 / 需求；达到需求显示绿色，还差学分显示橙色（与学校页面的配色一致）。 */
    private LinearLayout values(String earned, String required, boolean top) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView got = CampusUi.text(this, earned.isEmpty() ? "—" : earned, top ? 15 : 13,
            required.isEmpty() ? CampusUi.INK : (reached(earned, required) ? CampusUi.GREEN : ORANGE));
        got.setTypeface(null, Typeface.BOLD);
        line.addView(got);
        if (!required.isEmpty()) {
            String need = required.indexOf('~') >= 0 ? required.replace("~", " ~ ") : required;
            line.addView(CampusUi.text(this, " / " + need, top ? 13 : 12, CampusUi.MUTED));
        }
        return line;
    }

    private static boolean reached(String earned, String required) {
        if (earned.isEmpty() || required.isEmpty()) return false;
        try {
            String need = required.indexOf('~') >= 0 ? required.substring(0, required.indexOf('~')) : required;
            return Double.parseDouble(earned) >= Double.parseDouble(need);
        } catch (NumberFormatException e) { return false; }
    }

    private void browseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(14), dp(14));
        row.setBackground(CampusUi.shape(this, Color.WHITE, 14));
        TextView label = CampusUi.text(this, "查看第二课堂活动", 15, CampusUi.GREEN);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(CampusUi.text(this, "›", 18, CampusUi.MUTED));
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
        String[] items = {"查看学校页面", "重新读取", "清除本机缓存"};
        new AlertDialog.Builder(this).setItems(items, (dialog, which) -> {
            if (which == 0) openWeb(SchoolCatalog.DEKT_HOME, false);
            else if (which == 1) importPage();
            else new AlertDialog.Builder(this).setTitle("清除第二课堂学分？")
                .setMessage("只清除本机保存的这份记录，学校系统里的数据不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (d, w) -> { store.clear(); open.clear(); render(); }).show();
        }).show();
    }

    private void importPage() { openWeb(SchoolCatalog.DEKT_HOME, true); }

    private void openWeb(String url, boolean extract) {
        if (!AppSession.require(this)) return;
        importing = true;
        generation++;
        reading = false;
        handler.removeCallbacksAndMessages(null);
        auto = extract;
        polls = 0;
        if (webPage == null) webPage = buildWeb();
        webStatus.setText("正在打开学校页面…");
        setContentView(webPage);
        web.loadUrl(url);
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
        Button read = CampusUi.button(this, "读取本页", true, v -> extractNow());
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
                if (!SchoolCatalog.isSchoolUrl(url)) return;
                boolean credits = creditsPage(url);
                if (credits) webStatus.setText("第二课堂学分页面已打开 · 正在读取…");
                else if (loginPage(url)) webStatus.setText("请登录学校账号，登录后会回到第二课堂页面");
                else webStatus.setText("学校页面已打开 · 可点右上角“读取本页”");
                if (auto && credits && importing && !reading) {
                    polls = 0;
                    handler.postDelayed(DektActivity.this::extractNow, 800);
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) webStatus.setText("连接学校页面失败 · 请确认在校园网内");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
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

    private static boolean loginPage(String url) {
        String host = Uri.parse(url).getHost();
        return host != null && host.equals("authserver.sit.edu.cn");
    }

    private void quitWeb() {
        generation++;
        reading = false;
        handler.removeCallbacksAndMessages(null);
        if (web != null) web.stopLoading();
        importing = false;
        if (page != null) { setContentView(page); render(); }
    }

    /** 手动或在页面结束时读取当前页：脚本自己会在页面里重试，这里只等它给出结果。 */
    private void extractNow() {
        if (web == null || !importing) return;
        if (reading || !store.isCurrent()) return;
        String url = web.getUrl();
        if (url == null || !SchoolCatalog.isSchoolUrl(url)) { webStatus.setText("请先打开学校页面"); return; }
        if (script == null || script.isEmpty()) { webStatus.setText("读取脚本缺失，请安装完整版本"); return; }
        reading = true;
        final int token = ++generation;
        polls = 0;
        handler.removeCallbacksAndMessages(null);
        webStatus.setText("正在读取这一页…");
        web.evaluateJavascript(script, null);
        handler.postDelayed(() -> poll(token), 700);
    }

    private boolean active(int token) {
        return token == generation && importing && !isFinishing() && !isDestroyed() && store.isCurrent();
    }

    private void poll(int token) {
        if (web == null || !active(token)) return;
        web.evaluateJavascript("window.__xinxinDekt?JSON.stringify(window.__xinxinDekt):''", value -> {
            if (web == null || !active(token)) return;
            String raw = unquote(value);
            JSONObject state = null;
            if (!raw.isEmpty()) { try { state = new JSONObject(raw); } catch (Exception e) { state = null; } }
            if (state == null) { again(token); return; }
            String status = state.optString("status", "");
            if ("complete".equals(status)) { save(state, token); return; }
            if ("failed".equals(status)) { failed(state.optString("reason", "")); return; }
            again(token);
        });
    }

    private void again(int token) {
        polls++;
        if (polls > 20) { failed("empty"); return; }
        webStatus.setText("正在读取这一页…");
        handler.postDelayed(() -> poll(token), 500);
    }

    private void failed(String reason) {
        reading = false;
        if ("login".equals(reason)) webStatus.setText("请先在上面登录学校账号，登录后再点“读取本页”");
        else if ("blocked".equals(reason)) webStatus.setText("只在学校页面内读取");
        else webStatus.setText("没有读到学分明细 · 请展开“学分明细”后再点“读取本页”");
    }

    private void save(JSONObject state, int token) {
        webStatus.setText("正在整理…");
        if (!active(token)) return;
        try {
                store.save(DektParser.parse(state));
                open.clear();
                importing = false;
                handler.removeCallbacksAndMessages(null);
                setContentView(page);
                render();
                Toast.makeText(this, "第二课堂学分已更新", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { reading = false; webStatus.setText("读取失败，请重试，原有学分已保留"); }
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
