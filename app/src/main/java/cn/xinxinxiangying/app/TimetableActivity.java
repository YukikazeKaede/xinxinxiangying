package cn.xinxinxiangying.app;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
/** Compatibility route; all tabs now live in MainActivity. */
public final class TimetableActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);if(!AppSession.require(this))return;Intent i=new Intent(this,MainActivity.class).putExtra("tab",0).putExtra("showSettings",getIntent().getBooleanExtra("showSettings",false)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);finish();overridePendingTransition(0,0);}
}
