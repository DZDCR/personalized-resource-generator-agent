package com.example.learning.dto.upload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * tusd HTTP Hook 响应体。
 * 字段均为可选；pre-create 可通过 RejectUpload 拦截，通过 ChangeFileInfo 改写元数据。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TusdHookResponse {

    @JsonProperty("HTTPResponse")
    private HttpResponse httpResponse;

    @JsonProperty("RejectUpload")
    private Boolean rejectUpload;

    @JsonProperty("ChangeFileInfo")
    private ChangeFileInfo changeFileInfo;

    /** 放行（不修改任何信息）。 */
    public static TusdHookResponse ok() {
        return new TusdHookResponse();
    }

    /** 拒绝上传，并把指定状态码/消息透传给客户端。 */
    public static TusdHookResponse reject(int statusCode, String message) {
        TusdHookResponse response = new TusdHookResponse();
        response.rejectUpload = true;
        HttpResponse http = new HttpResponse();
        http.setStatusCode(statusCode);
        http.setBody("{\"code\":" + statusCode + ",\"message\":\"" + escape(message) + "\"}");
        http.setHeader(Map.of("Content-Type", "application/json; charset=utf-8"));
        response.httpResponse = http;
        return response;
    }

    /** 放行，并用服务端校验后的元数据覆盖客户端元数据（可剔除凭证字段）。 */
    public static TusdHookResponse changeMeta(Map<String, String> metaData) {
        TusdHookResponse response = new TusdHookResponse();
        ChangeFileInfo info = new ChangeFileInfo();
        info.setMetaData(new LinkedHashMap<>(metaData));
        response.changeFileInfo = info;
        return response;
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HttpResponse {
        @JsonProperty("StatusCode")
        private Integer statusCode;
        @JsonProperty("Body")
        private String body;
        @JsonProperty("Header")
        private Map<String, String> header;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChangeFileInfo {
        @JsonProperty("ID")
        private String id;
        @JsonProperty("MetaData")
        private Map<String, String> metaData;
        @JsonProperty("Storage")
        private Map<String, String> storage;
    }
}
