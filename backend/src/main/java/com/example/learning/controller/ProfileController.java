package com.example.learning.controller;

import com.example.learning.common.ApiResult;
import com.example.learning.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 画像接口。
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/{studentId}")
    public ApiResult<String> get(@PathVariable Long studentId) {
        return ApiResult.success(profileService.getProfileJson(studentId));
    }
}