package com.example.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件上传相关配置（tusd / RustFS / 上传凭证）。
 * 前缀：app.upload，对应 application.yml。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** 前端分片大小（字节），由 /api/upload/config 下发给 Uppy。 */
    private long chunkSize = 8L * 1024 * 1024;

    /** 前端同时上传的文件数，避免单机出口带宽与 tusd 内存被打满。 */
    private int maxConcurrentUploads = 3;

    private Tusd tusd = new Tusd();
    private Storage storage = new Storage();
    private Security security = new Security();
    private Cleanup cleanup = new Cleanup();
    private Download download = new Download();
    private Batch batch = new Batch();

    /** tusd 服务信息。 */
    @Data
    public static class Tusd {
        /** tusd 内网地址：仅服务端调用（终止上传），不暴露给浏览器。 */
        private String baseUrl = "http://localhost:1080";
        /** 浏览器访问 tusd 的同源路径前缀，必须与 tusd -base-path 一致。 */
        private String publicPath = "/files/";
    }

    /** RustFS（S3 兼容）连接信息。 */
    @Data
    public static class Storage {
        private String endpoint = "http://localhost:9000";
        private String bucket = "learning-uploads";
        private String accessKey = "rustfsadmin";
        private String secretKey = "rustfsadmin";
        private String region = "us-east-1";
        /**
         * 启动时若桶不存在则自动创建（私有桶）。
         * 生产可关闭并要求运维预置桶，避免应用账号具备 CreateBucket 权限。
         */
        private boolean autoCreateBucket = true;
        /** 启动时下发桶生命周期规则，让存储端回收未完成的 Multipart 分片。 */
        private boolean lifecycleEnabled = true;
        /** 未完成 Multipart 上传的保留天数，超过后存储端中止并释放已写分片。 */
        private int abortIncompleteMultipartDays = 7;
        /** 生命周期规则生效的对象前缀，必须与 tusd 的 -s3-object-prefix 一致。 */
        private String objectPrefix = "uploads/";
    }

    /** 上传凭证与限制。 */
    @Data
    public static class Security {
        /** HMAC 密钥，生产环境必须用环境变量覆盖。 */
        private String signingKey = "dev-only-change-me";
        /** 凭证有效期（分钟）。 */
        private long tokenTtlMinutes = 120;
        /** 单文件上限（字节），同时下发给 Uppy 做前置校验。 */
        private long maxFileSize = 5L * 1024 * 1024 * 1024;
        /** 允许的扩展名（小写，不含点）。 */
        private List<String> allowedExtensions = List.of(
                "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "csv",
                "md", "txt", "zip", "png", "jpg", "jpeg", "gif", "webp", "mp4", "mp3");
    }

    /**
     * 残留上传清理。
     * <p>学生上传到一半关掉页面后，记录会永远停在 UPLOADING，对应的 S3 分片
     * 也一直占空间。这里做的是「业务侧兜底回收」；存储侧的
     * AbortIncompleteMultipartUpload 生命周期规则是第二道网。
     */
    @Data
    public static class Cleanup {
        private boolean enabled = true;
        /**
         * UPLOADING 状态的存活时长（小时），超过即回收。
         * 默认 7 天：宁可少清，也不误伤放假回来续传的大文件。
         */
        private long uploadingTtlHours = 168;
        /** 单次处理条数，避免停机后堆积过多时一次性加载。 */
        private int batchSize = 100;
        /** 执行时间：每天凌晨 3:17，避开上传高峰。 */
        private String cron = "0 17 3 * * ?";
    }

    /**
     * 下载侧的传输参数。
     *
     * <p>这里的取值直接决定并发下载能力：每条正在传输的下载会独占一条到 RustFS 的
     * HTTP 连接，连接池小于并发数时，多出来的请求会卡在「取连接」上直到超时。
     * AWS SDK 的默认值是 50 连接 / 30 秒读超时，对大文件并发下载明显不足。
     */
    @Data
    public static class Download {
        /**
         * 到 RustFS 的最大连接数。
         * <p>ApacheHttpClient 默认 50。按「单机 100 人同时看视频 + 余量」取 200；
         * RustFS 侧也要能承受同等并发，否则瓶颈只是从应用挪到了存储。
         */
        private int maxConnections = 200;
        /** 建立连接的超时（秒）。同机房内网，5 秒足够。 */
        private int connectionTimeoutSeconds = 5;
        /**
         * 两次数据之间的读超时（秒），不是整个传输的总时长。
         * <p>默认 30 秒对慢速客户端太短：网速抖动 30 秒没读到新数据就会断开，
         * 断点续传因此被频繁触发。放宽到 5 分钟。
         */
        private int socketTimeoutSeconds = 300;
        /**
         * 从连接池取连接的等待上限（秒）。
         * <p>并发超过 {@link #maxConnections} 时请求在此排队，超过即失败。
         * 给 60 秒，宁可排队也不要直接报错。
         */
        private int acquireTimeoutSeconds = 60;
    }

    /**
     * 小文件批量导入。
     *
     * <p>与 tus 链路是两条路：大文件靠分片与断点续传，小文件直接 PUT。
     * 阈值定在 10MB —— 再大就该走分片了，否则一次请求要等在内存里读完，
     * 中途断掉就得整份重传。
     */
    @Data
    public static class Batch {
        /** 单次最多文件数。 */
        private int maxFiles = 20;
        /** 单文件上限（字节）。 */
        private long maxFileSize = 10L * 1024 * 1024;
        /** 单次请求总大小上限（字节），防止一次把堆吃光。 */
        private long maxTotalSize = 50L * 1024 * 1024;
    }
}
