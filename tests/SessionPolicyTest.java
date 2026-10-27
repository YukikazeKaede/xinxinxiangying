import cn.xinxinxiangying.app.SessionPolicy;

/** 会话状态判断、Cookie 清理边界、认证去重与目标回跳的单元测试（纯合成数据）。 */
public final class SessionPolicyTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static void check(Object actual, Object expected, String message) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(message + " -> " + actual);
    }

    public static void main(String[] args) {
        actions();
        statuses();
        loginPages();
        cookies();
        targets();
        credentials();
        singleFlight();
        System.out.println("SessionPolicyTest PASS " + checks + " checks");
    }

    private static void actions() {
        check(SessionPolicy.action(SessionPolicy.Signal.HTTP_401, true),
            SessionPolicy.Action.REAUTH, "401 during sync should reauth");
        check(SessionPolicy.action(SessionPolicy.Signal.HTTP_401, false),
            SessionPolicy.Action.WAIT_FOR_USER, "401 while browsing stays on school page");
        check(SessionPolicy.action(SessionPolicy.Signal.HTTP_403, true),
            SessionPolicy.Action.SHOW_ERROR, "403 is permission, not session expiry");
        check(SessionPolicy.action(SessionPolicy.Signal.HTTP_5XX, true),
            SessionPolicy.Action.SHOW_ERROR, "5xx is transient");
        check(SessionPolicy.action(SessionPolicy.Signal.NETWORK, true),
            SessionPolicy.Action.SHOW_ERROR, "network failure is transient");
        check(SessionPolicy.action(SessionPolicy.Signal.TLS, true),
            SessionPolicy.Action.SHOW_ERROR, "TLS failure is not session expiry");
        check(SessionPolicy.action(SessionPolicy.Signal.LOGIN_PAGE, true),
            SessionPolicy.Action.WAIT_FOR_USER, "login page waits for user");
        check(SessionPolicy.action(SessionPolicy.Signal.SERVER_ERROR, false),
            SessionPolicy.Action.SHOW_ERROR, "generic server error is not auth");
        check(SessionPolicy.action(null, true), SessionPolicy.Action.IGNORE, "null signal ignored");
    }

    private static void statuses() {
        check(SessionPolicy.signalForHttp(401), SessionPolicy.Signal.HTTP_401, "401");
        check(SessionPolicy.signalForHttp(403), SessionPolicy.Signal.HTTP_403, "403");
        check(SessionPolicy.signalForHttp(408), SessionPolicy.Signal.HTTP_408, "408");
        check(SessionPolicy.signalForHttp(429), SessionPolicy.Signal.HTTP_429, "429");
        check(SessionPolicy.signalForHttp(500), SessionPolicy.Signal.HTTP_5XX, "500");
        check(SessionPolicy.signalForHttp(502), SessionPolicy.Signal.HTTP_5XX, "502");
        check(SessionPolicy.signalForHttp(503), SessionPolicy.Signal.HTTP_5XX, "503");
        check(SessionPolicy.signalForHttp(302), SessionPolicy.Signal.SERVER_ERROR, "3xx not auth");
        check(SessionPolicy.signalForHttp(200), SessionPolicy.Signal.SERVER_ERROR, "200 not auth");
    }

    private static void loginPages() {
        check(SessionPolicy.isAuthHost("authserver.sit.edu.cn"), true, "auth host");
        check(SessionPolicy.isAuthHost("jwxt.sit.edu.cn"), false, "jwxt is not auth host");
        check(SessionPolicy.isAuthHost(null), false, "null host");
        check(SessionPolicy.isLoginUrl("https://authserver.sit.edu.cn/authserver/login?service=x"), true, "CAS login");
        check(SessionPolicy.isLoginUrl("https://jwxt.sit.edu.cn/sso/jziotlogin"), true, "jwxt SSO");
        check(SessionPolicy.isLoginUrl("https://jwxt.sit.edu.cn/jwglxt/kbcx/xskbcxZccx_cxXSkbcxIndex.html"), false, "timetable page");
        check(SessionPolicy.isLoginUrl("https://xg.sit.edu.cn/login"), true, "xg login");
        check(SessionPolicy.isLoginUrl("https://xg.sit.edu.cn/dektxf/xfqktj?redirect=%2F"), false, "credit page");
        check(SessionPolicy.isLoginUrl("https://ecard.sit.edu.cn/epay/h5/v5qrcode"), false, "payment code page");
        check(SessionPolicy.isLoginUrl("https://ecard.sit.edu.cn/epay/h5/index"), false, "card page");
        check(SessionPolicy.isLoginUrl("not-a-url"), false, "invalid URL");
    }

    private static void cookies() {
        check(SessionPolicy.shouldClearCookies(true, "ug:1", "ug:1"), true, "explicit clear wins");
        check(SessionPolicy.shouldClearCookies(false, "ug:1", "ug:1"), false, "same owner keeps cookies");
        check(SessionPolicy.shouldClearCookies(false, "ug:1", "ug:2"), true, "different owner clears");
        check(SessionPolicy.shouldClearCookies(false, "", "ug:1"), true, "unknown previous owner clears once");
        check(SessionPolicy.shouldClearCookies(false, "ug:1", ""), false, "empty requested owner does not clear");
        check(SessionPolicy.shouldClearCookies(false, null, null), false, "null owners do not clear");
    }

    private static void targets() {
        check(SessionPolicy.target("timetable"), SessionPolicy.Target.TIMETABLE, "timetable target");
        check(SessionPolicy.target("exams"), SessionPolicy.Target.ACADEMIC, "exams target");
        check(SessionPolicy.target("grades"), SessionPolicy.Target.ACADEMIC, "grades target");
        check(SessionPolicy.target("gpa"), SessionPolicy.Target.ACADEMIC, "gpa target");
        check(SessionPolicy.target("second"), SessionPolicy.Target.SECOND, "second target");
        check(SessionPolicy.target("second_activities"), SessionPolicy.Target.SECOND_ACTIVITIES, "activities target");
        check(SessionPolicy.target("card"), SessionPolicy.Target.CARD, "card target");
        check(SessionPolicy.target("code"), SessionPolicy.Target.CODE, "code target");
        check(SessionPolicy.target("electricity"), SessionPolicy.Target.ELECTRICITY, "electricity target");
        check(SessionPolicy.target("unknown"), SessionPolicy.Target.MAIN, "unknown target");
        check(SessionPolicy.isAcademicService("gpa"), true, "academic service");
        check(SessionPolicy.isAcademicService("second"), false, "second is not academic");
        check(SessionPolicy.isWebService("electricity"), true, "web service");
    }

    private static void credentials() {
        check(SessionPolicy.secretToSave(true, "typed"), "", "stored password is not rewritten");
        check(SessionPolicy.rememberToSave(true, false, "typed"), false, "stored password stays remembered");
        check(SessionPolicy.secretToSave(false, "typed"), "typed", "new password passed through");
        check(SessionPolicy.rememberToSave(false, true, "typed"), true, "explicit remember saves");
        check(SessionPolicy.rememberToSave(false, false, "typed"), false, "explicit forget removes");
        check(SessionPolicy.secretToSave(false, ""), "", "empty password stays empty");
        check(SessionPolicy.rememberToSave(false, true, ""), false, "empty password is not remembered");
        check(SessionPolicy.sameAccount("1001", "1001"), true, "same account");
        check(SessionPolicy.sameAccount("1001", "1002"), false, "different account");
        check(SessionPolicy.sameAccount("1001", ""), true, "missing school id is not rejected");
        check(SessionPolicy.sameAccount("", "1002"), true, "missing expected id is not rejected");
        check(SessionPolicy.sameAccount(" 1001 ", "1001"), true, "trim account id");
    }

    private static void singleFlight() {
        SessionPolicy.SingleFlight flight = new SessionPolicy.SingleFlight();
        check(flight.begin(), true, "first begin");
        check(flight.begin(), false, "duplicate begin while authentication is open");
        check(flight.active(), true, "waiting for user keeps authentication active");
        flight.end();
        check(flight.active(), false, "cancel releases authentication");
        check(flight.begin(), true, "begin after end");
        check(flight.begin(), false, "a long-running authentication never expires itself");
        flight.end();
        flight.end();
        check(flight.active(), false, "ending twice is safe");
    }
}
