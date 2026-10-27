package cn.xinxinxiangying.app;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONObject;

/** Native school-account entry; credentials are sent only through the school's own form, and CAS sessions are reused first. */
public final class SchoolLoginActivity extends Activity {
    private boolean pg, openingWeb;
    private long authFlowId;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);CampusUi.window(this);
        authFlowId=getIntent().getLongExtra("authFlowId",0);
        if(state!=null&&!SchoolSession.reauthActive())authFlowId=0;
        if(authFlowId==0){
            if(!SchoolSession.beginReauth(this,getIntent().getStringExtra("service"))){finish();return;}
            authFlowId=SchoolSession.currentReauthId();getIntent().putExtra("authFlowId",authFlowId);
        }else if(authFlowId!=SchoolSession.currentReauthId()){finish();return;}
        pg=getSharedPreferences("school_profile",0).getBoolean("postgraduate",false);SchoolAccount account=new SchoolAccount(this);
        JSONObject remembered=account.remembered(pg);
        if(AppSession.valid(this)&&remembered!=null&&!getIntent().getBooleanExtra("edit",false)){open(remembered.optString("username"),remembered.optString("password"),true,false);return;}
        if(AppSession.valid(this)&&!account.username(pg).isEmpty()&&!getIntent().getBooleanExtra("edit",false)){open(account.username(pg),"",false,false);return;}
        LinearLayout root=CampusUi.column(this);root.setBackground(CampusUi.wash(true));CampusUi.insets(root);
        ScrollView scroll=new ScrollView(this);LinearLayout form=CampusUi.column(this);int d=CampusUi.dp(this,24);form.setPadding(d,CampusUi.dp(this,12),d,d);form.setBackgroundColor(0xeeffffff);scroll.addView(form);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));setContentView(root);
        if(AppSession.valid(this)){Button back=CampusUi.button(this,"返回",false,v->finish());form.addView(back,new LinearLayout.LayoutParams(-2,CampusUi.dp(this,44)));}
        TextView title=CampusUi.text(this,"学校账号登录",23,CampusUi.INK);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setPadding(0,d,0,CampusUi.dp(this,12));form.addView(title);
        TextView hint=CampusUi.text(this,"上海应用技术大学 · "+(pg?"研究生":"本科生")+"\n账号密码与学校统一认证一致，验证通过后才能使用。",14,CampusUi.MUTED);hint.setLineSpacing(CampusUi.dp(this,5),1);form.addView(hint);
        String message=getIntent().getStringExtra("message");if(message!=null&&!message.isEmpty()){TextView notice=CampusUi.text(this,message,13,0xffa14e36);notice.setPadding(0,d/2,0,d/2);form.addView(notice);}
        Button studentType=CampusUi.button(this,pg?"当前身份：研究生，点击切换":"当前身份：本科生，点击切换",false,v->{getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",!pg).commit();AppSession.invalidate(this);getIntent().putExtra("edit",true).putExtra("authFlowId",0L);recreate();});form.addView(studentType,new LinearLayout.LayoutParams(-1,CampusUi.dp(this,44)));
        TextView userLabel=CampusUi.text(this,"学号",13,CampusUi.INK);userLabel.setPadding(0,d,0,CampusUi.dp(this,7));form.addView(userLabel);
        EditText user=new EditText(this);user.setSingleLine(true);user.setHint("请输入学校学号");user.setText(account.username(pg));user.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);user.setTextSize(16);user.setTextColor(CampusUi.INK);user.setPadding(d/2,d/2,d/2,d/2);user.setBackground(CampusUi.shape(this,CampusUi.BG,8));if(android.os.Build.VERSION.SDK_INT>=26)user.setAutofillHints(View.AUTOFILL_HINT_USERNAME);form.addView(user);
        TextView passwordLabel=CampusUi.text(this,"学校密码",13,CampusUi.INK);passwordLabel.setPadding(0,d,0,CampusUi.dp(this,7));form.addView(passwordLabel);
        EditText password=new EditText(this);password.setSingleLine(true);password.setSaveEnabled(false);password.setTextSize(16);password.setTextColor(CampusUi.INK);password.setPadding(d/2,d/2,d/2,d/2);password.setBackground(CampusUi.shape(this,CampusUi.BG,8));password.setHint("请输入学校密码");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);if(android.os.Build.VERSION.SDK_INT>=26)password.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);form.addView(password);
        CheckBox remember=new CheckBox(this);remember.setText("在本机加密记住密码");remember.setTextSize(13);remember.setTextColor(CampusUi.INK);remember.setPadding(0,CampusUi.dp(this,15),0,CampusUi.dp(this,15));remember.setChecked(account.remembered(pg)!=null);form.addView(remember);
        Button login=CampusUi.button(this,"登录",true,v->{String name=user.getText().toString().trim(),secret=password.getText().toString();if(name.isEmpty()){user.setError("请输入学号");return;}if(secret.isEmpty()){password.setError("请输入学校密码");return;}if(name.length()>100||secret.length()>512){password.setError("账号或密码过长");return;}((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(password.getWindowToken(),0);password.setText("");open(name,secret,remember.isChecked(),true);});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,CampusUi.dp(this,52));lp.topMargin=CampusUi.dp(this,15);form.addView(login,lp);
        if(AppSession.valid(this)&&!account.username(pg).isEmpty()){Button session=CampusUi.button(this,"使用已有学校登录状态同步",false,v->open(account.username(pg),"",false,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,CampusUi.dp(this,48));p.topMargin=CampusUi.dp(this,12);form.addView(session,p);}
        TextView privacy=CampusUi.text(this,"密码仅用于学校认证。勾选记住密码后，使用此设备的系统密钥加密保存。登录成功后优先复用学校会话；学校要求验证码或二次验证时，请完成必要验证，应用会继续原来的访问。",12,CampusUi.MUTED);privacy.setLineSpacing(CampusUi.dp(this,4),1);privacy.setPadding(0,d,0,0);form.addView(privacy);
    }
    @Override public void onBackPressed(){SchoolSession.endReauth(this,authFlowId);if(AppSession.valid(this))super.onBackPressed();else finishAffinity();}
    @Override protected void onDestroy(){if(isFinishing()&&!openingWeb)SchoolSession.endReauth(this,authFlowId);super.onDestroy();}
    /**
     * 只有明确换号、首次登录或调用方要求清理时才清 Cookie。
     * 同一账号再次登录不清会话，先让学校统一认证复用已有登录态。
     */
    private void open(String username,String password,boolean remember,boolean explicit){
        SchoolAccount account=new SchoolAccount(this);
        String known=account.username(pg);
        boolean switching=explicit&&!known.isEmpty()&&!username.isEmpty()&&!known.equals(username);
        boolean clear=getIntent().getBooleanExtra("clearSession",false)||switching||known.isEmpty();
        if(switching)AppSession.invalidate(this);
        String service=getIntent().getStringExtra("service");if(service==null||service.isEmpty())service="timetable";
        if(!SchoolSession.reauthActive())SchoolSession.beginReauth(this,service);
        authFlowId=SchoolSession.currentReauthId();
        Intent target=new Intent(this,SchoolWebActivity.class)
            .putExtra("service",service)
            .putExtra("postgraduate",pg)
            .putExtra("autoSync",true)
            .putExtra("username",username)
            .putExtra("password",password)
            .putExtra("remember",remember)
            .putExtra("clearSession",clear)
            .putExtra("returnService",service)
            .putExtra("authFlowId",authFlowId)
            .putExtra("sessionOnly",!SchoolCatalog.isAcademic(service)&&!"timetable".equals(service));
        if("timetable".equals(service))target.putExtra("syncAll",true);
        if(SchoolCatalog.isAcademic(service)&&!"timetable".equals(service))target.putExtra("academicSync",true);
        startActivity(target);openingWeb=true;finish();
    }
}
