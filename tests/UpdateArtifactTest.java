import cn.xinxinxiangying.app.UpdateArtifact;
import org.json.JSONObject;

public final class UpdateArtifactTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        JSONObject legacy=new JSONObject().put("url","https://example.invalid/legacy.apk");
        check(UpdateArtifact.select(legacy,new String[]{"arm64-v8a"})==legacy,"legacy feed remains supported");
        JSONObject artifacts=new JSONObject();
        for(String abi:new String[]{"arm64-v8a","armeabi-v7a","x86","x86_64"})artifacts.put(abi,new JSONObject().put("url","https://example.invalid/"+abi+".apk"));
        JSONObject feed=new JSONObject().put("artifacts",artifacts);
        for(String abi:new String[]{"arm64-v8a","armeabi-v7a","x86","x86_64"})
            check(UpdateArtifact.select(feed,new String[]{abi}).getString("url").endsWith("/"+abi+".apk"),"selects each device architecture");
        check(UpdateArtifact.select(feed,new String[]{"x86_64","x86"})==artifacts.getJSONObject("x86_64"),"uses device preference order");
        check(UpdateArtifact.select(feed,new String[]{"unknown","armeabi-v7a"})==artifacts.getJSONObject("armeabi-v7a"),"selects a supported fallback");
        for(JSONObject invalid:new JSONObject[]{new JSONObject().put("artifacts",new JSONObject()),new JSONObject().put("artifacts","invalid")}){
            try{UpdateArtifact.select(invalid,new String[]{"x86"});throw new AssertionError("unsupported artifact accepted");}
            catch(IllegalArgumentException expected){checks++;}
        }
        System.out.println("PASS UpdateArtifactTest: "+checks+" checks");
    }
}
