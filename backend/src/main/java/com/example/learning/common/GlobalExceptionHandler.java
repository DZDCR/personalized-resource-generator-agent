package com.example.learning.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器：统一将异常转换为 ApiResult。
 * 业务异常使用 BizException，未知异常记日志后返回通用错误。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<Void> handleBiz(BizException ex) {
        return ApiResult.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleValidation(MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String msg = first == null ? "参数校验失败" : first.getField() + " " + first.getDefaultMessage();
        return ApiResult.error(400, msg);
    }

    /**
     * 上传体积超限。这个异常在进入 Controller 之前由 multipart 解析器抛出，
     * 业务层拿不到请求，必须在全局处理，否则用户只看到「服务器内部错误」。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<Void> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.info("上传体积超限: {}", ex.getMessage());
        return ApiResult.error(413, "文件超过单次上传上限，请改用分片上传");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleUnknown(Exception ex) {
        log.error("unexpected error", ex);
        return ApiResult.error(500, "服务器内部错误");
    }
}