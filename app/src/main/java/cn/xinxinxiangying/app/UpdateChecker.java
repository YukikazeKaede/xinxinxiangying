package cn.xinxinxiangying.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * In-app update check. The feed is a small HTTPS JSON document configured by
 * {@code update_manifest_url}; the APK is downloaded into app-private storage,
 * verified against the manifest SHA-256 and handed to the system installer.
 */
public final class UpdateChecker {
    private static final String PREFS = "app_update";
    private static final long AUTO_INTERVAL = 6 * 60 * 60 * 1000L;
    private static boolean autoChecked;
    private static File pendingInstall;
    private static volatile boolean downloading;
    private static volatile boolean cancelled;
    private static volatile HttpURLConnection connection;

    private UpdateChecker() {}

    private static final class Manifest {
        int code;
        String name;
        String url;
        String sha256;
        long size;
        String notes;
    }

    public static String versionName(Activity a) {
        try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return ""; }
    }

    private static int versionCode(Activity a) {
        try {
            PackageManager pm = a.getPackageManager();
            if (Build.VERSION.SDK_INT >= 28) return (int) pm.getPackageInfo(a.getPackageName(), 0).getLongVersionCode();
            return pm.getPackageInfo(a.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) { return 0; }
    }

    /** Called once per process from MainActivity; respects the six-hour interval. */
    public static void autoCheck(Activity a) {
        if (autoChecked) return;
        autoChecked = true;
        long last = a.getSharedPreferences(PREFS, 0).getLong("last_check", 0);
        if (System.currentTimeMillis() - last < AUTO_INTERVAL) return;
        check(a, false);
    }

    public static void check(final Activity a, final boolean manual) {
        if (downloading) {
            if (manual) Toast.makeText(a, "正在下载更新", Toast.LENGTH_SHORT).show();
            return;
        }
        final String feed = a.getString(R.string.update_manifest_url).trim();
        if (feed.isEmpty() || !feed.startsWith("https://")) {
            if (manual) new AlertDialog.Builder(a).setTitle("检查更新")
                    .setMessage("应用内更新需要一个 HTTPS 更新清单地址，当前版本尚未配置。")
                    .setPositiveButton("知道了", null).show();
            return;
        }
        if (manual) Toast.makeText(a, "正在检查更新…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                final Manifest m = fetch(feed);
                a.getSharedPreferences(PREFS, 0).edit().putLong("last_check", System.currentTimeMillis()).apply();
                final int current = versionCode(a);
                a.runOnUiThread(() -> {
                    if (a.isFinishing() || a.isDestroyed()) return;
                    if (m.code <= current) {
                        if (manual) Toast.makeText(a, "已是最新版本 " + versionName(a), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int skipped = a.getSharedPreferences(PREFS, 0).getInt("skipped_code", 0);
                    if (!manual && skipped >= m.code) return;
                    show(a, m);
                });
            } catch (final Exception e) {
                a.runOnUiThread(() -> {
                    if (manual && !a.isFinishing() && !a.isDestroyed()) new AlertDialog.Builder(a)
                            .setTitle("检查更新失败")
                            .setMessage(e.getMessage() == null ? "网络不可用，请稍后再试。" : e.getMessage())
                            .setPositiveButton("知道了", null).show();
                });
            }
        }, "update-check").start();
    }

    private static Manifest fetch(String feed) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(feed).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("User-Agent", "xinxin-update");
        try {
            int status = c.getResponseCode();
            if (!"https".equals(c.getURL().getProtocol())) throw new IOException("更新清单地址必须是 HTTPS");
            if (status != 200) throw new IOException("更新服务返回 HTTP " + status);
            StringBuilder body = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            try (Reader in = new InputStreamReader(c.getInputStream(), "UTF-8")) {
                while ((n = in.read(buf)) > 0) {
                    body.append(buf, 0, n);
                    if (body.length() > 65536) throw new IOException("更新清单过大");
                }
            }
            JSONObject o = new JSONObject(body.toString());
            Manifest m = new Manifest();
            m.code = o.getInt("versionCode");
            m.name = o.getString("versionName").trim();
            m.url = o.getString("url").trim();
            m.sha256 = o.getString("sha256").trim().toLowerCase(Locale.ROOT);
            m.size = o.optLong("size", 0);
            m.notes = o.optString("notes", "").trim();
            if (m.code <= 0 || m.name.isEmpty()) throw new IOException("更新清单的版本号无效");
            if (!m.url.startsWith("https://")) throw new IOException("更新下载地址必须是 HTTPS");
            if (!m.sha256.matches("[0-9a-f]{64}")) throw new IOException("更新清单的校验值无效");
            return m;
        } finally {
            c.disconnect();
        }
    }

    private static void show(final Activity a, final Manifest m) {
        StringBuilder message = new StringBuilder();
        if (!m.notes.isEmpty()) message.append(m.notes).append("\n\n");
        message.append("当前版本：").append(versionName(a)).append("\n新版本：").append(m.name);
        if (m.size > 0) message.append("\n安装包：").append(size(m.size));
        AlertDialog.Builder builder = new AlertDialog.Builder(a)
                .setTitle("发现新版本 " + m.name)
                .setMessage(message)
                .setPositiveButton("立即更新", (d, w) -> download(a, m))
                .setNegativeButton("稍后", null);
        builder.setNeutralButton("跳过此版本", (d, w) ->
                a.getSharedPreferences(PREFS, 0).edit().putInt("skipped_code", m.code).apply());
        builder.show();
    }

    private static void download(final Activity a, final Manifest m) {
        if (a.isFinishing() || a.isDestroyed()) return;
        final File dir = new File(a.getFilesDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(a, "无法创建更新目录", Toast.LENGTH_SHORT).show();
            return;
        }
        final File target = new File(dir, "update-" + m.code + ".apk");
        try {
            if (target.isFile() && m.sha256.equals(sha256(target))) {
                install(a, target);
                return;
            }
        } catch (Exception ignored) {}
        if (target.exists()) target.delete();

        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView label = new TextView(a);
        label.setText("正在下载 " + m.name);
        final ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        box.addView(label);
        box.addView(bar);
        final AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("下载更新")
                .setView(box)
                .setNegativeButton("取消", (d, w) -> {
                    cancelled = true;
                    HttpURLConnection c = connection;
                    if (c != null) c.disconnect();
                })
                .setCancelable(false)
                .create();
        downloading = true;
        cancelled = false;
        dialog.show();

        new Thread(() -> {
            final File part = new File(target.getPath() + ".part");
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(m.url).openConnection();
                connection = c;
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setInstanceFollowRedirects(true);
                c.setUseCaches(false);
                c.setRequestProperty("Cache-Control", "no-cache");
                c.setRequestProperty("User-Agent", "xinxin-update");
                int status = c.getResponseCode();
                if (!"https".equals(c.getURL().getProtocol())) throw new IOException("更新下载地址必须是 HTTPS");
                if (status != 200) throw new IOException("下载失败 HTTP " + status);
                final long total = m.size > 0 ? m.size : contentLength(c.getHeaderField("Content-Length"));
                if (total > 0) a.runOnUiThread(() -> {
                    bar.setIndeterminate(false);
                    bar.setMax(100);
                });
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                    byte[] buf = new byte[16384];
                    long read = 0;
                    int n;
                    int lastPercent = -1;
                    while ((n = in.read(buf)) > 0) {
                        if (cancelled) throw new IOException("已取消");
                        out.write(buf, 0, n);
                        read += n;
                        if (total > 0) {
                            int percent = (int) (read * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final int shown = percent;
                                a.runOnUiThread(() -> {
                                    if (dialog.isShowing()) bar.setProgress(shown);
                                });
                            }
                        }
                    }
                } finally {
                    c.disconnect();
                    connection = null;
                }
                if (cancelled) throw new IOException("已取消");
                if (!m.sha256.equals(sha256(part))) throw new IOException("安装包校验失败，请重试");
                if (!part.renameTo(target)) throw new IOException("无法保存安装包");
                a.runOnUiThread(() -> {
                    downloading = false;
                    if (dialog.isShowing()) dialog.dismiss();
                    install(a, target);
                });
            } catch (final Exception e) {
                part.delete();
                a.runOnUiThread(() -> {
                    downloading = false;
                    connection = null;
                    if (dialog.isShowing()) dialog.dismiss();
                    if (!cancelled && !a.isFinishing() && !a.isDestroyed()) {
                        Toast.makeText(a, e.getMessage() == null ? "下载失败" : e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "update-download").start();
    }

    private static void install(final Activity a, final File file) {
        if (a.isFinishing() || a.isDestroyed()) return;
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            pendingInstall = file;
            new AlertDialog.Builder(a)
                    .setTitle("允许安装更新")
                    .setMessage("系统需要允许「心心相应」安装应用。开启后返回，应用会继续安装。")
                    .setPositiveButton("去开启", (d, w) -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception e) {
                            try { a.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); }
                            catch (Exception ignored) {}
                        }
                    })
                    .setNegativeButton("取消", (d, w) -> pendingInstall = null)
                    .setCancelable(false)
                    .show();
            return;
        }
        startInstaller(a, file);
    }

    /** Returns true when an install is pending, so automatic checks stay quiet. */
    public static boolean resumeInstall(Activity a) {
        File file = pendingInstall;
        if (file == null) return false;
        if (!file.isFile()) { pendingInstall = null; return false; }
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) return true;
        pendingInstall = null;
        startInstaller(a, file);
        return true;
    }

    private static void startInstaller(Activity a, File file) {
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(a.getPackageName() + ".updates")
                .appendPath(file.getName())
                .build();
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClipData(ClipData.newRawUri("更新安装包", uri));
        try {
            a.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(a, "无法打开系统安装器：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static long contentLength(String value) {
        try { return Long.parseLong(value); } catch (Exception e) { return 0; }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
        }
        final char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest.digest()) {
            hex.append(digits[(b >> 4) & 0xf]).append(digits[b & 0xf]);
        }
        return hex.toString();
    }

    private static String size(long bytes) {
        if (bytes >= 1048576) return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
        if (bytes >= 1024) return (bytes / 1024) + " KB";
        return bytes + " B";
    }
}
