package com.example.learning.common;

import com.example.learning.config.PromptLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 运维接口：健康检查、Prompt 热更新（可选）。
 */
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class OpsController {

    private final PromptLoader promptLoader;

    @GetMapping("/health")
    public ApiResult<Map<String, String>> health() {
        return ApiResult.success(Map.of("status", "UP"));
    }

    @PostMapping("/prompts/reload")
    public ApiResult<Void> reloadPrompts() {
        promptLoader.invalidateAll();
        return ApiResult.success();
    }
}