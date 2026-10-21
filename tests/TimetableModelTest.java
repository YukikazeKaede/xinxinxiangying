import cn.xinxinxiangying.app.TimetableModel;
import org.json.*;
import java.util.*;
public class TimetableModelTest{
    static int count;static void check(boolean ok,String text){count++;if(!ok)throw new AssertionError(text);}
    static JSONObject course(String id,int day,int start,String parity)throws Exception{return new JSONObject().put("id",id).put("name","课程,一;\\n长文本").put("weekday",day).put("startPeriod",start).put("endPeriod",start+1).put("startWeek",1).put("endWeek",4).put("parity",parity);}
    static JSONObject patch(String kind,int week,int day,int tw,int td)throws Exception{return new JSONObject().put("kind",kind).put("week",week).put("day",day).put("targetWeek",tw).put("targetDay",td);}
    public static void main(String[] args)throws Exception{
        JSONObject a=course("a",1,1,"odd"),b=course("b",2,3,"all");JSONObject doc=new JSONObject().put("courses",new JSONArray().put(a).put(b)).put("totalWeeks",4).put("startDate","2026-09-07").put("semester","测试");
        check(TimetableModel.day(doc,1,1).length()==1,"odd week");check(TimetableModel.day(doc,2,1).length()==0,"even skip");
        a.put("weeks",new JSONArray().put(2).put(4));check(TimetableModel.occurs(a,2)&&!TimetableModel.occurs(a,1),"discrete list authoritative");a.remove("weeks");
        doc.put("patches",new JSONArray().put(patch("copy",1,1,1,6)));check(TimetableModel.day(doc,1,6).length()==1&&TimetableModel.day(doc,1,1).length()==1,"copy preserves source");
        doc.put("patches",new JSONArray().put(patch("move",1,1,2,3)));check(TimetableModel.day(doc,1,1).length()==0&&TimetableModel.day(doc,2,3).length()==1,"move across weeks");check(TimetableModel.day(doc,2,3).getJSONObject(0).getInt("displayDay")==3,"actual destination weekday");
        doc.put("patches",new JSONArray().put(patch("swap",1,1,1,2)));check(TimetableModel.day(doc,1,1).getJSONObject(0).getString("id").equals("b"),"swap source");check(TimetableModel.day(doc,1,2).getJSONObject(0).getString("id").equals("a"),"swap target");
        doc.put("patches",new JSONArray().put(patch("copy",1,1,1,6)).put(patch("remove",1,6,0,0)));check(TimetableModel.day(doc,1,6).length()==0,"patch ordering");
        try{TimetableModel.validatePatches(new JSONArray().put(patch("move",1,1,1,1)),4);throw new AssertionError("same source target");}catch(IllegalArgumentException e){count++;}
        try{TimetableModel.validatePatches(new JSONArray().put(patch("copy",1,1,5,1)),4);throw new AssertionError("out of semester");}catch(IllegalArgumentException e){count++;}
        JSONArray times=new JSONArray().put(new JSONObject().put("start","08:00").put("end","08:45")).put(new JSONObject().put("start","08:50").put("end","09:35")).put(new JSONObject().put("start","10:00").put("end","10:45")).put(new JSONObject().put("start","10:50").put("end","11:35"));TimetableModel.validateTimes(times);count++;
        try{TimetableModel.validateTimes(new JSONArray().put(new JSONObject().put("start","24:00").put("end","25:00")));throw new AssertionError("bad time");}catch(IllegalArgumentException e){count++;}
        doc.put("periodTimes",times).put("patches",new JSONArray());String ics=TimetableModel.ical(doc);check(ics.contains("DTSTART;TZID=Asia/Shanghai:20260907T080000"),"Shanghai calendar start");check(ics.contains("DTEND;TZID=Asia/Shanghai:20260907T093500"),"last period end");check(ics.contains("SUMMARY:课程\\,一\\;\\\\n"),"RFC text escaping");check(ics.split("BEGIN:VEVENT",-1).length-1==6,"odd recurrence plus all-week courses");
        doc.put("patches",new JSONArray().put(patch("move",1,1,1,6)));ics=TimetableModel.ical(doc);check(ics.contains("20260912T080000")&&!ics.contains("20260907T080000"),"export respects patches");
        for(String line:ics.split("\\r\\n"))check(line.getBytes("UTF-8").length<=75,"folded UTF-8 lines");
        check(TimetableModel.date("2026-02-30")==null,"invalid date");check(TimetableModel.weeks(new JSONObject().put("weeks",new JSONArray().put(1).put(2).put(3).put(5))).equals("第1–3、5周"),"compact weeks");
        check(TimetableModel.week(doc,TimetableModel.date("2026-09-14"))==2,"calendar week");System.out.println("PASS "+count+" timetable model assertions");
    }
}