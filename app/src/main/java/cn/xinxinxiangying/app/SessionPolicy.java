package cn.xinxinxiangying.app;

import java.net.URI;
import java.util.Locale;

/**
 * 会话判断的纯逻辑：不依赖 Android，便于单元测试。
 *
 * <p>这里只回答两个问题：这次失败是不是“学校明确要求重新认证”，
 * 以及登录成功后应该回到哪个功能。网络、权限、服务端故障都不能等同于会话失效。</p>
 */
public final class SessionPolicy {
    private SessionPolicy() { }

    public enum Signal {
        LOGIN_PAGE,
        HTTP_401,
        HTTP_403,
        HTTP_408,
        HTTP_429,
        HTTP_5XX,
        NETWORK,
        TLS,
        SERVER_ERROR
    }

    public enum Action {
        IGNORE,
        WAIT_FOR_USER,
        REAUTH,
        SHOW_ERROR
    }

    public enum Target {
        TIMETABLE,
        ACADEMIC,
        SECOND,
        SECOND_ACTIVITIES,
        CARD,
        CODE,
        ELECTRICITY,
        MAIN
    }

    /**
     * 401 才可能是会话失效；403 是权限或服务拒绝，5xx/超时/网络/TLS 是临时故障。
     * 自动同步时 401 交给统一认证；手动浏览时保留学校页面，让用户在该页面完成登录。
     */
    public static Action action(Signal signal, boolean autoSync) {
        if (signal == null) return Action.IGNORE;
        switch (signal) {
            case HTTP_401:
                return autoSync ? Action.REAUTH : Action.WAIT_FOR_USER;
            case LOGIN_PAGE:
                return Action.WAIT_FOR_USER;
            case HTTP_403:
            case HTTP_408:
            case HTTP_429:
            case HTTP_5XX:
            case NETWORK:
            case TLS:
            case SERVER_ERROR:
                return Action.SHOW_ERROR;
            default:
                return Action.IGNORE;
        }
    }

    public static Signal signalForHttp(int status) {
        if (status == 401) return Signal.HTTP_401;
        if (status == 403) return Signal.HTTP_403;
        if (status == 408) return Signal.HTTP_408;
        if (status == 429) return Signal.HTTP_429;
        if (status >= 500 && status <= 599) return Signal.HTTP_5XX;
        return Signal.SERVER_ERROR;
    }

    /** 只有学校统一认证主机上的密码表单才算登录页。其他系统的支付密码、查询密码不在此列。 */
    public static boolean isAuthHost(String host) {
        return host != null && "authserver.sit.edu.cn".equalsIgnoreCase(host);
    }

    public static boolean isLoginUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            if ("authserver.sit.edu.cn".equals(host)) return true;
            if ("jwxt.sit.edu.cn".equals(host) || "yjsgl.sit.edu.cn".equals(host)) {
                return path.contains("login") || path.contains("sso");
            }
            if ("xg.sit.edu.cn".equals(host)) return path.contains("/login");
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 只有明确退出、切换账号，或本机记录的所有者确实变化时才清 Cookie。
     * 同一账号的普通刷新、重认证和页面跳转都不清。
     */
    public static boolean shouldClearCookies(boolean explicitClear, String previousOwner, String requestedOwner) {
        if (explicitClear) return true;
        String wanted = requestedOwner == null ? "" : requestedOwner;
        if (wanted.isEmpty()) return false;
        String previous = previousOwner == null ? "" : previousOwner;
        return !wanted.equals(previous);
    }

    public static boolean isAcademicService(String service) {
        return "exams".equals(service) || "grades".equals(service) || "gpa".equals(service);
    }

    /**
     * 从 Keystore 复用的密码不是本次输入：保存时传空密码，只更新账号，保留原密文。
     * 只有用户这次真的输入了密码，才按“是否勾选记住”新增或删除密文。
     */
    public static String secretToSave(boolean fromStore, String password) {
        return fromStore ? "" : (password == null ? "" : password);
    }

    public static boolean rememberToSave(boolean fromStore, boolean remember, String password) {
        return !fromStore && remember && password != null && !password.isEmpty();
    }

    /** 学校响应带学号时，必须与本机预期账号一致；缺失时不据此拒绝。 */
    public static boolean sameAccount(String expected, String actual) {
        if (expected == null || expected.trim().isEmpty()) return true;
        if (actual == null || actual.trim().isEmpty()) return true;
        return expected.trim().equals(actual.trim());
    }

    public static boolean isWebService(String service) {
        return "card".equals(service) || "code".equals(service) || "electricity".equals(service);
    }

    public static Target target(String service) {
        if ("timetable".equals(service)) return Target.TIMETABLE;
        if (isAcademicService(service)) return Target.ACADEMIC;
        if ("second".equals(service)) return Target.SECOND;
        if ("second_activities".equals(service)) return Target.SECOND_ACTIVITIES;
        if ("card".equals(service)) return Target.CARD;
        if ("code".equals(service)) return Target.CODE;
        if ("electricity".equals(service)) return Target.ELECTRICITY;
        return Target.MAIN;
    }

    /** 认证页关闭或认证完成前只允许一个流程；人工验证没有固定完成时限。 */
    public static final class SingleFlight {
        private boolean running;

        public synchronized boolean begin() {
            if (running) return false;
            running = true;
            return true;
        }

        public synchronized void end() {
            running = false;
        }

        public synchronized boolean active() {
            return running;
        }
    }
}
