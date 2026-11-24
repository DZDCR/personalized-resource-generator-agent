package com.example.learning.common;

import lombok.Getter;

/**
 * 业务异常：携带业务错误码。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    /** 可选的结构化错误数据（如角色选择提示），前端可直接消费。 */
    private final Object data;

    public BizException(int code, String message) {
        this(code, message, null);
    }

    public BizException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public BizException(String message) {
        this(500, message);
    }

    public static BizException notFound(String entity) {
        return new BizException(404, entity + "不存在");
    }
}