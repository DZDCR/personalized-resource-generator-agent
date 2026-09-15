package com.example.learning.service;

import com.example.learning.agent.product.*;
import com.example.learning.common.BizException;
import com.example.learning.dto.response.ResourceSummary;
import com.example.learning.entity.ResourceEntity;
import com.example.learning.repository.ResourceRepository;
import com.example.learning.repository.StudentProfileRepository;
import com.example.learning.common.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 资源服务：多智能体产物持久化、向量入库（骨架阶段留空）、查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final StudentProfileRepository profileRepository;
    private final JsonUtils jsonUtils;

    /** 将六类产物写入资源表，并留出向量入库钩子。 */
    @Transactional
    public void save(ResourceBundle bundle, ResourceBlueprint spec, Long studentId, String taskId) {
        saveOne(taskId, studentId, spec, ResourceEntity.ResourceType.DOC, bundle.doc());
        saveOne(taskId, studentId, spec, ResourceEntity.ResourceType.MINDMAP, bundle.mindmap());
        saveOne(taskId, studentId, spec, ResourceEntity.ResourceType.QUIZ, bundle.quiz());
        saveOne(taskId, studentId, spec, ResourceEntity.ResourceType.READING, bundle.reading());
        saveOne(taskId, studentId, spec, ResourceEntity.ResourceType.VIDEO_SCRIPT, bundle.video());
        saveOne(taskId, studentId, spec, ResourceEntity.ResourceType.CODE, bundle.code());
        // TODO: encodeToVector(bundle, spec)  // Qdrant 语义入库
    }

    private void saveOne(String taskId, Long studentId, ResourceBlueprint spec,
                         ResourceEntity.ResourceType type, Object product) {
        if (product == null) {
            log.warn("task {} missing resource type {}", taskId, type);
            return;
        }
        ResourceEntity entity = new ResourceEntity();
        entity.setTaskId(taskId);
        entity.setStudentId(studentId);
        entity.setType(type);
        entity.setTopic(spec.topic());
        entity.setDifficulty(spec.difficulty());
        entity.setContentJson(jsonUtils.write(product));
        entity.setMetaJson(jsonUtils.write(spec.knowledgePoints()));
        resourceRepository.save(entity);
    }

    public List<ResourceSummary> listByStudent(Long studentId) {
        List<ResourceEntity> all = resourceRepository.findAll();
        return all.stream()
                .filter(r -> r.getStudentId() == null || r.getStudentId().equals(studentId))
                .map(r -> new ResourceSummary(r.getId(), r.getType(), r.getTopic(),
                        r.getDifficulty(), r.getMetaJson(), r.getCreatedAt()))
                .toList();
    }

    public ResourceEntity get(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "资源不存在"));
    }

    /** 读取学生最近一版画像 JSON（用于 Agent 上下文），无则返回空对象。 */
    public String loadProfileJson(Long studentId) {
        return profileRepository.findTopByStudentIdOrderByVersionDesc(studentId)
                .map(com.example.learning.entity.StudentProfileEntity::getProfileJson)
                .orElse("{}");
    }
}