import cn.xinxinxiangying.app.AcademicParser;
import org.json.*;

/** 成绩/考试接口字段映射的合成用例，不代表真实抓包内容。 */
public final class AcademicParserTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static JSONObject payload(String xnm,String xqm,JSONArray items)throws JSONException{
        JSONObject term=new JSONObject().put("xnm",xnm).put("xqm",xqm);
        return new JSONObject().put("term",term).put("items",items);
    }
    private static JSONObject gradeRow(String course,String score,String credit,String point)throws JSONException{
        return new JSONObject().put("kcmc",course).put("cj",score).put("xf",credit).put("jd",point);
    }
    private static void reject(String service,JSONObject payload,JSONObject fallback,String label){
        try{AcademicParser.parse(service,payload,fallback);throw new AssertionError("应拒绝："+label);}
        catch(AssertionError failed){throw failed;}
        catch(Exception expected){check(expected.getMessage()!=null&&!expected.getMessage().isEmpty(),label);}
    }
    public static void main(String[] args)throws Exception {
        JSONObject grades=payload("2026","3",new JSONArray().put(gradeRow("高等数学","90","4","4.0")).put(gradeRow("大学英语","80","2","3.0")));
        grades.getJSONArray("items").getJSONObject(0).put("kcxzmc","必修").put("jsxm","张老师").put("kch","MATH1001");
        JSONObject parsed=AcademicParser.parse("grades",grades,null);
        check("grades".equals(parsed.getString("service")),"成绩服务名");
        check("2026-2027学年第1学期".equals(parsed.getJSONObject("term").getString("label")),"学期名称");
        JSONArray items=parsed.getJSONArray("items");
        check(items.length()==2,"两条成绩");
        JSONObject first=items.getJSONObject(0);
        check("高等数学".equals(first.getString("course")),"课程名");
        check("90".equals(first.getString("score")),"成绩");
        check("4".equals(first.getString("credit")),"学分");
        check("4.0".equals(first.getString("point")),"绩点");
        check("必修".equals(first.getString("nature")),"课程性质");
        check("张老师".equals(first.getString("teacher")),"教师");
        check("MATH1001".equals(first.getString("code")),"课程号");
        JSONObject summary=parsed.getJSONObject("summary");
        check(summary.getInt("count")==2,"统计条数");
        check("6.00".equals(summary.getString("credit")),"学分合计");
        check("86.67".equals(summary.getString("average")),"学分加权平均分");
        check("3.67".equals(summary.getString("gpa")),"学分加权绩点");

        check(AcademicParser.overallGpa(parsed).isEmpty(),"单学期缓存不显示为全部学期绩点");
        JSONObject allPayload=payload("","",new JSONArray().put(gradeRow("SYNTHETIC_OLD","90","4","4")).put(gradeRow("SYNTHETIC_NEW","60","2","1"))).put("allTerms",true);
        JSONObject all=AcademicParser.parse("grades",allPayload,null);
        check("3.00".equals(AcademicParser.overallGpa(all)),"全部学期按课程学分加权而非各学期算术平均");
        check("全部学期".equals(all.getJSONObject("term").getString("label")),"全部学期具有明确范围");
        JSONObject zero=payload("","",new JSONArray().put(gradeRow("SYNTHETIC_PASS","90","2","4"))
            .put(new JSONObject().put("kcmc","SYNTHETIC_ZERO").put("xf","2").put("xfjd","0"))).put("allTerms",true);
        check("2.00".equals(AcademicParser.overallGpa(AcademicParser.parse("grades",zero,null))),"零绩点课程计入分母");

        JSONObject invalid=payload("","",new JSONArray().put(gradeRow("SYNTHETIC_FINITE","80","2","3"))
            .put(gradeRow("SYNTHETIC_INVALID","80","Infinity","4"))).put("allTerms",true);
        check("3.00".equals(AcademicParser.overallGpa(AcademicParser.parse("grades",invalid,null))),"无效学分不污染全部学期平均值");

        JSONObject fallback=payload("2025","12",new JSONArray().put(new JSONObject().put("kcmc","线性代数").put("zcj","75").put("xf","1").put("xfjd","2.8")));
        JSONObject odd=AcademicParser.parse("grades",fallback,null);
        check("75".equals(odd.getJSONArray("items").getJSONObject(0).getString("score")),"zcj 兜底成绩");
        check("2.80".equals(odd.getJSONArray("items").getJSONObject(0).getString("point")),"没有 jd 时按 xfjd÷xf 折算绩点");

        JSONObject total=payload("2025","12",new JSONArray().put(new JSONObject().put("kcmc","大学物理A1").put("cj","75").put("xf","3").put("xfjd","7.50")));
        check("2.50".equals(AcademicParser.parse("grades",total,null).getJSONArray("items").getJSONObject(0).getString("point")),"总绩点折算单科绩点");
        check("2.50".equals(AcademicParser.parse("grades",total,null).getJSONArray("items").getJSONObject(0).getString("point")),"总绩点折算稳定");

        JSONObject single=payload("2025","12",new JSONArray().put(new JSONObject().put("kcmc","高等数学").put("cj","90").put("xf","4").put("jd","4.0").put("xfjd","16.00")));
        check("4.0".equals(AcademicParser.parse("grades",single,null).getJSONArray("items").getJSONObject(0).getString("point")),"有 jd 时直接用 jd");
        check("2025-2026学年第2学期".equals(odd.getJSONObject("term").getString("label")),"第二学期名称");

        JSONObject exams=payload("2026","3",new JSONArray().put(new JSONObject()
            .put("kcmc","大学物理").put("ksmc","期末考试").put("kssj","2026-01-06 09:00-11:00")
            .put("cdmc","奉贤第一教学楼A101").put("zwh","12").put("ksfs","闭卷")));
        JSONObject examParsed=AcademicParser.parse("exams",exams,null);
        check("exams".equals(examParsed.getString("service")),"考试服务名");
        JSONObject exam=examParsed.getJSONArray("items").getJSONObject(0);
        check("大学物理".equals(exam.getString("course")),"考试课程");
        check("期末考试".equals(exam.getString("name")),"考试名称");
        check("2026-01-06 09:00-11:00".equals(exam.getString("time")),"考试时间");
        check("奉贤第一教学楼A101".equals(exam.getString("place")),"考场");
        check("12".equals(exam.getString("seat")),"座位号");
        check("闭卷".equals(exam.getString("type")),"考试方式");
        check(examParsed.optJSONObject("summary")==null,"考试无成绩统计");

        JSONObject onlyName=payload("2026","3",new JSONArray().put(new JSONObject().put("ksmc","补考")));
        check(AcademicParser.parse("exams",onlyName,null).getJSONArray("items").length()==1,"只有考试名称也保留");

        JSONObject campusExam=payload("2025","12",new JSONArray().put(new JSONObject()
            .put("kcmc","线性代数A").put("ksmc","期末考试").put("cdmc","二教F105").put("cdxqmc","奉贤校区").put("zwh","18")));
        check("二教F105 奉贤校区".equals(AcademicParser.parse("exams",campusExam,null).getJSONArray("items").getJSONObject(0).getString("place")),"考场补上校区");
        JSONObject campusInside=payload("2025","12",new JSONArray().put(new JSONObject()
            .put("kcmc","大学物理").put("cdmc","奉贤校区二教F306").put("cdxqmc","奉贤校区")));
        check("奉贤校区二教F306".equals(AcademicParser.parse("exams",campusInside,null).getJSONArray("items").getJSONObject(0).getString("place")),"已含校区则不重复");

        JSONObject unknown=payload("2026","3",new JSONArray().put(new JSONObject().put("foo","1").put("bar","2")));
        reject("grades",unknown,null,"完全陌生的字段");

        JSONObject noCourse=payload("2026","3",new JSONArray().put(new JSONObject().put("cj","90")));
        check(AcademicParser.parse("grades",noCourse,null).getJSONArray("items").length()==0,"缺少课程名的成绩被丢弃");

        JSONObject empty=payload("","3",new JSONArray());
        JSONObject hinted=AcademicParser.parse("grades",empty,new JSONObject().put("xnm","2026").put("xqm","3"));
        check("2026".equals(hinted.getJSONObject("term").getString("xnm")),"学年回落到课表学期");
        check(hinted.getJSONArray("items").length()==0,"空成绩列表");
        check("0.00".equals(hinted.getJSONObject("summary").getString("credit")),"空列表学分为零");
        reject("grades",payload("","",new JSONArray()),null,"完全没有学年学期");

        JSONObject extra=payload("2026","3",new JSONArray().put(gradeRow("概率论","88","3","3.5")));
        extra.getJSONArray("items").getJSONObject(0).put("weird","x");
        JSONObject extraParsed=AcademicParser.parse("grades",extra,null);
        check(extraParsed.getJSONArray("unrecognizedFields").length()==1,"记录未识别字段");
        check("weird".equals(extraParsed.getJSONArray("unrecognizedFields").getString(0)),"未识别字段名");
        check(extraParsed.has("importedAt"),"导入时间");

        check("2026-2027学年第1学期".equals(AcademicParser.label("2026","3")),"label 第一学期");
        check("2026-2027学年第2学期".equals(AcademicParser.label("2026","12")),"label 第二学期");
        System.out.println("AcademicParserTest: "+checks+" checks passed");
    }
}
