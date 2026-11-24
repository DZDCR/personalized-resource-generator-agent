package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * 上传文件记录。
 * 一行对应 tusd 的一个上传（uploadId 即 tusd 的 Upload-ID），
 * 由 post-create 插入、post-finish 完结、post-terminate 标记终止。
 * 文件二进制不落库，存于 RustFS，objectKey 由 tusd 的 s3store 提供。
 */
@Data
@Entity
@Table(name = "upload_files",
        uniqueConstraints = @UniqueConstraint(name = "uk_upload_files_upload_id", columnNames = "upload_id"),
        indexes = {
                @Index(name = "idx_upload_files_student", columnList = "student_id, created_at"),
                @Index(name = "idx_upload_files_status", columnList = "status")
        })
public class UploadFileEntity {

    public enum UploadStatus {
        /** 分片上传中（可断点续传） */
        UPLOADING,
        /** 服务端分片合并完成，对象可下载 */
        COMPLETED,
        /** 客户端主动终止（tus DELETE） */
        TERMINATED,
        /** 业务侧逻辑删除 */
        DELETED,
        /** 超时未续传，已被清理任务回收（tusd 分片与对象均已删除） */
        EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** tusd 上传 ID */
    /**
     * tusd Upload-ID。S3 store 会带版本后缀：<uuid>+<base64(uid.version)>，
     * 实测长度 150+，故不能按 uuid 的 36/64 设列宽。
     */
    @Column(name = "upload_id", nullable = false, length = 255)
    private String uploadId;

    /** 归属学生 */
    @Column(name = "student_id")
    private Long studentId;

    /** 原始文件名（仅展示与下载时使用，不参与对象 key） */
    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "content_type", length = 160)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** 已接收字节数（UPLOADING 阶段可用于展示服务端进度） */
    @Column(name = "offset_bytes")
    private Long offsetBytes;

    @Column(name = "bucket", length = 128)
    private String bucket;

    /** RustFS 对象 key，由 tusd s3store 生成 */
    @Column(name = "object_key", length = 512)
    private String objectKey;

    /** 存储类型：s3store */
    @Column(name = "storage_type", length = 32)
    private String storageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UploadStatus status;

    /**
     * tusd 元数据快照（jsonb）。
     * 用 Map 而非 String：String 会被 Hibernate 当 varchar 绑定，PostgreSQL 不会
     * 隐式转成 jsonb（SQLState 42804）；Map + JSON 类型才会正确走 jsonb 参数。
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta_json", columnDefinition = "jsonb")
    private Map<String, String> metaData;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
