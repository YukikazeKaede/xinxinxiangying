import cn.xinxinxiangying.app.ElectricityParser;
import org.json.*;

/** Dormitory and balances are synthetic; no school identifiers are used. */
public final class ElectricityParserTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static JSONObject room()throws Exception{
        JSONArray fields=new JSONArray();
        for(String role:new String[]{"school","build","floor","room"})fields.put(new JSONObject().put("role",role).put("id",role).put("value","SYNTHETIC_"+role).put("title","SYNTHETIC_"+role));
        return new JSONObject().put("fields",fields).put("cookie","SYNTHETIC_PRIVATE");
    }
    private static JSONObject state(String value,String unit)throws Exception{return new JSONObject().put("status","complete").put("room",room()).put("balance",new JSONObject().put("value",value).put("unit",unit));}
    private static void reject(JSONObject value,String label)throws Exception{
        try{ElectricityParser.balance(value);throw new AssertionError(label);}catch(IllegalArgumentException expected){checks++;}
    }
    public static void main(String[] args)throws Exception{
        JSONObject selected=ElectricityParser.room(room());
        check(selected.getJSONArray("fields").length()==4,"all selected levels retained");
        check(!selected.toString().contains("SYNTHETIC_PRIVATE"),"private unrelated fields excluded");
        check(ElectricityParser.description(selected).contains("SYNTHETIC_room"),"dormitory description");
        check(ElectricityParser.balance(state("42.5","元")).getJSONObject("balance").getString("value").equals("42.50"),"money format");
        check(ElectricityParser.balance(state("0","元")).getJSONObject("balance").getString("value").equals("0.00"),"zero is a valid result");
        check(ElectricityParser.balance(state("-1.23","元")).getJSONObject("balance").getString("value").equals("-1.23"),"negative balance");
        check(ElectricityParser.balance(state("12.345","度")).getJSONObject("balance").getString("value").equals("12.345"),"energy is not converted to money");
        reject(state("NaN","元"),"NaN rejected");reject(state("1e9","元"),"scientific notation rejected");reject(state("1","美元"),"unknown unit rejected");
        reject(state("1","元").put("status","pending"),"unfinished read rejected");
        reject(state("1","元").put("room",new JSONObject()),"missing room rejected");
        JSONObject bad=room();bad.getJSONArray("fields").getJSONObject(3).put("value","-1");
        reject(state("1","元").put("room",bad),"placeholder room rejected");
        JSONObject duplicate=room();duplicate.getJSONArray("fields").put(duplicate.getJSONArray("fields").getJSONObject(0));
        reject(state("1","元").put("room",duplicate),"duplicate levels rejected");
        System.out.println("ElectricityParserTest: "+checks+" checks passed");
    }
}
