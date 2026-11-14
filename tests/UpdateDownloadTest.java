package cn.xinxinxiangying.app;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;

/** Synthetic HTTPS responses exercise the production downloader without Android or external servers. */
public final class UpdateDownloadTest {
    private static final byte[] GOOD = "SYNTHETIC_APK_CONTENT".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final List<String> opened = new ArrayList<>();
    private static final List<Mock> connections = new ArrayList<>();
    private static UpdateDownload transfer;
    private static int checks;
    private static File dir, target;
    private static String hash;
    private static void check(boolean ok, String reason) { checks++; if (!ok) throw new AssertionError(reason); }
    private static String url(String path) { return "https://example.invalid/" + path; }
    private static final class Mock extends HttpURLConnection {
        boolean closed;
        Mock(URL url) { super(url); }
        public void connect() {}
        public boolean usingProxy() { return false; }
        public void disconnect() { closed = true; }
        public int getResponseCode() throws IOException {
            if (url.getPath().contains("timeout")) throw new SocketTimeoutException("SYNTHETIC_TIMEOUT");
            if (url.getPath().contains("downgrade")) url = new URL("http://example.invalid/unsafe");
            return url.getPath().contains("404") ? 404 : 200;
        }
        public String getHeaderField(String key) { return url.getPath().contains("unknown") ? null : "" + GOOD.length; }
        public InputStream getInputStream() {
            byte[] body = GOOD;
            String path = url.getPath();
            if (path.contains("corrupt")) { body = GOOD.clone(); body[0] ^= 1; }
            if (path.contains("short")) body = Arrays.copyOf(GOOD, GOOD.length - 1);
            if (path.contains("large")) body = Arrays.copyOf(GOOD, GOOD.length + 1);
            final byte[] bytes = body;
            return new ByteArrayInputStream(bytes) {
                public synchronized int read(byte[] buf, int off, int len) {
                    if (path.contains("cancel")) transfer.cancel();
                    if (path.contains("slow") && pos == 0) {
                        try { Thread.sleep(15020); } catch (InterruptedException e) { throw new AssertionError(e); }
                        return super.read(buf, off, 1);
                    }
                    return super.read(buf, off, len);
                }
            };
        }
    }
    private static void reset() throws IOException {
        Files.deleteIfExists(target.toPath());
        Files.deleteIfExists(new File(target + ".part").toPath());
        opened.clear(); connections.clear(); transfer = new UpdateDownload();
    }
    private static void run(String... paths) throws Exception {
        List<String> sources = new ArrayList<>();
        for (String path : paths) sources.add(url(path));
        transfer.download(sources, target, hash, GOOD.length, (i, n, read, total) -> {});
    }
    private static void clean() {
        check(!new File(target + ".part").exists(), "no partial file remains");
        for (Mock connection : connections) check(connection.closed, "all connections disconnected");
    }
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            protected URLConnection openConnection(URL url) throws IOException {
                opened.add(url.toString());
                if (url.getPath().contains("refused")) throw new ConnectException("SYNTHETIC_REFUSED");
                Mock c = new Mock(url); connections.add(c); return c;
            }
        } : null);
        dir = Files.createTempDirectory("SYNTHETIC-update-").toFile(); target = new File(dir, "update-28.apk");
        Files.write(target.toPath(), GOOD); hash = UpdateDownload.sha256(target);
        try {
            reset(); run("good", "unused");
            check(opened.equals(Arrays.asList(url("good"))), "primary success does not request mirrors"); clean();
            run("unused"); check(opened.size() == 1, "verified cache skips all network requests");
            for (String bad : new String[]{"404", "timeout", "refused", "corrupt", "short", "large", "downgrade", "slow"}) {
                reset(); run(bad, "good");
                check(opened.equals(Arrays.asList(url(bad), url("good"))), "fallback after " + bad);
                check(hash.equals(UpdateDownload.sha256(target)), "only verified bytes saved after " + bad); clean();
            }
            reset(); Files.write(target.toPath(), new byte[]{0}); run("good");
            check(hash.equals(UpdateDownload.sha256(target)), "corrupted cache is replaced"); clean();
            reset(); transfer.download(Arrays.asList(url("unknown")), target, hash, 0, (i,n,r,t) -> {});
            check(hash.equals(UpdateDownload.sha256(target)), "missing size/header still requires hash"); clean();
            reset();
            try { run("404", "corrupt"); throw new AssertionError("all sources failed but succeeded"); }
            catch (IOException e) { check(e.getMessage().contains("2"), "failure reports attempted source count"); }
            check(!target.exists(), "all failed sources never produce installable file"); clean();
            reset();
            try { run("cancel", "unused"); throw new AssertionError("cancelled transfer succeeded"); }
            catch (IOException e) { check(transfer.isCancelled(), "cancel remains distinguishable"); }
            check(opened.size() == 1 && !target.exists(), "cancellation stops before next source and installer"); clean();
            reset(); transfer.cancel();
            try { run("unused"); throw new AssertionError("pre-cancel succeeded"); }
            catch (IOException expected) { check(opened.isEmpty(), "pre-cancel does not connect"); } clean();
            check(!UpdateDownload.isSlow(1, 14999), "slow detection has a grace period");
            check(UpdateDownload.isSlow(32768 * 15, 15000), "32 KiB/s triggers fallback");
            check(!UpdateDownload.isSlow(65536 * 15, 15000), "64 KiB/s remains usable");
            System.out.println("PASS UpdateDownloadTest: " + checks + " checks");
        } finally { Files.deleteIfExists(target.toPath()); Files.deleteIfExists(new File(target + ".part").toPath()); Files.deleteIfExists(dir.toPath()); }
    }
}
