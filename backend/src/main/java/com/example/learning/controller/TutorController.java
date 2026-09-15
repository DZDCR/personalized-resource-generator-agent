package com.example.learning.controller;

import com.example.learning.common.ApiResult;
import com.example.learning.service.TutorService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 智能辅导接口（加分项）。
 */
@RestController
@RequestMapping("/api/tutor")
@RequiredArgsConstructor
public class TutorController {

    private final TutorService tutorService;

    @PostMapping("/ask")
    public ApiResult<Map<String, Object>> ask(
            @RequestParam @NotNull Long studentId,
            @RequestParam @NotBlank String question) {
        return ApiResult.success(tutorService.answer(studentId, question));
    }
}