package com.example.learning.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 周评估任务：为活跃学生生成本周学习效果评估报告。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyAssessmentJob {

    @Scheduled(cron = "0 30 3 * * MON")
    public void runWeeklyAssessment() {
        log.info("weekly assessment skipped (scaffold)");
    }
}