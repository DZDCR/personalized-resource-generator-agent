package com.example.learning.repository;

import com.example.learning.entity.UploadFileEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadFileRepository extends JpaRepository<UploadFileEntity, Long> {

    /** Hook 幂等：tusd 会重试 post-create / post-finish */
    Optional<UploadFileEntity> findByUploadId(String uploadId);

    List<UploadFileEntity> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    Optional<UploadFileEntity> findByIdAndStudentId(Long id, Long studentId);

    List<UploadFileEntity> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, UploadFileEntity.UploadStatus status);

    /**
     * 清理任务用：按状态 + 创建时间取一批待处理记录。
     * <p>用 Pageable 限制批量，避免某次长时间停机后堆积几万条时一次性加载。
     */
    List<UploadFileEntity> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            UploadFileEntity.UploadStatus status, Instant before, Pageable pageable);

    /**
     * 状态机推进：仅当当前状态等于 expected 时才改成 target。
     * <p>清理任务用它把 UPLOADING 置为 EXPIRED，SQL 的 WHERE 保证不会覆盖
     * 同一时刻刚被 post-finish 改成 COMPLETED 的记录。
     *
     * @return 受影响行数，0 表示状态已被其它流程改变
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UploadFileEntity e set e.status = :target, e.updatedAt = :now "
            + "where e.uploadId = :uploadId and e.status = :expected")
    int updateStatusIfCurrent(@Param("uploadId") String uploadId,
                              @Param("target") UploadFileEntity.UploadStatus target,
                              @Param("expected") UploadFileEntity.UploadStatus expected,
                              @Param("now") Instant now);

    /**
     * 条件更新状态：仅当当前状态不等于 excluded 时才改。
     * <p>
     * post-terminate 是 tusd 的异步回调（DELETE 已返回 204 之后才发出），
     * 若按“先查再改”实现，会与业务删除产生读写竞态，把 DELETED 覆盖回 TERMINATED。
     * 这里把判断下推到 SQL 的 WHERE，由数据库保证先后顺序。
     *
     * @return 受影响行数，0 表示记录已被其它流程改成 excluded 状态
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UploadFileEntity e set e.status = :target, e.updatedAt = :now "
            + "where e.uploadId = :uploadId and e.status <> :excluded")
    int updateStatusUnless(@Param("uploadId") String uploadId,
                           @Param("target") UploadFileEntity.UploadStatus target,
                           @Param("excluded") UploadFileEntity.UploadStatus excluded,
                           @Param("now") Instant now);
}
