package cn.xinxinxiangying.app;

import java.net.URI;

/** URLs observed in the supplied APK. Paths are candidates until tested after login. */
public final class SchoolCatalog {
    private SchoolCatalog() {}
    public static final String AUTH = "https://authserver.sit.edu.cn/authserver/login";
    public static final String UG_SSO = "https://jwxt.sit.edu.cn/sso/jziotlogin";
    public static final String UG_HOME = "https://jwxt.sit.edu.cn/";
    public static final String PG_HOME = "https://yjsgl.sit.edu.cn/login/loginSso";
    public static final String CARD_HOME = "https://ecard.sit.edu.cn/epay/h5/index";
    public static final String PORTAL = "https://portal.sit.edu.cn";
    /** 学校菜单里的「学生课表查询（按周次）」页，登录后可直接打开（同源、无凭据）。 */
    public static final String UG_KB_ENTRY = "https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxZccx_cxXSkbcxIndex.html?gnmkdm=N2154&layout=default";
    /** 课表页自身调用的手机版课表接口：同源 POST，返回本人课表 JSON（不含凭据）。 */
    public static final String UG_KB_JSON = "https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxMobile_cxXsKb.html?gnmkdm=N2154";
    /** 考试安排页自身调用的查询接口（gnmkdm 取自学校原应用里出现的模块号）。 */
    public static final String UG_EXAMS = "https://jwxt.sit.edu.cn/jwglxt/kwgl/kscx_cxXsksxxIndex.html";
    public static final String GNMK_EXAMS = "N358105";
    /** 学生成绩/学分绩点查询接口。 */
    public static final String UG_GRADES = "https://jwxt.sit.edu.cn/jwglxt/cjcx/cjcx_cxDgXscj.html";
    public static final String GNMK_GRADES = "N305005";
    /** 第二课堂学分（活动学分）页面，与教务系统不是同一套地址。 */
    public static final String DEKT_HOME = "https://xg.sit.edu.cn/dektxf/xfqktj?redirect=%2F";
    /** 第二课堂活动报名页：活动列表按板块分组。 */
    public static final String DEKT_ACTIVITIES = "https://xg.sit.edu.cn/hdgl/hdydlist?redirect=%2F";
    public static final String[] IDS = {"timetable", "card", "code", "electricity", "exams", "grades", "gpa", "second"};
    public static final String[] TITLES = {"个人课表", "校园卡充值", "校园付款码", "宿舍电费", "考试安排", "考试成绩", "学分绩点", "第二课堂"};
    public static String title(String id) {
        for (int i=0;i<IDS.length;i++) if (IDS[i].equals(id)) return TITLES[i];
        return "学校服务";
    }
    public static boolean isSchoolUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                && (host.equalsIgnoreCase("sit.edu.cn") || host.toLowerCase(java.util.Locale.ROOT).endsWith(".sit.edu.cn"))
                && uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (IllegalArgumentException e) { return false; }
    }
    public static boolean isAcademicUrl(String url) {
        if (!isSchoolUrl(url)) return false;
        String host = URI.create(url).getHost();
        return "jwxt.sit.edu.cn".equalsIgnoreCase(host) || "yjsgl.sit.edu.cn".equalsIgnoreCase(host);
    }
    public static boolean isAcademic(String id) {
        return "timetable".equals(id) || "exams".equals(id) || "grades".equals(id) || "gpa".equals(id);
    }
    /** 成绩与学分绩点读同一份成绩数据，考试安排单独一份。 */
    public static String academicKind(String id) {
        return "exams".equals(id) ? "exams" : "grades";
    }
    public static String academicEndpoint(String id) {
        return "exams".equals(id) ? UG_EXAMS : UG_GRADES;
    }
    public static String academicGnmkdm(String id) {
        return "exams".equals(id) ? GNMK_EXAMS : GNMK_GRADES;
    }
    public static String url(String id, boolean postgraduate) {
        if (isAcademic(id) && postgraduate) return PG_HOME;
        // Observed data URLs may be JSON/POST endpoints, not navigable pages.
        if ("timetable".equals(id)) return UG_KB_ENTRY;
        if ("exams".equals(id)) return UG_EXAMS;
        if ("grades".equals(id) || "gpa".equals(id)) return UG_GRADES;
        if ("second".equals(id)) return DEKT_HOME;
        if ("code".equals(id)) return "https://ecard.sit.edu.cn/epay/h5/v5qrcode";
        if ("electricity".equals(id)) return "https://ecard.sit.edu.cn/epay/h5/payele";
        // No recharge create-order endpoint has been verified: enter the official homepage.
        return CARD_HOME;
    }
}
