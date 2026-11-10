package cn.xinxinxiangying.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/**
 * 本机验证码识别：assets 里的 ddddocr 模型 + 字符表，端侧 ONNX 推理，不联网。
 *
 * <p>与 captcha-probe/probe.py 是同一套预处理/后处理，改动必须两边同步：
 * RGB 等比缩放到高 64（BILINEAR）→ ITU-R 601 灰度 → 像素 /255 → NCHW →
 * argmax → 去连续重复 → 丢 CTC blank(index 0) → 查字符表。
 *
 * <p>模型是 <b>动态宽度</b>（[1,1,64,image_width]），所以每张图都要按原图比例
 * 现算宽度；把它写死成固定宽度会让 5 张黄金样本之外的样本全部走样。
 */
public final class CaptchaSolver {
    static final String MODEL_ASSET = "ocr/captcha.onnx";
    static final String CHARSET_ASSET = "ocr/charset.json";
    static final int HEIGHT = 64;
    static final int LENGTH = 4;
    /** 学校验证码是 4 位数字+字母；其它字符一律视为识别失败。 */
    private static final String ALLOWED =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /**
     * 置信度下限。实测 60 张真实样本中最低的正确结果是 0.75，唯一一张错的是 0.77，
     * 所以这个阈值<b>分不开</b>“对”和“错”，只用来拦住几乎没看清的输出；
     * 长度与字符集校验才是主要闸门。
     */
    static final float MIN_CONFIDENCE = 0.50f;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final String[] charset;

    private CaptchaSolver(OrtEnvironment environment, OrtSession session, String inputName, String[] charset) {
        this.environment = environment;
        this.session = session;
        this.inputName = inputName;
        this.charset = charset;
    }

    /** 一次性载入模型与字符表；失败抛异常，由调用方决定是否退回人工。 */
    public static CaptchaSolver load(Context context) throws Exception {
        byte[] model = readAsset(context, MODEL_ASSET);
        File modelFile = materializeModel(context, model);
        String[] charset = parseCharset(new String(readAsset(context, CHARSET_ASSET), "UTF-8"));
        OrtEnvironment environment = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        // 6.5 MB 的小模型单线程足够，省电也比并发更可控。
        options.setIntraOpNumThreads(1);
        OrtSession session = environment.createSession(modelFile.getAbsolutePath(), options);
        String inputName = session.getInputNames().iterator().next();
        return new CaptchaSolver(environment, session, inputName, charset);
    }

    /**
     * 识别一张图片（PNG/JPEG 字节均可）。不可用时返回 null ——
     * 长度不是 4、含 [0-9A-Za-z] 之外的字符、或置信度低于阈值，都算不可用。
     */
    public String solve(byte[] image) {
        Result result = inspect(image);
        return result != null && result.usable ? result.text : null;
    }

    /** 与 solve 相同，但把置信度和“为什么不可用”一并给出，供日志与测试用。 */
    public Result inspect(byte[] image) {
        if (image == null || image.length == 0) return null;
        Bitmap source = BitmapFactory.decodeByteArray(image, 0, image.length);
        if (source == null) return null;
        try {
            int width = source.getWidth();
            int height = source.getHeight();
            if (width <= 0 || height <= 0) return null;
            int targetWidth = Math.max(1, Math.round(width * (float) HEIGHT / height));
            Bitmap scaled = Bitmap.createScaledBitmap(source, targetWidth, HEIGHT, true);
            try {
                FloatBuffer buffer = toNchw(scaled, targetWidth);
                long[] shape = new long[]{1, 1, HEIGHT, targetWidth};
                try (OnnxTensor input = OnnxTensor.createTensor(environment, buffer, shape);
                     OrtSession.Result output = session.run(
                             Collections.<String, OnnxTensor>singletonMap(inputName, input))) {
                    return decode((float[][][]) output.get(0).getValue());
                }
            } finally {
                if (scaled != source) scaled.recycle();
            }
        } catch (Exception error) {
            return null;
        } finally {
            source.recycle();
        }
    }

