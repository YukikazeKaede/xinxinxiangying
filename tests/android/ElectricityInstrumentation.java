package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.*;
import android.widget.*;
import cn.xinxinxiangying.app.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Uses only an isolated installation and synthetic school selectors, never a real room or payment. */
public final class ElectricityInstrumentation extends Instrumentation {
    private static final String USER="SYNTHETIC_ELECTRICITY";
    private Context context;
    private final List<Activity> pages=new ArrayList<>();
    private ElectricityActivity activity;
    private SchoolWebActivity recharge;
    private volatile String balance="42.50";
    private volatile boolean unavailable;
    private int checks, requests;
    private interface Work{void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
    private void ui(Work work)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception("UI fixture failed",error[0]);waitForIdleSync();}
    private void owner(String name,boolean pg)throws Exception{
        context.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",pg).commit();
        new SchoolAccount(context).save(pg,name,"",false);AppSession.invalidate(context);
        context.getSharedPreferences("school_session",0).edit().putString("identity",(pg?"pg:":"ug:")+name).commit();
        check(AppSession.valid(context),"synthetic owner established");
    }
    private JSONObject room(String value)throws Exception{
        JSONArray fields=new JSONArray();
        for(String role:new String[]{"elesys","school","area","build","floor","room"})fields.put(new JSONObject().put("role",role).put("id",role)
            .put("value","room".equals(role)?"SYNTHETIC_ROOM_"+value:"SYNTHETIC_"+role)
            .put("title","room".equals(role)?"合成寝室"+value:"school".equals(role)?"奉贤校区":"area".equals(role)?"住宿区":"elesys".equals(role)?"新电费缴纳":"合成"+role));
        return ElectricityParser.room(new JSONObject().put("fields",fields));
    }
    private JSONObject payload(JSONObject room,String value)throws Exception{return new JSONObject().put("status","complete").put("room",room).put("balance",new JSONObject().put("value",value).put("unit","元"));}
    private String html(){
        String body="<!doctype html><meta charset='utf-8'><style>body{font:16px sans-serif;background:#fff;color:#263c38}.weui-cell{padding:16px;border-bottom:1px solid #edf1f0}input{border:0;font-size:16px}</style>";
        String[] names={"电费系统","校区","区域","楼栋","楼层","房间"};String[] ids={"elesys","school","area","build","floor","room"};
        for(int i=0;i<ids.length;i++)body+="<div class='weui-cell'><label>"+names[i]+"：</label><input readonly id='"+ids[i]+"'></div>";
        // The school answers on a second page: 下一步 submits the read-only query.
        body+="<div id='balance'></div><button onclick=\"location.href='/epay/h5/payele/result?room='+encodeURIComponent(document.getElementById('room').getAttribute('data-values')||'')\">下一步</button>"
            +"<button onclick='window.syntheticPayments++'>充值电费</button><script>"
            +"window.syntheticPayments=0;var ids=['elesys','school','area','build','floor','room'],titles=['新电费缴纳','奉贤校区','住宿区','合成楼栋','合成楼层','合成寝室A'];"
            +"var data=[{}],data1=[],data2=[];function jQuery(x){if(Array.isArray(x)){x.each=function(f){x.forEach(function(v,i){f(i,v)})};return x}return {data:function(){return x.picker}}}jQuery.fn={select:function(){}};jQuery.active=0;"
            +"function options(i){return i===5?[{title:'合成寝室A',value:'SYNTHETIC_ROOM_A'},{title:'合成寝室B',value:'SYNTHETIC_ROOM_B'}]:[{title:titles[i],value:'SYNTHETIC_'+ids[i]}]}"
            +"ids.forEach(function(id,i){var input=document.getElementById(id);input.picker={config:{items:i===0?options(0):[]},updateInputValue:function(v,t){input.value=t[0];input.setAttribute('data-values',v[0]);for(var k=i+1;k<ids.length;k++){document.getElementById(ids[k]).value='';document.getElementById(ids[k]).setAttribute('data-values','');document.getElementById(ids[k]).picker.config.items=[];}jQuery.active++;setTimeout(function(){if(i<5){document.getElementById(ids[i+1]).picker.config.items=options(i+1);if(i<2)window['data'+(i+1)]=[{}];}jQuery.active--;},80)}}});"
            +"window.syntheticChoose=function(which){ids.forEach(function(id,i){var input=document.getElementById(id);input.value=i===5?'合成寝室'+which:titles[i];input.setAttribute('data-values',i===5?'SYNTHETIC_ROOM_'+which:'SYNTHETIC_'+id);input.picker.config.items=options(i);});};</script>";
        return body;
    }
    /** The school's result page: room, labelled remaining energy, and the amount we never touch. */
    private String resultHtml(){
        return "<!doctype html><meta charset='utf-8'><style>body{font:16px sans-serif;background:#fff;color:#263c38}.weui-cell{padding:16px;border-bottom:1px solid #edf1f0}input{border:0;font-size:16px}</style>"
            +"<div class='weui-cell'><div>房间</div><div id='roomText'>—</div></div>"
            +"<div class='weui-cell'><div>剩余电量</div><div id='balanceText'>—</div></div>"
            +"<div class='weui-cell'><div>充值</div><input id='amount' placeholder='请输入充值金额'></div>"
            +"<button onclick='window.syntheticPayments++'>充值</button><script>window.syntheticPayments=0;"
            +"var found=/room=([^&]*)/.exec(location.search),room=found?decodeURIComponent(found[1]):'',second=room.indexOf('_B')>0;"
            +"document.getElementById('roomText').textContent=second?'合成寝室B':'合成寝室A';"
            +"document.getElementById('balanceText').textContent=(second?'8.75':'"+balance+"')+'度';</script>";
    }
    @Override public void callActivityOnCreate(Activity page,Bundle state){
        super.callActivityOnCreate(page,state);pages.add(page);
        if(!(page instanceof ElectricityActivity)&&!(page instanceof SchoolWebActivity))return;
        try{
            WebView web=(WebView)field(page,"web");WebViewClient real=web.getWebViewClient();web.stopLoading();
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                    if(request.isForMainFrame())requests++;
                    boolean failed=unavailable&&page instanceof ElectricityActivity;
                    String path=request.getUrl().getPath();
                    String body=failed?"SYNTHETIC_FAILURE":path!=null&&path.endsWith("/result")?resultHtml():html();
                    return new WebResourceResponse("text/html","UTF-8",failed?500:200,failed?"Synthetic failure":"OK",Collections.emptyMap(),new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
                }
                @Override public void onPageStarted(WebView view,String url,Bitmap icon){real.onPageStarted(view,url,icon);}
                @Override public void onPageFinished(WebView view,String url){real.onPageFinished(view,url);}
                @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){real.onReceivedHttpError(view,request,response);}
            });
            if(page instanceof SchoolWebActivity){recharge=(SchoolWebActivity)page;web.loadUrl(SchoolCatalog.ELECTRICITY);}
        }catch(Exception error){throw new RuntimeException(error);}
    }
    private String js(Activity page,String source)throws Exception{
        CountDownLatch ready=new CountDownLatch(1);String[] result={""};
        ui(()->((WebView)field(page,"web")).evaluateJavascript(source,value->{result[0]=value;ready.countDown();}));
        check(ready.await(4,TimeUnit.SECONDS),"page callback completes");return result[0];
    }
    private void open(){activity=(ElectricityActivity)startActivitySync(new Intent(context,ElectricityActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();}
    private void waitReady()throws Exception{
        boolean[] ready={false};for(int i=0;i<100&&!ready[0];i++){ui(()->ready[0]=((Button)field(activity,"saveButton")).isEnabled());if(!ready[0])SystemClock.sleep(100);}
        check(ready[0],"school selector is ready");
    }
    private void waitBalance(String value)throws Exception{
        boolean[] ready={false};for(int i=0;i<160&&!ready[0];i++){
            ui(()->{JSONObject saved=new ElectricityStore(context).read();JSONObject found=saved==null?null:saved.optJSONObject("balance");ready[0]=!(Boolean)field(activity,"refreshing")&&found!=null&&value.equals(found.optString("value"));});
            if(!ready[0])SystemClock.sleep(100);
        }check(ready[0],"latest balance arrives");
    }
    private void capture()throws Exception{
        ui(()->{View root=activity.getWindow().getDecorView();Bitmap image=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));
            try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"SYNTHETIC-electricity.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();});
    }
    @Override public void onStart(){
        Bundle report=new Bundle();int result=Activity.RESULT_OK;boolean owned=false;context=getTargetContext();
        try{
            check(context.getPackageName().endsWith(".pr23test"),"isolated installation required");owned=true;
            new SchoolAccount(context).clear();ElectricityStore.clearAll(context);owner(USER,false);
            ElectricityStore first=new ElectricityStore(context);first.saveRoom(room("A"));first.saveBalance(room("A"),payload(room("A"),"3.00"));
            owner("SYNTHETIC_OTHER",false);check(new ElectricityStore(context).read()==null&&first.read()==null,"accounts are isolated");
            try{first.saveRoom(room("B"));throw new AssertionError("stale save accepted");}catch(IllegalStateException expected){checks++;}
            owner(USER,true);check(new ElectricityStore(context).read()==null,"student types are isolated");
            owner(USER,false);ElectricityStore.clearAll(context);
            Field update=UpdateChecker.class.getDeclaredField("autoChecked");update.setAccessible(true);update.setBoolean(null,true);
            open();waitReady();check((Boolean)field(activity,"choosing"),"first entry opens room selection");
            ui(()->((Button)field(activity,"saveButton")).performClick());SystemClock.sleep(150);
            check(new ElectricityStore(context).read()==null,"incomplete selection is not saved");
            boolean[] filled={false};for(int i=0;i<80&&!filled[0];i++){filled[0]=js(activity,"document.getElementById('area')&&document.getElementById('area').getAttribute('data-values')").contains("SYNTHETIC_area");if(!filled[0])SystemClock.sleep(100);}
            check(filled[0],"school and area are filled automatically");
            check("true".equals(js(activity,"!document.getElementById('room').getAttribute('data-values')")),"build floor room stay empty for the user");
            js(activity,"syntheticChoose('A')");waitBalance("42.5");
            check(((TextView)field(activity,"amount")).getText().toString().contains("42.5"),"balance is shown natively");
            check(((TextView)field(activity,"balanceTitle")).getText().toString().contains("电量"),"school energy unit is labelled");
            check("0".equals(js(activity,"window.syntheticPayments")),"application never clicks recharge on the result page");
            check(((Button)field(activity,"rechargeButton")).isEnabled(),"recharge entry is available");capture();
            balance="49.80";int before=requests;ui(()->((Button)field(activity,"updateButton")).performClick());waitBalance("49.8");check(requests>before,"update button reloads school data");
            ui(()->activity.finish());balance="55.60";open();waitBalance("55.6");check(true,"re-entry automatically refreshes");
            unavailable=true;ui(()->((Button)field(activity,"updateButton")).performClick());
            boolean[] done={false};for(int i=0;i<100&&!done[0];i++){ui(()->done[0]=!(Boolean)field(activity,"refreshing"));if(!done[0])SystemClock.sleep(100);}
            check(done[0]&&new ElectricityStore(context).read().getJSONObject("balance").getString("value").equals("55.6"),"failed refresh preserves old balance");unavailable=false;
            ui(()->call(activity,"chooseRoom",new Class<?>[0]));waitReady();js(activity,"syntheticChoose('B')");waitBalance("8.75");
            check(ElectricityParser.description(new ElectricityStore(context).read().getJSONObject("room")).contains("合成寝室B"),"switching room updates selected dormitory");
            ui(()->call(activity,"chooseRoom",new Class<?>[0]));waitReady();ui(()->activity.onBackPressed());
            check(new ElectricityStore(context).read().getJSONObject("balance").getString("value").equals("8.75"),"cancelling selection preserves original room balance");
            ui(()->((Button)field(activity,"rechargeButton")).performClick());
            for(int i=0;i<100&&recharge==null;i++)SystemClock.sleep(100);
            check(recharge!=null,"recharge opens official school page");
            boolean restored=false;for(int i=0;i<120&&!restored;i++){String value=js(recharge,"document.getElementById('room')&&document.getElementById('room').getAttribute('data-values')");restored=value.contains("SYNTHETIC_ROOM_B");if(!restored)SystemClock.sleep(100);}
            check(restored,"recharge restores saved room");check("0".equals(js(recharge,"window.syntheticPayments")),"application never clicks payment");
            ui(()->recharge.finish());waitBalance("8.75");check(true,"returning from recharge refreshes automatically");
            ui(()->activity.finish());owner("SYNTHETIC_OTHER",true);new ElectricityStore(context).saveRoom(room("A"));owner(USER,false);
            MainActivity main=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ui(()->call(main,"confirmClear",new Class<?>[0]));
            boolean pressed=false;for(int i=0;i<30&&!pressed;i++){
                AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null){for(AccessibilityNodeInfo item:root.findAccessibilityNodeInfosByText("清除")){if("android.widget.Button".contentEquals(item.getClassName())&&item.isClickable())pressed=item.performAction(AccessibilityNodeInfo.ACTION_CLICK);item.recycle();}root.recycle();}if(!pressed)SystemClock.sleep(100);
            }
            check(pressed,"global clear confirmation is actionable");
            for(int i=0;i<50&&!context.getSharedPreferences("electricity_v1",0).getAll().isEmpty();i++)SystemClock.sleep(100);
            check(context.getSharedPreferences("electricity_v1",0).getAll().isEmpty(),"logout clears all dormitories and balances");
            report.putString("result","PASS ElectricityInstrumentation: "+checks+" checks");
        }catch(Throwable failure){result=Activity.RESULT_CANCELED;report.putString("result","FAIL "+android.util.Log.getStackTraceString(failure));}
        finally{if(owned){try{ui(()->{for(Activity page:pages)if(!page.isFinishing())page.finish();});}catch(Exception ignored){}ElectricityStore.clearAll(context);new SchoolAccount(context).clear();}}
        finish(result,report);
    }
}
