package cn.xinxinxiangying.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Two-field dormitory lookup; selection and balance use the school's own portal. */
public final class ElectricityActivity extends Activity {
    private ElectricityStore store;
    private LinearLayout home;
    private TextView amount, balanceTitle, updated, bedroom;
    private EditText buildingInput, roomInput;
    private Button updateButton, rechargeButton;
    private String error = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); CampusUi.window(this);
        if (!AppSession.require(this, "electricity")) return;
        store = new ElectricityStore(this);
        home = buildHome(); setContentView(home);
        JSONObject saved = store.read(), room = saved == null ? null : saved.optJSONObject("portalRoom");
        buildingInput.setText(state != null ? state.getString("building", "") : room == null ? "" : room.optString("buildingNumber"));
        roomInput.setText(state != null ? state.getString("room", "") : room == null ? "" : room.optString("roomNumber"));
        render();
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
        LinearLayout form = card(content);
        buildingInput = roomInput(form, "楼栋", "例如：23", "electricity-building", 4);
        roomInput = roomInput(form, "寝室号", "例如：1104", "electricity-room", 8);
        form.addView(note("填写楼栋和寝室号，自动匹配学校的电控系统、校区、区域、楼栋、楼层和房间。"));
        form.addView(CampusUi.button(this, "查询电费", true, v -> queryRoom()), new LinearLayout.LayoutParams(-1, dp(48)));
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
        Button change = CampusUi.button(this, "修改", false, v -> { buildingInput.requestFocus(); ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(buildingInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT); }); change.setTag("change-room");
        room.addView(change, new LinearLayout.LayoutParams(-2, dp(44))); balance.addView(room);
        LinearLayout notes = card(content);
        notes.addView(note("学校电费余额每天更新一次，以学校记录和电表为准。"));
        notes.addView(note("缴费成功后，学校系统需要对账和同步，可稍后点上方“更新”核对。"));
        notes.addView(note("温馨提示：如对充值过程和结果有疑问，请直接咨询学校的后勤或一卡通中心。"));
        LinearLayout footer = CampusUi.column(this); footer.setPadding(dp(16), dp(12), dp(16), dp(16));
        rechargeButton = CampusUi.button(this, "充值电费", true, v -> recharge()); footer.addView(rechargeButton, new LinearLayout.LayoutParams(-1, dp(52)));
        page.addView(footer); return page;
    }
    private TextView note(String value) {
        TextView line = CampusUi.text(this, value, 13, CampusUi.MUTED); line.setLineSpacing(dp(6), 1);
        line.setPadding(0, 0, 0, dp(10)); return line;
    }
    private EditText roomInput(LinearLayout form, String label, String hint, String tag, int max) {
        TextView title = CampusUi.text(this, label, 14, CampusUi.INK); form.addView(title);
        EditText input = new EditText(this); input.setSingleLine(true); input.setTextSize(17);
        input.setTextColor(CampusUi.INK); input.setHint(hint); input.setTag(tag); input.setContentDescription(label);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
        form.addView(input, new LinearLayout.LayoutParams(-1, dp(52))); title.setLabelFor(View.generateViewId());
        input.setId(title.getLabelFor()); return input;
    }
    private void queryRoom() {
        String building = buildingInput.getText().toString().trim(), room = roomInput.getText().toString().trim();
        if (!building.matches("[0-9]{1,4}")) { buildingInput.setError("请输入楼栋编号，例如 23"); buildingInput.requestFocus(); return; }
        if (!room.matches("[0-9]{1,8}")) { roomInput.setError("请输入寝室号，例如 1104"); roomInput.requestFocus(); return; }
        openPortal(false, true, building, room);
    }
    private void refresh() { queryRoom(); }
    private void recharge() {
        JSONObject saved = store.read(), room = saved == null ? null : saved.optJSONObject("portalRoom");
        if (room == null) { queryRoom(); return; }
        openPortal(true, false, room.optString("buildingNumber"), room.optString("roomNumber"));
    }
    private void openPortal(boolean recharge, boolean choose, String building, String room) {
        if (!store.isCurrent()) { finish(); return; }
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(buildingInput.getWindowToken(), 0);
        startActivityForResult(new Intent(this, SchoolWebActivity.class).putExtra("service", "electricity")
            .putExtra("electricityRecharge", recharge).putExtra("electricityChoose", choose)
            .putExtra("electricityBuilding", building).putExtra("electricityRoomNumber", room)
            .putExtra("postgraduate", getSharedPreferences("school_profile", 0).getBoolean("postgraduate", false)), 1);
    }
    private void render() {
        JSONObject saved = store.read(), room = saved == null ? null : saved.optJSONObject("room");
        JSONObject balance = saved == null ? null : saved.optJSONObject("balance");
        JSONObject portalRoom = saved == null ? null : saved.optJSONObject("portalRoom");
        boolean energy = balance != null && "度".equals(balance.optString("unit"));
        balanceTitle.setText(energy ? "宿舍剩余电量" : "宿舍剩余电费");
        amount.setText(balance == null ? "—" : (energy ? "" : "￥") + balance.optString("value") + (energy ? " 度" : ""));
        bedroom.setText(portalRoom == null ? ElectricityParser.description(room) : portalRoom.optString("name"));
        String stamp = saved == null || saved.optLong("updatedAt") == 0 ? "尚未读取余额" : "更新于 "
            + new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(saved.optLong("updatedAt")));
        updated.setText(!error.isEmpty() ? error + "\n" + stamp : portalRoom == null ? "填写楼栋和寝室号后查询" : stamp);
        rechargeButton.setEnabled(portalRoom != null);
    }
    private void leave() { if (isTaskRoot()) startActivity(new Intent(this, MainActivity.class).putExtra("tab", 1)); finish(); }
    @Override public void onBackPressed() { leave(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 1 || store == null || !store.isCurrent()) return;
        error = data == null ? "" : data.getStringExtra("electricityError"); if (error == null) error = "";
        render();
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (buildingInput != null) { state.putString("building", buildingInput.getText().toString()); state.putString("room", roomInput.getText().toString()); }
    }
    @Override protected void onResume() { super.onResume(); if (store != null && home != null) { if (!store.isCurrent()) finish(); else render(); } }
}
