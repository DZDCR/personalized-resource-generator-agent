package com.example.learning.repository;

import com.example.learning.entity.ResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository extends JpaRepository<ResourceEntity, Long> {

    List<ResourceEntity> findByStudentIdAndType(Long studentId, ResourceEntity.ResourceType type);

    List<ResourceEntity> findByTopicContaining(String topic);

    List<ResourceEntity> findByTaskId(String taskId);

    Optional<ResourceEntity> findFirstByTaskIdAndType(String taskId, ResourceEntity.ResourceType type);
}