    /** 灰度 + NCHW，顺序必须是 [1][1][H][W]，行优先。 */
    private FloatBuffer toNchw(Bitmap bitmap, int width) {
        int[] pixels = new int[width * HEIGHT];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, HEIGHT);
        FloatBuffer buffer = FloatBuffer.allocate(pixels.length);
        for (int index = 0; index < pixels.length; index++) {
            int color = pixels[index];
            float red = (color >> 16) & 0xFF;
            float green = (color >> 8) & 0xFF;
            float blue = color & 0xFF;
            buffer.put(index, (0.299f * red + 0.587f * green + 0.114f * blue) / 255f);
        }
        buffer.rewind();
        return buffer;
    }

    /** CTC 解码：逐步 argmax，去掉连续重复，丢掉 index 0（blank）。 */
    private Result decode(float[][][] output) {
        if (output == null || output.length == 0) return null;
        StringBuilder text = new StringBuilder(LENGTH + 2);
        float scoreSum = 0f;
        int scoreCount = 0;
        int previous = -1;
        for (float[][] step : output) {
            if (step == null || step.length == 0) continue;
            float[] logits = step[0];
            int best = 0;
            float bestValue = logits[0];
            for (int index = 1; index < logits.length; index++) {
                if (logits[index] > bestValue) {
                    bestValue = logits[index];
                    best = index;
                }
            }
            if (best != previous && best != 0 && best < charset.length) {
                text.append(charset[best]);
                // 只对真正吐出来的字符算概率：softmax 分母用同一行的全部类别。
                double total = 0;
                for (float value : logits) total += Math.exp(value - bestValue);
                scoreSum += (float) (1 / total);
                scoreCount++;
            }
            previous = best;
        }
        String raw = text.toString();
        float confidence = scoreCount == 0 ? 0f : scoreSum / scoreCount;
        boolean usable = raw.length() == LENGTH && isAllowed(raw) && confidence >= MIN_CONFIDENCE;
        return new Result(raw, confidence, usable);
    }

    private static boolean isAllowed(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (ALLOWED.indexOf(text.charAt(index)) < 0) return false;
        }
        return true;
    }

    /**
     * 模型只能从真实文件路径载入（createSession 不认 assets://），
     * 而且 assets 是 deflate 压缩的，只能用 open() 读流。
     * 文件名带模型 sha 前 8 位：换模型自动重新落盘，旧文件顺手删掉。
     */
    private static File materializeModel(Context context, byte[] model) throws Exception {
        File directory = new File(context.getFilesDir(), "ocr");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("无法创建模型目录");
        String name = "captcha-" + sha256(model).substring(0, 8) + ".onnx";
        File target = new File(directory, name);
        File[] stale = directory.listFiles((dir, fileName) -> fileName.endsWith(".onnx") && !fileName.equals(name));
        if (stale != null) {
            for (File file : stale) file.delete();
        }
        if (target.isFile() && target.length() == model.length) return target;
        try (FileOutputStream stream = new FileOutputStream(target)) {
            stream.write(model);
            stream.getFD().sync();
        }
        return target;
    }

    private static byte[] readAsset(Context context, String name) throws Exception {
        try (InputStream stream = context.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, stream.available()));
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) != -1) out.write(chunk, 0, read);
            return out.toByteArray();
        }
    }

    private static String[] parseCharset(String json) throws Exception {
        org.json.JSONArray array = new org.json.JSONArray(json);
        String[] charset = new String[array.length()];
        for (int index = 0; index < charset.length; index++) charset[index] = array.getString(index);
        return charset;
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder text = new StringBuilder(digest.length * 2);
        for (byte value : digest) text.append(String.format(Locale.ROOT, "%02x", value));
        return text.toString();
    }

    /** 释放 ORT 会话；Activity 在 onDestroy 调用，避免 60~90 MB RSS 常驻。 */
    public void close() {
        try {
            session.close();
        } catch (Exception ignored) {
            // 关闭失败不影响退出流程。
        }
    }

    public static final class Result {
        public final String text;
        public final float confidence;
        public final boolean usable;

        Result(String text, float confidence, boolean usable) {
            this.text = text;
            this.confidence = confidence;
            this.usable = usable;
        }
    }
}
