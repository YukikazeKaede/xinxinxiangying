package cn.xinxinxiangying.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 把学校成绩/考试接口返回的条目归一成应用自己的列表结构。 */
public final class AcademicParser {
    private AcademicParser(){}

    private static String text(JSONObject row,String... keys){
        for(String key:keys){
            String value=row.optString(key,"").trim();
            if(!value.isEmpty()&&!"null".equals(value))return value;
        }
        return "";
    }
    private static double number(JSONObject row,String... keys){
        for(String key:keys){
            String value=row.optString(key,"").trim();
            if(value.isEmpty())continue;
            try{return Double.parseDouble(value.replace("%","").replace(",",""));}catch(NumberFormatException ignored){}
        }
        return Double.NaN;
    }
    private static String fixed(double value){return Double.isNaN(value)?"":String.format(Locale.ROOT,"%.2f",value);}

    public static JSONObject parse(String service,JSONObject payload,JSONObject fallbackTerm) throws Exception {
        String kind="exams".equals(service)?"exams":"grades";
        JSONObject term=new JSONObject();
        String xnm=payload.optJSONObject("term")==null?"":payload.optJSONObject("term").optString("xnm","");
        String xqm=payload.optJSONObject("term")==null?"":payload.optJSONObject("term").optString("xqm","");
        if(xnm.isEmpty()&&fallbackTerm!=null)xnm=fallbackTerm.optString("xnm","");
        if(xqm.isEmpty()&&fallbackTerm!=null)xqm=fallbackTerm.optString("xqm","");
        if(!xnm.matches("\\d{4}"))throw new IllegalArgumentException("学校没有返回学年信息，请重新导入");
        if(!xqm.matches("\\d{1,2}"))throw new IllegalArgumentException("学校没有返回学期信息，请重新导入");
        term.put("xnm",xnm).put("xqm",xqm).put("label",label(xnm,xqm));
        JSONArray rows=payload.optJSONArray("items");
        if(rows==null)rows=new JSONArray();
        JSONArray items=new JSONArray();
        List<String> unknown=new ArrayList<>();
        boolean recognized=false;
        String[] known=kind.equals("exams")
            ?new String[]{"kcmc","kcbmc","ksmc","kssj","ksrq","cdmc","jxdd","zwh","ksfs","bz"}
            :new String[]{"kcmc","kcbmc","cj","zcj","cjjc","xf","jd","xfjd","kcxz","kcxzmc","jsxm"};
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row==null)continue;
            for(String name:known)if(row.has(name)&&!row.optString(name,"").isEmpty())recognized=true;
            if(i==0)for(java.util.Iterator<String> it=row.keys();it.hasNext();){String key=it.next();boolean seen=false;for(String name:known)if(name.equals(key)){seen=true;break;}if(!seen&&unknown.size()<24)unknown.add(key);}
            JSONObject item=kind.equals("exams")?exam(row):grade(row);
            if(item!=null)items.put(item);
        }
        if(items.length()==0&&rows.length()>0&&!recognized)throw new IllegalArgumentException("学校返回的字段尚未识别，请把这次的日志告诉开发者");
        JSONObject out=new JSONObject();
        out.put("service",kind).put("term",term).put("items",items).put("importedAt",System.currentTimeMillis());
        out.put("unrecognizedFields",new JSONArray(unknown));
        if(kind.equals("grades"))out.put("summary",summary(items));
        return out;
    }
    private static JSONObject grade(JSONObject row)throws Exception{
        String course=text(row,"kcmc","kcbmc","kcmcZh");
        if(course.isEmpty())return null;
        JSONObject item=new JSONObject();
        item.put("course",course);
        item.put("score",text(row,"cj","zcj","cjjc","zzdycj","bfzcj"));
        item.put("credit",text(row,"xf","xkxf","kcxf"));
        item.put("point",gradePoint(row));
        item.put("nature",text(row,"kcxz","kcxzmc","kclb","kclbmc"));
        item.put("teacher",text(row,"jsxm","xm","skjs"));
        item.put("code",text(row,"kch","kcdm"));
        return item;
    }
    /** 学校有的返回单科绩点 jd，有的只返回总计 xfjd（= 绩点×学分）。 */
    private static String gradePoint(JSONObject row){
        String point=text(row,"jd");
        if(!point.isEmpty())return point;
        double total=parse(row.optString("xfjd","")),credit=parse(row.optString("xf",""));
        if(total<=0)return "";
        if(credit>0){
            double each=total/credit;
            // 折算结果落在 0—5 之间才当成总绩点，否则 xfjd 本身就是单科绩点。
            if(each>=0&&each<=5)return fixed(each);
        }
        return fixed(total);
    }
    private static JSONObject exam(JSONObject row)throws Exception{
        String course=text(row,"kcmc","kcbmc");
        String name=text(row,"ksmc","kslb","ksfs");
        if(course.isEmpty()&&name.isEmpty())return null;
        JSONObject item=new JSONObject();
        item.put("course",course);
        item.put("name",name);
        item.put("time",text(row,"kssj","ksrq","kssjms"));
        item.put("place",place(row));
        item.put("seat",text(row,"zwh","seat"));
        item.put("type",text(row,"ksfs","ksxs","kslx"));
        item.put("note",text(row,"bz","bzxx"));
        item.put("code",text(row,"kch","kcdm"));
        return item;
    }
    /** 考场只写楼栋房间时补上校区，避免两个校区同名教室分不清。 */
    private static String place(JSONObject row){
        String place=text(row,"cdmc","jxdd","kscdmc");
        String campus=text(row,"cdxqmc","xqmc");
        return place.isEmpty()||campus.isEmpty()||place.contains(campus)?place:(place+" "+campus);
    }
    private static JSONObject summary(JSONArray items)throws Exception{
        double credits=0,weightedScore=0,scoreCredits=0,weightedPoint=0,pointCredits=0;
        int scored=0,pointed=0;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.getJSONObject(i);
            double credit=parse(item.optString("credit",""));
            if(!Double.isNaN(credit))credits+=credit;
            double score=parse(item.optString("score",""));
            if(!Double.isNaN(score)){scored++;if(!Double.isNaN(credit)){weightedScore+=score*credit;scoreCredits+=credit;}}
            double point=parse(item.optString("point",""));
            if(!Double.isNaN(point)){pointed++;if(!Double.isNaN(credit)){weightedPoint+=point*credit;pointCredits+=credit;}}
        }
        JSONObject summary=new JSONObject();
        summary.put("count",items.length()).put("credit",fixed(credits));
        summary.put("average",fixed(scored==0?Double.NaN:(scoreCredits>0?weightedScore/scoreCredits:Double.NaN)));
        summary.put("gpa",fixed(pointed==0?Double.NaN:(pointCredits>0?weightedPoint/pointCredits:Double.NaN)));
        summary.put("weighted",pointCredits>0);
        return summary;
    }
    private static double parse(String value){
        if(value==null||value.trim().isEmpty())return Double.NaN;
        try{return Double.parseDouble(value.trim().replace("%",""));}catch(NumberFormatException e){return Double.NaN;}
    }
    public static String label(String xnm,String xqm){
        int year;
        try{year=Integer.parseInt(xnm);}catch(NumberFormatException e){return "";}
        String name="12".equals(xqm)?"第2学期":"第1学期";
        return year+"-"+(year+1)+"学年"+name;
    }
}
