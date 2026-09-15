package com.example.learning.agent.product;

import java.util.List;

/**
 * DocWriterAgent 产出的课程讲解文档。
 */
public record CourseDoc(
        String title,
        String markdown,
        List<String> knowledgePoints
) {}