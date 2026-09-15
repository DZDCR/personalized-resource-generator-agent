package com.example.learning.agent.product;

import java.util.List;

/**
 * QuizAgent 产出的练习题目集。
 */
public record QuizSet(
        String topic,
        List<QuizItem> items
) {

    public record QuizItem(
            String id,
            String question,
            List<String> options,
            String answer,
            String explanation,
            String difficulty,
            String type
    ) {}
}