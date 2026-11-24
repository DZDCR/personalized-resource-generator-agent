package com.example.learning.auth.security;

import com.example.learning.auth.entity.AuthUser;
import com.example.learning.auth.repository.AuthUserRepository;
import com.example.learning.auth.service.RbacQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security 用户加载：支持用户名/邮箱/手机号三种账号标识。
 * 账号锁定、禁用状态映射为 UserDetails 标志，由 DaoAuthenticationProvider 统一拦截。
 */
@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final AuthUserRepository userRepository;
    private final SaltedPasswordEncoder passwordEncoder;
    private final RbacQueryService queryService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
        AuthUser user = byAccount(account)
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));
        List<GrantedAuthority> authorities = new ArrayList<>();
        queryService.permissionCodes(user.getId()).forEach(code ->
                authorities.add(new SimpleGrantedAuthority(code)));
        queryService.effectiveRoles(user.getId()).forEach(role ->
                authorities.add(new SimpleGrantedAuthority(role.getCode())));
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
        return User.withUsername(user.getUsername())
                .password(passwordEncoder.encodeExisting(user))
                .authorities(authorities)
                .accountLocked(locked)
                .disabled(user.getStatus() != com.example.learning.auth.entity.UserStatus.ACTIVE)
                .build();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<AuthUser> byAccount(String account) {
        if (account == null || account.isBlank()) {
            return java.util.Optional.empty();
        }
        String value = account.trim();
        java.util.Optional<AuthUser> user = userRepository.findByUsernameIgnoreCase(value);
        if (user.isPresent()) {
            return user;
        }
        user = userRepository.findByEmailIgnoreCase(value);
        if (user.isPresent()) {
            return user;
        }
        return userRepository.findByPhone(value);
    }
}
