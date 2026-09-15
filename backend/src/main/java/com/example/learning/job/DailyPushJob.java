package com.example.learning.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日资源推送任务：按画像偏好推送当日学习资源。
 * 骨架阶段仅记录日志，生产实现见 docs/architecture.md 推送规则引擎。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyPushJob {

    @Scheduled(cron = "0 0 8 * * ?")
    public void pushDailyResources() {
        log.info("daily resource push skipped (scaffold)");
    }
}