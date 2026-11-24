package com.example.learning.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortIncompleteMultipartUpload;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 启动时自检对象存储桶，不存在则创建。
 * <p>
 * tusd 不会自动建桶，缺失时所有 PutObject / CreateMultipartUpload 都会失败。
 * 早期版本用一次性容器（minio/mc）建桶，但 MinIO 已从 Docker Hub 下架该镜像，
 * 拉取必然失败，因此改为由后端在建连后自建桶，少一个外部依赖与启动时序问题。
 * <p>
 * 对象存储不可用时只告警、不阻断启动：上传链路的健康状态由 /api/upload/config
 * 与 tusd 的 Hook 反映，后端本身仍可提供其它业务能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RustFsBucketInitializer implements ApplicationRunner {

    private final S3Client rustFsS3Client;
    private final UploadProperties uploadProperties;

    @Override
    public void run(ApplicationArguments args) {
        UploadProperties.Storage storage = uploadProperties.getStorage();
        String bucket = storage.getBucket();

        if (!storage.isAutoCreateBucket()) {
            log.info("跳过对象存储桶自检（app.upload.storage.auto-create-bucket=false），使用桶 {}", bucket);
            return;
        }

        try {
            rustFsS3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("对象存储桶已存在: {}", bucket);
            applyLifecycle(bucket);
        } catch (NoSuchBucketException e) {
            if (createBucket(bucket)) {
                applyLifecycle(bucket);
            }
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                if (createBucket(bucket)) {
                    applyLifecycle(bucket);
                }
            } else {
                log.warn("对象存储桶自检失败（bucket={}, status={}）: {}",
                        bucket, e.statusCode(), e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorMessage());
            }
        } catch (Exception e) {
            log.warn("对象存储暂不可用，跳过桶自检（上传功能需先启动 RustFS）: {}", e.getMessage());
        }
    }

    private boolean createBucket(String bucket) {
        try {
            rustFsS3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("已自动创建对象存储桶: {}", bucket);
            return true;
        } catch (Exception e) {
            log.warn("自动创建桶失败（bucket={}）: {}", bucket, e.getMessage());
            return false;
        }
    }

    /**
     * 下发桶生命周期规则：中止长期未完成的 Multipart 上传。
     *
     * <p>为什么需要：tus 断点续传在服务端对应一次 S3 Multipart Upload，学生中途放弃后
     * 已写入的分片（part）不会自动消失，也不出现在对象列表里，只能靠
     * AbortIncompleteMultipartUpload 规则或显式 AbortMultipartUpload 回收。
     * 这是存储成本的第二道网——第一道是 UploadCleanupJob 的业务侧清理。
     *
     * <p>失败不阻断启动：部分 S3 兼容实现对 PutBucketLifecycleConfiguration 支持不全，
     * 此时记一条警告即可，清理任务仍在工作。
     */
    private void applyLifecycle(String bucket) {
        UploadProperties.Storage storage = uploadProperties.getStorage();
        if (!storage.isLifecycleEnabled()) {
            log.info("跳过生命周期规则下发（app.upload.storage.lifecycle-enabled=false）");
            return;
        }
        int days = Math.max(1, storage.getAbortIncompleteMultipartDays());
        String prefix = storage.getObjectPrefix() == null ? "" : storage.getObjectPrefix();
        try {
            BucketLifecycleConfiguration configuration = BucketLifecycleConfiguration.builder()
                    .rules(LifecycleRule.builder()
                            .id("abort-incomplete-multipart")
                            .status(ExpirationStatus.ENABLED)
                            .filter(LifecycleRuleFilter.builder().prefix(prefix).build())
                            .abortIncompleteMultipartUpload(AbortIncompleteMultipartUpload.builder()
                                    .daysAfterInitiation(days)
                                    .build())
                            .build())
                    .build();
            rustFsS3Client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                    .bucket(bucket)
                    .lifecycleConfiguration(configuration)
                    .build());
            log.info("已下发生命周期规则：{} 下超过 {} 天未完成的 Multipart 上传将被中止", prefix, days);
        } catch (Exception e) {
            log.warn("下发生命周期规则失败（bucket={}，该实现可能不支持，可忽略；"
                    + "业务侧 UploadCleanupJob 仍在兜底）: {}", bucket, e.getMessage());
        }
    }
}
