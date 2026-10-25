import cn.xinxinxiangying.app.DektParser;
import cn.xinxinxiangying.app.SchoolCatalog;
import org.json.*;

/** 第二课堂学分页文字的合成用例：不代表真实页面结构（页面未能联网核对）。 */
public final class DektParserTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static JSONObject row(String name,String earned,String required,int depth)throws JSONException{
        return new JSONObject().put("name",name).put("earned",earned).put("required",required).put("depth",depth);
    }
    private static JSONObject payload(String earned,String required,String updated,JSONArray rows)throws JSONException{
        return new JSONObject().put("earned",earned).put("required",required).put("updated",updated).put("rows",rows);
    }
    private static void reject(JSONObject payload,String label){
        try{DektParser.parse(payload);throw new AssertionError("应拒绝："+label);}
        catch(AssertionError failed){throw failed;}
        catch(Exception expected){check(expected.getMessage()!=null&&!expected.getMessage().isEmpty(),label);}
    }
    public static void main(String[] args)throws Exception {
        JSONArray rows=new JSONArray()
            .put(row("德育","2.40","3.00",0))
            .put(row("讲座报告、主题教育","1.20","1.80",1))
            .put(row("安全教育","0.20","0.20",1))
            .put(row("志愿公益","1.00","1.00",1))
            .put(row("劳育","0.45","3.00",0))
            .put(row("三创活动","0.25","1.5~3",1))
            .put(row("校园文明","0.20","0~3",1));
        JSONObject parsed=DektParser.parse(payload("3.65","8.00","25/10/2026 19:29",rows));
        check("3.65".equals(parsed.getString("earned")),"累计学分");
        check("8.00".equals(parsed.getString("required")),"需求学分");
        check("25/10/2026 19:29".equals(parsed.getString("updated")),"学校更新时间");
        check(SchoolCatalog.DEKT_HOME.equals(parsed.getString("source")),"数据来源");
        check(parsed.getLong("importedAt")>0,"导入时间");
        JSONArray categories=parsed.getJSONArray("categories");
        check(categories.length()==2,"两个大类");
        JSONObject moral=categories.getJSONObject(0);
        check("德育".equals(moral.getString("name")),"大类名");
        check("2.40".equals(moral.getString("earned")),"大类已获");
        check(moral.getJSONArray("children").length()==3,"德育三个子项");
        check("志愿公益".equals(moral.getJSONArray("children").getJSONObject(2).getString("name")),"子项名");
        JSONObject labour=categories.getJSONObject(1);
        check("1.50~3.00".equals(labour.getJSONArray("children").getJSONObject(0).getString("required")),"区间需求归一小数位");

        JSONArray plain=new JSONArray().put(row("德育","2.40","3.00",0)).put(row("劳育","0.45","3.00",0));
        JSONObject computed=DektParser.parse(payload("","","",plain));
        check("2.85".equals(computed.getString("earned")),"缺总数时按大类求和");
        check("6.00".equals(computed.getString("required")),"缺需求时按大类求和");
        check("".equals(computed.getString("updated")),"没有更新时间为空");

        JSONArray ranged=new JSONArray().put(row("德育","0.30","1.50~3.00",0));
        check("".equals(DektParser.parse(payload("","","",ranged)).getString("required")),"区间需求不参与求和");

        JSONArray repeated=new JSONArray()
            .put(row("德育","2.40","3.00",0))
            .put(row("德育","2.40","3.00",0))
            .put(row("安全教育","0.20","0.20",1));
        JSONArray merged=DektParser.parse(payload("2.40","3.00","",repeated)).getJSONArray("categories");
        check(merged.length()==1,"同名大类去重");
        check(merged.getJSONObject(0).getJSONArray("children").length()==1,"子项并入已存在的大类");

        JSONArray orphan=new JSONArray().put(row("安全教育","0.20","0.20",1));
        check(DektParser.parse(payload("0.20","0.20","",orphan)).getJSONArray("categories").length()==1,"没有大类时子项自成一类");

        reject(payload("3.65","8.00","",new JSONArray()),"没有明细");
        reject(payload("3.65","8.00","",new JSONArray().put(row("  ","1.00","1.00",0))),"名称全空");

        check("2.40".equals(DektParser.number("2.4")),"数字保留两位");
        check("".equals(DektParser.number("三学分")),"不认识的数字");
        check("".equals(DektParser.number("1000")),"超出范围的数字");
        check("1.50~3.00".equals(DektParser.range("1.5 ~ 3")),"区间归一");
        check("".equals(DektParser.range("1.5~abc")),"半个区间");
        check(DektParser.text("  教室\nA101 ").equals("教室 A101"),"文字压空白");
        System.out.println("DektParserTest: "+checks+" checks passed");
    }
}
