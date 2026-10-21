package cn.xinxinxiangying.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Atomic, per-student-type local timetable; raw pages and login tokens are never stored. */
public final class TimetableRepository {
    private final SharedPreferences data, profile;
    private final Context context;
    public TimetableRepository(Context context) {
        this.context=context.getApplicationContext();
        data=context.getApplicationContext().getSharedPreferences("timetables_v1",Context.MODE_PRIVATE);
        profile=context.getApplicationContext().getSharedPreferences("school_profile",Context.MODE_PRIVATE);
    }
    private String type() { return profile.getBoolean("postgraduate",false)?"pg":"ug"; }
    private String key(String kind){return kind+new SchoolAccount(context).scope("pg".equals(kind));}
    private String prefix(String kind){return "semester:"+key(kind)+":";}
    public synchronized JSONObject read() {
        try {
            String saved=data.getString(key(type()),null);
            if(saved!=null) return new JSONObject(saved);
            return new JSONObject().put("courses",new JSONArray()).put("studentType",type()).put("semester","").put("startDate","").put("totalWeeks",20);
        } catch(JSONException e) { throw new IllegalStateException("本机课表无法读取，请重新导入",e); }
    }
    public synchronized void save(JSONObject input) {
        try {
            String kind=input.optString("studentType",type());
            if(!kind.equals("ug")&&!kind.equals("pg")) throw new IllegalArgumentException("学生类型无效");
            JSONArray courses=input.getJSONArray("courses");
            if(courses.length()>1000) throw new IllegalArgumentException("课程记录过多，请检查识别结果");
            JSONArray clean=new JSONArray(); Set<String> ids=new HashSet<>(); int maxWeek=1;
            for(int i=0;i<courses.length();i++) {
                JSONObject c=courses.getJSONObject(i), out=new JSONObject();
                String name=c.optString("name","").trim(), id=c.optString("id","").trim();
                if(name.isEmpty()||name.length()>200||id.isEmpty()||!ids.add(id)) throw new IllegalArgumentException("课程名称或编号无效");
                int day=c.getInt("weekday"), start=c.getInt("startPeriod"), end=c.getInt("endPeriod"), sw=c.getInt("startWeek"), ew=c.getInt("endWeek");
                if(day<1||day>7||start<1||end<start||end>16||sw<1||ew<sw||ew>60) throw new IllegalArgumentException("课程星期、节次或周次无效");
                String parity=c.optString("parity","all");
                if(!Arrays.asList("all","odd","even").contains(parity)) throw new IllegalArgumentException("课程单双周无效");
                out.put("id",id).put("name",name).put("weekday",day).put("startPeriod",start).put("endPeriod",end).put("startWeek",sw).put("endWeek",ew).put("parity",parity);
                for(String key:new String[]{"teacher","room","note","origin"}) {
                    String value=c.optString(key,""); if(value.length()>1000) throw new IllegalArgumentException("课程文字过长"); out.put(key,value);
                }
                if(c.has("weeks")) {
                    JSONArray weeks=c.getJSONArray("weeks"), copy=new JSONArray(); Set<Integer> seen=new HashSet<>();
                    if(weeks.length()==0||weeks.length()>60) throw new IllegalArgumentException("课程周次列表无效");
                    for(int j=0;j<weeks.length();j++) { int w=weeks.getInt(j); if(w<sw||w>ew||!seen.add(w)) throw new IllegalArgumentException("课程周次列表无效"); copy.put(w); }
                    out.put("weeks",copy);
                }
                maxWeek=Math.max(maxWeek,ew); clean.put(out);
            }
            int total=input.optInt("totalWeeks",Math.max(20,maxWeek));
            if(total<maxWeek||total<1||total>60) throw new IllegalArgumentException("学期周数不能少于已有课程的结束周");
            String date=input.optString("startDate","");
            if(!date.isEmpty()) {
                SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT); fmt.setLenient(false);
                if(!date.matches("\\d{4}-\\d{2}-\\d{2}")||!fmt.format(fmt.parse(date)).equals(date)) throw new IllegalArgumentException("开学日期无效");
                Calendar calendar=Calendar.getInstance(); calendar.setTime(fmt.parse(date));
                if(calendar.get(Calendar.DAY_OF_WEEK)!=Calendar.MONDAY) throw new IllegalArgumentException("请选择第一教学周的周一");
            }
            String source=input.optString("sourceUrl","");
            if(!source.isEmpty()&&!SchoolCatalog.isAcademicUrl(source)) throw new IllegalArgumentException("课表来源不是学校教务系统");
            // Store origin and path only: exclude any ticket/session/query parameters.
            if(!source.isEmpty()) { java.net.URI u=java.net.URI.create(source); source=new java.net.URI(u.getScheme(),null,u.getHost(),-1,(u.getPath()==null?"":u.getPath()).replaceAll(";[^/]*",""),null,null).toString(); }
            String semester=input.optString("semester",""); if(semester.length()>120) throw new IllegalArgumentException("学期名称过长");
            JSONObject out=new JSONObject().put("schemaVersion",1).put("courses",clean).put("studentType",kind).put("semester",semester)
                .put("startDate",date).put("totalWeeks",total).put("sourceUrl",source).put("importedAt",input.optLong("importedAt",System.currentTimeMillis()));
            String id=input.optString("timetableId","");JSONObject same=findSemester(semester,kind);
            if(id.isEmpty()&&same!=null)id=same.optString("timetableId","");if(id.isEmpty())id=UUID.randomUUID().toString();
            if(!id.matches("[a-zA-Z0-9-]{1,80}"))throw new IllegalArgumentException("课表编号无效");out.put("timetableId",id);
            JSONArray deleted=input.optJSONArray("deletedCourseIds");if(deleted!=null){if(deleted.length()>2000)throw new IllegalArgumentException("删除记录过多");JSONArray copy=new JSONArray();for(int i=0;i<deleted.length();i++){String value=deleted.getString(i);if(value.length()>100)throw new IllegalArgumentException("删除记录无效");copy.put(value);}out.put("deletedCourseIds",copy);}
            JSONArray patches=input.optJSONArray("patches");if(patches!=null){TimetableModel.validatePatches(patches,total);out.put("patches",new JSONArray(patches.toString()));}
            JSONArray times=input.optJSONArray("periodTimes");if(times!=null){TimetableModel.validateTimes(times);out.put("periodTimes",new JSONArray(times.toString()));}
            for(String field:new String[]{"viewMode","palette","wallpaper"}){String value=input.optString(field,"");if(value.length()>600)throw new IllegalArgumentException("课表外观设置无效");out.put(field,value);}
            out.put("smartWeekend",input.optBoolean("smartWeekend",false)).put("showStatus",input.optBoolean("showStatus",false)).put("cardAlpha",Math.max(50,Math.min(255,input.optInt("cardAlpha",255))));
            out.put("visibleDays",input.optInt("visibleDays",7)==5?5:7);
            out.put("reminderMinutes",Math.max(-1,Math.min(120,input.optInt("reminderMinutes",-1))));
            SharedPreferences.Editor edit=data.edit();String previous=data.getString(key(kind),null);
            if(previous!=null){JSONObject old=new JSONObject(previous);String oldId=old.optString("timetableId","");if(oldId.isEmpty()){oldId=UUID.randomUUID().toString();old.put("timetableId",oldId);}edit.putString(prefix(kind)+oldId,old.toString());}
            edit.putString(key(kind),out.toString()).putString(prefix(kind)+id,out.toString());
            if(!edit.commit())throw new IllegalStateException("课表保存失败，原课表已保留");
            TimetableWidget.update(context);CourseReminder.schedule(context);
        } catch(JSONException|java.text.ParseException|java.net.URISyntaxException e) { throw new IllegalArgumentException("课表数据格式无效，原课表已保留",e); }
    }
    public synchronized JSONObject findSemester(String name){return findSemester(name,type());}
    private JSONObject findSemester(String name,String kind){try{String active=data.getString(key(kind),null);if(active!=null){JSONObject doc=new JSONObject(active);if(name.equals(doc.optString("semester")))return doc;}for(Map.Entry<String,?> e:data.getAll().entrySet())if(e.getKey().startsWith(prefix(kind))&&e.getValue() instanceof String){JSONObject doc=new JSONObject((String)e.getValue());if(name.equals(doc.optString("semester")))return doc;}return null;}catch(JSONException e){throw new IllegalStateException("学期记录无法读取",e);}}
    public synchronized JSONArray semesters(){JSONArray list=new JSONArray();try{JSONObject active=read();Set<String> ids=new HashSet<>();if(!active.optString("timetableId","").isEmpty()){list.put(active);ids.add(active.optString("timetableId"));}for(Map.Entry<String,?> e:data.getAll().entrySet())if(e.getKey().startsWith(prefix(type()))&&e.getValue() instanceof String){JSONObject doc=new JSONObject((String)e.getValue());if(ids.add(doc.optString("timetableId")))list.put(doc);}if(list.length()==0&&active.getJSONArray("courses").length()>0)list.put(active);return list;}catch(JSONException e){throw new IllegalStateException("学期列表无法读取",e);}}
    public synchronized void select(String id){String value=data.getString(prefix(type())+id,null);if(value==null)throw new IllegalArgumentException("学期不存在");if(!data.edit().putString(key(type()),value).commit())throw new IllegalStateException("切换学期失败");TimetableWidget.update(context);CourseReminder.schedule(context);}
    public synchronized void delete(String id){try{SharedPreferences.Editor edit=data.edit().remove(prefix(type())+id);if(id.equals(read().optString("timetableId")))edit.remove(key(type()));if(!edit.commit())throw new IllegalStateException("删除学期失败");TimetableWidget.update(context);CourseReminder.schedule(context);}catch(RuntimeException e){throw e;}}
    public synchronized void clear(){JSONObject active=read();SharedPreferences.Editor edit=data.edit().remove(key(type()));if(!active.optString("timetableId","").isEmpty())edit.remove(prefix(type())+active.optString("timetableId"));if(!edit.commit())throw new IllegalStateException("课表清除失败");TimetableWidget.update(context);CourseReminder.schedule(context);}

}
