package com.example.learning.auth.repository;

import com.example.learning.auth.entity.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUser, Long> {

    Optional<AuthUser> findByUsernameIgnoreCase(String username);

    Optional<AuthUser> findByEmailIgnoreCase(String email);

    Optional<AuthUser> findByPhone(String phone);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    @Query("""
            select u from AuthUser u
            where lower(u.username) like :kw
               or lower(u.displayName) like :kw
               or lower(u.email) like :kw
               or u.phone like :kw
            order by u.id desc
            """)
    Page<AuthUser> search(@Param("kw") String keywordPattern, Pageable pageable);

    /** 角色继承/权限变更后，使所有用户的刷新令牌失效（短期访问令牌自然过期）。 */
    @Modifying
    @Query("update AuthUser u set u.tokenVersion = u.tokenVersion + 1")
    int bumpAllTokenVersions();
}
