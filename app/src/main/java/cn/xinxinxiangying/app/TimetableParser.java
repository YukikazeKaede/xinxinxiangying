package cn.xinxinxiangying.app;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Conservative import adapters. kbList keys are fixture-backed candidates, not a verified SIT contract. */
public final class TimetableParser {
    private TimetableParser() {}
    private static final Pattern PERIOD = Pattern.compile("(?<![0-9])(\\d{1,2})(?:\\s*[-~—至]\\s*(\\d{1,2}))?\\s*节");
    private static final Pattern WEEK = Pattern.compile("([0-9\\s,，、;；\\-~—至]+)\\s*周\\s*(?:[（(]\\s*([单双])(?:周)?\\s*[）)])?");
    private static final Pattern DAY = Pattern.compile("(?:星期|周)([一二三四五六日天1-7])");

    public static JSONObject parse(JSONObject extraction) throws JSONException {
        if (extraction == null) throw new JSONException("提取结果为空");
        LinkedHashMap<String,JSONObject> found = new LinkedHashMap<>();
        JSONArray warnings = new JSONArray();
        JSONArray raw = extraction.optJSONArray("raw");
        if (raw != null) for (int i=0;i<raw.length();i++) {
            JSONObject carrier = raw.optJSONObject(i);
            if (carrier != null) readRaw(carrier.opt("data"), found);
        }
        // 按周次页课程块带完整 data-* 字段，属于明确课程记录，优先于通用 DOM 推测。
        JSONArray lessons = extraction.optJSONArray("lessons");
        if (found.isEmpty() && lessons != null) for (int i=0;i<lessons.length();i++) {
            JSONObject lesson = lessons.optJSONObject(i);
            if (lesson == null) throw new JSONException("第"+(i+1)+"条课程记录格式错误");
            readLesson(lesson, found);
        }
        // Explicit blocks carry reliable DOM coordinates; never infer weekday from array order.
        JSONArray blocks = extraction.optJSONArray("blocks");
        if (found.isEmpty() && blocks != null) for (int i=0;i<blocks.length();i++) {
            JSONObject block = blocks.optJSONObject(i);
            if (block == null) throw new JSONException("第"+(i+1)+"个课程块格式错误");
            addBlock(block, found);
        }
        JSONArray tables = extraction.optJSONArray("tables");
        if (found.isEmpty() && tables != null) for (int i=0;i<tables.length();i++) readTable(tables.getJSONObject(i), found);
        if (found.isEmpty()) throw new JSONException("未识别到完整课表：必须明确课程名、星期、节次和周次，请打开个人课表后重试");
        JSONArray courses = new JSONArray(); for(JSONObject course:found.values()) courses.put(course);
        warnings.put("导入结果须与学校课表核对；未推测学期开始日期。");
        JSONArray failedWeeks = extraction.optJSONArray("failedWeeks");
        if (failedWeeks == null) failedWeeks = new JSONArray();
        if (failedWeeks.length() > 0) warnings.put("第"+failedWeeks.join("、")+"周未抓取成功，请稍后在课表页重新同步核对。");
        return new JSONObject().put("courses",courses).put("semester",extraction.optString("semester","")).put("warnings",warnings)
            .put("termStart",extraction.optString("termStart","")).put("termWeeks",extraction.optInt("termWeeks",0)).put("failedWeeks",failedWeeks);
    }

    private static void readRaw(Object data, Map<String,JSONObject> found) throws JSONException {
        if (data instanceof JSONObject) {
            JSONObject object=(JSONObject)data;
            if(object.has("kcmc")) readCourseRow(object,found);
            JSONArray list=object.has("kbList")?object.getJSONArray("kbList"):null;
            if(list!=null) for(int i=0;i<list.length();i++) {
                readCourseRow(list.getJSONObject(i),found);
            }
            // Common response envelope only; do not guess research-student metadata/time-slot merges.
            Object nested=object.opt("data"); if(nested instanceof JSONObject || nested instanceof JSONArray) readRaw(nested,found);
        } else if(data instanceof JSONArray) {
            JSONArray list=(JSONArray)data;
            for(int i=0;i<list.length();i++) readRaw(list.opt(i),found);
        }
    }
    private static void readCourseRow(JSONObject row,Map<String,JSONObject> found) throws JSONException {
        String name=row.optString("kcmc","").trim();
        if(name.isEmpty()) throw new JSONException("课程缺少 kcmc");
        // xqjmc exists in the supplied AOT string evidence; xqj remains a fixture candidate.
        int day=row.has("xqj")?integer(row.opt("xqj"),"星期 xqj"):parseDayName(row.optString("xqjmc",""));
        int[] periods=parsePeriods(row.optString("jcs","").trim());
        put(found,base(name,row.optString("xm",""),row.optString("cdmc",""),day,periods[0],periods[1],row.optString("zcd", "")));
    }
    /** 按周次页 div.lesson 上的 data-* 记录：字段由学校页面显式标注，不做文字猜测。 */
    private static void readLesson(JSONObject row,Map<String,JSONObject> found) throws JSONException {
        String name=row.optString("kcmc","").trim();
        if(name.isEmpty()) throw new JSONException("课程缺少 kcmc");
        int day=integer(row.opt("xqj"),"星期 xqj");
        int[] periods=parsePeriods(row.optString("jcor","").trim());
        put(found,base(name,row.optString("jsxm",""),row.optString("cdmc",""),day,periods[0],periods[1],row.optString("zcd","")));
    }
    private static int parseDayName(String value) throws JSONException {
        String s=value.trim();
        if(s.matches("[1-7]")) return Integer.parseInt(s);
        Matcher match=DAY.matcher(s);
        if(match.matches())return dayValue(match.group(1));
        throw new JSONException("缺少或无效的星期 xqjmc："+value);
    }

