package com.example.learning.agent.product;

/**
 * ReadingAgent 产出的拓展阅读材料。
 */
public record ReadingMaterial(
        String title,
        String markdownContent,
        String source
) {}