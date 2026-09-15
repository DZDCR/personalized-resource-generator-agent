package com.example.learning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 学习路径服务骨架：基于知识图谱先修关系的路径规划。
 * <p>实现要点（详见 docs/architecture.md）：
 * <ol>
 *   <li>获取目标知识点的先修闭包（BFS）。</li>
 *   <li>拓扑排序得到学习顺序。</li>
 *   <li>每节点按认知风格匹配资源并预估时长。</li>
 *   <li>基于评估结果动态调整（骨骨架阶段返回示例）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PathService {

    private final KnowledgeGraphService knowledgeGraphService;

    /** 生成学习路径（骨架返回示例数据）。 */
    public Map<String, Object> generate(Long studentId, String targetTopic) {
        List<String> prerequisiteClosure = knowledgeGraphService.prerequisiteClosure(targetTopic);
        List<String> ordered = knowledgeGraphService.topologicalSort(prerequisiteClosure);

        Map<String, Object> plan = Map.of(
                "studentId", studentId,
                "target", targetTopic,
                "stepOrder", ordered,
                "estimatedMinutes", estimate(ordered),
                "checkpointPassRate", 0.8
        );
        log.info("path generated for {} -> {}", studentId, ordered);
        return plan;
    }

    /** 节点完成后调整路径（触发条件见 architecture.md）。 */
    public void adjustAfterAssessment(Long studentId, Map<String, Object> assessment) {
        log.info("dynamic adjust skipped (scaffold)");
    }

    private Map<String, Integer> estimate(List<String> topics) {
        return topics.stream().collect(java.util.stream.Collectors.toMap(t -> t, t -> 45));
    }
}