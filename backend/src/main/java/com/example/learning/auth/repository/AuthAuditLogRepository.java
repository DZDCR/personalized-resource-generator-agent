package com.example.learning.auth.repository;

import com.example.learning.auth.entity.AuthAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long>, JpaSpecificationExecutor<AuthAuditLog> {
}
