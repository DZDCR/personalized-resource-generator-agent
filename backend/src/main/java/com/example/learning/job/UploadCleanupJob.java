package com.example.learning.job;

import com.example.learning.config.UploadProperties;
import com.example.learning.entity.UploadFileEntity;
import com.example.learning.repository.UploadFileRepository;
import com.example.learning.service.RustFsStorageService;
import com.example.learning.service.UploadFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 残留上传清理任务。
 *
 * <p>要解决的问题：学生上传到一半关页面，或断点后再没回来，记录会永久停在
 * UPLOADING。这些记录本身不大，但它们在 RustFS 里对应一次未完成的 S3 Multipart
 * 上传，已写入的分片会一直占空间且不会被任何业务逻辑回收。
 *
 * <p>两道网：
 * <ol>
 *   <li>本任务：业务侧识别超时记录 → 调 tusd DELETE 清理分片 → 状态置 EXPIRED。</li>
 *   <li>桶生命周期规则：存储侧按 AbortIncompleteMultipartUpload 兜底，
 *       即使本任务停摆或记录丢失，分片也会在设定天数后被存储端中止。</li>
 * </ol>
 *
 * <p>并发安全：更新走 {@code status = UPLOADING} 的条件更新，多实例同时跑时
 * 后到的一方影响行数为 0，不会重复处理；tusd DELETE 重复调用只会得到 404，无副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadCleanupJob {

    /** 单次最多处理几批，避免长时间停机后积压过多导致一次跑太久。 */
    private static final int MAX_ROUNDS = 5;

    private final UploadFileRepository repository;
    private final UploadFileService uploadFileService;
    private final RustFsStorageService storageService;
    private final UploadProperties properties;

    @Scheduled(cron = "${app.upload.cleanup.cron:0 17 3 * * ?}")
    public void purgeStaleUploads() {
        UploadProperties.Cleanup config = properties.getCleanup();
        if (!config.isEnabled()) {
            log.debug("残留上传清理已关闭（app.upload.cleanup.enabled=false）");
            return;
        }

        Instant threshold = Instant.now().minus(Duration.ofHours(config.getUploadingTtlHours()));
        int batchSize = Math.max(1, config.getBatchSize());
        int totalExpired = 0;
        int scanned = 0;

        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<UploadFileEntity> batch = repository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                    UploadFileEntity.UploadStatus.UPLOADING, threshold, PageRequest.of(0, batchSize));
            if (batch.isEmpty()) {
                break;
            }
            scanned += batch.size();
            int expired = 0;
            for (UploadFileEntity entity : batch) {
                if (purgeOne(entity)) {
                    expired++;
                }
            }
            totalExpired += expired;
            if (batch.size() < batchSize) {
                break;
            }
        }

        if (scanned > 0) {
            log.info("残留上传清理完成：扫描 {} 条，回收 {} 条（门槛 {} 小时）",
                    scanned, totalExpired, config.getUploadingTtlHours());
        }
    }

    /** 回收单条：先让 tusd 清分片，再推进状态。 */
    private boolean purgeOne(UploadFileEntity entity) {
        String uploadId = entity.getUploadId();
        try {
            storageService.terminate(uploadId);
        } catch (Exception ex) {
            // tusd 里已没有这个 upload（404）是常见情况：可能早被 post-terminate 清掉了，
            // 此时只需确保对象本体也不残留，然后照常推进状态。
            log.debug("tusd 终止未成功，尝试直接清理对象 uploadId={}: {}", uploadId, ex.getMessage());
            try {
                storageService.deleteObject(entity.getBucket(), entity.getObjectKey());
            } catch (Exception inner) {
                log.warn("清理对象失败，仍推进状态 uploadId={}: {}", uploadId, inner.getMessage());
            }
        }

        boolean expired = uploadFileService.expireIfStillUploading(uploadId);
        if (!expired) {
            // 扫描到处理之间被 post-finish 改成 COMPLETED，属正常竞态，绝不覆盖
            log.info("跳过已变更状态的上传 uploadId={}", uploadId);
            return false;
        }
        log.info("已回收超时未续传的上传 uploadId={} studentId={} createdAt={}",
                uploadId, entity.getStudentId(), entity.getCreatedAt());
        return true;
    }
}
