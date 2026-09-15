package com.example.learning.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每周画像重估任务：用近7天学习行为全面重估画像。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileReassessJob {

    @Scheduled(cron = "0 0 2 * * MON")
    public void reassess() {
        log.info("weekly profile reassess skipped (scaffold)");
    }
}