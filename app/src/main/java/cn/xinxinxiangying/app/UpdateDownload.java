package cn.xinxinxiangying.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.List;

/** Sequential HTTPS mirrors; only a verified complete file can reach the installer. */
public final class UpdateDownload {
    public interface Progress { void update(int source, int count, long read, long total); }
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;

    public void cancel() {
        cancelled = true;
        HttpURLConnection c = connection;
        if (c != null) c.disconnect();
    }

    public boolean isCancelled() { return cancelled; }

    private void checkCancelled() throws IOException {
        if (cancelled) throw new IOException("已取消");
    }

    public void download(List<String> sources, File target, String hash, long size, Progress progress) throws Exception {
        File part = new File(target.getPath() + ".part");
        try {
            checkCancelled();
            if (target.isFile() && hash.equals(sha256(target))) return;
            if (target.exists() && !target.delete()) throw new IOException("无法清理旧安装包");
            for (int i = 0; i < sources.size(); i++) {
                checkCancelled();
                HttpURLConnection c = null;
                try {
                    if (part.exists() && !part.delete()) throw new IOException("无法清理临时安装包");
                    progress.update(i + 1, sources.size(), 0, size);
                    URL url = new URL(sources.get(i));
                    if (!"https".equals(url.getProtocol())) throw new IOException("更新下载地址必须是 HTTPS");
                    c = (HttpURLConnection) url.openConnection();
                    connection = c;
                    checkCancelled();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(20000);
                    c.setInstanceFollowRedirects(true);
                    c.setUseCaches(false);
                    c.setRequestProperty("Cache-Control", "no-cache");
                    c.setRequestProperty("User-Agent", "xinxin-update");
                    int status = c.getResponseCode();
                    if (!"https".equals(c.getURL().getProtocol())) throw new IOException("更新下载地址必须是 HTTPS");
                    if (status != 200) throw new IOException("下载失败 HTTP " + status);
                    long total = size > 0 ? size : contentLength(c.getHeaderField("Content-Length"));
                    long read = 0, started = System.nanoTime();
                    int lastPercent = -1;
                    try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            checkCancelled();
                            out.write(buf, 0, n);
                            read += n;
                            if (size > 0 && read > size) throw new IOException("安装包大小不符");
                            int percent = total > 0 ? (int) Math.min(100, read * 100 / total) : -1;
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                progress.update(i + 1, sources.size(), read, total);
                            }
                            // Permit the final source to finish even on a slow network.
                            long elapsed = (System.nanoTime() - started) / 1000000;
                            if (i + 1 < sources.size() && read != total && isSlow(read, elapsed))
                                throw new IOException("下载速度过慢，正在切换下载源");
                        }
                    }
                    checkCancelled();
                    if (size > 0 && read != size) throw new IOException("安装包下载不完整");
                    if (!hash.equals(sha256(part))) throw new IOException("安装包校验失败");
                    checkCancelled();
                    if (!part.renameTo(target)) throw new IOException("无法保存安装包");
                    return;
                } catch (IOException e) {
                    checkCancelled();
                    if (i + 1 == sources.size())
                        throw new IOException("下载失败，已尝试 " + sources.size() + " 个源，请稍后重试", e);
                } finally {
                    if (c != null) c.disconnect();
                    connection = null;
                }
            }
            throw new IOException("没有可用的 HTTPS 下载源");
        } finally {
            if (part.exists()) part.delete();
        }
    }

    static boolean isSlow(long read, long elapsedMillis) {
        return elapsedMillis >= 15000 && read * 1000.0 / elapsedMillis < 64 * 1024;
    }

    private static long contentLength(String value) {
        try { return Long.parseLong(value); } catch (Exception e) { return 0; }
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
        }
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest.digest()) hex.append(digits[(b >> 4) & 15]).append(digits[b & 15]);
        return hex.toString();
    }
}
