package com.example.learning.controller;

import com.example.learning.agent.Orchestrator;
import com.example.learning.common.ApiResult;
import com.example.learning.dto.request.ResourceGenerateRequest;
import com.example.learning.dto.response.GenerateTaskResponse;
import com.example.learning.dto.response.ResourceSummary;
import com.example.learning.entity.ResourceEntity;
import com.example.learning.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资源接口：多智能体生成 + 查询。
 */
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final Orchestrator orchestrator;
    private final ResourceService resourceService;

    /** 提交多智能体生成任务（异步），立即返回任务号。 */
    @PostMapping("/generate")
    public ApiResult<GenerateTaskResponse> generate(@Valid @RequestBody ResourceGenerateRequest request) {
        return ApiResult.success(orchestrator.start(request));
    }

    @GetMapping("/{studentId}")
    public ApiResult<List<ResourceSummary>> list(@PathVariable Long studentId) {
        return ApiResult.success(resourceService.listByStudent(studentId));
    }

    @GetMapping("/detail/{id}")
    public ApiResult<ResourceEntity> detail(@PathVariable Long id) {
        return ApiResult.success(resourceService.get(id));
    }
}