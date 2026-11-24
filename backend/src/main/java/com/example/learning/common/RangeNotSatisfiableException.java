package com.example.learning.common;

import lombok.Getter;

/**
 * 请求的字节范围超出文件大小（HTTP 416）。
 *
 * <p>单独建异常的原因：{@link BizException} 会被 {@code GlobalExceptionHandler}
 * 统一包装成 HTTP 200 + 业务码，而 Range 请求越界必须返回真正的 416，
 * 否则浏览器播放器会把整个响应当成数据体，表现为“视频卡住不动”。
 *
 * <p>RFC 9110 要求 416 响应携带 {@code Content-Range: bytes * /{size}}，
 * 让客户端知道文件的真实大小。
 */
@Getter
public class RangeNotSatisfiableException extends RuntimeException {

    private final String contentRange;

    public RangeNotSatisfiableException(Long totalSize) {
        super("请求的字节范围超出文件大小");
        this.contentRange = totalSize == null ? null : "bytes */" + totalSize;
    }
}
