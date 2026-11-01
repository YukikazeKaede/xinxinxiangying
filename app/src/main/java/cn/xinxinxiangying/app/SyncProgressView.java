package cn.xinxinxiangying.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Opaque foreground for an attached WebView that performs school reads behind it. */
public final class SyncProgressView extends LinearLayout {
    public SyncProgressView(Context context, TextView task) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setBackgroundColor(CampusUi.BG);
        setClickable(true);
        int inset = CampusUi.dp(context, 28);
        setPadding(inset, inset, inset, inset);
        ProgressBar spinner = new ProgressBar(context);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(CampusUi.GREEN));
        addView(spinner, new LayoutParams(CampusUi.dp(context, 32), CampusUi.dp(context, 32)));
        task.setTextSize(18);
        task.setTextColor(CampusUi.INK);
        task.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        task.setGravity(Gravity.CENTER);
        task.setPadding(0, inset, 0, 0);
        task.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(task, new LayoutParams(-1, -2));
    }
}
