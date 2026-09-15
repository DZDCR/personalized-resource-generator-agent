package com.example.learning.agent.product;

/**
 * 一次多智能体协作的完整产物（六类资源）。
 */
public record ResourceBundle(
        CourseDoc doc,
        MindmapData mindmap,
        QuizSet quiz,
        ReadingMaterial reading,
        VideoScript video,
        CodeCaseSet code
) {}