    private static int integer(Object value,String field) throws JSONException {
        if(value==null || value==JSONObject.NULL || !String.valueOf(value).matches("\\d{1,2}")) throw new JSONException("缺少或无效的"+field);
        return Integer.parseInt(String.valueOf(value));
    }
    private static int[] parsePeriods(String source) throws JSONException {
        String value=source.replaceAll("\\s+","").replaceAll("节$","");
        Matcher m=Pattern.compile("^(\\d{1,2})(?:[-~—至](\\d{1,2}))?$").matcher(value);
        if(!m.matches()) throw new JSONException("无法判定连续节次："+source);
        int start=Integer.parseInt(m.group(1)),end=m.group(2)==null?start:Integer.parseInt(m.group(2));
        if(start<1 || end>16 || start>end) throw new JSONException("节次范围须为 1–16 且开始不晚于结束");
        return new int[]{start,end};
    }
    private static JSONObject base(String name,String teacher,String room,int day,int start,int end,String weekText) throws JSONException {
        if(name.trim().isEmpty()) throw new JSONException("课程名为空");
        if(day<1 || day>7) throw new JSONException("星期范围须为 1–7");
        if(start<1 || end>16 || start>end) throw new JSONException("节次范围须为 1–16 且开始不晚于结束");
        TreeSet<Integer> weeks=parseWeeks(weekText);
        String parity=weekText.contains("单")?"odd":weekText.contains("双")?"even":"all";
        JSONArray explicit=new JSONArray(); for(int week:weeks) explicit.put(week);
        return new JSONObject().put("name",name.trim()).put("teacher",teacher.trim()).put("room",room.trim())
            .put("weekday",day).put("startPeriod",start).put("endPeriod",end)
            .put("startWeek",weeks.first()).put("endWeek",weeks.last()).put("parity",parity).put("weeks",explicit).put("note","");
    }
    private static TreeSet<Integer> parseWeeks(String source) throws JSONException {
        if(source==null || source.trim().isEmpty()) throw new JSONException("缺少明确周次，不能自动填充整个学期");
        String value=source.replaceAll("\\s+","").replace("（","(").replace("）",")");
        boolean odd=value.contains("单"),even=value.contains("双");
        if(odd && even) throw new JSONException("周次同时包含单双标记");
        // 真实教务周次按段重复“周”字："1-7周（单）,11-15周（单）"；也有单周写法"第6周"，去掉周/第再校验范围。
        value=value.replaceAll("\\((?:单|双)周?\\)","").replace("周","").replace("第","");
        if(!value.matches("\\d{1,2}(?:[-~—至]\\d{1,2})?(?:[,，、;；]\\d{1,2}(?:[-~—至]\\d{1,2})?)*"))
            throw new JSONException("无法判定周次："+source);
        TreeSet<Integer> weeks=new TreeSet<>();
        for(String part:value.split("[,，、;；]")) {
            String[] span=part.split("[-~—至]"); int start=Integer.parseInt(span[0]),end=span.length==1?start:Integer.parseInt(span[1]);
            if(start<1 || end>60 || start>end) throw new JSONException("周次范围须为 1–60 且开始不晚于结束");
            for(int n=start;n<=end;n++) if((!odd&&!even)||(odd&&n%2==1)||(even&&n%2==0)) weeks.add(n);
        }
        if(weeks.isEmpty()) throw new JSONException("周次与单双周条件无交集");
        return weeks;
    }
    private static void put(Map<String,JSONObject> found,JSONObject c) throws JSONException {
        String canonical=c.getString("name")+"\u001f"+c.getString("teacher")+"\u001f"+c.getString("room")+"\u001f"+c.getInt("weekday")+"\u001f"+c.getInt("startPeriod")+"\u001f"+c.getInt("endPeriod")+"\u001f"+c.getJSONArray("weeks").toString();
        String id=UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8)).toString();
        c.put("id",id); found.put(id,c);
    }

    private static void addBlock(JSONObject block,Map<String,JSONObject> found) throws JSONException {
        String text=block.optString("text","").trim();
        if(text.isEmpty()) return;
        Matcher week=WEEK.matcher(text), period=PERIOD.matcher(text), dayMatcher=DAY.matcher(text);
        if(!week.find()) throw new JSONException("课程块缺少明确周次："+shortText(text));
        String weekText=week.group();
        if(week.find()) throw new JSONException("一个课程块含多组周次，需拆分后导入："+shortText(text));
        int day=block.has("weekday")?integer(block.opt("weekday"),"星期"):dayMatcher.find()?dayValue(dayMatcher.group(1)):0;
        int start,end;
        if(block.has("startPeriod") && block.has("endPeriod")) {
            start=integer(block.opt("startPeriod"),"开始节次");end=integer(block.opt("endPeriod"),"结束节次");
        } else if(period.find()) {start=Integer.parseInt(period.group(1));end=period.group(2)==null?start:Integer.parseInt(period.group(2));}
        else throw new JSONException("课程块缺少明确节次："+shortText(text));
        String name="",teacher="",room=""; List<String> notes=new ArrayList<>();
        for(String original:text.split("[\\r\\n]+")) {
            String line=original.trim(); if(line.isEmpty()) continue;
            if(line.matches("^(?:教师|老师|任课教师)\\s*[:：].*")) {teacher=line.replaceFirst("^[^:：]+[:：]\\s*","");continue;}
            if(line.matches("^(?:教室|地点|上课地点)\\s*[:：].*")) {room=line.replaceFirst("^[^:：]+[:：]\\s*","");continue;}
            if(WEEK.matcher(line).matches() || PERIOD.matcher(line).matches() || DAY.matcher(line).matches()) continue;
            if(WEEK.matcher(line).find()) throw new JSONException("周次行含无法判定的附加内容："+shortText(line));
            if(name.isEmpty()) name=line;
            else if(teacher.isEmpty() && line.endsWith("老师")) teacher=line;
            else notes.add(line);
        }
        if(name.endsWith("老师")) throw new JSONException("课程块缺少明确课程名");
        JSONObject c=base(name,teacher,room,day,start,end,weekText);
        StringBuilder note=new StringBuilder();for(String line:notes){if(note.length()>0)note.append('\n');note.append(line);}
        c.put("note",note.toString());put(found,c);
    }
    private static String shortText(String s) {return s.substring(0,Math.min(30,s.length())).replace('\n',' ');}
    private static int dayValue(String s) {int n="一二三四五六日".indexOf(s);return n>=0?n+1:s.equals("天")?7:Integer.parseInt(s);}

    private static void readTable(JSONObject table,Map<String,JSONObject> found) throws JSONException {
        JSONArray rows=table.optJSONArray("rows");if(rows==null)return;
        Map<Integer,Integer> weekdays=new HashMap<>();
        // Grid expands rowSpan/colSpan so column coordinates remain meaningful.
        Map<Integer,Integer> occupiedUntil=new HashMap<>();
        for(int r=0;r<rows.length();r++) {
            JSONArray row=rows.getJSONArray(r);int col=0; int[] rowPeriod=null;
            for(int c=0;c<row.length();c++) {
                while(occupiedUntil.containsKey(col)&&occupiedUntil.get(col)>r)col++;
                JSONObject cell=row.getJSONObject(c);String text=cell.optString("text","").trim();
                int rs=cell.optInt("rowSpan",1),cs=cell.optInt("colSpan",1);
                if(rs<1||rs>100||cs<1||cs>30)throw new JSONException("课表单元格跨度无效");
                Matcher dm=DAY.matcher(text),pm=PERIOD.matcher(text);
                if(dm.matches())for(int k=0;k<cs;k++)weekdays.put(col+k,dayValue(dm.group(1)));
                else if(pm.matches())rowPeriod=parsePeriods(text);
                else if(WEEK.matcher(text).find()) {
                    if(cs!=1||!weekdays.containsKey(col)||rowPeriod==null||rs!=1)
                        throw new JSONException("课程表格位置不完整或含合并课程格，请使用明确课程块导入");
                    addBlock(new JSONObject().put("text",text).put("weekday",weekdays.get(col)).put("startPeriod",rowPeriod[0]).put("endPeriod",rowPeriod[1]),found);
                }
                for(int k=0;k<cs;k++)if(rs>1)occupiedUntil.put(col+k,r+rs);
                col+=cs;
            }
        }
    }
}
