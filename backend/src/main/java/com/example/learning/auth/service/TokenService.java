package com.example.learning.auth.service;

import com.example.learning.auth.config.AuthProperties;
import com.example.learning.auth.entity.AuthUser;
import com.example.learning.auth.security.Hashes;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 会话凭证签发：
 * <ul>
 *   <li>访问令牌：HS256 JWT，15 分钟，携带 uid/roles/perms/ver；</li>
 *   <li>刷新令牌：32 字节随机串，仅以 SHA-256 摘要落库，7 天，HttpOnly Cookie；</li>
 *   <li>轮换：每次刷新吊销旧令牌并生成新令牌；</li>
 *   <li>撤销：tokenVersion 变更或 revoked 标记使旧凭证失效。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;

    public IssuedToken issueAccessToken(AuthUser user, List<String> activeRoleCodes, List<String> permissions) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getJwt().getAccessTtlMinutes() * 60);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .id(UUID.randomUUID().toString())
                .claim("uid", user.getId())
                .claim("name", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName())
                .claim("roles", activeRoleCodes)
                .claim("perms", permissions)
                .claim("ver", user.getTokenVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt,
                properties.getJwt().getAccessTtlMinutes() * 60);
    }

    public String newRefreshToken() {
        return Hashes.randomToken(32);
    }

    public String hashRefreshToken(String rawToken) {
        return Hashes.sha256Hex(rawToken);
    }

    public long refreshTtlSeconds() {
        return properties.getJwt().getRefreshTtlDays() * 24 * 3600;
    }

    public record IssuedToken(String token, Instant expiresAt, long expiresIn) {
    }
}
