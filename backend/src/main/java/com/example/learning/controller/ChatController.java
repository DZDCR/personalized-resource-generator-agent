package com.example.learning.controller;

import com.example.learning.common.ApiResult;
import com.example.learning.dto.request.ChatRequest;
import com.example.learning.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 对话接口：统一入口，后端根据画像状态路由到画像构建或辅导。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ApiResult<Map<String, Object>> chat(@Valid @RequestBody ChatRequest request) {
        Long studentId = Long.parseLong(request.studentId());
        return ApiResult.success(chatService.handleDialogue(studentId, request.message()));
    }
}