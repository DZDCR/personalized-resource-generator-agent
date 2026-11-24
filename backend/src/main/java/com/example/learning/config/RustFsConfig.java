package com.example.learning.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;

/**
 * RustFS 的 S3 客户端。
 * RustFS 是 S3 兼容实现，必须强制 path-style（bucket 作为路径而非子域名）。
 * 该客户端只用于服务端操作（读取/删除对象），上传链路由 tusd 独立完成。
 *
 * <p>下载并发能力由 HTTP 连接池决定，这里显式配置而不用 SDK 默认值：
 * 默认 ApacheHttpClient 只有 50 条连接、30 秒读超时。每条正在传输的下载会独占
 * 一条连接直到传完，第 51 个并发请求就会卡在「取连接」上，最终超时失败；
 * 慢速客户端若 30 秒没有新数据，也会被误判断线。取值见 {@code app.upload.download}。
 */
@Configuration
@RequiredArgsConstructor
public class RustFsConfig {

    private final UploadProperties properties;

    @Bean
    public S3Client rustFsS3Client() {
        UploadProperties.Storage storage = properties.getStorage();
        UploadProperties.Download download = properties.getDownload();

        SdkHttpClient httpClient = ApacheHttpClient.builder()
                .maxConnections(download.getMaxConnections())
                .connectionTimeout(Duration.ofSeconds(download.getConnectionTimeoutSeconds()))
                .socketTimeout(Duration.ofSeconds(download.getSocketTimeoutSeconds()))
                .connectionAcquisitionTimeout(Duration.ofSeconds(download.getAcquireTimeoutSeconds()))
                // 长连接：下载是「建连一次、传很久」，复用连接能省掉每次 TCP 握手
                .tcpKeepAlive(true)
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(storage.getEndpoint()))
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storage.getAccessKey(), storage.getSecretKey())))
                .forcePathStyle(true)
                .httpClient(httpClient)
                // 不设 apiCallTimeout：那是「整次调用」的总时长，会误杀大文件传输
                .build();
    }
}
