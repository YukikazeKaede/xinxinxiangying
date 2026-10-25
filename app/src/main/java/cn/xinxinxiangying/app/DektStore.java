package cn.xinxinxiangying.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** 本机保存的第二课堂学分快照，按学校账号区分。 */
public final class DektStore {
    private static final String STORE="dekt_v1";
    private final Context context;
    private final String owner;
    public DektStore(Context c){context=c.getApplicationContext();owner=key();}
    private SharedPreferences prefs(){return context.getSharedPreferences(STORE,Context.MODE_PRIVATE);}
    private String key(){
        boolean pg=context.getSharedPreferences("school_profile",0).getBoolean("postgraduate",false);
        return (pg?"pg:":"ug:")+new SchoolAccount(context).scope(pg)+"_second";
    }
    public JSONObject read(){
        if(!isCurrent())return null;
        String body=prefs().getString(owner,"");
        if(body.isEmpty())return null;
        try{return new JSONObject(body);}catch(Exception e){return null;}
    }
    public boolean isCurrent(){return AppSession.valid(context)&&owner.equals(key());}
    public void save(JSONObject snapshot)throws Exception{
        if(!isCurrent())throw new IllegalStateException("账号已变更，请重新读取");
        if(!prefs().edit().putString(owner,snapshot.toString()).commit())throw new IllegalStateException("保存失败，请重试");
    }
    public long importedAt(){JSONObject saved=read();return saved==null?0:saved.optLong("importedAt",0);}
    public void clear(){prefs().edit().remove(owner).commit();}
}
