package com.example.learning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 对话路由服务：根据学生状态将对话分发给画像构建或智能辅导。
 * <p>规则：若画像置信度 &lt; 0.8 → 走 ProfileAgent 构建；
 * 否则进入辅导模式。骨架阶段仅返回占位回复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ProfileService profileService;

    public Map<String, Object> handleDialogue(Long studentId, String message) {
        String profileJson = profileService.getProfileJson(studentId);
        boolean profileMature = !"{}".equals(profileJson);

        if (!profileMature) {
            // TODO: 调用 ProfileAgent.extract，应用画像更新，返回 replyToStudent
            log.info("profile-building mode for student {}", studentId);
            return Map.of(
                    "mode", "PROFILE_BUILDING",
                    "reply", "好的，我们先聊聊你的情况吧！请问你是哪个专业的学生？"
            );
        }
        // TODO: 调用 TutorAgent.explain 产出多模态解答
        return Map.of(
                "mode", "TUTORING",
                "reply", "这是个好问题！可以参考已生成的知识卡片与导图（骨架阶段）"
        );
    }
}