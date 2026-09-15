package com.example.learning.agent.product;

/**
 * MindmapAgent 产出的思维导图。
 * jsonTree 为面向 markmap 的 JSON 树，前端渲染为可交互导图。
 */
public record MindmapData(
        String root,
        String jsonTree
) {}