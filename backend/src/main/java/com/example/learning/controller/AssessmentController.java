package com.example.learning.controller;

import com.example.learning.common.ApiResult;
import com.example.learning.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学习效果评估接口（加分项）。
 */
@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    @GetMapping("/{studentId}/weekly")
    public ApiResult<Map<String, Object>> weekly(@PathVariable Long studentId) {
        return ApiResult.success(assessmentService.weeklyReport(studentId));
    }
}