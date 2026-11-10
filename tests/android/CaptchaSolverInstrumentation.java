package cn.xinxinxiangying.app.test;
import android.app.*;
import android.os.*;
import cn.xinxinxiangying.app.*;
import java.io.*;
import java.util.*;
/**
 * 方案 §7 V1 硬闸门：设备端必须复现 PC 侧 captcha-probe/probe.py 的识别结果。
 *
 * <p>样本是合成图（captcha-probe/make_synthetic.py 生成），随测试包放在
 * tests/android/assets/ocr/golden/，不进入应用 APK。真实学校验证码只留在
 * 本机被忽略的目录里做准确率统计，不提交。
 *
 * <p>比对按大小写不敏感：模型会把大写读成小写，这是模型行为而不是流水线差异，
 * 学校侧也不区分大小写。真正要比的是流水线（缩放、灰度、NCHW 顺序）一致。
 */
public final class CaptchaSolverInstrumentation extends Instrumentation {
    int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static byte[] bytes(InputStream in)throws IOException{try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] chunk=new byte[8192];int read;while((read=source.read(chunk))!=-1)out.write(chunk,0,read);return out.toByteArray();}}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            CaptchaSolver solver=CaptchaSolver.load(getTargetContext());
            Map<String,String> expected=new LinkedHashMap<>();
            for(String line:new String(bytes(getContext().getAssets().open("ocr/golden/expected.tsv")),"UTF-8").split("\n")){
                String trimmed=line.trim();
                if(trimmed.isEmpty())continue;
                String[] parts=trimmed.split("\t");
                check(parts.length>=2,"bad expected row: "+trimmed);
                expected.put(parts[0],parts[1]);
            }
            check(expected.size()>=5,"need at least 5 golden samples, found "+expected.size());
            for(Map.Entry<String,String> item:expected.entrySet()){
                byte[] png=bytes(getContext().getAssets().open("ocr/golden/"+item.getKey()));
                CaptchaSolver.Result outcome=solver.inspect(png);
                check(outcome!=null,item.getKey()+": Bitmap/inference failed");
                check(outcome.text.equalsIgnoreCase(item.getValue()),item.getKey()+": expected "+item.getValue()+" got "+outcome.text);
                check(outcome.usable,item.getKey()+": rejected at confidence "+outcome.confidence);
                check(solver.solve(png)!=null,item.getKey()+": solve() returned null for a usable sample");
            }
            // 不可用的输入必须干净地返回 null，绝不能让异常冒到登录流程里。
            check(solver.inspect(new byte[0])==null,"empty input must decode to null");
            check(solver.inspect(new byte[]{1,2,3,4})==null,"truncated input must decode to null");
            check(solver.solve(new byte[0])==null,"solve() must return null for empty input");
            solver.close();
            result.putString("result","PASS "+checks+" on-device captcha checks ("+expected.size()+" golden samples)");
        }catch(Throwable e){result.putString("failure",e.toString());result.putString("stack",android.util.Log.getStackTraceString(e));}
        finish(result.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
