package com.example.learning.auth.service;

import com.example.learning.auth.config.AuthProperties;
import com.example.learning.auth.dto.AuthRequests;
import com.example.learning.auth.entity.AuthIdentity;
import com.example.learning.auth.entity.AuthUser;
import com.example.learning.auth.repository.AuthIdentityRepository;
import com.example.learning.auth.repository.AuthUserRepository;
import com.example.learning.auth.security.AuthCodes;
import com.example.learning.auth.security.ClientInfo;
import com.example.learning.auth.security.Hashes;
import com.example.learning.common.BizException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 统一身份认证（OIDC 授权码 + PKCE 演示实现）。
 *
 * <p>真实生产接入时，把 /authorize 替换为 IdP 登录页，/token 改为向 IdP 换取 id_token 并校验签名、
 * issuer、audience、nonce；本地通过 auth_identity(provider, subject) 映射用户。当前实现保留了同样的
 * code 一次性、redirect_uri 白名单、state 透传、PKCE S256 校验语义，便于课堂演示。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoService {

    private final AuthProperties properties;
    private final AuthIdentityRepository identityRepository;
    private final AuthUserRepository userRepository;
    private final AuthService authService;
    private final AuthAuditService auditService;

    private final Cache<String, SsoCode> codes = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /**
     * 模拟 IdP 授权端点：开发环境自动同意，生成一次性 code 并 302 回客户端。
     */
    public URI authorize(String clientId, String redirectUri, String state,
                         String responseType, String codeChallenge, String codeChallengeMethod) {
        validateClient(clientId);
        if (!properties.getSso().getRedirectUris().contains(redirectUri)) {
            throw new BizException(AuthCodes.BAD_REQUEST, "redirect_uri 不在白名单内");
        }
        if (responseType != null && !"code".equalsIgnoreCase(responseType)) {
            throw new BizException(AuthCodes.BAD_REQUEST, "仅支持 authorization_code 模式");
        }
        if (state == null || state.isBlank()) {
            throw new BizException(AuthCodes.BAD_REQUEST, "缺少 state");
        }
        if (codeChallenge != null && !codeChallenge.isBlank()
                && !"S256".equalsIgnoreCase(codeChallengeMethod)) {
            throw new BizException(AuthCodes.BAD_REQUEST, "PKCE 仅支持 S256");
        }
        AuthIdentity identity = identityRepository
                .findByProviderAndSubject(properties.getSso().getProvider(), properties.getSso().getDemoSubject())
                .orElseThrow(() -> new BizException(AuthCodes.SERVER_ERROR, "统一身份映射未初始化"));
        AuthUser user = userRepository.findById(identity.getUserId())
                .orElseThrow(() -> new BizException(AuthCodes.SERVER_ERROR, "统一身份关联用户不存在"));

        String code = Hashes.randomToken(32);
        codes.put(code, new SsoCode(clientId, redirectUri, user.getId(),
                codeChallenge, codeChallengeMethod, Instant.now().plusSeconds(
                properties.getSso().getCodeTtlSeconds())));
        auditService.record("LOGIN_SSO_SUCCESS", user, true, null, "统一身份认证授权码已签发");
        String url = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
        return URI.create(url);
    }

    @Transactional
    public AuthService.IssuedSession exchange(AuthRequests.SsoToken request, ClientInfo client) {
        validateClient(request.clientId());
        SsoCode stored = codes.asMap().remove(request.code());
        if (stored == null || stored.expiresAt().isBefore(Instant.now())) {
            throw new BizException(AuthCodes.TOKEN_INVALID, "授权码无效或已过期");
        }
        if (!stored.clientId().equals(request.clientId())) {
            throw new BizException(AuthCodes.TOKEN_INVALID, "授权码与 client_id 不匹配");
        }
        if (!stored.redirectUri().equals(request.redirectUri())) {
            throw new BizException(AuthCodes.TOKEN_INVALID, "redirect_uri 与授权请求不一致");
        }
        if (stored.codeChallenge() != null && !stored.codeChallenge().isBlank()) {
            if (request.codeVerifier() == null || request.codeVerifier().isBlank()
                    || !Hashes.constantTimeEquals(stored.codeChallenge(),
                    Hashes.pkceS256(request.codeVerifier()))) {
                throw new BizException(AuthCodes.TOKEN_INVALID, "PKCE 校验失败");
            }
        } else if (!Hashes.constantTimeEquals(properties.getSso().getClientSecret(), request.clientSecret())) {
            throw new BizException(AuthCodes.TOKEN_INVALID, "client_secret 校验失败");
        }
        AuthUser user = userRepository.findById(stored.userId())
                .orElseThrow(() -> new BizException(AuthCodes.TOKEN_INVALID, "统一身份用户不存在"));
        if (user.getStatus() != com.example.learning.auth.entity.UserStatus.ACTIVE) {
            throw new BizException(AuthCodes.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }
        AuthService.IssuedSession session = authService.issueSessionForUser(user, List.of(), client,
                "LOGIN_SSO_SUCCESS", "统一身份认证登录（" + properties.getSso().getProvider() + "）");
        auditService.record("LOGIN_SSO_SUCCESS", user, true, client, "OIDC code 换取本地会话成功");
        return session;
    }

    /** 供前端探测 SSO 可用性与 client_id（不返回 secret）。 */
    public SsoPublicConfig publicConfig() {
        return new SsoPublicConfig(properties.getSso().getProvider(),
                properties.getSso().getClientId(),
                properties.getSso().getRedirectUris());
    }

    private void validateClient(String clientId) {
        if (clientId == null || !properties.getSso().getClientId().equals(clientId)) {
            throw new BizException(AuthCodes.BAD_REQUEST, "未知的 client_id");
        }
    }

    private record SsoCode(String clientId, String redirectUri, Long userId,
                           String codeChallenge, String codeChallengeMethod, Instant expiresAt) {
    }

    public record SsoPublicConfig(String provider, String clientId, List<String> redirectUris) {
    }
}
