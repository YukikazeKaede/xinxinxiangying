package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.WebView;
import cn.xinxinxiangying.app.*;
import org.json.*;
import java.util.concurrent.*;

/** Read-only timing probe. Never reads QR contents, captures pages, pays, or changes accounts. */
public final class PaymentCodeTimingInstrumentation extends Instrumentation {
    private SchoolWebActivity page;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle report=new Bundle();int result=Activity.RESULT_OK;long started=SystemClock.elapsedRealtime(),ready=-1,graphic=-1;
        try{
            if(!AppSession.valid(getTargetContext())){report.putString("state","login-required");return;}
            page=(SchoolWebActivity)startActivitySync(new Intent(getTargetContext(),SchoolWebActivity.class).putExtra("service","code").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            java.lang.reflect.Field f=SchoolWebActivity.class.getDeclaredField("web");f.setAccessible(true);WebView web=(WebView)f.get(page);
            if(web==null){report.putString("state","login-required");return;}
            String state="timeout";
            while(SystemClock.elapsedRealtime()-started<30000&&!page.isFinishing()){
                CountDownLatch done=new CountDownLatch(1);String[] value={"0"};
                runOnMainSync(()->web.evaluateJavascript("(function(){if(document.querySelector('input[type=password]'))return 3;if(location.origin!=='https://ecard.sit.edu.cn'||location.pathname!=='/epay/h5/v5qrcode')return 0;var nodes=document.querySelectorAll('[id*=qrcode i] canvas,[id*=qrcode i] img,[class*=qrcode i] canvas,[class*=qrcode i] img');for(var i=0;i<nodes.length;i++){var n=nodes[i],r=n.getBoundingClientRect();if(r.width>60&&r.height>60&&(n.tagName==='CANVAS'?n.width>0:n.complete&&n.naturalWidth>0))return 2;}return document.readyState==='complete'?1:0;})()",s->{value[0]=s;done.countDown();}));
                if(!done.await(2,TimeUnit.SECONDS)){state="probe-timeout";break;}
                if("3".equals(value[0])){state="school-login-required";break;}
                long elapsed=SystemClock.elapsedRealtime()-started;
                if(("1".equals(value[0])||"2".equals(value[0]))&&ready<0)ready=elapsed;
                if("2".equals(value[0])){graphic=elapsed;state="graphic-candidate";break;}
                SystemClock.sleep(200);
            }
            report.putString("state",state);report.putLong("pageReadyMs",ready);report.putLong("graphicCandidateMs",graphic);
            report.putString("meaning","Graphic presence does not prove code freshness or cashier acceptance.");
        }catch(Throwable e){report.putString("state","probe-failed-"+e.getClass().getSimpleName());result=Activity.RESULT_CANCELED;}
        finally{if(page!=null)runOnMainSync(()->page.finish());finish(result,report);}
    }
}
