package com.example.learning.controller;

import com.example.learning.common.ApiResult;
import com.example.learning.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学习行为埋点接口。
 */
@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @PostMapping("/{studentId}/{eventType}")
    public ApiResult<Void> record(@PathVariable Long studentId,
                                  @PathVariable String eventType,
                                  @RequestBody(required = false) Object payload) {
        telemetryService.record(studentId, eventType, payload);
        return ApiResult.success();
    }
}