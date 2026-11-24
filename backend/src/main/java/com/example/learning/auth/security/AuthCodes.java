package com.example.learning.auth.security;

/**
 * 认证/授权错误码。前端据此做差异化提示。
 */
public final class AuthCodes {

    public static final int BAD_REQUEST = 40000;
    public static final int INVALID_CREDENTIALS = 40101;
    public static final int ACCOUNT_LOCKED = 40102;
    public static final int CAPTCHA_INVALID = 40103;
    public static final int OTP_INVALID = 40104;
    public static final int TOKEN_INVALID = 40105;
    public static final int ACCOUNT_DISABLED = 40106;
    public static final int UNAUTHORIZED = 40100;
    public static final int FORBIDDEN = 40301;
    /** RBAC2 动态职责分离：需要用户选择本次会话激活的角色。 */
    public static final int ROLE_CHOICE_REQUIRED = 40901;
    /** 静态职责分离/基数/前置角色等约束冲突。 */
    public static final int CONSTRAINT_VIOLATION = 40902;
    public static final int CONFLICT = 40900;
    public static final int RATE_LIMITED = 42901;
    public static final int SERVER_ERROR = 50000;

    private AuthCodes() {
    }
}
