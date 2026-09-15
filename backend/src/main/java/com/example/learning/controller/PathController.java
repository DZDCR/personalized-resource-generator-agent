package com.example.learning.controller;

import com.example.learning.common.ApiResult;
import com.example.learning.dto.request.PathGenerateRequest;
import com.example.learning.service.PathService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学习路径接口。
 */
@RestController
@RequestMapping("/api/path")
@RequiredArgsConstructor
public class PathController {

    private final PathService pathService;

    @PostMapping("/generate")
    public ApiResult<Map<String, Object>> generate(@Valid @RequestBody PathGenerateRequest request) {
        return ApiResult.success(pathService.generate(
                Long.parseLong(request.studentId()), request.targetTopic()));
    }
}