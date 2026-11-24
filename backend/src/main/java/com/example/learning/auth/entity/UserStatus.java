package com.example.learning.auth.entity;

/**
 * 账号状态。锁定状态由 lockedUntil 动态表达，避免状态机漂移。
 */
public enum UserStatus {
    ACTIVE,
    DISABLED
}
