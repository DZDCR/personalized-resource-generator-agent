package com.example.learning.auth.controller;

import com.example.learning.auth.config.AuthProperties;
import com.example.learning.auth.dto.AuthRequests;
import com.example.learning.auth.dto.AuthViews.CaptchaView;
import com.example.learning.auth.dto.AuthViews.MeView;
import com.example.learning.auth.dto.AuthViews.SendCodeView;
import com.example.learning.auth.dto.AuthViews.TokenResponse;
import com.example.learning.auth.security.AuthCodes;
import com.example.learning.auth.security.ClientInfo;
import com.example.learning.auth.security.JwtClaims;
import com.example.learning.auth.service.AuthService;
import com.example.learning.auth.service.CaptchaService;
import com.example.learning.auth.service.DevOutboxService;
import com.example.learning.auth.service.RateLimitService;
import com.example.learning.auth.service.SsoService;
import com.example.learning.common.ApiResult;
import com.example.learning.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证入口。登录成功返回访问令牌 JSON + HttpOnly 刷新令牌 Cookie。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final SsoService ssoService;
    private final DevOutboxService outboxService;
    private final RateLimitService rateLimitService;
    private final AuthProperties properties;

    @GetMapping("/captcha")
    public ApiResult<CaptchaView> captcha() {
        return ApiResult.success(captchaService.create());
    }

    @GetMapping("/providers")
    public ApiResult<Map<String, Object>> providers() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("password", true);
        data.put("emailOtp", true);
        data.put("smsOtp", true);
        data.put("sso", true);
        data.put("ssoProvider", properties.getSso().getProvider());
        data.put("captcha", properties.getCaptcha().isEnabled());
        data.put("devMode", properties.isDevMode());
        data.put("maxFailedAttempts", properties.getLock().getMaxFailedAttempts());
        return ApiResult.success(data);
    }

    @PostMapping("/login/password")
    public ApiResult<TokenResponse> loginWithPassword(@Valid @RequestBody AuthRequests.PasswordLogin request,
                                                      HttpServletRequest servletRequest,
                                                      HttpServletResponse servletResponse) {
        ClientInfo client = ClientInfo.of(servletRequest);
        rateLimitService.check("login:" + client.ip(), 30);
        AuthService.IssuedSession session = authService.loginWithPassword(request, client);
        writeRefreshCookie(servletResponse, session);
        return ApiResult.success(session.view());
    }

    @PostMapping("/login/otp")
    public ApiResult<TokenResponse> loginWithOtp(@Valid @RequestBody AuthRequests.OtpLogin request,
                                                 HttpServletRequest servletRequest,
                                                 HttpServletResponse servletResponse) {
        ClientInfo client = ClientInfo.of(servletRequest);
        rateLimitService.check("login:" + client.ip(), 30);
        AuthService.IssuedSession session = authService.loginWithOtp(request, client);
        writeRefreshCookie(servletResponse, session);
        return ApiResult.success(session.view());
    }

    @PostMapping("/otp/send")
    public ApiResult<SendCodeView> sendOtp(@Valid @RequestBody AuthRequests.SendCode request,
                                           HttpServletRequest servletRequest) {
        ClientInfo client = ClientInfo.of(servletRequest);
        rateLimitService.check("otp:" + client.ip(), 30);
        return ApiResult.success(authService.sendOtp(request, client));
    }

    @PostMapping("/register")
    public ApiResult<TokenResponse> register(@Valid @RequestBody AuthRequests.Register request,
                                             HttpServletRequest servletRequest,
                                             HttpServletResponse servletResponse) {
        ClientInfo client = ClientInfo.of(servletRequest);
        rateLimitService.check("register:" + client.ip(), 10);
        AuthService.IssuedSession session = authService.register(request, client);
        writeRefreshCookie(servletResponse, session);
        return ApiResult.success(session.view());
    }

    @PostMapping("/forgot")
    public ApiResult<SendCodeView> forgot(@Valid @RequestBody AuthRequests.Forgot request,
                                          HttpServletRequest servletRequest) {
        ClientInfo client = ClientInfo.of(servletRequest);
        rateLimitService.check("forgot:" + client.ip(), 20);
        return ApiResult.success(authService.forgot(request, client));
    }

    @PostMapping("/reset")
    public ApiResult<Void> reset(@Valid @RequestBody AuthRequests.Reset request,
                                 HttpServletRequest servletRequest) {
        ClientInfo client = ClientInfo.of(servletRequest);
        rateLimitService.check("reset:" + client.ip(), 20);
        authService.resetPassword(request, client);
        return ApiResult.success();
    }

    @PostMapping("/refresh")
    public ApiResult<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        ClientInfo client = ClientInfo.of(servletRequest);
        rateLimitService.check("refresh:" + client.ip(), 120);
        AuthService.IssuedSession session = authService.refresh(refreshToken, client);
        writeRefreshCookie(servletResponse, session);
        return ApiResult.success(session.view());
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        authService.logout(refreshToken, ClientInfo.of(servletRequest));
        clearRefreshCookie(servletResponse);
        return ApiResult.success();
    }

    @GetMapping("/me")
    public ApiResult<MeView> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResult.success(authService.me(JwtClaims.userId(jwt), JwtClaims.activeRoles(jwt)));
    }

    @PutMapping("/password")
    public ApiResult<Void> changePassword(@Valid @RequestBody AuthRequests.ChangePassword request,
                                          @AuthenticationPrincipal Jwt jwt,
                                          HttpServletRequest servletRequest) {
        authService.changePassword(JwtClaims.userId(jwt), request, ClientInfo.of(servletRequest));
        return ApiResult.success();
    }

    @PostMapping("/session/roles")
    public ApiResult<TokenResponse> switchRoles(@Valid @RequestBody AuthRequests.SwitchRoles request,
                                                @AuthenticationPrincipal Jwt jwt,
                                                HttpServletRequest servletRequest,
                                                HttpServletResponse servletResponse) {
        ClientInfo client = ClientInfo.of(servletRequest);
        AuthService.IssuedSession session = authService.switchRoles(JwtClaims.userId(jwt), request, client);
        writeRefreshCookie(servletResponse, session);
        return ApiResult.success(session.view());
    }

    // ------------------------------------------------------------------
    // 统一身份认证（OIDC 授权码 + PKCE 演示）
    // ------------------------------------------------------------------

    @GetMapping("/sso/config")
    public ApiResult<SsoService.SsoPublicConfig> ssoConfig() {
        return ApiResult.success(ssoService.publicConfig());
    }

    @GetMapping("/sso/authorize")
    public ResponseEntity<Void> ssoAuthorize(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("state") String state,
            @RequestParam(value = "response_type", defaultValue = "code") String responseType,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod) {
        URI redirect = ssoService.authorize(clientId, redirectUri, state, responseType,
                codeChallenge, codeChallengeMethod);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirect.toString())
                .build();
    }

    @PostMapping("/sso/token")
    public ApiResult<TokenResponse> ssoToken(@Valid @RequestBody AuthRequests.SsoToken request,
                                             HttpServletRequest servletRequest,
                                             HttpServletResponse servletResponse) {
        ClientInfo client = ClientInfo.of(servletRequest);
        rateLimitService.check("sso:" + client.ip(), 30);
        AuthService.IssuedSession session = ssoService.exchange(request, client);
        writeRefreshCookie(servletResponse, session);
        return ApiResult.success(session.view());
    }

    // ------------------------------------------------------------------
    // 开发环境发件箱（仅 dev-mode 暴露）
    // ------------------------------------------------------------------

    @GetMapping("/dev/outbox")
    public ResponseEntity<ApiResult<List<DevOutboxService.DevMessage>>> devOutbox() {
        if (!properties.isDevMode()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(ApiResult.success(outboxService.list()));
    }

    private void writeRefreshCookie(HttpServletResponse response, AuthService.IssuedSession session) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, session.refreshToken())
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(session.refreshTtlSeconds()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
