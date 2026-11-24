package com.example.learning.controller;

import com.example.learning.dto.upload.TusdHookRequest;
import com.example.learning.dto.upload.TusdHookResponse;
import com.example.learning.service.UploadFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * tusd HTTP Hook 回调入口（仅内网可达，由 tusd 调用）。
 *
 * 注意：这里不能用 ApiResult 包装，tusd 只认识自己的 HookResponse 结构。
 * pre-create 返回 200 + RejectUpload 拒绝；post-* 失败返回 5xx 让 tusd 重试。
 */
@Slf4j
@RestController
@RequestMapping("/api/upload/hooks")
@RequiredArgsConstructor
public class UploadHookController {

    private final UploadFileService uploadFileService;

    @PostMapping
    public ResponseEntity<TusdHookResponse> handle(@RequestBody TusdHookRequest request) {
        String type = request.getType() == null ? "" : request.getType();
        try {
            return switch (type) {
                case "pre-create" -> ResponseEntity.ok(uploadFileService.preCreate(request));
                case "post-create" -> {
                    uploadFileService.postCreate(request);
                    yield ResponseEntity.ok(TusdHookResponse.ok());
                }
                case "post-finish" -> {
                    uploadFileService.postFinish(request);
                    yield ResponseEntity.ok(TusdHookResponse.ok());
                }
                case "post-terminate" -> {
                    uploadFileService.postTerminate(request);
                    yield ResponseEntity.ok(TusdHookResponse.ok());
                }
                default -> {
                    log.debug("忽略未订阅的 tusd hook: {}", type);
                    yield ResponseEntity.ok(TusdHookResponse.ok());
                }
            };
        } catch (Exception ex) {
            if ("pre-create".equals(type)) {
                log.error("pre-create 校验失败", ex);
                return ResponseEntity.ok(TusdHookResponse.reject(500, "上传校验失败，请稍后重试"));
            }
            // 其余事件交给 tusd 重试（默认 3 次，指数退避）
            log.error("tusd hook 处理失败 type={}", type, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(TusdHookResponse.ok());
        }
    }
}
