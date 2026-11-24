package com.example.learning.auth.service;

import com.example.learning.auth.security.AuthCodes;
import com.example.learning.common.BizException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻量 IP 限流。单机演示使用 Caffeine；多实例部署时替换为 Redis + 滑动窗口。
 */
@Service
public class RateLimitService {

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public void check(String key, int limit) {
        AtomicInteger counter = counters.get(key, ignored -> new AtomicInteger());
        if (counter.incrementAndGet() > limit) {
            throw new BizException(AuthCodes.RATE_LIMITED, "请求过于频繁，请稍后再试");
        }
    }
}
