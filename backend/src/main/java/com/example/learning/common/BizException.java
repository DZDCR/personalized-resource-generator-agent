package com.example.learning.common;

import lombok.Getter;

/**
 * 业务异常：携带业务错误码。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        this(500, message);
    }

    public static BizException notFound(String entity) {
        return new BizException(404, entity + "不存在");
    }
}