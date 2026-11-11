package cn.xinxinxiangying.app;

import org.json.JSONObject;

/** Select a device-compatible artifact; legacy feeds retain their original format. */
public final class UpdateArtifact {
    private UpdateArtifact() {}
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
