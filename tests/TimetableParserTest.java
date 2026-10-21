import cn.xinxinxiangying.app.TimetableParser;
import org.json.*;

/** Synthetic adapter fixtures; do not represent captured SIT responses. Run with a real org.json jar. */
public final class TimetableParserTest {
    private static int checks;
    private static void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    private static JSONObject raw(String weeks) throws JSONException {
        JSONObject row=new JSONObject().put("kcmc","高等数学").put("xm","张老师").put("cdmc","A101")
            .put("xqj","1").put("jcs","1-2").put("zcd",weeks);
        return new JSONObject().put("semester","2026-2027-1").put("raw",new JSONArray().put(new JSONObject().put("data",new JSONObject().put("kbList",new JSONArray().put(row)))));
    }
    private static JSONObject course(JSONObject input)throws JSONException{return TimetableParser.parse(input).getJSONArray("courses").getJSONObject(0);}
    private static void reject(JSONObject input,String label)throws Exception {
        try {TimetableParser.parse(input);throw new AssertionError("应拒绝："+label);}catch(JSONException expected){check(!expected.getMessage().isEmpty(),label);}
    }
    public static void main(String[] args)throws Exception {
        JSONObject odd=course(raw("1-16周(单)"));
        check(odd.getString("parity").equals("odd"),"单周");
        check(odd.getJSONArray("weeks").toString().equals("[1,3,5,7,9,11,13,15]"),"单周明确周次");
        JSONObject even=course(raw("1-16周（双）"));
        check(even.getJSONArray("weeks").toString().equals("[2,4,6,8,10,12,14,16]"),"双周");
        check(course(raw("1,3,5-7周")).getJSONArray("weeks").toString().equals("[1,3,5,6,7]"),"离散周次");
        reject(raw("0-16周"),"非法周次起点");reject(raw("9-2周"),"反向周次");reject(raw("1-61周"),"非法周次上限");
        reject(raw(""),"缺少周次");reject(raw("1周(双)"),"空单双交集");
        JSONObject bad=raw("1-16周");bad.getJSONArray("raw").getJSONObject(0).getJSONObject("data").getJSONArray("kbList").getJSONObject(0).put("jcs","16-17");
        reject(bad,"非法节次");
        JSONObject duplicate=raw("1-16周");JSONArray list=duplicate.getJSONArray("raw").getJSONObject(0).getJSONObject("data").getJSONArray("kbList");
        list.put(new JSONObject(list.getJSONObject(0).toString()));
        check(TimetableParser.parse(duplicate).getJSONArray("courses").length()==1,"课程去重");
        list.put(new JSONObject(list.getJSONObject(0).toString()).put("zcd","1-8周"));
        check(TimetableParser.parse(duplicate).getJSONArray("courses").length()==2,"同位置不同周次不合并");
        check(course(raw("1-16周")).getString("id").equals(course(raw("1-16周")).getString("id")),"稳定 ID");
        JSONObject direct=new JSONObject().put("raw",new JSONArray().put(new JSONObject().put("data",new JSONArray().put(raw("1-8周").getJSONArray("raw").getJSONObject(0).getJSONObject("data").getJSONArray("kbList").getJSONObject(0)))));
        check(course(direct).getString("name").equals("高等数学"),"明确同名字段数组");
        for(String day:new String[]{"星期一","星期1","1","周一"}) {
            JSONObject input=raw("1-8周");JSONObject item=input.getJSONArray("raw").getJSONObject(0).getJSONObject("data").getJSONArray("kbList").getJSONObject(0);
            item.remove("xqj");item.put("xqjmc",day);
            check(course(input).getInt("weekday")==1,"xqjmc 明确星期："+day);
        }
        JSONObject rawPriority=raw("1-8周");
        rawPriority.put("blocks",new JSONArray().put(new JSONObject().put("text","页面其它不完整内容")));
        rawPriority.put("tables",new JSONArray().put(new JSONObject().put("rows",new JSONArray().put(new JSONArray().put(new JSONObject().put("text","1-8周"))))));
        check(TimetableParser.parse(rawPriority).getJSONArray("courses").length()==1,"完整 raw 优先，不解析 DOM");
        JSONObject brokenRaw=raw("1-8周");JSONArray brokenList=brokenRaw.getJSONArray("raw").getJSONObject(0).getJSONObject("data").getJSONArray("kbList");
        brokenList.put(new JSONObject().put("kcmc","缺少位置信息"));reject(brokenRaw,"kbList 不完整项必须整体拒绝");
        reject(new JSONObject().put("raw",new JSONArray().put(new JSONObject().put("data",new JSONObject().put("kbList","invalid")))),"kbList 非数组拒绝");
        JSONObject block=new JSONObject().put("text","高等数学\n张老师\n1-16周(单)\n1-2节\n教室:A101").put("weekday",3).put("startPeriod",5).put("endPeriod",6);
        JSONObject blockCourse=course(new JSONObject().put("blocks",new JSONArray().put(block)));
        check(blockCourse.getInt("weekday")==3&&blockCourse.getInt("startPeriod")==5,"DOM位置优先");
        check(blockCourse.getString("name").equals("高等数学")&&blockCourse.getString("teacher").equals("张老师")&&blockCourse.getString("room").equals("A101"),"课程文字字段");
        JSONObject blockPriority=new JSONObject().put("blocks",new JSONArray().put(block)).put("tables",new JSONArray().put(new JSONObject().put("rows",new JSONArray().put(new JSONArray().put(new JSONObject().put("text","1-8周"))))));
        check(TimetableParser.parse(blockPriority).getJSONArray("courses").length()==1,"blocks 优先，不重复解析 tables");
        JSONObject minimal=new JSONObject().put("text","大学英语\n1-8周\n3-4节").put("weekday",2);
        check(course(new JSONObject().put("blocks",new JSONArray().put(minimal))).getString("teacher").isEmpty(),"允许缺老师教室");
        reject(new JSONObject().put("text","只有一段无法识别的页面"),"未知格式");
        reject(new JSONObject().put("blocks",new JSONArray().put(new JSONObject().put("text","数学\n1-8周\n1-2节"))),"缺少星期");
        reject(new JSONObject().put("blocks",new JSONArray().put(new JSONObject().put("text","数学\n1-8周(单双)\n1-2节").put("weekday",1))),"未知周次附加内容");
        JSONObject table=new JSONObject().put("rows",new JSONArray()
            .put(new JSONArray().put(new JSONObject().put("text","节次")).put(new JSONObject().put("text","周一")))
            .put(new JSONArray().put(new JSONObject().put("text","1-2节")).put(new JSONObject().put("text","物理\n1-16周\n教室:B202"))));
        check(course(new JSONObject().put("tables",new JSONArray().put(table))).getInt("weekday")==1,"明确表格位置");
        // 按周次页 div.lesson：课程名/教师/教室/星期/节次/周次都由学校页面 data-* 显式标注。
        JSONObject lessons=new JSONObject().put("semester","2026-2027学年第1学期").put("termStart","2026-09-14").put("termWeeks",19)
            .put("lessons",new JSONArray()
                .put(new JSONObject().put("kcmc","大学物理A2").put("jsxm","戴凯").put("cdmc","二教F206").put("xqj","2").put("jcor","1-2").put("zcd","1-14周"))
                .put(new JSONObject().put("kcmc","毛泽东思想和中国特色社会主义理论体系概论").put("jsxm","袁竞闻").put("cdmc","二教G203").put("xqj","2").put("jcor","5-6").put("zcd","1-7周（单）,11-15周（单）")));
        JSONObject parsedLessons=TimetableParser.parse(lessons);
        check(parsedLessons.getJSONArray("courses").length()==2,"lessons 两条课程");
        JSONObject parityCourse=null;
        for(int i=0;i<parsedLessons.getJSONArray("courses").length();i++){JSONObject c=parsedLessons.getJSONArray("courses").getJSONObject(i);if(c.getString("name").startsWith("毛泽东"))parityCourse=c;}
        check(parityCourse!=null&&parityCourse.getJSONArray("weeks").toString().equals("[1,3,5,7,11,13,15]"),"lessons 单周周次");
        check(parityCourse.getString("parity").equals("odd"),"lessons 单周标记");
        check(parityCourse.getInt("weekday")==2&&parityCourse.getInt("startPeriod")==5&&parityCourse.getInt("endPeriod")==6,"lessons 位置字段");
        check(parsedLessons.getString("termStart").equals("2026-09-14")&&parsedLessons.getInt("termWeeks")==19,"termStart/termWeeks 透传");
        JSONObject partial=new JSONObject().put("lessons",new JSONArray().put(new JSONObject().put("kcmc","天气学").put("xqj","3").put("jcor","3-4").put("zcd","1-8周")))
            .put("failedWeeks",new JSONArray().put(7).put(9));
        check(TimetableParser.parse(partial).getJSONArray("warnings").toString().contains("7、9"),"未抓取周次写入提示");
        JSONObject both=raw("1-8周");both.put("lessons",new JSONArray().put(new JSONObject().put("kcmc","另一门课").put("xqj","4").put("jcor","1-2").put("zcd","1-8周")));
        check(TimetableParser.parse(both).getJSONArray("courses").getJSONObject(0).getString("name").equals("高等数学"),"raw 优先于 lessons");
        JSONObject lessonVsTable=new JSONObject().put("lessons",new JSONArray().put(new JSONObject().put("kcmc","概率论").put("xqj","1").put("jcor","3-4").put("zcd","1-16周")))
            .put("tables",new JSONArray().put(new JSONObject().put("rows",new JSONArray().put(new JSONArray().put(new JSONObject().put("text","1-8周"))))));
        check(TimetableParser.parse(lessonVsTable).getJSONArray("courses").length()==1,"lessons 优先于 tables");
        reject(new JSONObject().put("lessons",new JSONArray().put(new JSONObject().put("xqj","1").put("jcor","1-2").put("zcd","1-8周"))),"课程缺少 kcmc");
        reject(new JSONObject().put("lessons",new JSONArray().put(new JSONObject().put("kcmc","数学").put("xqj","1").put("jcor","17-18").put("zcd","1-8周"))),"lessons 非法节次");
        reject(new JSONObject().put("lessons",new JSONArray().put(new JSONObject().put("kcmc","数学").put("xqj","1").put("jcor","1-2"))),"lessons 缺少周次");
        // 手机版课表接口（xskbcxMobile_cxXsKb）返回的 kbList：zcd 为整学期周次文本，含"第N周"单周写法。
        check(course(raw("第6周")).getJSONArray("weeks").toString().equals("[6]"),"单周写法 第6周");
        check(course(raw("第6周")).getInt("startWeek")==6,"第6周起始周");
        check(course(raw("4-10周(双)")).getJSONArray("weeks").toString().equals("[4,6,8,10]"),"接口双周段");
        check(course(raw("1-7周,10-16周")).getJSONArray("weeks").length()==14,"接口分段周次");
        check(course(raw("1-4周,6-10周")).getInt("endWeek")==10,"接口分段末尾");
        JSONObject apiRow=new JSONObject().put("kcmc","大学物理实验2").put("xm","李老师").put("cdmc","二教F206")
            .put("xqj","2").put("jcor","10-11").put("jcs","10-11").put("zcd","第6周").put("xqmc","奉贤校区").put("jxb_id","2026-2027-1-1");
        JSONObject api=new JSONObject().put("semester","2026-2027学年第1学期").put("termStart","2026-09-14").put("termWeeks",19)
            .put("raw",new JSONArray().put(new JSONObject().put("url","https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxMobile_cxXsKb.html").put("data",
                new JSONObject().put("kbList",new JSONArray().put(apiRow)).put("xsxx",new JSONObject().put("XM","张三").put("XH","23110001")))));
        JSONObject apiCourse=course(api);
        check(apiCourse.getString("name").equals("大学物理实验2")&&apiCourse.getString("room").equals("二教F206"),"接口课程字段");
        check(apiCourse.getInt("startPeriod")==10&&apiCourse.getInt("endPeriod")==11,"接口两位数节次");
        check(apiCourse.getInt("weekday")==2,"接口星期字段");
        JSONObject dupApi=new JSONObject(api.toString());
        dupApi.getJSONArray("raw").getJSONObject(0).getJSONObject("data").getJSONArray("kbList").put(new JSONObject(apiRow.toString()));
        check(TimetableParser.parse(dupApi).getJSONArray("courses").length()==1,"逐周取回的同一条课程合并");
        System.out.println("TimetableParserTest passed: "+checks+" checks");
    }
}
