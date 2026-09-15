package com.example.learning.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 辅导服务骨架：多模态答疑组装（加分项）。
 * 生产实现：问题分类 → TutorAgent 生成 → 文字+图解+代码差异&视频片段拼接。
 */
@Service
public class TutorService {

    public Map<String, Object> answer(Long studentId, String question) {
        String type = classify(question);
        return Map.of(
                "type", type,
                "text", "骨架阶段占位解答，" + question,
                "visualBlock", null
        );
    }

    private String classify(String question) {
        if (question.contains("报错") || question.contains("代码") || question.contains("编译")) {
            return "code";
        }
        if (question.contains("怎么算") || question.contains("推导") || question.contains("为什么等于")) {
            return "calculation";
        }
        return "concept";
    }
}