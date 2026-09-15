package com.example.learning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识图谱服务骨架：先修关系查询。
 * <p>生产实现接入 Neo4j（Spring Data Neo4j）；骨架阶段使用内存示例图谱，
 * 便于不启动 Neo4j 时验证路径算法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    /** 示例图谱：主题 -> 先修主题 */
    private static final Map<String, List<String>> EXAMPLE_GRAPH = Map.of(
            "递归", List.of("函数", "栈"),
            "函数", List.of("变量"),
            "栈", List.of("数组"),
            "数组", List.of(),
            "变量", List.of()
    );

    private final List<String> bfs = new ArrayList<>();

    /** 返回目标知识点所需的全部先修（含间接），不包含目标本身。 */
    public List<String> prerequisiteClosure(String target) {
        List<String> result = new ArrayList<>();
        collect(target, result, new ArrayList<>());
        return result.stream().distinct().toList();
    }

    private void collect(String node, List<String> acc, List<String> visiting) {
        if (visiting.contains(node)) {
            return; // 防环
        }
        visiting.add(node);
        for (String pre : EXAMPLE_GRAPH.getOrDefault(node, List.of())) {
            if (!acc.contains(pre)) {
                acc.add(pre);
            }
            collect(pre, acc, visiting);
        }
    }

    /** 拓扑排序：保证先修在前（骨架按 BFS 收集顺序近似）。 */
    public List<String> topologicalSort(List<String> nodes) {
        return nodes;
    }
}