package com.example.learning.agent.product;

import java.util.List;
import java.util.Map;

/**
 * DesignerAgent 产出的资源蓝图：后续所有 Agent 的依据。
 */
public record ResourceBlueprint(
        String topic,
        String studentLevel,
        List<String> knowledgePoints,
        String difficulty,
        Map<String, Object> resourcePlan
) {}