package com.example.learning.dto.upload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * tusd HTTP Hook 请求体。
 * 字段名遵循 tusd 的 PascalCase 约定，必须显式标注 @JsonProperty。
 *
 * @see <a href="https://github.com/tus/tusd/blob/main/docs/_advanced-topics/hooks.md">tusd hooks</a>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TusdHookRequest {

    /** pre-create / post-create / post-receive / post-finish / post-terminate */
    @JsonProperty("Type")
    private String type;

    @JsonProperty("Event")
    private Event event;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Event {

        @JsonProperty("Upload")
        private Upload upload;

        @JsonProperty("HTTPRequest")
        private HttpRequest httpRequest;
    }

    /** 上传实体。pre-create 阶段 ID 为空、Storage 不可用。 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Upload {

        @JsonProperty("ID")
        private String id;

        /** 总字节数；Upload-Defer-Length 时可能为 null */
        @JsonProperty("Size")
        private Long size;

        @JsonProperty("SizeIsDeferred")
        private boolean sizeIsDeferred;

        @JsonProperty("Offset")
        private Long offset;

        /** 客户端携带的元数据，值恒为字符串 */
        @JsonProperty("MetaData")
        private Map<String, String> metaData;

        @JsonProperty("IsPartial")
        private boolean partial;

        @JsonProperty("IsFinal")
        private boolean finalUpload;

        @JsonProperty("PartialUploads")
        private List<String> partialUploads;

        /** 存储位置。s3store: {Type, Bucket, Key}；filestore: {Type, Path, InfoPath} */
        @JsonProperty("Storage")
        private Map<String, String> storage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HttpRequest {

        @JsonProperty("Method")
        private String method;

        @JsonProperty("URI")
        private String uri;

        @JsonProperty("RemoteAddr")
        private String remoteAddr;

        @JsonProperty("Header")
        private Map<String, List<String>> header;
    }

    /** 取某个请求头的首个值，不存在返回 null。 */
    public String header(String name) {
        if (event == null || event.getHttpRequest() == null || event.getHttpRequest().getHeader() == null) {
            return null;
        }
        return event.getHttpRequest().getHeader().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(e -> e.getValue() == null || e.getValue().isEmpty() ? null : e.getValue().get(0))
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);
    }
}
