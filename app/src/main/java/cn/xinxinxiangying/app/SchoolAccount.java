package cn.xinxinxiangying.app;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Optional device-only credentials, encrypted with a non-exportable Android Keystore key. */
public final class SchoolAccount {
    private static final String ALIAS="xinxin.school.login.v1";
    private final SharedPreferences prefs;private final Context context;
    public SchoolAccount(Context c){context=c.getApplicationContext();prefs=c.getApplicationContext().getSharedPreferences("school_accounts",0);}
    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());return generator.generateKey();
    }
    public String username(boolean pg){return prefs.getString(pg?"pg_user":"ug_user","");}
    public JSONObject remembered(boolean pg){
        try{String value=prefs.getString(pg?"pg_secret":"ug_secret","");if(value.isEmpty())return null;JSONObject box=new JSONObject(value);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(box.getString("iv"),Base64.NO_WRAP)));cipher.updateAAD((pg?"pg":"ug").getBytes("UTF-8"));return new JSONObject(new String(cipher.doFinal(Base64.decode(box.getString("data"),Base64.NO_WRAP)),"UTF-8"));}catch(Exception e){return null;}
    }
    public void save(boolean pg,String username,String password,boolean remember)throws Exception{
        boolean changed=!username.equals(username(pg));SharedPreferences.Editor edit=prefs.edit().putString(pg?"pg_user":"ug_user",username);
        // 用已有学校会话同步时密码为空：保留本机已有密文，只有用户明确取消记住密码才删除。
        if(!password.isEmpty()){
            if(remember){Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD((pg?"pg":"ug").getBytes("UTF-8"));byte[] data=cipher.doFinal(new JSONObject().put("username",username).put("password",password).toString().getBytes("UTF-8"));edit.putString(pg?"pg_secret":"ug_secret",new JSONObject().put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).put("data",Base64.encodeToString(data,Base64.NO_WRAP)).toString());}
            else edit.remove(pg?"pg_secret":"ug_secret");
        }
        if(!edit.commit())throw new IllegalStateException("登录设置保存失败");if(changed)((android.app.NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(77);
    }
    public void forget(boolean pg){prefs.edit().remove(pg?"pg_secret":"ug_secret").commit();}
    public void clear(){AppSession.invalidate(context);prefs.edit().clear().commit();try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS);}catch(Exception ignored){}}
    public String snapshotKey(boolean pg,String service){String scope=scope(pg);return (pg?"pg_":"ug_")+scope+(scope.isEmpty()?"":"_")+service;}
    public String scope(boolean pg){String user=username(pg);if(user.isEmpty())return "";try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(user.getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return "_"+out;}catch(Exception e){throw new IllegalStateException(e);}}
}
