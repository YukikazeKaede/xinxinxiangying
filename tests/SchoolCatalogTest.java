import cn.xinxinxiangying.app.SchoolCatalog;

public final class SchoolCatalogTest {
    private static int count;
    private static void check(boolean value, String message) {
        count++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        String[] blocked = { null, "", "http://jwxt.sit.edu.cn/", "https://sit.edu.cn.evil.example/", "https://evil-sit.edu.cn/", "https://sit.edu.cn@evil.example/", "https://evil@jwxt.sit.edu.cn/", "javascript:alert(1)", "file:///data/data/app", "https://jwxt.sit.edu.cn:444/", "https://sit.edu.cn\\@evil.example/" };
        for (String url:blocked) check(!SchoolCatalog.isSchoolUrl(url),"Must reject "+url);
        check(SchoolCatalog.isSchoolUrl("https://jwxt.sit.edu.cn/"),"Academic origin");
        check(SchoolCatalog.isSchoolUrl("https://AUTHServer.SIT.EDU.CN:443/authserver/login"),"Case-insensitive host");
        check(!SchoolCatalog.isAcademicUrl(SchoolCatalog.AUTH),"Never save authentication text");
        check(!SchoolCatalog.isAcademicUrl(SchoolCatalog.CARD_HOME),"Never save payment text");
        check(SchoolCatalog.isAcademicUrl(SchoolCatalog.PG_HOME),"PG academic origin");
        for (String id:SchoolCatalog.IDS) for(boolean pg:new boolean[]{false,true})
            check(SchoolCatalog.isSchoolUrl(SchoolCatalog.url(id,pg)),"School-only catalog "+id);
        check(SchoolCatalog.isCardPaymentGateway("https://openapi.alipay.com/gateway.do?method=alipay.trade.wap.pay"),"Observed payment form target");
        check(!SchoolCatalog.isSchoolUrl("https://openapi.alipay.com/gateway.do"),"Payment gateway remains external to school");
        for(String url:new String[]{null,"http://openapi.alipay.com/gateway.do","https://openapi.alipay.com.evil.example/gateway.do","https://evil@openapi.alipay.com/gateway.do","https://openapi.alipay.com:444/gateway.do","https://openapi.alipay.com/other","https://openapi.alipay.com/%67ateway.do","https://openapi.alipay.com/gateway.do#other"})
            check(!SchoolCatalog.isCardPaymentGateway(url),"Reject unofficial payment target");
        check(SchoolCatalog.isAlipayCashierUrl("https://mclient.alipay.com/cashier/mobilepay.htm?SYNTHETIC=1"),"Observed cashier redirect host");
        for(String url:new String[]{null,"http://mclient.alipay.com/cashier/mobilepay.htm","https://mclient.alipay.com.evil.example/","https://evil@mclient.alipay.com/","https://mclient.alipay.com:444/","https://evil.example/?url=https://mclient.alipay.com/","https://openapi.alipay.com/gateway.do"})
            check(!SchoolCatalog.isAlipayCashierUrl(url),"Reject unofficial cashier redirect");
        check(SchoolCatalog.PG_HOME.equals(SchoolCatalog.url("grades",true)),"Do not invent PG grades endpoint");
        check(SchoolCatalog.UG_KB_ENTRY.equals(SchoolCatalog.url("timetable",false)),"Timetable opens the school's own weekly query entry");
        check(SchoolCatalog.isAcademicUrl(SchoolCatalog.UG_KB_ENTRY),"Weekly query entry is academic");
        check(SchoolCatalog.isAcademicUrl(SchoolCatalog.UG_KB_JSON),"Course JSON endpoint stays academic");
        check(SchoolCatalog.UG_KB_JSON.startsWith("https://jwxt.sit.edu.cn/jwglxt/kbcx/"),"Course JSON uses the observed menu path");
        check(SchoolCatalog.CARD_ENTRY.equals(SchoolCatalog.url("card",false)),"Recharge uses the observed official app SSO entry");
        check(SchoolCatalog.CARD_ENTRY.equals(SchoolCatalog.url("card",true)),"Recharge entry is shared by student types");
        check(SchoolCatalog.url("code",false).endsWith("/epay/h5/v5qrcode"),"Payment code keeps its own entry");
        check(SchoolCatalog.ELECTRICITY.equals(SchoolCatalog.url("electricity",false)),"Electricity entry is unchanged");
        check(SchoolCatalog.UG_EXAMS.equals(SchoolCatalog.url("exams",false)),"Exam schedule uses the observed module page");
        check(SchoolCatalog.UG_GRADES.equals(SchoolCatalog.url("grades",false)),"Grades use the observed score module page");
        check(SchoolCatalog.UG_GRADES.equals(SchoolCatalog.url("gpa",false)),"GPA is derived from the same score module");
        check(SchoolCatalog.DEKT_HOME.equals(SchoolCatalog.url("second",false)),"Second-classroom credits use the xg host");
        check(SchoolCatalog.DEKT_HOME.equals(SchoolCatalog.url("second",true)),"Second-classroom credits are shared by both student types");
        check(SchoolCatalog.isSchoolUrl(SchoolCatalog.DEKT_SSO),"Dekt SSO stays on a school origin");
        check(SchoolCatalog.DEKT_SSO.startsWith("https://authserver.sit.edu.cn/authserver/login?service="),"Dekt SSO goes through CAS login");
        check(SchoolCatalog.DEKT_SSO.contains("check.zf"),"Dekt SSO uses the CAS service endpoint");
        check(!SchoolCatalog.DEKT_SSO.contains("/zftal-xgxt-web/login"),"Never use the 404 Dekt login path");
        check(!SchoolCatalog.isAcademic("second"),"Second-classroom credits are not the academic system");
        check("第二课堂".equals(SchoolCatalog.title("second")),"Second-classroom title");
        check(SchoolCatalog.isAcademicUrl(SchoolCatalog.UG_EXAMS)&&SchoolCatalog.isAcademicUrl(SchoolCatalog.UG_GRADES),"Score and exam pages stay academic");
        check("exams".equals(SchoolCatalog.academicKind("exams")),"Exam kind");
        check("grades".equals(SchoolCatalog.academicKind("grades"))&&"grades".equals(SchoolCatalog.academicKind("gpa")),"Grade kinds share one dataset");
        check(SchoolCatalog.UG_EXAMS.equals(SchoolCatalog.academicEndpoint("exams")),"Exam endpoint");
        check(SchoolCatalog.UG_GRADES_JSON.equals(SchoolCatalog.academicEndpoint("gpa")),"GPA endpoint");
        check(SchoolCatalog.GNMK_EXAMS.startsWith("N")&&SchoolCatalog.GNMK_GRADES.startsWith("N")&&!SchoolCatalog.GNMK_EXAMS.equals(SchoolCatalog.GNMK_GRADES),"Module codes differ");
        System.out.println("PASS: "+count+" school-origin and routing assertions");
    }
}
