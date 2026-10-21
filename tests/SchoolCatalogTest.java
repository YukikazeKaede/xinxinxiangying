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
        check(SchoolCatalog.PG_HOME.equals(SchoolCatalog.url("grades",true)),"Do not invent PG grades endpoint");
        check(SchoolCatalog.UG_KB_ENTRY.equals(SchoolCatalog.url("timetable",false)),"Timetable opens the school's own weekly query entry");
        check(SchoolCatalog.isAcademicUrl(SchoolCatalog.UG_KB_ENTRY),"Weekly query entry is academic");
        check(SchoolCatalog.isAcademicUrl(SchoolCatalog.UG_KB_JSON),"Course JSON endpoint stays academic");
        check(SchoolCatalog.UG_KB_JSON.startsWith("https://jwxt.sit.edu.cn/jwglxt/kbcx/"),"Course JSON uses the observed menu path");
        check(SchoolCatalog.CARD_HOME.equals(SchoolCatalog.url("card",false)),"Do not invent recharge endpoint");
        check(SchoolCatalog.UG_EXAMS.equals(SchoolCatalog.url("exams",false)),"Exam schedule uses the observed module page");
        check(SchoolCatalog.UG_GRADES.equals(SchoolCatalog.url("grades",false)),"Grades use the observed score module page");
        check(SchoolCatalog.UG_GRADES.equals(SchoolCatalog.url("gpa",false)),"GPA is derived from the same score module");
        check(SchoolCatalog.isAcademicUrl(SchoolCatalog.UG_EXAMS)&&SchoolCatalog.isAcademicUrl(SchoolCatalog.UG_GRADES),"Score and exam pages stay academic");
        check("exams".equals(SchoolCatalog.academicKind("exams")),"Exam kind");
        check("grades".equals(SchoolCatalog.academicKind("grades"))&&"grades".equals(SchoolCatalog.academicKind("gpa")),"Grade kinds share one dataset");
        check(SchoolCatalog.UG_EXAMS.equals(SchoolCatalog.academicEndpoint("exams")),"Exam endpoint");
        check(SchoolCatalog.UG_GRADES.equals(SchoolCatalog.academicEndpoint("gpa")),"GPA endpoint");
        check(SchoolCatalog.GNMK_EXAMS.startsWith("N")&&SchoolCatalog.GNMK_GRADES.startsWith("N")&&!SchoolCatalog.GNMK_EXAMS.equals(SchoolCatalog.GNMK_GRADES),"Module codes differ");
        System.out.println("PASS: "+count+" school-origin and routing assertions");
    }
}
