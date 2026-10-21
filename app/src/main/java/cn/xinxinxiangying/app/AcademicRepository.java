package cn.xinxinxiangying.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** 本机保存的考试成绩与学分绩点快照，按学校账号区分。 */
public final class AcademicRepository {
    private static final String STORE="academics_v1";
    private final Context context;
    public AcademicRepository(Context c){context=c.getApplicationContext();}
    private SharedPreferences prefs(){return context.getSharedPreferences(STORE,Context.MODE_PRIVATE);}
    private String key(String service){
        boolean pg=context.getSharedPreferences("school_profile",0).getBoolean("postgraduate",false);
        return (pg?"pg:":"ug:")+new SchoolAccount(context).scope(pg)+"_"+service;
    }
    static String service(String id){return "exams".equals(id)?"exams":"grades";}
    public JSONObject read(String id){
        String body=prefs().getString(key(service(id)),"");
        if(body.isEmpty())return null;
        try{return new JSONObject(body);}catch(Exception e){return null;}
    }
    public void save(String id,JSONObject snapshot)throws Exception{
        prefs().edit().putString(key(service(id)),snapshot.toString()).commit();
    }
    public long importedAt(String id){
        JSONObject saved=read(id);
        return saved==null?0:saved.optLong("importedAt",0);
    }
    public void clear(){prefs().edit().clear().commit();}
    public void clear(String id){prefs().edit().remove(key(service(id))).commit();}
}
