package com.example.learning.service;

import com.example.learning.common.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 画像服务骨架：对话式画像构建的核心入口。
 * <p>实现要点（详见 docs/architecture.md）：
 * <ol>
 *   <li>ProfileAgent 从对话中抽取结构化更新（字段路径 + 新值 + 置信度增量）。</li>
 *   <li>加权合并：历史 0.6 + 新增 0.4，版本 +1。</li>
 *   <li>冲突检测：学习行为与画像明显不符时下发澄清问题。</li>
 *   <li>随学随新：测试提交/资源完成事件也触发更新（骨架阶段以内存存储）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final JsonUtils jsonUtils;

    /** 骨架阶段：内存版画像存储，接 JPA 后替换为 StudentProfileRepository */
    private final Map<Long, String> profileStore = new ConcurrentHashMap<>();

    public String getProfileJson(Long studentId) {
        return profileStore.getOrDefault(studentId, "{}");
    }

    /**
     * 记录一次画像抽取结果。
     *
     * @param updatePath  点路径，如 dimensions.knowledge_base.level
     * @param updateValue 新值
     */
    public void applyUpdate(Long studentId, String updatePath, Object updateValue) {
        String current = getProfileJson(studentId);
        JsonNode root;
        try {
            root = jsonUtils.read(current, JsonNode.class);
        } catch (Exception e) {
            root = jsonUtils.read("{}", JsonNode.class);
        }
        // TODO: setAtPath(root, updatePath, updateValue)  按点路径写入
        profileStore.put(studentId, jsonUtils.write(root));
        log.info("profile {} updated at {}", studentId, updatePath);
    }
}