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
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 第二课堂活动：本机缓存的活动列表与活动详情；报名在学校页面里完成。 */
public final class ActivitiesActivity extends Activity {
    private static final int GRAY = 0xff8a9a95;
    private static final int BLUE = 0xff4f86c6;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ActivityStore store;
    private LinearLayout page, content, filterHost, listHost, webPage;
    private TextView status, webStatus, barName;
    private String statusBase = "";
    private Button barAction, loadMoreButton;
    private boolean captureBar;
    private EditText search;
    private WebView web;
    private String script;
    private String loginScript;
    private String pendingTarget;
    private boolean loginSubmitted;
    private boolean pg;
    private boolean importing, browsing;
    private boolean reading, signupSubmitted;
    private int generation;
    private int polls;
    private boolean scopeLoaded, listRead, hiddenList, optionsShown;
    private JSONObject pendingOptions;
    private ScrollView activityScroll;
    private int requestedPage = 1;
    private boolean loadMoreFailed;
    private final Runnable searchRequest = () -> loadList(true);
    private String term = "";
    private String plan = "";
    private JSONObject detail;
    private String query = "", year = "", block = "", type = "", stateFilter = "";
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        CampusUi.window(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store = new ActivityStore(this);
        pg = getSharedPreferences("school_profile", 0).getBoolean("postgraduate", false);
        if (!AppSession.require(this, "second_activities")) return;
        script = asset("dekt-activities.js");
        loginScript = asset("school-login.js");
        page = CampusUi.column(this);
        page.setBackgroundColor(CampusUi.BG);
        CampusUi.insets(page);
        captureBar = true;
        page.addView(bar("第二课堂活动", "刷新", v -> refresh(), v -> menu()));
        captureBar = false;
        status = CampusUi.text(this, "", 11, CampusUi.MUTED);
        status.setPadding(dp(20), dp(2), dp(20), dp(8));
        page.addView(status);
        ScrollView scroll = activityScroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (y > oldY && !importing && !loadMoreFailed && scroll.getChildCount() > 0
                && y >= scroll.getChildAt(0).getHeight() - scroll.getHeight() - dp(80)) loadNextPage();
        });
        content = CampusUi.column(this);
        content.setPadding(dp(14), 0, dp(14), dp(20));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        render();
        handler.post(this::loadList);
    }

    private int dp(int value) { return CampusUi.dp(this, value); }

    @Override protected void onResume() {
        super.onResume();
        if (page != null && (!AppSession.require(this, "second_activities") || !store.isCurrent())) finish();
    }

    private boolean active(int token) {
        return token == generation && importing && !isFinishing() && !isDestroyed() && store.isCurrent();
    }

    private void leave() {
        if (isTaskRoot()) startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override public void onBackPressed() {
        if (importing) {
            if (hiddenList && page.isShown()) { cancelRead(); generation++; importing = false; leave(); }
            else quitWeb();
            return;
        }
        if (detail != null) { detail = null; setContentView(page); render(); return; }
        leave();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) { web.stopLoading(); web.destroy(); web = null; }
        super.onDestroy();
    }

    private LinearLayout bar(String name, String action, View.OnClickListener click, View.OnClickListener menuClick) {
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
        back.setOnClickListener(v -> onBackPressed());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView label = CampusUi.text(this, name, 18, CampusUi.INK);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(dp(12), 0, 0, 0);
        bar.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Button button = null;
        if (action != null) {
            button = CampusUi.button(this, action, true, click);
            bar.addView(button, new LinearLayout.LayoutParams(-2, dp(44)));
        }
        if (captureBar) { barName = label; barAction = button; }
        LinearLayout more = new LinearLayout(this);
        more.setGravity(Gravity.CENTER);
        more.setBackground(CampusUi.ripple(this, Color.WHITE, 6));
        more.addView(new CampusUi.Icon(this, "more", CampusUi.GREEN), new LinearLayout.LayoutParams(dp(22), dp(22)));
        more.setContentDescription("更多");
        more.setFocusable(true);
        more.setOnClickListener(menuClick);
        bar.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private View searchBox() {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(CampusUi.shape(this, Color.WHITE, 12));
        box.setPadding(dp(14), dp(2), dp(10), dp(2));
        box.addView(new CampusUi.Icon(this, "search", CampusUi.MUTED), new LinearLayout.LayoutParams(dp(18), dp(18)));
        search = new EditText(this);
        search.setHint("搜索活动名称");
        search.setHintTextColor(CampusUi.MUTED);
        search.setTextColor(CampusUi.INK);
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setBackground(null);
        search.setPadding(dp(10), 0, 0, 0);
        if (!query.isEmpty()) {
            search.setText(query);
            search.setSelection(query.length());
        }
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                handler.removeCallbacks(searchRequest);
                handler.postDelayed(searchRequest, 350);
            }
        });
        box.addView(search, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.bottomMargin = dp(12);
        box.setLayoutParams(lp);
        return box;
    }

    private void render() {
        JSONObject saved = store.read();
        if (barName != null) barName.setText("第二课堂活动");
        content.removeAllViews();
        filterHost = CampusUi.column(this);
        listHost = CampusUi.column(this);
        content.addView(searchBox());
        content.addView(filterHost);
        content.addView(listHost);
        if (saved == null) {
            status.setText("尚无本学期活动缓存");
            filters(new JSONObject(), new JSONArray());
            empty();
            return;
        }
        long savedAt = saved.optLong("savedAt", 0);
        JSONObject list = saved.optJSONObject("list");
        if (!scopeLoaded) {
            JSONObject scope = list == null ? null : list.optJSONObject("scope");
            year = scope == null ? "" : scope.optString("xn", "");
            term = scope == null ? "" : scope.optString("xq", "");
            scopeLoaded = true;
        }
        JSONArray items = ActivityFeed.flatten(list);
        JSONArray visible = ActivityFeed.visible(items, System.currentTimeMillis());
        statusBase = "本机保存 · " + when(savedAt) + (list != null && list.has("page")
            ? " · 已加载 " + visible.length() + "/" + list.optInt("total", visible.length()) + " 个活动"
            : " · 共 " + visible.length() + " 个活动");
        if (list == null || !"school-list".equals(list.optString("source"))) statusBase += " · 旧版概览，请重新读取完整列表";
        status.setText(statusBase);
        filters(list, visible);
        fillList();
    }

    /** 四个筛选行：学年、板块、类型、状态，与手机版页面的分类一致。类型按板块归属列出。 */
    private void filters(JSONObject list, JSONArray visible) {
        List<String> blocks = new ArrayList<>();
        JSONArray blockOptions = list == null ? null : list.optJSONArray("blocks");
        if (blockOptions != null) for (int i = 0; i < blockOptions.length(); i++) add(blocks, blockOptions.optJSONObject(i).optString("name"));
        for (int i = 0; i < visible.length(); i++) {
            JSONObject item = visible.optJSONObject(i);
            add(blocks, item.optString("dl", ""));
        }
        List<String> types = ActivityFeed.types(list, visible, block);
        if (!type.isEmpty() && !types.contains(type)) type = "";
        LinearLayout card = CampusUi.column(this);
        card.setBackground(CampusUi.shape(this, Color.WHITE, 14));
        card.setPadding(0, dp(6), 0, dp(6));
        filterHost.addView(card, cardParams());
        scopeRow(card, list, "xn", "学年", year);
        scopeRow(card, list, "xq", "学期", term);
        chipRow(card, "板块", blocks, block, value -> { block = value; type = ""; loadList(true); });
        chipRow(card, "类型", types, type, value -> { type = value; loadList(true); });
        List<String> states = fixed("报名中", "无需报名", "已截止", "未开始", "已结束");
        JSONArray stateOptions = options(list, "hdbmzt");
        if (stateOptions != null) for (int i = 0; i < stateOptions.length(); i++) add(states, displayState(stateOptions.optJSONObject(i).optString("name")));
        chipRow(card, "状态", states, stateFilter,
            value -> { stateFilter = value; loadList(true); });
    }

    private static JSONArray options(JSONObject list, String key) {
        JSONObject filters = list == null ? null : list.optJSONObject("filters");
        return filters == null ? null : filters.optJSONArray(key);
    }

    private static String optionName(JSONArray values, String code) {
        if (values != null) for (int i = 0; i < values.length(); i++) {
            JSONObject option = values.optJSONObject(i);
            if (option != null && code.equals(option.optString("code"))) return option.optString("name");
        }
        return "";
    }

    private void scopeRow(LinearLayout card, JSONObject list, String key, String title, String code) {
        JSONArray values = options(list, key);
        List<String> names = new ArrayList<>();
        if (values != null) for (int i = 0; i < values.length(); i++) add(names, values.optJSONObject(i).optString("name"));
        if ("xn".equals(key)) Collections.sort(names, Collections.reverseOrder());
        chipRow(card, title, names, optionName(values, code), name -> {
            String value = "";
            if (values != null) for (int i = 0; i < values.length(); i++) {
                JSONObject option = values.optJSONObject(i);
                if (name.equals(option.optString("name"))) value = option.optString("code");
            }
            if ("xn".equals(key)) year = value; else term = value;
            loadList(true);
        }, !"xq".equals(key));
    }

    private List<String> fixed(String... values) {
        List<String> out = new ArrayList<>();
        for (String value : values) out.add(value);
        return out;
    }

    private void add(List<String> list, String value) {
        if (value == null || value.isEmpty() || list.contains(value)) return;
        list.add(value);
    }

    private void chipRow(LinearLayout card, String name, List<String> options, String selected, Pick pick) {
        chipRow(card, name, options, selected, pick, true);
    }

    private void chipRow(LinearLayout card, String name, List<String> options, String selected, Pick pick, boolean includeAll) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(4), dp(8), dp(4));
        TextView label = CampusUi.text(this, name, 11, CampusUi.MUTED);
        row.addView(label, new LinearLayout.LayoutParams(dp(36), -2));
        // “全部”固定在滚动区外，选中具体类型或横向滚动时也始终可见。
        if (includeAll) row.addView(chip(ActivityFeed.NONE, selected == null || selected.isEmpty(), v -> pick.on("")));
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        for (String option : options)
            chips.addView(chip(option, option.equals(selected), v -> pick.on(option)));
        scroller.addView(chips);
        row.addView(scroller, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
    }

    private interface Pick { void on(String value); }

    private TextView chip(String text, boolean on, View.OnClickListener click) {
        TextView view = CampusUi.text(this, text, 12, on ? CampusUi.GREEN : CampusUi.MUTED);
        view.setPadding(dp(11), dp(6), dp(11), dp(6));
        view.setBackground(CampusUi.shape(this, on ? CampusUi.SOFT : 0xfff2f6f5, 12));
        view.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        view.setLayoutParams(lp);
        return view;
    }

    private void fillList() {
        loadMoreButton = null;
        listHost.removeAllViews();
        JSONObject saved = store.read();
        if (saved == null) return;
        JSONArray visible = ActivityFeed.visible(ActivityFeed.flatten(saved.optJSONObject("list")), System.currentTimeMillis());
        long now = System.currentTimeMillis();
        int shown = 0;
        LinearLayout card = CampusUi.column(this);
        card.setBackground(CampusUi.shape(this, Color.WHITE, 14));
        boolean first = true;
        for (int i = 0; i < visible.length(); i++) {
            JSONObject item = visible.optJSONObject(i);
            if (item == null || !match(item, now)) continue;
            if (!first) card.addView(divider());
            first = false;
            card.addView(cardRow(item, now));
            shown++;
        }
        if (shown == 0) {
            TextView none = CampusUi.text(this, "没有符合筛选条件的活动", 13, CampusUi.MUTED);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, dp(24), 0, dp(24));
            listHost.addView(none);
        } else {
            listHost.addView(card, cardParams());
        }
        status.setText(statusBase + (shown != visible.length() ? " · 显示 " + shown + " 个" : ""));
        JSONObject list = saved.optJSONObject("list");
        if (list != null && list.optBoolean("hasMore", false)) {
            Button more = loadMoreButton = CampusUi.button(this, loadMoreFailed ? "加载失败，点击重试" : "下滑加载更多", false, v -> loadNextPage());
            listHost.addView(more, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        TextView note = CampusUi.text(this, "每次加载10条，下滑继续；默认本学期，其他学期按选择查询。", 11, CampusUi.MUTED);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(6), dp(16), dp(6), dp(8));
        listHost.addView(note);
    }

    private boolean match(JSONObject item, long now) {
        if (!query.isEmpty() && !item.optString("name", "").toLowerCase(Locale.ROOT)
            .contains(query.toLowerCase(Locale.ROOT))) return false;
        if (!block.isEmpty() && !block.equals(item.optString("dl", ""))) return false;
        if (!type.isEmpty() && !type.equals(item.optString("lb", ""))) return false;
        String current = ActivityFeed.state(item, now);
        return stateFilter.isEmpty() || stateFilter.equals(displayState(current));
    }

    private View cardRow(JSONObject item, long now) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(CampusUi.ripple(this, Color.WHITE, 0));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = CampusUi.text(this, item.optString("name", ""), 15, CampusUi.INK);
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(2);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(badge(ActivityFeed.state(item, now)));
        row.addView(head);
        String span = ActivityFeed.span(item.optString("start", ""), item.optString("end", ""));
        if (!span.isEmpty()) {
            TextView time = CampusUi.text(this, "时间：" + span, 12, CampusUi.MUTED);
            time.setPadding(0, dp(6), 0, 0);
            row.addView(time);
        }
        String meta = join(item.optString("dl", ""), item.optString("lb", ""), item.optString("host", ""));
        if (!meta.isEmpty()) {
            TextView line = CampusUi.text(this, meta, 12, CampusUi.MUTED);
            line.setPadding(0, dp(4), 0, 0);
            row.addView(line);
        }
        row.setFocusable(true);
        row.setContentDescription(item.optString("name", ""));
        row.setOnClickListener(v -> openDetail(item.optString("id", "")));
        return row;
    }

    private static String join(String... parts) {
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (text.length() > 0) text.append(" · ");
            text.append(part);
        }
        return text.toString();
    }

    private static String displayState(String state) {
        if ("报名已截止".equals(state)) return "已截止";
        if ("报名未开始".equals(state)) return "未开始";
        return state;
    }

    private TextView badge(String state) {
        state = displayState(state);
        int color = "报名中".equals(state) ? CampusUi.GREEN
            : "未开始".equals(state) ? BLUE
            : "无需报名".equals(state) ? 0xff0f766e : GRAY;
        TextView view = CampusUi.text(this, state, 10, color);
        view.setPadding(dp(8), dp(3), dp(8), dp(3));
        view.setBackground(CampusUi.shape(this, 0x14000000 | (color & 0x00ffffff), 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(8);
        view.setLayoutParams(lp);
        return view;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(CampusUi.LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        line.setLayoutParams(lp);
        return line;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        return lp;
    }

    private void empty() {
        listHost.removeAllViews();
        LinearLayout box = CampusUi.column(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, dp(38), 0, dp(10));
        box.addView(new CampusUi.ScheduleArt(this), new LinearLayout.LayoutParams(dp(150), dp(140)));
        TextView head = CampusUi.text(this, "还没有读取第二课堂活动", 17, CampusUi.INK);
        head.setGravity(Gravity.CENTER);
        head.setPadding(0, dp(20), 0, 0);
        box.addView(head);
        TextView hint = CampusUi.text(this, "登录学校账号后，应用会在第二课堂活动页面里读取活动列表并保存在本机。", 13, CampusUi.MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(4), 1);
        hint.setPadding(dp(18), dp(10), dp(18), dp(18));
        box.addView(hint);
        Button load = CampusUi.button(this, "从学校读取", true, v -> loadList());
        box.addView(load, new LinearLayout.LayoutParams(-2, dp(46)));
        listHost.addView(box);
    }

    private void menu() {
        String[] items = {"查看学校页面", "重新读取", "清除本机缓存"};
        new AlertDialog.Builder(this).setItems(items, (dialog, which) -> {
            if (which == 0) browse(SchoolCatalog.DEKT_ACTIVITIES);
            else if (which == 1) { scopeLoaded = false; loadList(); }
            else new AlertDialog.Builder(this).setTitle("清除活动列表？")
                .setMessage("只清除本机保存的这份列表，学校系统里的数据不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (d, w) -> {
                    store.clear();
                    year = term = block = type = stateFilter = "";
                    scopeLoaded = false;
                    query = "";
                    if (search != null) search.setText("");
                    render();
                }).show();
        }).show();
    }

    private void refresh() {
        if (detail != null) { openDetail(detail.optString("id", "")); return; }
        loadList(true);
    }

    // ---- 学校页面：读列表、读详情、报名 ----

    private void loadList() { loadList(false); }

    private JSONObject queryCatalog() {
        if (pendingOptions != null) return pendingOptions;
        JSONObject saved = store.read();
        return saved == null ? null : saved.optJSONObject("list");
    }

    private String optionCode(JSONArray choices, String name) {
        if (choices != null) for (int i = 0; i < choices.length(); i++) {
            JSONObject option = choices.optJSONObject(i);
            if (option != null && name.equals(option.optString("name"))) return option.optString("code");
        }
        return "";
    }

    private void loadList(boolean selectedScope) {
        if (!AppSession.require(this, "second_activities")) return;
        try {
            JSONObject request = new JSONObject().put("action", "list").put("page", 1).put("reuseDictionary", selectedScope);
            JSONObject catalog = queryCatalog(), conditions = new JSONObject();
            if (selectedScope && scopeLoaded && catalog != null && "school-list".equals(catalog.optString("source"))) {
                request.put("filters", new JSONObject().put("xn", year).put("xq", term));
                if (!block.isEmpty()) conditions.put("dldm", ActivityFeed.blockCode(catalog, ActivityFeed.flatten(catalog), block));
                if (!type.isEmpty()) conditions.put("lbdm", optionCode(catalog.optJSONArray("menus"), type));
                if (!stateFilter.isEmpty()) {
                    String code = "";
                    JSONArray choices = options(catalog, "hdbmzt");
                    if (choices != null) for (int i = 0; i < choices.length(); i++) {
                        JSONObject option = choices.optJSONObject(i);
                        if (stateFilter.equals(displayState(option.optString("name")))) code = option.optString("code");
                    }
                    if (code.isEmpty()) {
                        code = optionCode(options(catalog, "hdsfjxz"), stateFilter);
                        if (code.isEmpty()) { Toast.makeText(this, "学校暂未提供此筛选条件", Toast.LENGTH_SHORT).show(); return; }
                        conditions.put("hdsfjxz", code);
                    } else conditions.put("hdbmzt", code);
                }
                request.put("search", query).put("conditions", conditions);
            } else { query = block = type = stateFilter = ""; }
            requestedPage = 1;
            loadMoreFailed = false;
            openWeb(SchoolCatalog.DEKT_ACTIVITIES, request.toString(), false);
        } catch (Exception e) { Toast.makeText(this, "无法读取筛选条件，请重试", Toast.LENGTH_SHORT).show(); }
    }

    private void loadNextPage() {
        if (importing || detail != null || !store.isCurrent()) return;
        JSONObject saved = store.read(), list = saved == null ? null : saved.optJSONObject("list");
        if (list == null || !list.optBoolean("hasMore", false)) return;
        try {
            JSONObject criteria = list.optJSONObject("query");
            if (criteria == null) return;
            requestedPage = list.getInt("page") + 1;
            JSONObject request = new JSONObject().put("action", "list").put("page", requestedPage)
                .put("filters", list.getJSONObject("scope")).put("search", criteria.optString("text"))
                .put("conditions", criteria.getJSONObject("conditions")).put("expectedTotal", list.getInt("total"))
                .put("pageSize", list.getInt("pageSize"));
            loadMoreFailed = false;
            if (loadMoreButton != null) { loadMoreButton.setText("正在加载…"); loadMoreButton.setEnabled(false); }
            openWeb(SchoolCatalog.DEKT_ACTIVITIES, request.toString(), false);
        } catch (Exception e) { loadMoreFailed = true; }
    }

    private void openDetail(String id) {
        if (id == null || id.isEmpty()) return;
        openWeb("https://xg.sit.edu.cn/hdgl/hdxq/" + Uri.encode(id),
            action("detail", id), false);
    }

    private static String action(String name, String id) {
        try { return new JSONObject().put("action", name).put("id", id)
            .put("requestId", java.util.UUID.randomUUID().toString()).toString(); }
        catch (Exception e) { throw new IllegalArgumentException("无法读取活动"); }
    }

    private void browse(String url) { openWeb(url, null, true); }

    private void openWeb(String url, String planJson, boolean keep) {
        if (!AppSession.require(this, "second_activities")) return;
        cancelRead();
        importing = true;
        generation++;
        handler.removeCallbacksAndMessages(null);
        reading = signupSubmitted = false;
        optionsShown = false;
        loginSubmitted = false;
        pendingTarget = url;
        browsing = keep;
        plan = planJson == null ? "" : planJson;
        try { hiddenList = !keep && "list".equals(new JSONObject(plan).optString("action")); }
        catch (Exception ignored) { hiddenList = false; }
        polls = 0;
        if (webPage == null) webPage = buildWeb();
        webMessage(browsing ? "学校页面已打开" : "正在复用学校登录状态…");
        setContentView(hiddenList ? page : webPage);
        // 先走学工统一认证入口，已有 CAS 会话时不再要求输入账号密码。
        if (hiddenList && web.getUrl() != null && isXgPage(web.getUrl()) && !SessionPolicy.isLoginUrl(web.getUrl())) extractNow();
        else web.loadUrl(SchoolCatalog.DEKT_SSO);
    }

    private void webMessage(String message) {
        if (webStatus != null) webStatus.setText(message);
        if (hiddenList && status != null) status.setText(message);
    }

    private static boolean isAuthPage(String url) {
        return SessionPolicy.isAuthHost(Uri.parse(url).getHost());
    }

    private static boolean isXgPage(String url) {
        return "xg.sit.edu.cn".equalsIgnoreCase(Uri.parse(url).getHost());
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
                if ("challenge".equals(state)) webMessage("请完成学校验证码，完成后会自动继续");
                else if ("rejected".equals(state)) {
                    new SchoolAccount(this).forget(pg);
                    webMessage("保存的密码未通过，请在学校页面重新输入");
                } else if ("submitted".equals(state)) webMessage("正在验证学校账号…");
                else if ("not-login".equals(state) || "needs-login".equals(state)) loginSubmitted = false;
            });
        } catch (Exception e) {
            loginSubmitted = false;
        }
    }

    private LinearLayout buildWeb() {
        LinearLayout root = CampusUi.column(this);
        root.setBackgroundColor(CampusUi.BG);
        CampusUi.insets(root);
        root.addView(bar("学校第二课堂", "读取本页", v -> extractNow(), v -> quitWeb()));
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
                Toast.makeText(ActivitiesActivity.this, "已停止打开外部页面", Toast.LENGTH_SHORT).show();
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame() && !SchoolCatalog.isSchoolUrl(request.getUrl().toString()))
                    return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", Collections.emptyMap(), null);
                return null;
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                if (!importing || !store.isCurrent() || isFinishing() || !SchoolCatalog.isSchoolUrl(url) || !url.equals(view.getUrl())) return;
                if (isAuthPage(url)) {
                    if (hiddenList) setContentView(webPage);
                    webMessage("请登录学校账号，登录后会自动回到第二课堂活动");
                    autoFillAuth();
                    return;
                }
                // 换票后先到学工首页，再打开用户本来要看的活动列表/详情。
                // 列表只需同源会话，无需再加载活动概览及其中的跨学期推荐。
                if (hiddenList && isXgPage(url) && !SessionPolicy.isLoginUrl(url)) pendingTarget = null;
                if (pendingTarget != null) {
                    if (url.equals(pendingTarget)) pendingTarget = null;
                    else if (isXgPage(url) && SessionPolicy.isLoginUrl(url)) {
                        if (hiddenList) setContentView(webPage);
                        webMessage("请登录学校账号，登录后会自动回到第二课堂活动");
                        return;
                    } else if (isXgPage(url)) {
                        String target = pendingTarget;
                        pendingTarget = null;
                        webMessage("正在打开第二课堂活动…");
                        web.loadUrl(target);
                        return;
                    }
                }
                if (hiddenList) setContentView(page);
                if (browsing) {
                    webMessage("学校页面已打开 · 报名请在学校页面上完成"); return;
                }
                if (!plan.isEmpty() && importing && !reading && !signupSubmitted) {
                    handler.postDelayed(ActivitiesActivity.this::extractNow, 800);
                } else {
                    webMessage("请先登录学校账号，登录后再读取");
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) webMessage("连接学校页面失败 · 请确认在校园网内");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (!request.isForMainFrame() || view != web || !importing || isFinishing() || isDestroyed()
                    || !store.isCurrent() || !request.getUrl().toString().equals(view.getUrl())) return;
                String current = request.getUrl().toString();
                if (response.getStatusCode() >= 400 && pendingTarget != null && !current.equals(pendingTarget)) {
                    String target = pendingTarget;
                    pendingTarget = null;
                    webMessage("统一认证入口暂不可用，直接打开第二课堂活动…");
                    view.loadUrl(target);
                }
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                webMessage("无法验证学校网站证书 · 已停止加载");
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private void extractNow() {
        if (web == null || !importing || browsing) return;
        if (reading) return;
        if (signupSubmitted) { webMessage("请先返回详情确认报名结果，再决定是否重试"); return; }
        if (plan.isEmpty()) { webMessage("这页不是活动页面 · 点“重新读取”"); return; }
        if (script == null || script.isEmpty()) { webMessage("读取脚本缺失，请安装完整版本"); return; }
        String url = web.getUrl();
        if (url == null || !SchoolCatalog.isSchoolUrl(url)
            || !"xg.sit.edu.cn".equalsIgnoreCase(Uri.parse(url).getHost())) {
            webMessage("请先登录并打开第二课堂页面"); return;
        }
        if (!store.isCurrent()) return;
        reading = true;
        final int token = ++generation;
        try {
            String action = new JSONObject(plan).optString("action");
            signupSubmitted = "signup".equals(action);
            listRead = "list".equals(action);
        }
        catch (Exception e) { reading = false; return; }
        polls = 0;
        handler.removeCallbacksAndMessages(null);
        webMessage(listRead ? "正在读取完整活动列表…" : "正在读取这一页…");
        String code = script.replace("__ACT_PLAN__", JSONObject.quote(plan));
        web.evaluateJavascript(code, null);
        handler.postDelayed(() -> poll(token), 600);
    }

    private void poll(int token) {
        if (web == null || !active(token)) return;
        web.evaluateJavascript("window.__xinxinActs?JSON.stringify(window.__xinxinActs):''", value -> {
            if (web == null || !active(token)) return;
            String raw = unquote(value);
            JSONObject state = null;
            if (!raw.isEmpty()) { try { state = new JSONObject(raw); } catch (Exception e) { state = null; } }
            if (state == null) { again(token); return; }
            if (listRead && !optionsShown && state.optJSONObject("options") != null) {
                optionsShown = true;
                showOptions(state.optJSONObject("options"));
            }
            String status = state.optString("status", "");
            if ("complete".equals(status)) {
                String action = state.optString("action", "");
                if ("signup".equals(action)) { onSignup(state); return; }
                if ("detail".equals(action)) onDetail(state); else onList(state);
                return;
            }
            if ("failed".equals(status)) { failed(state.optString("reason", "")); return; }
            if (listRead) webMessage("正在更新所选学期 · 已读取 " + state.optInt("pages", 0) + " 页 · 暂显示原缓存");
            again(token);
        });
    }

    private void showOptions(JSONObject options) {
        if (options == null || options.optJSONObject("scope") == null) return;
        pendingOptions = options;
        year = options.optJSONObject("scope").optString("xn", "");
        term = options.optJSONObject("scope").optString("xq", "");
        scopeLoaded = true;
        filterHost.removeAllViews();
        JSONObject saved = store.read();
        filters(options, ActivityFeed.flatten(saved == null ? null : saved.optJSONObject("list")));
    }

    private void again(int token) {
        polls++;
        if (polls > (listRead ? 200 : 25)) { failed("timeout"); return; }
        handler.postDelayed(() -> poll(token), 600);
    }

    private void cancelRead() {
        if (web != null) web.evaluateJavascript("if(window.__xinxinActs&&typeof window.__xinxinActs.cancel==='function')window.__xinxinActs.cancel()", null);
    }

    private void failed(String reason) {
        cancelRead();
        reading = false;
        if (listRead && requestedPage > 1) {
            importing = false;
            loadMoreFailed = true;
            fillList();
            status.setText("加载下一页失败，已加载活动保留；可点击重试");
            return;
        }
        if (hiddenList && !"login".equals(reason)) {
            importing = false;
            pendingOptions = null;
            scopeLoaded = false;
            restoreQuery();
            render();
        }
        if (hiddenList && "login".equals(reason)) setContentView(webPage);
        if ("login".equals(reason)) webMessage("学校登录状态已失效 · 请点右上角“刷新”重新验证，本机缓存不会丢失");
        else if ("blocked".equals(reason)) webMessage("只在学校页面内读取");
        else if ("filters".equals(reason)) webMessage("未读取到学校筛选字典，请稍后重试；原缓存保留");
        else if ("changed".equals(reason)) webMessage("读取期间学校列表发生变化，请重新读取；原缓存保留");
        else if ("limit".equals(reason)) webMessage("活动过多，请先在学校页面查看；未保存不完整列表");
        else if ("timeout".equals(reason)) webMessage("读取超时，请稍后重试；原缓存保留");
        else if ("detail".equals(reason)) webMessage("没有读到活动详情 · 请点“重新读取”");
        else webMessage("没有读到活动列表 · 请确认页面加载完成后再点“读取本页”");
    }

    private void onList(JSONObject state) {
        JSONObject list = state.optJSONObject("list");
        if (list == null || !"school-list".equals(list.optString("source")) || list.optJSONArray("groups") == null) { failed("list"); return; }
        int oldY = activityScroll.getScrollY();
        try {
            if (requestedPage > 1) {
                JSONObject saved = store.read(), old = saved == null ? null : saved.optJSONObject("list");
                if (old == null || old.optInt("page") + 1 != list.optInt("page") || list.optInt("page") != requestedPage
                    || old.optInt("total") != list.optInt("total") || old.optInt("pageSize") != list.optInt("pageSize")
                    || !old.getJSONObject("scope").getString("xn").equals(list.getJSONObject("scope").getString("xn"))
                    || !old.getJSONObject("scope").getString("xq").equals(list.getJSONObject("scope").getString("xq"))
                    || !old.getJSONObject("query").getString("text").equals(list.getJSONObject("query").getString("text"))) { failed("changed"); return; }
                JSONObject before = old.getJSONObject("query").getJSONObject("conditions"), after = list.getJSONObject("query").getJSONObject("conditions");
                for (String key : new String[]{"dldm", "lbdm", "hdbmzt", "hdsfjxz"})
                    if (!before.optString(key).equals(after.optString(key))) { failed("changed"); return; }
                JSONArray rows = old.getJSONArray("groups").getJSONObject(0).getJSONArray("items");
                JSONArray nextGroups = list.getJSONArray("groups");
                JSONArray next = nextGroups.length() == 0 ? new JSONArray() : nextGroups.getJSONObject(0).getJSONArray("items");
                java.util.HashSet<String> seen = new java.util.HashSet<>();
                for (int i = 0; i < rows.length(); i++) seen.add(rows.getJSONObject(i).optString("id"));
                int added = 0;
                for (int i = 0; i < next.length(); i++) if (seen.add(next.getJSONObject(i).optString("id"))) { rows.put(next.getJSONObject(i)); added++; }
                if (next.length() > 0 && added == 0) { failed("changed"); return; }
                list.put("groups", new JSONArray().put(new JSONObject().put("items", rows)));
            }
            JSONObject snapshot = new JSONObject().put("savedAt", System.currentTimeMillis()).put("list", list);
            store.save(snapshot);
        } catch (Exception e) { failed("list"); return; }
        importing = false;
        handler.removeCallbacksAndMessages(null);
        setContentView(page);
        detail = null;
        pendingOptions = null;
        scopeLoaded = false;
        render();
        final int scrollY = requestedPage > 1 ? oldY : 0;
        activityScroll.post(() -> activityScroll.scrollTo(0, scrollY));
        if (requestedPage == 1) Toast.makeText(this, "活动列表已更新", Toast.LENGTH_SHORT).show();
    }

    private void onDetail(JSONObject state) {
        detail = state.optJSONObject("detail");
        importing = false;
        handler.removeCallbacksAndMessages(null);
        if (detail == null) { failed("detail"); return; }
        // 详情接口没有报名窗口，用列表里的时间补上，方便先判断“未开始/已截止”。
        JSONObject cached = listItem(detail.optString("id", ""));
        if (cached != null) {
            fill(detail, "signupStart", cached.optString("signupStart", ""));
            fill(detail, "signupEnd", cached.optString("signupEnd", ""));
        }
        JSONObject saved = store.read();
        JSONObject list = saved == null ? null : saved.optJSONObject("list");
        if (list != null && "school-list".equals(list.optString("source"))) {
            String schoolState = optionName(options(list, "hdbmzt"), detail.optString("state", ""));
            try { detail.put("schoolState", schoolState.isEmpty() ? "状态待核实" : schoolState); } catch (Exception ignored) { }
        }
        setContentView(page);
        renderDetail();
    }

    private static void fill(JSONObject target, String key, String value) {
        if (value == null || value.isEmpty() || !target.optString(key, "").isEmpty()) return;
        try { target.put(key, value); } catch (Exception ignored) { }
    }

    /** 报名结果：成功就回到详情并重新读取，失败把学校给的原文提示出来。 */
    private void onSignup(JSONObject state) {
        JSONObject result = state.optJSONObject("signup");
        importing = false;
        handler.removeCallbacksAndMessages(null);
        String message = result == null ? "学校没有返回结果，请稍后重试" : result.optString("message", "");
        boolean ok = result != null && result.optBoolean("ok", false);
        setContentView(page);
        if (detail != null) renderDetail();
        if (message.isEmpty()) message = ok ? "报名成功" : "学校拒绝了这次报名";
        new AlertDialog.Builder(this)
            .setTitle(ok ? "已报名" : "没有报名成功")
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show();
        if (ok && detail != null) {
            detail.remove("signed");
            try { detail.put("signed", true); } catch (Exception ignored) { }
            if (detail != null) renderDetail();
        }
    }

    private void restoreQuery() {
        JSONObject saved = store.read(), list = saved == null ? null : saved.optJSONObject("list");
        JSONObject criteria = list == null ? null : list.optJSONObject("query");
        JSONObject conditions = criteria == null ? null : criteria.optJSONObject("conditions");
        query = criteria == null ? "" : criteria.optString("text");
        block = conditions == null ? "" : optionName(list.optJSONArray("blocks"), conditions.optString("dldm"));
        type = conditions == null ? "" : optionName(list.optJSONArray("menus"), conditions.optString("lbdm"));
        stateFilter = conditions == null ? "" : displayState(optionName(options(list,"hdbmzt"), conditions.optString("hdbmzt")));
    }

    private void quitWeb() {
        cancelRead();
        pendingOptions = null;
        scopeLoaded = false;
        generation++;
        reading = false;
        handler.removeCallbacksAndMessages(null);
        if (web != null) web.stopLoading();
        importing = false;
        browsing = false;
        setContentView(page);
        if (detail != null) renderDetail(); else render();
    }

    // ---- 活动详情页 ----

    private void renderDetail() {
        if (detail == null) { render(); return; }
        if (barName != null) barName.setText("活动详情");
        JSONObject saved = store.read();
        JSONArray menus = saved == null ? new JSONArray()
            : saved.optJSONObject("list") == null ? new JSONArray()
            : saved.optJSONObject("list").optJSONArray("menus");
        String group = detail.optString("dlmc", blockName(detail.optString("groupCode", "")));
        String label = detail.optString("lbmc", labelName(menus, detail.optString("labelCode", "")));
        status.setText((detail.optString("year", "") + " " + detail.optString("term", "")).trim());
        filterHost.removeAllViews();
        listHost.removeAllViews();
        content.removeAllViews();
        TextView name = CampusUi.text(this, detail.optString("name", ""), 20, CampusUi.INK);
        name.setTypeface(null, Typeface.BOLD);
        name.setLineSpacing(dp(3), 1);
        content.addView(name);
        LinearLayout tags = new LinearLayout(this);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        tags.setPadding(0, dp(10), 0, 0);
        String pair = join(group, label);
        if (!pair.isEmpty()) tags.addView(tag(pair, CampusUi.GREEN, 0xffe7f3ef));
        String credit = detail.optString("credit", "");
        if (!credit.isEmpty()) tags.addView(tag(credit + " 学分", ORANGE_RGB(), 0xfffff5de));
        String signedTag = detail.optBoolean("signed", false) ? "已报名" : "";
        if (!signedTag.isEmpty()) tags.addView(tag(signedTag, 0xff4a7c59, 0xffe7f3ef));
        content.addView(tags);
        LinearLayout info = CampusUi.column(this);
        info.setBackground(CampusUi.shape(this, Color.WHITE, 14));
        info.setPadding(dp(16), dp(4), dp(16), dp(4));
        infoRow(info, "开始时间", stamp(detail.optString("start", "")));
        infoRow(info, "结束时间", stamp(detail.optString("end", "")));
        infoRow(info, "活动地点", detail.optString("place", ""));
        String duration = detail.optString("duration", "");
        if (!duration.isEmpty()) infoRow(info, "活动时长", duration + " 分钟");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        content.addView(info, lp);
        String desc = detail.optString("desc", "");
        if (!desc.isEmpty()) {
            home("活动描述");
            LinearLayout card = CampusUi.column(this);
            card.setBackground(CampusUi.shape(this, Color.WHITE, 14));
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            TextView text = CampusUi.text(this, desc, 13, CampusUi.INK);
            text.setLineSpacing(dp(5), 1);
            card.addView(text);
            content.addView(card, cardParams());
        }
        home("主办信息");
        LinearLayout host = CampusUi.column(this);
        host.setBackground(CampusUi.shape(this, Color.WHITE, 14));
        host.setPadding(dp(16), dp(4), dp(16), dp(4));
        infoRow(host, "主办方", hostName(detail.optString("id", ""), detail.optString("host", "")));
        infoRow(host, "负责人", detail.optString("leader", ""));
        infoRow(host, "联系方式", detail.optString("phone", ""));
        String project = projectName(detail.optString("id", ""));
        if (project.isEmpty()) project = detail.optString("project", "");
        if (!project.isEmpty() && !project.matches("[0-9A-Fa-f]{16,}"))
            infoRow(host, "所属项目", project);
        content.addView(host, cardParams());
        boolean signed = detail.optBoolean("signed", false);
        long now = System.currentTimeMillis();
        long end = ActivityFeed.time(detail.optString("end", ""));
        String buttonText = "报名";
        boolean enabled = true;
        String note = "报名在本页完成，提交后会显示学校的处理结果。";
        if (signed) { buttonText = "已报名"; enabled = false; note = "这一项已经报过名，可以在学校页面查看进度。"; }
        else if (end > 0 && end < now) { buttonText = "报名已结束"; enabled = false; note = "活动已经结束，不能再报名。"; }
        else if (signupNotOpen()) { buttonText = "报名未开始"; enabled = false; note = "还没到学校的报名开始时间。"; }
        else if (signupClosed()) { buttonText = "报名已截止"; enabled = false; note = "学校已经关闭了这次报名。"; }
        else if (detail.has("schoolState") && !"报名中".equals(detail.optString("schoolState"))) {
            buttonText = detail.optString("schoolState"); enabled = false;
            note = "当前状态以学校页面为准，请查看学校页面确认。";
        }
        Button signup = CampusUi.button(this, buttonText, enabled, v -> startSignup());
        signup.setEnabled(enabled);
        content.addView(signup, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView noteView = CampusUi.text(this, note, 11, CampusUi.MUTED);
        noteView.setGravity(Gravity.CENTER);
        noteView.setPadding(0, dp(12), 0, dp(8));
        content.addView(noteView);
    }

    private boolean signupNotOpen() {
        long start = ActivityFeed.time(detail.optString("signupStart", ""));
        return start > 0 && start > System.currentTimeMillis();
    }

    private boolean signupClosed() {
        long end = ActivityFeed.time(detail.optString("signupEnd", ""));
        return end > 0 && end < System.currentTimeMillis();
    }

    /** 报名靠学校自己的表单接口完成，结果由 onSignup 处理。 */
    private void startSignup() {
        String id = detail == null ? "" : detail.optString("id", "");
        if (id.isEmpty()) return;
        openWeb(SchoolCatalog.DEKT_ACTIVITIES,
            action("signup", id), false);
        webMessage("正在提交报名…");
    }

    private static int ORANGE_RGB() { return 0xffb7791f; }

    /** 去掉秒：2026-11-01 13:00:00 → 2026-11-01 13:00。 */
    private static String stamp(String value) {
        String text = value == null ? "" : value.trim();
        if (text.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) return text.substring(0, 16);
        return text;
    }

    private JSONObject listItem(String id) {
        JSONObject saved = store.read();
        if (saved == null) return null;
        JSONArray items = ActivityFeed.flatten(saved.optJSONObject("list"));
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && id.equals(item.optString("id", ""))) return item;
        }
        return null;
    }

    /** 学校用数字代码表示主办方，列表里才有名称；读不到名称时宁可不显示。 */
    private String hostName(String id, String fallback) {
        JSONObject item = listItem(id);
        String name = item == null ? "" : item.optString("host", "");
        if (!name.isEmpty()) return name;
        return fallback.matches("\\d{3,}") ? "" : fallback;
    }

    private String projectName(String id) {
        JSONObject item = listItem(id);
        return item == null ? "" : item.optString("project", "");
    }

    private String blockName(String code) {
        if ("01".equals(code)) return "德育";
        if ("02".equals(code)) return "劳育";
        if ("03".equals(code)) return "美育";
        if ("04".equals(code)) return "健康教育";
        return "";
    }

    private String labelName(JSONArray menus, String code) {
        if (menus == null || code.isEmpty()) return "";
        for (int i = 0; i < menus.length(); i++) {
            JSONObject menu = menus.optJSONObject(i);
            if (menu != null && code.equals(menu.optString("code", ""))) return menu.optString("name", "");
        }
        return "";
    }

    private void home(String text) {
        TextView view = CampusUi.text(this, text, 13, CampusUi.MUTED);
        view.setPadding(dp(4), dp(18), 0, dp(8));
        content.addView(view);
    }

    private void infoRow(LinearLayout card, String key, String value) {
        if (value == null || value.isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(11), 0, dp(11));
        TextView label = CampusUi.text(this, key, 13, CampusUi.MUTED);
        row.addView(label, new LinearLayout.LayoutParams(dp(78), -2));
        TextView text = CampusUi.text(this, value, 13, CampusUi.INK);
        text.setGravity(Gravity.END);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
    }

    private TextView tag(String text, int color, int background) {
        TextView view = CampusUi.text(this, text, 11, color);
        view.setPadding(dp(9), dp(4), dp(9), dp(4));
        view.setBackground(CampusUi.shape(this, background, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        view.setLayoutParams(lp);
        return view;
    }

    private static String when(long millis) {
        if (millis <= 0) return "尚未读取";
        return new SimpleDateFormat("M月d日 HH:mm 更新", Locale.CHINA).format(new Date(millis));
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
