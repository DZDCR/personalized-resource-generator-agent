package com.example.learning.repository;

import com.example.learning.entity.PathNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PathNodeRepository extends JpaRepository<PathNodeEntity, Long> {

    List<PathNodeEntity> findByPathIdOrderByOrderIndexAsc(Long pathId);

    long countByPathIdAndStatus(Long pathId, PathNodeEntity.NodeStatus status);
}