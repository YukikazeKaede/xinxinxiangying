package cn.xinxinxiangying.app;

import org.json.*;
import java.text.*;
import java.util.*;

/** Pure calendar operations shared by views, exports, reminders and widgets. */
public final class TimetableModel {
    private TimetableModel(){}
    /** Default period times (节次 -> HH:mm-HH:mm). Used when no custom times are set. */
    public static final String[] DEFAULT_PERIOD_TIMES = {
        "08:20-09:05", // 第1节
        "09:10-09:55", // 第2节
        "10:15-11:00", // 第3节
        "11:05-11:50", // 第4节
        "13:00-13:45", // 第5节（午间休息后）
        "13:50-14:35", // 第6节
        "14:55-15:40", // 第7节
        "15:45-16:30", // 第8节
        "18:00-18:45", // 第9节（傍晚休息后）
        "18:50-19:35", // 第10节
        "19:40-20:25"  // 第11节
    };
    public static JSONArray defaultPeriodTimes(){
        JSONArray times=new JSONArray();
        for(String entry:DEFAULT_PERIOD_TIMES){String[] parts=entry.split("-");try{times.put(new JSONObject().put("start",parts[0]).put("end",parts[1]));}catch(JSONException e){throw new IllegalStateException(e);}}
        return times;
    }
    public static Calendar date(String date){try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));Date d=f.parse(date);if(!f.format(d).equals(date))return null;Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));c.setTime(d);c.set(Calendar.HOUR_OF_DAY,12);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c;}catch(Exception e){return null;}}
    public static boolean occurs(JSONObject c,int week){JSONArray weeks=c.optJSONArray("weeks");if(weeks!=null){for(int i=0;i<weeks.length();i++)if(weeks.optInt(i)==week)return true;return false;}return week>=c.optInt("startWeek",1)&&week<=c.optInt("endWeek",20)&&!("odd".equals(c.optString("parity"))&&week%2==0||"even".equals(c.optString("parity"))&&week%2!=0);}
    public static void validatePatches(JSONArray patches,int total)throws JSONException{
        if(patches.length()>500)throw new IllegalArgumentException("调课记录过多");
        for(int i=0;i<patches.length();i++){JSONObject p=patches.getJSONObject(i);String kind=p.optString("kind");if(!Arrays.asList("remove","copy","move","swap").contains(kind))throw new IllegalArgumentException("调课类型无效");int week=p.getInt("week"),day=p.getInt("day");if(week<1||week>total||day<1||day>7)throw new IllegalArgumentException("调课日期超出学期");if(!kind.equals("remove")){int tw=p.getInt("targetWeek"),td=p.getInt("targetDay");if(tw<1||tw>total||td<1||td>7||tw==week&&td==day)throw new IllegalArgumentException("调课目标无效");}}
    }
    public static void validateTimes(JSONArray times)throws JSONException{if(times.length()>16)throw new IllegalArgumentException("最多设置16节课");String last="";for(int i=0;i<times.length();i++){JSONObject t=times.getJSONObject(i);String start=t.optString("start"),end=t.optString("end");if(!start.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")||!end.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")||start.compareTo(end)>=0||!last.isEmpty()&&start.compareTo(last)<0)throw new IllegalArgumentException("节次时间须按先后排列，且结束晚于开始");last=end;}}
    public static JSONArray day(JSONObject document,int week,int day){JSONArray result=schedule(document).get(week+":"+day);return result==null?new JSONArray():result;}
    public static Map<String,JSONArray> schedule(JSONObject document){
        try{JSONArray all=document.optJSONArray("courses"),patches=document.optJSONArray("patches");Map<String,JSONArray> days=new HashMap<>();
            int total=document.optInt("totalWeeks",20);for(int w=1;w<=total;w++)for(int d=1;d<=7;d++){JSONArray list=new JSONArray();if(all!=null)for(int i=0;i<all.length();i++){JSONObject c=all.optJSONObject(i);if(c!=null&&c.optInt("weekday")==d&&occurs(c,w))list.put(new JSONObject(c.toString()));}days.put(w+":"+d,list);}
            if(patches!=null)for(int i=0;i<patches.length();i++){JSONObject p=patches.getJSONObject(i);String from=p.getInt("week")+":"+p.getInt("day"),to=p.optInt("targetWeek")+":"+p.optInt("targetDay"),kind=p.getString("kind");JSONArray source=days.get(from);if(source==null)continue;if(kind.equals("remove"))days.put(from,new JSONArray());else if(days.containsKey(to)){JSONArray target=days.get(to);if(kind.equals("swap")){days.put(to,new JSONArray(source.toString()));days.put(from,new JSONArray(target.toString()));}else{JSONArray merged=new JSONArray(target.toString());for(int j=0;j<source.length();j++)merged.put(new JSONObject(source.getJSONObject(j).toString()));days.put(to,merged);if(kind.equals("move"))days.put(from,new JSONArray());}}}
            for(Map.Entry<String,JSONArray> entry:days.entrySet()){String[] pos=entry.getKey().split(":");JSONArray result=entry.getValue();List<JSONObject> sorted=new ArrayList<>();for(int i=0;i<result.length();i++){JSONObject c=result.getJSONObject(i);c.put("displayWeek",Integer.parseInt(pos[0])).put("displayDay",Integer.parseInt(pos[1]));sorted.add(c);}Collections.sort(sorted,(a,b)->Integer.compare(a.optInt("startPeriod"),b.optInt("startPeriod")));JSONArray out=new JSONArray();for(JSONObject c:sorted)out.put(c);entry.setValue(out);}return days;
        }catch(JSONException e){throw new IllegalArgumentException("调课记录无法读取",e);}
    }
    public static int week(JSONObject document,Calendar today){Calendar start=date(document.optString("startDate"));if(start==null)return 1;Calendar end=(Calendar)today.clone();end.set(Calendar.HOUR_OF_DAY,12);end.set(Calendar.MINUTE,0);end.set(Calendar.SECOND,0);end.set(Calendar.MILLISECOND,0);int days=0;while(start.before(end)&&days<420){start.add(Calendar.DATE,1);days++;}return Math.min(document.optInt("totalWeeks",20),days/7+1);}
    public static String weeks(JSONObject c){JSONArray a=c.optJSONArray("weeks");if(a==null)return "第"+c.optInt("startWeek",1)+"–"+c.optInt("endWeek",20)+"周"+("odd".equals(c.optString("parity"))?"（单周）":"even".equals(c.optString("parity"))?"（双周）":"");StringBuilder out=new StringBuilder();int i=0;while(i<a.length()){int first=a.optInt(i),last=first;while(i+1<a.length()&&a.optInt(i+1)==last+1){last=a.optInt(++i);}if(out.length()>0)out.append("、");out.append(first);if(last!=first)out.append("–").append(last);i++;}return "第"+out+"周";}
    private static String escape(String s){return s.replace("\\","\\\\").replace(";","\\;").replace(",","\\,").replace("\r","").replace("\n","\\n");}
    private static void line(StringBuilder out,String line){int n=0;for(int offset=0;offset<line.length();){int cp=line.codePointAt(offset);String value=new String(Character.toChars(cp));int bytes;try{bytes=value.getBytes("UTF-8").length;}catch(Exception e){bytes=value.length();}if(n+bytes>74){out.append("\r\n ");n=1;}out.append(value);n+=bytes;offset+=Character.charCount(cp);}out.append("\r\n");}
    public static String ical(JSONObject document){Calendar start=date(document.optString("startDate"));if(start==null)throw new IllegalArgumentException("请先设置开学日期");JSONArray times=document.optJSONArray("periodTimes");if(times==null||times.length()==0)throw new IllegalArgumentException("请先设置每节课的起止时间");StringBuilder out=new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Xinxinxiangying//Timetable//ZH\r\nCALSCALE:GREGORIAN\r\n");Map<String,JSONArray> schedule=schedule(document);SimpleDateFormat stamp=new SimpleDateFormat("yyyyMMdd\'T\'HHmmss\'Z\'",Locale.ROOT);stamp.setTimeZone(TimeZone.getTimeZone("UTC"));String dtstamp=stamp.format(new Date());SimpleDateFormat fmt=new SimpleDateFormat("yyyyMMdd",Locale.ROOT);fmt.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));for(int w=1;w<=document.optInt("totalWeeks",20);w++)for(int d=1;d<=7;d++){JSONArray courses=schedule.get(w+":"+d);Calendar date=(Calendar)start.clone();date.add(Calendar.DATE,(w-1)*7+d-1);for(int i=0;i<courses.length();i++){JSONObject c=courses.optJSONObject(i);int sp=c.optInt("startPeriod"),ep=c.optInt("endPeriod");if(ep>times.length())throw new IllegalArgumentException("请补全第"+ep+"节课的起止时间");String ds=fmt.format(date.getTime());out.append("BEGIN:VEVENT\r\n");line(out,"UID:"+document.optString("timetableId","local")+"-"+c.optString("id")+"-"+w+"-"+d+"-"+i+"@xinxinxiangying.local");line(out,"DTSTAMP:"+dtstamp);line(out,"DTSTART;TZID=Asia/Shanghai:"+ds+"T"+times.optJSONObject(sp-1).optString("start").replace(":","")+"00");line(out,"DTEND;TZID=Asia/Shanghai:"+ds+"T"+times.optJSONObject(ep-1).optString("end").replace(":","")+"00");line(out,"SUMMARY:"+escape(c.optString("name")));line(out,"LOCATION:"+escape(c.optString("room")));line(out,"DESCRIPTION:"+escape("教师："+c.optString("teacher")+"\n"+c.optString("note")));out.append("END:VEVENT\r\n");}}out.append("END:VCALENDAR\r\n");return out.toString();}
}
