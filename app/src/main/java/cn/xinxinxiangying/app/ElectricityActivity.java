package cn.xinxinxiangying.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/** Native electricity overview backed by the school's existing read-only room selectors. */
public final class ElectricityActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ElectricityStore store;
    private LinearLayout home, school;
    private TextView amount, balanceTitle, updated, bedroom, webHint, webTitle;
    private Button updateButton, rechargeButton, saveButton;
    private WebView web;
    private String script, loginScript, operation = "", error = "";
    private JSONObject expectedRoom;
    private boolean choosing, refreshing, showingSchool, attemptedLogin;
    private int generation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); CampusUi.window(this);
        if (!AppSession.require(this, "electricity")) return;
        store = new ElectricityStore(this);
        script = asset("electricity-page.js"); loginScript = asset("school-login.js");
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        school = buildSchool(); root.addView(school, new android.widget.FrameLayout.LayoutParams(-1, -1));
        home = buildHome(); root.addView(home, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root); render();
    }
    private int dp(int value) { return CampusUi.dp(this, value); }
    private LinearLayout header(String title, View.OnClickListener back) {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(16), dp(8)); bar.setBackgroundColor(Color.WHITE);
        LinearLayout arrow = new LinearLayout(this); arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(CampusUi.ripple(this, Color.WHITE, 10)); arrow.setContentDescription("返回");
        arrow.addView(new CampusUi.Icon(this, "back", CampusUi.GREEN), new LinearLayout.LayoutParams(dp(22), dp(22)));
        arrow.setOnClickListener(back); bar.addView(arrow, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView label = CampusUi.text(this, title, 18, CampusUi.INK); label.setTypeface(null, Typeface.BOLD);
        label.setPadding(dp(12), 0, 0, 0); bar.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        return bar;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = CampusUi.column(this); card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(CampusUi.shape(this, Color.WHITE, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(dp(16), dp(16), dp(16), 0);
        parent.addView(card, lp); return card;
    }
    private LinearLayout buildHome() {
        LinearLayout page = CampusUi.column(this); page.setBackgroundColor(CampusUi.BG); CampusUi.insets(page);
        page.addView(header("宿舍电费", v -> leave()));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = CampusUi.column(this); content.setPadding(0, 0, 0, dp(24)); scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout balance = card(content), top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(new CampusUi.Icon(this, "electricity", CampusUi.GREEN), new LinearLayout.LayoutParams(dp(24), dp(24)));
        balanceTitle = CampusUi.text(this, "寝室剩余电费", 16, CampusUi.INK); balanceTitle.setTypeface(null, Typeface.BOLD);
        balanceTitle.setPadding(dp(10), 0, dp(6), 0); top.addView(balanceTitle, new LinearLayout.LayoutParams(0, -2, 1));
        updateButton = CampusUi.button(this, "更新", false, v -> refresh());
        top.addView(updateButton, new LinearLayout.LayoutParams(-2, dp(44))); balance.addView(top);
        amount = CampusUi.text(this, "—", 40, CampusUi.GREEN); amount.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        amount.setPadding(0, dp(20), 0, dp(6)); amount.setIncludeFontPadding(false); balance.addView(amount);
        updated = CampusUi.text(this, "选择寝室后查看余额", 12, CampusUi.MUTED);
        updated.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); updated.setLineSpacing(dp(4), 1); balance.addView(updated);
        View divider = new View(this); divider.setBackgroundColor(CampusUi.LINE);
        LinearLayout.LayoutParams separator = new LinearLayout.LayoutParams(-1, dp(1)); separator.setMargins(0, dp(22), 0, dp(18)); balance.addView(divider, separator);
        LinearLayout room = new LinearLayout(this); room.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = CampusUi.column(this); TextView label = CampusUi.text(this, "宿舍编号", 14, CampusUi.INK);
        label.setTypeface(null, Typeface.BOLD); labels.addView(label);
        bedroom = CampusUi.text(this, "尚未选择寝室", 13, CampusUi.MUTED); bedroom.setPadding(0, dp(7), dp(10), 0);
        bedroom.setLineSpacing(dp(3), 1); labels.addView(bedroom); room.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Button change = CampusUi.button(this, "选择宿舍", false, v -> chooseRoom()); change.setTag("change-room");
        room.addView(change, new LinearLayout.LayoutParams(-2, dp(44))); balance.addView(room);
        LinearLayout notes = card(content);
        notes.addView(note("充值在学校官方的电费缴纳页面完成，本应用只负责打开页面并读取余额，不参与任何支付环节。"));
        notes.addView(note("缴费成功后，学校系统需要后台对账与数据同步，通常要等几分钟余额才会更新；可稍后点上方“更新”查看。"));
        notes.addView(note("温馨提示：如对充值过程和结果有疑问，请直接咨询学校的后勤或一卡通中心。"));
        LinearLayout footer = CampusUi.column(this); footer.setPadding(dp(16), dp(12), dp(16), dp(16));
        rechargeButton = CampusUi.button(this, "充值电费", true, v -> recharge()); footer.addView(rechargeButton, new LinearLayout.LayoutParams(-1, dp(52)));
        page.addView(footer); return page;
    }
    private TextView note(String value) {
        TextView line = CampusUi.text(this, value, 13, CampusUi.MUTED); line.setLineSpacing(dp(6), 1);
        line.setPadding(0, 0, 0, dp(10)); return line;
    }
    private LinearLayout buildSchool() {
        LinearLayout page = CampusUi.column(this); page.setBackgroundColor(CampusUi.BG); CampusUi.insets(page);
        LinearLayout bar = header("选择寝室", v -> cancelSchool()); webTitle = (TextView) bar.getChildAt(1); page.addView(bar);
        webHint = CampusUi.text(this, "校区和区域会自动填写，请选择楼栋、楼层和房间。", 12, CampusUi.MUTED);
        webHint.setPadding(dp(20), dp(10), dp(20), dp(12)); page.addView(webHint);
        web = new WebView(this); WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setSaveFormData(false);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (android.os.Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        page.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footer = CampusUi.column(this); footer.setPadding(dp(16), dp(12), dp(16), dp(16));
        saveButton = CampusUi.button(this, "保存寝室", false, v -> saveRoom()); footer.addView(saveButton, new LinearLayout.LayoutParams(-1, dp(52))); page.addView(footer);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !SchoolCatalog.isSchoolUrl(request.getUrl().toString());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame() && !SchoolCatalog.isSchoolUrl(request.getUrl().toString()))
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", Collections.emptyMap(), new java.io.ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (view != web) return; generation++; handler.removeCallbacksAndMessages(null);
                final int token = generation;
                handler.postDelayed(() -> { if (active(token) && refreshing && !showingSchool) failed("读取学校电费超时，已保留上次余额"); }, 65000);
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (view != web || isFinishing() || !store.isCurrent() || !url.equals(view.getUrl()) || operation.isEmpty()) return;
                CookieManager.getInstance().flush();
                if (SessionPolicy.isAuthHost(Uri.parse(url).getHost())) { inspectAuth(); return; }
                if (url.startsWith("https://ecard.sit.edu.cn/") && Uri.parse(url).getPath().contains("login")) { showSchool(true); return; }
                if (!isElectricityPage(url)) { failed("学校电费页面尚未就绪，请返回后重试"); return; }
                final int token = generation;
                if (choosing) {
                    showSchool(false);
                    JSONObject saved = store.read(), room = saved == null ? null : saved.optJSONObject("room");
                    // The adapter fills 电控校区/缴费校区/缴费区域 itself and reports a finished room,
                    // which is saved without another tap on 保存寝室.
                    run(plan("choose", room), token, result -> {
                        String status = result.optString("status");
                        if ("complete".equals(status)) { commitRoom(result); return; }
                        if ("failed".equals(status)) { selectionFailed(result); return; }
                        saveButton.setEnabled(true);
                        poll(token, 0, true);
                    });
                } else {
                    showHome(); run(plan("balance", expectedRoom), token, result -> arrived(token, result));
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && view == web && request.getUrl().toString().equals(view.getUrl())) failed("连接学校失败，已保留上次余额");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && view == web && request.getUrl().toString().equals(view.getUrl())) failed("学校电费页面暂不可用，请稍后重试");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler callback, android.net.http.SslError error) {
                callback.cancel(); if (view == web) failed("无法验证学校网站证书，请稍后重试");
            }
        });
        return page;
    }
    private static JSONObject plan(String action, JSONObject room) {
        try { JSONObject value = new JSONObject().put("action", action); if (room != null) value.put("room", room); return value; }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private interface Result { void accept(JSONObject value); }
    private void run(JSONObject plan, int token, Result callback) {
        if (script.isEmpty()) { failed("电费读取组件缺失，请重新安装"); return; }
        web.evaluateJavascript(script.replace("__ELECTRICITY_PLAN__", plan.toString()), value -> {
            if (!active(token)) return; callback.accept(decode(value));
        });
    }
    private JSONObject decode(String value) {
        try { Object text = new JSONTokener(value).nextValue(); return new JSONObject(text instanceof String ? (String) text : "{}"); }
        catch (Exception ignored) { return new JSONObject(); }
    }
    private boolean active(int token) { return token == generation && !isFinishing() && !isDestroyed() && store.isCurrent() && !operation.isEmpty(); }
    private static boolean isElectricityPage(String url) {
        if (!SchoolCatalog.isSchoolUrl(url)) return false;
        Uri uri = Uri.parse(url); String host = uri.getHost(), path = uri.getPath();
        return host != null && "ecard.sit.edu.cn".equalsIgnoreCase(host) && path != null && path.startsWith("/epay/");
    }
    /** Runs after the plan; the school answers on a second page, so pending keeps polling. */
    private void arrived(int token, JSONObject result) {
        if (!active(token)) return;
        String status = result.optString("status");
        if ("complete".equals(status) || "failed".equals(status)) settled(token, result);
        else poll(token, 0, false);
    }
    private void poll(int token, int attempt, boolean selection) {
        if (!active(token)) return;
        if (attempt > (selection ? 600 : 115)) {
            if (selection) { saveButton.setEnabled(true); webHint.setText("请重新选择寝室，完成后保存。"); }
            else failed("读取超时，请稍后点“更新”重试");
            return;
        }
        web.evaluateJavascript("window.__xinxinElectricityPage?JSON.stringify(window.__xinxinElectricityPage):''", value -> {
            if (!active(token)) return; JSONObject result = decode(value); String status = result.optString("status");
            if ("complete".equals(status)) { if (selection) commitRoom(result); else settled(token, result); }
            else if ("failed".equals(status)) { if (selection) selectionFailed(result); else settled(token, result); }
            else handler.postDelayed(() -> poll(token, attempt + 1, selection), selection ? 700 : 500);
        });
    }
    /** Stores a room completed on the school page; the caller may be the adapter or 保存寝室. */
    private void commitRoom(JSONObject result) {
        try {
            store.saveRoom(result.optJSONObject("room"));
            choosing = false; showHome(); render(); refresh();
        } catch (Exception failure) {
            saveButton.setEnabled(true);
            webHint.setText(failure.getMessage() == null ? "寝室未保存，请重新选择" : failure.getMessage());
        }
    }
    private void selectionFailed(JSONObject result) {
        saveButton.setEnabled(true);
        String reason = result.optString("reason");
        if ("login".equals(reason)) { showSchool(true); return; }
        if ("page".equals(reason)) { webHint.setText("学校电费页面结构已变化，请返回后重试"); return; }
        if ("moved".equals(reason)) { webHint.setText("学校页面已跳转，请返回后重新选择"); return; }
        if ("selector".equals(reason)) { webHint.setText("学校寝室选项无法自动填写，请手动选择"); return; }
        if ("timeout".equals(reason)) { webHint.setText("选择超时，请重新选择寝室"); return; }
        webHint.setText("请重新选择寝室，完成后保存。");
    }
    private void settled(int token, JSONObject result) {
        boolean complete = "complete".equals(result.optString("status"));
        if (complete) {
            try { store.saveBalance(expectedRoom, result); refreshing = false; operation = ""; error = ""; render(); }
            catch (Exception ignored) { failed("寝室或账号已变化，本次结果未保存"); }
            return;
        }
        String reason = result.optString("reason");
        if ("login".equals(reason)) { showSchool(true); return; }
        failed(readFailure(reason));
    }
    private static String readFailure(String reason) {
        if ("room".equals(reason)) return "学校页面寝室与已保存的不一致，请重新选择寝室";
        if ("page".equals(reason)) return "学校电费页面结构已变化，请返回后重试";
        if ("moved".equals(reason)) return "学校页面已跳转，请重新点“更新”";
        if ("selector".equals(reason)) return "学校寝室选项无法恢复，请重新选择寝室";
        if ("query".equals(reason)) return "学校查询无法提交，请重新点“更新”";
        if ("timeout".equals(reason)) return "读取学校电费超时，请稍后点“更新”重试";
        return "未读取到当前寝室余额，已保留上次数据";
    }
    private void render() {
        JSONObject saved = store.read(), room = saved == null ? null : saved.optJSONObject("room");
        JSONObject balance = saved == null ? null : saved.optJSONObject("balance");
        boolean energy = balance != null && "度".equals(balance.optString("unit"));
        balanceTitle.setText(energy ? "宿舍剩余电量" : "宿舍剩余电费");
        amount.setText(balance == null ? "—" : (energy ? "" : "￥") + balance.optString("value") + (energy ? " 度" : ""));
        bedroom.setText(ElectricityParser.description(room));
        ((Button) home.findViewWithTag("change-room")).setText(room == null ? "选择宿舍" : "切换宿舍");
        String stamp = saved == null || saved.optLong("updatedAt") == 0 ? "尚未读取余额" : "更新于 "
            + new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(saved.optLong("updatedAt")));
        updated.setText(refreshing ? "正在读取学校电费…" : !error.isEmpty() ? error + "\n" + stamp : room == null ? "请先选择并保存寝室" : stamp);
        updateButton.setEnabled(!refreshing); rechargeButton.setEnabled(room != null);
    }
    private void stop() { generation++; handler.removeCallbacksAndMessages(null); web.stopLoading(); operation = ""; refreshing = false; }
    private void showHome() { showingSchool = false; home.setVisibility(View.VISIBLE); web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS); }
    private void showSchool(boolean authentication) {
        web.setEnabled(true);
        showingSchool = true; home.setVisibility(View.GONE); web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        webTitle.setText(authentication ? "学校验证" : "选择寝室"); saveButton.setVisibility(authentication ? View.GONE : View.VISIBLE);
        webHint.setText(authentication ? "请完成学校验证，完成后会继续读取电费。"
            : "校区和区域会自动填写，请选择楼栋、楼层和房间；选完自动保存。");
    }
    private void chooseRoom() { stop(); choosing = true; attemptedLogin = false; operation = "choose"; saveButton.setEnabled(false); showSchool(false); web.loadUrl(SchoolCatalog.ELECTRICITY); }
    private void refresh() {
        JSONObject saved = store.read(); expectedRoom = saved == null ? null : saved.optJSONObject("room");
        if (expectedRoom == null) { chooseRoom(); return; }
        stop(); choosing = false; refreshing = true; attemptedLogin = false; operation = "balance"; error = "";
        showHome(); render(); web.loadUrl(SchoolCatalog.ELECTRICITY);
    }
    private void saveRoom() {
        final int token = generation; run(plan("capture", null), token, result -> {
            if (!"complete".equals(result.optString("status"))) { webHint.setText("请先完成楼栋、楼层和房间选择"); return; }
            commitRoom(result);
        });
    }
    private void recharge() {
        JSONObject saved = store.read(); if (saved == null || saved.optJSONObject("room") == null) { chooseRoom(); return; }
        stop(); startActivity(new Intent(this, SchoolWebActivity.class).putExtra("service", "electricity")
            .putExtra("postgraduate", getSharedPreferences("school_profile", 0).getBoolean("postgraduate", false))
            .putExtra("electricityRoom", saved.optJSONObject("room").toString()));
    }
    /**
     * The school answers a rejected application with its own error page instead of a login form.
     * Reading those words is the difference between a clear reason and an endless 正在读取.
     */
    private void inspectAuth() {
        final int token = generation;
        web.evaluateJavascript("(function(){var t=(document.body&&document.body.innerText)||'';"
            + "if(t.indexOf('未授权的访问')>=0||t.indexOf('不允许访问目标应用')>=0)return 'denied';"
            + "if(t.indexOf('应用未注册')>=0)return 'unregistered';"
            + "return document.querySelector('input[type=password]')?'login':'other';})()", value -> {
            if (!active(token)) return;
            String state = text(value);
            if ("denied".equals(state)) { failed("学校未向本账号开放电费应用（未授权的访问），请联系一卡通中心"); return; }
            if ("unregistered".equals(state)) { failed("学校电费应用未在统一认证注册，请联系信息化办公室"); return; }
            authenticate();
        });
    }
    private static String text(String value) {
        try { Object parsed = new JSONTokener(value).nextValue(); return parsed instanceof String ? (String) parsed : ""; }
        catch (Exception ignored) { return ""; }
    }
    private void authenticate() {
        final int token = generation;
        if (loginScript.isEmpty()) { showSchool(true); return; }
        JSONObject credentials = new JSONObject();
        boolean pg = getSharedPreferences("school_profile", 0).getBoolean("postgraduate", false);
        JSONObject remembered = new SchoolAccount(this).remembered(pg);
        try {
            if (!attemptedLogin && remembered != null) credentials.put("username", remembered.optString("username"))
                .put("password", remembered.optString("password")).put("submit", true);
        } catch (Exception ignored) { showSchool(true); return; }
        attemptedLogin = true;
        web.evaluateJavascript(loginScript.replace("__CREDENTIALS__", credentials.toString()), value -> {
            if (!active(token)) return;
            String state = text(value);
            if ("rejected".equals(state)) new SchoolAccount(this).forget(pg);
            if ("submitted".equals(state)) handler.postDelayed(() -> { if (active(token) && SessionPolicy.isAuthHost(Uri.parse(web.getUrl()).getHost())) authenticate(); }, 1000);
            else if (!"not-login".equals(state)) showSchool(true);
        });
    }
    private void failed(String message) { refreshing = false; error = message; if (choosing || showingSchool) { saveButton.setEnabled(true); webHint.setText(message); } else { operation = ""; render(); } }
    private void cancelSchool() { stop(); choosing = false; showHome(); render(); if (store.read() == null) leave(); }
    private void leave() { if (isTaskRoot()) startActivity(new Intent(this, MainActivity.class).putExtra("tab", 1)); finish(); }
    @Override public void onBackPressed() { if (showingSchool) cancelSchool(); else leave(); }
    @Override protected void onResume() {
        super.onResume(); if (store == null || home == null) return;
        if (!store.isCurrent()) { finish(); return; }
        if (!choosing && !refreshing && !showingSchool) refresh();
    }
    @Override protected void onPause() { if (web != null && refreshing && !showingSchool) { stop(); render(); } super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if (web != null) { web.stopLoading(); web.destroy(); web = null; } super.onDestroy(); }
    private String asset(String name) {
        try (InputStream input = getAssets().open(name); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096]; int count; while ((count = input.read(bytes)) >= 0) output.write(bytes, 0, count); return output.toString("UTF-8");
        } catch (Exception ignored) { return ""; }
    }
}
