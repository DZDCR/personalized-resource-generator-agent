package com.example.learning.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 学习效果评估服务骨架（加分项）。
 * 生产实现：聚合测试得分/学习行为 → LLM 生成定性报告 →
 * 回写画像 + 触发路径动态调整。
 */
@Service
public class AssessmentService {

    public Map<String, Object> weeklyReport(Long studentId) {
        return Map.of(
                "studentId", studentId,
                "period", "2026-W37",
                "scoreTotal", 0,
                "dimensions", Map.of("knowledge", 0, "effort", 0, "efficiency", 0, "growth", 0),
                "report", "骨架阶段：接入 quiz_records + learning_events 后生成"
        );
    }
}