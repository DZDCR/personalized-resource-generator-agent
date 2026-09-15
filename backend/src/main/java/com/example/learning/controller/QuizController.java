package com.example.learning.controller;

import com.example.learning.common.ApiResult;
import com.example.learning.dto.request.QuizSubmitRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 练习测试接口。
 */
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    @PostMapping("/submit")
    public ApiResult<Map<String, Object>> submit(@RequestBody QuizSubmitRequest request) {
        // TODO: 判分 + 写 QuizRecordEntity + 触发画像更新
        return ApiResult.success(Map.of("score", 0, "message", "骨架阶段占位"));
    }
}