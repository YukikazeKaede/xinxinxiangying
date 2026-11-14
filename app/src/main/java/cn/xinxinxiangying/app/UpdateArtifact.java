package cn.xinxinxiangying.app;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.net.URL;

/** Select a device-compatible artifact; legacy feeds retain their original format. */
public final class UpdateArtifact {
    private UpdateArtifact() {}
    public static List<String> sources(JSONObject artifact) {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        addSource(sources, artifact.opt("url"));
        JSONArray mirrors = artifact.optJSONArray("mirrors");
        if (mirrors != null) for (int i = 0; i < mirrors.length(); i++) addSource(sources, mirrors.opt(i));
        return new ArrayList<>(sources);
    }
    private static void addSource(LinkedHashSet<String> sources, Object value) {
        if (!(value instanceof String)) return;
        String source = ((String) value).trim();
        try {
            URL url = new URL(source);
            if ("https".equals(url.getProtocol()) && !url.getHost().isEmpty() && url.getUserInfo() == null)
                sources.add(source);
        } catch (Exception ignored) {}
    }
    public static JSONObject select(JSONObject manifest,String[] supportedAbis) {
        if(!manifest.has("artifacts"))return manifest;
        JSONObject artifacts=manifest.optJSONObject("artifacts");
        if(artifacts!=null&&supportedAbis!=null)for(String abi:supportedAbis){
            JSONObject artifact=artifacts.optJSONObject(abi);
            if(artifact!=null)return artifact;
        }
        throw new IllegalArgumentException("本次更新没有适合此设备架构的安装包");
    }
}
